package com.example.parental_control_child

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity

class MainActivity : AppCompatActivity() {

    // onCreate: Cuando se crea la actividad (primera vez que se abre)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Concecta con el diseño de la app (layout)
        setContentView(R.layout.activity_main)
    }
}