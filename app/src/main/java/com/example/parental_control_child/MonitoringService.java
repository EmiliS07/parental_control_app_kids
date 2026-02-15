package com.example.parental_control_child;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.content.Intent;
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
    private static final String CHANNEL_ID = "ParentalControlChannel";
    private static final String NOTIF_QUEUE_CHANNEL = "ParentalMessagesChannel";
    private static final int FOREGROUND_ID = 1;

    private FirebaseFirestore db;
    private ListenerRegistration notificationListener;

    @Override
    public void onCreate() {
        super.onCreate();
        db = FirebaseFirestore.getInstance();
        createNotificationChannels();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        startForeground(FOREGROUND_ID, createForegroundNotification());

        // Iniciar la escucha de la cola de notificaciones
        startListeningForNotifications();

        return START_STICKY;
    }

    private void startListeningForNotifications() {
        FirebaseUser currentUser = FirebaseAuth.getInstance().getCurrentUser();
        if (currentUser == null) return;

        String childId = currentUser.getUid();

        // Consulta: Notificaciones para este niño, ordenadas por tiempo
        Query query = db.collection("notifications_queue")
                .whereEqualTo("toChildId", childId)
                .orderBy("timestamp", Query.Direction.ASCENDING);

        notificationListener = query.addSnapshotListener((snapshots, e) -> {
            if (e != null) {
                Log.e(TAG, "Error escuchando cola: ", e);
                return;
            }

            if (snapshots != null) {
                for (DocumentChange dc : snapshots.getDocumentChanges()) {
                    if (dc.getType() == DocumentChange.Type.ADDED) {
                        // Extraer datos del mensaje
                        String title = dc.getDocument().getString("title");
                        String message = dc.getDocument().getString("message");
                        
                        // Mostrar notificación local
                        showLocalNotification(title, message);
                        
                        // OPCIONAL: Eliminar el mensaje de la cola tras recibirlo
                        // dc.getDocument().getReference().delete();
                    }
                }
            }
        });
    }

    private void showLocalNotification(String title, String message) {
        NotificationManager manager = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
        int notifId = (int) System.currentTimeMillis();

        Notification localNotif = new NotificationCompat.Builder(this, NOTIF_QUEUE_CHANNEL)
                .setContentTitle(title != null ? title : "Mensaje de Padre")
                .setContentText(message != null ? message : "")
                .setSmallIcon(android.R.drawable.ic_dialog_info)
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setAutoCancel(true)
                .build();

        if (manager != null) {
            manager.notify(notifId, localNotif);
        }
    }

    private void createNotificationChannels() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationManager manager = getSystemService(NotificationManager.class);
            
            // Canal para el servicio persistente
            NotificationChannel serviceChannel = new NotificationChannel(
                    CHANNEL_ID, "Servicio de Monitoreo", NotificationManager.IMPORTANCE_LOW);
            
            // Canal para los mensajes recibidos (importancia alta para que suene)
            NotificationChannel msgChannel = new NotificationChannel(
                    NOTIF_QUEUE_CHANNEL, "Mensajes de Padres", NotificationManager.IMPORTANCE_HIGH);
            
            if (manager != null) {
                manager.createNotificationChannel(serviceChannel);
                manager.createNotificationChannel(msgChannel);
            }
        }
    }

    private Notification createForegroundNotification() {
        return new NotificationCompat.Builder(this, CHANNEL_ID)
                .setContentTitle("Control Parental Activo")
                .setContentText("Escuchando actualizaciones...")
                .setSmallIcon(android.R.drawable.ic_lock_idle_lock)
                .setPriority(NotificationCompat.PRIORITY_LOW)
                .setOngoing(true)
                .build();
    }

    @Override
    public void onDestroy() {
        if (notificationListener != null) notificationListener.remove();
        super.onDestroy();
    }

    @Override
    public IBinder onBind(Intent intent) { return null; }
}