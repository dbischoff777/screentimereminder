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
    private static final String CHANNEL_ID = "ScreenTimeReminderChannel";
    private static final String PREFS_NAME = "ScreenTimeReminder";
    private static final int NOTIFICATION_ID_LIMIT_REACHED = 1;
    private static final int NOTIFICATION_ID_APPROACHING_LIMIT = 2;
    private static final int NOTIFICATION_ID_BACKGROUND_SERVICE = 3;
    private static final long UPDATE_INTERVAL = 60000; // 1 minute
    private static final long WATCHDOG_INTERVAL = 10000; // 10 seconds
    private static final String ACTION_RESTART_SERVICE = "com.screentimereminder.app.RESTART_SERVICE";
    private static final int SERVICE_RESTART_ALARM_ID = 1001;
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
                    // Simply forward the update to AppUsageTracker
                    AppUsageTracker.getInstance(context).handleUsageUpdate(usageData);
                } catch (Exception e) {
                    Log.e(TAG, "Error processing usage update", e);
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
            // Register restart receiver
            IntentFilter restartFilter = new IntentFilter(ACTION_RESTART_SERVICE);
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                registerReceiver(restartReceiver, restartFilter, Context.RECEIVER_NOT_EXPORTED);
            } else {
                registerReceiver(restartReceiver, restartFilter);
            }

            // Register for package updates
            IntentFilter packageFilter = new IntentFilter();
            packageFilter.addAction(Intent.ACTION_PACKAGE_REPLACED);
            packageFilter.addDataScheme("package");
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                registerReceiver(packageUpdateReceiver, packageFilter, Context.RECEIVER_NOT_EXPORTED);
            } else {
                registerReceiver(packageUpdateReceiver, packageFilter);
            }

            // Set up alarm manager for service restart
            alarmManager = (AlarmManager) getSystemService(Context.ALARM_SERVICE);
            Intent intent = new Intent(this, BackgroundService.class);
            intent.setAction(ACTION_RESTART_SERVICE);
            restartIntent = PendingIntent.getService(
                this, SERVICE_RESTART_ALARM_ID, intent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
            );

            createNotificationChannel();
            mainHandler = new Handler(Looper.getMainLooper());
            startTime = System.currentTimeMillis();
            isRunning = true;
            usageStatsManager = (UsageStatsManager) getSystemService(Context.USAGE_STATS_SERVICE);
            
            // Set up the update runnable
            updateRunnable = new Runnable() {
                @Override
                public void run() {
                    try {
                        Log.d(TAG, "Starting background update cycle");
                        updateAppUsage();
                        // Schedule next update
                        if (isRunning) {
                            mainHandler.postDelayed(this, UPDATE_INTERVAL);
                        }
                    } catch (Exception e) {
                        Log.e(TAG, "Error in background update cycle", e);
                        // Retry after a shorter interval if there's an error
                        if (isRunning) {
                            mainHandler.postDelayed(this, 30000);
                        }
                    }
                }
            };
            
            // Start the update runnable
            mainHandler.post(updateRunnable);
            
            // Set up the watchdog runnable with improved error handling
            watchdogRunnable = new Runnable() {
                @Override
                public void run() {
                    try {
                        if (!isRunning) {
                            Log.d(TAG, "Watchdog: Service not running, attempting restart");
                            restartService();
                            return;
                        }
                        
                        Log.d(TAG, "Watchdog: Service is alive - " + new Date().toString());
                        // Verify background updates are working
                        verifyBackgroundUpdates();
                        
                        // Schedule next watchdog check if still running
                        if (isRunning) {
                            mainHandler.postDelayed(this, WATCHDOG_INTERVAL);
                        }
                    } catch (Exception e) {
                        Log.e(TAG, "Error in watchdog", e);
                        if (isRunning) {
                            mainHandler.postDelayed(this, WATCHDOG_INTERVAL);
                        }
                    }
                }
            };
            
            // Start the watchdog
            mainHandler.post(watchdogRunnable);
            
            // Start as foreground service with high priority
            startForeground(NOTIFICATION_ID_BACKGROUND_SERVICE, createHighPriorityNotification());
            
            // Acquire partial wake lock
            PowerManager powerManager = (PowerManager) getSystemService(Context.POWER_SERVICE);
            if (powerManager != null) {
                wakeLock = powerManager.newWakeLock(
                    PowerManager.PARTIAL_WAKE_LOCK,
                    "ScreenTimeReminder::BackgroundTracking"
                );
                wakeLock.acquire();
            }

            // Schedule periodic updates
            startPeriodicUpdates();

            // Set up watchdog
            setupWatchdog();

            // Schedule periodic service check using AlarmManager as backup
            scheduleServiceRestartAlarm();

        } catch (Exception e) {
            Log.e(TAG, "Error in service onCreate", e);
            restartService();
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
            // Get current screen time using AppUsageTracker's method for consistency
            float totalTime = AppUsageTracker.calculateScreenTime(this);
            
            // Get latest chain ID from settings update
            SharedPreferences prefs = getSharedPreferences(AppUsageTracker.PREFS_NAME, Context.MODE_PRIVATE);
            String chainId = prefs.getString("lastSettingsChainId", "NO_CHAIN_ID");
            
            // Use AppUsageTracker to check screen time limit and handle notifications
            AppUsageTracker.checkScreenTimeLimitStatic(this, Math.round(totalTime), AppUsageTracker.getNotificationFrequencyStatic(this));
            
            Log.d(TAG, String.format("[%s] Starting background service update - Current values:", chainId));
            Log.d(TAG, String.format("[%s] - Total screen time: %.2f minutes", chainId, totalTime));

            // Update widget with latest values
            Intent updateIntent = new Intent(this, ScreenTimeWidgetProvider.class);
            updateIntent.setAction("android.appwidget.action.APPWIDGET_UPDATE");
            int[] ids = AppWidgetManager.getInstance(this)
                .getAppWidgetIds(new ComponentName(this, ScreenTimeWidgetProvider.class));
            updateIntent.putExtra(AppWidgetManager.EXTRA_APPWIDGET_IDS, ids);
            updateIntent.putExtra("totalScreenTime", totalTime);
            sendBroadcast(updateIntent);

            Log.d(TAG, String.format("[%s] Completed background service update", chainId));
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
            SharedPreferences prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
            int totalMinutes = prefs.getInt("totalScreenTime", 0);
            long lastUpdate = prefs.getLong("lastUpdateTime", 0);
            
            // Create a JSON object with the usage data
            JSONObject usageData = new JSONObject();
            usageData.put("totalMinutes", totalMinutes);
            usageData.put("timestamp", lastUpdate);
            
            // Broadcast the update
            Intent intent = new Intent("com.screentimereminder.app.APP_USAGE_UPDATE");
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

    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                CHANNEL_ID,
                "Screen Time Tracking",
                NotificationManager.IMPORTANCE_HIGH
            );
            channel.setDescription("Tracks screen time and shows notifications");
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
            NotificationCompat.Builder builder = new NotificationCompat.Builder(this, CHANNEL_ID)
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
            restartIntent.setAction(ACTION_RESTART_SERVICE);
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

    private String getAppIconBase64(String packageName) {
        // Implement the logic to get the base64 representation of the app icon
        // This is a placeholder and should be replaced with the actual implementation
        return "";
    }

    private void restartService() {
        try {
            Log.d(TAG, "Attempting to restart service");
            
            // Stop current service
            stopForeground(true);
            stopSelf();
            
            // Start new service instance
            Intent restartIntent = new Intent(getApplicationContext(), BackgroundService.class);
            restartIntent.setAction(ACTION_RESTART_SERVICE);
            
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
            SharedPreferences prefs = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
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
                        long currentFrequency = currentPrefs.getLong(AppUsageTracker.KEY_NOTIFICATION_FREQUENCY, 5L);
                        
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

    private void scheduleServiceRestartAlarm() {
        try {
            // Schedule alarm to restart service every 15 minutes as backup
            if (alarmManager != null) {
                long interval = 15 * 60 * 1000; // 15 minutes
                long triggerTime = System.currentTimeMillis() + interval;

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

    private void startServiceInternal() {
        try {
            Intent serviceIntent = new Intent(getApplicationContext(), BackgroundService.class);
            serviceIntent.setAction(ACTION_RESTART_SERVICE);
            
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
            float totalTime = AppUsageTracker.calculateScreenTime(this);
            int currentLimit = AppUsageTracker.getScreenTimeLimitStatic(this);
            int currentFrequency = AppUsageTracker.getNotificationFrequencyStatic(this);
            
            // Rest of the method remains the same
        } catch (Exception e) {
            Log.e(TAG, "Error checking screen time limit", e);
        }
    }
} 