package com.screentimereminder.app;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.app.usage.UsageEvents;
import android.app.usage.UsageStatsManager;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.PowerManager;
import android.provider.Settings;
import android.util.Log;

import androidx.core.app.NotificationCompat;

import java.util.Calendar;
import java.util.HashMap;
import java.util.Map;

import org.json.JSONArray;
import org.json.JSONObject;

public class AppUsageService extends Service {
    private static final String TAG = "AppUsageService";
    private static final long UPDATE_INTERVAL = 60000; // 1 minute
    private static final String CHANNEL_ID = "AppUsageServiceChannel";
    private static final int NOTIFICATION_ID = 1001;
    
    private Handler handler;
    private Runnable updateRunnable;
    private UsageStatsManager usageStatsManager;
    private String lastForegroundApp = "";
    private long lastUpdateTime = 0;
    private Map<String, Long> appUsageMap = new HashMap<>();
    private PowerManager.WakeLock wakeLock;

    @Override
    public void onCreate() {
        super.onCreate();
        Log.d(TAG, "Service created");
        
        try {
            // Initialize managers
            handler = new Handler(Looper.getMainLooper());
            usageStatsManager = (UsageStatsManager) getSystemService(Context.USAGE_STATS_SERVICE);
            
            if (usageStatsManager == null) {
                Log.e(TAG, "Failed to initialize UsageStatsManager");
                stopSelf();
                return;
            }
            
            // Create notification channel before starting foreground service
            createNotificationChannel();
            
            // Setup update runnable
            setupUpdateRunnable();
            
            // Acquire wake lock
            acquireWakeLock();
            
            Log.d(TAG, "Service initialized successfully");
        } catch (Exception e) {
            Log.e(TAG, "Error initializing service", e);
            stopSelf();
        }
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        try {
            Log.d(TAG, "Service started with action: " + (intent != null ? intent.getAction() : "null"));
            
            // Check if we have the necessary permissions
            if (!checkPermissions()) {
                Log.e(TAG, "Missing required permissions");
                stopSelf();
                return START_NOT_STICKY;
            }
            
            // Start as foreground service with notification
            try {
                Notification notification = createNotification();
                startForeground(NOTIFICATION_ID, notification);
                Log.d(TAG, "Started as foreground service successfully");
            } catch (Exception e) {
                Log.e(TAG, "Error starting foreground service", e);
                stopSelf();
                return START_NOT_STICKY;
            }
            
            if (intent != null && "GET_USAGE_DATA".equals(intent.getAction())) {
                // Force an immediate update and broadcast
                updateAppUsage();
            } else {
                // Ensure tracking is running for normal start
                if (!isTrackingActive()) {
                    startTracking();
                }
            }
            
            return START_STICKY;
        } catch (Exception e) {
            Log.e(TAG, "Error in onStartCommand", e);
            stopSelf();
            return START_NOT_STICKY;
        }
    }

    private boolean checkPermissions() {
        try {
            // Check for PACKAGE_USAGE_STATS permission using UsageStatsManager
            UsageStatsManager usageStatsManager = (UsageStatsManager) getSystemService(Context.USAGE_STATS_SERVICE);
            long currentTime = System.currentTimeMillis();
            // Query for a short time range
            UsageEvents events = usageStatsManager.queryEvents(currentTime - 1000, currentTime);
            boolean hasUsageStatsPermission = events != null;
            
            // Check for FOREGROUND_SERVICE permission
            boolean hasForegroundServicePermission = true;
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                hasForegroundServicePermission = getPackageManager().checkPermission(
                    android.Manifest.permission.FOREGROUND_SERVICE, getPackageName())
                    == PackageManager.PERMISSION_GRANTED;
                
                // Also check for FOREGROUND_SERVICE_SPECIAL_USE permission on Android 14+
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                    hasForegroundServicePermission &= getPackageManager().checkPermission(
                        "android.permission.FOREGROUND_SERVICE_SPECIAL_USE", getPackageName())
                        == PackageManager.PERMISSION_GRANTED;
                }
            }
            
            Log.d(TAG, String.format("Permissions check - Usage Stats: %b, Foreground Service: %b",
                hasUsageStatsPermission, hasForegroundServicePermission));
            
            return hasUsageStatsPermission && hasForegroundServicePermission;
        } catch (Exception e) {
            Log.e(TAG, "Error checking permissions", e);
            return false;
        }
    }

    private void createNotificationChannel() {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                NotificationManager notificationManager = getSystemService(NotificationManager.class);
                if (notificationManager == null) {
                    Log.e(TAG, "NotificationManager is null");
                    return;
                }
                
                // Check if channel already exists
                NotificationChannel existingChannel = notificationManager.getNotificationChannel(CHANNEL_ID);
                if (existingChannel != null) {
                    Log.d(TAG, "Notification channel already exists");
                    return;
                }
                
                NotificationChannel channel = new NotificationChannel(
                    CHANNEL_ID,
                    "Screen Time Alerts",
                    NotificationManager.IMPORTANCE_HIGH
                );
                channel.setDescription("Alerts for screen time limits and usage");
                channel.setShowBadge(true);
                channel.enableLights(true);
                channel.setLightColor(android.graphics.Color.BLUE);
                channel.enableVibration(true);
                channel.setVibrationPattern(new long[]{100, 200, 300, 400, 500});
                
                notificationManager.createNotificationChannel(channel);
                Log.d(TAG, "Created notification channel successfully");
            }
        } catch (Exception e) {
            Log.e(TAG, "Error creating notification channel", e);
        }
    }

    private Notification createNotification() {
        try {
            Intent notificationIntent = new Intent(this, MainActivity.class);
            PendingIntent pendingIntent = PendingIntent.getActivity(
                this,
                0,
                notificationIntent,
                PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT
            );

            NotificationCompat.Builder builder = new NotificationCompat.Builder(this, CHANNEL_ID)
                .setContentTitle("Screen Time Reminder")
                .setContentText("Monitoring app usage")
                .setSmallIcon(R.drawable.ic_launcher_foreground)
                .setOngoing(true)
                .setPriority(NotificationCompat.PRIORITY_LOW)
                .setContentIntent(pendingIntent);

            // For Android 14+, set foreground service type
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                builder.setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE);
            }

            return builder.build();
        } catch (Exception e) {
            Log.e(TAG, "Error creating notification", e);
            // Return a basic notification as fallback
            return new NotificationCompat.Builder(this, CHANNEL_ID)
                .setContentTitle("Screen Time Reminder")
                .setContentText("Running")
                .setSmallIcon(R.drawable.ic_launcher_foreground)
                .build();
        }
    }

    private void acquireWakeLock() {
        try {
            PowerManager powerManager = (PowerManager) getSystemService(POWER_SERVICE);
            if (powerManager != null) {
                if (wakeLock != null && wakeLock.isHeld()) {
                    wakeLock.release();
                }
                
                wakeLock = powerManager.newWakeLock(
                    PowerManager.PARTIAL_WAKE_LOCK,
                    "ScreenTimeReminder::AppUsageServiceWakeLock"
                );
                wakeLock.setReferenceCounted(false);
                wakeLock.acquire(24 * 60 * 60 * 1000L); // 24 hours timeout
                Log.d(TAG, "Wake lock acquired successfully");
            }
        } catch (Exception e) {
            Log.e(TAG, "Error acquiring wake lock", e);
        }
    }

    private boolean isTrackingActive() {
        return handler != null && updateRunnable != null && handler.hasCallbacks(updateRunnable);
    }

    private void setupUpdateRunnable() {
        updateRunnable = new Runnable() {
            @Override
            public void run() {
                try {
                    updateAppUsage();
                } catch (Exception e) {
                    Log.e(TAG, "Error in update runnable", e);
                } finally {
                    // Always schedule next update
                    if (handler != null) {
                        handler.postDelayed(this, UPDATE_INTERVAL);
                    }
                }
            }
        };
    }

    private void startTracking() {
        Log.d(TAG, "Starting tracking");
        if (handler != null && updateRunnable != null) {
            handler.removeCallbacks(updateRunnable);
            handler.post(updateRunnable);
            Log.d(TAG, "Tracking started successfully");
        }
    }

    private void updateAppUsage() {
        try {
            long currentTime = System.currentTimeMillis();
            Log.d(TAG, "Updating app usage at " + new java.util.Date(currentTime));
            
            // Get start of day
            Calendar calendar = Calendar.getInstance();
            calendar.set(Calendar.HOUR_OF_DAY, 0);
            calendar.set(Calendar.MINUTE, 0);
            calendar.set(Calendar.SECOND, 0);
            calendar.set(Calendar.MILLISECOND, 0);
            long startTime = calendar.getTimeInMillis();
            
            // Query for events since start of day
            UsageEvents usageEvents = usageStatsManager.queryEvents(startTime, currentTime);
            UsageEvents.Event event = new UsageEvents.Event();
            
            // Reset the map for today's data
            appUsageMap.clear();
            
            String currentApp = null;
            long lastEventTime = startTime;
            
            while (usageEvents.hasNextEvent()) {
                usageEvents.getNextEvent(event);
                
                if (event.getEventType() == UsageEvents.Event.MOVE_TO_FOREGROUND) {
                    // New app came to foreground
                    currentApp = event.getPackageName();
                    lastEventTime = event.getTimeStamp();
                    Log.d(TAG, "App moved to foreground: " + currentApp + " at " + new java.util.Date(lastEventTime));
                    
                } else if (event.getEventType() == UsageEvents.Event.MOVE_TO_BACKGROUND && currentApp != null) {
                    // App went to background, update its time
                    long timeSpent = event.getTimeStamp() - lastEventTime;
                    if (timeSpent > 0) {
                        updateAppUsageTime(currentApp, timeSpent);
                        Log.d(TAG, String.format("Updated time for %s: +%d ms (total: %d ms)", 
                            currentApp, timeSpent, appUsageMap.get(currentApp)));
                    }
                    currentApp = null;
                }
            }
            
            // Update time for the current foreground app
            if (currentApp != null) {
                long timeSpent = currentTime - lastEventTime;
                if (timeSpent > 0) {
                    updateAppUsageTime(currentApp, timeSpent);
                    Log.d(TAG, String.format("Updated time for current app %s: +%d ms (total: %d ms)", 
                        currentApp, timeSpent, appUsageMap.get(currentApp)));
                }
            }
            
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
            // Calculate total screen time
            long totalScreenTime = 0;
            for (Map.Entry<String, Long> entry : appUsageMap.entrySet()) {
                if (!isSystemApp(entry.getKey())) {
                    totalScreenTime += entry.getValue();
                }
            }
            
            // Convert usage data to JSON
            JSONObject data = new JSONObject();
            data.put("totalScreenTime", totalScreenTime / 60000.0); // Convert to minutes
            
            JSONArray apps = new JSONArray();
            for (Map.Entry<String, Long> entry : appUsageMap.entrySet()) {
                String packageName = entry.getKey();
                
                // Skip system apps and our own app
                if (isSystemApp(packageName) || packageName.equals(getPackageName())) {
                    continue;
                }
                
                JSONObject app = new JSONObject();
                app.put("packageName", packageName);
                app.put("time", entry.getValue() / 60000.0); // Convert to minutes
                apps.put(app);
            }
            data.put("apps", apps);
            
            // Broadcast the data
            Intent intent = new Intent("com.screentimereminder.APP_USAGE_UPDATE");
            intent.putExtra("usageData", data.toString());
            sendBroadcast(intent);
            
            Log.d(TAG, "Broadcasting usage data: " + data.toString());
        } catch (Exception e) {
            Log.e(TAG, "Error broadcasting usage data", e);
        }
    }

    private boolean isSystemApp(String packageName) {
        try {
            PackageManager packageManager = getPackageManager();
            ApplicationInfo appInfo = packageManager.getApplicationInfo(packageName, 0);
            return (appInfo.flags & ApplicationInfo.FLAG_SYSTEM) != 0;
        } catch (Exception e) {
            return false;
        }
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @Override
    public void onDestroy() {
        try {
            Log.d(TAG, "Service being destroyed");
            
            if (handler != null) {
                handler.removeCallbacks(updateRunnable);
                handler = null;
            }
            
            if (wakeLock != null && wakeLock.isHeld()) {
                wakeLock.release();
                wakeLock = null;
            }
            
            // Clear any notifications
            NotificationManager notificationManager = getSystemService(NotificationManager.class);
            if (notificationManager != null) {
                notificationManager.cancel(NOTIFICATION_ID);
            }
            
            super.onDestroy();
            Log.d(TAG, "Service destroyed successfully");
        } catch (Exception e) {
            Log.e(TAG, "Error in onDestroy", e);
            super.onDestroy();
        }
    }
} 