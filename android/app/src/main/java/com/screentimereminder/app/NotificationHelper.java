package com.screentimereminder.app;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.util.Log;

import androidx.core.app.NotificationCompat;

public class NotificationHelper {
    private static final String TAG = "NotificationHelper";
    private static final String CHANNEL_ID = "screen_time_alerts";
    private static final String CHANNEL_NAME = "Screen Time Alerts";
    private static final String CHANNEL_DESCRIPTION = "Notifications for screen time limits and reminders";
    
    private final Context context;
    private final NotificationManager notificationManager;
    
    public NotificationHelper(Context context) {
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
            channel.enableLights(true);
            channel.setLightColor(android.graphics.Color.BLUE);
            
            if (notificationManager != null) {
                notificationManager.createNotificationChannel(channel);
            }
        }
    }
    
    public void showApproachingLimitNotification(long currentTime, long limit) {
        Intent intent = new Intent(context, MainActivity.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        
        PendingIntent pendingIntent = PendingIntent.getActivity(
            context,
            0,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
        );
        
        NotificationCompat.Builder builder = new NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU ? 
                R.drawable.ic_stat_screen_time_monochrome : 
                R.drawable.ic_stat_screen_time)
            .setContentTitle("Approaching Screen Time Limit")
            .setContentText("You're getting close to your daily screen time limit")
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setVibrate(new long[]{0, 500, 200, 500})
            .setLights(android.graphics.Color.BLUE, 3000, 3000)
            .setContentIntent(pendingIntent);
            
        if (notificationManager != null) {
            notificationManager.notify(1001, builder.build());
        }
    }
    
    public void showLimitReachedNotification() {
        Intent intent = new Intent(context, MainActivity.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        
        PendingIntent pendingIntent = PendingIntent.getActivity(
            context,
            0,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
        );
        
        NotificationCompat.Builder builder = new NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU ? 
                R.drawable.ic_stat_screen_time_monochrome : 
                R.drawable.ic_stat_screen_time)
            .setContentTitle("Screen Time Limit Reached")
            .setContentText("You've reached your daily screen time limit")
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setVibrate(new long[]{0, 1000, 500, 1000})
            .setLights(android.graphics.Color.RED, 3000, 3000)
            .setContentIntent(pendingIntent);
            
        if (notificationManager != null) {
            notificationManager.notify(1002, builder.build());
        }
    }
} 