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
import com.tmeter.db.AppDatabase;
import com.tmeter.db.TemperatureLog;
import com.tmeter.sensor.TemperatureProvider;

import java.util.Locale;

public class TemperatureRecordService extends Service {

    private static final String TAG = "TempRecordService";
    private static final String CHANNEL_ID = "temperature_logger_channel";
    private static final int NOTIFICATION_ID = 1001;
    private static final String WAKELOCK_TAG = "TMeter::RecordingWakeLock";

    private TemperatureProvider temperatureProvider;
    private AppDatabase database;

    private HandlerThread handlerThread;
    private Handler handler;
    private PowerManager.WakeLock wakeLock;

    private SharedPreferences sharedPreferences;
    private SharedPreferences.OnSharedPreferenceChangeListener preferenceChangeListener;

    private final Runnable recordRunnable = new Runnable() {
        @Override
        public void run() {
            recordTemperature();
            handler.postDelayed(this, getRecordingInterval());
        }
    };

    @Override
    public void onCreate() {
        super.onCreate();
        Log.d(TAG, "Service onCreate");

        temperatureProvider = new TemperatureProvider(this);
        temperatureProvider.startListening((temperature, source) -> { });

        database = AppDatabase.getDatabase(this);

        // Dedicated background thread for consistent timing
        handlerThread = new HandlerThread("TempRecordThread");
        handlerThread.start();
        handler = new Handler(handlerThread.getLooper());

        // Acquire partial wake lock to keep CPU alive for exact intervals
        PowerManager pm = (PowerManager) getSystemService(Context.POWER_SERVICE);
        wakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, WAKELOCK_TAG);
        wakeLock.acquire();

        createNotificationChannel();

        Notification notification = buildNotification(0.0f, "Initializing...", false);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE);
        } else {
            startForeground(NOTIFICATION_ID, notification);
        }

        sharedPreferences = PreferenceManager.getDefaultSharedPreferences(this);
        preferenceChangeListener = (sharedPrefs, key) -> {
            if ("recording_frequency_ms".equals(key)) {
                Log.d(TAG, "Frequency changed, rescheduling logger");
                rescheduleLoggingTask();
            }
        };
        sharedPreferences.registerOnSharedPreferenceChangeListener(preferenceChangeListener);

        handler.post(recordRunnable);
    }

    private void rescheduleLoggingTask() {
        handler.removeCallbacks(recordRunnable);
        handler.post(recordRunnable);
    }

    private long getRecordingInterval() {
        String valStr = sharedPreferences.getString("recording_frequency_ms", "60000");
        try {
            return Long.parseLong(valStr);
        } catch (NumberFormatException e) {
            return 60000L;
        }
    }

    private void recordTemperature() {
        StringBuilder sourceBuilder = new StringBuilder();
        float temp = temperatureProvider.getCurrentReading(sourceBuilder);
        String source = sourceBuilder.toString();
        long now = System.currentTimeMillis();

        Log.d(TAG, String.format(Locale.US, "Logging reading: %.1f°C from %s", temp, source));

        TemperatureLog logEntry = new TemperatureLog(now, temp, source);
        AppDatabase.databaseWriteExecutor.execute(() -> database.temperatureLogDao().insert(logEntry));

        updateNotification(temp, source);
    }

    private void updateNotification(float currentTemp, String source) {
        NotificationManager manager = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
        if (manager != null) {
            manager.notify(NOTIFICATION_ID, buildNotification(currentTemp, source, true));
        }
    }

    private Notification buildNotification(float temp, String source, boolean hasReading) {
        Intent notificationIntent = new Intent(this, MainActivity.class);
        PendingIntent pendingIntent = PendingIntent.getActivity(
                this, 0, notificationIntent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
        );

        String contentText = hasReading
                ? String.format(Locale.getDefault(), "Current Temperature: %.1f°C (%s)", temp, source)
                : "Active background logging...";

        return new NotificationCompat.Builder(this, CHANNEL_ID)
                .setContentTitle("Thermometer Active Recording")
                .setContentText(contentText)
                .setSmallIcon(android.R.drawable.ic_menu_compass)
                .setContentIntent(pendingIntent)
                .setOngoing(true)
                .build();
    }

    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel serviceChannel = new NotificationChannel(
                    CHANNEL_ID,
                    "Thermometer Logger Service",
                    NotificationManager.IMPORTANCE_LOW
            );
            serviceChannel.setDescription("Shows active background recording status");
            NotificationManager manager = getSystemService(NotificationManager.class);
            if (manager != null) {
                manager.createNotificationChannel(serviceChannel);
            }
        }
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        return START_STICKY;
    }

    @Override
    public void onDestroy() {
        Log.d(TAG, "Service onDestroy");

        handler.removeCallbacks(recordRunnable);
        handlerThread.quitSafely();

        if (wakeLock != null && wakeLock.isHeld()) {
            wakeLock.release();
        }

        if (temperatureProvider != null) {
            temperatureProvider.stopListening();
        }

        if (sharedPreferences != null) {
            if (preferenceChangeListener != null) {
                sharedPreferences.unregisterOnSharedPreferenceChangeListener(preferenceChangeListener);
            }
            sharedPreferences.edit().putBoolean("is_recording_active", false).apply();
        }

        super.onDestroy();
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }
}
