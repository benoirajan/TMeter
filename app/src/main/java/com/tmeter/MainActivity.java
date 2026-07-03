package com.tmeter;

import android.Manifest;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.os.Build;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.preference.PreferenceManager;

import com.github.mikephil.charting.charts.LineChart;
import com.github.mikephil.charting.components.XAxis;
import com.github.mikephil.charting.components.YAxis;
import com.github.mikephil.charting.data.Entry;
import com.github.mikephil.charting.data.LineData;
import com.github.mikephil.charting.data.LineDataSet;
import com.github.mikephil.charting.formatter.ValueFormatter;
import com.tmeter.db.AppDatabase;
import com.tmeter.db.TemperatureLog;
import com.tmeter.sensor.TemperatureProvider;
import com.tmeter.service.TemperatureRecordService;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class MainActivity extends AppCompatActivity {

    private TextView tvCurrentTemp;
    private TextView tvSourceText;
    private View viewSourceIndicatorDot;
    private LinearLayout layoutSourceTag;
    
    private Button btnToggleRecording;
    private Button btnClearLogs;
    private ImageButton btnSettings;
    private TextView tvStatusSubtext;
    
    private LineChart tempChart;
    
    private TemperatureProvider temperatureProvider;
    private AppDatabase database;
    private SharedPreferences sharedPreferences;
    
    private boolean isRecordingActive = false;
    private long referenceTime = 0L; // Used for X-axis precision in MPAndroidChart

    // Register the permissions launcher
    private final ActivityResultLauncher<String> requestPermissionLauncher =
            registerForActivityResult(new ActivityResultContracts.RequestPermission(), isGranted -> {
                if (isGranted) {
                    toggleService();
                } else {
                    Toast.makeText(this, "Notification permission is required for background logging.", Toast.LENGTH_LONG).show();
                }
            });

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        
        tvCurrentTemp = findViewById(R.id.tvCurrentTemp);
        tvSourceText = findViewById(R.id.tvSourceText);
        viewSourceIndicatorDot = findViewById(R.id.viewSourceIndicatorDot);
        layoutSourceTag = findViewById(R.id.layoutSourceTag);
        btnToggleRecording = findViewById(R.id.btnToggleRecording);
        btnClearLogs = findViewById(R.id.btnClearLogs);
        btnSettings = findViewById(R.id.btnSettings);
        tvStatusSubtext = findViewById(R.id.tvStatusSubtext);
        tempChart = findViewById(R.id.tempChart);
        
        sharedPreferences = PreferenceManager.getDefaultSharedPreferences(this);
        database = AppDatabase.getDatabase(this);
        temperatureProvider = new TemperatureProvider(this);
        
        setupChart();
        setupClickListeners();
        observeDatabase();
        updateUIState();
    }

    @Override
    protected void onResume() {
        super.onResume();
        // Start listening to live temperature updates on the UI thread
        temperatureProvider.startListening((temperature, source) -> {
            runOnUiThread(() -> updateRealtimeTempDisplay(temperature, source));
        });
        updateUIState();
    }

    @Override
    protected void onPause() {
        super.onPause();
        // Stop listening to sensors when UI is not active to conserve battery
        temperatureProvider.stopListening();
    }

    private void updateRealtimeTempDisplay(float temp, String source) {
        tvCurrentTemp.setText(String.format(Locale.getDefault(), "%.1f °C", temp));
        tvSourceText.setText("SENSOR".equals(source) ? R.string.temp_sensor : R.string.temp_battery);
        
        // Dynamic styles depending on SENSOR vs BATTERY
        int color = "SENSOR".equals(source) 
                ? ContextCompat.getColor(this, R.color.sensor_green)
                : ContextCompat.getColor(this, R.color.battery_orange);
                
        viewSourceIndicatorDot.setBackgroundColor(color);
        
        // Also update background of the source tag with translucent color
        GradientDrawable shape = new GradientDrawable();
        shape.setShape(GradientDrawable.RECTANGLE);
        shape.setCornerRadius(16);
        shape.setColor(Color.argb(30, Color.red(color), Color.green(color), Color.blue(color)));
        shape.setStroke(2, color);
        layoutSourceTag.setBackground(shape);
    }

    private void setupClickListeners() {
        btnToggleRecording.setOnClickListener(v -> handleRecordingToggle());
        
        btnClearLogs.setOnClickListener(v -> {
            new AlertDialog.Builder(this)
                    .setTitle("Clear Log History")
                    .setMessage("Are you sure you want to delete all stored temperature readings?")
                    .setPositiveButton("Clear All", (dialog, which) -> {
                        AppDatabase.databaseWriteExecutor.execute(() -> {
                            database.temperatureLogDao().clearAllLogs();
                            runOnUiThread(() -> Toast.makeText(MainActivity.this, "Logs cleared successfully", Toast.LENGTH_SHORT).show());
                        });
                    })
                    .setNegativeButton(R.string.cancel, null)
                    .show();
        });
        
        btnSettings.setOnClickListener(v -> showFrequencySettingsDialog());

        findViewById(R.id.btnViewLogs).setOnClickListener(v ->
                startActivity(new Intent(this, LogAnalyticsActivity.class)));

        findViewById(R.id.btnViewCalendar).setOnClickListener(v ->
                startActivity(new Intent(this, RecordingCalendarActivity.class)));

        findViewById(R.id.btnTempAlert).setOnClickListener(v ->
                startActivity(new Intent(this, TempAlertActivity.class)));
    }

    private void handleRecordingToggle() {
        // For Android 13+ (API 33), check and request notification permission
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) 
                    != PackageManager.PERMISSION_GRANTED) {
                requestPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS);
                return;
            }
        }
        toggleService();
    }

    private void toggleService() {
        boolean newState = !isRecordingActive;
        
        sharedPreferences.edit().putBoolean("is_recording_active", newState).apply();
        
        Intent serviceIntent = new Intent(this, TemperatureRecordService.class);
        if (newState) {
            ContextCompat.startForegroundService(this, serviceIntent);
            Toast.makeText(this, "Temperature Logging Started", Toast.LENGTH_SHORT).show();
        } else {
            stopService(serviceIntent);
            Toast.makeText(this, "Temperature Logging Stopped", Toast.LENGTH_SHORT).show();
        }
        
        updateUIState();
    }

    private void updateUIState() {
        isRecordingActive = sharedPreferences.getBoolean("is_recording_active", false);
        
        if (isRecordingActive) {
            btnToggleRecording.setText(R.string.stop_recording);
            btnToggleRecording.setBackgroundTintList(android.content.res.ColorStateList.valueOf(ContextCompat.getColor(this, R.color.colorAccent)));
            btnToggleRecording.setCompoundDrawablesWithIntrinsicBounds(android.R.drawable.ic_media_pause, 0, 0, 0);
            tvStatusSubtext.setText(R.string.recording_active);
            tvStatusSubtext.setTextColor(ContextCompat.getColor(this, R.color.sensor_green));
        } else {
            btnToggleRecording.setText(R.string.start_recording);
            btnToggleRecording.setBackgroundTintList(android.content.res.ColorStateList.valueOf(ContextCompat.getColor(this, R.color.colorPrimary)));
            btnToggleRecording.setCompoundDrawablesWithIntrinsicBounds(android.R.drawable.ic_media_play, 0, 0, 0);
            tvStatusSubtext.setText(R.string.recording_inactive);
            tvStatusSubtext.setTextColor(ContextCompat.getColor(this, R.color.text_muted));
        }
    }

    private void showFrequencySettingsDialog() {
        final String[] optionsText = {
                "10 Seconds",
                "30 Seconds",
                "1 Minute",
                "5 Minutes",
                "15 Minutes",
                "30 Minutes",
                "1 Hour"
        };
        final String[] optionsValue = {
                "10000",
                "30000",
                "60000",
                "300000",
                "900000",
                "1800000",
                "3600000"
        };

        String currentVal = sharedPreferences.getString("recording_frequency_ms", "60000");
        int checkedItem = 2; // Default 1 minute
        for (int i = 0; i < optionsValue.length; i++) {
            if (optionsValue[i].equals(currentVal)) {
                checkedItem = i;
                break;
            }
        }

        new AlertDialog.Builder(this)
                .setTitle("Select Recording Frequency")
                .setSingleChoiceItems(optionsText, checkedItem, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        sharedPreferences.edit()
                                .putString("recording_frequency_ms", optionsValue[which])
                                .apply();
                        Toast.makeText(MainActivity.this, "Frequency updated: " + optionsText[which], Toast.LENGTH_SHORT).show();
                        dialog.dismiss();
                    }
                })
                .setNegativeButton(R.string.cancel, null)
                .show();
    }

    private void setupChart() {
        tempChart.getDescription().setEnabled(false);
        tempChart.setTouchEnabled(true);
        tempChart.setDragEnabled(true);
        tempChart.setScaleEnabled(true);
        tempChart.setPinchZoom(true);
        tempChart.setDrawGridBackground(false);
        
        // Style X-Axis
        XAxis xAxis = tempChart.getXAxis();
        xAxis.setPosition(XAxis.XAxisPosition.BOTTOM);
        xAxis.setTextColor(ContextCompat.getColor(this, R.color.text_muted));
        xAxis.setDrawGridLines(true);
        xAxis.setGridColor(ContextCompat.getColor(this, R.color.card_border));
        xAxis.setLabelRotationAngle(-25f);
        
        // Style Y-Axis
        YAxis leftAxis = tempChart.getAxisLeft();
        leftAxis.setTextColor(ContextCompat.getColor(this, R.color.text_muted));
        leftAxis.setDrawGridLines(true);
        leftAxis.setGridColor(ContextCompat.getColor(this, R.color.card_border));
        
        YAxis rightAxis = tempChart.getAxisRight();
        rightAxis.setEnabled(false); // Disable right axis for simplicity
        
        tempChart.getLegend().setTextColor(Color.WHITE);
        tempChart.setNoDataText("No temperature logs recorded yet.");
        tempChart.setNoDataTextColor(ContextCompat.getColor(this, R.color.text_muted));
    }

    private void observeDatabase() {
        Calendar cal = Calendar.getInstance();
        cal.set(Calendar.HOUR_OF_DAY, 0);
        cal.set(Calendar.MINUTE, 0);
        cal.set(Calendar.SECOND, 0);
        cal.set(Calendar.MILLISECOND, 0);
        long startOfDay = cal.getTimeInMillis();

        database.temperatureLogDao().getTodayLogsLive(startOfDay).observe(this, logs -> {
            if (logs != null && !logs.isEmpty()) {
                updateChartData(logs);
            } else {
                tempChart.clear();
                tempChart.invalidate();
            }
        });
    }

    private void updateChartData(List<TemperatureLog> logs) {
        ArrayList<Entry> entries = new ArrayList<>();
        
        // Set the reference time from the first element to avoid floating point precision loss in MPAndroidChart
        referenceTime = logs.get(0).getTimestamp();
        
        for (TemperatureLog log : logs) {
            // X-axis value is the duration in seconds since referenceTime
            float xValue = (log.getTimestamp() - referenceTime) / 1000.0f;
            entries.add(new Entry(xValue, log.getTemperature()));
        }
        
        LineDataSet dataSet = new LineDataSet(entries, "Temperature (°C)");
        dataSet.setMode(LineDataSet.Mode.CUBIC_BEZIER);
        dataSet.setCubicIntensity(0.15f);
        dataSet.setDrawCircles(true);
        dataSet.setCircleRadius(3f);
        dataSet.setCircleColor(ContextCompat.getColor(this, R.color.colorPrimary));
        dataSet.setDrawCircleHole(false);
        dataSet.setColor(ContextCompat.getColor(this, R.color.colorPrimary));
        dataSet.setLineWidth(3f);
        dataSet.setDrawValues(false); // Keep cleaner UI
        
        // Draw filled gradient area
        dataSet.setDrawFilled(true);
        int startColor = ContextCompat.getColor(this, R.color.colorPrimary);
        int endColor = Color.TRANSPARENT;
        
        GradientDrawable gradient = new GradientDrawable(
                GradientDrawable.Orientation.TOP_BOTTOM,
                new int[]{startColor, endColor}
        );
        dataSet.setFillDrawable(gradient);
        
        // Custom Value Formatter for the X axis
        tempChart.getXAxis().setValueFormatter(new ValueFormatter() {
            private final SimpleDateFormat dateFormat = new SimpleDateFormat("HH:mm:ss", Locale.getDefault());
            
            @Override
            public String getFormattedValue(float value) {
                long originalTimestamp = referenceTime + (long) (value * 1000L);
                return dateFormat.format(new Date(originalTimestamp));
            }
        });
        
        LineData lineData = new LineData(dataSet);
        tempChart.setData(lineData);
        tempChart.invalidate(); // Refresh chart
    }
}
