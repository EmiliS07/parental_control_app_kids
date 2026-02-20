package com.example.parental_control_child;

import android.Manifest;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.messaging.FirebaseMessaging;

import java.util.HashMap;
import java.util.Map;

public class CodePanel extends AppCompatActivity {

    private static final String TAG = "CodePanel";

    private EditText etCode;
    private Button btnLink;
    private TextView tvStatus;
    private SharedPreferences prefs;
    private FirebaseFirestore db;
    private FirebaseAuth mAuth;

    private final ActivityResultLauncher<String> requestPermissionLauncher =
            registerForActivityResult(new ActivityResultContracts.RequestPermission(), isGranted -> {
                if (isGranted) syncFCMToken();
            });

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_code_panel);

        prefs = getSharedPreferences("ParentalControl", MODE_PRIVATE);
        db = FirebaseFirestore.getInstance();
        mAuth = FirebaseAuth.getInstance();

        if (isDeviceLinked()) {
            checkNotificationPermission();
            goToAppLauncher();
            return;
        }

        // MEJORA: Iniciamos sesión anónima de inmediato para tener permisos de lectura
        signInAnonymously();

        etCode = findViewById(R.id.etCode);
        btnLink = findViewById(R.id.btnLink);
        tvStatus = findViewById(R.id.tvStatus);

        etCode.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                btnLink.setEnabled(s.length() == 6);
            }
            @Override
            public void afterTextChanged(Editable s) {}
        });

        btnLink.setOnClickListener(v -> validateAndLink());
        checkNotificationPermission();
    }

    private void signInAnonymously() {
        if (mAuth.getCurrentUser() == null) {
            mAuth.signInAnonymously()
                .addOnSuccessListener(result -> Log.d(TAG, "Sesión anónima iniciada: " + result.getUser().getUid()))
                .addOnFailureListener(e -> Log.e(TAG, "Error en sesión anónima", e));
        }
    }

    private void validateAndLink() {
        String code = etCode.getText().toString().trim();
        btnLink.setEnabled(false);
        etCode.setEnabled(false);
        showStatus("Verificando código...", true);

        // Buscamos en "parents" el campo "linkCode"
        db.collection("parents")
                .whereEqualTo("linkCode", code)
                .get()
                .addOnCompleteListener(task -> {
                    if (task.isSuccessful() && task.getResult() != null && !task.getResult().isEmpty()) {
                        String parentId = task.getResult().getDocuments().get(0).getId();
                        Log.d(TAG, "Padre encontrado: " + parentId);
                        
                        if (mAuth.getCurrentUser() == null) {
                            mAuth.signInAnonymously().addOnCompleteListener(authTask -> {
                                if (authTask.isSuccessful()) completeLinking(parentId, code);
                                else {
                                    showStatus("❌ Error de autenticación", false);
                                    resetUI();
                                }
                            });
                        } else {
                            completeLinking(parentId, code);
                        }
                    } else {
                        if (!task.isSuccessful()) {
                            Log.e(TAG, "Error Firestore: ", task.getException());
                            showStatus("❌ Error de conexión con el servidor", false);
                        } else {
                            Log.d(TAG, "No se encontró el código: " + code);
                            showStatus("❌ Código inválido o expirado", false);
                        }
                        resetUI();
                    }
                });
    }

    private void resetUI() {
        btnLink.setEnabled(true);
        etCode.setEnabled(true);
        etCode.setText("");
    }

    private void completeLinking(String parentId, String code) {
        String childId = mAuth.getCurrentUser().getUid();

        // Obtenemos el token de FCM para enviarlo de una vez al padre
        FirebaseMessaging.getInstance().getToken().addOnCompleteListener(tokenTask -> {
            String fcmToken = tokenTask.isSuccessful() ? tokenTask.getResult() : "";

            // 1. Datos del niño para su propia colección
            Map<String, Object> childData = new HashMap<>();
            childData.put("parentId", parentId);
            childData.put("childId", childId);
            childData.put("deviceName", Build.MODEL);
            childData.put("fcmToken", fcmToken);
            childData.put("linkedAt", com.google.firebase.Timestamp.now());
            childData.put("status", "active");

            // 2. Datos para actualizar al padre (Cubrimos múltiples variantes de nombres de campos)
            Map<String, Object> parentUpdate = new HashMap<>();
            parentUpdate.put("childId", childId);
            parentUpdate.put("kidId", childId);             
            parentUpdate.put("isChildLinked", true);
            parentUpdate.put("hasLinkedChild", true);       
            parentUpdate.put("isLinked", true);             
            parentUpdate.put("linkCode", "");               
            parentUpdate.put("fcmToken", fcmToken); // El padre necesita el token para enviar notificaciones
            parentUpdate.put("lastChildUpdate", com.google.firebase.Timestamp.now());

            db.collection("children").document(childId)
                    .set(childData, com.google.firebase.firestore.SetOptions.merge())
                    .addOnSuccessListener(aVoid -> {
                        // Notificamos al padre en la colección "parents"
                        db.collection("parents").document(parentId)
                                .update(parentUpdate)
                                .addOnSuccessListener(v -> {
                                    Log.d(TAG, "Padre notificado con éxito");
                                    showStatus("✅ Vinculado correctamente", true);
                                    saveDeviceLinked(code, parentId);
                                    new Handler().postDelayed(this::goToAppLauncher, 1000);
                                })
                                .addOnFailureListener(e -> {
                                    Log.e(TAG, "Error al notificar al padre, intentando set(merge)", e);
                                    // Si falla update (ej. por reglas), intentamos set con merge
                                    db.collection("parents").document(parentId)
                                            .set(parentUpdate, com.google.firebase.firestore.SetOptions.merge())
                                            .addOnSuccessListener(v -> {
                                                showStatus("✅ Vinculado correctamente", true);
                                                saveDeviceLinked(code, parentId);
                                                new Handler().postDelayed(this::goToAppLauncher, 1000);
                                            });
                                });
                    })
                    .addOnFailureListener(e -> {
                        Log.e(TAG, "Error guardando en children", e);
                        showStatus("❌ Error al guardar vínculo", false);
                        resetUI();
                    });
        });
    }

    private void syncFCMToken() {
        FirebaseMessaging.getInstance().getToken().addOnCompleteListener(task -> {
            if (task.isSuccessful() && mAuth.getCurrentUser() != null) {
                String token = task.getResult();
                Map<String, Object> data = new HashMap<>();
                data.put("fcmToken", token);
                db.collection("children").document(mAuth.getCurrentUser().getUid()).update(data);
            }
        });
    }

    private void saveDeviceLinked(String code, String parentId) {
        prefs.edit()
                .putBoolean("isLinked", true)
                .putString("linkCode", code)
                .putString("parentId", parentId)
                .apply();
    }

    private void checkNotificationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                requestPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS);
            } else { syncFCMToken(); }
        } else { syncFCMToken(); }
    }

    private boolean isDeviceLinked() { return prefs.getBoolean("isLinked", false); }

    private void goToAppLauncher() {
        startActivity(new Intent(this, HomeActivity.class));
        finish();
    }

    @SuppressWarnings("deprecation")
    private void showStatus(String message, boolean isSuccess) {
        tvStatus.setText(message);
        tvStatus.setTextColor(getResources().getColor(isSuccess ? android.R.color.holo_green_dark : android.R.color.holo_red_dark));
        tvStatus.setVisibility(View.VISIBLE);
    }
}
