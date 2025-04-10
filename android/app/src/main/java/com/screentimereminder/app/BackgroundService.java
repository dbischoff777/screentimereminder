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
import java.util.concurrent.ExecutorService;

import org.json.JSONObject;
import org.json.JSONArray;

public class BackgroundService extends Service {
    private static final String TAG = "BackgroundService";
    private static final String CHANNEL_ID = "ScreenTimeReminderChannel";
    private static final String PREFS_NAME = "ScreenTimeReminder";
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

    private final ExecutorService backgroundExecutor = Executors.newSingleThreadExecutor();
    private final Object updateLock = new Object();
    private volatile boolean isUpdating = false;

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
            
            // Set up the update runnable
            updateRunnable = new Runnable() {
                @Override
                public void run() {
                    try {
                        Log.d(TAG, "Starting background update cycle");
                        updateAppUsage();
                        // Schedule next update
                        mainHandler.postDelayed(this, UPDATE_INTERVAL);
                    } catch (Exception e) {
                        Log.e(TAG, "Error in background update cycle", e);
                        // Retry after a shorter interval if there's an error
                        mainHandler.postDelayed(this, 30000);
                    }
                }
            };
            
            // Start the update runnable
            mainHandler.post(updateRunnable);
            
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
            
            // Start as foreground service
            startForeground(NOTIFICATION_ID_BACKGROUND_SERVICE, createNotification());
            
            // Acquire wake lock
            PowerManager powerManager = (PowerManager) getSystemService(Context.POWER_SERVICE);
            if (powerManager != null) {
                wakeLock = powerManager.newWakeLock(
                    PowerManager.PARTIAL_WAKE_LOCK,
                    "ScreenTimeReminder::BackgroundTracking"
                );
                wakeLock.acquire();
            }
        } catch (Exception e) {
            Log.e(TAG, "Error in service onCreate", e);
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
            // Only create notification if we're not already a foreground service
            if (!isRunning) {
                try {
                    Notification notification = createNotification();
                    if (notification != null) {
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                            startForeground(NOTIFICATION_ID_BACKGROUND_SERVICE, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC);
                        } else {
                            startForeground(NOTIFICATION_ID_BACKGROUND_SERVICE, notification);
                        }
                        Log.d(TAG, "Started as foreground service in onStartCommand");
                        isRunning = true;
                    }
                } catch (SecurityException e) {
                    Log.e(TAG, "SecurityException when starting foreground service", e);
                    // Try to start without foreground service type
                    try {
                        Notification notification = createNotification();
                        if (notification != null) {
                            startForeground(NOTIFICATION_ID_BACKGROUND_SERVICE, notification);
                            Log.d(TAG, "Started as foreground service without type in onStartCommand");
                            isRunning = true;
                        }
                    } catch (Exception ex) {
                        Log.e(TAG, "Failed to start foreground service in onStartCommand", ex);
                    }
                }
            }
            
            // Start the update runnable if not already running
            if (updateRunnable != null && !isRunning) {
                mainHandler.post(updateRunnable);
                Log.d(TAG, "Started update runnable in onStartCommand");
            }
            
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
            // Get current usage stats
            long startTime = getStartOfDay();
            long endTime = System.currentTimeMillis();
            
            // Query usage events for more accurate calculation
            UsageEvents usageEvents = usageStatsManager.queryEvents(startTime, endTime);
            UsageEvents.Event event = new UsageEvents.Event();
            float nativeTotalTime = 0;
            String lastPackage = null;
            long lastEventTime = 0;
            
            while (usageEvents.hasNextEvent()) {
                usageEvents.getNextEvent(event);
                String packageName = event.getPackageName();
                
                // Skip our own app and system apps
                if (packageName.equals(getPackageName()) || isSystemApp(packageName)) {
                    continue;
                }
                
                if (event.getEventType() == UsageEvents.Event.MOVE_TO_FOREGROUND) {
                    if (lastPackage != null && lastEventTime > 0) {
                        // Calculate time spent in the last app
                        long timeSpent = event.getTimeStamp() - lastEventTime;
                        nativeTotalTime += timeSpent / (60f * 1000f);
                    }
                    lastPackage = packageName;
                    lastEventTime = event.getTimeStamp();
                } else if (event.getEventType() == UsageEvents.Event.MOVE_TO_BACKGROUND) {
                    if (lastPackage != null && lastEventTime > 0) {
                        // Calculate time spent in the current app
                        long timeSpent = event.getTimeStamp() - lastEventTime;
                        nativeTotalTime += timeSpent / (60f * 1000f);
                    }
                    lastPackage = null;
                    lastEventTime = 0;
                }
            }
            
            // If there's still an active app, calculate its time up to now
            if (lastPackage != null && lastEventTime > 0) {
                long timeSpent = endTime - lastEventTime;
                nativeTotalTime += timeSpent / (60f * 1000f);
            }
            
            // Get the stored capacitor value
            SharedPreferences prefs = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
            float capacitorTime = prefs.getFloat("capacitorScreenTime", 0);
            long lastCapacitorUpdate = prefs.getLong("lastCapacitorUpdate", 0);
            
            // Use the higher value between native and capacitor, but only if capacitor value is recent (within 5 minutes)
            float finalTotalTime = nativeTotalTime;
            if (lastCapacitorUpdate > 0 && (System.currentTimeMillis() - lastCapacitorUpdate) < 5 * 60 * 1000) {
                finalTotalTime = Math.max(nativeTotalTime, capacitorTime);
            }
            
            // Update shared preferences
            SharedPreferences.Editor editor = prefs.edit();
            editor.putFloat(AppUsageTracker.KEY_TOTAL_SCREEN_TIME, finalTotalTime);
            editor.putLong(AppUsageTracker.KEY_LAST_UPDATE, System.currentTimeMillis());
            editor.apply();
            
            // Broadcast the update
            Intent broadcastIntent = new Intent("com.screentimereminder.app.APP_USAGE_UPDATE");
            JSONObject updateData = new JSONObject();
            updateData.put("totalScreenTime", finalTotalTime);
            updateData.put("timestamp", System.currentTimeMillis());
            broadcastIntent.putExtra("usageData", updateData.toString());
            sendBroadcast(broadcastIntent);
            
            // Update widget
            Intent updateIntent = new Intent(this, ScreenTimeWidgetProvider.class);
            updateIntent.setAction("android.appwidget.action.APPWIDGET_UPDATE");
            int[] ids = AppWidgetManager.getInstance(this)
                .getAppWidgetIds(new ComponentName(this, ScreenTimeWidgetProvider.class));
            updateIntent.putExtra(AppWidgetManager.EXTRA_APPWIDGET_IDS, ids);
            sendBroadcast(updateIntent);
            
            Log.d(TAG, "Background update completed. Total time: " + finalTotalTime + " minutes");
        } catch (Exception e) {
            Log.e(TAG, "Error in background update", e);
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
            Intent intent = new Intent("com.screentimereminder.APP_USAGE_UPDATE");
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
            
            // Shutdown executor
            if (backgroundExecutor != null && !backgroundExecutor.isShutdown()) {
                backgroundExecutor.shutdown();
                try {
                    if (!backgroundExecutor.awaitTermination(1, TimeUnit.SECONDS)) {
                        backgroundExecutor.shutdownNow();
                    }
                } catch (InterruptedException e) {
                    backgroundExecutor.shutdownNow();
                }
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
} 