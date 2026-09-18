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
            checkAllPermissionsAndStart()
        }
    }

    private fun checkAllPermissionsAndStart() {
        val permissionsNeeded = mutableListOf<String>()
        
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                permissionsNeeded.add(Manifest.permission.POST_NOTIFICATIONS)
            }
        }
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
            permissionsNeeded.add(Manifest.permission.CAMERA)
        }

        if (permissionsNeeded.isNotEmpty()) {
            ActivityCompat.requestPermissions(this, permissionsNeeded.toTypedArray(), 101)
        } else {
            startMotoService()
        }
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == 101) {
            startMotoService()
        }
    }

    private fun startMotoService() {
        val intent = Intent(this, GestureForegroundService::class.java)
        ContextCompat.startForegroundService(this, intent)
        statusTextView.text = "Status: Moto Actions Active"
    }
}
