package com.example.parental_control_child;

import android.accessibilityservice.AccessibilityService;
import android.annotation.SuppressLint;
import android.util.Log;
import android.view.accessibility.AccessibilityEvent;

@SuppressLint("AccessibilityPolicy")
public class ParentalAccessibilityService extends AccessibilityService {

    private static final String TAG = "AccessibilityService";

    @Override
    public void onAccessibilityEvent(AccessibilityEvent event) {
        // Detectar cambios de app, clics, etc.
        if (event.getEventType() == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) {
            String packageName = event.getPackageName() != null ?
                    event.getPackageName().toString() : "";

            Log.d(TAG, "App opened: " + packageName);

            // TODO: Verificar si la app está bloqueada
            // TODO: Reportar uso al servidor
        }
    }

    @Override
    public void onInterrupt() {
        Log.d(TAG, "Service interrupted");
    }

    @Override
    protected void onServiceConnected() {
        super.onServiceConnected();
        Log.d(TAG, "Accessibility service connected");
    }
}