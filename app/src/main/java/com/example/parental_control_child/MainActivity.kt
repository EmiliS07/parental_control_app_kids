package com.example.parental_control_child

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import android.Manifest

class MainActivity : AppCompatActivity() {
    private val locationPermissionCode = 100


    // onCreate: Cuando se crea la actividad (primera vez que se abre)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Concecta con el diseño de la app (layout)
        setContentView(R.layout.activity_main)

        // Pedir los permisos de ubicacion
        ActivityCompat.requestPermissions(
            this,
            arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION),
            locationPermissionCode
        )
    }
}