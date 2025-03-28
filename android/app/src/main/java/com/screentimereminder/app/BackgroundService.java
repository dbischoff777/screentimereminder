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
        // Schedule a restart every 30 minutes to ensure the service stays alive
        Handler restartHandler = new Handler(Looper.getMainLooper());
        restartHandler.postDelayed(new Runnable() {
            @Override
            public void run() {
                if (isRunning) {
                    Intent restartIntent = new Intent(getApplicationContext(), BackgroundService.class);
                    restartIntent.setAction("RESTART_SERVICE");
                    startService(restartIntent);
                    restartHandler.postDelayed(this, 30 * 60 * 1000); // 30 minutes
                }
            }
        }, 30 * 60 * 1000); // 30 minutes
    }
    
    private void updateScreenTime() {
        try {
            long currentTime = System.currentTimeMillis();
            long timeDiff = currentTime - lastUpdateTime;
            
            // Get actual usage stats for the period
            Calendar calendar = Calendar.getInstance();
            calendar.add(Calendar.MINUTE, -1); // Last minute
            long startTime = calendar.getTimeInMillis();
            
            List<UsageStats> usageStats = usageStatsManager.queryUsageStats(
                UsageStatsManager.INTERVAL_DAILY,
                startTime,
                currentTime
            );
            
            if (usageStats != null) {
                long totalVisibleTime = 0;
                for (UsageStats stats : usageStats) {
                    if (stats.getTotalTimeVisible() > 0) {
                        totalVisibleTime += stats.getTotalTimeVisible();
                    }
                }
                
                // Update total screen time with actual usage
                totalScreenTime += totalVisibleTime;
            } else {
                // Fallback to time difference if usage stats are not available
                totalScreenTime += timeDiff;
            }
            
            // Save current state
            SharedPreferences.Editor editor = prefs.edit();
            editor.putLong(KEY_LAST_UPDATE_TIME, currentTime);
            editor.putLong(KEY_TOTAL_SCREEN_TIME, totalScreenTime);
            editor.apply();
            
            Log.d(TAG, "Screen time updated: " + (totalScreenTime / 60000) + " minutes");
        } catch (Exception e) {
            Log.e(TAG, "Error updating screen time", e);
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