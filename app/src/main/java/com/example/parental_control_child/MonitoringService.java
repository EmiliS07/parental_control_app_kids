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
            Log.e(TAG, "No hay usuario autenticado");
            return;
        }

        String childId = user.getUid();
        Log.d(TAG, "Escuchando notificaciones para UID: " + childId);

        Query query = db.collection("notifications_queue")
                .whereEqualTo("toChildId", childId);

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
                        
                        Log.d(TAG, "¡MENSAJE RECIBIDO! Mostrando notificación: " + title);
                        showSystemNotification(title, message);

                        dc.getDocument().getReference().delete();
                    }
                }
            }
        });
    }

    private void showSystemNotification(String title, String message) {
        NotificationManager manager = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
        if (manager == null) {
            Log.e(TAG, "NotificationManager es nulo, no se puede mostrar la notificación");
            return;
        }

        int notificationId = (int) System.currentTimeMillis();

        Intent fullScreenIntent = new Intent(this, HomeActivity.class);
        PendingIntent fullScreenPendingIntent = PendingIntent.getActivity(this, 0,
                fullScreenIntent, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

        NotificationCompat.Builder builder = new NotificationCompat.Builder(this, CHANNEL_ID_ALERTS)
                .setSmallIcon(android.R.drawable.ic_dialog_alert)
                .setContentTitle(title != null ? title : "Mensaje de tus padres")
                .setContentText(message != null ? message : "")
                .setPriority(NotificationCompat.PRIORITY_MAX) 
                .setCategory(NotificationCompat.CATEGORY_CALL) // Categoría de llamada es la más urgente
                .setDefaults(Notification.DEFAULT_ALL)
                .setVibrate(new long[]{0, 500, 100, 500}) // Patrón de vibración
                .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
                .setAutoCancel(true)
                // ¡ESTA ES LA CLAVE!
                .setFullScreenIntent(fullScreenPendingIntent, true);

        Log.d(TAG, "Notificando con ID: " + notificationId);
        manager.notify(notificationId, builder.build());
    }

    private void createNotificationChannels() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationManager manager = getSystemService(NotificationManager.class);
            if (manager == null) return;

            // Borrar el canal viejo para asegurar que se aplique la nueva configuración de importancia
            manager.deleteNotificationChannel(CHANNEL_ID_ALERTS);

            NotificationChannel serviceChannel = new NotificationChannel(
                    CHANNEL_ID_SERVICE, "Servicio de Protección", NotificationManager.IMPORTANCE_LOW);
            
            // Canal para las alertas, con importancia máxima
            NotificationChannel alertsChannel = new NotificationChannel(
                    CHANNEL_ID_ALERTS, "Alertas de Padres", NotificationManager.IMPORTANCE_HIGH);
            alertsChannel.enableVibration(true);
            alertsChannel.setLockscreenVisibility(Notification.VISIBILITY_PUBLIC);
            // Opcional: saltar el modo "No molestar"
            alertsChannel.setBypassDnd(true);

            manager.createNotificationChannel(serviceChannel);
            manager.createNotificationChannel(alertsChannel);
        }
    }

    private Notification createServiceNotification() {
        return new NotificationCompat.Builder(this, CHANNEL_ID_SERVICE)
                .setContentTitle("Protección activa")
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
