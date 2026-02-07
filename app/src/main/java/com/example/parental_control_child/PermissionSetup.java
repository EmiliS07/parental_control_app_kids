package com.example.parental_control_child;

import android.Manifest;
import android.annotation.SuppressLint;
import android.app.AppOpsManager;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

@SuppressWarnings("deprecation")
public class PermissionSetup extends AppCompatActivity {

    private static final int REQUEST_LOCATION = 100;
    private static final int REQUEST_USAGE_STATS = 101;
    private static final int REQUEST_NOTIFICATION = 102;

    private SharedPreferences prefs;

    // UI Elements
    @SuppressWarnings({"unused", "FieldCanBeLocal"})
    private LinearLayout stepLocation, stepUsageStats, stepNotifications, stepBattery;
    private TextView tvLocationStatus, tvUsageStatus, tvNotifStatus, tvBatteryStatus;
    private Button btnLocation, btnUsageStats, btnNotifications, btnBattery;
    private Button btnFinish;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_permission_setup);

        prefs = getSharedPreferences("ParentalControl", MODE_PRIVATE);

        initViews();
        updatePermissionStatus();
    }

    @Override
    protected void onResume() {
        super.onResume();
        updatePermissionStatus();
    }

    private void initViews() {
        // Step 1: Ubicación
        stepLocation = findViewById(R.id.stepLocation);
        tvLocationStatus = findViewById(R.id.tvLocationStatus);
        btnLocation = findViewById(R.id.btnLocation);
        btnLocation.setOnClickListener(v -> requestLocationPermission());

        // Step 2: Uso de apps
        stepUsageStats = findViewById(R.id.stepUsageStats);
        tvUsageStatus = findViewById(R.id.tvUsageStatus);
        btnUsageStats = findViewById(R.id.btnUsageStats);
        btnUsageStats.setOnClickListener(v -> requestUsageStatsPermission());

        // Step 3: Notificaciones
        stepNotifications = findViewById(R.id.stepNotifications);
        tvNotifStatus = findViewById(R.id.tvNotifStatus);
        btnNotifications = findViewById(R.id.btnNotifications);
        btnNotifications.setOnClickListener(v -> requestNotificationPermission());

        // Step 4: Batería
        stepBattery = findViewById(R.id.stepBattery);
        tvBatteryStatus = findViewById(R.id.tvBatteryStatus);
        btnBattery = findViewById(R.id.btnBattery);
        btnBattery.setOnClickListener(v -> requestBatteryOptimization());

        // Botón finalizar
        btnFinish = findViewById(R.id.btnFinish);
        btnFinish.setOnClickListener(v -> finishSetup());
    }

    private void updatePermissionStatus() {
        // 1. Ubicación
        boolean hasLocation = checkLocationPermission();
        updateStepStatus(tvLocationStatus, btnLocation, hasLocation);

        // 2. Uso de apps
        boolean hasUsageStats = checkUsageStatsPermission();
        updateStepStatus(tvUsageStatus, btnUsageStats, hasUsageStats);

        // 3. Notificaciones (Android 13+)
        boolean hasNotifications = checkNotificationPermission();
        updateStepStatus(tvNotifStatus, btnNotifications, hasNotifications);

        // 4. Batería
        boolean hasBattery = checkBatteryOptimization();
        updateStepStatus(tvBatteryStatus, btnBattery, hasBattery);

        // Habilitar botón finalizar si tiene todos los permisos
        boolean allGranted = hasLocation && hasUsageStats && hasNotifications && hasBattery;
        btnFinish.setEnabled(allGranted);
        btnFinish.setAlpha(allGranted ? 1.0f : 0.5f);
    }

    @SuppressLint("SetTextI18n")
    private void updateStepStatus(TextView statusView, Button button, boolean granted) {
        if (granted) {
            statusView.setText("✅ Concedido");
            statusView.setTextColor(getResources().getColor(android.R.color.holo_green_dark));
            button.setEnabled(false);
            button.setText("Concedido");
        } else {
            statusView.setText("⚠️ Requerido");
            statusView.setTextColor(getResources().getColor(android.R.color.holo_orange_dark));
            button.setEnabled(true);
            button.setText("Conceder");
        }
    }

    // ========== VERIFICAR PERMISOS ==========

    private boolean checkLocationPermission() {
        return ContextCompat.checkSelfPermission(this,
                Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED;
    }

    private boolean checkUsageStatsPermission() {
        AppOpsManager appOps = (AppOpsManager) getSystemService(Context.APP_OPS_SERVICE);
        int mode = appOps.checkOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS,
                android.os.Process.myUid(), getPackageName());
        return mode == AppOpsManager.MODE_ALLOWED;
    }

    private boolean checkNotificationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            return ContextCompat.checkSelfPermission(this,
                    Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED;
        }
        return true; // No requerido en Android < 13
    }

    private boolean checkBatteryOptimization() {
        String packageName = getPackageName();
        android.os.PowerManager pm = (android.os.PowerManager) getSystemService(Context.POWER_SERVICE);
        return pm.isIgnoringBatteryOptimizations(packageName);
    }

    // ========== SOLICITAR PERMISOS ==========

    private void requestLocationPermission() {
        ActivityCompat.requestPermissions(this,
                new String[]{Manifest.permission.ACCESS_FINE_LOCATION}, REQUEST_LOCATION);
    }

    private void requestUsageStatsPermission() {
        Intent intent = new Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS);
        startActivityForResult(intent, REQUEST_USAGE_STATS);
        Toast.makeText(this, "Por favor, habilita el acceso de uso", Toast.LENGTH_LONG).show();
    }

    private void requestNotificationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ActivityCompat.requestPermissions(this,
                    new String[]{Manifest.permission.POST_NOTIFICATIONS}, REQUEST_NOTIFICATION);
        }
    }

    @SuppressLint("BatteryLife")
    private void requestBatteryOptimization() {
        Intent intent = new Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS);
        intent.setData(Uri.parse("package:" + getPackageName()));
        startActivity(intent);
        Toast.makeText(this, "Por favor, desactiva la optimización de batería", Toast.LENGTH_LONG).show();
    }

    // ========== RESULTADOS ==========

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions,
                                           @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        updatePermissionStatus();
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        updatePermissionStatus();
    }

    private void finishSetup() {
        // Guardar que completó el setup
        SharedPreferences.Editor editor = prefs.edit();
        editor.putBoolean("permissionsGranted", true);
        editor.putLong("permissionsGrantedAt", System.currentTimeMillis());
        editor.apply();

        Toast.makeText(this, "✅ Configuración completa", Toast.LENGTH_SHORT).show();

        // Ir a HomeActivity
        Intent intent = new Intent(this, HomeActivity.class);
        startActivity(intent);
        finish();
    }
}