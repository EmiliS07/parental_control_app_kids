package com.example.parental_control_child;

import android.accessibilityservice.AccessibilityService;
import android.accessibilityservice.AccessibilityServiceInfo;
import android.util.Log;
import android.view.accessibility.AccessibilityEvent;
import android.widget.Toast;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.ListenerRegistration;

import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

public class ParentalAccessibilityService extends AccessibilityService {

    private static final String TAG = "ParentalAccessibility";
    private FirebaseFirestore db;
    private ListenerRegistration blockListener;
    private final Set<String> blockedPackages = new HashSet<>();
    private final Map<String, TimeRange> appTimeRanges = new HashMap<>();
    private String lastBlockedAppToast = "";

    private static class TimeRange {
        String startTime;
        String endTime;

        TimeRange(String startTime, String endTime) {
            this.startTime = startTime;
            this.endTime = endTime;
        }
    }

    @Override
    protected void onServiceConnected() {
        super.onServiceConnected();
        db = FirebaseFirestore.getInstance();
        
        AccessibilityServiceInfo info = new AccessibilityServiceInfo();
        info.eventTypes = AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED | 
                         AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED;
        info.feedbackType = AccessibilityServiceInfo.FEEDBACK_GENERIC;
        info.flags = AccessibilityServiceInfo.FLAG_INCLUDE_NOT_IMPORTANT_VIEWS | 
                    AccessibilityServiceInfo.FLAG_RETRIEVE_INTERACTIVE_WINDOWS;
        info.notificationTimeout = 100;
        setServiceInfo(info);

        startListeningToBlocks();
    }

    private void startListeningToBlocks() {
        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
        if (user == null) {
            Log.e(TAG, "ERROR: Usuario no autenticado en Accesibilidad. Reintentando en 3s...");
            new android.os.Handler().postDelayed(this::startListeningToBlocks, 3000);
            return;
        }

        String uid = user.getUid();
        if (blockListener != null) blockListener.remove();

        blockListener = db.collection("children").document(uid)
                .addSnapshotListener((snapshot, e) -> {
                    if (e != null) {
                        Log.e(TAG, "Error Firestore Listener: " + e.getMessage());
                        return;
                    }

                    if (snapshot != null && snapshot.exists()) {
                        updateBlockedList(snapshot);
                    }
                });
    }

    private void updateBlockedList(DocumentSnapshot snapshot) {
        Set<String> newBlockedList = new HashSet<>();
        Map<String, TimeRange> newTimeRanges = new HashMap<>();
        
        try {
            Map<String, Object> appsMap = (Map<String, Object>) snapshot.get("appsMap");
            if (appsMap != null) {
                for (Object value : appsMap.values()) {
                    if (value instanceof Map) {
                        Map<String, Object> appData = (Map<String, Object>) value;
                        String pkg = (String) appData.get("packageName");
                        if (pkg == null) continue;
                        pkg = pkg.trim();

                        // 1. Verificar bloqueo directo
                        Boolean isBlocked = (Boolean) appData.get("blocked");
                        if (isBlocked != null && isBlocked) {
                            newBlockedList.add(pkg);
                        }

                        // 2. Verificar rango de tiempo
                        String startTime = (String) appData.get("startTime");
                        String endTime = (String) appData.get("endTime");
                        if (startTime != null && !startTime.isEmpty() && endTime != null && !endTime.isEmpty()) {
                            newTimeRanges.put(pkg, new TimeRange(startTime, endTime));
                        }
                    }
                }
            }
        } catch (Exception e) {
            Log.e(TAG, "Error al procesar appsMap: " + e.getMessage());
        }

        synchronized (blockedPackages) {
            blockedPackages.clear();
            blockedPackages.addAll(newBlockedList);
            appTimeRanges.clear();
            appTimeRanges.putAll(newTimeRanges);
        }
        Log.d(TAG, "Sincronización: " + blockedPackages.size() + " apps bloqueadas, " + appTimeRanges.size() + " con horario.");
    }

    @Override
    public void onAccessibilityEvent(AccessibilityEvent event) {
        if (event.getPackageName() == null) return;
        String pkgName = event.getPackageName().toString();
        if (pkgName.equals(getPackageName()) || pkgName.equals("android")) return;

        boolean shouldBlock = false;

        synchronized (blockedPackages) {
            // Caso 1: Bloqueo manual total
            if (blockedPackages.contains(pkgName)) {
                shouldBlock = true;
            } 
            // Caso 2: Bloqueo por horario
            else if (appTimeRanges.containsKey(pkgName)) {
                TimeRange range = appTimeRanges.get(pkgName);
                if (isTimeInRange(range.startTime, range.endTime)) {
                    shouldBlock = true;
                }
            }
        }

        if (shouldBlock) {
            performGlobalAction(GLOBAL_ACTION_HOME);
            if (!lastBlockedAppToast.equals(pkgName)) {
                Toast.makeText(this, "⚠️ Aplicación bloqueada por horario", Toast.LENGTH_SHORT).show();
                lastBlockedAppToast = pkgName;
            }
        }
    }

    private boolean isTimeInRange(String start, String end) {
        try {
            SimpleDateFormat sdf = new SimpleDateFormat("HH:mm", Locale.getDefault());
            String currentTimeStr = sdf.format(new Date());
            
            Date now = sdf.parse(currentTimeStr);
            Date startTime = sdf.parse(start);
            Date endTime = sdf.parse(end);

            if (now == null || startTime == null || endTime == null) return false;

            // Manejo de rangos que cruzan la medianoche (ej: 22:00 a 06:00)
            if (endTime.before(startTime)) {
                return now.after(startTime) || now.before(endTime);
            } else {
                return now.after(startTime) && now.before(endTime);
            }
        } catch (Exception e) {
            Log.e(TAG, "Error comparando horas: " + e.getMessage());
            return false;
        }
    }

    @Override public void onInterrupt() {}
}
