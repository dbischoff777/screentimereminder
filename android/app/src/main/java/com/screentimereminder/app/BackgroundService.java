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
import androidx.work.Constraints;
import androidx.work.ExistingPeriodicWorkPolicy;
import androidx.work.NetworkType;
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
    private static final String PREFS_NAME = "ScreenTimePrefs";
    private static final String KEY_SCREEN_TIME_LIMIT = "screen_time_limit";
    private static final String KEY_NOTIFICATION_FREQUENCY = "notification_frequency";
    private static final String KEY_NOTIFICATIONS_ENABLED = "notifications_enabled";
    
    private final IBinder binder = new LocalBinder();
    private NotificationHelper notificationHelper;
    private SharedPreferences prefs;
    private WorkManager workManager;
    
    private boolean isRunning = false;
    
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
            notificationHelper = new NotificationHelper(this);
            prefs = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
            workManager = WorkManager.getInstance(this);
            setupBackgroundWork();
        } catch (Exception e) {
            Log.e(TAG, "Error in onCreate", e);
        }
    }
    
    private void setupBackgroundWork() {
        try {
            // Create a periodic work request that runs every 5 minutes
            PeriodicWorkRequest usageTrackingWork = new PeriodicWorkRequest.Builder(
                AppUsageTrackingWorker.class,
                5, // Repeat interval
                TimeUnit.MINUTES
            )
            .setConstraints(
                new Constraints.Builder()
                    .setRequiredNetworkType(NetworkType.NOT_REQUIRED)
                    .setRequiresBatteryNotLow(true)
                    .build()
            )
            .build();
            
            // Enqueue the work with a unique name
            workManager.enqueueUniquePeriodicWork(
                WORK_NAME,
                ExistingPeriodicWorkPolicy.REPLACE,
                usageTrackingWork
            );
            
            Log.d(TAG, "Background work scheduled successfully");
        } catch (Exception e) {
            Log.e(TAG, "Error scheduling background work", e);
        }
    }
    
    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        try {
            Log.d(TAG, "Background service started");
            isRunning = true;
            startForeground();
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
            isRunning = false;
            // Cancel the background work when the service is destroyed
            if (workManager != null) {
                workManager.cancelUniqueWork(WORK_NAME);
            }
            super.onDestroy();
        } catch (Exception e) {
            Log.e(TAG, "Error in onDestroy", e);
        }
    }
    
    public boolean isRunning() {
        return isRunning;
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
}

// Worker class for background app usage tracking
class AppUsageTrackingWorker extends Worker {
    private static final String TAG = "AppUsageTrackingWorker";
    private static final String PREFS_NAME = "ScreenTimePrefs";
    private static final String KEY_SCREEN_TIME_LIMIT = "screen_time_limit";
    private static final String KEY_NOTIFICATION_FREQUENCY = "notification_frequency";
    private static final String KEY_NOTIFICATIONS_ENABLED = "notifications_enabled";
    
    private final UsageStatsManager usageStatsManager;
    private final AppUsageDataStore dataStore;
    private final NotificationHelper notificationHelper;
    private final SharedPreferences prefs;
    
    public AppUsageTrackingWorker(@NonNull Context context, @NonNull WorkerParameters params) {
        super(context, params);
        this.usageStatsManager = (UsageStatsManager) context.getSystemService(Context.USAGE_STATS_SERVICE);
        this.dataStore = new AppUsageDataStore(context);
        this.notificationHelper = new NotificationHelper(context);
        this.prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
    }
    
    @NonNull
    @Override
    public Result doWork() {
        try {
            Log.d(TAG, "Starting background work");
            
            // Get the current time and calculate the start time (beginning of day)
            long endTime = System.currentTimeMillis();
            Calendar calendar = Calendar.getInstance();
            calendar.set(Calendar.HOUR_OF_DAY, 0);
            calendar.set(Calendar.MINUTE, 0);
            calendar.set(Calendar.SECOND, 0);
            calendar.set(Calendar.MILLISECOND, 0);
            long startTime = calendar.getTimeInMillis();
            
            // Get usage stats for the day
            List<UsageStats> usageStats = usageStatsManager.queryUsageStats(
                UsageStatsManager.INTERVAL_DAILY,
                startTime,
                endTime
            );
            
            if (usageStats != null) {
                // Process and store the usage data
                for (UsageStats stats : usageStats) {
                    if (stats.getTotalTimeVisible() > 0) {
                        dataStore.updateAppUsage(
                            stats.getPackageName(),
                            stats.getTotalTimeVisible() / 60000 // Convert to minutes
                        );
                    }
                }
                
                // Check if we need to send notifications
                checkAndSendNotifications();
                
                Log.d(TAG, "Background work completed successfully");
                return Result.success();
            } else {
                Log.w(TAG, "No usage stats available");
                return Result.retry();
            }
        } catch (Exception e) {
            Log.e(TAG, "Error in background work", e);
            return Result.failure();
        }
    }
    
    private void checkAndSendNotifications() {
        try {
            boolean notificationsEnabled = prefs.getBoolean(KEY_NOTIFICATIONS_ENABLED, false);
            if (!notificationsEnabled) {
                return;
            }
            
            int screenTimeLimit = prefs.getInt(KEY_SCREEN_TIME_LIMIT, 120); // Default 2 hours
            int notificationFrequency = prefs.getInt(KEY_NOTIFICATION_FREQUENCY, 15); // Default 15 minutes
            
            // Get total screen time for today
            long totalScreenTime = dataStore.getTotalScreenTime();
            
            // Check if we're approaching the limit
            if (totalScreenTime >= (screenTimeLimit - notificationFrequency) * 60000 && 
                totalScreenTime < screenTimeLimit * 60000) {
                long minutesRemaining = (screenTimeLimit * 60000 - totalScreenTime) / 60000;
                notificationHelper.showApproachingLimitNotification(minutesRemaining, totalScreenTime);
            }
            // Check if we've reached the limit
            else if (totalScreenTime >= screenTimeLimit * 60000) {
                notificationHelper.showLimitReachedNotification();
            }
        } catch (Exception e) {
            Log.e(TAG, "Error checking notifications", e);
        }
    }
} 