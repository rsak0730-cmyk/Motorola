package com.example.gestureflow

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.Spinner
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat

class MainActivity : AppCompatActivity() {

    private lateinit var statusTextView: TextView
    private lateinit var spinnerChop: Spinner
    private lateinit var spinnerTwist: Spinner
    private lateinit var spinnerFlip: Spinner
    private lateinit var spinnerShake: Spinner

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

        startButton.setOnClickListener {
            prefs.edit().apply {
                putInt("chop", spinnerChop.selectedItemPosition)
                putInt("twist", spinnerTwist.selectedItemPosition)
                putInt("flip", spinnerFlip.selectedItemPosition)
                putInt("shake", spinnerShake.selectedItemPosition)
                apply()
            }
            requestPermissionsAndStart()
        }
    }

    private fun requestPermissionsAndStart() {
        val permissions = mutableListOf(Manifest.permission.CAMERA)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            permissions.add(Manifest.permission.POST_NOTIFICATIONS)
        }

        val missing = permissions.any {
            ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }

        if (missing) {
            ActivityCompat.requestPermissions(this, permissions.toTypedArray(), 101)
        } else {
            startServiceSafely()
        }
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        startServiceSafely()
    }

    private fun startServiceSafely() {
        try {
            val intent = Intent(this, GestureForegroundService::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                startForegroundService(intent)
            } else {
                startService(intent)
            }
            statusTextView.text = "Status: Moto Actions Active"
        } catch (e: Exception) {
            statusTextView.text = "Status: Error starting service"
        }
    }
}
