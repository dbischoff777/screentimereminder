package com.screentimereminder.app;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.app.usage.UsageEvents;
import android.app.usage.UsageStats;
import android.app.usage.UsageStatsManager;
import android.appwidget.AppWidgetManager;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.content.pm.ServiceInfo;
import android.graphics.Color;
import android.os.Binder;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.PowerManager;
import android.os.SystemClock;
import android.provider.Settings;
import android.util.Log;

import androidx.core.app.NotificationCompat;

import java.util.Calendar;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.ExecutorService;

import org.json.JSONObject;
import org.json.JSONArray;

import android.app.AlarmManager;
import android.content.BroadcastReceiver;
import android.content.BroadcastReceiver;

public class BackgroundService extends Service {
    private static final String TAG = "BackgroundService";
    private static final String CHANNEL_ID = SettingsConstants.NOTIFICATION_CHANNEL_ID;
    private static final String PREFS_NAME = SettingsConstants.PREFS_NAME;
    private static final int NOTIFICATION_ID_LIMIT_REACHED = SettingsConstants.NOTIFICATION_ID_LIMIT_REACHED;
    private static final int NOTIFICATION_ID_APPROACHING_LIMIT = SettingsConstants.NOTIFICATION_ID_APPROACHING_LIMIT;
    private static final int NOTIFICATION_ID_BACKGROUND_SERVICE = SettingsConstants.NOTIFICATION_ID_BACKGROUND_SERVICE;
    private static final long UPDATE_INTERVAL = SettingsConstants.UPDATE_INTERVAL;
    private static final long WATCHDOG_INTERVAL = SettingsConstants.WATCHDOG_INTERVAL;
    private static final String ACTION_RESTART_SERVICE = SettingsConstants.ACTION_RESTART_SERVICE;
    private static final int SERVICE_RESTART_ALARM_ID = SettingsConstants.SERVICE_RESTART_ALARM_ID;
    private static boolean isRunning = false;
    private static ScheduledExecutorService scheduler;
    private static ScheduledExecutorService watchdogScheduler;
    private AlarmManager alarmManager;
    private PendingIntent restartIntent;

    private final IBinder binder = new LocalBinder();
    private Handler mainHandler;
    private long startTime;
    private boolean isTracking = false;
    private PowerManager.WakeLock wakeLock;
    private UsageStatsManager usageStatsManager;
    private String lastForegroundApp = "";
    private long lastUpdateTime = 0;
    private Map<String, Long> appUsageMap = new HashMap<>();
    private Runnable updateRunnable;
    private Runnable watchdogRunnable;

    private final ExecutorService backgroundExecutor = Executors.newSingleThreadExecutor();
    private final Object updateLock = new Object();
    private volatile boolean isUpdating = false;
    private static long lastUpdateTimestamp = 0;
    private static final long UPDATE_THRESHOLD = 500; // 500ms threshold

    private final BroadcastReceiver packageUpdateReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            try {
                String action = intent.getAction();
                if (Intent.ACTION_PACKAGE_REPLACED.equals(action)) {
                    String packageName = intent.getData().getSchemeSpecificPart();
                    if (packageName.equals(getPackageName())) {
                        Log.d(TAG, "Our app was updated, restarting service");
                        restartService();
                    }
                }
            } catch (Exception e) {
                Log.e(TAG, "Error handling package update", e);
            }
        }
    };

    private final BroadcastReceiver restartReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (ACTION_RESTART_SERVICE.equals(intent.getAction())) {
                Log.d(TAG, "Received restart broadcast");
                startServiceInternal();
            }
        }
    };

    private final BroadcastReceiver usageUpdateReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (intent.hasExtra("usageData")) {
                String usageData = intent.getStringExtra("usageData");
                try {
                    // Simply forward all updates to AppUsageTracker
                    AppUsageTracker.getInstance(context).handleUsageUpdate(usageData);
                } catch (Exception e) {
                    Log.e(TAG, "Error forwarding usage update to AppUsageTracker", e);
                }
            }
        }
    };

    public class LocalBinder extends Binder {
        BackgroundService getService() {
            return BackgroundService.this;
        }
    }

    @Override
    public void onCreate() {
        super.onCreate();
        Log.d(TAG, "Service onCreate");
        try {
            // Register receivers
            registerReceivers();

            // Set up alarm manager for service restart
            alarmManager = (AlarmManager) getSystemService(Context.ALARM_SERVICE);
            Intent intent = new Intent(this, BackgroundService.class);
            intent.setAction(SettingsConstants.ACTION_RESTART_SERVICE);
            restartIntent = PendingIntent.getService(
                this, SettingsConstants.SERVICE_RESTART_ALARM_ID, intent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
            );

            createNotificationChannel();
            mainHandler = new Handler(Looper.getMainLooper());
            startTime = System.currentTimeMillis();
            isRunning = true;
            
            // Set up the update runnable
            updateRunnable = new Runnable() {
                @Override
                public void run() {
                    try {
                        if (isRunning) {
                            updateAppUsage();
                            mainHandler.postDelayed(this, SettingsConstants.UPDATE_INTERVAL);
                        }
                    } catch (Exception e) {
                        Log.e(TAG, "Error in background update cycle", e);
                        if (isRunning) {
                            mainHandler.postDelayed(this, 30000);
                        }
                    }
                }
            };
            
            // Start the update runnable
            mainHandler.post(updateRunnable);
            
            // Set up the watchdog runnable
            watchdogRunnable = new Runnable() {
                @Override
                public void run() {
                    try {
                        if (!isRunning) {
                            Log.d(TAG, "Watchdog: Service not running, attempting restart");
                            restartService();
                            return;
                        }
                        
                        if (isRunning) {
                            mainHandler.postDelayed(this, SettingsConstants.WATCHDOG_INTERVAL);
                        }
                    } catch (Exception e) {
                        Log.e(TAG, "Error in watchdog", e);
                        if (isRunning) {
                            mainHandler.postDelayed(this, SettingsConstants.WATCHDOG_INTERVAL);
                        }
                    }
                }
            };
            
            // Start the watchdog
            mainHandler.post(watchdogRunnable);
            
            // Start as foreground service
            startForeground(SettingsConstants.NOTIFICATION_ID_BACKGROUND_SERVICE, createHighPriorityNotification());
            
            // Acquire partial wake lock
            PowerManager powerManager = (PowerManager) getSystemService(Context.POWER_SERVICE);
            if (powerManager != null) {
                wakeLock = powerManager.newWakeLock(
                    PowerManager.PARTIAL_WAKE_LOCK,
                    "ScreenTimeReminder::BackgroundTracking"
                );
                wakeLock.acquire();
            }

            // Schedule service restart alarm
            scheduleServiceRestartAlarm();

            Log.d(TAG, "Service initialization complete");

        } catch (Exception e) {
            Log.e(TAG, "Error in service onCreate", e);
            restartService();
        }
    }

    private void registerReceivers() {
        // Register package update receiver
        IntentFilter packageFilter = new IntentFilter();
        packageFilter.addAction(Intent.ACTION_PACKAGE_REPLACED);
        packageFilter.addDataScheme("package");
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(packageUpdateReceiver, packageFilter, Context.RECEIVER_NOT_EXPORTED);
        } else {
            registerReceiver(packageUpdateReceiver, packageFilter);
        }

        // Register restart receiver
        IntentFilter restartFilter = new IntentFilter(SettingsConstants.ACTION_RESTART_SERVICE);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(restartReceiver, restartFilter, Context.RECEIVER_NOT_EXPORTED);
        } else {
            registerReceiver(restartReceiver, restartFilter);
        }

        // Register usage update receiver
        IntentFilter usageFilter = new IntentFilter(SettingsConstants.ACTION_USAGE_UPDATE);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(usageUpdateReceiver, usageFilter, Context.RECEIVER_NOT_EXPORTED);
        } else {
            registerReceiver(usageUpdateReceiver, usageFilter);
        }
    }

    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                SettingsConstants.NOTIFICATION_CHANNEL_ID,
                SettingsConstants.NOTIFICATION_CHANNEL_NAME,
                NotificationManager.IMPORTANCE_HIGH
            );
            channel.setDescription(SettingsConstants.NOTIFICATION_CHANNEL_DESCRIPTION);
            channel.enableLights(true);
            channel.setLightColor(Color.RED);
            channel.enableVibration(true);
            channel.setVibrationPattern(new long[]{0, 1000, 500, 1000});
            channel.setLockscreenVisibility(Notification.VISIBILITY_PUBLIC);
            
            NotificationManager notificationManager = getSystemService(NotificationManager.class);
            notificationManager.createNotificationChannel(channel);
        }
    }

    private Notification createHighPriorityNotification() {
        try {
            NotificationCompat.Builder builder = new NotificationCompat.Builder(this, SettingsConstants.NOTIFICATION_CHANNEL_ID)
                .setContentTitle("Screen Time Tracking Active")
                .setContentText("Monitoring app usage")
                .setSmallIcon(android.R.drawable.ic_menu_recent_history)
                .setPriority(NotificationCompat.PRIORITY_MAX)
                .setOngoing(true)
                .setCategory(NotificationCompat.CATEGORY_SERVICE)
                .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
                .setVisibility(NotificationCompat.VISIBILITY_PUBLIC);

            // Add action to restart service
            Intent restartIntent = new Intent(this, BackgroundService.class);
            restartIntent.setAction(SettingsConstants.ACTION_RESTART_SERVICE);
            PendingIntent pendingIntent = PendingIntent.getService(
                this, 0, restartIntent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
            );
            builder.addAction(android.R.drawable.ic_menu_rotate, "Restart Service", pendingIntent);

            return builder.build();
        } catch (Exception e) {
            Log.e(TAG, "Error creating notification", e);
            return null;
        }
    }

    private void scheduleServiceRestartAlarm() {
        try {
            if (alarmManager != null) {
                long triggerTime = System.currentTimeMillis() + SettingsConstants.SERVICE_RESTART_INTERVAL;

                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                    alarmManager.setExactAndAllowWhileIdle(
                        AlarmManager.RTC_WAKEUP,
                        triggerTime,
                        restartIntent
                    );
                } else {
                    alarmManager.setExact(
                        AlarmManager.RTC_WAKEUP,
                        triggerTime,
                        restartIntent
                    );
                }
                Log.d(TAG, "Scheduled service restart alarm");
            }
        } catch (Exception e) {
            Log.e(TAG, "Error scheduling service restart alarm", e);
        }
    }

    @Override
    public void onDestroy() {
        try {
            Log.d(TAG, "Service onDestroy");
            isRunning = false;
            
            // Unregister receivers
            try {
                unregisterReceiver(packageUpdateReceiver);
                unregisterReceiver(restartReceiver);
                unregisterReceiver(usageUpdateReceiver);
            } catch (Exception e) {
                Log.e(TAG, "Error unregistering receivers", e);
            }
            
            // Clean up executors
            if (scheduler != null) {
                scheduler.shutdownNow();
            }
            if (watchdogScheduler != null) {
                watchdogScheduler.shutdownNow();
            }
            
            // Release wake lock
            if (wakeLock != null && wakeLock.isHeld()) {
                try {
                    wakeLock.release();
                } catch (Exception e) {
                    Log.e(TAG, "Error releasing wake lock", e);
                }
            }
            
            super.onDestroy();
            
            // Schedule service restart
            scheduleServiceRestartAlarm();
            
        } catch (Exception e) {
            Log.e(TAG, "Error in onDestroy", e);
        }
    }

    private void updateAppUsage() {
        try {
            long currentTime = System.currentTimeMillis();
            long elapsedTime = currentTime - startTime;
            
            // Only log every 5 minutes to reduce noise
            if (elapsedTime > 300000) {
                Log.d(TAG, "Service running for " + (elapsedTime / 1000 / 60) + " minutes");
                startTime = currentTime;
            }

            // Calculate current screen time and let AppUsageTracker handle the check
            float totalTime = AppUsageTracker.calculateScreenTime(getApplicationContext());
            AppUsageTracker.checkScreenTimeLimitStatic(
                getApplicationContext(), 
                Math.round(totalTime), 
                AppUsageTracker.getNotificationFrequencyStatic(getApplicationContext())
            );
            
        } catch (Exception e) {
            Log.e(TAG, "Error updating app usage", e);
        }
    }

    private float getTodayScreenTime(Context context) {
        try {
            // Get start of day
            Calendar calendar = Calendar.getInstance();
            calendar.set(Calendar.HOUR_OF_DAY, 0);
            calendar.set(Calendar.MINUTE, 0);
            calendar.set(Calendar.SECOND, 0);
            calendar.set(Calendar.MILLISECOND, 0);
            long startTime = calendar.getTimeInMillis();
            long endTime = System.currentTimeMillis();

            // Get app usage data
            UsageStatsManager usageStatsManager = (UsageStatsManager) context.getSystemService(Context.USAGE_STATS_SERVICE);
            if (usageStatsManager == null) {
                return 0;
            }

            List<UsageStats> stats = usageStatsManager.queryUsageStats(
                UsageStatsManager.INTERVAL_DAILY, startTime, endTime
            );

            if (stats == null) {
                return 0;
            }

            float totalMinutes = 0;
            String ourPackage = context.getPackageName();

            for (UsageStats stat : stats) {
                String packageName = stat.getPackageName();
                
                // Skip our own app and system apps
                if (packageName.equals(ourPackage) || isSystemApp(packageName)) {
                    continue;
                }

                // Only count time if the app was used today
                if (stat.getLastTimeUsed() >= startTime) {
                    totalMinutes += stat.getTotalTimeInForeground() / (60f * 1000f);
                }
            }

            return totalMinutes;
        } catch (Exception e) {
            Log.e(TAG, "Error getting today's screen time", e);
            return 0;
        }
    }

    private void updateAppUsageTime(String packageName, long timeSpent) {
        long currentUsage = appUsageMap.getOrDefault(packageName, 0L);
        appUsageMap.put(packageName, currentUsage + timeSpent);
    }

    private void broadcastUsageData() {
        try {
            Log.d(TAG, "Starting broadcastUsageData");
            
            // Get the latest total screen time from shared preferences
            SharedPreferences prefs = getSharedPreferences(SettingsConstants.PREFS_NAME, MODE_PRIVATE);
            float totalScreenTime = AppUsageTracker.calculateScreenTime(getApplicationContext());
            long lastUpdate = System.currentTimeMillis();
            
            // Create a JSON object with the usage data
            JSONObject usageData = new JSONObject();
            usageData.put("totalScreenTime", totalScreenTime);
            usageData.put("timestamp", lastUpdate);
            
            // Broadcast the update
            Intent intent = new Intent(SettingsConstants.ACTION_USAGE_UPDATE);
            intent.putExtra("usageData", usageData.toString());
            sendBroadcast(intent);
            
            Log.d(TAG, "Broadcasted usage update with data: " + usageData.toString());
            
        } catch (Exception e) {
            Log.e(TAG, "Error broadcasting usage data", e);
        }
    }

    @Override
    public IBinder onBind(Intent intent) {
        Log.d(TAG, "Service onBind");
        return binder;
    }

    @Override
    public boolean onUnbind(Intent intent) {
        Log.d(TAG, "Service onUnbind");
        return super.onUnbind(intent);
    }

    public static boolean isRunning() {
        return isRunning;
    }

    public void startTracking() {
        try {
            isTracking = true;
            startTime = System.currentTimeMillis();
            Log.d(TAG, "Tracking started");
        } catch (Exception e) {
            Log.e(TAG, "Error starting tracking", e);
        }
    }

    public void stopTracking() {
        try {
            isTracking = false;
            Log.d(TAG, "Tracking stopped");
        } catch (Exception e) {
            Log.e(TAG, "Error stopping tracking", e);
        }
    }

    public long getCurrentTime() {
        try {
            if (!isTracking) return 0;
            
            // Calculate elapsed time since start
            long currentTime = System.currentTimeMillis();
            long elapsedTime = currentTime - startTime;
            
            // Log the time calculation for debugging
            Log.d(TAG, String.format("Time calculation: current=%d, start=%d, elapsed=%d", 
                currentTime, startTime, elapsedTime));
                
            return elapsedTime;
        } catch (Exception e) {
            Log.e(TAG, "Error getting current time", e);
            return 0;
        }
    }

    public void resetTime() {
        try {
            startTime = System.currentTimeMillis();
            Log.d(TAG, "Time reset");
        } catch (Exception e) {
            Log.e(TAG, "Error resetting time", e);
        }
    }

    private long getStartOfDay() {
        Calendar calendar = Calendar.getInstance();
        calendar.set(Calendar.HOUR_OF_DAY, 0);
        calendar.set(Calendar.MINUTE, 0);
        calendar.set(Calendar.SECOND, 0);
        calendar.set(Calendar.MILLISECOND, 0);
        return calendar.getTimeInMillis();
    }

    private boolean isSystemApp(String packageName) {
        try {
            PackageManager packageManager = getPackageManager();
            ApplicationInfo appInfo = packageManager.getApplicationInfo(packageName, 0);
            return (appInfo.flags & ApplicationInfo.FLAG_SYSTEM) != 0;
        } catch (PackageManager.NameNotFoundException e) {
            return false;
        }
    }

    private String getAppName(String packageName) {
        try {
            PackageManager packageManager = getPackageManager();
            ApplicationInfo appInfo = packageManager.getApplicationInfo(packageName, 0);
            return packageManager.getApplicationLabel(appInfo).toString();
        } catch (PackageManager.NameNotFoundException e) {
            return packageName;
        }
    }

    private String getCategoryForApp(String appName) {
        // Implement the logic to determine the category for a given app name
        // This is a placeholder and should be replaced with the actual implementation
        return "Uncategorized";
    }

    private void restartService() {
        try {
            Log.d(TAG, "Attempting to restart service");
            
            // Stop current service
            stopForeground(true);
            stopSelf();
            
            // Start new service instance
            Intent restartIntent = new Intent(getApplicationContext(), BackgroundService.class);
            restartIntent.setAction(SettingsConstants.ACTION_RESTART_SERVICE);
            
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                startForegroundService(restartIntent);
            } else {
                startService(restartIntent);
            }
            
            // Schedule backup alarm
            scheduleServiceRestartAlarm();
            
        } catch (Exception e) {
            Log.e(TAG, "Error restarting service", e);
        }
    }

    private void verifyBackgroundUpdates() {
        try {
            SharedPreferences prefs = getSharedPreferences(SettingsConstants.PREFS_NAME, Context.MODE_PRIVATE);
            long lastUpdate = prefs.getLong("lastUpdateTime", 0);
            long currentTime = System.currentTimeMillis();
            
            // If no updates for more than 2 minutes, restart updates
            if (currentTime - lastUpdate > 2 * 60 * 1000) {
                Log.w(TAG, "Background updates seem stalled, restarting");
                if (updateRunnable != null) {
                    mainHandler.removeCallbacks(updateRunnable);
                    mainHandler.post(updateRunnable);
                }
            }
        } catch (Exception e) {
            Log.e(TAG, "Error verifying background updates", e);
        }
    }

    private void startPeriodicUpdates() {
        try {
            if (scheduler != null && !scheduler.isShutdown()) {
                scheduler.shutdown();
            }

            // Always update every minute to ensure we have fresh data
            final long UPDATE_INTERVAL = 60 * 1000; // 1 minute in milliseconds
            
            Log.d(TAG, "Starting periodic updates with 1 minute interval");

            scheduler = Executors.newSingleThreadScheduledExecutor();
            scheduler.scheduleAtFixedRate(() -> {
                try {
                    if (isTracking) {
                        // Get fresh values each time
                        SharedPreferences currentPrefs = getSharedPreferences(AppUsageTracker.PREFS_NAME, Context.MODE_PRIVATE);
                        long currentLimit = currentPrefs.getLong(AppUsageTracker.KEY_SCREEN_TIME_LIMIT, AppUsageTracker.DEFAULT_SCREEN_TIME_LIMIT);
                        long currentFrequency = currentPrefs.getLong(AppUsageTracker.KEY_NOTIFICATION_FREQUENCY, AppUsageTracker.DEFAULT_NOTIFICATION_FREQUENCY);
                        
                        Log.d(TAG, "Periodic update with current values - Frequency: " + currentFrequency + 
                              " minutes, Limit: " + currentLimit + " minutes");
                        
                        // Update app usage with current values
                        updateAppUsage();
                    }
                } catch (Exception e) {
                    Log.e(TAG, "Error in periodic update", e);
                }
            }, 0, 1, TimeUnit.MINUTES);

            Log.d(TAG, "Periodic updates scheduled every minute");
        } catch (Exception e) {
            Log.e(TAG, "Error starting periodic updates", e);
        }
    }

    private void setupWatchdog() {
        try {
            if (watchdogScheduler == null || watchdogScheduler.isShutdown()) {
                watchdogScheduler = Executors.newSingleThreadScheduledExecutor();
                watchdogScheduler.scheduleAtFixedRate(() -> {
                    try {
                        if (!isRunning) {
                            Log.d(TAG, "Watchdog: Service not running, restarting");
                            restartService();
                        } else {
                            verifyBackgroundUpdates();
                        }
                    } catch (Exception e) {
                        Log.e(TAG, "Error in watchdog", e);
                    }
                }, WATCHDOG_INTERVAL, WATCHDOG_INTERVAL, TimeUnit.MILLISECONDS);
            }
        } catch (Exception e) {
            Log.e(TAG, "Error setting up watchdog", e);
        }
    }

    private void startServiceInternal() {
        try {
            Intent serviceIntent = new Intent(getApplicationContext(), BackgroundService.class);
            serviceIntent.setAction(SettingsConstants.ACTION_RESTART_SERVICE);
            
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                startForegroundService(serviceIntent);
            } else {
                startService(serviceIntent);
            }
        } catch (Exception e) {
            Log.e(TAG, "Error starting service internally", e);
        }
    }

    private void updateWidgetWithData(float totalMinutes) {
        // Implement the logic to update the widget with new data
        // This is a placeholder and should be replaced with the actual implementation
        Log.d(TAG, "Updating widget with new data: " + totalMinutes + " minutes");
    }

    private void checkScreenTimeLimit() {
        try {
            // Defer entirely to AppUsageTracker
            float totalTime = AppUsageTracker.calculateScreenTime(this);
            AppUsageTracker.checkScreenTimeLimitStatic(this, Math.round(totalTime), 
                AppUsageTracker.getNotificationFrequencyStatic(this));
        } catch (Exception e) {
            Log.e(TAG, "Error checking screen time limit", e);
        }
    }
} 