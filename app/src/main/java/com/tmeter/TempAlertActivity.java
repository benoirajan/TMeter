package com.tmeter;

import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.RadioGroup;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.preference.PreferenceManager;

import com.tmeter.service.TemperatureAlertService;

import java.util.Locale;

public class TempAlertActivity extends AppCompatActivity {

    private RadioGroup rgOperator;
    private EditText etThreshold;
    private Button btnToggleAlert;
    private TextView tvAlertStatus, tvAlertRule;
    private SharedPreferences sharedPreferences;
    private boolean isAlertActive = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_temp_alert);

        rgOperator = findViewById(R.id.rgOperator);
        etThreshold = findViewById(R.id.etThreshold);
        btnToggleAlert = findViewById(R.id.btnToggleAlert);
        tvAlertStatus = findViewById(R.id.tvAlertStatus);
        tvAlertRule = findViewById(R.id.tvAlertRule);
        ImageButton btnBack = findViewById(R.id.btnBack);

        sharedPreferences = PreferenceManager.getDefaultSharedPreferences(this);

        btnBack.setOnClickListener(v -> finish());
        btnToggleAlert.setOnClickListener(v -> toggleAlert());

        loadSavedState();
        updateUI();
    }

    private void loadSavedState() {
        isAlertActive = sharedPreferences.getBoolean("alert_active", false);
        float threshold = sharedPreferences.getFloat("alert_threshold", 0f);
        String operator = sharedPreferences.getString("alert_operator", ">=");

        if (threshold != 0f) {
            etThreshold.setText(String.valueOf(threshold));
        }

        switch (operator) {
            case "<=":
                rgOperator.check(R.id.rbLessEqual);
                break;
            case "==":
                rgOperator.check(R.id.rbEqual);
                break;
            default:
                rgOperator.check(R.id.rbGreaterEqual);
                break;
        }
    }

    private void toggleAlert() {
        if (isAlertActive) {
            // Deactivate
            stopService(new Intent(this, TemperatureAlertService.class));
            isAlertActive = false;
            sharedPreferences.edit().putBoolean("alert_active", false).apply();
            Toast.makeText(this, "Alert deactivated", Toast.LENGTH_SHORT).show();
        } else {
            // Validate input
            String thresholdStr = etThreshold.getText().toString().trim();
            if (thresholdStr.isEmpty()) {
                Toast.makeText(this, "Please enter a threshold temperature", Toast.LENGTH_SHORT).show();
                return;
            }

            float threshold;
            try {
                threshold = Float.parseFloat(thresholdStr);
            } catch (NumberFormatException e) {
                Toast.makeText(this, "Invalid temperature value", Toast.LENGTH_SHORT).show();
                return;
            }

            String operator = getSelectedOperator();

            // Save rule
            sharedPreferences.edit()
                    .putFloat("alert_threshold", threshold)
                    .putString("alert_operator", operator)
                    .putBoolean("alert_active", true)
                    .apply();

            // Start service
            ContextCompat.startForegroundService(this, new Intent(this, TemperatureAlertService.class));
            isAlertActive = true;
            Toast.makeText(this, "Alert activated", Toast.LENGTH_SHORT).show();
        }
        updateUI();
    }

    private String getSelectedOperator() {
        int checkedId = rgOperator.getCheckedRadioButtonId();
        if (checkedId == R.id.rbLessEqual) return "<=";
        if (checkedId == R.id.rbEqual) return "==";
        return ">=";
    }

    private void updateUI() {
        if (isAlertActive) {
            float threshold = sharedPreferences.getFloat("alert_threshold", 0f);
            String operator = sharedPreferences.getString("alert_operator", ">=");

            btnToggleAlert.setText("Deactivate Alert");
            btnToggleAlert.setBackgroundTintList(
                    android.content.res.ColorStateList.valueOf(ContextCompat.getColor(this, R.color.colorAccent)));
            tvAlertStatus.setText("Alert Active");
            tvAlertStatus.setTextColor(ContextCompat.getColor(this, R.color.sensor_green));
            tvAlertRule.setText(String.format(Locale.getDefault(), "Notify when temp %s %.1f°C", operator, threshold));
        } else {
            btnToggleAlert.setText("Activate Alert");
            btnToggleAlert.setBackgroundTintList(
                    android.content.res.ColorStateList.valueOf(ContextCompat.getColor(this, R.color.sensor_green)));
            tvAlertStatus.setText("Alert Inactive");
            tvAlertStatus.setTextColor(ContextCompat.getColor(this, R.color.text_muted));
            tvAlertRule.setText("No active rule");
        }
    }
}
