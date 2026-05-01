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
    private long lastGuardTime = 0;

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
        // Reducimos el ruido: solo nos interesan cambios de ventana y contenido relevante
        info.eventTypes = AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED | 
                         AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED;
        info.feedbackType = AccessibilityServiceInfo.FEEDBACK_GENERIC;
        info.flags = AccessibilityServiceInfo.FLAG_RETRIEVE_INTERACTIVE_WINDOWS |
                     AccessibilityServiceInfo.FLAG_INCLUDE_NOT_IMPORTANT_VIEWS |
                     AccessibilityServiceInfo.FLAG_REPORT_VIEW_IDS;
        info.notificationTimeout = 100;
        setServiceInfo(info);

        Log.d(TAG, "!!! SERVICIO DE SEGURIDAD OPTIMIZADO ACTIVADO !!!");
        startListeningToFirestore();
    }

    private void startListeningToFirestore() {
        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
        if (user == null) {
            new Handler(Looper.getMainLooper()).postDelayed(this::startListeningToFirestore, 2000);
            return;
        }

        if (blockListener != null) blockListener.remove();

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
                        if (data.get("timeLimitMinutes") instanceof Number) limit = ((Number) data.get("timeLimitMinutes")).longValue();
                        newConfigs.put(pkg, new AppConfig(pkg, blocked, limit, (String) data.get("startTime"), (String) data.get("endTime")));
                    }
                }
            }
        } catch (Exception ignored) {}
        synchronized (appConfigs) {
            appConfigs.clear();
            appConfigs.putAll(newConfigs);
        }
    }

    @Override
    public void onAccessibilityEvent(AccessibilityEvent event) {
        if (event == null || event.getPackageName() == null) return;
        
        int eventType = event.getEventType();
        
        // IGNORAR NOTIFICACIONES Y OTROS EVENTOS RUIDOSOS
        if (eventType == AccessibilityEvent.TYPE_NOTIFICATION_STATE_CHANGED) return;

        String pkgName = event.getPackageName().toString();

        // No actuar sobre nuestra propia app
        if (pkgName.equals(getPackageName()) || 
            pkgName.contains("BlockedActivity") || 
            pkgName.contains("UninstallGuardActivity")) return;

        // 1. PROTECCIÓN ESPECÍFICA: Bloquear SOLO desinstalación y acceso a info de ESTA APP
        if (!isUninstallAllowed() && eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) {
            if (isSystemSettingsOrInstaller(pkgName)) {
                checkAndBlockSelfProtection(event);
            }
        }

        // 2. BLOQUEO POR REGLAS (Tiempo Límite, etc.)
        // Solo verificamos cuando cambia la ventana principal para evitar lentitud y falsos positivos
        if (eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) {
            checkAndEnforce(pkgName);
        }
    }

    private boolean isSystemSettingsOrInstaller(String pkgName) {
        String p = pkgName.toLowerCase();
        // Solo monitoreamos Ajustes y el Instalador de paquetes
        // Eliminamos "gms" y "systemui" que causaban bloqueos por notificaciones
        return p.contains("settings") || p.contains("packageinstaller") || p.contains("vending");
    }

    private void checkAndBlockSelfProtection(AccessibilityEvent event) {
        // Ignorar si estamos simplemente viendo la lista de todas las aplicaciones
        CharSequence className = event.getClassName();
        if (className != null) {
            String c = className.toString().toLowerCase();
            if (c.contains("manageapplications") || c.contains("list") || c.contains("tabactivity")) {
                return; 
            }
        }

        AccessibilityNodeInfo root = getRootInActiveWindow();
        if (root == null) root = event.getSource();
        if (root == null) return;

        // Buscamos si "Kambery" aparece como foco de la pantalla de ajustes/instalación
        boolean isOurAppTargeted = false;
        
        // Verificar título de la ventana
        if (event.getText() != null) {
            for (CharSequence t : event.getText()) {
                if (t != null && t.toString().equalsIgnoreCase("Kambery")) {
                    isOurAppTargeted = true;
                    break;
                }
            }
        }

        // Si no se detectó en el título, buscamos en el contenido pero con precaución
        if (!isOurAppTargeted) {
            List<AccessibilityNodeInfo> nodes = root.findAccessibilityNodeInfosByText("Kambery");
            if (nodes != null && !nodes.isEmpty()) {
                // Si encontramos "Kambery" en una pantalla de Ajustes o Instalador que NO es la lista general
                isOurAppTargeted = true;
                for (AccessibilityNodeInfo n : nodes) n.recycle();
            }
        }

        if (isOurAppTargeted) {
            long now = System.currentTimeMillis();
            if (now - lastGuardTime > 2000) {
                lastGuardTime = now;
                Log.w(TAG, "Protegiendo Kambery de desinstalación/cambios...");
                
                performGlobalAction(GLOBAL_ACTION_HOME);
                
                new Handler(Looper.getMainLooper()).postDelayed(() -> {
                    Intent intent = new Intent(this, UninstallGuardActivity.class);
                    intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | 
                                  Intent.FLAG_ACTIVITY_REORDER_TO_FRONT |
                                  Intent.FLAG_ACTIVITY_CLEAR_TOP);
                    try {
                        startActivity(intent);
                    } catch (Exception ignored) {}
                }, 150);
            }
        }
        root.recycle();
    }

    private void checkAndEnforce(String pkgName) {
        AppConfig config;
        synchronized (appConfigs) { config = appConfigs.get(pkgName); }
        if (config == null) return;

        String reason = null;
        if (config.blocked) {
            reason = "Esta aplicación está bloqueada.";
        } else if (config.timeLimitMinutes > 0 && getTodayUsageMinutes(pkgName) >= config.timeLimitMinutes) {
            reason = "Se agotó el tiempo de uso diario.";
        } else if (config.startTime != null && config.endTime != null && isTimeInRestrictedRange(config.startTime, config.endTime)) {
            reason = "Horario restringido.";
        }
        
        if (reason != null) {
            long now = System.currentTimeMillis();
            if (pkgName.equals(lastBlockedPackage) && (now - lastBlockTime < 2000)) return;
            lastBlockedPackage = pkgName;
            lastBlockTime = now;

            Intent intent = new Intent(this, BlockedActivity.class);
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | 
                          Intent.FLAG_ACTIVITY_REORDER_TO_FRONT | 
                          Intent.FLAG_ACTIVITY_CLEAR_TOP);
            intent.putExtra("reason", reason);
            intent.putExtra("packageName", pkgName);
            try {
                startActivity(intent);
            } catch (Exception ignored) {}
        }
    }

    private boolean isUninstallAllowed() { 
        return prefs != null && prefs.getBoolean("allow_uninstall", false); 
    }

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
