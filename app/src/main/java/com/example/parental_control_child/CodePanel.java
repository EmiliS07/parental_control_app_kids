package com.example.parental_control_child;

import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.os.Handler;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

public class CodePanel extends AppCompatActivity {

    private EditText etCode;
    private Button btnLink;
    private TextView tvStatus;
    private SharedPreferences prefs;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_code_panel);

        // Inicializar SharedPreferences
        prefs = getSharedPreferences("ParentalControl", MODE_PRIVATE);

        // Verificar si ya está vinculado
        if (isDeviceLinked()) {
            goToAppLauncher();
            return;
        }

        // Referencias
        etCode = findViewById(R.id.etCode);
        btnLink = findViewById(R.id.btnLink);
        tvStatus = findViewById(R.id.tvStatus);

        // Auto-submit cuando complete 6 dígitos
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

        // Acción del botón
        btnLink.setOnClickListener(v -> validateAndLink());
    }

    private void validateAndLink() {
        String code = etCode.getText().toString().trim();

        if (code.length() != 6) {
            showStatus("El código debe tener 6 dígitos", false);
            return;
        }

        // Deshabilitar botón mientras procesa
        btnLink.setEnabled(false);
        etCode.setEnabled(false);
        showStatus("Verificando código...", true);

        // Simular llamada al servidor (2 segundos)
        new Handler().postDelayed(() -> {
            // TODO: Aquí irá la llamada real al servidor
            boolean success = simulateServerValidation(code);

            if (success) {
                showStatus("✅ Dispositivo vinculado correctamente", true);
                saveDeviceLinked(code);

                // Ir al launcher después de 1 segundo
                new Handler().postDelayed(this::goToAppLauncher, 1000);
            } else {
                showStatus("❌ Código inválido o expirado", false);
                btnLink.setEnabled(true);
                etCode.setEnabled(true);
                etCode.setText("");
            }
        }, 2000);
    }

    private boolean simulateServerValidation(String code) {
        // Por ahora aceptamos cualquier código de 6 dígitos
        // Más adelante conectaremos con AWS/Firebase
        return code.matches("\\d{6}");
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