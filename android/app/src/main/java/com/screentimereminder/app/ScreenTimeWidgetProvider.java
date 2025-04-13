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
    private static final String PREFS_NAME = "ScreenTimeReminder";
    private static final String ACTION_UPDATE_WIDGET = "com.screentimereminder.app.UPDATE_WIDGET";
    private static final long DEBOUNCE_TIME = 1000; // 1 second debounce
    private static long lastUpdateTime = 0;
    private static final long DEFAULT_SCREEN_TIME_LIMIT = 120; // Default limit in minutes
    private static final String KEY_SCREEN_TIME_LIMIT = "screenTimeLimit";

    @Override
    public void onUpdate(Context context, AppWidgetManager appWidgetManager, int[] appWidgetIds) {
        try {
            // Get fresh data using the same method as Statistics.tsx
            float currentScreenTime = getTodayScreenTime(context);
            
            // Get the limit from AppUsageTracker
            int screenTimeLimit = AppUsageTracker.getScreenTimeLimitStatic(context);
            Log.d(TAG, "Widget onUpdate called with values - Time: " + currentScreenTime + ", Limit: " + screenTimeLimit);

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

    private float getTodayScreenTime(Context context) {
        try {
            // Use AppUsageTracker's calculateScreenTime method for consistency
            return AppUsageTracker.calculateScreenTime(context);
        } catch (Exception e) {
            Log.e(TAG, "Error getting today's screen time", e);
            return 0;
        }
    }

    private boolean isSystemApp(Context context, String packageName) {
        try {
            PackageManager packageManager = context.getPackageManager();
            ApplicationInfo appInfo = packageManager.getApplicationInfo(packageName, 0);
            return (appInfo.flags & ApplicationInfo.FLAG_SYSTEM) != 0;
        } catch (PackageManager.NameNotFoundException e) {
            return false;
        }
    }

    private void startBackgroundService(Context context) {
        try {
            Intent serviceIntent = new Intent(context, BackgroundService.class);
            serviceIntent.setAction("START_TRACKING");
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(serviceIntent);
            } else {
                context.startService(serviceIntent);
            }
        } catch (Exception e) {
            Log.e(TAG, "Error starting background service", e);
        }
    }

    private void updateWidget(Context context, AppWidgetManager appWidgetManager, int appWidgetId, float screenTime, int screenTimeLimit) {
        try {
            // Check debounce
            long currentTime = System.currentTimeMillis();
            /* if (currentTime - lastUpdateTime < DEBOUNCE_TIME) {
                Log.d(TAG, "Skipping update due to debounce");
                return;
            } */
            lastUpdateTime = currentTime;

            Log.d(TAG, "Creating RemoteViews with layout: widget_layout");
            RemoteViews views = new RemoteViews(context.getPackageName(), R.layout.widget_layout);
            
            // Format screen time
            String timeText = formatTime(screenTime);
            String limitText = formatTime(screenTimeLimit);
            
            // Calculate progress percentage (cap at 100%)
            int progress = Math.min(100, Math.round((screenTime / screenTimeLimit) * 100));
            
            Log.d(TAG, "Updating views with values - Time: " + timeText + ", Limit: " + limitText + ", Progress: " + progress);
            
            // Update views with detailed logging
            Log.d(TAG, "Setting time_text to: " + "Screen Time: " + timeText);
            views.setTextViewText(R.id.time_text, "Screen Time: " + timeText);
            
            Log.d(TAG, "Setting progress_bar to: " + progress);
            views.setProgressBar(R.id.progress_bar, 100, progress, false);
            
            // Set up refresh button click
            Intent refreshIntent = new Intent(context, ScreenTimeWidgetProvider.class);
            refreshIntent.setAction(ACTION_UPDATE_WIDGET);
            refreshIntent.putExtra(AppWidgetManager.EXTRA_APPWIDGET_IDS, new int[]{appWidgetId});
            PendingIntent refreshPendingIntent = PendingIntent.getBroadcast(
                context, appWidgetId, refreshIntent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
            );
            
            // Update widget
            appWidgetManager.updateAppWidget(appWidgetId, views);
            
            Log.d(TAG, String.format("Widget update completed - Time: %.2f, Limit: %d", screenTime, screenTimeLimit));
        } catch (Exception e) {
            Log.e(TAG, "Error updating widget", e);
            e.printStackTrace();
        }
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

    @Override
    public void onReceive(Context context, Intent intent) {
        try {
            String action = intent.getAction();
            Log.d(TAG, "Received action: " + action);
            
            if (action == null) {
                super.onReceive(context, intent);
                return;
            }

            // Get widget manager and IDs
            AppWidgetManager appWidgetManager = AppWidgetManager.getInstance(context);
            ComponentName componentName = new ComponentName(context, ScreenTimeWidgetProvider.class);
            int[] appWidgetIds = appWidgetManager.getAppWidgetIds(componentName);

            // Handle different actions
            switch (action) {
                case ACTION_UPDATE_WIDGET:
                    // Manual refresh - use onUpdate to recalculate everything
                    onUpdate(context, appWidgetManager, appWidgetIds);
                    break;
                    
                case AppWidgetManager.ACTION_APPWIDGET_UPDATE:
                    // First check if we have screenTimeLimit in the intent
                    if (intent.hasExtra("totalScreenTime") && intent.hasExtra("screenTimeLimit")) {
                        float totalScreenTime = intent.getFloatExtra("totalScreenTime", 0);
                        int screenTimeLimit = intent.getIntExtra("screenTimeLimit", (int)AppUsageTracker.DEFAULT_SCREEN_TIME_LIMIT);
                        
                        Log.d(TAG, "Updating widget with intent data - Time: " + totalScreenTime + ", Limit: " + screenTimeLimit);
                        
                        // Update all widgets with the provided data
                        for (int appWidgetId : appWidgetIds) {
                            updateWidget(context, appWidgetManager, appWidgetId, totalScreenTime, screenTimeLimit);
                        }
                    } else {
                        // No specific data in intent, fall back to onUpdate
                        Log.d(TAG, "No specific data in intent, falling back to onUpdate");
                        onUpdate(context, appWidgetManager, appWidgetIds);
                    }
                    break;

                case "com.screentimereminder.app.APP_USAGE_UPDATE":
                    // Handle usage update broadcast
                    if (intent.hasExtra("usageData")) {
                        try {
                            String usageData = intent.getStringExtra("usageData");
                            JSONObject data = new JSONObject(usageData);
                            
                            // Check if this is a settings update message
                            String messageAction = data.optString("action", "");
                            if ("UPDATE_SETTINGS".equals(messageAction)) {
                                String chainId = data.optString("chainId", "unknown");
                                long screenTimeLimit = data.optLong("screenTimeLimit", AppUsageTracker.DEFAULT_SCREEN_TIME_LIMIT);
                                long notificationFrequency = data.optLong("notificationFrequency", AppUsageTracker.DEFAULT_NOTIFICATION_FREQUENCY);
                                
                                Log.d(TAG, String.format("[%s] Received settings update broadcast in Widget:", chainId));
                                Log.d(TAG, String.format("[%s] - Screen time limit: %d minutes", chainId, screenTimeLimit));
                                
                                // Update local settings to match the broadcast
                                SharedPreferences prefs = context.getSharedPreferences(AppUsageTracker.PREFS_NAME, Context.MODE_PRIVATE);
                                SharedPreferences.Editor editor = prefs.edit();
                                editor.putLong(AppUsageTracker.KEY_SCREEN_TIME_LIMIT, screenTimeLimit);
                                editor.putLong(AppUsageTracker.KEY_NOTIFICATION_FREQUENCY, notificationFrequency);
                                editor.putString("lastSettingsChainId", chainId);
                                editor.putBoolean("userHasSetLimit", true);
                                
                                // Apply changes synchronously to ensure they are saved immediately
                                boolean success = editor.commit();
                                
                                Log.d(TAG, String.format("[%s] Settings applied in Widget (success: %b)", chainId, success));
                                
                                // Update widget with new settings
                                float currentScreenTime = getTodayScreenTime(context);
                                for (int appWidgetId : appWidgetIds) {
                                    updateWidget(context, appWidgetManager, appWidgetId, currentScreenTime, (int)screenTimeLimit);
                                }
                            } else {
                                float totalScreenTime = (float) data.getDouble("totalScreenTime");
                                int screenTimeLimit = data.getInt("screenTimeLimit");
                                
                                Log.d(TAG, "Updating widget with new data - Time: " + totalScreenTime + ", Limit: " + screenTimeLimit);
                                
                                // Update all widgets with the new data
                                for (int appWidgetId : appWidgetIds) {
                                    updateWidget(context, appWidgetManager, appWidgetId, totalScreenTime, screenTimeLimit);
                                }
                            }
                        } catch (Exception e) {
                            Log.e(TAG, "Error parsing usage data", e);
                            // Fall back to native calculation if parsing fails
                            onUpdate(context, appWidgetManager, appWidgetIds);
                        }
                    } else {
                        // If no usage data, use native calculation
                        onUpdate(context, appWidgetManager, appWidgetIds);
                    }
                    break;

                default:
                    super.onReceive(context, intent);
                    break;
            }
        } catch (Exception e) {
            Log.e(TAG, "Error in onReceive", e);
            super.onReceive(context, intent);
        }
    }

    @Override
    public void onEnabled(Context context) {
        super.onEnabled(context);
        try {
            // Start background service when widget is first added
            startBackgroundService(context);
        } catch (Exception e) {
            Log.e(TAG, "Error in onEnabled", e);
        }
    }

    @Override
    public void onDisabled(Context context) {
        super.onDisabled(context);
        // No need to stop the service as other widgets or the app might still need it
    }
}