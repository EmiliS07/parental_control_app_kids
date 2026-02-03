package com.example.parental_control_child

import android.annotation.SuppressLint
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.widget.Button
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.net.toUri

class PermissionPanel : AppCompatActivity() {
    private val permissionCode = 100

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_permission_panel)

        val btnRequestPermissions = findViewById<Button>(R.id.btnRequestPermissions)

        btnRequestPermissions.setOnClickListener {
            startPermissions()
        }
    }

    private fun startPermissions() {
        // Permisos basicos
        val permisos = mutableListOf(
            android.Manifest.permission.ACCESS_FINE_LOCATION,
            android.Manifest.permission.ACCESS_COARSE_LOCATION,
            android.Manifest.permission.READ_SMS,
            android.Manifest.permission.RECEIVE_SMS
        )

        // Permisos de notificaciones (Android +13)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            permisos.add(android.Manifest.permission.POST_NOTIFICATIONS)
        }

        // Requerir permisos
        ActivityCompat.requestPermissions(
            this,
            permisos.toTypedArray(),
            permissionCode
        )

        // Optimizacion de bateria
        @SuppressLint("BatteryLife")
        val intentBateria = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS)
        intentBateria.data = "package:$packageName".toUri()
        startActivity(intentBateria)

        // Requerir ubicacion en segundo plano
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ActivityCompat.requestPermissions(
                this,
                arrayOf(android.Manifest.permission.ACCESS_BACKGROUND_LOCATION),
                permissionCode + 1
            )
        }

        // Llevar a configuracion de uso de apps
        val intentUsage = Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS)
        startActivity(intentUsage)

        // Llevar a administradores del dispositivo
        val intentAdmin = Intent(Settings.ACTION_SECURITY_SETTINGS)
        startActivity(intentAdmin)
    }
}