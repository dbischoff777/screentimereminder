package com.screentimereminder.app;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.app.usage.UsageEvents;
import android.app.usage.UsageStats;
import android.app.usage.UsageStatsManager;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.content.pm.ServiceInfo;
import android.os.Binder;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.PowerManager;
import android.provider.Settings;
import android.util.Log;

import androidx.core.app.NotificationCompat;

import java.util.Calendar;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import org.json.JSONObject;

public class BackgroundService extends Service {
    private static final String TAG = "BackgroundService";
    private static final String CHANNEL_ID = "ScreenTimeReminderChannel";
    private static final int NOTIFICATION_ID_LIMIT_REACHED = 1;
    private static final int NOTIFICATION_ID_APPROACHING_LIMIT = 2;
    private static final int NOTIFICATION_ID_BACKGROUND_SERVICE = 3;
    private static final long UPDATE_INTERVAL = 60000; // 1 minute
    private static final long WATCHDOG_INTERVAL = 10000; // 10 seconds
    private static boolean isRunning = false;
    private static ScheduledExecutorService scheduler;
    private static ScheduledExecutorService watchdogScheduler;

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
            createNotificationChannel();
            mainHandler = new Handler(Looper.getMainLooper());
            startTime = System.currentTimeMillis();
            isRunning = true;
            usageStatsManager = (UsageStatsManager) getSystemService(Context.USAGE_STATS_SERVICE);
            
            // Set up the watchdog runnable
            watchdogRunnable = new Runnable() {
                @Override
                public void run() {
                    Log.d(TAG, "Watchdog: Service is alive - " + new Date().toString());
                    // Schedule next watchdog check
                    mainHandler.postDelayed(this, WATCHDOG_INTERVAL);
                }
            };
            
            // Start the watchdog
            mainHandler.post(watchdogRunnable);
            Log.d(TAG, "Watchdog started");
            
            // Set up the update runnable
            updateRunnable = new Runnable() {
                @Override
                public void run() {
                    try {
                        Log.d(TAG, "Starting update cycle");
                        updateAppUsage();
                        // Schedule next update with a slight random delay
                        long nextUpdate = UPDATE_INTERVAL + (long)(Math.random() * 10000);
                        mainHandler.postDelayed(this, nextUpdate);
                        Log.d(TAG, "Next update scheduled in " + (nextUpdate/1000) + " seconds");
                    } catch (Exception e) {
                        Log.e(TAG, "Error in update runnable", e);
                        // Retry after a shorter interval if there's an error
                        mainHandler.postDelayed(this, 30000);
                    }
                }
            };

            // Start as foreground service immediately with a persistent notification
            try {
                Notification notification = createNotification();
                if (notification != null) {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                        startForeground(NOTIFICATION_ID_BACKGROUND_SERVICE, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC);
                    } else {
                        startForeground(NOTIFICATION_ID_BACKGROUND_SERVICE, notification);
                    }
                    Log.d(TAG, "Started as foreground service");
                }
            } catch (SecurityException e) {
                Log.e(TAG, "SecurityException when starting foreground service", e);
                // Try to start without foreground service type
                try {
                    Notification notification = createNotification();
                    if (notification != null) {
                        startForeground(NOTIFICATION_ID_BACKGROUND_SERVICE, notification);
                        Log.d(TAG, "Started as foreground service without type");
                    }
                } catch (Exception ex) {
                    Log.e(TAG, "Failed to start foreground service", ex);
                }
            }
            
            // Start the update runnable
            if (updateRunnable != null) {
                mainHandler.post(updateRunnable);
                Log.d(TAG, "Started update runnable");
            }

            // Acquire wake lock
            try {
                PowerManager powerManager = (PowerManager) getSystemService(POWER_SERVICE);
                if (powerManager != null) {
                    wakeLock = powerManager.newWakeLock(
                        PowerManager.PARTIAL_WAKE_LOCK | PowerManager.ON_AFTER_RELEASE,
                        "ScreenTimeReminder::BackgroundTracking"
                    );
                    if (wakeLock != null) {
                        wakeLock.setReferenceCounted(false);
                        wakeLock.acquire(10 * 60 * 1000L); // 10 minutes
                        Log.d(TAG, "WakeLock acquired successfully");
                    }
                }
            } catch (Exception e) {
                Log.e(TAG, "Error acquiring WakeLock", e);
            }

            // Request battery optimization exemption
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                PowerManager powerManager = (PowerManager) getSystemService(Context.POWER_SERVICE);
                if (powerManager != null && !powerManager.isIgnoringBatteryOptimizations(getPackageName())) {
                    Intent intent = new Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS);
                    intent.setData(android.net.Uri.parse("package:" + getPackageName()));
                    intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                    startActivity(intent);
                }
            }
        } catch (Exception e) {
            Log.e(TAG, "Error in onCreate", e);
        }
    }

    private void ensureServiceRunning() {
        try {
            android.app.ActivityManager manager = (android.app.ActivityManager) getSystemService(Context.ACTIVITY_SERVICE);
            boolean isServiceRunning = false;
            
            for (android.app.ActivityManager.RunningServiceInfo service : manager.getRunningServices(Integer.MAX_VALUE)) {
                if (BackgroundService.class.getName().equals(service.service.getClassName())) {
                    isServiceRunning = true;
                    break;
                }
            }
            
            if (!isServiceRunning) {
                Log.d(TAG, "Service not running, restarting...");
                Intent serviceIntent = new Intent(this, BackgroundService.class);
                serviceIntent.setAction("START_TRACKING");
                
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    startForegroundService(serviceIntent);
                } else {
                    startService(serviceIntent);
                }
                
                // Request battery optimization exemption
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                    PowerManager powerManager = (PowerManager) getSystemService(Context.POWER_SERVICE);
                    if (powerManager != null && !powerManager.isIgnoringBatteryOptimizations(getPackageName())) {
                        Intent intent = new Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS);
                        intent.setData(android.net.Uri.parse("package:" + getPackageName()));
                        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                        startActivity(intent);
                    }
                }
            }
        } catch (Exception e) {
            Log.e(TAG, "Error ensuring service is running", e);
        }
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        Log.d(TAG, "Service onStartCommand - Action: " + (intent != null ? intent.getAction() : "null"));
        try {
            // Ensure we're running as foreground service
            try {
                Notification notification = createNotification();
                if (notification != null) {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                        startForeground(NOTIFICATION_ID_BACKGROUND_SERVICE, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC);
                    } else {
                        startForeground(NOTIFICATION_ID_BACKGROUND_SERVICE, notification);
                    }
                    Log.d(TAG, "Started as foreground service in onStartCommand");
                }
            } catch (SecurityException e) {
                Log.e(TAG, "SecurityException when starting foreground service", e);
                // Try to start without foreground service type
                try {
                    Notification notification = createNotification();
                    if (notification != null) {
                        startForeground(NOTIFICATION_ID_BACKGROUND_SERVICE, notification);
                        Log.d(TAG, "Started as foreground service without type in onStartCommand");
                    }
                } catch (Exception ex) {
                    Log.e(TAG, "Failed to start foreground service in onStartCommand", ex);
                }
            }
            
            // Start the update runnable
            if (updateRunnable != null) {
                mainHandler.post(updateRunnable);
                Log.d(TAG, "Started update runnable in onStartCommand");
            }
            
            // Return START_STICKY to ensure the service is restarted if killed
            return START_STICKY;
        } catch (Exception e) {
            Log.e(TAG, "Error in onStartCommand", e);
            return START_STICKY;
        }
    }

    @Override
    public void onTaskRemoved(Intent rootIntent) {
        Log.d(TAG, "Service onTaskRemoved");
        super.onTaskRemoved(rootIntent);
        
        // Restart the service
        Intent restartServiceIntent = new Intent(getApplicationContext(), this.getClass());
        restartServiceIntent.setPackage(getPackageName());
        restartServiceIntent.setAction("RESTART_SERVICE");
        
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(restartServiceIntent);
        } else {
            startService(restartServiceIntent);
        }
    }

    private void updateAppUsage() {
        try {
            Log.d(TAG, "Starting app usage update check");
            long currentTime = System.currentTimeMillis();
            long startTime = lastUpdateTime > 0 ? lastUpdateTime : currentTime - UPDATE_INTERVAL;
            
            UsageEvents usageEvents = usageStatsManager.queryEvents(startTime, currentTime);
            UsageEvents.Event event = new UsageEvents.Event();
            
            Log.d(TAG, String.format("Querying usage events from %d to %d", startTime, currentTime));
            
            while (usageEvents.hasNextEvent()) {
                usageEvents.getNextEvent(event);
                if (event.getEventType() == UsageEvents.Event.MOVE_TO_FOREGROUND) {
                    String packageName = event.getPackageName();
                    Log.d(TAG, "App moved to foreground: " + packageName);
                    
                    // Update usage time for the previous app
                    if (!lastForegroundApp.isEmpty() && !lastForegroundApp.equals(packageName)) {
                        long timeSpent = currentTime - lastUpdateTime;
                        updateAppUsageTime(lastForegroundApp, timeSpent);
                        Log.d(TAG, String.format("Updated usage for %s: %d ms", lastForegroundApp, timeSpent));
                    }
                    
                    lastForegroundApp = packageName;
                    lastUpdateTime = currentTime;
                }
            }
            
            // Update the current app's usage time
            if (!lastForegroundApp.isEmpty()) {
                long timeSpent = currentTime - lastUpdateTime;
                updateAppUsageTime(lastForegroundApp, timeSpent);
                Log.d(TAG, String.format("Updated current app %s: %d ms", lastForegroundApp, timeSpent));
                lastUpdateTime = currentTime;
            }
            
            // Calculate total screen time and update shared preferences
            long totalTime = 0;
            Map<String, UsageStats> stats = usageStatsManager.queryAndAggregateUsageStats(
                getStartOfDay(),
                System.currentTimeMillis()
            );
            
            if (stats != null) {
                for (UsageStats usageStats : stats.values()) {
                    if (!isSystemApp(usageStats.getPackageName())) {
                        totalTime += usageStats.getTotalTimeInForeground();
                    }
                }
            }
            
            // Convert to minutes and update shared preferences
            float totalMinutes = totalTime / (1000f * 60f);
            SharedPreferences prefs = getSharedPreferences("ScreenTimeReminder", MODE_PRIVATE);
            prefs.edit()
                .putFloat("totalScreenTime", totalMinutes)
                .putLong("lastUpdateTime", System.currentTimeMillis())
                .apply();
            
            Log.d(TAG, String.format("Updated total screen time: %.2f minutes", totalMinutes));
            
            // Broadcast the updated usage data
            broadcastUsageData();
            
        } catch (Exception e) {
            Log.e(TAG, "Error updating app usage", e);
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
            SharedPreferences prefs = getSharedPreferences("ScreenTimeReminder", MODE_PRIVATE);
            float totalMinutes = prefs.getFloat("totalScreenTime", 0);
            long lastUpdate = prefs.getLong("lastUpdateTime", 0);
            
            // Create a JSON object with the usage data
            JSONObject usageData = new JSONObject();
            usageData.put("totalMinutes", totalMinutes);
            usageData.put("timestamp", lastUpdate);
            
            // Broadcast the update
            Intent intent = new Intent("com.screentimereminder.APP_USAGE_UPDATE");
            intent.putExtra("usageData", usageData.toString());
            sendBroadcast(intent);
            
            Log.d(TAG, "Broadcasted usage update with data: " + usageData.toString());
            
        } catch (Exception e) {
            Log.e(TAG, "Error broadcasting usage data", e);
        }
    }

    private void checkScreenTimeLimit(float totalMinutes) {
        try {
            // Get screen time limit from SharedPreferences
            SharedPreferences prefs = getSharedPreferences("ScreenTimeReminder", MODE_PRIVATE);
            int screenTimeLimit = prefs.getInt("screenTimeLimit", 60); // Default 60 minutes
            int notificationFrequency = prefs.getInt("notificationFrequency", 5); // Default 5 minutes
            
            // Get current time for notification tracking
            long currentTime = System.currentTimeMillis();
            
            // Get last notification times
            long lastLimitReachedNotification = prefs.getLong("lastLimitReachedNotification", 0);
            long lastApproachingLimitNotification = prefs.getLong("lastApproachingLimitNotification", 0);
            
            // Define cooldown period (1 minute in milliseconds)
            long NOTIFICATION_COOLDOWN = 60 * 1000;
            
            // Calculate remaining minutes
            int remainingMinutes = Math.max(0, screenTimeLimit - (int)totalMinutes);
            
            // Check if enough time has passed since last notifications
            boolean canShowLimitReached = (currentTime - lastLimitReachedNotification) >= NOTIFICATION_COOLDOWN;
            boolean canShowApproaching = (currentTime - lastApproachingLimitNotification) >= NOTIFICATION_COOLDOWN;
            
            Log.d(TAG, String.format("Checking screen time limit - Total: %.2f, Limit: %d, Remaining: %d, CanShowLimit: %b, CanShowApproaching: %b",
                totalMinutes, screenTimeLimit, remainingMinutes, canShowLimitReached, canShowApproaching));
            
            if (totalMinutes >= screenTimeLimit && canShowLimitReached) {
                Log.d(TAG, "Showing limit reached notification");
                showNotification("Screen Time Limit Reached", 
                    "You have reached your daily screen time limit of " + screenTimeLimit + " minutes.");
                prefs.edit().putLong("lastLimitReachedNotification", currentTime).apply();
            } else if (remainingMinutes <= notificationFrequency && remainingMinutes > 0 && canShowApproaching) {
                Log.d(TAG, "Showing approaching limit notification");
                showNotification("Approaching Screen Time Limit", 
                    "You have " + remainingMinutes + " minutes remaining.");
                prefs.edit().putLong("lastApproachingLimitNotification", currentTime).apply();
            }
        } catch (Exception e) {
            Log.e(TAG, "Error checking screen time limit", e);
        }
    }

    private void showNotification(String title, String message) {
        try {
            // Get the latest screen time data
            long totalTime = 0;
            Map<String, UsageStats> stats = usageStatsManager.queryAndAggregateUsageStats(
                getStartOfDay(),
                System.currentTimeMillis()
            );
            
            if (stats != null) {
                for (UsageStats usageStats : stats.values()) {
                    if (!isSystemApp(usageStats.getPackageName())) {
                        totalTime += usageStats.getTotalTimeInForeground();
                    }
                }
            }
            
            // Convert to minutes
            float totalMinutes = totalTime / (1000f * 60f);
            
            // Update shared preferences with latest data
            SharedPreferences prefs = getSharedPreferences("ScreenTimeReminder", MODE_PRIVATE);
            prefs.edit()
                .putFloat("totalScreenTime", totalMinutes)
                .putLong("lastUpdateTime", System.currentTimeMillis())
                .apply();
            
            // Format the time
            int hours = (int) (totalMinutes / 60);
            int minutes = (int) (totalMinutes % 60);
            String timeString = String.format("%d hours %d minutes", hours, minutes);
            
            // Create the notification message
            String notificationMessage = String.format("%s\nCurrent screen time: %s", message, timeString);
            
            NotificationManager notificationManager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
            
            // Clear all previous notifications
            notificationManager.cancelAll();
            
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                NotificationChannel channel = new NotificationChannel(
                    "screen_time_channel",
                    "Screen Time Notifications",
                    NotificationManager.IMPORTANCE_HIGH
                );
                channel.setDescription("Notifications for screen time limits");
                channel.enableLights(true);
                channel.enableVibration(true);
                channel.setShowBadge(true);
                channel.setLockscreenVisibility(Notification.VISIBILITY_PUBLIC);
                channel.setImportance(NotificationManager.IMPORTANCE_HIGH);
                notificationManager.createNotificationChannel(channel);
            }
            
            // Create an intent for when the notification is tapped
            Intent intent = new Intent(this, MainActivity.class);
            intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
            PendingIntent pendingIntent = PendingIntent.getActivity(
                this,
                0,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
            );
            
            // Determine notification ID based on the type of notification
            int notificationId = NOTIFICATION_ID_BACKGROUND_SERVICE;
            if (title.contains("Limit Reached")) {
                notificationId = NOTIFICATION_ID_LIMIT_REACHED;
            } else if (title.contains("Approaching")) {
                notificationId = NOTIFICATION_ID_APPROACHING_LIMIT;
            }
            
            NotificationCompat.Builder builder = new NotificationCompat.Builder(this, "screen_time_channel")
                .setSmallIcon(android.R.drawable.ic_dialog_info)
                .setContentTitle(title)
                .setContentText(notificationMessage)
                .setStyle(new NotificationCompat.BigTextStyle().bigText(notificationMessage))
                .setPriority(NotificationCompat.PRIORITY_MAX)
                .setAutoCancel(false)
                .setOngoing(true)
                .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
                .setCategory(NotificationCompat.CATEGORY_ALARM)
                .setDefaults(NotificationCompat.DEFAULT_ALL)
                .setOnlyAlertOnce(false)
                .setContentIntent(pendingIntent)
                .setFullScreenIntent(pendingIntent, true)
                .setColor(getResources().getColor(android.R.color.holo_red_dark))
                .setTicker(notificationMessage)
                .setWhen(System.currentTimeMillis())
                .setShowWhen(true)
                .setUsesChronometer(true)
                .setChronometerCountDown(false)
                .setTimeoutAfter(0); // Never timeout
            
            // Add action buttons
            Intent dismissIntent = new Intent(this, MainActivity.class);
            dismissIntent.setAction("DISMISS_NOTIFICATION");
            PendingIntent dismissPendingIntent = PendingIntent.getActivity(
                this,
                1,
                dismissIntent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
            );
            
            builder.addAction(
                android.R.drawable.ic_menu_close_clear_cancel,
                "Dismiss",
                dismissPendingIntent
            );
            
            // Show the notification with the appropriate ID
            notificationManager.notify(notificationId, builder.build());
            Log.d(TAG, "Showing notification with ID " + notificationId + ": " + notificationMessage);
        } catch (Exception e) {
            Log.e(TAG, "Error showing notification", e);
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

    @Override
    public void onDestroy() {
        super.onDestroy();
        Log.d(TAG, "Service onDestroy");
        try {
            isRunning = false;
            
            // Remove the update runnable
            if (updateRunnable != null) {
                mainHandler.removeCallbacks(updateRunnable);
                Log.d(TAG, "Removed update runnable");
            }
            
            // Remove the watchdog runnable
            if (watchdogRunnable != null) {
                mainHandler.removeCallbacks(watchdogRunnable);
                Log.d(TAG, "Removed watchdog runnable");
            }
            
            // Release wake lock safely
            if (wakeLock != null && wakeLock.isHeld()) {
                try {
                    wakeLock.release();
                    Log.d(TAG, "WakeLock released successfully");
                } catch (Exception e) {
                    Log.e(TAG, "Error releasing WakeLock", e);
                }
            }
            
            // Restart the service
            Intent restartServiceIntent = new Intent(getApplicationContext(), this.getClass());
            restartServiceIntent.setPackage(getPackageName());
            restartServiceIntent.setAction("RESTART_SERVICE");
            
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                startForegroundService(restartServiceIntent);
                Log.d(TAG, "Restarted service as foreground service");
            } else {
                startService(restartServiceIntent);
                Log.d(TAG, "Restarted service");
            }
        } catch (Exception e) {
            Log.e(TAG, "Error in onDestroy", e);
        }
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
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                NotificationChannel channel = new NotificationChannel(
                    CHANNEL_ID,
                    "Screen Time Reminder",
                    NotificationManager.IMPORTANCE_HIGH
                );
                channel.setDescription("Background service for screen time tracking");
                channel.enableLights(true);
                channel.enableVibration(true);
                channel.setShowBadge(true);
                channel.setLockscreenVisibility(Notification.VISIBILITY_PUBLIC);
                
                NotificationManager notificationManager = getSystemService(NotificationManager.class);
                if (notificationManager != null) {
                    notificationManager.createNotificationChannel(channel);
                    Log.d(TAG, "Notification channel created successfully");
                }
            }
        } catch (Exception e) {
            Log.e(TAG, "Error creating notification channel", e);
        }
    }

    private Notification createNotification() {
        try {
            // Create a persistent notification
            NotificationCompat.Builder builder = new NotificationCompat.Builder(this, CHANNEL_ID)
                .setContentTitle("Screen Time Reminder")
                .setContentText("Tracking screen time")
                .setSmallIcon(android.R.drawable.ic_menu_recent_history)
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setOngoing(true)
                .setAutoCancel(false)
                .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
                .setCategory(NotificationCompat.CATEGORY_SERVICE)
                .setShowWhen(false)
                .setOnlyAlertOnce(true)
                .setDefaults(NotificationCompat.DEFAULT_ALL)
                .setSound(null)
                .setVibrate(null);

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
} 