package com.example.parental_control_child;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Intent;
import android.content.pm.ServiceInfo;
import android.os.Build;
import android.os.IBinder;
import android.util.Log;

import androidx.core.app.NotificationCompat;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.DocumentChange;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.ListenerRegistration;
import com.google.firebase.firestore.Query;

import java.util.HashSet;
import java.util.Set;

public class MonitoringService extends Service {

    private static final String TAG = "MonitoringService";
    private static final String CHANNEL_ID_SERVICE = "MonitoringServiceChannel";
    private static final String CHANNEL_ID_ALERTS = "ParentalAlertsChannel";
    private static final int FOREGROUND_ID = 1;

    private FirebaseFirestore db;
    private ListenerRegistration firestoreListener;
    private final Set<String> processedMessages = new HashSet<>();

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

        return START_STICKY;
    }

    private void startListeningToFirestore() {
        if (firestoreListener != null) return;

        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
        if (user == null) {
            Log.e(TAG, "No hay usuario autenticado");
            return;
        }

        String childId = user.getUid();
        Log.d(TAG, "Escuchando notificaciones para UID: " + childId);

        Query query = db.collection("notifications_queue")
                .whereEqualTo("toChildId", childId);

        firestoreListener = query.addSnapshotListener((snapshots, e) -> {
            if (e != null) {
                Log.e(TAG, "Error en Firestore Listener: ", e);
                return;
            }

            if (snapshots != null) {
                for (DocumentChange dc : snapshots.getDocumentChanges()) {
                    if (dc.getType() == DocumentChange.Type.ADDED) {
                        String docId = dc.getDocument().getId();
                        
                        // Evitar procesar el mismo mensaje varias veces en la misma sesión
                        if (processedMessages.contains(docId)) continue;
                        processedMessages.add(docId);

                        String title = dc.getDocument().getString("title");
                        String message = dc.getDocument().getString("message");
                        
                        Log.d(TAG, "¡MENSAJE RECIBIDO! Mostrando notificación: " + title);
                        showSystemNotification(title, message);

                        // BORRADO INMEDIATO PARA EVITAR BUCLES
                        dc.getDocument().getReference().delete()
                            .addOnSuccessListener(aVoid -> Log.d(TAG, "Documento borrado de la cola"))
                            .addOnFailureListener(err -> Log.e(TAG, "Error al borrar documento", err));
                    }
                }
            }
        });
    }

    private void showSystemNotification(String title, String message) {
        NotificationManager manager = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
        if (manager == null) return;

        int notificationId = (int) System.currentTimeMillis();

        Intent fullScreenIntent = new Intent(this, HomeActivity.class);
        PendingIntent fullScreenPendingIntent = PendingIntent.getActivity(this, 0,
                fullScreenIntent, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

        NotificationCompat.Builder builder = new NotificationCompat.Builder(this, CHANNEL_ID_ALERTS)
                .setSmallIcon(android.R.drawable.ic_dialog_alert)
                .setContentTitle(title != null ? title : "Aviso Parental")
                .setContentText(message != null ? message : "")
                .setPriority(NotificationCompat.PRIORITY_MAX) 
                .setCategory(NotificationCompat.CATEGORY_ALARM)
                .setDefaults(Notification.DEFAULT_ALL)
                .setVibrate(new long[]{0, 500, 100, 500})
                .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
                .setAutoCancel(true)
                .setFullScreenIntent(fullScreenPendingIntent, true);

        manager.notify(notificationId, builder.build());
    }

    private void createNotificationChannels() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationManager manager = getSystemService(NotificationManager.class);
            if (manager == null) return;

            manager.deleteNotificationChannel(CHANNEL_ID_ALERTS);

            NotificationChannel serviceChannel = new NotificationChannel(
                    CHANNEL_ID_SERVICE, "Protección", NotificationManager.IMPORTANCE_LOW);
            
            NotificationChannel alertsChannel = new NotificationChannel(
                    CHANNEL_ID_ALERTS, "Alertas de Padres", NotificationManager.IMPORTANCE_HIGH);
            alertsChannel.enableVibration(true);
            alertsChannel.setLockscreenVisibility(Notification.VISIBILITY_PUBLIC);

            manager.createNotificationChannel(serviceChannel);
            manager.createNotificationChannel(alertsChannel);
        }
    }

    private Notification createServiceNotification() {
        return new NotificationCompat.Builder(this, CHANNEL_ID_SERVICE)
                .setContentTitle("Escudo Parental Activo")
                .setContentText("Tu dispositivo está protegido")
                .setSmallIcon(android.R.drawable.ic_lock_idle_lock)
                .setOngoing(true)
                .build();
    }

    @Override
    public void onDestroy() {
        if (firestoreListener != null) firestoreListener.remove();
        super.onDestroy();
    }

    @Override
    public IBinder onBind(Intent intent) { return null; }
}
