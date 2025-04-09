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
import org.json.JSONObject;

public class ScreenTimeWidgetProvider extends AppWidgetProvider {
    private static final String TAG = "ScreenTimeWidget";
    private static final String ACTION_REFRESH_WIDGET = "com.screentimereminder.app.REFRESH_WIDGET";

    @Override
    public void onReceive(Context context, Intent intent) {
        super.onReceive(context, intent);
        String action = intent.getAction();
        Log.d(TAG, "Received action: " + action);

        if (action != null) {
            if (action.equals(ACTION_REFRESH_WIDGET) || 
                action.equals("android.appwidget.action.APPWIDGET_UPDATE")) {
                // Get AppWidget ids
                AppWidgetManager appWidgetManager = AppWidgetManager.getInstance(context);
                ComponentName thisWidget = new ComponentName(context, ScreenTimeWidgetProvider.class);
                int[] appWidgetIds = appWidgetManager.getAppWidgetIds(thisWidget);
                
                Log.d(TAG, "Refreshing widgets: " + appWidgetIds.length);
                
                // Update all widgets
                onUpdate(context, appWidgetManager, appWidgetIds);
            } else if (action.equals("com.screentimereminder.app.APP_USAGE_UPDATE")) {
                try {
                    // Check if this is a refresh trigger
                    String usageData = intent.getStringExtra("usageData");
                    if (usageData != null) {
                        JSONObject data = new JSONObject(usageData);
                        if (data.has("action") && data.getString("action").equals("REFRESH_WIDGET")) {
                            Log.d(TAG, "Received refresh trigger from WidgetService");
                            // Trigger widget refresh
                            AppWidgetManager appWidgetManager = AppWidgetManager.getInstance(context);
                            ComponentName thisWidget = new ComponentName(context, ScreenTimeWidgetProvider.class);
                            int[] appWidgetIds = appWidgetManager.getAppWidgetIds(thisWidget);
                            onUpdate(context, appWidgetManager, appWidgetIds);
                            return;
                        }
                    }
                } catch (Exception e) {
                    Log.e(TAG, "Error handling usage update", e);
                }
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
            // Get SharedPreferences
            SharedPreferences prefs = context.getSharedPreferences(AppUsageTracker.PREFS_NAME, Context.MODE_PRIVATE);
            
            // Check if we have a recent screen time value
            long lastUpdate = prefs.getLong(AppUsageTracker.KEY_LAST_UPDATE, 0);
            long now = System.currentTimeMillis();
            float totalScreenTime;
            
            // If we have a recent update (less than 30 seconds old), use the stored value
            if (now - lastUpdate < 30000) {
                totalScreenTime = prefs.getFloat(AppUsageTracker.KEY_TOTAL_SCREEN_TIME, 0f);
                Log.d(TAG, "Using cached screen time: " + totalScreenTime + " minutes (last update: " + (now - lastUpdate) + "ms ago)");
            } else {
                // Otherwise get fresh value from AppUsageTracker
                totalScreenTime = AppUsageTracker.getTotalScreenTimeStatic(context);
                Log.d(TAG, "Using fresh screen time value: " + totalScreenTime + " minutes");
            }
            
            // Get the screen time limit
            boolean userHasSetLimit = prefs.getBoolean("userHasSetLimit", false);
            long screenTimeLimit;
            
            if (userHasSetLimit) {
                screenTimeLimit = prefs.getLong(AppUsageTracker.KEY_SCREEN_TIME_LIMIT, AppUsageTracker.DEFAULT_SCREEN_TIME_LIMIT);
                Log.d(TAG, "Using user-set screen time limit: " + screenTimeLimit);
            } else {
                screenTimeLimit = AppUsageTracker.DEFAULT_SCREEN_TIME_LIMIT;
                Log.d(TAG, "Using default screen time limit: " + screenTimeLimit);
            }

            // Log the values for debugging
            Log.d(TAG, String.format("Retrieved values - Total: %.2f minutes, Limit: %d minutes", 
                totalScreenTime, screenTimeLimit));

            // Format the time values
            String usedTimeText = String.format("Used: %s", formatTime((int)Math.round(totalScreenTime)));
            String limitText = String.format("%s", formatTime((int)screenTimeLimit));

            // Calculate progress percentage (capped at 100%)
            int progressPercent = Math.min((int)((totalScreenTime * 100.0f) / screenTimeLimit), 100);

            // Update the TextViews
            views.setTextViewText(R.id.screen_time_text, usedTimeText);
            views.setTextViewText(R.id.limit_text, limitText);
            
            // Update progress bar
            views.setProgressBar(R.id.progress_bar, 100, progressPercent, false);

            // Set up refresh button
            Intent refreshIntent = new Intent(context, ScreenTimeWidgetProvider.class);
            refreshIntent.setAction(ACTION_REFRESH_WIDGET);
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