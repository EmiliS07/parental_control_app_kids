package com.example.parental_control_child;

import android.content.Intent;
import android.os.Bundle;
import androidx.appcompat.app.AppCompatActivity;

public class AppLauncher extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // Ejecutamos el panel principal
        Intent intent = new Intent(this, CodePanel.class);
        startActivity(intent);
        finish();
    }
}