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
import android.os.SystemClock;
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
import java.util.Date;
import java.util.List;
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
    private static final String KEY_LAST_UPDATE_TIME = "last_update_time";
    private static final String KEY_TOTAL_SCREEN_TIME = "total_screen_time";
    private static final String KEY_SERVICE_RUNNING = "service_running";
    
    private final IBinder binder = new LocalBinder();
    private SharedPreferences prefs;
    private WorkManager workManager;
    private Handler updateHandler;
    private Runnable updateRunnable;
    private PowerManager.WakeLock wakeLock;
    private boolean isRunning = false;
    private long lastUpdateTime;
    private long totalScreenTime;
    private UsageStatsManager usageStatsManager;
    
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
            
            // Initialize managers and preferences
            prefs = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
            workManager = WorkManager.getInstance(this);
            usageStatsManager = (UsageStatsManager) getSystemService(Context.USAGE_STATS_SERVICE);
            
            // Initialize wake lock with a more specific tag
            PowerManager powerManager = (PowerManager) getSystemService(Context.POWER_SERVICE);
            wakeLock = powerManager.newWakeLock(
                PowerManager.PARTIAL_WAKE_LOCK | PowerManager.ACQUIRE_CAUSES_WAKEUP,
                "ScreenTimeReminder::BackgroundTracking"
            );
            
            // Initialize handler for periodic updates
            updateHandler = new Handler(Looper.getMainLooper());
            updateRunnable = new Runnable() {
                @Override
                public void run() {
                    updateScreenTime();
                    updateHandler.postDelayed(this, 60000); // Update every minute
                }
            };
            
            // Load saved state
            lastUpdateTime = prefs.getLong(KEY_LAST_UPDATE_TIME, System.currentTimeMillis());
            totalScreenTime = prefs.getLong(KEY_TOTAL_SCREEN_TIME, 0);
            
            // Mark service as running
            prefs.edit().putBoolean(KEY_SERVICE_RUNNING, true).apply();
            
            setupBackgroundWork();
        } catch (Exception e) {
            Log.e(TAG, "Error in onCreate", e);
        }
    }
    
    private void setupBackgroundWork() {
        try {
            // Create a periodic work request that runs every 1 minute
            PeriodicWorkRequest usageTrackingWork = new PeriodicWorkRequest.Builder(
                AppUsageTrackingWorker.class,
                1, // Repeat interval
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
            
            // Acquire wake lock with timeout
            if (!wakeLock.isHeld()) {
                wakeLock.acquire(24 * 60 * 60 * 1000L); // 24 hours
            }
            
            // Start periodic updates
            updateHandler.post(updateRunnable);
            
            // Schedule a restart of the service to ensure it stays alive
            scheduleServiceRestart();
            
            return START_STICKY;
        } catch (Exception e) {
            Log.e(TAG, "Error in onStartCommand", e);
            return START_NOT_STICKY;
        }
    }
    
    private void scheduleServiceRestart() {
        try {
            // Create an alarm to restart the service every 30 minutes
            AlarmManager alarmManager = (AlarmManager) getSystemService(Context.ALARM_SERVICE);
            Intent restartIntent = new Intent(this, BackgroundService.class);
            PendingIntent restartPendingIntent = PendingIntent.getService(
                this,
                0,
                restartIntent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
            );

            // Set the alarm to trigger every 30 minutes
            long triggerTime = System.currentTimeMillis() + (30 * 60 * 1000);
            alarmManager.setExactAndAllowWhileIdle(
                AlarmManager.RTC_WAKEUP,
                triggerTime,
                restartPendingIntent
            );

            Log.d(TAG, "Service restart scheduled for " + new Date(triggerTime));
        } catch (Exception e) {
            Log.e(TAG, "Error scheduling service restart", e);
        }
    }
    
    private void updateScreenTime() {
        try {
            if (!isRunning) {
                Log.w(TAG, "Service not running, skipping screen time update");
                return;
            }

            long currentTime = System.currentTimeMillis();
            long timeDiff = currentTime - lastUpdateTime;
            
            // Get usage stats for the time period
            List<UsageStats> usageStats = usageStatsManager.queryUsageStats(
                UsageStatsManager.INTERVAL_DAILY,
                lastUpdateTime,
                currentTime
            );
            
            if (usageStats != null) {
                // Calculate total screen time
                long periodScreenTime = 0;
                for (UsageStats stats : usageStats) {
                    if (stats.getTotalTimeVisible() > 0) {
                        periodScreenTime += stats.getTotalTimeVisible();
                    }
                }
                
                // Update total screen time
                totalScreenTime += periodScreenTime;
                
                // Save the updated values
                SharedPreferences.Editor editor = prefs.edit();
                editor.putLong(KEY_LAST_UPDATE_TIME, currentTime);
                editor.putLong(KEY_TOTAL_SCREEN_TIME, totalScreenTime);
                editor.apply();
                
                // Check if we need to send notifications
                checkAndSendNotifications();
                
                Log.d(TAG, "Screen time updated: " + (totalScreenTime / 60000) + " minutes");
            }
        } catch (Exception e) {
            Log.e(TAG, "Error updating screen time", e);
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
            
            // Convert total screen time to minutes
            long totalMinutes = totalScreenTime / 60000;
            
            // Check if we're approaching the limit
            if (totalMinutes >= (screenTimeLimit - notificationFrequency) && 
                totalMinutes < screenTimeLimit) {
                long minutesRemaining = screenTimeLimit - totalMinutes;
                showApproachingLimitNotification(minutesRemaining);
            }
            // Check if we've reached the limit
            else if (totalMinutes >= screenTimeLimit) {
                showLimitReachedNotification();
            }
        } catch (Exception e) {
            Log.e(TAG, "Error checking notifications", e);
        }
    }
    
    private void showApproachingLimitNotification(long minutesRemaining) {
        try {
            NotificationManager notificationManager = 
                (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
            
            // Create notification channel for Android 8.0+
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                NotificationChannel channel = new NotificationChannel(
                    CHANNEL_ID,
                    "Screen Time Alerts",
                    NotificationManager.IMPORTANCE_HIGH
                );
                channel.setDescription("Notifications for screen time limits");
                channel.enableVibration(true);
                channel.enableLights(true);
                channel.setLightColor(Color.BLUE);
                notificationManager.createNotificationChannel(channel);
            }
            
            // Create notification
            NotificationCompat.Builder builder = new NotificationCompat.Builder(this, CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_stat_screen_time)
                .setContentTitle("Screen Time Limit Approaching")
                .setContentText("You have " + minutesRemaining + " minutes remaining")
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setAutoCancel(true)
                .setVibrate(new long[]{0, 500, 200, 500})
                .setLights(Color.BLUE, 3000, 3000);
            
            // Create intent to open app
            Intent intent = new Intent(this, MainActivity.class);
            PendingIntent pendingIntent = PendingIntent.getActivity(
                this,
                0,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
            );
            builder.setContentIntent(pendingIntent);
            
            // Show notification
            notificationManager.notify(1, builder.build());
            Log.d(TAG, "Approaching limit notification sent");
        } catch (Exception e) {
            Log.e(TAG, "Error showing approaching limit notification", e);
        }
    }
    
    private void showLimitReachedNotification() {
        try {
            NotificationManager notificationManager = 
                (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
            
            // Create notification
            NotificationCompat.Builder builder = new NotificationCompat.Builder(this, CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_stat_screen_time)
                .setContentTitle("Screen Time Limit Reached")
                .setContentText("You've reached your daily screen time limit")
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setAutoCancel(true)
                .setVibrate(new long[]{0, 1000, 500, 1000})
                .setLights(Color.RED, 3000, 3000);
            
            // Create intent to open app
            Intent intent = new Intent(this, MainActivity.class);
            PendingIntent pendingIntent = PendingIntent.getActivity(
                this,
                0,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
            );
            builder.setContentIntent(pendingIntent);
            
            // Show notification
            notificationManager.notify(2, builder.build());
            Log.d(TAG, "Limit reached notification sent");
        } catch (Exception e) {
            Log.e(TAG, "Error showing limit reached notification", e);
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
            
            // Release wake lock
            if (wakeLock.isHeld()) {
                wakeLock.release();
            }
            
            // Stop periodic updates
            if (updateHandler != null && updateRunnable != null) {
                updateHandler.removeCallbacks(updateRunnable);
            }
            
            // Cancel the background work
            if (workManager != null) {
                workManager.cancelUniqueWork(WORK_NAME);
            }
            
            // Mark service as not running
            prefs.edit().putBoolean(KEY_SERVICE_RUNNING, false).apply();
            
            super.onDestroy();
        } catch (Exception e) {
            Log.e(TAG, "Error in onDestroy", e);
        }
    }
    
    public boolean isRunning() {
        return isRunning;
    }
    
    public long getTotalScreenTime() {
        return totalScreenTime;
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
    private final SharedPreferences prefs;
    
    public AppUsageTrackingWorker(@NonNull Context context, @NonNull WorkerParameters params) {
        super(context, params);
        this.usageStatsManager = (UsageStatsManager) context.getSystemService(Context.USAGE_STATS_SERVICE);
        this.dataStore = new AppUsageDataStore(context);
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
} 