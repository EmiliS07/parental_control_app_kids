package com.example.parental_control_child;

import android.app.admin.DevicePolicyManager;
import android.content.ComponentName;
import android.content.Context;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.util.Log;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.FirebaseFirestore;

public class UninstallGuardActivity extends AppCompatActivity {

    private static final String TAG = "UninstallGuard";
    private EditText etCode;
    private FirebaseFirestore db;
    private SharedPreferences prefs;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_uninstall_guard);

        db = FirebaseFirestore.getInstance();
        prefs = getSharedPreferences("ParentalControl", MODE_PRIVATE);
        etCode = findViewById(R.id.etUninstallCode);
        Button btnVerify = findViewById(R.id.btnVerifyUninstall);
        Button btnCancel = findViewById(R.id.btnCancelUninstall);

        btnVerify.setOnClickListener(v -> verifyCode());
        btnCancel.setOnClickListener(v -> finish());
    }

    private void verifyCode() {
        String inputCode = etCode.getText().toString().trim();
        if (inputCode.isEmpty()) {
            Toast.makeText(this, "Ingresa el código", Toast.LENGTH_SHORT).show();
            return;
        }

        String userId = FirebaseAuth.getInstance().getUid();
        if (userId == null) {
            Toast.makeText(this, "Error: No hay sesión activa", Toast.LENGTH_SHORT).show();
            Log.e(TAG, "No user logged in");
            return;
        }

        btnVerifyStatus(false);

        db.collection("children").document(userId).get()
            .addOnSuccessListener(snapshot -> {
                if (snapshot.exists()) {
                    String correctCode = snapshot.getString("uninstallCode");
                    Log.d(TAG, "Code from DB: " + correctCode + " | Input: " + inputCode);
                    
                    if (inputCode.equals(correctCode)) {
                        allowUninstallProcess();
                    } else {
                        Toast.makeText(this, "Código incorrecto", Toast.LENGTH_SHORT).show();
                        btnVerifyStatus(true);
                    }
                } else {
                    Toast.makeText(this, "Error: No se encontró configuración", Toast.LENGTH_SHORT).show();
                    btnVerifyStatus(true);
                }
            })
            .addOnFailureListener(e -> {
                Log.e(TAG, "Error fetching code", e);
                Toast.makeText(this, "Error de conexión", Toast.LENGTH_SHORT).show();
                btnVerifyStatus(true);
            });
    }

    private void btnVerifyStatus(boolean enabled) {
        findViewById(R.id.btnVerifyUninstall).setEnabled(enabled);
    }

    private void allowUninstallProcess() {
        // 1. Permitir que la accesibilidad deje de bloquear temporalmente
        prefs.edit().putBoolean("allow_uninstall", true).apply();

        // 2. Desactivar Device Admin si está activo
        DevicePolicyManager dpm = (DevicePolicyManager) getSystemService(Context.DEVICE_POLICY_SERVICE);
        ComponentName adminComponent = new ComponentName(this, MyAdminReceiver.class);
        if (dpm != null && dpm.isAdminActive(adminComponent)) {
            dpm.removeActiveAdmin(adminComponent);
            Log.d(TAG, "Device Admin removed");
        }

        Toast.makeText(this, "Protección desactivada. Ya puedes desinstalar desde Ajustes.", Toast.LENGTH_LONG).show();
        
        // Cerramos la actividad para que el usuario pueda volver a Ajustes
        finish();
    }

    @Override
    public void onBackPressed() {
        // No hacer nada para evitar que el niño salga fácilmente
    }
}