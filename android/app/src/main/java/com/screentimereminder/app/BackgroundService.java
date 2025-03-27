package com.screentimereminder.app;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.app.usage.UsageStats;
import android.app.usage.UsageStatsManager;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Binder;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.PowerManager;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.app.NotificationCompat;
import androidx.work.ExistingPeriodicWorkPolicy;
import androidx.work.PeriodicWorkRequest;
import androidx.work.WorkManager;
import androidx.work.Worker;
import androidx.work.WorkerParameters;

import java.util.Calendar;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

/**
 * A simple background service implementation that replaces the Cordova plugin.
 * This service runs in the foreground to prevent being killed by the system.
 */
public class BackgroundService extends Service {
    private static final String TAG = "BackgroundService";
    private static final int NOTIFICATION_ID = 1000;
    private static final String CHANNEL_ID = "screen_time_reminder_channel";
    private static final String WORK_NAME = "app_usage_tracking";
    private static final long CHECK_INTERVAL = 60000; // Check every minute
    private static final String PREFS_NAME = "ScreenTimePrefs";
    private static final String KEY_SCREEN_TIME_LIMIT = "screen_time_limit";
    private static final String KEY_NOTIFICATION_FREQUENCY = "notification_frequency";
    private static final String KEY_NOTIFICATIONS_ENABLED = "notifications_enabled";
    
    private final IBinder binder = new LocalBinder();
    private PowerManager.WakeLock wakeLock;
    private boolean isRunning = false;
    private Handler handler;
    private Runnable usageCheckRunnable;
    private Map<String, Long> appUsageTimes = new ConcurrentHashMap<>();
    private long lastCheckTime = 0;
    private NotificationHelper notificationHelper;
    private SharedPreferences prefs;
    
    /**
     * Class for clients to access the service
     */
    public class LocalBinder extends Binder {
        BackgroundService getService() {
            return BackgroundService.this;
        }
    }
    
    @Override
    public void onCreate() {
        try {
            super.onCreate();
            Log.d(TAG, "Background service created");
            handler = new Handler(Looper.getMainLooper());
            notificationHelper = new NotificationHelper(this);
            prefs = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
            setupBackgroundWork();
        } catch (Exception e) {
            Log.e(TAG, "Error in onCreate", e);
        }
    }
    
    private void setupBackgroundWork() {
        // Create a periodic work request that runs every 15 minutes
        PeriodicWorkRequest usageTrackingWork = new PeriodicWorkRequest.Builder(
            AppUsageTrackingWorker.class,
            15, // Repeat interval
            TimeUnit.MINUTES
        ).build();
        
        // Enqueue the work with a unique name
        WorkManager.getInstance(this)
            .enqueueUniquePeriodicWork(
                WORK_NAME,
                ExistingPeriodicWorkPolicy.REPLACE,
                usageTrackingWork
            );
    }
    
    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        try {
            Log.d(TAG, "Background service started");
            
            if (!isRunning) {
                isRunning = true;
                startForeground();
                acquireWakeLock();
            }
            
            return START_STICKY;
        } catch (Exception e) {
            Log.e(TAG, "Error in onStartCommand", e);
            return START_NOT_STICKY;
        }
    }
    
    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return binder;
    }
    
    @Override
    public void onDestroy() {
        try {
            Log.d(TAG, "Background service destroyed");
            
            if (handler != null && usageCheckRunnable != null) {
                handler.removeCallbacks(usageCheckRunnable);
            }
            
            if (wakeLock != null && wakeLock.isHeld()) {
                wakeLock.release();
                wakeLock = null;
            }
            
            isRunning = false;
            super.onDestroy();
        } catch (Exception e) {
            Log.e(TAG, "Error in onDestroy", e);
        }
    }
    
    /**
     * Start the service as a foreground service with a notification
     */
    private void startForeground() {
        try {
            // Create notification channel for Android 8.0+
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                NotificationChannel channel = new NotificationChannel(
                    CHANNEL_ID,
                    "Screen Time Reminder",
                    NotificationManager.IMPORTANCE_LOW
                );
                channel.setDescription("Tracks your screen time in the background");
                
                NotificationManager notificationManager = getSystemService(NotificationManager.class);
                if (notificationManager != null) {
                    notificationManager.createNotificationChannel(channel);
                }
            }
            
            // Create an intent to open the app when the notification is tapped
            Intent notificationIntent = new Intent(this, MainActivity.class);
            notificationIntent.setAction(Intent.ACTION_MAIN);
            notificationIntent.addCategory(Intent.CATEGORY_LAUNCHER);
            notificationIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            
            PendingIntent pendingIntent = PendingIntent.getActivity(
                this,
                0,
                notificationIntent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
            );
            
            // Build the notification
            Notification notification = new NotificationCompat.Builder(this, CHANNEL_ID)
                .setContentTitle("Screen Time Reminder")
                .setContentText("Tracking your screen time")
                .setSmallIcon(R.drawable.ic_stat_screen_time)
                .setPriority(NotificationCompat.PRIORITY_LOW)
                .setOngoing(true)
                .setContentIntent(pendingIntent)
                .build();
            
            startForeground(NOTIFICATION_ID, notification);
            Log.d(TAG, "Started foreground service with notification");
        } catch (Exception e) {
            Log.e(TAG, "Error starting foreground service", e);
        }
    }
    
    /**
     * Acquire a wake lock to keep the CPU running while the service is active
     */
    private void acquireWakeLock() {
        try {
            if (wakeLock == null) {
                PowerManager powerManager = (PowerManager) getSystemService(Context.POWER_SERVICE);
                if (powerManager != null) {
                    wakeLock = powerManager.newWakeLock(
                        PowerManager.PARTIAL_WAKE_LOCK,
                        "ScreenTimeReminder:BackgroundServiceWakeLock"
                    );
                    wakeLock.acquire();
                    Log.d(TAG, "Wake lock acquired");
                }
            }
        } catch (Exception e) {
            Log.e(TAG, "Error acquiring wake lock", e);
        }
    }
    
    /**
     * Check if the service is currently running
     */
    public boolean isRunning() {
        return isRunning;
    }
    
    public Map<String, Long> getAppUsageTimes() {
        return appUsageTimes;
    }
    
    public void resetUsageTimes() {
        appUsageTimes.clear();
        lastCheckTime = System.currentTimeMillis();
    }
}

// Worker class for background app usage tracking
class AppUsageTrackingWorker extends Worker {
    private static final String TAG = "AppUsageTrackingWorker";
    private final UsageStatsManager usageStatsManager;
    private final AppUsageDataStore dataStore;
    private final NotificationHelper notificationHelper;
    
    public AppUsageTrackingWorker(@NonNull Context context, @NonNull WorkerParameters params) {
        super(context, params);
        this.usageStatsManager = (UsageStatsManager) context.getSystemService(Context.USAGE_STATS_SERVICE);
        this.dataStore = new AppUsageDataStore(context);
        this.notificationHelper = new NotificationHelper(context);
    }
    
    @NonNull
    @Override
    public Result doWork() {
        try {
            // Get today's start time
            Calendar calendar = Calendar.getInstance();
            calendar.set(Calendar.HOUR_OF_DAY, 0);
            calendar.set(Calendar.MINUTE, 0);
            calendar.set(Calendar.SECOND, 0);
            calendar.set(Calendar.MILLISECOND, 0);
            
            long startTime = calendar.getTimeInMillis();
            long endTime = System.currentTimeMillis();
            
            // Get usage stats for today
            List<UsageStats> usageStats = usageStatsManager.queryUsageStats(
                UsageStatsManager.INTERVAL_DAILY,
                startTime,
                endTime
            );
            
            if (usageStats != null) {
                // Process usage stats and update the app's data
                for (UsageStats stats : usageStats) {
                    if (stats.getTotalTimeInForeground() > 0) {
                        dataStore.updateAppUsage(stats.getPackageName(), stats.getTotalTimeInForeground());
                    }
                }
                
                // Check if we need to send notifications
                checkAndSendNotifications();
            }
            
            return Result.success();
        } catch (Exception e) {
            Log.e(TAG, "Error in background work", e);
            return Result.retry();
        }
    }
    
    private void checkAndSendNotifications() {
        // Get the current screen time limit and notification settings from SharedPreferences
        SharedPreferences prefs = getApplicationContext().getSharedPreferences("ScreenTimePrefs", Context.MODE_PRIVATE);
        boolean notificationsEnabled = prefs.getBoolean("notificationsEnabled", false);
        long screenTimeLimit = prefs.getLong("screenTimeLimit", 0) * 60 * 1000; // Convert minutes to milliseconds
        long notificationFrequency = prefs.getLong("notificationFrequency", 15) * 60 * 1000; // Convert minutes to milliseconds
        
        if (!notificationsEnabled || screenTimeLimit == 0) {
            return;
        }
        
        long totalScreenTime = dataStore.getTotalScreenTime();
        
        // Check if we're approaching the limit
        if (totalScreenTime >= (screenTimeLimit - notificationFrequency) && totalScreenTime < screenTimeLimit) {
            notificationHelper.showApproachingLimitNotification(totalScreenTime, screenTimeLimit);
        }
        
        // Check if we've reached the limit
        if (totalScreenTime >= screenTimeLimit) {
            notificationHelper.showLimitReachedNotification();
        }
    }
} 