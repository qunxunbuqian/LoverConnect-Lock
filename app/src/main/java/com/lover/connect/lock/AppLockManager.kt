package com.lover.connect.lock

import android.content.Context
import android.os.Handler
import android.os.Looper
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

class AppLockManager(
    private val context: Context,
    private val overlayManager: LockOverlayManager
) {
    private val handler = Handler(Looper.getMainLooper())
    private var monitorRunning = false
    private var foregroundPkg: String? = null

    // in-memory tracking (persisted to state file)
    private var timerAccMap = mutableMapOf<String, Int>()         // pkg -> accumulated foreground seconds
    private var timerLastTickSec: Long = 0

    companion object {
        private const val TICK_MS = 500L
        private const val STATE_FILE = "lock_state.json"
        private const val CONFIG_FILE = "lock_config.json"
    }

    // ========== Config ==========

    data class LockApp(
        var appName: String,
        var duration: Int,        // countdown lock seconds
        var unlockDelay: Int,     // auto-unlock delay, 0=disabled
        var active: Boolean
    )

    data class LockConfig(
        var defaultPassword: String = "1784",
        val apps: MutableMap<String, LockApp> = mutableMapOf()
    )

    private var config = LockConfig()
    private var stateDirty = false

    private fun configFile(): File = File(context.filesDir, CONFIG_FILE)
    private fun stateFile(): File = File(context.filesDir, STATE_FILE)

    init {
        overlayManager.passwordCorrectCallback = { pkg -> doUnlock(pkg) }
    }

    fun loadConfig() {
        try {
            val f = configFile()
            if (f.exists()) {
                val j = JSONObject(f.readText())
                config.defaultPassword = j.optString("defaultPassword", "1784")
                config.apps.clear()
                val apps = j.optJSONObject("apps")
                if (apps != null) {
                    for (pkg in apps.keys()) {
                        val a = apps.getJSONObject(pkg)
                        config.apps[pkg] = LockApp(
                            appName = a.optString("appName", pkg),
                            duration = a.optInt("duration", 30),
                            unlockDelay = a.optInt("unlockDelay", 0),
                            active = a.optBoolean("active", true)
                        )
                    }
                }
            }
        } catch (e: Exception) { android.util.Log.e("AppLock", "loadConfig err", e) }
        loadState()
    }

    private fun saveConfig() {
        try {
            val j = JSONObject()
            j.put("defaultPassword", config.defaultPassword)
            val apps = JSONObject()
            for ((pkg, app) in config.apps) {
                val a = JSONObject()
                a.put("appName", app.appName); a.put("duration", app.duration)
                a.put("unlockDelay", app.unlockDelay); a.put("active", app.active)
                apps.put(pkg, a)
            }
            j.put("apps", apps)
            configFile().writeText(j.toString(2))
        } catch (e: Exception) { android.util.Log.e("AppLock", "saveConfig err", e) }
    }

    private fun loadState() {
        try {
            val f = stateFile()
            if (f.exists()) {
                val j = JSONObject(f.readText())
                val acc = j.optJSONObject("timerAccMap")
                if (acc != null) {
                    for (pkg in acc.keys()) timerAccMap[pkg] = acc.getInt(pkg)
                }
            }
        } catch (e: Exception) { android.util.Log.e("AppLock", "loadState err", e) }
    }

    private fun saveState() {
        try {
            val j = JSONObject()
            val acc = JSONObject()
            for ((pkg, sec) in timerAccMap) acc.put(pkg, sec)
            j.put("timerAccMap", acc)
                        // preserve lockedApps from file
            val sf = stateFile()
            if (sf.exists()) {
                try {
                    val existing = JSONObject(sf.readText())
                    val existingLocked = existing.optJSONObject("lockedApps")
                    if (existingLocked != null) j.put("lockedApps", existingLocked)
                } catch (_: Exception) {}
            }
            sf.writeText(j.toString(2))
        } catch (e: Exception) { android.util.Log.e("AppLock", "saveState err", e) }
    }

    // ========== Monitoring ==========

    fun startMonitor(getForeground: () -> String?) {
        foregroundPkg = getForeground()
        monitorRunning = true
        timerLastTickSec = nowSec()
        postTick(getForeground)
    }

    fun stopMonitor() { monitorRunning = false }

    private fun postTick(getForeground: () -> String?) {
        if (!monitorRunning) return
        handler.postDelayed({ tick(getForeground) }, TICK_MS)
    }

    private fun tick(getForeground: () -> String?) {
        if (!monitorRunning) return
        try {
            val fg = getForeground() ?: run { postTick(getForeground); return }
            foregroundPkg = fg
            val now = nowSec()
            val delta = (now - timerLastTickSec).coerceIn(0, 5)
            timerLastTickSec = now

            // 1. Check auto-unlock timers for locked apps
            val lockedStateFile = File(context.filesDir, "lock_state.json")
            var lockedApps = mutableMapOf<String, Long>()  // pkg -> lockStartTime
            try {
                if (lockedStateFile.exists()) {
                    val j = JSONObject(lockedStateFile.readText())
                    val locked = j.optJSONObject("lockedApps")
                    if (locked != null) {
                        for (pkg in locked.keys()) lockedApps[pkg] = locked.getLong(pkg)
                    }
                }
            } catch (_: Exception) {}

            val toUnlock = mutableListOf<String>()
            for ((pkg, lst) in lockedApps) {
                val app = config.apps[pkg] ?: continue
                if (app.unlockDelay > 0 && (now - lst) >= app.unlockDelay) {
                    toUnlock.add(pkg)
                }
            }
            for (pkg in toUnlock) doUnlock(pkg)

            // 2. If foreground is a locked app, show lock screen
            if (lockedApps.containsKey(fg)) {
                val app = config.apps[fg]
                if (app != null) {
                    overlayManager.showLockScreen(fg, app.appName, config.defaultPassword,
                        app.unlockDelay, lockedApps[fg] ?: 0L)
                }
                postTick(getForeground); return
            }

            // 3. If foreground is a tracked active app, accumulate time
            val app = config.apps[fg]
            if (app != null && app.active) {
                val prevRem = app.duration - (timerAccMap[fg] ?: 0)
                if (prevRem <= 0) {
                    // already should be locked, handle in next tick
                    postTick(getForeground); return
                }
                timerAccMap[fg] = (timerAccMap[fg] ?: 0) + delta.toInt()
                stateDirty = true
                val acc = timerAccMap[fg] ?: 0
                val rem = app.duration - acc
                if (rem <= 0) {
                    // LOCK!
                    overlayManager.dismiss()
                    timerAccMap[fg] = 0
                    lockedApps[fg] = now
                    saveLockedApps(lockedApps)
                    // show lock screen immediately instead of waiting for next tick
                    overlayManager.showLockScreen(fg, app.appName, config.defaultPassword, app.unlockDelay, now)
                    postTick(getForeground)
                    return
                } else {
                    overlayManager.showCountdown(fg, rem)
                }
            } else {
                // foreground not a tracked app
                if (overlayManager.overlayType == "countdown") {
                    overlayManager.dismiss()
                }
                if (stateDirty) { saveState(); stateDirty = false }
            }

            // 4. If foreground is not locked but the overlay is a lock screen for a different app, dismiss it
            if (overlayManager.overlayType == "lock" && overlayManager.overlayForPkg != fg) {
                    overlayManager.dismiss()
            }

        } catch (e: Exception) {
            android.util.Log.e("AppLock", "tick err", e)
        }
        postTick(getForeground)
    }

    // ========== MCP Tool Handlers ==========

    fun mcpGetConfig(): String {
        val sb = StringBuilder()
        sb.appendLine(chr(0x5E94) + chr(0x7528) + chr(0x9501) + chr(0x914D) + chr(0x7F6E) + ":")
        sb.appendLine("- " + chr(0x5BC6) + chr(0x7801) + ": ****")
        sb.appendLine("- " + chr(0x5DF2) + chr(0x9501) + chr(0x5B9A) + " " + config.apps.size.toString() + " " + chr(0x4E2A) + chr(0x5E94) + chr(0x7528))
        for ((pkg, app) in config.apps) {
            val acc = timerAccMap[pkg] ?: 0
            val rem = app.duration - acc
            val status = if (rem <= 0) chr(0x5DF2) + chr(0x9501) + chr(0x5B9A) else fmtTime(rem) + " " + chr(0x540E) + chr(0x9501) + chr(0x5B9A)
            sb.appendLine("  " + app.appName + " (" + pkg + "): " + chr(0x5012) + chr(0x8BA1) + chr(0x65F6) + "=" + app.duration.toString() + "s, " + chr(0x72B6) + chr(0x6001) + "=" + status)
        }
        sb.appendLine(chr(0x5F53) + chr(0x524D) + chr(0x524D) + chr(0x53F0) + ": " + (foregroundPkg ?: chr(0x672A) + chr(0x77E5)))
        return sb.toString()
    }

    fun mcpAddApp(pkg: String, appName: String, duration: Int, unlockDelay: Int): String {
        config.apps[pkg] = LockApp(appName = appName, duration = duration, unlockDelay = unlockDelay, active = true)
        timerAccMap[pkg] = timerAccMap[pkg] ?: 0
        saveConfig()
        saveState()
        return appName + " " + chr(0x5DF2) + chr(0x6DFB) + chr(0x52A0) + " (" + duration.toString() + "s" + chr(0x5012) + chr(0x8BA1) + chr(0x65F6) + ")"
    }

    fun mcpRemoveApp(pkg: String): String {
        val name = config.apps[pkg]?.appName ?: pkg
        config.apps.remove(pkg)
        timerAccMap.remove(pkg)
        saveConfig(); saveState()
        overlayManager.dismiss()
        return name + " " + chr(0x5DF2) + chr(0x79FB) + chr(0x9664)
    }

    fun mcpSetPassword(base64Password: String): String {
        try {
            val decoded = String(android.util.Base64.decode(base64Password, android.util.Base64.DEFAULT))
            if (decoded.length == 4 && decoded.all { it.isDigit() }) {
                config.defaultPassword = decoded
                saveConfig()
            }
        } catch (_: Exception) {}
        return chr(0x5BC6) + chr(0x7801) + chr(0x5DF2) + chr(0x66F4) + chr(0x65B0)
    }

    fun mcpUnlockNow(pkg: String): String {
        val name = config.apps[pkg]?.appName ?: pkg
        doUnlock(pkg)
        return name + " " + chr(0x5DF2) + chr(0x89E3) + chr(0x9501)
    }

    private fun doUnlock(pkg: String) {
        timerAccMap.remove(pkg)
        config.apps[pkg]?.active = false
        saveConfig()
        overlayManager.dismiss()
        val lockedStateFile = File(context.filesDir, "lock_state.json")
        try {
            if (lockedStateFile.exists()) {
                val j = JSONObject(lockedStateFile.readText())
                val locked = j.optJSONObject("lockedApps") ?: JSONObject()
                locked.remove(pkg)
                j.put("lockedApps", locked)
                // also preserve timerAccMap in the save
                val acc = JSONObject()
                for ((p, sec) in timerAccMap) acc.put(p, sec)
                j.put("timerAccMap", acc)
                lockedStateFile.writeText(j.toString(2))
                return
            }
        } catch (_: Exception) {}
        saveState()
    }

    private fun saveLockedApps(locked: Map<String, Long>) {
        try {
            val j = JSONObject()
            val acc = JSONObject()
            for ((pkg, sec) in timerAccMap) acc.put(pkg, sec)
            j.put("timerAccMap", acc)
            val apps = JSONObject()
            for ((pkg, t) in locked) apps.put(pkg, t)
            j.put("lockedApps", apps)
            File(context.filesDir, "lock_state.json").writeText(j.toString(2))
        } catch (_: Exception) {}
    }

    fun mcpGetForeground(): String {
        val pkg = foregroundPkg ?: return chr(0x65E0) + chr(0x6CD5) + chr(0x83B7) + chr(0x53D6) + chr(0x524D) + chr(0x53F0) + chr(0x5E94) + chr(0x7528) + "（" + chr(0x65E0) + chr(0x969C) + chr(0x788D) + chr(0x670D) + chr(0x52A1) + chr(0x672A) + chr(0x542F) + chr(0x52A8) + "）"
        val name = try {
            val pm = context.packageManager
            pm.getApplicationLabel(pm.getApplicationInfo(pkg, 0)).toString()
        } catch (_: Exception) { pkg.split(".").last() }
        return pkg + " (" + name + ")"
    }

    private fun fmtTime(sec: Int): String {
        val s = sec.coerceAtLeast(0); val m = s / 60; val r = s % 60
        return (if (m < 10) "0" else "") + m + ":" + (if (r < 10) "0" else "") + r
    }

    private fun nowSec(): Long = System.currentTimeMillis() / 1000
    private fun chr(c: Int): String = c.toChar().toString()
}
