package com.example.parental_control_child;

import android.app.AppOpsManager;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
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

public class PermissionSetup extends AppCompatActivity {

    private static final int REQUEST_NOTIFICATION = 102;

    private SharedPreferences prefs;

    // UI Elements (SIN ubicación)
    private LinearLayout stepUsageStats, stepNotifications, stepBattery, stepOverlay, stepAccessibility, stepNotificationListener;
    private TextView tvUsageStatus, tvNotifStatus, tvBatteryStatus, tvOverlayStatus, tvAccessibilityStatus, tvNotifListenerStatus;
    private Button btnUsageStats, btnNotifications, btnBattery, btnOverlay, btnAccessibility, btnNotifListener;
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
        // Step 1: Uso de apps
        stepUsageStats = findViewById(R.id.stepUsageStats);
        tvUsageStatus = findViewById(R.id.tvUsageStatus);
        btnUsageStats = findViewById(R.id.btnUsageStats);
        btnUsageStats.setOnClickListener(v -> requestUsageStatsPermission());

        // Step 2: Notificaciones
        stepNotifications = findViewById(R.id.stepNotifications);
        tvNotifStatus = findViewById(R.id.tvNotifStatus);
        btnNotifications = findViewById(R.id.btnNotifications);
        btnNotifications.setOnClickListener(v -> requestNotificationPermission());

        // Step 3: Batería
        stepBattery = findViewById(R.id.stepBattery);
        tvBatteryStatus = findViewById(R.id.tvBatteryStatus);
        btnBattery = findViewById(R.id.btnBattery);
        btnBattery.setOnClickListener(v -> requestBatteryOptimization());

        // Step 4: Overlay
        stepOverlay = findViewById(R.id.stepOverlay);
        tvOverlayStatus = findViewById(R.id.tvOverlayStatus);
        btnOverlay = findViewById(R.id.btnOverlay);
        btnOverlay.setOnClickListener(v -> requestOverlayPermission());

        // Step 5: Accesibilidad
        stepAccessibility = findViewById(R.id.stepAccessibility);
        tvAccessibilityStatus = findViewById(R.id.tvAccessibilityStatus);
        btnAccessibility = findViewById(R.id.btnAccessibility);
        btnAccessibility.setOnClickListener(v -> requestAccessibilityPermission());

        // Step 6: Listener de notificaciones
        stepNotificationListener = findViewById(R.id.stepNotificationListener);
        tvNotifListenerStatus = findViewById(R.id.tvNotifListenerStatus);
        btnNotifListener = findViewById(R.id.btnNotifListener);
        btnNotifListener.setOnClickListener(v -> requestNotificationListenerPermission());

        // Botón finalizar
        btnFinish = findViewById(R.id.btnFinish);
        btnFinish.setOnClickListener(v -> finishSetup());
    }

    private void updatePermissionStatus() {
        // 1. Uso de apps
        boolean hasUsageStats = checkUsageStatsPermission();
        updateStepStatus(tvUsageStatus, btnUsageStats, hasUsageStats);

        // 2. Notificaciones (Android 13+)
        boolean hasNotifications = checkNotificationPermission();
        updateStepStatus(tvNotifStatus, btnNotifications, hasNotifications);

        // 3. Batería
        boolean hasBattery = checkBatteryOptimization();
        updateStepStatus(tvBatteryStatus, btnBattery, hasBattery);

        // 4. Overlay
        boolean hasOverlay = checkOverlayPermission();
        updateStepStatus(tvOverlayStatus, btnOverlay, hasOverlay);

        // 5. Accesibilidad
        boolean hasAccessibility = checkAccessibilityPermission();
        updateStepStatus(tvAccessibilityStatus, btnAccessibility, hasAccessibility);

        // 6. Notification Listener
        boolean hasNotifListener = checkNotificationListenerPermission();
        updateStepStatus(tvNotifListenerStatus, btnNotifListener, hasNotifListener);

        // Habilitar botón finalizar si tiene todos los permisos
        boolean allGranted = hasUsageStats && hasNotifications && hasBattery &&
                hasOverlay && hasAccessibility && hasNotifListener;
        btnFinish.setEnabled(allGranted);
        btnFinish.setAlpha(allGranted ? 1.0f : 0.5f);
    }

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

    private boolean checkUsageStatsPermission() {
        AppOpsManager appOps = (AppOpsManager) getSystemService(Context.APP_OPS_SERVICE);
        int mode = appOps.checkOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS,
                android.os.Process.myUid(), getPackageName());
        return mode == AppOpsManager.MODE_ALLOWED;
    }

    private boolean checkNotificationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            return checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS)
                    == android.content.pm.PackageManager.PERMISSION_GRANTED;
        }
        return true; // No requerido en Android < 13
    }

    private boolean checkBatteryOptimization() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            String packageName = getPackageName();
            android.os.PowerManager pm = (android.os.PowerManager) getSystemService(Context.POWER_SERVICE);
            return pm.isIgnoringBatteryOptimizations(packageName);
        }
        return true;
    }

    private boolean checkOverlayPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            return Settings.canDrawOverlays(this);
        }
        return true;
    }

    private boolean checkAccessibilityPermission() {
        int accessibilityEnabled = 0;
        try {
            accessibilityEnabled = Settings.Secure.getInt(
                    getContentResolver(),
                    Settings.Secure.ACCESSIBILITY_ENABLED
            );
        } catch (Settings.SettingNotFoundException e) {
            e.printStackTrace();
        }

        if (accessibilityEnabled == 1) {
            String services = Settings.Secure.getString(
                    getContentResolver(),
                    Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
            );
            if (services != null) {
                return services.contains(getPackageName());
            }
        }
        return false;
    }

    private boolean checkNotificationListenerPermission() {
        String listeners = Settings.Secure.getString(
                getContentResolver(),
                "enabled_notification_listeners"
        );
        return listeners != null && listeners.contains(getPackageName());
    }

    // ========== SOLICITAR PERMISOS ==========

    private void requestUsageStatsPermission() {
        Intent intent = new Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS);
        startActivity(intent);
        Toast.makeText(this, "Por favor, habilita el acceso de uso", Toast.LENGTH_LONG).show();
    }

    private void requestNotificationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ActivityCompat.requestPermissions(this,
                    new String[]{android.Manifest.permission.POST_NOTIFICATIONS}, REQUEST_NOTIFICATION);
        }
    }

    private void requestBatteryOptimization() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            Intent intent = new Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS);
            intent.setData(Uri.parse("package:" + getPackageName()));
            startActivity(intent);
            Toast.makeText(this, "Por favor, desactiva la optimización de batería", Toast.LENGTH_LONG).show();
        }
    }

    private void requestOverlayPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            Intent intent = new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                    Uri.parse("package:" + getPackageName()));
            startActivity(intent);
            Toast.makeText(this, "Por favor, habilita 'Mostrar sobre otras apps'", Toast.LENGTH_LONG).show();
        }
    }

    private void requestAccessibilityPermission() {
        Intent intent = new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS);
        startActivity(intent);
        Toast.makeText(this, "Por favor, habilita el servicio de accesibilidad", Toast.LENGTH_LONG).show();
    }

    private void requestNotificationListenerPermission() {
        Intent intent = new Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS);
        startActivity(intent);
        Toast.makeText(this, "Por favor, habilita el acceso a notificaciones", Toast.LENGTH_LONG).show();
    }

    // ========== RESULTADOS ==========

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions,
                                           @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        updatePermissionStatus();
    }

    private void finishSetup() {
        // Guardar que completó el setup
        SharedPreferences.Editor editor = prefs.edit();
        editor.putBoolean("permissionsGranted", true);
        editor.putLong("permissionsGrantedAt", System.currentTimeMillis());
        editor.apply();

        // Iniciar servicio de monitoreo
        Intent serviceIntent = new Intent(this, MonitoringService.class);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(serviceIntent);
        } else {
            startService(serviceIntent);
        }

        Toast.makeText(this, "✅ Configuración completa", Toast.LENGTH_SHORT).show();

        // Ir a HomeActivity
        Intent intent = new Intent(this, HomeActivity.class);
        startActivity(intent);
        finish();
    }
}