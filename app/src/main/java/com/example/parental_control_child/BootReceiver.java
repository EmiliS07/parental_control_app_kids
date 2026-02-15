package com.example.parental_control_child;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.util.Log;

public class BootReceiver extends BroadcastReceiver {

    private static final String TAG = "BootReceiver";

    @Override
    public void onReceive(Context context, Intent intent) {
        if (intent.getAction() == null) return;

        if (intent.getAction().equals(Intent.ACTION_BOOT_COMPLETED) ||
                intent.getAction().equals("android.intent.action.QUICKBOOT_POWERON")) {

            Log.d(TAG, "Device booted - checking if linked");

            // Verificar si el dispositivo está vinculado
            SharedPreferences prefs = context.getSharedPreferences("ParentalControl", Context.MODE_PRIVATE);
            boolean isLinked = prefs.getBoolean("isLinked", false);

            if (isLinked) {
                Log.d(TAG, "Device is linked - starting monitoring service");

                // Iniciar servicio de monitoreo
                Intent serviceIntent = new Intent(context, MonitoringService.class);
                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                    context.startForegroundService(serviceIntent);
                } else {
                    context.startService(serviceIntent);
                }

                // Opcional: Abrir la app
                Intent launchIntent = new Intent(context, HomeActivity.class);
                launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                context.startActivity(launchIntent);
            } else {
                Log.d(TAG, "Device not linked - skipping auto-start");
            }
        }
    }
}