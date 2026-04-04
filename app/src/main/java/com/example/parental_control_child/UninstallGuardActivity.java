package com.example.parental_control_child;

import android.app.admin.DevicePolicyManager;
import android.content.ComponentName;
import android.content.Context;
import android.os.Bundle;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.FirebaseFirestore;

public class UninstallGuardActivity extends AppCompatActivity {

    private EditText etCode;
    private FirebaseFirestore db;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_uninstall_guard);

        db = FirebaseFirestore.getInstance();
        etCode = findViewById(R.id.etUninstallCode);
        Button btnVerify = findViewById(R.id.btnVerifyUninstall);
        Button btnCancel = findViewById(R.id.btnCancelUninstall);

        btnVerify.setOnClickListener(v -> verifyCode());
        btnCancel.setOnClickListener(v -> finish());
    }

    private void verifyCode() {
        String inputCode = etCode.getText().toString().trim();
        if (inputCode.isEmpty()) return;

        String userId = FirebaseAuth.getInstance().getUid();
        if (userId == null) {
            Toast.makeText(this, "Error de sesión", Toast.LENGTH_SHORT).show();
            return;
        }

        // Buscamos el código dinámico en el documento del niño
        db.collection("children").document(userId).get().addOnSuccessListener(snapshot -> {
            if (snapshot.exists()) {
                String correctCode = snapshot.getString("uninstallCode"); // El padre debe generar este código en Firestore
                if (inputCode.equals(correctCode)) {
                    disableAdminAndAllowUninstall();
                } else {
                    Toast.makeText(this, "Código incorrecto", Toast.LENGTH_SHORT).show();
                }
            }
        });
    }

    private void disableAdminAndAllowUninstall() {
        DevicePolicyManager dpm = (DevicePolicyManager) getSystemService(Context.DEVICE_POLICY_SERVICE);
        ComponentName adminComponent = new ComponentName(this, MyAdminReceiver.class);
        if (dpm != null && dpm.isAdminActive(adminComponent)) {
            dpm.removeActiveAdmin(adminComponent);
            Toast.makeText(this, "Protección desactivada. Ya puedes desinstalar.", Toast.LENGTH_LONG).show();
            finish();
        }
    }

    @Override
    public void onBackPressed() {
        // Bloquear botón atrás
    }
}