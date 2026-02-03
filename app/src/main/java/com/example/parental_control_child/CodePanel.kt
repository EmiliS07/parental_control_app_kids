package com.example.parental_control_child

import android.content.Intent
import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity

class CodePanel : AppCompatActivity() {

    private var correctCode = "6767"

    override fun onCreate(savedInstance: Bundle?) {
        super.onCreate(savedInstance)

        // Cargamos el diseño
        setContentView(R.layout.activity_code_panel)


        val inputCode = findViewById<EditText>(R.id.inputCode)
        val btnVerify = findViewById<Button>(R.id.btnVerify)


        btnVerify.setOnClickListener {
            val enteredCode = inputCode.text.toString()

            if (enteredCode == correctCode) {
                val intent = Intent(this, PermissionPanel::class.java)
                startActivity(intent)
            } else {
                Toast.makeText(this, "Codigo correcto", Toast.LENGTH_SHORT).show()
            }
        }
    }

}