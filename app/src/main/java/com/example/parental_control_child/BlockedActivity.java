package com.example.parental_control_child;

import android.content.Intent;
import android.os.Bundle;
import android.widget.Button;
import android.widget.TextView;
import androidx.appcompat.app.AppCompatActivity;

public class BlockedActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_blocked);

        TextView tvReason = findViewById(R.id.tvBlockedReason);
        Button btnHome = findViewById(R.id.btnGoHome);

        String reason = getIntent().getStringExtra("reason");
        if (reason != null) {
            tvReason.setText(reason);
        }

        btnHome.setOnClickListener(v -> {
            Intent intent = new Intent(Intent.ACTION_MAIN);
            intent.addCategory(Intent.CATEGORY_HOME);
            intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            startActivity(intent);
            finish();
        });
    }
/**
 *Evitamos el inputs de volver atrás para impedir que salgan del bloqueo
 */

    @Override
    public void onBackPressed() {
    }
}