package com.example.parental_control_child;

import android.accessibilityservice.AccessibilityService;
import android.accessibilityservice.AccessibilityServiceInfo;
import android.app.usage.UsageStats;
import android.app.usage.UsageStatsManager;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.accessibility.AccessibilityEvent;
import android.view.accessibility.AccessibilityNodeInfo;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.ListenerRegistration;

import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class ParentalAccessibilityService extends AccessibilityService {

    private static final String TAG = "ParentalAccessibility";
    private FirebaseFirestore db;
    private ListenerRegistration blockListener;
    private SharedPreferences prefs;
    
    private final Map<String, AppConfig> appConfigs = new HashMap<>();
    private String lastBlockedPackage = "";
    private long lastBlockTime = 0;

    private static class AppConfig {
        String packageName;
        boolean blocked;
        long timeLimitMinutes;
        String startTime;
        String endTime;

        AppConfig(String pkg, boolean blocked, long limit, String start, String end) {
            this.packageName = pkg;
            this.blocked = blocked;
            this.timeLimitMinutes = limit;
            this.startTime = start;
            this.endTime = end;
        }
    }

    @Override
    protected void onServiceConnected() {
        super.onServiceConnected();
        db = FirebaseFirestore.getInstance();
        prefs = getSharedPreferences("ParentalControl", MODE_PRIVATE);
        
        AccessibilityServiceInfo info = new AccessibilityServiceInfo();
        // Escuchamos cambios de estado de ventana y contenido de forma instantánea
        info.eventTypes = AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED | 
                         AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED;
        info.feedbackType = AccessibilityServiceInfo.FEEDBACK_GENERIC;
        info.flags = AccessibilityServiceInfo.FLAG_RETRIEVE_INTERACTIVE_WINDOWS |
                     AccessibilityServiceInfo.FLAG_INCLUDE_NOT_IMPORTANT_VIEWS;
        info.notificationTimeout = 0; 
        setServiceInfo(info);

        Log.d(TAG, "Servicio de Accesibilidad Conectado");
        startListeningToFirestore();
    }

    private void startListeningToFirestore() {
        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
        if (user == null) {
            Log.d(TAG, "Usuario no autenticado, reintentando escucha en 5s...");
            new Handler(Looper.getMainLooper()).postDelayed(this::startListeningToFirestore, 5000);
            return;
        }

        if (blockListener != null) blockListener.remove();

        blockListener = db.collection("children").document(user.getUid())
                .addSnapshotListener((snapshot, e) -> {
                    if (e != null) {
                        Log.e(TAG, "Error en Firestore Listener", e);
                        return;
                    }
                    if (snapshot != null && snapshot.exists()) {
                        updateConfig(snapshot);
                    }
                });
    }

    private void updateConfig(DocumentSnapshot snapshot) {
        Map<String, AppConfig> newConfigs = new HashMap<>();
        try {
            Map<String, Object> appsMap = (Map<String, Object>) snapshot.get("appsMap");
            if (appsMap != null) {
                for (Object value : appsMap.values()) {
                    if (value instanceof Map) {
                        Map<String, Object> data = (Map<String, Object>) value;
                        String pkg = (String) data.get("packageName");
                        if (pkg == null) continue;
                        
                        boolean blocked = Boolean.TRUE.equals(data.get("blocked"));
                        long limit = 0;
                        if (data.get("timeLimitMinutes") instanceof Number) {
                            limit = ((Number) data.get("timeLimitMinutes")).longValue();
                        }
                        String start = (String) data.get("startTime");
                        String end = (String) data.get("endTime");

                        newConfigs.put(pkg, new AppConfig(pkg, blocked, limit, start, end));
                    }
                }
            }
            Log.d(TAG, "Configuración de apps actualizada: " + newConfigs.size() + " apps");
        } catch (Exception e) { 
            Log.e(TAG, "Error parseando appsMap", e); 
        }

        synchronized (appConfigs) {
            appConfigs.clear();
            appConfigs.putAll(newConfigs);
        }
    }

    @Override
    public void onAccessibilityEvent(AccessibilityEvent event) {
        if (event == null || event.getPackageName() == null) return;
        String pkgName = event.getPackageName().toString();

        // No actuar si somos nosotros mismos o la pantalla de bloqueo
        if (pkgName.equals(getPackageName()) || pkgName.contains("BlockedActivity")) return;

        // Escudo antidesinstalación
        if (isLinked() && !isUninstallAllowed()) {
            if (pkgName.contains("settings") || pkgName.contains("packageinstaller")) {
                checkUninstallAttempt();
            }
        }

        // BLOQUEO POR SUPERPOSICIÓN (Sin sacar al niño automáticamente al Home)
        checkAndEnforce(pkgName);
    }

    private void checkUninstallAttempt() {
        AccessibilityNodeInfo root = getRootInActiveWindow();
        if (root == null) return;
        List<AccessibilityNodeInfo> nodes = root.findAccessibilityNodeInfosByText("Security Kambery");
        if (nodes != null && !nodes.isEmpty()) {
            Log.w(TAG, "Intento de desinstalación detectado. Volviendo al Home para proteger.");
            performGlobalAction(GLOBAL_ACTION_HOME);
            launchActivity(UninstallGuardActivity.class, null, null);
            for(AccessibilityNodeInfo n : nodes) n.recycle();
        }
        root.recycle();
    }

    private void checkAndEnforce(String pkgName) {
        AppConfig config;
        synchronized (appConfigs) { config = appConfigs.get(pkgName); }
        if (config == null) return;

        String reason = null;
        if (config.blocked) {
            reason = "Esta aplicación ha sido bloqueada por tus padres.";
        } else if (config.timeLimitMinutes > 0 && getTodayUsageMinutes(pkgName) >= config.timeLimitMinutes) {
            reason = "Has alcanzado el límite de tiempo diario.";
        } else if (config.startTime != null && config.endTime != null && isTimeInRestrictedRange(config.startTime, config.endTime)) {
            reason = "No puedes usar esta app en este horario.";
        }
        
        if (reason != null) {
            Log.d(TAG, "App restringida detectada: " + pkgName + ". Mostrando pantalla de bloqueo.");
            launchActivity(BlockedActivity.class, reason, pkgName);
        }
    }

    private void launchActivity(Class<?> cls, String reason, String pkg) {
        long now = System.currentTimeMillis();
        // Cooldown muy bajo (300ms) para evitar que el niño interactúe con la app pero no saturar el sistema
        if (pkg != null && pkg.equals(lastBlockedPackage) && (now - lastBlockTime < 300)) return;
        
        lastBlockedPackage = pkg != null ? pkg : "";
        lastBlockTime = now;

        Intent intent = new Intent(this, cls);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | 
                      Intent.FLAG_ACTIVITY_REORDER_TO_FRONT |
                      Intent.FLAG_ACTIVITY_SINGLE_TOP |
                      Intent.FLAG_ACTIVITY_NO_ANIMATION);
        if (reason != null) intent.putExtra("reason", reason);
        if (pkg != null) intent.putExtra("packageName", pkg);
        
        try {
            startActivity(intent);
        } catch (Exception e) {
            Log.e(TAG, "Error lanzando actividad de bloqueo: " + cls.getSimpleName(), e);
        }
    }

    private boolean isLinked() { return prefs != null && prefs.getBoolean("isLinked", false); }
    private boolean isUninstallAllowed() { return prefs != null && prefs.getBoolean("allow_uninstall", false); }

    private long getTodayUsageMinutes(String packageName) {
        UsageStatsManager usm = (UsageStatsManager) getSystemService(Context.USAGE_STATS_SERVICE);
        if (usm == null) return 0;
        Calendar c = Calendar.getInstance();
        long end = c.getTimeInMillis();
        c.set(Calendar.HOUR_OF_DAY, 0); c.set(Calendar.MINUTE, 0); c.set(Calendar.SECOND, 0);
        Map<String, UsageStats> stats = usm.queryAndAggregateUsageStats(c.getTimeInMillis(), end);
        if (stats != null && stats.containsKey(packageName)) return stats.get(packageName).getTotalTimeInForeground() / 60000;
        return 0;
    }

    private boolean isTimeInRestrictedRange(String start, String end) {
        try {
            if (start == null || end == null) return false;
            SimpleDateFormat sdf = new SimpleDateFormat("HH:mm", Locale.getDefault());
            Date now = sdf.parse(sdf.format(new Date()));
            Date s = sdf.parse(start); 
            Date e = sdf.parse(end);
            if (now == null || s == null || e == null) return false;
            
            if (e.before(s)) return now.after(s) || now.before(e);
            else return now.after(s) && now.before(e);
        } catch (Exception e) { return false; }
    }

    @Override public void onInterrupt() {}
    @Override public void onDestroy() { 
        if (blockListener != null) blockListener.remove(); 
        super.onDestroy(); 
    }
}
