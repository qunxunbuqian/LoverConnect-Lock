package com.lover.connect

import android.accessibilityservice.AccessibilityService
import android.graphics.Bitmap
import android.os.Build
import android.util.Base64
import android.view.Display
import android.view.accessibility.AccessibilityEvent
import java.io.ByteArrayOutputStream
import java.util.concurrent.Executors

class LCAccessibilityService : AccessibilityService() {

    companion object {
        var instance: LCAccessibilityService? = null
        var currentForegroundPackage: String? = null
            internal set
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        event ?: return
        if (event.eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) {
            event.packageName?.toString()?.let { pkg ->
                if (pkg != "com.lover.connect" && !pkg.startsWith("com.android.systemui")) {
                    currentForegroundPackage = pkg
                }
            }
        }
    }

    override fun onInterrupt() {}

    override fun onDestroy() {
        super.onDestroy()
        instance = null
        currentForegroundPackage = null
    }

    fun takeScreenshotNow(callback: (String?) -> Unit) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) {
            callback(null)
            return
        }
        takeScreenshot(Display.DEFAULT_DISPLAY, Executors.newSingleThreadExecutor(),
            object : TakeScreenshotCallback {
                override fun onSuccess(screenshot: ScreenshotResult) {
                    try {
                        val hardwareBuffer = screenshot.hardwareBuffer
                        val colorSpace = screenshot.colorSpace
                        val bitmap = Bitmap.wrapHardwareBuffer(hardwareBuffer, colorSpace)
                        hardwareBuffer.close()
                        if (bitmap != null) {
                            val softBitmap = bitmap.copy(Bitmap.Config.ARGB_8888, false)
                            bitmap.recycle()
                            val stream = ByteArrayOutputStream()
                            softBitmap.compress(Bitmap.CompressFormat.JPEG, 60, stream)
                            softBitmap.recycle()
                            val base64 = Base64.encodeToString(stream.toByteArray(), Base64.NO_WRAP)
                            callback(base64)
                        } else { callback(null) }
                    } catch (_: Exception) { callback(null) }
                }
                override fun onFailure(errorCode: Int) { callback(null) }
            })
    }
}
