package com.universalgestures.actions

import android.Manifest
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import android.media.AudioManager
import android.os.Build
import android.provider.MediaStore
import android.provider.Settings
import android.view.KeyEvent
import androidx.core.content.ContextCompat
import com.universalgestures.accessibility.GestureAccessibilityService
import com.universalgestures.core.ActionResult
import com.universalgestures.core.ActionType

/** Public-API-only action engine with explicit permission/capability checks. */
class ActionExecutor(private val context: Context) {

    private var flashOn = false

    fun execute(type: ActionType, target: String = ""): ActionResult = try {
        when (type) {
            ActionType.NONE -> ActionResult(false, "No action assigned")
            ActionType.FLASHLIGHT_TOGGLE -> toggleFlashlight()
            ActionType.OPEN_CAMERA -> openCamera()
            ActionType.OPEN_APP -> openApp(target)
            ActionType.OPEN_SHORTCUT, ActionType.CUSTOM_SHORTCUT -> ActionResult(
                false,
                "Android does not provide third-party apps a public API to invoke another app's pinned shortcut. Use Launch application instead."
            )
            ActionType.OPEN_SETTINGS -> openSettings()
            ActionType.MEDIA_PLAY_PAUSE -> mediaKey(KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE)
            ActionType.MEDIA_NEXT -> mediaKey(KeyEvent.KEYCODE_MEDIA_NEXT)
            ActionType.MEDIA_PREVIOUS -> mediaKey(KeyEvent.KEYCODE_MEDIA_PREVIOUS)
            ActionType.VOLUME_UP -> adjustVolume(AudioManager.ADJUST_RAISE)
            ActionType.VOLUME_DOWN -> adjustVolume(AudioManager.ADJUST_LOWER)
            ActionType.SCREENSHOT -> accessibilityAction(type, "Screenshot")
            ActionType.LOCK_SCREEN -> accessibilityAction(type, "Lock screen")
            ActionType.OPEN_NOTIFICATIONS -> accessibilityAction(type, "Open notifications")
            ActionType.DND_TOGGLE -> toggleDnd()
            ActionType.RINGER_TOGGLE -> toggleRinger()
        }
    } catch (e: Exception) {
        ActionResult(false, "Action failed: ${e.message ?: e.javaClass.simpleName}")
    }

    private fun accessibilityAction(type: ActionType, label: String): ActionResult {
        val result = GestureAccessibilityService.performIfEnabled(type)
            ?: return ActionResult(false, "$label requires the accessibility service to be explicitly enabled in system settings.")
        return if (result) ActionResult(true, "$label requested") else ActionResult(false, "$label was unavailable or rejected by Android")
    }

    private fun toggleFlashlight(): ActionResult {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
            return ActionResult(false, "Camera permission is needed to control the flashlight. Grant it from Settings after choosing this action.")
        }
        val cameraManager = context.getSystemService(Context.CAMERA_SERVICE) as? CameraManager
            ?: return ActionResult(false, "Camera service unavailable")
        val cameraId = cameraManager.cameraIdList.firstOrNull { id ->
            cameraManager.getCameraCharacteristics(id).get(CameraCharacteristics.FLASH_INFO_AVAILABLE) == true
        } ?: return ActionResult(false, "No flash-capable camera on this device")
        flashOn = !flashOn
        cameraManager.setTorchMode(cameraId, flashOn)
        return ActionResult(true, if (flashOn) "Flashlight on" else "Flashlight off")
    }

    private fun openCamera(): ActionResult {
        val intent = Intent(MediaStore.ACTION_IMAGE_CAPTURE).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        if (intent.resolveActivity(context.packageManager) == null) return ActionResult(false, "No camera app is available")
        context.startActivity(intent)
        return ActionResult(true, "Camera opened")
    }

    private fun openApp(packageName: String): ActionResult {
        if (packageName.isBlank()) return ActionResult(false, "No application selected for this gesture")
        val intent = context.packageManager.getLaunchIntentForPackage(packageName)
            ?: return ActionResult(false, "Application not installed or not launchable: $packageName")
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(intent)
        return ActionResult(true, "Opened $packageName")
    }

    private fun openSettings(): ActionResult {
        context.startActivity(Intent(Settings.ACTION_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        return ActionResult(true, "Settings opened")
    }

    private fun mediaKey(keyCode: Int): ActionResult {
        val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager
            ?: return ActionResult(false, "Audio service unavailable")
        audioManager.dispatchMediaKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, keyCode))
        audioManager.dispatchMediaKeyEvent(KeyEvent(KeyEvent.ACTION_UP, keyCode))
        return ActionResult(true, "Media key dispatched")
    }

    private fun adjustVolume(direction: Int): ActionResult {
        val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager
            ?: return ActionResult(false, "Audio service unavailable")
        audioManager.adjustStreamVolume(AudioManager.STREAM_MUSIC, direction, 0)
        return ActionResult(true, "Volume adjusted")
    }

    private fun toggleDnd(): ActionResult {
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
            ?: return ActionResult(false, "Notification service unavailable")
        if (!manager.isNotificationPolicyAccessGranted) {
            return ActionResult(false, "Do Not Disturb requires notification policy access. Grant it in system settings.")
        }
        val turningOn = manager.currentInterruptionFilter == NotificationManager.INTERRUPTION_FILTER_ALL
        manager.setInterruptionFilter(
            if (turningOn) NotificationManager.INTERRUPTION_FILTER_PRIORITY
            else NotificationManager.INTERRUPTION_FILTER_ALL
        )
        return ActionResult(true, if (turningOn) "Do Not Disturb on" else "Do Not Disturb off")
    }

    private fun toggleRinger(): ActionResult {
        val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager
            ?: return ActionResult(false, "Audio service unavailable")
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && manager?.isNotificationPolicyAccessGranted != true) {
            return ActionResult(false, "Ringer mode changes require notification policy access. Grant it in system settings.")
        }
        val next = if (audioManager.ringerMode == AudioManager.RINGER_MODE_NORMAL) {
            AudioManager.RINGER_MODE_VIBRATE
        } else AudioManager.RINGER_MODE_NORMAL
        audioManager.ringerMode = next
        return ActionResult(true, "Ringer mode changed")
    }
}
