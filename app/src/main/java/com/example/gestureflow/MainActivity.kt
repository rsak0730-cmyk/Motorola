package com.example.gestureflow

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.hardware.camera2.CameraManager
import android.os.Build
import android.os.Bundle
import android.provider.MediaStore
import android.content.Intent
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.Spinner
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import kotlin.math.abs
import kotlin.math.sqrt

class MainActivity : AppCompatActivity(), SensorEventListener {

    private lateinit var statusTextView: TextView
    private lateinit var spinnerChop: Spinner
    private lateinit var spinnerTwist: Spinner
    private lateinit var spinnerFlip: Spinner
    private lateinit var spinnerShake: Spinner

    private lateinit var sensorManager: SensorManager
    private var accelerometer: Sensor? = null
    private var cameraManager: CameraManager? = null
    private var cameraId: String? = null
    private var isListening = false
    private var isFlashOn = false
    private var lastTrigger: Long = 0

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        statusTextView = findViewById(R.id.statusTextView)
        spinnerChop = findViewById(R.id.spinnerChop)
        spinnerTwist = findViewById(R.id.spinnerTwist)
        spinnerFlip = findViewById(R.id.spinnerFlip)
        spinnerShake = findViewById(R.id.spinnerShake)
        val startButton = findViewById<Button>(R.id.startButton)

        val actions = arrayOf("Enable Feature", "Disabled")
        val adapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, actions)

        spinnerChop.adapter = adapter
        spinnerTwist.adapter = adapter
        spinnerFlip.adapter = adapter
        spinnerShake.adapter = adapter

        val prefs = getSharedPreferences("MotoPrefs", Context.MODE_PRIVATE)
        spinnerChop.setSelection(prefs.getInt("chop", 0))
        spinnerTwist.setSelection(prefs.getInt("twist", 0))
        spinnerFlip.setSelection(prefs.getInt("flip", 0))
        spinnerShake.setSelection(prefs.getInt("shake", 0))

        sensorManager = getSystemService(Context.SENSOR_SERVICE) as SensorManager
        accelerometer = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
        
        try {
            cameraManager = getSystemService(Context.CAMERA_SERVICE) as CameraManager
            cameraId = cameraManager?.cameraIdList?.getOrNull(0)
        } catch (e: Exception) {
            e.printStackTrace()
        }

        startButton.setOnClickListener {
            prefs.edit().apply {
                putInt("chop", spinnerChop.selectedItemPosition)
                putInt("twist", spinnerTwist.selectedItemPosition)
                putInt("flip", spinnerFlip.selectedItemPosition)
                putInt("shake", spinnerShake.selectedItemPosition)
                apply()
            }
            checkPermissionsAndToggle()
        }
    }

    private fun checkPermissionsAndToggle() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.CAMERA), 101)
            return
        }
        toggleListening()
    }

    private fun toggleListening() {
        isListening = !isListening
        if (isListening) {
            accelerometer?.let {
                sensorManager.registerListener(this, it, SensorManager.SENSOR_DELAY_GAME)
            }
            statusTextView.text = "Status: Moto Actions Active & Listening"
            Toast.makeText(this, "Gestures Enabled!", Toast.LENGTH_SHORT).show()
        } else {
            sensorManager.unregisterListener(this)
            statusTextView.text = "Status: Service Stopped"
            Toast.makeText(this, "Gestures Paused", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onSensorChanged(event: SensorEvent?) {
        if (!isListening) return
        if (event?.sensor?.type == Sensor.TYPE_ACCELEROMETER) {
            val now = System.currentTimeMillis()
            if (now - lastTrigger < 1500) return

            val x = event.values[0]
            val y = event.values[1]
            val z = event.values[2]
            val gForce = sqrt((x * x + y * y + z * z).toDouble()) / 9.81

            val prefs = getSharedPreferences("MotoPrefs", Context.MODE_PRIVATE)

            if (gForce > 2.7 && prefs.getInt("chop", 0) == 0) {
                lastTrigger = now
                toggleFlashlight()
            } else if (abs(x) > 8.5 && abs(y) < 3.0 && prefs.getInt("twist", 0) == 0) {
                lastTrigger = now
                openCamera()
            } else if (z < -8.5 && prefs.getInt("flip", 0) == 0) {
                lastTrigger = now
                Toast.makeText(this, "Phone placed face down", Toast.LENGTH_SHORT).show()
            } else if (gForce > 2.0 && abs(z) < 3.0 && prefs.getInt("shake", 0) == 0) {
                lastTrigger = now
                openApp("com.whatsapp")
            }
        }
    }

    private fun toggleFlashlight() {
        try {
            cameraId?.let { id ->
                isFlashOn = !isFlashOn
                cameraManager?.setTorchMode(id, isFlashOn)
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun openCamera() {
        try {
            val intent = Intent(MediaStore.ACTION_IMAGE_CAPTURE).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            startActivity(intent)
        } catch (e: Exception) {
            Toast.makeText(this, "Camera launch failed", Toast.LENGTH_SHORT).show()
        }
    }

    private fun openApp(pkg: String) {
        try {
            val intent = packageManager.getLaunchIntentForPackage(pkg)
            if (intent != null) {
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                startActivity(intent)
            } else {
                Toast.makeText(this, "WhatsApp not installed", Toast.LENGTH_SHORT).show()
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}

    override fun onDestroy() {
        super.onDestroy()
        sensorManager.unregisterListener(this)
        try {
            cameraId?.let { cameraManager?.setTorchMode(it, false) }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}
