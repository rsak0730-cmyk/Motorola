package com.universalgestures.accessibility

import android.accessibilityservice.AccessibilityService
import android.content.ContentValues
import android.graphics.Bitmap
import android.os.Build
import android.provider.MediaStore
import android.view.accessibility.AccessibilityEvent
import com.universalgestures.core.ActionType
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

/** Explicit opt-in service for platform actions unavailable to ordinary apps. */
class GestureAccessibilityService : AccessibilityService() {

    override fun onServiceConnected() {
        super.onServiceConnected()
        activeService = this
    }

    override fun onDestroy() {
        if (activeService === this) activeService = null
        super.onDestroy()
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        // Intentionally do not inspect window contents or input events.
    }

    override fun onInterrupt() = Unit

    private fun perform(action: ActionType): Boolean = when (action) {
        ActionType.LOCK_SCREEN -> performGlobalAction(GLOBAL_ACTION_LOCK_SCREEN)
        ActionType.OPEN_NOTIFICATIONS -> performGlobalAction(GLOBAL_ACTION_NOTIFICATIONS)
        ActionType.SCREENSHOT -> saveScreenshot()
        else -> false
    }

    private fun saveScreenshot(): Boolean {
        // SAFETY: Android only exposes this API to an explicitly enabled accessibility service.
        // Scoped MediaStore access saves to Pictures/Universal Gestures; no app-private upload.
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) return false
        return try {
            takeScreenshot(
                android.view.Display.DEFAULT_DISPLAY,
                screenshotExecutor,
                object : TakeScreenshotCallback {
                    override fun onSuccess(screenshot: ScreenshotResult) {
                        var bitmap: Bitmap? = null
                        var uri: android.net.Uri? = null
                        try {
                            val captured = Bitmap.wrapHardwareBuffer(screenshot.hardwareBuffer, screenshot.colorSpace)
                                ?.copy(Bitmap.Config.ARGB_8888, false)
                                ?: return
                            bitmap = captured
                            val values = ContentValues().apply {
                                put(MediaStore.Images.Media.DISPLAY_NAME, "UniversalGestures_${System.currentTimeMillis()}.png")
                                put(MediaStore.Images.Media.MIME_TYPE, "image/png")
                                put(MediaStore.Images.Media.RELATIVE_PATH, "Pictures/Universal Gestures")
                                put(MediaStore.Images.Media.IS_PENDING, 1)
                            }
                            uri = contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)
                            val target = uri ?: return
                            contentResolver.openOutputStream(target)?.use { output ->
                                check(captured.compress(Bitmap.CompressFormat.PNG, 100, output))
                            } ?: error("Unable to open screenshot output")
                            contentResolver.update(target, ContentValues().apply {
                                put(MediaStore.Images.Media.IS_PENDING, 0)
                            }, null, null)
                        } catch (_: Exception) {
                            uri?.let { contentResolver.delete(it, null, null) }
                        } finally {
                            bitmap?.recycle()
                            screenshot.hardwareBuffer.close()
                        }
                    }

                    override fun onFailure(errorCode: Int) = Unit
                }
            )
            true
        } catch (_: Exception) {
            false
        }
    }

    companion object {
        @Volatile private var activeService: GestureAccessibilityService? = null
        private val screenshotExecutor: ExecutorService = Executors.newSingleThreadExecutor()

        /** Returns null when the user has not explicitly enabled the service. */
        fun performIfEnabled(action: ActionType): Boolean? = activeService?.perform(action)
    }
}
