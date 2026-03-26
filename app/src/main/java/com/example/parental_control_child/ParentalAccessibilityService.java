package com.example.parental_control_child;

import android.accessibilityservice.AccessibilityService;
import android.accessibilityservice.AccessibilityServiceInfo;
import android.app.usage.UsageStats;
import android.app.usage.UsageStatsManager;
import android.content.Context;
import android.content.Intent;
import android.util.Log;
import android.view.accessibility.AccessibilityEvent;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.ListenerRegistration;

import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

public class ParentalAccessibilityService extends AccessibilityService {

    private static final String TAG = "ParentalAccessibility";
    private FirebaseFirestore db;
    private ListenerRegistration blockListener;
    
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
        
        AccessibilityServiceInfo info = new AccessibilityServiceInfo();
        info.eventTypes = AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED;
        info.feedbackType = AccessibilityServiceInfo.FEEDBACK_GENERIC;
        info.flags = AccessibilityServiceInfo.FLAG_INCLUDE_NOT_IMPORTANT_VIEWS;
        info.notificationTimeout = 50; // Más rápido
        setServiceInfo(info);

        startListeningToFirestore();
    }

    private void startListeningToFirestore() {
        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
        if (user == null) {
            new android.os.Handler().postDelayed(this::startListeningToFirestore, 3000);
            return;
        }

        blockListener = db.collection("children").document(user.getUid())
                .addSnapshotListener((snapshot, e) -> {
                    if (e != null || snapshot == null || !snapshot.exists()) return;
                    updateConfig(snapshot);
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
                        Object limitObj = data.get("timeLimitMinutes");
                        if (limitObj instanceof Number) limit = ((Number) limitObj).longValue();

                        String start = (String) data.get("startTime");
                        String end = (String) data.get("endTime");

                        newConfigs.put(pkg, new AppConfig(pkg, blocked, limit, start, end));
                    }
                }
            }
        } catch (Exception e) {
            Log.e(TAG, "Error parsing appsMap", e);
        }

        synchronized (appConfigs) {
            appConfigs.clear();
            appConfigs.putAll(newConfigs);
        }
    }

    @Override
    public void onAccessibilityEvent(AccessibilityEvent event) {
        if (event.getEventType() != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) return;
        if (event.getPackageName() == null) return;

        String pkgName = event.getPackageName().toString();
        
        // No bloquear nuestra propia app, ni el sistema, ni el launcher
        if (pkgName.equals(getPackageName()) || pkgName.equals("android") || 
            pkgName.contains("launcher") || pkgName.contains("settings")) return;

        checkAndEnforce(pkgName);
    }

    private void checkAndEnforce(String pkgName) {
        AppConfig config;
        synchronized (appConfigs) {
            config = appConfigs.get(pkgName);
        }

        if (config == null) return;

        String reason = null;

        if (config.blocked) {
            reason = "Esta aplicación ha sido bloqueada por tus padres.";
        } else if (config.timeLimitMinutes > 0) {
            long currentUsage = getTodayUsageMinutes(pkgName);
            if (currentUsage >= config.timeLimitMinutes) {
                reason = "Has alcanzado el límite de tiempo diario (" + config.timeLimitMinutes + " min).";
            }
        }

        if (reason == null && config.startTime != null && config.endTime != null && !config.startTime.isEmpty()) {
            if (isTimeInRestrictedRange(config.startTime, config.endTime)) {
                reason = "No puedes usar esta aplicación en este horario (" + config.startTime + " - " + config.endTime + ").";
            }
        }

        if (reason != null) {
            blockApp(pkgName, reason);
        }
    }

    private long getTodayUsageMinutes(String packageName) {
        UsageStatsManager usm = (UsageStatsManager) getSystemService(Context.USAGE_STATS_SERVICE);
        if (usm == null) return 0;
        Calendar calendar = Calendar.getInstance();
        long endTime = calendar.getTimeInMillis();
        calendar.set(Calendar.HOUR_OF_DAY, 0);
        calendar.set(Calendar.MINUTE, 0);
        calendar.set(Calendar.SECOND, 0);
        Map<String, UsageStats> stats = usm.queryAndAggregateUsageStats(calendar.getTimeInMillis(), endTime);
        if (stats.containsKey(packageName)) {
            return stats.get(packageName).getTotalTimeInForeground() / (1000 * 60);
        }
        return 0;
    }

    private boolean isTimeInRestrictedRange(String start, String end) {
        try {
            SimpleDateFormat sdf = new SimpleDateFormat("HH:mm", Locale.getDefault());
            String nowStr = sdf.format(new Date());
            Date now = sdf.parse(nowStr);
            Date startDate = sdf.parse(start);
            Date endDate = sdf.parse(end);
            if (now == null || startDate == null || endDate == null) return false;
            if (endDate.before(startDate)) return now.after(startDate) || now.before(endDate);
            else return now.after(startDate) && now.before(endDate);
        } catch (Exception e) { return false; }
    }

    private void blockApp(String pkgName, String reason) {
        long currentTime = System.currentTimeMillis();
        // Evitar lanzamientos múltiples en menos de 2 segundos
        if (pkgName.equals(lastBlockedPackage) && (currentTime - lastBlockTime < 2000)) return;
        
        lastBlockedPackage = pkgName;
        lastBlockTime = currentTime;

        // 1. Mandar al Home inmediatamente
        performGlobalAction(GLOBAL_ACTION_HOME);

        // 2. Mostrar alerta de emergencia (en una nueva tarea independiente)
        Intent intent = new Intent(this, BlockedActivity.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK 
                      | Intent.FLAG_ACTIVITY_MULTIPLE_TASK 
                      | Intent.FLAG_ACTIVITY_NO_ANIMATION);
        intent.putExtra("reason", reason);
        intent.putExtra("packageName", pkgName);
        startActivity(intent);
    }

    @Override public void onInterrupt() {}

    @Override
    public void onDestroy() {
        if (blockListener != null) blockListener.remove();
        super.onDestroy();
    }
}