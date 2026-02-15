package com.example.parental_control_child;

import android.util.Log;
import androidx.annotation.NonNull;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.messaging.FirebaseMessagingService;
import com.google.firebase.messaging.RemoteMessage;
import java.util.HashMap;
import java.util.Map;

public class MyFirebaseMessagingService extends FirebaseMessagingService {

    private static final String TAG = "FCMService";

    @Override
    public void onMessageReceived(@NonNull RemoteMessage remoteMessage) {
        super.onMessageReceived(remoteMessage);
        // Aquí se manejan los mensajes recibidos mientras la app está en primer plano
        Log.d(TAG, "Mensaje recibido de: " + remoteMessage.getFrom());
        
        if (remoteMessage.getNotification() != null) {
            Log.d(TAG, "Cuerpo de la notificación: " + remoteMessage.getNotification().getBody());
        }
    }

    @Override
    public void onNewToken(@NonNull String token) {
        super.onNewToken(token);
        Log.d(TAG, "Nuevo token generado: " + token);
        // Guardar el token automáticamente si el usuario ya está logueado
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
                        // Si el documento no existe aún, se crea con set()
                        db.collection("children").document(user.getUid())
                                .set(data, com.google.firebase.firestore.SetOptions.merge());
                    });
        }
    }
}