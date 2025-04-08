package com.screentimereminder.app;

import android.app.PendingIntent;
import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProvider;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.util.Log;
import android.widget.RemoteViews;
import java.util.Map;

public class ScreenTimeWidgetProvider extends AppWidgetProvider {
    private static final String TAG = "ScreenTimeWidget";
    private static final String PREFS_NAME = "ScreenTimeReminder";
    private static final String KEY_SCREEN_TIME_LIMIT = "screenTimeLimit";
    private static final String KEY_TOTAL_SCREEN_TIME = "totalScreenTime";

    @Override
    public void onReceive(Context context, Intent intent) {
        super.onReceive(context, intent);
        Log.d(TAG, "Received action: " + intent.getAction());

        if (intent.getAction() != null) {
            if (intent.getAction().equals("com.screentimereminder.app.REFRESH_WIDGET")) {
                // Get AppWidget ids
                AppWidgetManager appWidgetManager = AppWidgetManager.getInstance(context);
                ComponentName thisWidget = new ComponentName(context, ScreenTimeWidgetProvider.class);
                int[] appWidgetIds = appWidgetManager.getAppWidgetIds(thisWidget);
                
                Log.d(TAG, "Manually refreshing widgets: " + appWidgetIds.length);
                
                // Update all widgets
                onUpdate(context, appWidgetManager, appWidgetIds);
            } else if (intent.getAction().equals("android.appwidget.action.APPWIDGET_UPDATE")) {
                // Get AppWidget ids
                AppWidgetManager appWidgetManager = AppWidgetManager.getInstance(context);
                ComponentName thisWidget = new ComponentName(context, ScreenTimeWidgetProvider.class);
                int[] appWidgetIds = appWidgetManager.getAppWidgetIds(thisWidget);
                
                Log.d(TAG, "System requested widget update: " + appWidgetIds.length);
                
                // Update all widgets
                onUpdate(context, appWidgetManager, appWidgetIds);
            }
        }
    }

    @Override
    public void onUpdate(Context context, AppWidgetManager appWidgetManager, int[] appWidgetIds) {
        // Update each widget
        for (int appWidgetId : appWidgetIds) {
            updateAppWidget(context, appWidgetManager, appWidgetId);
        }
    }

    private void updateAppWidget(Context context, AppWidgetManager appWidgetManager, int appWidgetId) {
        RemoteViews views = new RemoteViews(context.getPackageName(), R.layout.widget_layout);

        try {
            // Get data from shared preferences
            SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
            
            // Log all relevant preferences
            Map<String, ?> allPrefs = prefs.getAll();
            Log.d(TAG, "All SharedPreferences values from " + PREFS_NAME + ":");
            for (Map.Entry<String, ?> entry : allPrefs.entrySet()) {
                Log.d(TAG, entry.getKey() + ": " + entry.getValue() + " (" + (entry.getValue() != null ? entry.getValue().getClass().getSimpleName() : "null") + ")");
            }
            
            float totalScreenTime = prefs.getFloat(KEY_TOTAL_SCREEN_TIME, 0f);
            long screenTimeLimit = prefs.getLong(KEY_SCREEN_TIME_LIMIT, 1L); // Default to 1 minute

            // Format the time values
            String usedTimeText = String.format("Used: %s", formatTime(Math.round(totalScreenTime)));
            String limitText = String.format("%s", formatTime((int)screenTimeLimit));

            // Calculate progress percentage (capped at 100%)
            int progressPercent = Math.min((int)((totalScreenTime * 100.0f) / screenTimeLimit), 100);

            // Update the TextViews
            views.setTextViewText(R.id.screen_time_text, usedTimeText);
            views.setTextViewText(R.id.limit_text, limitText);
            
            // Update progress bar
            views.setProgressBar(R.id.progress_bar, 100, progressPercent, false);

            // Set up refresh button with custom action
            Intent refreshIntent = new Intent(context, ScreenTimeWidgetProvider.class);
            refreshIntent.setAction("com.screentimereminder.app.REFRESH_WIDGET");
            PendingIntent refreshPendingIntent = PendingIntent.getBroadcast(
                context, 
                appWidgetId, 
                refreshIntent, 
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
            );
            views.setOnClickPendingIntent(R.id.refresh_button, refreshPendingIntent);

            Log.d(TAG, String.format("Updating widget %d - Time: %s, Limit: %s, Progress: %d%%, Raw values: %.2f/%d", 
                appWidgetId, usedTimeText, limitText, progressPercent, totalScreenTime, screenTimeLimit));

            // Update the widget
            appWidgetManager.updateAppWidget(appWidgetId, views);
        } catch (Exception e) {
            Log.e(TAG, "Error updating widget", e);
        }
    }

    private String formatTime(int minutes) {
        if (minutes < 1) {
            return "0m";
        }
        int hours = minutes / 60;
        int mins = minutes % 60;
        if (hours > 0) {
            return mins > 0 ? String.format("%dh %dm", hours, mins) : String.format("%dh", hours);
        } else {
            return String.format("%dm", mins);
        }
    }
} 