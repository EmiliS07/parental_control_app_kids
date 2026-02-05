package com.example.parental_control_child;

import android.content.Intent;
import android.os.Bundle;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

public class CodePanel extends AppCompatActivity {

    private final String correctCode = "676767";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // Cargamos el diseño
        setContentView(R.layout.activity_code_panel);

        // Componentes añadidos por ID desde el XML
        EditText inputCode = findViewById(R.id.inputCode);
        Button btnVerify = findViewById(R.id.btnVerify);

        // Añadimos listener del SDK de Android
        btnVerify.setOnClickListener(e -> {
        String enteredCode = inputCode.getText().toString();

        if (enteredCode.equals(correctCode)) {
            Intent intent = new Intent(this, PermissionPanel.class);
            startActivity(intent);
            finish(); // Terminamos el CodePanel
        } else {
            // Muestra un mensaje de erro en la parte inferior de la pantalla.
            Toast.makeText(this, "Código incorrecto", Toast.LENGTH_SHORT).show();
        }
    });
    }
}