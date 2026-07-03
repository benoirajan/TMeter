package com.tmeter.service;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.ServiceInfo;
import android.os.Build;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.IBinder;
import android.os.PowerManager;
import android.util.Log;

import androidx.annotation.Nullable;
import androidx.core.app.NotificationCompat;
import androidx.preference.PreferenceManager;

import com.tmeter.MainActivity;
import com.tmeter.sensor.TemperatureProvider;

import java.util.Locale;

public class TemperatureAlertService extends Service {

    private static final String TAG = "TempAlertService";
    private static final String CHANNEL_ID = "temperature_alert_channel";
    private static final int FOREGROUND_NOTIFICATION_ID = 2001;
    private static final int ALERT_NOTIFICATION_ID = 2002;
    private static final String WAKELOCK_TAG = "TMeter::AlertWakeLock";
    private static final long CHECK_INTERVAL_MS = 10000L; // Check every 10 seconds

    private TemperatureProvider temperatureProvider;
    private HandlerThread handlerThread;
    private Handler handler;
    private PowerManager.WakeLock wakeLock;
    private SharedPreferences sharedPreferences;

    private final Runnable checkRunnable = new Runnable() {
        @Override
        public void run() {
            checkTemperatureAlert();
            handler.postDelayed(this, CHECK_INTERVAL_MS);
        }
    };

    @Override
    public void onCreate() {
        super.onCreate();
        Log.d(TAG, "Alert Service onCreate");

        temperatureProvider = new TemperatureProvider(this);
        temperatureProvider.startListening((temperature, source) -> { });

        handlerThread = new HandlerThread("TempAlertThread");
        handlerThread.start();
        handler = new Handler(handlerThread.getLooper());

        PowerManager pm = (PowerManager) getSystemService(Context.POWER_SERVICE);
        wakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, WAKELOCK_TAG);
        wakeLock.acquire();

        sharedPreferences = PreferenceManager.getDefaultSharedPreferences(this);

        createNotificationChannels();

        Notification notification = buildForegroundNotification();
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(FOREGROUND_NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE);
        } else {
            startForeground(FOREGROUND_NOTIFICATION_ID, notification);
        }

        handler.post(checkRunnable);
    }

    private void checkTemperatureAlert() {
        float threshold = sharedPreferences.getFloat("alert_threshold", 0f);
        String operator = sharedPreferences.getString("alert_operator", ">=");

        StringBuilder sourceBuilder = new StringBuilder();
        float currentTemp = temperatureProvider.getCurrentReading(sourceBuilder);

        boolean triggered = false;
        switch (operator) {
            case ">=":
                triggered = currentTemp >= threshold;
                break;
            case "<=":
                triggered = currentTemp <= threshold;
                break;
            case "==":
                triggered = Math.abs(currentTemp - threshold) < 0.5f;
                break;
        }

        if (triggered) {
            fireAlertNotification(currentTemp, operator, threshold);
        }
    }

    private void fireAlertNotification(float currentTemp, String operator, float threshold) {
        Intent intent = new Intent(this, MainActivity.class);
        PendingIntent pendingIntent = PendingIntent.getActivity(
                this, 0, intent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
        );

        String message = String.format(Locale.getDefault(),
                "Temperature %.1f°C is %s %.1f°C threshold!",
                currentTemp, operator, threshold);

        Notification notification = new NotificationCompat.Builder(this, CHANNEL_ID)
                .setContentTitle("⚠️ Temperature Alert")
                .setContentText(message)
                .setSmallIcon(android.R.drawable.ic_dialog_alert)
                .setContentIntent(pendingIntent)
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setAutoCancel(true)
                .build();

        NotificationManager manager = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
        if (manager != null) {
            manager.notify(ALERT_NOTIFICATION_ID, notification);
        }
    }

    private Notification buildForegroundNotification() {
        Intent intent = new Intent(this, MainActivity.class);
        PendingIntent pendingIntent = PendingIntent.getActivity(
                this, 0, intent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
        );

        float threshold = sharedPreferences.getFloat("alert_threshold", 0f);
        String operator = sharedPreferences.getString("alert_operator", ">=");

        return new NotificationCompat.Builder(this, CHANNEL_ID)
                .setContentTitle("Temperature Alert Active")
                .setContentText(String.format(Locale.getDefault(), "Monitoring: temp %s %.1f°C", operator, threshold))
                .setSmallIcon(android.R.drawable.ic_dialog_alert)
                .setContentIntent(pendingIntent)
                .setOngoing(true)
                .build();
    }

    private void createNotificationChannels() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                    CHANNEL_ID,
                    "Temperature Alerts",
                    NotificationManager.IMPORTANCE_HIGH
            );
            channel.setDescription("Alerts when temperature crosses threshold");
            NotificationManager manager = getSystemService(NotificationManager.class);
            if (manager != null) {
                manager.createNotificationChannel(channel);
            }
        }
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        return START_STICKY;
    }

    @Override
    public void onDestroy() {
        Log.d(TAG, "Alert Service onDestroy");

        handler.removeCallbacks(checkRunnable);
        handlerThread.quitSafely();

        if (wakeLock != null && wakeLock.isHeld()) {
            wakeLock.release();
        }

        if (temperatureProvider != null) {
            temperatureProvider.stopListening();
        }

        sharedPreferences.edit().putBoolean("alert_active", false).apply();

        super.onDestroy();
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }
}
