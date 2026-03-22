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
        Log.d(TAG, "Iniciando tarea de ubicación...");

        String uid = FirebaseAuth.getInstance().getUid();
        if (uid == null) {
            Log.e(TAG, "No hay usuario autenticado");
            return Result.failure();
        }

        try {
            FusedLocationProviderClient locationClient = LocationServices.getFusedLocationProviderClient(getApplicationContext());
            
            // Forzar la obtención de la ubicación actual de forma síncrona (espera hasta 20 seg)
            @SuppressLint("MissingPermission")
            Location location = Tasks.await(
                    locationClient.getCurrentLocation(Priority.PRIORITY_HIGH_ACCURACY, null),
                    20, TimeUnit.SECONDS
            );

            if (location != null) {
                saveToFirestoreSync(uid, location);
                return Result.success();
            } else {
                Log.w(TAG, "No se pudo obtener la ubicación (es null)");
                return Result.retry();
            }
        } catch (Exception e) {
            Log.e(TAG, "Error en LocationWorker: " + e.getMessage());
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

        // Usar Tasks.await para asegurar que el Worker no termine hasta que Firestore guarde
        Tasks.await(db.collection("children").document(uid)
                .set(update, SetOptions.merge()));
        
        Log.d(TAG, "Ubicación guardada con éxito en Firestore");
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
