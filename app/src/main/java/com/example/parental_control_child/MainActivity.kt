package com.example.parental_control_child

import android.annotation.SuppressLint
import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import android.os.Build
import android.provider.Settings
import androidx.core.net.toUri

class MainActivity : AppCompatActivity() {
    private val permissionCode = 100

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        // Permisos basicos
        val permisos = mutableListOf(
            android.Manifest.permission.ACCESS_FINE_LOCATION,
            android.Manifest.permission.ACCESS_COARSE_LOCATION
        )

        // Permisos de notificaciones (Andrdoid +13)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            permisos.add(android.Manifest.permission.POST_NOTIFICATIONS)
        }

        // Requerir permisos
        ActivityCompat.requestPermissions(
            this,
            permisos.toTypedArray(),
            permissionCode
        )

        @SuppressLint("BatteryLife")
        val intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS)
        intent.data = "package:$packageName".toUri()
        startActivity(intent)

        // Requerir ubicacion en segundo plano
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ActivityCompat.requestPermissions(
                this,
                arrayOf(android.Manifest.permission.ACCESS_BACKGROUND_LOCATION),
                permissionCode + 1
            )
        }
    }
}