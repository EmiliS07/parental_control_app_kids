package com.example.parental_control_child;

import android.annotation.SuppressLint;
import android.content.Context;
import android.location.Location;
import android.net.wifi.WifiInfo;
import android.net.wifi.WifiManager;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.work.Worker;
import androidx.work.WorkerParameters;

import com.google.android.gms.location.FusedLocationProviderClient;
import com.google.android.gms.location.LocationServices;
import com.google.android.gms.location.Priority;
import com.google.android.gms.tasks.Tasks;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.FieldValue;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.SetOptions;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

public class LocationWorker extends Worker {

    private static final String TAG = "LocationWorker";

    public LocationWorker(@NonNull Context context, @NonNull WorkerParameters workerParams) {
        super(context, workerParams);
    }

    @NonNull
    @Override
    public Result doWork() {
        Log.d(TAG, "--- [INICIO] Ejecutando tarea de ubicación ---");

        FirebaseAuth auth = FirebaseAuth.getInstance();
        
        // 1. Verificar/Forzar Autenticación
        if (auth.getCurrentUser() == null) {
            Log.d(TAG, "No hay sesión activa. Intentando login anónimo...");
            try {
                Tasks.await(auth.signInAnonymously(), 10, TimeUnit.SECONDS);
                Log.d(TAG, "Login anónimo exitoso: " + auth.getUid());
            } catch (Exception e) {
                Log.e(TAG, "Error crítico: No se pudo autenticar al niño: " + e.getMessage());
                return Result.retry();
            }
        }

        String uid = auth.getUid();
        if (uid == null) return Result.failure();

        try {
            // 2. Obtener ubicación con GPS (espera hasta 25 seg)
            FusedLocationProviderClient locationClient = LocationServices.getFusedLocationProviderClient(getApplicationContext());
            
            Log.d(TAG, "Solicitando ubicación al GPS...");
            @SuppressLint("MissingPermission")
            Location location = Tasks.await(
                    locationClient.getCurrentLocation(Priority.PRIORITY_HIGH_ACCURACY, null),
                    25, TimeUnit.SECONDS
            );

            if (location != null) {
                Log.d(TAG, "Coordenadas obtenidas: " + location.getLatitude() + ", " + location.getLongitude());
                
                // 3. Guardar en Firestore
                saveToFirestoreSync(uid, location);
                return Result.success();
            } else {
                Log.w(TAG, "El GPS no devolvió ubicación. ¿Está el GPS encendido?");
                return Result.retry();
            }
        } catch (Exception e) {
            Log.e(TAG, "Error durante la ejecución: " + e.getMessage());
            return Result.retry();
        }
    }

    private void saveToFirestoreSync(String uid, Location location) throws Exception {
        FirebaseFirestore db = FirebaseFirestore.getInstance();
        
        Map<String, Object> lastLocation = new HashMap<>();
        lastLocation.put("latitude", location.getLatitude());
        lastLocation.put("longitude", location.getLongitude());
        lastLocation.put("wifiName", getWifiSSID());
        lastLocation.put("timestamp", FieldValue.serverTimestamp());

        Map<String, Object> update = new HashMap<>();
        update.put("lastLocation", lastLocation);

        Log.d(TAG, "Subiendo datos a Firestore para UID: " + uid);
        
        // El document(uid) debe coincidir exactamente con el de la colección children
        Tasks.await(db.collection("children").document(uid)
                .set(update, SetOptions.merge()), 15, TimeUnit.SECONDS);
        
        Log.d(TAG, "✅ UBICACIÓN ACTUALIZADA EN FIRESTORE");
    }

    private String getWifiSSID() {
        try {
            WifiManager wifiManager = (WifiManager) getApplicationContext().getSystemService(Context.WIFI_SERVICE);
            if (wifiManager != null) {
                WifiInfo info = wifiManager.getConnectionInfo();
                if (info != null) {
                    String ssid = info.getSSID();
                    if (ssid == null || ssid.equals("<unknown ssid>")) return "Wi-Fi Desconocido";
                    return ssid.replace("\"", "");
                }
            }
        } catch (Exception ignored) {}
        return "Desconectado";
    }
}
