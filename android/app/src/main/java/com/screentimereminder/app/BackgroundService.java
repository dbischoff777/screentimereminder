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
        super.onCreate();
        Log.d(TAG, "Background service created");
    }
    
    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        Log.d(TAG, "Background service started");
        
        if (!isRunning) {
            isRunning = true;
            startForeground();
            acquireWakeLock();
        }
        
        // If the service is killed, restart it
        return START_STICKY;
    }
    
    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return binder;
    }
    
    @Override
    public void onDestroy() {
        Log.d(TAG, "Background service destroyed");
        
        if (wakeLock != null && wakeLock.isHeld()) {
            wakeLock.release();
            wakeLock = null;
        }
        
        isRunning = false;
        super.onDestroy();
    }
    
    /**
     * Start the service as a foreground service with a notification
     */
    private void startForeground() {
        // Create notification channel for Android 8.0+
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                    CHANNEL_ID,
                    "Screen Time Reminder",
                    NotificationManager.IMPORTANCE_LOW
            );
            channel.setDescription("Tracks your screen time in the background");
            
            NotificationManager notificationManager = getSystemService(NotificationManager.class);
            notificationManager.createNotificationChannel(channel);
        }
        
        // Create an intent to open the app when the notification is tapped
        Intent notificationIntent = getPackageManager().getLaunchIntentForPackage(getPackageName());
        
        // Use FLAG_IMMUTABLE for Android 12+ compatibility
        int flags = PendingIntent.FLAG_UPDATE_CURRENT;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            flags |= PendingIntent.FLAG_IMMUTABLE;
        }
        
        PendingIntent pendingIntent = PendingIntent.getActivity(
                this,
                0,
                notificationIntent,
                flags
        );
        
        // Build the notification
        Notification notification = new NotificationCompat.Builder(this, CHANNEL_ID)
                .setContentTitle("Screen Time Reminder")
                .setContentText("Tracking your screen time")
                .setSmallIcon(R.drawable.ic_launcher_foreground)
                .setContentIntent(pendingIntent)
                .setPriority(NotificationCompat.PRIORITY_LOW)
                .setOngoing(true)
                .build();
        
        // Start as a foreground service with the notification
        startForeground(NOTIFICATION_ID, notification);
    }
    
    /**
     * Acquire a wake lock to keep the CPU running while the service is active
     */
    private void acquireWakeLock() {
        if (wakeLock == null) {
            PowerManager powerManager = (PowerManager) getSystemService(Context.POWER_SERVICE);
            wakeLock = powerManager.newWakeLock(
                    PowerManager.PARTIAL_WAKE_LOCK,
                    "ScreenTimeReminder:BackgroundServiceWakeLock"
            );
            wakeLock.acquire();
        }
    }
    
    /**
     * Check if the service is currently running
     */
    public boolean isRunning() {
        return isRunning;
    }
} 