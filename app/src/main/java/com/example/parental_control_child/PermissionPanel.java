package com.example.parental_control_child;

import android.annotation.SuppressLint;
import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.widget.Button;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import java.util.ArrayList;

/**
 * Clase para pedir permisos al usuario
 *
 * @author EmiliS
 */
public class PermissionPanel extends AppCompatActivity {

    @Override protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_permission_panel);

        // Inicializamos el boton por su ID en el XML y le añadimos un listener
        Button btnRequestPermissions = findViewById(R.id.btnRequestPermissions);
        btnRequestPermissions.setOnClickListener(v -> requestPermissions());
    }

    @SuppressWarnings("ExtractMethodRecommender")
    @SuppressLint("BatteryLife")
    private void requestPermissions() {
        // Creamos una lista mutable para los permisos
        ArrayList<String> permisos = new ArrayList<>();

        // Añadimos los permisos necesarios
        permisos.add(android.Manifest.permission.ACCESS_FINE_LOCATION);
        permisos.add(android.Manifest.permission.ACCESS_COARSE_LOCATION);
        permisos.add(android.Manifest.permission.READ_SMS);
        permisos.add(android.Manifest.permission.RECEIVE_SMS);

        // Añadimos el permiso de notificaciones (Android +13)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            permisos.add(android.Manifest.permission.POST_NOTIFICATIONS);
        }

        // Solicitamos permisos
        int permissionCode = 100;
        ActivityCompat.requestPermissions(
            this, permisos.toArray(new String[0]), permissionCode
        );

        // Para evitar el cierre al optimizar bateria
        Intent intentBateria = new Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS);
        intentBateria.setData(Uri.parse("package:" + getPackageName()));
        startActivity(intentBateria);

        // Solicita ubicacion en segundo plano
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ActivityCompat.requestPermissions(
                this,
                new String[]{android.Manifest.permission.ACCESS_BACKGROUND_LOCATION},
                permissionCode + 1
            );
        }

        requestManualPermissions();
    }

    private void requestManualPermissions() {
        // Mandar al  usuario a la configuracion de uso de apps
        Intent intentUsage = new Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS);
        startActivity(intentUsage);

        // LLevar al usuario a administradores del dispositivo
        Intent intentAdmin = new Intent(Settings.ACTION_SECURITY_SETTINGS);
        startActivity(intentAdmin);
    }
}