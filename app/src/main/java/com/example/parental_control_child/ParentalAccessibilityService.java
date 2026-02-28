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

import java.util.HashSet;
import java.util.Map;
import java.util.Set;

public class ParentalAccessibilityService extends AccessibilityService {

    private static final String TAG = "ParentalAccessibility";
    private FirebaseFirestore db;
    private ListenerRegistration blockListener;
    private final Set<String> blockedPackages = new HashSet<>();
    private String lastBlockedAppToast = "";

    @Override
    protected void onServiceConnected() {
        super.onServiceConnected();
        db = FirebaseFirestore.getInstance();
        
        AccessibilityServiceInfo info = new AccessibilityServiceInfo();
        // Escuchamos múltiples eventos para asegurar que no se escape nada
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
        Log.d(TAG, ">>> INICIANDO ESCUCHA PARA UID: " + uid);

        if (blockListener != null) blockListener.remove();

        blockListener = db.collection("children").document(uid)
                .addSnapshotListener((snapshot, e) -> {
                    if (e != null) {
                        Log.e(TAG, "Error Firestore Listener: " + e.getMessage());
                        return;
                    }

                    if (snapshot != null && snapshot.exists()) {
                        updateBlockedList(snapshot);
                    } else {
                        Log.w(TAG, "El documento del niño no existe en Firestore.");
                    }
                });
    }

    private void updateBlockedList(DocumentSnapshot snapshot) {
        Set<String> newList = new HashSet<>();
        try {
            Map<String, Object> appsMap = (Map<String, Object>) snapshot.get("appsMap");
            if (appsMap != null) {
                for (Object value : appsMap.values()) {
                    if (value instanceof Map) {
                        Map<String, Object> appData = (Map<String, Object>) value;
                        Boolean isBlocked = (Boolean) appData.get("blocked");
                        String pkg = (String) appData.get("packageName");
                        if (isBlocked != null && isBlocked && pkg != null) {
                            newList.add(pkg.trim());
                        }
                    }
                }
            }
        } catch (Exception e) {
            Log.e(TAG, "Error al procesar appsMap: " + e.getMessage());
        }

        synchronized (blockedPackages) {
            blockedPackages.clear();
            blockedPackages.addAll(newList);
        }
        Log.d(TAG, "LISTA ACTUALIZADA: " + blockedPackages.size() + " apps: " + blockedPackages);
    }

    @Override
    public void onAccessibilityEvent(AccessibilityEvent event) {
        // Verificamos el paquete en cada cambio de estado de ventana
        if (event.getPackageName() == null) return;
        
        String pkgName = event.getPackageName().toString();
        
        // Evitar bucle infinito con nuestra propia app o el sistema
        if (pkgName.equals(getPackageName()) || pkgName.equals("android")) return;

        boolean block;
        synchronized (blockedPackages) {
            block = blockedPackages.contains(pkgName);
        }

        if (block) {
            Log.d(TAG, "!!! INTENTO DE ACCESO A APP BLOQUEADA: " + pkgName);
            performGlobalAction(GLOBAL_ACTION_HOME);
            
            if (!lastBlockedAppToast.equals(pkgName)) {
                Toast.makeText(this, "⚠️ Aplicación bloqueada", Toast.LENGTH_SHORT).show();
                lastBlockedAppToast = pkgName;
            }
        }
    }

    @Override public void onInterrupt() {}
}
