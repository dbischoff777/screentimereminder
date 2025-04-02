package com.screentimereminder.app;

import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.util.Log;

import androidx.core.app.NotificationCompat;

import com.getcapacitor.BridgeActivity;
import com.screentimereminder.app.R;

public class NotificationService {
    private static final String TAG = "NotificationService";
    private static final String CHANNEL_ID = "screen-time-alerts";
    private static final String CHANNEL_NAME = "Screen Time Alerts";
    private static final String CHANNEL_DESCRIPTION = "Notifications for screen time limits";
    
    private final Context context;
    private final NotificationManager notificationManager;
    
    public NotificationService(Context context) {
        this.context = context;
        this.notificationManager = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
        createNotificationChannel();
    }
    
    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                CHANNEL_ID,
                CHANNEL_NAME,
                NotificationManager.IMPORTANCE_HIGH
            );
            channel.setDescription(CHANNEL_DESCRIPTION);
            channel.enableVibration(true);
            channel.setVibrationPattern(new long[]{100, 200, 300, 400, 500});
            channel.enableLights(true);
            channel.setLightColor(android.graphics.Color.BLUE);
            
            notificationManager.createNotificationChannel(channel);
            Log.d(TAG, "Notification channel created successfully");
        }
    }
    
    public void showApproachingLimitNotification(int totalTime, int limit, int remainingMinutes) {
        Intent intent = new Intent(context, BridgeActivity.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        
        PendingIntent pendingIntent = PendingIntent.getActivity(
            context,
            0,
            intent,
            PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT
        );
        
        NotificationCompat.Builder builder = new NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_screen_time)
            .setLargeIcon(android.graphics.BitmapFactory.decodeResource(context.getResources(), R.mipmap.ic_launcher))
            .setContentTitle("Screen Time Limit Approaching")
            .setContentText(String.format("Total Screen Time: %d minutes\nDaily Limit: %d minutes\n%d minutes remaining", 
                totalTime, limit, remainingMinutes))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(false)
            .setOngoing(true)
            .setVibrate(new long[]{100, 200, 300, 400, 500})
            .setLights(android.graphics.Color.BLUE, 1000, 1000)
            .setContentIntent(pendingIntent);
        
        notificationManager.notify(1, builder.build());
        Log.d(TAG, "Approaching limit notification sent");
    }
    
    public void showLimitReachedNotification(int totalTime, int limit) {
        Intent intent = new Intent(context, BridgeActivity.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        
        PendingIntent pendingIntent = PendingIntent.getActivity(
            context,
            0,
            intent,
            PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT
        );
        
        NotificationCompat.Builder builder = new NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_screen_time)
            .setLargeIcon(android.graphics.BitmapFactory.decodeResource(context.getResources(), R.mipmap.ic_launcher))
            .setContentTitle("Screen Time Limit Reached")
            .setContentText(String.format("Total Screen Time: %d minutes\nDaily Limit: %d minutes\nYou have reached your daily limit!", 
                totalTime, limit))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(false)
            .setOngoing(true)
            .setVibrate(new long[]{100, 200, 300, 400, 500})
            .setLights(android.graphics.Color.RED, 1000, 1000)
            .setContentIntent(pendingIntent);
        
        notificationManager.notify(2, builder.build());
        Log.d(TAG, "Limit reached notification sent");
    }
    
    public void cancelAllNotifications() {
        notificationManager.cancelAll();
        Log.d(TAG, "All notifications cancelled");
    }
} 