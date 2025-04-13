package com.screentimereminder.app;

import android.app.PendingIntent;
import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProvider;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.os.Build;
import android.app.usage.UsageStats;
import android.app.usage.UsageStatsManager;
import android.util.Log;
import android.widget.RemoteViews;

import org.json.JSONObject;

import java.util.Calendar;
import java.util.List;
import java.util.Locale;

public class ScreenTimeWidgetProvider extends AppWidgetProvider {
    private static final String TAG = "ScreenTimeWidget";
    private static long lastUpdateTime = 0;

    @Override
    public void onUpdate(Context context, AppWidgetManager appWidgetManager, int[] appWidgetIds) {
        try {
            // Get fresh data using the same method as Statistics.tsx
            float currentScreenTime = getTodayScreenTime(context);
            
            // Get settings from SettingsManager
            SettingsManager settingsManager = SettingsManager.getInstance(context);
            int screenTimeLimit = (int)settingsManager.getScreenTimeLimit();
            boolean userHasSetLimit = settingsManager.hasUserSetLimit();
            
            Log.d(TAG, "Widget onUpdate called with values:");
            Log.d(TAG, "- Current screen time: " + currentScreenTime + " minutes");
            Log.d(TAG, "- Screen time limit: " + screenTimeLimit + " minutes");
            Log.d(TAG, "- User has set limit: " + userHasSetLimit);

            // Update all widgets
            for (int appWidgetId : appWidgetIds) {
                updateWidget(context, appWidgetManager, appWidgetId, currentScreenTime, screenTimeLimit);
            }

            // Start background service to ensure updates continue
            startBackgroundService(context);
        } catch (Exception e) {
            Log.e(TAG, "Error in onUpdate", e);
        }
    }

    private void updateWidget(Context context, AppWidgetManager appWidgetManager, int appWidgetId, float screenTime, int screenTimeLimit) {
        try {
            // Check debounce
            long currentTime = System.currentTimeMillis();
            if (currentTime - lastUpdateTime < SettingsConstants.DEBOUNCE_TIME) {
                Log.d(TAG, "Skipping update due to debounce");
                return;
            }
            lastUpdateTime = currentTime;

            Log.d(TAG, "Creating RemoteViews with layout: widget_layout");
            RemoteViews views = new RemoteViews(context.getPackageName(), R.layout.widget_layout);
            
            // Format screen time
            String timeText = formatTime(screenTime);
            String limitText = formatTime((float)screenTimeLimit);
            
            // Calculate progress percentage (cap at 100%)
            int progress = Math.min(100, Math.round((screenTime / screenTimeLimit) * 100));
            
            // Update views with detailed logging
            views.setTextViewText(R.id.time_text, "Screen Time: " + timeText);
            views.setProgressBar(R.id.progress_bar, 100, progress, false);
            
            // Set up refresh button click
            Intent refreshIntent = new Intent(context, ScreenTimeWidgetProvider.class);
            refreshIntent.setAction(SettingsConstants.ACTION_REFRESH_WIDGET);
            refreshIntent.putExtra(AppWidgetManager.EXTRA_APPWIDGET_IDS, new int[]{appWidgetId});
            PendingIntent refreshPendingIntent = PendingIntent.getBroadcast(
                context, appWidgetId, refreshIntent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
            );
            
            // Set up click to open app
            PackageManager packageManager = context.getPackageManager();
            Intent launchIntent = packageManager.getLaunchIntentForPackage(context.getPackageName());
            if (launchIntent != null) {
                PendingIntent pendingIntent = PendingIntent.getActivity(
                    context, 0, launchIntent,
                    PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
                );
                views.setOnClickPendingIntent(R.id.widget_layout, pendingIntent);
            }
            
            // Update widget
            appWidgetManager.updateAppWidget(appWidgetId, views);
            
            Log.d(TAG, String.format("Widget update completed - Time: %.2f, Limit: %d", screenTime, screenTimeLimit));
        } catch (Exception e) {
            Log.e(TAG, "Error updating widget", e);
        }
    }

    @Override
    public void onReceive(Context context, Intent intent) {
        try {
            String action = intent.getAction();
            if (action == null) {
                super.onReceive(context, intent);
                return;
            }

            // Get widget manager and IDs
            AppWidgetManager appWidgetManager = AppWidgetManager.getInstance(context);
            ComponentName componentName = new ComponentName(context, ScreenTimeWidgetProvider.class);
            int[] appWidgetIds = appWidgetManager.getAppWidgetIds(componentName);

            switch (action) {
                case SettingsConstants.ACTION_REFRESH_WIDGET:
                    onUpdate(context, appWidgetManager, appWidgetIds);
                    break;
                    
                case AppWidgetManager.ACTION_APPWIDGET_UPDATE:
                    if (intent.hasExtra("totalScreenTime") && intent.hasExtra("screenTimeLimit")) {
                        // Get totalScreenTime value, handling both integer and float cases
                        float totalScreenTime;
                        if (intent.hasExtra("totalScreenTime")) {
                            Object value = intent.getExtras().get("totalScreenTime");
                            if (value instanceof Integer) {
                                totalScreenTime = ((Integer) value).floatValue();
                            } else if (value instanceof Float) {
                                totalScreenTime = (Float) value;
                            } else if (value instanceof Double) {
                                totalScreenTime = ((Double) value).floatValue();
                            } else {
                                totalScreenTime = 0f;
                            }
                        } else {
                            totalScreenTime = 0f;
                        }
                        
                        // Get screenTimeLimit value, handling both integer and long cases
                        long screenTimeLimit;
                        if (intent.hasExtra("screenTimeLimit")) {
                            Object value = intent.getExtras().get("screenTimeLimit");
                            if (value instanceof Integer) {
                                screenTimeLimit = ((Integer) value).longValue();
                            } else if (value instanceof Long) {
                                screenTimeLimit = (Long) value;
                            } else {
                                screenTimeLimit = SettingsConstants.DEFAULT_SCREEN_TIME_LIMIT;
                            }
                        } else {
                            screenTimeLimit = SettingsConstants.DEFAULT_SCREEN_TIME_LIMIT;
                        }
                        
                        Log.d(TAG, String.format("Received update - totalScreenTime: %f (%s), screenTimeLimit: %d (%s)", 
                            totalScreenTime, 
                            intent.getExtras().get("totalScreenTime").getClass().getSimpleName(),
                            screenTimeLimit,
                            intent.getExtras().get("screenTimeLimit").getClass().getSimpleName()));
                        
                        // Skip debounce for settings changes or background updates
                        boolean isBackgroundUpdate = intent.getBooleanExtra("isBackgroundUpdate", false);
                        boolean isSettingsChange = intent.getBooleanExtra("isSettingsChange", false);
                        long currentTime = System.currentTimeMillis();
                        
                        if (!isBackgroundUpdate && !isSettingsChange && currentTime - lastUpdateTime < SettingsConstants.DEBOUNCE_TIME) {
                            Log.d(TAG, "Skipping update due to debounce");
                            return;
                        }
                        lastUpdateTime = currentTime;
                        
                        for (int appWidgetId : appWidgetIds) {
                            updateWidget(context, appWidgetManager, appWidgetId, totalScreenTime, (int)screenTimeLimit);
                        }
                    } else {
                        onUpdate(context, appWidgetManager, appWidgetIds);
                    }
                    break;

                case SettingsConstants.ACTION_USAGE_UPDATE:
                    handleUsageUpdate(context, intent, appWidgetManager, appWidgetIds);
                    break;

                default:
                    super.onReceive(context, intent);
                    break;
            }
        } catch (Exception e) {
            Log.e(TAG, "Error in onReceive", e);
            // If we get an error, try to update using the native tracker
            try {
                float currentScreenTime = getTodayScreenTime(context);
                SettingsManager settingsManager = SettingsManager.getInstance(context);
                long screenTimeLimit = settingsManager.getScreenTimeLimit();
                
                // Mark as settings change to bypass debounce
                Intent updateIntent = new Intent(context, ScreenTimeWidgetProvider.class);
                updateIntent.setAction(AppWidgetManager.ACTION_APPWIDGET_UPDATE);
                updateIntent.putExtra("totalScreenTime", currentScreenTime);
                updateIntent.putExtra("screenTimeLimit", screenTimeLimit);
                updateIntent.putExtra("isSettingsChange", true);
                context.sendBroadcast(updateIntent);
            } catch (Exception fallbackError) {
                Log.e(TAG, "Error in fallback update", fallbackError);
                super.onReceive(context, intent);
            }
        }
    }

    private void handleUsageUpdate(Context context, Intent intent, AppWidgetManager appWidgetManager, int[] appWidgetIds) {
        if (!intent.hasExtra("usageData")) {
            onUpdate(context, appWidgetManager, appWidgetIds);
            return;
        }

        try {
            String usageData = intent.getStringExtra("usageData");
            JSONObject data = new JSONObject(usageData);
            String messageAction = data.optString("action", "");
            
            if ("UPDATE_SETTINGS".equals(messageAction)) {
                // Get settings from SettingsManager
                SettingsManager settingsManager = SettingsManager.getInstance(context);
                float currentScreenTime = getTodayScreenTime(context);
                long screenTimeLimit = settingsManager.getScreenTimeLimit();
                
                // Mark as settings change to bypass debounce
                Intent updateIntent = new Intent(context, ScreenTimeWidgetProvider.class);
                updateIntent.setAction(AppWidgetManager.ACTION_APPWIDGET_UPDATE);
                updateIntent.putExtra("totalScreenTime", currentScreenTime);
                updateIntent.putExtra("screenTimeLimit", screenTimeLimit);
                updateIntent.putExtra("isSettingsChange", true);
                context.sendBroadcast(updateIntent);
            } else {
                // Handle totalScreenTime from JSON data
                float totalScreenTime;
                if (data.has("totalScreenTime")) {
                    Object value = data.get("totalScreenTime");
                    if (value instanceof Integer) {
                        totalScreenTime = ((Integer) value).floatValue();
                    } else if (value instanceof Double) {
                        totalScreenTime = ((Double) value).floatValue();
                    } else {
                        totalScreenTime = (float) data.getDouble("totalScreenTime");
                    }
                } else {
                    totalScreenTime = getTodayScreenTime(context);
                }
                
                SettingsManager settingsManager = SettingsManager.getInstance(context);
                long screenTimeLimit = settingsManager.getScreenTimeLimit();
                
                // Mark as background update to bypass debounce
                Intent updateIntent = new Intent(context, ScreenTimeWidgetProvider.class);
                updateIntent.setAction(AppWidgetManager.ACTION_APPWIDGET_UPDATE);
                updateIntent.putExtra("totalScreenTime", totalScreenTime);
                updateIntent.putExtra("screenTimeLimit", screenTimeLimit);
                updateIntent.putExtra("isBackgroundUpdate", true);
                context.sendBroadcast(updateIntent);
            }
        } catch (Exception e) {
            Log.e(TAG, "Error handling usage update", e);
            onUpdate(context, appWidgetManager, appWidgetIds);
        }
    }

    private void startBackgroundService(Context context) {
        try {
            Intent serviceIntent = new Intent(context, BackgroundService.class);
            serviceIntent.setAction(SettingsConstants.ACTION_RESTART_SERVICE);
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(serviceIntent);
            } else {
                context.startService(serviceIntent);
            }
        } catch (Exception e) {
            Log.e(TAG, "Error starting background service", e);
        }
    }

    private float getTodayScreenTime(Context context) {
        return AppUsageTracker.calculateScreenTime(context);
    }

    private String formatTime(float minutes) {
        int hours = (int) (minutes / 60);
        int mins = Math.round(minutes % 60);
        
        if (hours > 0) {
            return String.format(Locale.getDefault(), "%dh %dm", hours, mins);
        } else {
            return String.format(Locale.getDefault(), "%dm", mins);
        }
    }
}