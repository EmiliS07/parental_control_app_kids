package com.example.parental_control_child;

import android.app.admin.DeviceAdminReceiver;
import android.content.Context;
import android.content.Intent;
import android.widget.Toast;
import androidx.annotation.NonNull;

public class MyAdminReceiver extends DeviceAdminReceiver {
    @Override
    public void onEnabled(@NonNull Context context, @NonNull Intent intent) {
        super.onEnabled(context, intent);
        Toast.makeText(context, "Protección de desinstalación activada", Toast.LENGTH_SHORT).show();
    }

    @Override
    public void onDisabled(@NonNull Context context, @NonNull Intent intent) {
        super.onDisabled(context, intent);
        Toast.makeText(context, "Protección desactivada. Se notificará a tus padres.", Toast.LENGTH_LONG).show();
        // Aquí podrías enviar una alerta a Firebase diciendo que el niño desactivó la protección
    }
}