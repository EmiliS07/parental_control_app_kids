package com.example.parental_control_child;

import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.os.Handler;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.QueryDocumentSnapshot;

public class CodePanel extends AppCompatActivity {

    private static final String TAG = "CodePanel";

    private EditText etCode;
    private Button btnLink;
    private TextView tvStatus;
    private SharedPreferences prefs;
    private FirebaseFirestore db;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_code_panel);

        prefs = getSharedPreferences("ParentalControl", MODE_PRIVATE);

        if (isDeviceLinked()) {
            goToAppLauncher();
            return;
        }

        // Inicializar Cloud Firestore
        db = FirebaseFirestore.getInstance();

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
    }

    private void validateAndLink() {
        String code = etCode.getText().toString().trim();

        if (code.length() != 6) {
            showStatus("El código debe tener 6 dígitos", false);
            return;
        }

        btnLink.setEnabled(false);
        etCode.setEnabled(false);
        showStatus("Verificando código...", true);

        // Validar código con Cloud Firestore
        validateCodeWithFirestore(code);
    }

    private void validateCodeWithFirestore(String code) {
        db.collection("users")
                .whereEqualTo("linkCode", code)
                .get()
                .addOnCompleteListener(task -> {
                    if (task.isSuccessful() && task.getResult() != null && !task.getResult().isEmpty()) {
                        // Código encontrado
                        showStatus("✅ Dispositivo vinculado correctamente", true);
                        saveDeviceLinked(code);

                        new Handler().postDelayed(this::goToAppLauncher, 1000);

                    } else {
                        // Código no encontrado o error
                        showStatus("❌ Código inválido o expirado", false);
                        btnLink.setEnabled(true);
                        etCode.setEnabled(true);
                        etCode.setText("");
                        if (!task.isSuccessful()) {
                            Log.w(TAG, "Error al buscar el código.", task.getException());
                        }
                    }
                });
    }

    private void saveDeviceLinked(String code) {
        SharedPreferences.Editor editor = prefs.edit();
        editor.putBoolean("isLinked", true);
        editor.putString("linkCode", code);
        editor.putLong("linkedAt", System.currentTimeMillis());
        editor.apply();
    }

    private boolean isDeviceLinked() {
        return prefs.getBoolean("isLinked", false);
    }

    private void goToAppLauncher() {
        Intent intent = new Intent(this, HomeActivity.class);
        startActivity(intent);
        finish();
    }

    @SuppressWarnings("deprecation")
    private void showStatus(String message, boolean isSuccess) {
        tvStatus.setText(message);
        tvStatus.setTextColor(getResources().getColor(
                isSuccess ? android.R.color.holo_green_dark : android.R.color.holo_red_dark
        ));
        tvStatus.setVisibility(View.VISIBLE);
    }
}