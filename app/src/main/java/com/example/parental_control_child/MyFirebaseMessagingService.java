package com.example.parental_control_child;

import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.util.Log;
import androidx.annotation.NonNull;
import androidx.core.app.NotificationCompat;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.messaging.FirebaseMessagingService;
import com.google.firebase.messaging.RemoteMessage;
import java.util.HashMap;
import java.util.Map;

public class MyFirebaseMessagingService extends FirebaseMessagingService {

    private static final String TAG = "FCMService";
    private static final String CHANNEL_ID = "parental_control_channel_v2"; // v2 para asegurar importancia alta

    @Override
    public void onMessageReceived(@NonNull RemoteMessage remoteMessage) {
        super.onMessageReceived(remoteMessage);
        
        Log.d(TAG, "Mensaje recibido de: " + remoteMessage.getFrom());
        
        String title = null;
        String body = null;

        // 1. Priorizar datos personalizados (útil para cuando la app está abierta)
        if (remoteMessage.getData().size() > 0) {
            title = remoteMessage.getData().get("title");
            body = remoteMessage.getData().get("body");
            
            // Si las llaves son diferentes, intentar con "mensaje" o similares si fuera necesario
            if (title == null) title = "Alerta de Control Parental";
            if (body == null) body = remoteMessage.getData().get("message");
        }

        // 2. Si no hay datos, usar la notificación estándar de Firebase
        if (title == null && remoteMessage.getNotification() != null) {
            title = remoteMessage.getNotification().getTitle();
            body = remoteMessage.getNotification().getBody();
        }

        if (title != null || body != null) {
            showNotification(title, body);
        }
    }

    private void showNotification(String title, String body) {
        NotificationManager notificationManager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                    CHANNEL_ID,
                    "Notificaciones de Seguridad",
                    NotificationManager.IMPORTANCE_HIGH
            );
            channel.enableLights(true);
            channel.enableVibration(true);
            notificationManager.createNotificationChannel(channel);
        }

        Intent intent = new Intent(this, CodePanel.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
        PendingIntent pendingIntent = PendingIntent.getActivity(this, 0, intent,
                PendingIntent.FLAG_ONE_SHOT | PendingIntent.FLAG_IMMUTABLE);

        // Usamos ic_shield_check como icono de notificación (blanco y transparente) 
        // para que se vea bien en la barra de estado.
        NotificationCompat.Builder builder = new NotificationCompat.Builder(this, CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_shield_check) 
                .setContentTitle(title)
                .setContentText(body)
                .setAutoCancel(true)
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setDefaults(NotificationCompat.DEFAULT_ALL)
                .setContentIntent(pendingIntent);

        notificationManager.notify((int) System.currentTimeMillis(), builder.build());
    }

    @Override
    public void onNewToken(@NonNull String token) {
        super.onNewToken(token);
        Log.d(TAG, "Nuevo token: " + token);
        // Guardar en SharedPreferences por si el usuario no está logueado aún
        getSharedPreferences("FCM_PREFS", MODE_PRIVATE).edit().putString("fcm_token", token).apply();
        updateTokenInFirestore(token);
    }

    private void updateTokenInFirestore(String token) {
        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
        if (user != null) {
            FirebaseFirestore db = FirebaseFirestore.getInstance();
            Map<String, Object> data = new HashMap<>();
            data.put("fcmToken", token);
            data.put("lastTokenUpdate", com.google.firebase.Timestamp.now());

            db.collection("children").document(user.getUid())
                    .update(data)
                    .addOnFailureListener(e -> {
                        db.collection("children").document(user.getUid())
                                .set(data, com.google.firebase.firestore.SetOptions.merge());
                    });
        }
    }
}
