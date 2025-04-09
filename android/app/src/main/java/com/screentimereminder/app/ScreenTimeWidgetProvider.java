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
    private static final long DEBOUNCE_DELAY = 500; // 500ms debounce
    private static long lastUpdateTime = 0;
    private static float lastScreenTime = 0f;
    private static long lastScreenTimeLimit = 0;

    @Override
    public void onReceive(Context context, Intent intent) {
        super.onReceive(context, intent);
        String action = intent.getAction();
        Log.d(TAG, "Received action: " + action);

        if (action != null) {
            if (action.equals(ACTION_REFRESH_WIDGET) || 
                action.equals("android.appwidget.action.APPWIDGET_UPDATE")) {
                
                // Check if enough time has passed since last update
                long now = System.currentTimeMillis();
                if (now - lastUpdateTime < DEBOUNCE_DELAY) {
                    Log.d(TAG, "Skipping update due to debounce");
                    return;
                }
                lastUpdateTime = now;

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
                        
                        // Store the latest values
                        if (data.has("totalScreenTime")) {
                            lastScreenTime = (float) data.getDouble("totalScreenTime");
                        }
                        if (data.has("screenTimeLimit")) {
                            lastScreenTimeLimit = data.getLong("screenTimeLimit");
                        }

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
            
            // Get the most recent screen time value
            float totalScreenTime;
            long screenTimeLimit;
            
            // First check if we have a value from Capacitor/web interface
            if (prefs.contains("lastCapacitorUpdate")) {
                long lastCapacitorUpdate = prefs.getLong("lastCapacitorUpdate", 0);
                long lastNativeUpdate = prefs.getLong(AppUsageTracker.KEY_LAST_UPDATE, 0);
                
                // Use most recent value between Capacitor, stored value, and last known value
                if (lastCapacitorUpdate > lastNativeUpdate && lastCapacitorUpdate > lastUpdateTime) {
                    totalScreenTime = prefs.getFloat("capacitorScreenTime", lastScreenTime);
                    Log.d(TAG, "Using Capacitor screen time value: " + totalScreenTime + " minutes");
                } else if (lastUpdateTime > lastCapacitorUpdate && lastUpdateTime > lastNativeUpdate) {
                    totalScreenTime = lastScreenTime;
                    Log.d(TAG, "Using last known screen time value: " + totalScreenTime + " minutes");
                } else {
                    totalScreenTime = prefs.getFloat(AppUsageTracker.KEY_TOTAL_SCREEN_TIME, lastScreenTime);
                    Log.d(TAG, "Using native screen time value: " + totalScreenTime + " minutes");
                }
            } else {
                totalScreenTime = Math.max(lastScreenTime, 
                    prefs.getFloat(AppUsageTracker.KEY_TOTAL_SCREEN_TIME, 0f));
                Log.d(TAG, "Using stored screen time value: " + totalScreenTime + " minutes");
            }
            
            // Get the screen time limit
            boolean userHasSetLimit = prefs.getBoolean("userHasSetLimit", false);
            
            if (userHasSetLimit) {
                screenTimeLimit = prefs.getLong(AppUsageTracker.KEY_SCREEN_TIME_LIMIT, AppUsageTracker.DEFAULT_SCREEN_TIME_LIMIT);
                Log.d(TAG, "Using user-set screen time limit: " + screenTimeLimit);
            } else {
                screenTimeLimit = AppUsageTracker.DEFAULT_SCREEN_TIME_LIMIT;
                Log.d(TAG, "Using default screen time limit: " + screenTimeLimit);
            }

            // Store the values for next comparison
            lastScreenTime = totalScreenTime;
            lastScreenTimeLimit = screenTimeLimit;

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