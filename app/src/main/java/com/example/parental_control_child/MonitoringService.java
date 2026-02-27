package com.example.parental_control_child;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.app.usage.UsageStats;
import android.app.usage.UsageStatsManager;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.content.pm.ServiceInfo;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.util.Log;

import androidx.core.app.NotificationCompat;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.DocumentChange;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.ListenerRegistration;
import com.google.firebase.firestore.SetOptions;

import java.util.Calendar;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

public class MonitoringService extends Service {

    private static final String TAG = "MonitoringService";
    private static final String CHANNEL_ID_SERVICE = "MonitoringServiceChannel";
    private static final String CHANNEL_ID_ALERTS = "ParentalAlertsChannel";
    private static final int FOREGROUND_ID = 1;
    private static final long USAGE_UPLOAD_INTERVAL = 15 * 60 * 1000;

    private FirebaseFirestore db;
    private ListenerRegistration firestoreListener;
    private final Set<String> processedMessages = new HashSet<>();
    private final Handler usageHandler = new Handler(Looper.getMainLooper());

    private final Runnable usageRunnable = new Runnable() {
        @Override
        public void run() {
            uploadAppUsageData();
            usageHandler.postDelayed(this, USAGE_UPLOAD_INTERVAL);
        }
    };

    @Override
    public void onCreate() {
        super.onCreate();
        db = FirebaseFirestore.getInstance();
        createNotificationChannels();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        Notification notification = createServiceNotification();
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(FOREGROUND_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE);
        } else {
            startForeground(FOREGROUND_ID, notification);
        }
        startListeningToFirestore();
        usageHandler.post(usageRunnable);
        return START_STICKY;
    }

    private void uploadAppUsageData() {
        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
        if (user == null) return;

        UsageStatsManager usm = (UsageStatsManager) getSystemService(Context.USAGE_STATS_SERVICE);
        PackageManager pm = getPackageManager();
        if (usm == null) return;

        Calendar calendar = Calendar.getInstance();
        long endTime = calendar.getTimeInMillis();
        calendar.set(Calendar.HOUR_OF_DAY, 0);
        calendar.set(Calendar.MINUTE, 0);
        calendar.set(Calendar.SECOND, 0);
        calendar.set(Calendar.MILLISECOND, 0);
        long startTime = calendar.getTimeInMillis();

        Map<String, UsageStats> stats = usm.queryAndAggregateUsageStats(startTime, endTime);
        
        Map<String, Object> rootData = new HashMap<>();
        Map<String, Object> appsMap = new HashMap<>();
        long totalUsageMillis = 0;

        for (Map.Entry<String, UsageStats> entry : stats.entrySet()) {
            String packageName = entry.getKey();
            long usageTime = entry.getValue().getTotalTimeInForeground();
            
            if (usageTime < 0) continue;

            try {
                if (pm.getLaunchIntentForPackage(packageName) != null) {
                    ApplicationInfo appInfo = pm.getApplicationInfo(packageName, 0);
                    String appName = pm.getApplicationLabel(appInfo).toString();
                    long usageMinutes = usageTime / (1000 * 60);
                    totalUsageMillis += usageTime;

                    String firestoreKey = packageName.replace(".", "_");

                    Map<String, Object> appDetails = new HashMap<>();
                    appDetails.put("name", appName);
                    appDetails.put("packageName", packageName);
                    appDetails.put("usageTimeMinutes", usageMinutes);
                    
                    // IMPORTANTE: NO enviamos 'blocked' ni 'timeLimitMinutes' desde aquí.
                    // Esto permite que el MonitoringService actualice el tiempo de uso
                    // sin sobreescribir las restricciones puestas por el padre.
                    appsMap.put(firestoreKey, appDetails);
                }
            } catch (PackageManager.NameNotFoundException ignored) {}
        }

        rootData.put("totalUsageTimeMinutes", totalUsageMillis / (1000 * 60));
        rootData.put("appsMap", appsMap);
        rootData.put("lastUpdate", com.google.firebase.Timestamp.now());

        db.collection("children").document(user.getUid())
                .set(rootData, SetOptions.merge())
                .addOnSuccessListener(aVoid -> Log.d(TAG, "Sincronización de uso completada"))
                .addOnFailureListener(e -> Log.e(TAG, "Error al sincronizar uso", e));
    }

    private void startListeningToFirestore() {
        if (firestoreListener != null) return;
        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
        if (user == null) return;

        firestoreListener = db.collection("notifications_queue")
                .whereEqualTo("toChildId", user.getUid())
                .addSnapshotListener((snapshots, e) -> {
                    if (e != null || snapshots == null) return;
                    for (DocumentChange dc : snapshots.getDocumentChanges()) {
                        if (dc.getType() == DocumentChange.Type.ADDED) {
                            String docId = dc.getDocument().getId();
                            if (processedMessages.contains(docId)) continue;
                            processedMessages.add(docId);

                            showSystemNotification(
                                dc.getDocument().getString("title"),
                                dc.getDocument().getString("message")
                            );
                            dc.getDocument().getReference().delete();
                        }
                    }
                });
    }

    private void showSystemNotification(String title, String message) {
        NotificationManager manager = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
        if (manager == null) return;

        Intent intent = new Intent(this, HomeActivity.class);
        PendingIntent pi = PendingIntent.getActivity(this, 0, intent, 
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

        NotificationCompat.Builder builder = new NotificationCompat.Builder(this, CHANNEL_ID_ALERTS)
                .setSmallIcon(android.R.drawable.ic_dialog_info)
                .setContentTitle(title)
                .setContentText(message)
                .setPriority(NotificationCompat.PRIORITY_MAX)
                .setFullScreenIntent(pi, true)
                .setAutoCancel(true);

        manager.notify((int)System.currentTimeMillis(), builder.build());
    }

    private void createNotificationChannels() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationManager manager = getSystemService(NotificationManager.class);
            if (manager != null) {
                manager.createNotificationChannel(new NotificationChannel(
                        CHANNEL_ID_SERVICE, "Servicio", NotificationManager.IMPORTANCE_LOW));
                manager.createNotificationChannel(new NotificationChannel(
                        CHANNEL_ID_ALERTS, "Alertas", NotificationManager.IMPORTANCE_HIGH));
            }
        }
    }

    private Notification createServiceNotification() {
        return new NotificationCompat.Builder(this, CHANNEL_ID_SERVICE)
                .setContentTitle("Control Activo")
                .setSmallIcon(android.R.drawable.ic_lock_idle_lock)
                .build();
    }

    @Override
    public void onDestroy() {
        if (firestoreListener != null) firestoreListener.remove();
        usageHandler.removeCallbacks(usageRunnable);
        super.onDestroy();
    }

    @Override
    public IBinder onBind(Intent intent) { return null; }
}
