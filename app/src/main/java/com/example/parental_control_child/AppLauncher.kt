package com.example.parental_control_child

import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity

class AppLauncher : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val intent = Intent(this, CodePanel::class.java)
        startActivity(intent)
    }
}