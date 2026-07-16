package com.lover.connect.lock

import android.content.Context
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.widget.*

class LockOverlayManager(private val context: Context) {

    private val wm: WindowManager = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager

    var overlayView: View? = null; private set
    var overlayType: String = ""; private set
    var overlayForPkg: String = ""; private set

    private var pwdInput = ""
    private var dotsViews = arrayOfNulls<TextView>(4)
    private var unlockCdText: TextView? = null
    var passwordCorrectCallback: ((String) -> Unit)? = null

    fun dismiss() {
        overlayView?.let { v -> try { wm.removeView(v) } catch(_: Exception) {} }
        overlayView = null; overlayType = ""; overlayForPkg = ""
        pwdInput = ""; dotsViews = arrayOfNulls(4); unlockCdText = null
    }

    fun showCountdown(pkg: String, remSec: Int) {
        if (overlayType == "countdown" && overlayForPkg == pkg && overlayView != null) {
            updateCountdownText(remSec); return
        }
        dismiss()
        val tv = TextView(context).apply {
            text = emoji(0xD83D, 0xDD12) + " " + fmtTime(remSec)
            setTextColor(if (remSec <= 60) Color.parseColor("#E53935") else Color.parseColor("#4A4A4A"))
            textSize = 18f; setTypeface(null, Typeface.BOLD); gravity = Gravity.CENTER
            setPadding(dp(16), dp(6), dp(16), dp(6))
        }
        val gd = GradientDrawable(); gd.setColor(Color.WHITE); gd.cornerRadius = dp(19).toFloat()
        tv.background = gd
        val params = WindowManager.LayoutParams(
            WRAP, WRAP, WMT_OVERLAY,
            NF or NT or NL,
            PixelFormat.TRANSLUCENT
        ).apply { gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL; y = dp(10) }
        overlayView = tv; overlayType = "countdown"; overlayForPkg = pkg
        wm.addView(tv, params)
    }

    fun updateCountdownText(remSec: Int): Boolean {
        val tv = overlayView.takeIf { overlayType == "countdown" && it is TextView } as? TextView ?: return false
        val c = if (remSec <= 60) "#E53935" else "#4A4A4A"
        tv.setTextColor(Color.parseColor(c)); tv.text = emoji(0xD83D, 0xDD12) + " " + fmtTime(remSec)
        return true
    }

    fun showLockScreen(pkg: String, appName: String, password: String, unlockDelay: Int, lockStartTime: Long) {
        if (overlayType == "lock" && overlayForPkg == pkg && overlayView != null) {
            if (unlockDelay > 0 && lockStartTime > 0) updateLockUnlockText(unlockDelay, lockStartTime)
            return
        }
        dismiss(); pwdInput = ""; dotsViews = arrayOfNulls(4)
        val ud = unlockDelay
        val lockRem = if (ud > 0 && lockStartTime > 0) (ud - (nowSec() - lockStartTime)).toInt().coerceAtLeast(0) else ud
        val root = FrameLayout(context).apply { setBackgroundColor(Color.parseColor("#F5F0E1")) }
        val hint = when {
            ud > 0 && lockRem > 0 -> emoji(0xD83D, 0xDD13) + " " + fmtTime(lockRem) + " " + chr(0x540E) + chr(0x81EA) + chr(0x52A8) + chr(0x89E3) + chr(0x9501)
            ud > 0 -> emoji(0xD83D, 0xDD13) + " " + chr(0x5373) + chr(0x5C06) + chr(0x81EA) + chr(0x52A8) + chr(0x89E3) + chr(0x9501) + "..."
            else -> ""
        }
        val container = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL; gravity = Gravity.CENTER
            layoutParams = FrameLayout.LayoutParams(FMP, FMP)
        }
        container.addView(Space(context).apply { layoutParams = LinearLayout.LayoutParams(FMP, 0, 1f) })
        container.addView(TextView(context).apply { text = emoji(0xD83D, 0xDD12); textSize = 48f; gravity = Gravity.CENTER })
        container.addView(TextView(context).apply {
            text = appName + " " + chr(0x5DF2) + chr(0x9501) + chr(0x5B9A)
            setTextColor(Color.parseColor("#5D4E37")); textSize = 20f; setTypeface(null, Typeface.BOLD); gravity = Gravity.CENTER
        })
        container.addView(TextView(context).apply {
            text = chr(0x8BF7) + chr(0x8F93) + chr(0x5165) + chr(0x5BC6) + chr(0x7801) + chr(0x89E3) + chr(0x9501)
            setTextColor(Color.parseColor("#B0A090")); textSize = 13f; gravity = Gravity.CENTER
        })
        if (hint.isNotEmpty()) {
            unlockCdText = TextView(context).apply {
                text = hint; setTextColor(Color.parseColor("#8B9DC3")); textSize = 14f; gravity = Gravity.CENTER
            }.also { container.addView(it) }
        }
        val dotsRow = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER
        }
        for (i in 0..3) {
            dotsViews[i] = TextView(context).apply {
                text = chr(0x25CB); textSize = 26f; setTextColor(Color.parseColor("#C0B0A0"))
                setPadding(dp(8), 0, dp(8), 0)
            }.also { dotsRow.addView(it) }
        }
        container.addView(dotsRow)
        val rows = listOf(
            listOf("1","2","3"), listOf("4","5","6"), listOf("7","8","9"),
            listOf(chr(0x6E05)+chr(0x9664),"0",chr(0x232B))
        )
        for (rowLabels in rows) {
            val row = LinearLayout(context).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER }
            for (label in rowLabels) {
                val isSpecial = (label == chr(0x6E05)+chr(0x9664) || label == chr(0x232B))
                val bg = GradientDrawable().apply {
                    setColor(if (isSpecial) Color.parseColor("#E8DFD0") else Color.WHITE); cornerRadius = dp(12).toFloat()
                }
                val btn = FrameLayout(context).apply {
                    layoutParams = LinearLayout.LayoutParams(dp(72), dp(64)).apply { setMargins(dp(4),dp(4),dp(4),dp(4)) }
                    background = bg; setOnClickListener { onKeypadClick(label, password) }
                }
                btn.addView(TextView(context).apply {
                    text = label; textSize = if (isSpecial) 16f else 24f
                    setTextColor(Color.parseColor(if (isSpecial) "#8B7355" else "#5D4E37")); gravity = Gravity.CENTER
                })
                row.addView(btn)
            }
            container.addView(row)
        }
        container.addView(Space(context).apply { layoutParams = LinearLayout.LayoutParams(FMP, 0, 1f) })
        root.addView(container)
        val params = WindowManager.LayoutParams(
            FMP, FMP, WMT_OVERLAY,
            NTM or KSO or NL,
            PixelFormat.TRANSLUCENT
        ).apply { gravity = Gravity.TOP or Gravity.LEFT }
        overlayView = root; overlayType = "lock"; overlayForPkg = pkg
        wm.addView(root, params)
    }

    private fun onKeypadClick(label: String, password: String) {
        
        val clearStr = chr(0x6E05)+chr(0x9664)
        val backStr = chr(0x232B)
        when (label) {
            clearStr -> { pwdInput = ""; updateDotsUi() }
            backStr -> { if (pwdInput.isNotEmpty()) { pwdInput = pwdInput.dropLast(1); updateDotsUi() } }
            else -> {
                if (pwdInput.length < 4) {
                    pwdInput += label; updateDotsUi()
                    if (pwdInput.length == 4) {
                        Handler(Looper.getMainLooper()).postDelayed({
                            if (pwdInput == password) { passwordCorrectCallback?.invoke(overlayForPkg) }
                            else { pwdInput = ""; updateDotsUi() }
                        }, 200)
                    }
                }
            }
        }
    }

    private fun updateDotsUi() = (0..3).forEach { i ->
        dotsViews[i]?.apply {
            text = if (i < pwdInput.length) chr(0x25CF) else chr(0x25CB)
            setTextColor(Color.parseColor(if (i < pwdInput.length) "#D4A574" else "#C0B0A0"))
        }
    }

    fun updateLockUnlockText(unlockDelay: Int, lockStartTime: Long): Boolean {
        val el = unlockCdText ?: return false
        val rem = (unlockDelay - (nowSec() - lockStartTime)).toInt().coerceAtLeast(0)
        el.text = emoji(0xD83D, 0xDD13) + " " + fmtTime(rem) + " " + chr(0x540E) + chr(0x81EA) + chr(0x52A8) + chr(0x89E3) + chr(0x9501)
        return true
    }

    private fun fmtTime(sec: Int): String {
        val s = sec.coerceAtLeast(0); val m = s / 60; val r = s % 60
        return (if (m < 10) "0" else "") + m.toString() + ":" + (if (r < 10) "0" else "") + r.toString()
    }

    private fun dp(v: Int): Int = (v * context.resources.displayMetrics.density + 0.5f).toInt()
    private fun nowSec(): Long = System.currentTimeMillis() / 1000
    private fun chr(c: Int): String = c.toChar().toString()
    private fun emoji(h: Int, l: Int): String = String(intArrayOf(h, l), 0, 2)

    companion object {
        private const val WRAP = WindowManager.LayoutParams.WRAP_CONTENT
        private const val FMP = WindowManager.LayoutParams.MATCH_PARENT
        private const val NF = WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
        private const val NT = WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE
        private const val NL = WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS
        private const val NTM = WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL
        private const val KSO = WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON
        private val WMT_OVERLAY = WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
    }
}
