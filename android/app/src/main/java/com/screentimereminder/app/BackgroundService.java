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
            IntentFilter filter = new IntentFilter(ACTION_RESTART_SERVICE);
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                registerReceiver(restartReceiver, filter, Context.RECEIVER_NOT_EXPORTED);
            } else {
                registerReceiver(restartReceiver, filter);
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
            
            // Register for package updates
            IntentFilter filter2 = new IntentFilter();
            filter2.addAction(Intent.ACTION_PACKAGE_REPLACED);
            filter2.addDataScheme("package");
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                registerReceiver(packageUpdateReceiver, filter2, Context.RECEIVER_NOT_EXPORTED);
            } else {
                registerReceiver(packageUpdateReceiver, filter2);
            }
            
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
            schedulePeriodicUpdates();

            // Set up watchdog
            setupWatchdog();

            // Schedule periodic service check using AlarmManager as backup
            scheduleServiceRestartAlarm();

        } catch (Exception e) {
            Log.e(TAG, "Error in service onCreate", e);
            restartService();
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
                    Notification notification = createHighPriorityNotification();
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
                        Notification notification = createHighPriorityNotification();
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

            // Set up periodic screen time updates
            if (scheduler == null || scheduler.isShutdown()) {
                scheduler = Executors.newSingleThreadScheduledExecutor();
                scheduler.scheduleAtFixedRate(new Runnable() {
                    @Override
                    public void run() {
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
                                        long timeSpent = event.getTimeStamp() - lastEventTime;
                                        nativeTotalTime += timeSpent / (60f * 1000f);
                                    }
                                    lastPackage = packageName;
                                    lastEventTime = event.getTimeStamp();
                                } else if (event.getEventType() == UsageEvents.Event.MOVE_TO_BACKGROUND) {
                                    if (lastPackage != null && lastEventTime > 0) {
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

                            // Update SharedPreferences with new value
                            SharedPreferences prefs = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
                            SharedPreferences.Editor editor = prefs.edit();
                            editor.putFloat("capacitorScreenTime", nativeTotalTime);
                            editor.putLong("lastCapacitorUpdate", System.currentTimeMillis());
                            editor.apply();

                            // Broadcast the update
                            Intent broadcastIntent = new Intent("com.screentimereminder.app.APP_USAGE_UPDATE");
                            JSONObject updateData = new JSONObject();
                            updateData.put("totalScreenTime", nativeTotalTime);
                            updateData.put("timestamp", System.currentTimeMillis());
                            broadcastIntent.putExtra("usageData", updateData.toString());
                            sendBroadcast(broadcastIntent);

                            // Update widget
                            Intent updateIntent = new Intent(getApplicationContext(), ScreenTimeWidgetProvider.class);
                            updateIntent.setAction("android.appwidget.action.APPWIDGET_UPDATE");
                            int[] ids = AppWidgetManager.getInstance(getApplicationContext())
                                .getAppWidgetIds(new ComponentName(getApplicationContext(), ScreenTimeWidgetProvider.class));
                            updateIntent.putExtra(AppWidgetManager.EXTRA_APPWIDGET_IDS, ids);
                            sendBroadcast(updateIntent);

                            Log.d(TAG, "Periodic update completed. Total screen time: " + nativeTotalTime + " minutes");
                        } catch (Exception e) {
                            Log.e(TAG, "Error in periodic update", e);
                        }
                    }
                }, 0, 1, TimeUnit.MINUTES); // Update every minute
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
        
        // Schedule immediate restart
        Intent restartServiceIntent = new Intent(getApplicationContext(), this.getClass());
        restartServiceIntent.setPackage(getPackageName());
        restartServiceIntent.setAction(ACTION_RESTART_SERVICE);
        
        PendingIntent restartServicePendingIntent = PendingIntent.getService(
            getApplicationContext(), 1, restartServiceIntent,
            PendingIntent.FLAG_ONE_SHOT | PendingIntent.FLAG_IMMUTABLE);
            
        AlarmManager alarmService = (AlarmManager) getApplicationContext()
            .getSystemService(Context.ALARM_SERVICE);
            
        if (alarmService != null) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                alarmService.setExactAndAllowWhileIdle(
                    AlarmManager.RTC_WAKEUP,
                    System.currentTimeMillis() + 1000,
                    restartServicePendingIntent
                );
            } else {
                alarmService.setExact(
                    AlarmManager.RTC_WAKEUP,
                    System.currentTimeMillis() + 1000,
                    restartServicePendingIntent
                );
            }
        }
        
        // Also try to restart immediately
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(restartServiceIntent);
        } else {
            startService(restartServiceIntent);
        }
    }

    private void updateAppUsage() {
        try {
            long currentTime = System.currentTimeMillis();
            
            // Use synchronized block to prevent concurrent updates
            synchronized (updateLock) {
                // Check if we've updated recently
                if (currentTime - lastUpdateTimestamp < UPDATE_THRESHOLD) {
                    Log.d(TAG, "Skipping update - too soon since last update");
                    return;
                }
                
                // Get current screen time using the same method as the widget
                float totalScreenTime = getTodayScreenTime(this);
                Log.d(TAG, "Total screen time calculated: " + totalScreenTime + " minutes");
                
                // Update shared preferences with native calculation
                SharedPreferences prefs = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
                SharedPreferences.Editor editor = prefs.edit();
                editor.putFloat(AppUsageTracker.KEY_TOTAL_SCREEN_TIME, totalScreenTime);
                editor.putLong(AppUsageTracker.KEY_LAST_UPDATE, currentTime);
                editor.apply();

                // Check if our app is in the foreground
                boolean isAppInForeground = false;
                UsageStatsManager usageStatsManager = (UsageStatsManager) getSystemService(Context.USAGE_STATS_SERVICE);
                if (usageStatsManager != null) {
                    long endTime = System.currentTimeMillis();
                    long startTime = endTime - 1000; // Check last second
                    UsageEvents usageEvents = usageStatsManager.queryEvents(startTime, endTime);
                    UsageEvents.Event event = new UsageEvents.Event();
                    while (usageEvents.hasNextEvent()) {
                        usageEvents.getNextEvent(event);
                        if (event.getEventType() == UsageEvents.Event.MOVE_TO_FOREGROUND && 
                            event.getPackageName().equals(getPackageName())) {
                            isAppInForeground = true;
                            break;
                        }
                    }
                }

                // Only check screen time limit if app is in background
                if (!isAppInForeground) {
                    Log.d(TAG, "App is in background, checking screen time limit");
                    AppUsageTracker.checkScreenTimeLimitStatic(this, Math.round(totalScreenTime));
                } else {
                    Log.d(TAG, "App is in foreground, skipping screen time limit check");
                }
                
                // Broadcast the update
                Intent broadcastIntent = new Intent("com.screentimereminder.app.APP_USAGE_UPDATE");
                JSONObject updateData = new JSONObject();
                updateData.put("totalScreenTime", totalScreenTime);
                updateData.put("timestamp", currentTime);
                broadcastIntent.putExtra("usageData", updateData.toString());
                sendBroadcast(broadcastIntent);
                
                // Update widget
                Intent updateIntent = new Intent(this, ScreenTimeWidgetProvider.class);
                updateIntent.setAction("android.appwidget.action.APPWIDGET_UPDATE");
                int[] ids = AppWidgetManager.getInstance(this)
                    .getAppWidgetIds(new ComponentName(this, ScreenTimeWidgetProvider.class));
                updateIntent.putExtra(AppWidgetManager.EXTRA_APPWIDGET_IDS, ids);
                sendBroadcast(updateIntent);
                
                // Update timestamp after successful update
                lastUpdateTimestamp = currentTime;
                
                Log.d(TAG, "Background update completed. Total time: " + totalScreenTime + " minutes");
            }
        } catch (Exception e) {
            Log.e(TAG, "Error in background update", e);
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

    private void schedulePeriodicUpdates() {
        try {
            if (scheduler == null || scheduler.isShutdown()) {
                scheduler = Executors.newSingleThreadScheduledExecutor();
                scheduler.scheduleAtFixedRate(() -> {
                    try {
                        updateAppUsage();
                    } catch (Exception e) {
                        Log.e(TAG, "Error in periodic update", e);
                    }
                }, 0, 1, TimeUnit.MINUTES);
            }
        } catch (Exception e) {
            Log.e(TAG, "Error scheduling periodic updates", e);
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
} 