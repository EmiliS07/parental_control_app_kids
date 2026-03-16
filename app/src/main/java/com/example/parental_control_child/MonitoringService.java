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
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
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
import com.google.firebase.storage.FirebaseStorage;
import com.google.firebase.storage.StorageReference;

import java.io.ByteArrayOutputStream;
import java.util.Calendar;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class MonitoringService extends Service {

    private static final String TAG = "MONITOR_SERVICE";
    private static final String CHANNEL_ID_SERVICE = "MonitoringServiceChannel";
    private static final String CHANNEL_ID_ALERTS = "ParentalAlertsChannel";
    private static final int FOREGROUND_ID = 1;
    private static final long USAGE_UPLOAD_INTERVAL = 15 * 60 * 1000;

    private FirebaseFirestore db;
    private FirebaseStorage storage;
    private ListenerRegistration firestoreListener;
    private final Set<String> processedMessages = new HashSet<>();
    private final Handler usageHandler = new Handler(Looper.getMainLooper());

    private final Runnable usageRunnable = new Runnable() {
        @Override
        public void run() {
            syncAppsAndUsage();
            usageHandler.postDelayed(this, USAGE_UPLOAD_INTERVAL);
        }
    };

    @Override
    public void onCreate() {
        super.onCreate();
        db = FirebaseFirestore.getInstance();
        storage = FirebaseStorage.getInstance();
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
        syncAppsAndUsage();
        
        usageHandler.removeCallbacks(usageRunnable);
        usageHandler.postDelayed(usageRunnable, USAGE_UPLOAD_INTERVAL);
        
        return START_STICKY;
    }

    private void syncAppsAndUsage() {
        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
        if (user == null) {
            FirebaseAuth.getInstance().signInAnonymously().addOnSuccessListener(authResult -> syncAppsAndUsage());
            return;
        }

        String uid = user.getUid();
        
        db.collection("children").document(uid).get().addOnSuccessListener(documentSnapshot -> {
            Map<String, Object> currentAppsMap = new HashMap<>();
            if (documentSnapshot.exists() && documentSnapshot.get("appsMap") instanceof Map) {
                currentAppsMap = (Map<String, Object>) documentSnapshot.get("appsMap");
            }

            PackageManager pm = getPackageManager();
            UsageStatsManager usm = (UsageStatsManager) getSystemService(Context.USAGE_STATS_SERVICE);
            
            Calendar calendar = Calendar.getInstance();
            long endTime = calendar.getTimeInMillis();
            calendar.set(Calendar.HOUR_OF_DAY, 0);
            calendar.set(Calendar.MINUTE, 0);
            long startTime = calendar.getTimeInMillis();
            Map<String, UsageStats> usageStatsMap = (usm != null) ? usm.queryAndAggregateUsageStats(startTime, endTime) : new HashMap<>();

            List<ApplicationInfo> installedApps = pm.getInstalledApplications(PackageManager.GET_META_DATA);
            Map<String, Object> appsMapToUpdate = new HashMap<>();
            
            for (ApplicationInfo appInfo : installedApps) {
                String packageName = appInfo.packageName;
                if (pm.getLaunchIntentForPackage(packageName) != null) {
                    String appName = pm.getApplicationLabel(appInfo).toString();
                    String firestoreKey = packageName.replace(".", "_");
                    
                    long usageMinutes = 0;
                    if (usageStatsMap.containsKey(packageName)) {
                        usageMinutes = usageStatsMap.get(packageName).getTotalTimeInForeground() / (1000 * 60);
                    }

                    Map<String, Object> appData = new HashMap<>();
                    appData.put("name", appName);
                    appData.put("packageName", packageName);
                    appData.put("usageTimeMinutes", usageMinutes);
                    
                    appsMapToUpdate.put(firestoreKey, appData);
                }
            }

            Map<String, Object> rootData = new HashMap<>();
            rootData.put("appsMap", appsMapToUpdate);
            rootData.put("lastUpdate", com.google.firebase.Timestamp.now());

            // 1. Guardamos la estructura básica y minutos de uso
            Map<String, Object> finalCurrentAppsMap = currentAppsMap;
            db.collection("children").document(uid)
                    .set(rootData, SetOptions.merge())
                    .addOnSuccessListener(aVoid -> {
                        // 2. Una vez guardado el uso, disparamos la subida de iconos que falten
                        for (String key : appsMapToUpdate.keySet()) {
                            Map<String, Object> appInfo = (Map<String, Object>) appsMapToUpdate.get(key);
                            String pkg = (String) appInfo.get("packageName");
                            
                            boolean hasIcon = false;
                            if (finalCurrentAppsMap.containsKey(key)) {
                                Map<String, Object> existing = (Map<String, Object>) finalCurrentAppsMap.get(key);
                                if (existing != null && existing.get("iconUrl") != null) hasIcon = true;
                            }

                            if (!hasIcon) {
                                performIconUpload(pkg, uid, key);
                            }
                        }
                    });
            
        });
    }

    private void performIconUpload(String packageName, String childId, String firestoreKey) {
        try {
            PackageManager pm = getPackageManager();
            Drawable icon = pm.getApplicationIcon(packageName);
            Bitmap bitmap = drawableToBitmap(icon);
            
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            bitmap.compress(Bitmap.CompressFormat.PNG, 70, baos); // Comprimimos para rapidez
            byte[] data = baos.toByteArray();

            StorageReference iconRef = storage.getReference().child("app_icons/" + packageName + ".png");
            iconRef.putBytes(data).addOnSuccessListener(task -> {
                iconRef.getDownloadUrl().addOnSuccessListener(uri -> {
                    String url = uri.toString();
                    // Actualización puntual: SOLO escribimos el iconUrl sin tocar lo demás
                    db.collection("children").document(childId)
                            .update("appsMap." + firestoreKey + ".iconUrl", url)
                            .addOnSuccessListener(v -> Log.d(TAG, "Icono vinculado: " + packageName));
                });
            });
        } catch (Exception ignored) {}
    }

    private Bitmap drawableToBitmap(Drawable drawable) {
        if (drawable instanceof BitmapDrawable) return ((BitmapDrawable) drawable).getBitmap();
        int w = Math.max(1, drawable.getIntrinsicWidth());
        int h = Math.max(1, drawable.getIntrinsicHeight());
        // Limitar tamaño para no saturar Storage
        if (w > 256) w = 256;
        if (h > 256) h = 256;
        Bitmap bitmap = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(bitmap);
        drawable.setBounds(0, 0, canvas.getWidth(), canvas.getHeight());
        drawable.draw(canvas);
        return bitmap;
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
        NotificationCompat.Builder builder = new NotificationCompat.Builder(this, CHANNEL_ID_ALERTS)
                .setSmallIcon(android.R.drawable.ic_dialog_info)
                .setContentTitle(title)
                .setContentText(message)
                .setPriority(NotificationCompat.PRIORITY_HIGH)
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
                .setContentTitle("Monitoreo Activo")
                .setSmallIcon(android.R.drawable.ic_lock_idle_lock)
                .build();
    }

    @Override public void onDestroy() { 
        if (firestoreListener != null) firestoreListener.remove();
        usageHandler.removeCallbacks(usageRunnable);
        super.onDestroy(); 
    }
    @Override public IBinder onBind(Intent intent) { return null; }
}
