package com.example.parental_control_child;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
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

public class MonitoringService extends Service {

    private static final String TAG = "MonitoringService";
    private static final String CHANNEL_ID_SERVICE = "MonitoringServiceChannel";
    private static final String CHANNEL_ID_ALERTS = "ParentalAlertsChannel";
    private static final int FOREGROUND_ID = 1;

    private FirebaseFirestore db;
    private ListenerRegistration firestoreListener;

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
        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
        if (user == null) {
            Log.e(TAG, "No hay usuario autenticado para escuchar notificaciones");
            return;
        }

        String childId = user.getUid();

        Query query = db.collection("notifications_queue")
                .whereEqualTo("toChildId", childId)
                .orderBy("timestamp", Query.Direction.DESCENDING)
                .limit(5);

        if (firestoreListener != null) firestoreListener.remove();

        firestoreListener = query.addSnapshotListener((snapshots, e) -> {
            if (e != null) {
                Log.e(TAG, "Error en Firestore Listener: ", e);
                return;
            }

            if (snapshots != null) {
                for (DocumentChange dc : snapshots.getDocumentChanges()) {
                    if (dc.getType() == DocumentChange.Type.ADDED) {
                        String title = dc.getDocument().getString("title");
                        String message = dc.getDocument().getString("message");
                        
                        showSystemNotification(title, message);

                        // Eliminar el documento de la cola para no volver a mostrarlo
                        dc.getDocument().getReference().delete();
                    }
                }
            }
        });
    }

    private void showSystemNotification(String title, String message) {
        NotificationManager manager = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
        int notificationId = (int) System.currentTimeMillis();

        NotificationCompat.Builder builder = new NotificationCompat.Builder(this, CHANNEL_ID_ALERTS)
                .setSmallIcon(android.R.drawable.ic_dialog_info)
                .setContentTitle(title != null ? title : "Mensaje de tus padres")
                .setContentText(message != null ? message : "")
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setCategory(NotificationCompat.CATEGORY_MESSAGE)
                .setAutoCancel(true)
                .setDefaults(Notification.DEFAULT_ALL);

        if (manager != null) {
            manager.notify(notificationId, builder.build());
        }
    }

    private void createNotificationChannels() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationManager manager = getSystemService(NotificationManager.class);
            if (manager == null) return;

            NotificationChannel serviceChannel = new NotificationChannel(
                    CHANNEL_ID_SERVICE, "Servicio de Protección", NotificationManager.IMPORTANCE_LOW);
            
            NotificationChannel alertsChannel = new NotificationChannel(
                    CHANNEL_ID_ALERTS, "Mensajes de Padres", NotificationManager.IMPORTANCE_HIGH);
            alertsChannel.enableVibration(true);
            alertsChannel.enableLights(true);

            manager.createNotificationChannel(serviceChannel);
            manager.createNotificationChannel(alertsChannel);
        }
    }

    private Notification createServiceNotification() {
        return new NotificationCompat.Builder(this, CHANNEL_ID_SERVICE)
                .setContentTitle("Protección activa")
                .setContentText("Tu dispositivo está protegido por tus padres")
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
