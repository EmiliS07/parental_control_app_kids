package com.example.parental_control_child;

import android.Manifest;
import android.app.AppOpsManager;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.text.TextUtils;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.work.OneTimeWorkRequest;
import androidx.work.WorkManager;

import com.google.firebase.auth.FirebaseAuth;

public class PermissionSetup extends AppCompatActivity {

    private static final int REQUEST_LOCATION = 103;
    private static final int REQUEST_BACKGROUND_LOCATION = 104;
    private static final int REQUEST_NOTIFICATION = 102;

    private SharedPreferences prefs;
    private TextView tvUsageStatus, tvLocationStatus, tvNotifStatus, tvBatteryStatus, tvOverlayStatus, tvAccessibilityStatus, tvNotifListenerStatus;
    private Button btnUsageStats, btnLocation, btnNotifications, btnBattery, btnOverlay, btnAccessibility, btnNotifListener;
    private Button btnFinish;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        prefs = getSharedPreferences("ParentalControl", MODE_PRIVATE);

        // Si ya tiene permisos y está vinculado, ir directo a Home
        if (checkAllPermissionsGranted() && prefs.getBoolean("isLinked", false)) {
            startActivity(new Intent(this, HomeActivity.class));
            finish();
            return;
        }

        setContentView(R.layout.activity_permission_setup);
        
        if (FirebaseAuth.getInstance().getCurrentUser() == null) {
            FirebaseAuth.getInstance().signInAnonymously();
        }

        initViews();
        updatePermissionStatus();
    }

    @Override
    protected void onResume() {
        super.onResume();
        updatePermissionStatus();
    }

    private void initViews() {
        tvUsageStatus = findViewById(R.id.tvUsageStatus);
        btnUsageStats = findViewById(R.id.btnUsageStats);
        btnUsageStats.setOnClickListener(v -> requestUsageStatsPermission());

        tvLocationStatus = findViewById(R.id.tvLocationStatus);
        btnLocation = findViewById(R.id.btnLocation);
        btnLocation.setOnClickListener(v -> requestLocationPermission());

        tvNotifStatus = findViewById(R.id.tvNotifStatus);
        btnNotifications = findViewById(R.id.btnNotifications);
        btnNotifications.setOnClickListener(v -> requestNotificationPermission());

        tvBatteryStatus = findViewById(R.id.tvBatteryStatus);
        btnBattery = findViewById(R.id.btnBattery);
        btnBattery.setOnClickListener(v -> requestBatteryOptimization());

        tvOverlayStatus = findViewById(R.id.tvOverlayStatus);
        btnOverlay = findViewById(R.id.btnOverlay);
        btnOverlay.setOnClickListener(v -> requestOverlayPermission());

        tvAccessibilityStatus = findViewById(R.id.tvAccessibilityStatus);
        btnAccessibility = findViewById(R.id.btnAccessibility);
        btnAccessibility.setOnClickListener(v -> requestAccessibilityPermission());

        tvNotifListenerStatus = findViewById(R.id.tvNotifListenerStatus);
        btnNotifListener = findViewById(R.id.btnNotifListener);
        btnNotifListener.setOnClickListener(v -> requestNotificationListenerPermission());

        btnFinish = findViewById(R.id.btnFinish);
        btnFinish.setOnClickListener(v -> finishSetup());
    }

    private void updatePermissionStatus() {
        boolean hasUsageStats = checkUsageStatsPermission();
        updateStepStatus(tvUsageStatus, btnUsageStats, hasUsageStats);

        boolean hasLocation = checkLocationPermission();
        updateStepStatus(tvLocationStatus, btnLocation, hasLocation);

        boolean hasNotifications = checkNotificationPermission();
        updateStepStatus(tvNotifStatus, btnNotifications, hasNotifications);

        boolean hasBattery = checkBatteryOptimization();
        updateStepStatus(tvBatteryStatus, btnBattery, hasBattery);

        boolean hasOverlay = checkOverlayPermission();
        updateStepStatus(tvOverlayStatus, btnOverlay, hasOverlay);

        boolean hasAccessibility = checkAccessibilityPermission();
        updateStepStatus(tvAccessibilityStatus, btnAccessibility, hasAccessibility);

        boolean hasNotifListener = checkNotificationListenerPermission();
        updateStepStatus(tvNotifListenerStatus, btnNotifListener, hasNotifListener);

        boolean allGranted = hasUsageStats && hasLocation && hasNotifications && hasBattery &&
                hasOverlay && hasAccessibility && hasNotifListener;
        btnFinish.setEnabled(allGranted);
        btnFinish.setAlpha(allGranted ? 1.0f : 0.5f);
    }

    private boolean checkAllPermissionsGranted() {
        return checkUsageStatsPermission() && checkLocationPermission() && checkNotificationPermission() &&
                checkBatteryOptimization() && checkOverlayPermission() && checkAccessibilityPermission() &&
                checkNotificationListenerPermission();
    }

    private void updateStepStatus(TextView statusView, Button button, boolean granted) {
        if (granted) {
            statusView.setText("✅ Concedido");
            statusView.setTextColor(ContextCompat.getColor(this, android.R.color.holo_green_dark));
            button.setEnabled(false);
            button.setText("Listo");
        } else {
            statusView.setText("⚠️ Requerido");
            statusView.setTextColor(ContextCompat.getColor(this, android.R.color.holo_orange_dark));
            button.setEnabled(true);
            button.setText("Activar");
        }
    }

    private boolean checkUsageStatsPermission() {
        AppOpsManager appOps = (AppOpsManager) getSystemService(Context.APP_OPS_SERVICE);
        int mode = appOps.checkOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS, android.os.Process.myUid(), getPackageName());
        return mode == AppOpsManager.MODE_ALLOWED;
    }

    private boolean checkLocationPermission() {
        boolean fineLocation = ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            boolean backgroundLocation = ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_BACKGROUND_LOCATION) == PackageManager.PERMISSION_GRANTED;
            return fineLocation && backgroundLocation;
        }
        return fineLocation;
    }

    private boolean checkNotificationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            return ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED;
        }
        return true;
    }

    private boolean checkBatteryOptimization() {
        android.os.PowerManager pm = (android.os.PowerManager) getSystemService(Context.POWER_SERVICE);
        return pm.isIgnoringBatteryOptimizations(getPackageName());
    }

    private boolean checkOverlayPermission() { return Settings.canDrawOverlays(this); }

    private boolean checkAccessibilityPermission() {
        String service = getPackageName() + "/" + ParentalAccessibilityService.class.getName();
        int accessibilityEnabled = 0;
        try {
            accessibilityEnabled = Settings.Secure.getInt(getContentResolver(), Settings.Secure.ACCESSIBILITY_ENABLED);
        } catch (Settings.SettingNotFoundException e) {
            return false;
        }

        if (accessibilityEnabled == 1) {
            String settingValue = Settings.Secure.getString(getContentResolver(), Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES);
            if (settingValue != null) {
                TextUtils.SimpleStringSplitter splitter = new TextUtils.SimpleStringSplitter(':');
                splitter.setString(settingValue);
                while (splitter.hasNext()) {
                    String accessibilityService = splitter.next();
                    if (accessibilityService.equalsIgnoreCase(service)) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    private boolean checkNotificationListenerPermission() {
        String listeners = Settings.Secure.getString(getContentResolver(), "enabled_notification_listeners");
        return listeners != null && listeners.contains(getPackageName());
    }

    private void requestUsageStatsPermission() { startActivity(new Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS)); }

    private void requestLocationPermission() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION}, REQUEST_LOCATION);
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_BACKGROUND_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            Toast.makeText(this, "Selecciona 'Permitir todo el tiempo' para el rastreo continuo", Toast.LENGTH_LONG).show();
            ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.ACCESS_BACKGROUND_LOCATION}, REQUEST_BACKGROUND_LOCATION);
        }
    }

    private void requestNotificationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.POST_NOTIFICATIONS}, REQUEST_NOTIFICATION);
        }
    }

    private void requestBatteryOptimization() {
        Intent intent = new Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, Uri.parse("package:" + getPackageName()));
        startActivity(intent);
    }

    private void requestOverlayPermission() {
        startActivity(new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:" + getPackageName())));
    }

    private void requestAccessibilityPermission() { startActivity(new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)); }

    private void requestNotificationListenerPermission() { startActivity(new Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)); }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        updatePermissionStatus();
    }

    private void finishSetup() {
        // Marcamos que los permisos están listos
        prefs.edit().putBoolean("permissionsGranted", true).apply();
        
        // Ejecución inmediata de prueba para localización
        WorkManager.getInstance(this).enqueue(new OneTimeWorkRequest.Builder(LocationWorker.class).build());

        Intent serviceIntent = new Intent(this, MonitoringService.class);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            ContextCompat.startForegroundService(this, serviceIntent);
        } else {
            startService(serviceIntent);
        }

        // Si NO está vinculado, ir al CodePanel. Si ya lo está, ir a Home.
        if (!prefs.getBoolean("isLinked", false)) {
            startActivity(new Intent(this, CodePanel.class));
        } else {
            startActivity(new Intent(this, HomeActivity.class));
        }
        finish();
    }
}
