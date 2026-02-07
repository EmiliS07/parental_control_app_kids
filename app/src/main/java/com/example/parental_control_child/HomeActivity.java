package com.example.parental_control_child;

import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

public class HomeActivity extends AppCompatActivity {

    private SharedPreferences prefs;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        prefs = getSharedPreferences("ParentalControl", MODE_PRIVATE);

        // Verificar si tiene todos los permisos
        if (!hasRequiredPermissions()) {
            goToPermissionSetup();
            return;
        }

        // Si tiene permisos, mostrar el launcher
        setContentView(R.layout.activity_home);
        initializeLauncher();
    }

    @Override
    protected void onResume() {
        super.onResume();
        // Verificar permisos cada vez que vuelve a esta pantalla
        if (!hasRequiredPermissions()) {
            goToPermissionSetup();
        }
    }

    @SuppressWarnings("BooleanMethodIsAlwaysInverted")
    private boolean hasRequiredPermissions() {
        // Verificar si ya completó el setup de permisos
        return prefs.getBoolean("permissionsGranted", false);
    }

    private void goToPermissionSetup() {
        Intent intent = new Intent(this, PermissionSetup.class);
        startActivity(intent);
        finish();
    }

    private void initializeLauncher() {
        TextView tvWelcome = findViewById(R.id.tvWelcome);
        tvWelcome.setText("✅ Dispositivo activo\n\n🏠 Launcher en construcción...");

        // TODO: Aquí irá la lógica del launcher
        // - Cargar apps instaladas
        // - Mostrar grid personalizado
        // - Recibir comandos del servidor
    }
}