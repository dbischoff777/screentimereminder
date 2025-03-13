package com.screentimereminder.app;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.os.Binder;
import android.os.Build;
import android.os.IBinder;
import android.os.PowerManager;
import android.util.Log;

import androidx.annotation.Nullable;
import androidx.core.app.NotificationCompat;

/**
 * A simple background service implementation that replaces the Cordova plugin.
 * This service runs in the foreground to prevent being killed by the system.
 */
public class BackgroundService extends Service {
    private static final String TAG = "BackgroundService";
    private static final int NOTIFICATION_ID = 1000;
    private static final String CHANNEL_ID = "screen_time_reminder_channel";
    
    private final IBinder binder = new LocalBinder();
    private PowerManager.WakeLock wakeLock;
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
        } catch (Exception e) {
            Log.e(TAG, "Error in onCreate", e);
        }
    }
    
    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        try {
            Log.d(TAG, "Background service started");
            
            if (!isRunning) {
                isRunning = true;
                startForeground();
                acquireWakeLock();
            }
            
            // If the service is killed, restart it
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
            
            if (wakeLock != null && wakeLock.isHeld()) {
                wakeLock.release();
                wakeLock = null;
            }
            
            isRunning = false;
            super.onDestroy();
        } catch (Exception e) {
            Log.e(TAG, "Error in onDestroy", e);
        }
    }
    
    /**
     * Start the service as a foreground service with a notification
     */
    private void startForeground() {
        try {
            // Create notification channel for Android 8.0+
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                try {
                    NotificationChannel channel = new NotificationChannel(
                            CHANNEL_ID,
                            "Screen Time Reminder",
                            NotificationManager.IMPORTANCE_LOW
                    );
                    channel.setDescription("Tracks your screen time in the background");
                    
                    NotificationManager notificationManager = getSystemService(NotificationManager.class);
                    if (notificationManager != null) {
                        notificationManager.createNotificationChannel(channel);
                    } else {
                        Log.e(TAG, "NotificationManager is null");
                    }
                } catch (Exception e) {
                    Log.e(TAG, "Error creating notification channel", e);
                }
            }
            
            // Create an intent to open the app when the notification is tapped
            Intent notificationIntent = new Intent(this, MainActivity.class);
            notificationIntent.setAction(Intent.ACTION_MAIN);
            notificationIntent.addCategory(Intent.CATEGORY_LAUNCHER);
            notificationIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            
            // Use FLAG_IMMUTABLE for Android 12+ compatibility
            int flags = PendingIntent.FLAG_UPDATE_CURRENT;
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                flags |= PendingIntent.FLAG_IMMUTABLE;
            }
            
            PendingIntent pendingIntent = null;
            try {
                pendingIntent = PendingIntent.getActivity(
                        this,
                        0,
                        notificationIntent,
                        flags
                );
            } catch (Exception e) {
                Log.e(TAG, "Error creating PendingIntent", e);
            }
            
            // Build the notification
            NotificationCompat.Builder builder = new NotificationCompat.Builder(this, CHANNEL_ID)
                    .setContentTitle("Screen Time Reminder")
                    .setContentText("Tracking your screen time")
                    .setSmallIcon(R.drawable.ic_launcher_foreground)
                    .setPriority(NotificationCompat.PRIORITY_LOW)
                    .setOngoing(true);
                    
            if (pendingIntent != null) {
                builder.setContentIntent(pendingIntent);
            }
            
            Notification notification = builder.build();
            
            // Start as a foreground service with the notification
            startForeground(NOTIFICATION_ID, notification);
            Log.d(TAG, "Started foreground service with notification");
        } catch (Exception e) {
            Log.e(TAG, "Error starting foreground service", e);
            // Try with a simpler notification as fallback
            try {
                Notification notification = new NotificationCompat.Builder(this, CHANNEL_ID)
                        .setContentTitle("Screen Time Reminder")
                        .setContentText("Running")
                        .setSmallIcon(R.drawable.ic_launcher_foreground)
                        .setPriority(NotificationCompat.PRIORITY_LOW)
                        .build();
                startForeground(NOTIFICATION_ID, notification);
                Log.d(TAG, "Started foreground service with fallback notification");
            } catch (Exception e2) {
                Log.e(TAG, "Error starting foreground service with fallback notification", e2);
            }
        }
    }
    
    /**
     * Acquire a wake lock to keep the CPU running while the service is active
     */
    private void acquireWakeLock() {
        try {
            if (wakeLock == null) {
                PowerManager powerManager = (PowerManager) getSystemService(Context.POWER_SERVICE);
                if (powerManager != null) {
                    wakeLock = powerManager.newWakeLock(
                            PowerManager.PARTIAL_WAKE_LOCK,
                            "ScreenTimeReminder:BackgroundServiceWakeLock"
                    );
                    wakeLock.acquire();
                    Log.d(TAG, "Wake lock acquired");
                } else {
                    Log.e(TAG, "PowerManager is null");
                }
            }
        } catch (Exception e) {
            Log.e(TAG, "Error acquiring wake lock", e);
        }
    }
    
    /**
     * Check if the service is currently running
     */
    public boolean isRunning() {
        return isRunning;
    }
} 