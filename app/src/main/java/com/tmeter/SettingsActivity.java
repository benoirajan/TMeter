package com.tmeter;

import android.content.DialogInterface;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.preference.PreferenceManager;

import com.tmeter.db.AppDatabase;

public class SettingsActivity extends AppCompatActivity {

    private SharedPreferences sharedPreferences;
    private AppDatabase database;
    private TextView tvCurrentFrequency;

    private final String[] optionsText = {
            "10 Seconds", "30 Seconds", "1 Minute",
            "5 Minutes", "15 Minutes", "30 Minutes", "1 Hour"
    };
    private final String[] optionsValue = {
            "10000", "30000", "60000",
            "300000", "900000", "1800000", "3600000"
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_settings);

        sharedPreferences = PreferenceManager.getDefaultSharedPreferences(this);
        database = AppDatabase.getDatabase(this);

        tvCurrentFrequency = findViewById(R.id.tvCurrentFrequency);
        updateFrequencyLabel();

        findViewById(R.id.btnChangeFrequency).setOnClickListener(v -> showFrequencyDialog());

        findViewById(R.id.btnClearAllLogs).setOnClickListener(v -> {
            new AlertDialog.Builder(this)
                    .setTitle("Clear All Logs")
                    .setMessage("Are you sure you want to delete ALL stored temperature readings? This cannot be undone.")
                    .setPositiveButton("Clear All", (dialog, which) -> {
                        AppDatabase.databaseWriteExecutor.execute(() -> {
                            database.temperatureLogDao().clearAllLogs();
                            runOnUiThread(() -> Toast.makeText(this, "All logs cleared", Toast.LENGTH_SHORT).show());
                        });
                    })
                    .setNegativeButton("Cancel", null)
                    .show();
        });
    }

    private void showFrequencyDialog() {
        String currentVal = sharedPreferences.getString("recording_frequency_ms", "60000");
        int checkedItem = 2;
        for (int i = 0; i < optionsValue.length; i++) {
            if (optionsValue[i].equals(currentVal)) {
                checkedItem = i;
                break;
            }
        }

        new AlertDialog.Builder(this)
                .setTitle("Select Recording Frequency")
                .setSingleChoiceItems(optionsText, checkedItem, (dialog, which) -> {
                    sharedPreferences.edit()
                            .putString("recording_frequency_ms", optionsValue[which])
                            .apply();
                    updateFrequencyLabel();
                    Toast.makeText(this, "Frequency updated: " + optionsText[which], Toast.LENGTH_SHORT).show();
                    dialog.dismiss();
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void updateFrequencyLabel() {
        String currentVal = sharedPreferences.getString("recording_frequency_ms", "60000");
        for (int i = 0; i < optionsValue.length; i++) {
            if (optionsValue[i].equals(currentVal)) {
                tvCurrentFrequency.setText(optionsText[i]);
                return;
            }
        }
        tvCurrentFrequency.setText("1 Minute");
    }
}
