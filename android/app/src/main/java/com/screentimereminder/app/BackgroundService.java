package com.screentimereminder.app;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.app.usage.UsageEvents;
import android.app.usage.UsageStatsManager;
import android.content.Context;
import android.content.Intent;
import android.os.Binder;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.PowerManager;
import android.util.Log;

import androidx.core.app.NotificationCompat;

import java.util.HashMap;
import java.util.Map;

public class BackgroundService extends Service {
    private static final String TAG = "BackgroundService";
    private static final String CHANNEL_ID = "ScreenTimeReminderChannel";
    private static final int NOTIFICATION_ID = 1;
    private static final long UPDATE_INTERVAL = 60000; // 1 minute
    private static boolean isRunning = false;

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
                    updateAppUsage();
                    mainHandler.postDelayed(this, UPDATE_INTERVAL);
                }
            };
            
            // Acquire wake lock with error handling
            try {
                PowerManager powerManager = (PowerManager) getSystemService(POWER_SERVICE);
                if (powerManager != null) {
                    wakeLock = powerManager.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "ScreenTimeReminder::BackgroundTracking");
                    if (wakeLock != null) {
                        wakeLock.setReferenceCounted(false);
                        wakeLock.acquire(10 * 60 * 1000L); // 10 minutes instead of 24 hours
                        Log.d(TAG, "WakeLock acquired successfully");
                    }
                }
            } catch (Exception e) {
                Log.e(TAG, "Error acquiring WakeLock", e);
            }
        } catch (Exception e) {
            Log.e(TAG, "Error in onCreate", e);
        }
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        Log.d(TAG, "Service onStartCommand");
        try {
            // Create and start foreground notification
            Notification notification = createNotification();
            if (notification != null) {
                startForeground(NOTIFICATION_ID, notification);
            }
            
            // Start the update runnable
            if (updateRunnable != null) {
                mainHandler.post(updateRunnable);
            }
            
            return START_STICKY;
        } catch (Exception e) {
            Log.e(TAG, "Error in onStartCommand", e);
            return START_NOT_STICKY;
        }
    }

    private void updateAppUsage() {
        try {
            long currentTime = System.currentTimeMillis();
            long startTime = lastUpdateTime > 0 ? lastUpdateTime : currentTime - UPDATE_INTERVAL;
            
            UsageEvents usageEvents = usageStatsManager.queryEvents(startTime, currentTime);
            UsageEvents.Event event = new UsageEvents.Event();
            
            while (usageEvents.hasNextEvent()) {
                usageEvents.getNextEvent(event);
                if (event.getEventType() == UsageEvents.Event.MOVE_TO_FOREGROUND) {
                    String packageName = event.getPackageName();
                    
                    // Update usage time for the previous app
                    if (!lastForegroundApp.isEmpty() && !lastForegroundApp.equals(packageName)) {
                        long timeSpent = currentTime - lastUpdateTime;
                        updateAppUsageTime(lastForegroundApp, timeSpent);
                    }
                    
                    lastForegroundApp = packageName;
                    lastUpdateTime = currentTime;
                }
            }
            
            // Update the current app's usage time
            if (!lastForegroundApp.isEmpty()) {
                long timeSpent = currentTime - lastUpdateTime;
                updateAppUsageTime(lastForegroundApp, timeSpent);
                lastUpdateTime = currentTime;
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
            // Convert usage data to JSON
            StringBuilder jsonBuilder = new StringBuilder();
            jsonBuilder.append("[");
            
            boolean first = true;
            for (Map.Entry<String, Long> entry : appUsageMap.entrySet()) {
                if (!first) {
                    jsonBuilder.append(",");
                }
                first = false;
                
                jsonBuilder.append("{")
                    .append("\"packageName\":\"").append(entry.getKey()).append("\",")
                    .append("\"time\":").append(entry.getValue() / 60000.0) // Convert to minutes
                    .append("}");
            }
            
            jsonBuilder.append("]");
            
            // Broadcast the data
            Intent intent = new Intent("com.screentimereminder.APP_USAGE_UPDATE");
            intent.putExtra("usageData", jsonBuilder.toString());
            sendBroadcast(intent);
            
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
                    NotificationManager.IMPORTANCE_LOW
                );
                channel.setDescription("Background service for screen time tracking");
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
            NotificationCompat.Builder builder = new NotificationCompat.Builder(this, CHANNEL_ID)
                .setContentTitle("Screen Time Reminder")
                .setContentText("Tracking screen time in background")
                .setSmallIcon(android.R.drawable.ic_menu_recent_history)
                .setPriority(NotificationCompat.PRIORITY_LOW)
                .setOngoing(true); // Make notification persistent

            return builder.build();
        } catch (Exception e) {
            Log.e(TAG, "Error creating notification", e);
            return null;
        }
    }
} 