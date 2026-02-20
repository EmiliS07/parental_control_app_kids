package com.example.parental_control_child;

import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Build;
import android.os.Bundle;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

public class HomeActivity extends AppCompatActivity {

    private SharedPreferences prefs;
    private TextView tvLinkCode;
    private TextView tvDeviceInfo;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        prefs = getSharedPreferences("ParentalControl", MODE_PRIVATE);

        // Verificar si tiene todos los permisos
        if (!hasRequiredPermissions()) {
            goToPermissionSetup();
            return;
        }

        // Iniciar el servicio de monitoreo para asegurar que esté corriendo
        startMonitoringService();

        // Mostrar pantalla de configuración completa
        setContentView(R.layout.activity_home);
        initializeViews();
    }

    private void startMonitoringService() {
        Intent serviceIntent = new Intent(this, MonitoringService.class);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(serviceIntent);
        } else {
            startService(serviceIntent);
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        // Verificar permisos cada vez que vuelve a esta pantalla
        if (!hasRequiredPermissions()) {
            goToPermissionSetup();
        } else {
            // Asegurar que el servicio sigue activo
            startMonitoringService();
        }
    }

    private boolean hasRequiredPermissions() {
        // Verificar si ya completó el setup de permisos
        return prefs.getBoolean("permissionsGranted", false);
    }

    private void goToPermissionSetup() {
        Intent intent = new Intent(this, PermissionSetup.class);
        startActivity(intent);
        finish();
    }

    private void initializeViews() {
        tvLinkCode = findViewById(R.id.tvLinkCode);
        tvDeviceInfo = findViewById(R.id.tvDeviceInfo);

        // Obtener código de vinculación
        String linkCode = prefs.getString("linkCode", "------");
        tvLinkCode.setText(linkCode);

        // Mostrar información del dispositivo
        String deviceModel = Build.MANUFACTURER + " " + Build.MODEL;
        tvDeviceInfo.setText("Servicio de monitoreo activo • " + deviceModel);
    }

    @Override
    public void onBackPressed() {
        // Prevenir que el usuario salga con el botón atrás
        android.widget.Toast.makeText(this,
                "Este dispositivo está bajo supervisión parental",
                android.widget.Toast.LENGTH_SHORT).show();
    }
}
