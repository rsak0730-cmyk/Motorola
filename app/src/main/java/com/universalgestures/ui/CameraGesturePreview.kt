package com.universalgestures.ui

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.ImageFormat
import android.hardware.camera2.CameraCaptureSession
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraDevice
import android.hardware.camera2.CameraManager
import android.hardware.camera2.CaptureRequest
import android.media.Image
import android.media.ImageReader
import android.os.Handler
import android.os.HandlerThread
import android.os.Looper
import android.view.Surface
import android.view.TextureView
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import kotlin.math.abs

/** Foreground-only camera motion activity detector. Does not save or transmit frames. */
@Composable
fun ForegroundHandMotionPreview(onMotion: (String) -> Unit) {
    val context = LocalContext.current
    val texture = androidx.compose.runtime.remember { TextureView(context) }
    DisposableEffect(texture) {
        val controller = CameraMotionController(context, texture) { message ->
            Handler(Looper.getMainLooper()).post { onMotion(message) }
        }
        controller.start()
        onDispose { controller.stop() }
    }
    AndroidView(
        factory = { texture },
        modifier = Modifier.fillMaxWidth().height(260.dp)
    )
    Text(
        "Foreground camera motion only. Frames are analyzed in memory and discarded; this detects movement, not hand identity or pose.",
        style = MaterialTheme.typography.bodySmall
    )
}

private class CameraMotionController(
    private val context: Context,
    private val texture: TextureView,
    private val onMotion: (String) -> Unit
) {
    private val cameraManager = context.getSystemService(Context.CAMERA_SERVICE) as CameraManager
    private var thread: HandlerThread? = null
    private var handler: Handler? = null
    private var camera: CameraDevice? = null
    private var session: CameraCaptureSession? = null
    private var reader: ImageReader? = null
    private var previous: FloatArray? = null
    private var lastDetectionMs = 0L

    fun start() {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
            onMotion("Camera permission not granted.")
            return
        }
        thread = HandlerThread("UG-CameraMotion").also { it.start() }
        handler = Handler(thread!!.looper)
        if (texture.isAvailable) openCamera() else texture.surfaceTextureListener = object : TextureView.SurfaceTextureListener {
            override fun onSurfaceTextureAvailable(surface: android.graphics.SurfaceTexture, width: Int, height: Int) = openCamera()
            override fun onSurfaceTextureSizeChanged(surface: android.graphics.SurfaceTexture, width: Int, height: Int) = Unit
            override fun onSurfaceTextureDestroyed(surface: android.graphics.SurfaceTexture): Boolean = true
            override fun onSurfaceTextureUpdated(surface: android.graphics.SurfaceTexture) = Unit
        }
    }

    private fun openCamera() {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) return
        try {
            val id = cameraManager.cameraIdList.firstOrNull { cameraManager.getCameraCharacteristics(it).get(CameraCharacteristics.LENS_FACING) == CameraCharacteristics.LENS_FACING_BACK }
                ?: cameraManager.cameraIdList.firstOrNull()
                ?: run { onMotion("No camera is available."); return }
            val map = cameraManager.getCameraCharacteristics(id).get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP)
            val size = map?.getOutputSizes(ImageFormat.YUV_420_888)
                ?.filter { it.width <= 640 && it.height <= 480 }
                ?.maxByOrNull { it.width * it.height }
                ?: android.util.Size(320, 240)
            reader = ImageReader.newInstance(size.width, size.height, ImageFormat.YUV_420_888, 2).also { imageReader ->
                imageReader.setOnImageAvailableListener({ source -> source.acquireLatestImage()?.use(::analyze) }, handler)
            }
            cameraManager.openCamera(id, object : CameraDevice.StateCallback() {
                override fun onOpened(device: CameraDevice) {
                    camera = device
                    createSession(device, size.width, size.height)
                }
                override fun onDisconnected(device: CameraDevice) { device.close(); camera = null }
                override fun onError(device: CameraDevice, error: Int) { device.close(); camera = null; onMotion("Camera error: $error") }
            }, handler)
        } catch (e: SecurityException) {
            onMotion("Camera permission is required.")
        } catch (e: Exception) {
            onMotion("Camera unavailable: ${e.message ?: e.javaClass.simpleName}")
        }
    }

    private fun createSession(device: CameraDevice, width: Int, height: Int) {
        try {
            val surfaceTexture = texture.surfaceTexture ?: return
            surfaceTexture.setDefaultBufferSize(width, height)
            val previewSurface = Surface(surfaceTexture)
            val imageSurface = reader?.surface ?: return
            device.createCaptureSession(listOf(previewSurface, imageSurface), object : CameraCaptureSession.StateCallback() {
                override fun onConfigured(configured: CameraCaptureSession) {
                    session = configured
                    val request = device.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW).apply {
                        addTarget(previewSurface)
                        addTarget(imageSurface)
                        set(CaptureRequest.CONTROL_AF_MODE, CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_VIDEO)
                    }.build()
                    configured.setRepeatingRequest(request, null, handler)
                }
                override fun onConfigureFailed(session: CameraCaptureSession) { onMotion("Camera preview configuration failed.") }
            }, handler)
        } catch (e: Exception) { onMotion("Camera session failed: ${e.message}") }
    }

    private fun analyze(image: Image) {
        val plane = image.planes.firstOrNull() ?: return
        val buffer = plane.buffer
        val width = image.width
        val height = image.height
        val rowStride = plane.rowStride
        val pixelStride = plane.pixelStride
        val gridW = 16
        val gridH = 12
        val grid = FloatArray(gridW * gridH)
        var index = 0
        for (gy in 0 until gridH) {
            val y = ((gy + 1) * height / (gridH + 1)).coerceIn(0, height - 1)
            for (gx in 0 until gridW) {
                val x = ((gx + 1) * width / (gridW + 1)).coerceIn(0, width - 1)
                val byteIndex = (y * rowStride + x * pixelStride).coerceIn(0, buffer.limit() - 1)
                grid[index++] = buffer.get(byteIndex).toInt().and(0xff).toFloat()
            }
        }
        val old = previous
        previous = grid
        if (old != null) {
            var total = 0f
            var weightedX = 0f
            for (i in grid.indices) {
                val delta = abs(grid[i] - old[i])
                total += delta
                weightedX += delta * (i % gridW)
            }
            val averageDifference = total / grid.size
            val now = System.currentTimeMillis()
            if (averageDifference > 18f && now - lastDetectionMs > 900L) {
                lastDetectionMs = now
                val centerX = weightedX / total.coerceAtLeast(1f)
                val direction = if (centerX < gridW * 0.45f) "leftward" else if (centerX > gridW * 0.55f) "rightward" else "near-center"
                onMotion("Motion detected ($direction; change=${averageDifference.toInt()}). This is motion activity, not hand-shape recognition.")
            }
        }
    }

    fun stop() {
        try { session?.stopRepeating() } catch (_: Exception) { }
        try { session?.close() } catch (_: Exception) { }
        try { camera?.close() } catch (_: Exception) { }
        try { reader?.close() } catch (_: Exception) { }
        session = null; camera = null; reader = null
        thread?.quitSafely()
        thread = null; handler = null; previous = null
    }
}
