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
    private static final String PREFS_NAME = "ScreenTimeReminder";
    private static final String KEY_WIDGET_DATA = "widget_data";

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
                
                Log.d(TAG, "Updating widgets: " + appWidgetIds.length);
                
                // Update all widgets
                onUpdate(context, appWidgetManager, appWidgetIds);
            } else if (intent.getAction().equals("com.screentimereminder.app.APP_USAGE_UPDATE")) {
                try {
                    String usageData = intent.getStringExtra("usageData");
                    if (usageData != null) {
                        // Store the data
                        SharedPreferences.Editor editor = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit();
                        editor.putString(KEY_WIDGET_DATA, usageData);
                        editor.apply();
                        
                        // Update widgets
                        AppWidgetManager appWidgetManager = AppWidgetManager.getInstance(context);
                        ComponentName thisWidget = new ComponentName(context, ScreenTimeWidgetProvider.class);
                        int[] appWidgetIds = appWidgetManager.getAppWidgetIds(thisWidget);
                        onUpdate(context, appWidgetManager, appWidgetIds);
                        
                        Log.d(TAG, "Updated widget data from broadcast: " + usageData);
                    }
                } catch (Exception e) {
                    Log.e(TAG, "Error processing usage data update", e);
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
            // Try to get data from SharedPreferences first
            SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
            String widgetData = prefs.getString(KEY_WIDGET_DATA, null);
            
            int totalScreenTime;
            int screenTimeLimit;
            
            if (widgetData != null) {
                // Parse the stored JSON data
                JSONObject data = new JSONObject(widgetData);
                totalScreenTime = data.getInt("totalScreenTime");
                screenTimeLimit = data.getInt("screenTimeLimit");
                Log.d(TAG, "Using widget data from preferences: " + widgetData);
            } else {
                // Fallback to static methods
                totalScreenTime = AppUsageTracker.getTotalScreenTimeStatic(context);
                screenTimeLimit = AppUsageTracker.getScreenTimeLimitStatic(context);
                Log.d(TAG, "Using fallback data from static methods");
            }

            // Format the time values using the formatTime method
            String usedTimeText = String.format("Used: %s", formatTime(totalScreenTime));
            String limitText = String.format("Limit: %s", formatTime(screenTimeLimit));

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

            Log.d(TAG, String.format("Updating widget %d - Time: %s, Limit: %s, Progress: %d%%", 
                appWidgetId, usedTimeText, limitText, progressPercent));

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