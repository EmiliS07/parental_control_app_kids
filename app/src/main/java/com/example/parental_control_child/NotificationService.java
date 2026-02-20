package com.example.parental_control_child;

import android.content.ComponentName;
import android.content.Context;
import android.content.pm.PackageManager;
import android.service.notification.NotificationListenerService;
import android.service.notification.StatusBarNotification;
import android.util.Log;

public class NotificationService extends NotificationListenerService {

    private static final String TAG = "NotificationService";

    @Override
    public void onListenerConnected() {
        super.onListenerConnected();
        Log.d(TAG, "Notification Listener Connected");
    }

    @Override
    public void onListenerDisconnected() {
        super.onListenerDisconnected();
        Log.d(TAG, "Notification Listener Disconnected");
    }

    @Override
    public void onNotificationPosted(StatusBarNotification sbn) {
        if (sbn == null || sbn.getNotification() == null) return;

        try {
            String packageName = sbn.getPackageName();
            String title = sbn.getNotification().extras.getString("android.title");
            String text = sbn.getNotification().extras.getString("android.text");

            Log.d(TAG, "Notification from: " + packageName);
            Log.d(TAG, "Title: " + (title != null ? title : "null"));
            Log.d(TAG, "Text: " + (text != null ? text : "null"));
        } catch (Exception e) {
            Log.e(TAG, "Error reading notification data", e);
        }
    }

    @Override
    public void onNotificationRemoved(StatusBarNotification sbn) {
        // Notificación eliminada
    }

    /**
     * Force-rebinds the service by toggling its enabled state.
     * Useful when the system gets into a "Service not registered" inconsistent state.
     */
    public static void ensureServiceEnabled(Context context) {
        ComponentName componentName = new ComponentName(context, NotificationService.class);
        PackageManager pm = context.getPackageManager();
        pm.setComponentEnabledSetting(componentName,
                PackageManager.COMPONENT_ENABLED_STATE_DISABLED, PackageManager.DONT_KILL_APP);
        pm.setComponentEnabledSetting(componentName,
                PackageManager.COMPONENT_ENABLED_STATE_ENABLED, PackageManager.DONT_KILL_APP);
    }
}
