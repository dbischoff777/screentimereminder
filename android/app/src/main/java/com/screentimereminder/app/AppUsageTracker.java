package com.screentimereminder.app;

// This file contains the AppUsageTracker implementation for tracking app usage on Android devices
// It uses the UsageStatsManager API to retrieve app usage data and provides methods for querying and processing this data

import android.app.AppOpsManager;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.usage.UsageEvents;
import android.app.usage.UsageStats;
import android.app.usage.UsageStatsManager;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.os.PowerManager;
import android.provider.Settings;
import android.util.Log;

import com.getcapacitor.JSObject;
import com.getcapacitor.Plugin;
import com.getcapacitor.PluginCall;
import com.getcapacitor.PluginMethod;
import com.getcapacitor.annotation.CapacitorPlugin;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.Date;
import java.util.concurrent.ExecutorService;
import java.util.HashSet;
import java.util.Set;

import androidx.core.app.NotificationCompat;
import android.appwidget.AppWidgetManager;
import android.content.ComponentName;
import android.app.Service;

@CapacitorPlugin(name = "AppUsageTracker")
public class AppUsageTracker extends Plugin {
    private static final String TAG = "AppUsageTracker";
    public static final String PREFS_NAME = "ScreenTimeReminder";
    public static final String KEY_SCREEN_TIME_LIMIT = "screenTimeLimit";
    public static final String KEY_TOTAL_SCREEN_TIME = "totalScreenTime";
    public static final String KEY_LAST_UPDATE = "lastUpdateTime";
    public static final String KEY_LAST_LIMIT_NOTIFICATION = "lastLimitReachedNotification";
    public static final String KEY_LAST_APPROACHING_NOTIFICATION = "lastApproachingLimitNotification";
    public static final String KEY_NOTIFICATION_FREQUENCY = "notificationFrequency";
    
    private static AppUsageTracker instance;
    private Context context;
    public static final long DEFAULT_SCREEN_TIME_LIMIT = 120L; // 2 hours in minutes
    private long screenTimeLimit = DEFAULT_SCREEN_TIME_LIMIT;
    private int notificationFrequency = 5; // Default 5 minutes
    private UsageStatsManager usageStatsManager;
    private ScheduledExecutorService scheduler;
    private String currentForegroundApp = "";
    private Handler mainHandler;
    private NotificationService notificationService;
    private SharedPreferences prefs;
    private final ExecutorService backgroundExecutor = Executors.newSingleThreadExecutor();
    private boolean isTracking = false;
    private long lastUpdateTime = 0;
    
    public static AppUsageTracker getInstance(Context context) {
        if (instance == null) {
            instance = new AppUsageTracker();
            instance.context = context.getApplicationContext();
            instance.loadScreenTimeLimit();
        }
        return instance;
    }

    private void loadScreenTimeLimit() {
        try {
            if (context == null || prefs == null) {
                Log.e(TAG, "Context or SharedPreferences is null in loadScreenTimeLimit");
                return;
            }
            
            screenTimeLimit = prefs.getLong(KEY_SCREEN_TIME_LIMIT, DEFAULT_SCREEN_TIME_LIMIT);
            notificationFrequency = prefs.getInt(KEY_NOTIFICATION_FREQUENCY, 5);
            Log.d(TAG, "Loaded screen time limit: " + screenTimeLimit + " minutes");
        } catch (Exception e) {
            Log.e(TAG, "Error loading screen time limit", e);
        }
    }

    private void initialize(Context context) {
        usageStatsManager = (UsageStatsManager) context.getSystemService(Context.USAGE_STATS_SERVICE);
        mainHandler = new Handler(Looper.getMainLooper());
        prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        
        // Initialize notification service
        notificationService = new NotificationService(context);

        // Register broadcast receiver for background updates
        IntentFilter filter = new IntentFilter("com.screentimereminder.app.APP_USAGE_UPDATE");
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            context.registerReceiver(usageUpdateReceiver, filter, Context.RECEIVER_NOT_EXPORTED);
        } else {
            context.registerReceiver(usageUpdateReceiver, filter);
        }
    }
    
    @Override
    public void load() {
        super.load();
        Log.d(TAG, "Loading AppUsageTracker plugin");
        
        try {
            // Initialize context and handler
            this.context = getContext();
            this.mainHandler = new Handler(Looper.getMainLooper());
            
            if (context == null) {
                Log.e(TAG, "Context is null in load()");
                return;
            }
            
            // Initialize other components
            this.prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
            this.usageStatsManager = (UsageStatsManager) context.getSystemService(Context.USAGE_STATS_SERVICE);
            this.notificationService = new NotificationService(context);
            
            // Load settings
            loadScreenTimeLimit();
            
            // Register broadcast receiver
            IntentFilter filter = new IntentFilter();
            filter.addAction("com.screentimereminder.app.REFRESH_WIDGET");
            context.registerReceiver(refreshReceiver, filter);
            
            Log.d(TAG, "AppUsageTracker plugin loaded successfully");
        } catch (Exception e) {
            Log.e(TAG, "Error in load", e);
        }
    }

    private final BroadcastReceiver refreshReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (intent.getAction() != null && intent.getAction().equals("com.screentimereminder.app.REFRESH_DATA")) {
                try {
                    String refreshData = intent.getStringExtra("refreshData");
                    if (refreshData != null) {
                        JSONObject data = new JSONObject(refreshData);
                        if (data.has("action") && data.getString("action").equals("REFRESH_DATA")) {
                            Log.d(TAG, "Received refresh request from widget");
                            // Update app usage data
                            updateAppUsage();
                        }
                    }
                } catch (Exception e) {
                    Log.e(TAG, "Error handling refresh request", e);
                }
            }
        }
    };

    @Override
    protected void handleOnDestroy() {
        try {
            // Unregister the refresh receiver
            getContext().unregisterReceiver(refreshReceiver);
            
            // Stop tracking if active
            if (scheduler != null && !scheduler.isShutdown()) {
                scheduler.shutdown();
            }
            
            super.handleOnDestroy();
        } catch (Exception e) {
            Log.e(TAG, "Error in handleOnDestroy", e);
        }
    }
    
    /**
     * Check if the app has permission to access usage stats
     */
    @PluginMethod
    public void checkPermission(PluginCall call) {
        JSObject ret = new JSObject();
        ret.put("value", hasUsagePermission());
        call.resolve(ret);
    }
    
    /**
     * Open the usage access settings page
     */
    @PluginMethod
    public void requestUsagePermission(PluginCall call) {
        Log.d(TAG, "requestUsagePermission: Opening usage access settings");
        try {
            Intent intent = new Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS);
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            getActivity().startActivity(intent);
            Log.d(TAG, "requestUsagePermission: Successfully opened settings");
            call.resolve();
        } catch (Exception e) {
            Log.e(TAG, "requestUsagePermission: Failed to open usage access settings", e);
            call.reject("Failed to open usage access settings", e);
        }
    }
    
    /**
     * Start tracking app usage
     */
    @PluginMethod
    public void startTracking(PluginCall call) {
        try {
            if (!checkUsageStatsPermission()) {
                call.reject("Usage stats permission not granted");
                return;
            }

            // Start the background service
            Intent serviceIntent = new Intent(getContext(), BackgroundService.class);
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                getContext().startForegroundService(serviceIntent);
            } else {
                getContext().startService(serviceIntent);
            }

            // Initialize tracking
            isTracking = true;
            lastUpdateTime = System.currentTimeMillis();
            
            // Start periodic updates
            if (scheduler == null || scheduler.isShutdown()) {
                scheduler = Executors.newSingleThreadScheduledExecutor();
                scheduler.scheduleAtFixedRate(() -> {
                    try {
                        if (isTracking) {
                            updateAppUsage();
                        }
                    } catch (Exception e) {
                        Log.e(TAG, "Error in periodic update", e);
                    }
                }, 0, 1, TimeUnit.MINUTES);
            }

            call.resolve();
        } catch (Exception e) {
            Log.e(TAG, "Error starting tracking", e);
            call.reject("Failed to start tracking: " + e.getMessage());
        }
    }
    
    private void updateAppUsage() {
        try {
            // Get current usage stats
            UsageStatsManager usageStatsManager = (UsageStatsManager) getContext().getSystemService(Context.USAGE_STATS_SERVICE);
            if (usageStatsManager != null) {
                long startTime = getStartOfDay();
                long endTime = System.currentTimeMillis();
                
                // Query usage events for more accurate calculation
                UsageEvents usageEvents = usageStatsManager.queryEvents(startTime, endTime);
                UsageEvents.Event event = new UsageEvents.Event();
                float nativeTotalTime = 0;
                String lastPackage = null;
                long lastEventTime = 0;
                
                while (usageEvents.hasNextEvent()) {
                    usageEvents.getNextEvent(event);
                    String packageName = event.getPackageName();
                    
                    // Skip our own app and system apps
                    if (packageName.equals(getContext().getPackageName()) || isSystemApp(getContext(), packageName)) {
                        continue;
                    }
                    
                    if (event.getEventType() == UsageEvents.Event.MOVE_TO_FOREGROUND) {
                        if (lastPackage != null && lastEventTime > 0) {
                            // Calculate time spent in the last app
                            long timeSpent = event.getTimeStamp() - lastEventTime;
                            nativeTotalTime += timeSpent / (60f * 1000f);
                        }
                        lastPackage = packageName;
                        lastEventTime = event.getTimeStamp();
                    } else if (event.getEventType() == UsageEvents.Event.MOVE_TO_BACKGROUND) {
                        if (lastPackage != null && lastEventTime > 0) {
                            // Calculate time spent in the current app
                            long timeSpent = event.getTimeStamp() - lastEventTime;
                            nativeTotalTime += timeSpent / (60f * 1000f);
                        }
                        lastPackage = null;
                        lastEventTime = 0;
                    }
                }
                
                // If there's still an active app, calculate its time up to now
                if (lastPackage != null && lastEventTime > 0) {
                    long timeSpent = endTime - lastEventTime;
                    nativeTotalTime += timeSpent / (60f * 1000f);
                }
                
                // Get the stored capacitor value
                SharedPreferences prefs = getContext().getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
                float capacitorTime = prefs.getFloat("capacitorScreenTime", 0);
                long lastCapacitorUpdate = prefs.getLong("lastCapacitorUpdate", 0);
                
                // Use the higher value between native and capacitor, but only if capacitor value is recent (within 5 minutes)
                float finalTotalTime = nativeTotalTime;
                if (lastCapacitorUpdate > 0 && (System.currentTimeMillis() - lastCapacitorUpdate) < 5 * 60 * 1000) {
                    finalTotalTime = Math.max(nativeTotalTime, capacitorTime);
                }
                
                // Update shared preferences
                SharedPreferences.Editor editor = prefs.edit();
                editor.putFloat(KEY_TOTAL_SCREEN_TIME, finalTotalTime);
                editor.putLong(KEY_LAST_UPDATE, System.currentTimeMillis());
                editor.apply();
                
                // Broadcast the update
                Intent broadcastIntent = new Intent("com.screentimereminder.app.APP_USAGE_UPDATE");
                JSONObject updateData = new JSONObject();
                updateData.put("totalScreenTime", finalTotalTime);
                updateData.put("timestamp", System.currentTimeMillis());
                broadcastIntent.putExtra("usageData", updateData.toString());
                getContext().sendBroadcast(broadcastIntent);
                
                // Update widget
                Intent updateIntent = new Intent(getContext(), ScreenTimeWidgetProvider.class);
                updateIntent.setAction("android.appwidget.action.APPWIDGET_UPDATE");
                int[] ids = AppWidgetManager.getInstance(getContext())
                    .getAppWidgetIds(new ComponentName(getContext(), ScreenTimeWidgetProvider.class));
                updateIntent.putExtra(AppWidgetManager.EXTRA_APPWIDGET_IDS, ids);
                getContext().sendBroadcast(updateIntent);
            }
        } catch (Exception e) {
            Log.e(TAG, "Error updating app usage", e);
        }
    }
    
    /**
     * Stop tracking app usage
     */
    @PluginMethod
    public void stopTracking(PluginCall call) {
        try {
            // Stop the background service
            Intent serviceIntent = new Intent(getContext(), AppUsageService.class);
            getContext().stopService(serviceIntent);

            call.resolve();
        } catch (Exception e) {
            Log.e(TAG, "Error stopping tracking", e);
            call.reject("Failed to stop tracking: " + e.getMessage(), e);
        }
    }
    
    /**
     * Get app usage data for a specific time range
     */
    @PluginMethod
    public void getAppUsageData(PluginCall call) {
        try {
            if (context == null || usageStatsManager == null || mainHandler == null) {
                Log.e(TAG, "Required components not initialized in getAppUsageData");
                call.reject("App usage tracking not properly initialized");
                return;
            }

            backgroundExecutor.execute(() -> {
                try {
                    long startTime = getStartOfDay();
                    long endTime = System.currentTimeMillis();
                    
                    List<UsageStats> stats = usageStatsManager.queryUsageStats(
                        UsageStatsManager.INTERVAL_DAILY, startTime, endTime
                    );
                    
                    if (stats == null) {
                        Log.e(TAG, "Usage stats query returned null");
                        call.reject("Failed to get usage stats");
                        return;
                    }
                    
                    // Process stats and create response
                    JSONObject result = new JSONObject();
                    JSONArray appsArray = new JSONArray();
                    long totalScreenTime = 0;
                    
                    for (UsageStats stat : stats) {
                        String packageName = stat.getPackageName();
                        
                        if (isSystemApp(getContext(), packageName) || packageName.equals(getContext().getPackageName())) {
                            continue;
                        }
                        
                        long timeInForeground = stat.getTotalTimeInForeground();
                        if (timeInForeground > 0) {
                            double timeInMinutes = timeInForeground / 60000.0;
                            totalScreenTime += timeInForeground;
                            
                            String appName = getAppName(packageName);
                            String category = getCategoryForApp(appName);
                            
                            JSONObject appData = new JSONObject();
                            appData.put("name", appName);
                            appData.put("packageName", packageName);
                            appData.put("time", timeInMinutes);
                            appData.put("lastUsed", stat.getLastTimeUsed());
                            appData.put("category", category);
                            appData.put("icon", getAppIconBase64(packageName));
                            
                            appsArray.put(appData);
                        }
                    }
                    
                    result.put("apps", appsArray);
                    result.put("totalScreenTime", totalScreenTime / 60000.0);
                    result.put("timestamp", System.currentTimeMillis());
                    
                    // Convert JSONObject to JSObject before resolving
                    JSObject jsResult = new JSObject();
                    jsResult.put("data", result.toString());
                    
                    mainHandler.post(() -> call.resolve(jsResult));
                } catch (Exception e) {
                    Log.e(TAG, "Error getting app usage data", e);
                    mainHandler.post(() -> call.reject("Error getting app usage data: " + e.getMessage()));
                }
            });
        } catch (Exception e) {
            Log.e(TAG, "Error in getAppUsageData", e);
            call.reject("Error in getAppUsageData: " + e.getMessage());
        }
    }
    
    /**
     * Check if the app is exempt from battery optimization
     */
    @PluginMethod
    public void isBatteryOptimizationExempt(PluginCall call) {
        try {
            boolean isExempt = false;
            
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                PowerManager powerManager = (PowerManager) getContext().getSystemService(Context.POWER_SERVICE);
                if (powerManager != null) {
                    isExempt = powerManager.isIgnoringBatteryOptimizations(getContext().getPackageName());
                }
            }
            
            Log.d(TAG, "isBatteryOptimizationExempt: " + isExempt);
            
            JSObject ret = new JSObject();
            ret.put("value", isExempt);
            call.resolve(ret);
        } catch (Exception e) {
            Log.e(TAG, "Error checking battery optimization exemption", e);
            call.reject("Failed to check battery optimization exemption", e);
        }
    }
    
    /**
     * Request exemption from battery optimization
     */
    @PluginMethod
    public void requestBatteryOptimizationExemption(PluginCall call) {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                Intent intent = new Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS);
                intent.setData(android.net.Uri.parse("package:" + getContext().getPackageName()));
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                
                getContext().startActivity(intent);
                Log.d(TAG, "requestBatteryOptimizationExemption: Successfully opened settings");
                call.resolve();
            } else {
                Log.d(TAG, "requestBatteryOptimizationExemption: Not supported on this Android version");
                call.resolve();
            }
        } catch (Exception e) {
            Log.e(TAG, "Error requesting battery optimization exemption", e);
            call.reject("Failed to request battery optimization exemption", e);
        }
    }
    
    /**
     * Check if the app has reached the screen time limit
     */
    @PluginMethod
    public void checkScreenTimeLimit(PluginCall call) {
        try {
            // Get current screen time using the same method as Statistics.tsx and widget
            float totalTime = getTodayScreenTime(getContext());
            
            // Get limit value and notification frequency from call data
            JSObject data = call.getData();
            int limit = data.has("limit") ? data.getInt("limit") : 60;
            int notificationFrequency = data.has("notificationFrequency") ? data.getInt("notificationFrequency") : 5;
            
            // Save the limit and notification frequency to SharedPreferences
            SharedPreferences prefs = getContext().getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
            SharedPreferences.Editor editor = prefs.edit();
            editor.putLong(KEY_SCREEN_TIME_LIMIT, limit);
            editor.putLong(KEY_NOTIFICATION_FREQUENCY, notificationFrequency);
            editor.putBoolean("userHasSetLimit", true);
            editor.apply();

            // Check for notifications
            long lastLimitReached = prefs.getLong("lastLimitReachedNotification", 0);
            long lastApproachingLimit = prefs.getLong("lastApproachingLimitNotification", 0);
            long currentTime = System.currentTimeMillis();
            
            // Calculate percentage of limit
            float percentOfLimit = (totalTime / limit) * 100;
            
            // Use the notification frequency from settings
            long NOTIFICATION_COOLDOWN = notificationFrequency * 60 * 1000;
            
            // Check if we should show notifications
            if (percentOfLimit >= 100) {
                // Check cooldown for limit reached notification
                if (currentTime - lastLimitReached >= NOTIFICATION_COOLDOWN) {
                    showNotification("Screen Time Limit Reached", 
                        String.format("You have reached your daily limit of %d minutes.\nCurrent usage: %d minutes", 
                            limit, Math.round(totalTime)));
                    
                    editor.putLong("lastLimitReachedNotification", currentTime).apply();
                    Log.d(TAG, "Sent limit reached notification at " + percentOfLimit + "% of limit");
                }
            } else if (percentOfLimit >= 90) {
                // Check cooldown for approaching limit notification
                if (currentTime - lastApproachingLimit >= NOTIFICATION_COOLDOWN) {
                    showNotification("Approaching Screen Time Limit", 
                        String.format("You have %d minutes remaining.\nCurrent usage: %d minutes\nDaily limit: %d minutes", 
                            Math.round(limit - totalTime), Math.round(totalTime), limit));
                    
                    editor.putLong("lastApproachingLimitNotification", currentTime).apply();
                    Log.d(TAG, "Sent approaching limit notification at " + percentOfLimit + "% of limit");
                }
            }

            // Update widget
            Intent updateIntent = new Intent(getContext(), ScreenTimeWidgetProvider.class);
            updateIntent.setAction("android.appwidget.action.APPWIDGET_UPDATE");
            int[] ids = AppWidgetManager.getInstance(getContext())
                .getAppWidgetIds(new ComponentName(getContext(), ScreenTimeWidgetProvider.class));
            updateIntent.putExtra(AppWidgetManager.EXTRA_APPWIDGET_IDS, ids);
            getContext().sendBroadcast(updateIntent);

            call.resolve();
        } catch (Exception e) {
            Log.e(TAG, "Error checking screen time limit", e);
            call.reject("Failed to check screen time limit: " + e.getMessage());
        }
    }

    private float getTodayScreenTime(Context context) {
        try {
            // Get start of day
            Calendar calendar = Calendar.getInstance();
            calendar.set(Calendar.HOUR_OF_DAY, 0);
            calendar.set(Calendar.MINUTE, 0);
            calendar.set(Calendar.SECOND, 0);
            calendar.set(Calendar.MILLISECOND, 0);
            long startTime = calendar.getTimeInMillis();
            long endTime = System.currentTimeMillis();

            // Get app usage data
            UsageStatsManager usageStatsManager = (UsageStatsManager) context.getSystemService(Context.USAGE_STATS_SERVICE);
            if (usageStatsManager == null) {
                return 0;
            }

            List<UsageStats> stats = usageStatsManager.queryUsageStats(
                UsageStatsManager.INTERVAL_DAILY, startTime, endTime
            );

            if (stats == null) {
                return 0;
            }

            float totalMinutes = 0;
            String ourPackage = context.getPackageName();

            for (UsageStats stat : stats) {
                String packageName = stat.getPackageName();
                
                // Skip our own app and system apps
                if (packageName.equals(ourPackage) || isSystemApp(context, packageName)) {
                    continue;
                }

                // Only count time if the app was used today
                if (stat.getLastTimeUsed() >= startTime) {
                    totalMinutes += stat.getTotalTimeInForeground() / (60f * 1000f);
                }
            }

            return totalMinutes;
        } catch (Exception e) {
            Log.e(TAG, "Error getting today's screen time", e);
            return 0;
        }
    }
    
    @PluginMethod
    public void getSharedPreferences(PluginCall call) {
        try {
            SharedPreferences prefs = getContext().getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
            
            // Get values with correct types
            long lastLimitReached = prefs.getLong(KEY_LAST_LIMIT_NOTIFICATION, 0);
            long lastApproachingLimit = prefs.getLong(KEY_LAST_APPROACHING_NOTIFICATION, 0);
            long screenTimeLimit = prefs.getLong(KEY_SCREEN_TIME_LIMIT, DEFAULT_SCREEN_TIME_LIMIT);
            long notificationFrequency = prefs.getLong(KEY_NOTIFICATION_FREQUENCY, 15);
            float totalScreenTime = getSafeScreenTime(prefs); // Use our safe method here
            
            // If timestamps are 0, set them to current time
            long currentTime = System.currentTimeMillis();
            if (lastLimitReached == 0) {
                lastLimitReached = currentTime;
                prefs.edit().putLong(KEY_LAST_LIMIT_NOTIFICATION, currentTime).apply();
            }
            if (lastApproachingLimit == 0) {
                lastApproachingLimit = currentTime;
                prefs.edit().putLong(KEY_LAST_APPROACHING_NOTIFICATION, currentTime).apply();
            }
            
            // Log the values for debugging
            Log.d(TAG, "SharedPreferences values:");
            Log.d(TAG, "lastLimitReached: " + lastLimitReached + " (" + new Date(lastLimitReached).toString() + ")");
            Log.d(TAG, "lastApproachingLimit: " + lastApproachingLimit + " (" + new Date(lastApproachingLimit).toString() + ")");
            Log.d(TAG, "screenTimeLimit: " + screenTimeLimit);
            Log.d(TAG, "notificationFrequency: " + notificationFrequency);
            Log.d(TAG, "totalScreenTime: " + totalScreenTime);
            
            JSObject ret = new JSObject();
            ret.put("lastLimitReachedNotification", lastLimitReached);
            ret.put("lastApproachingLimitNotification", lastApproachingLimit);
            ret.put("screenTimeLimit", screenTimeLimit);
            ret.put("notificationFrequency", notificationFrequency);
            ret.put("totalScreenTime", totalScreenTime);
            
            call.resolve(ret);
        } catch (Exception e) {
            Log.e(TAG, "Error getting shared preferences", e);
            call.reject("Failed to get shared preferences: " + e.getMessage());
        }
    }
    
    /**
     * Check if the app has permission to access usage stats
     */
    private boolean checkUsageStatsPermission() {
        try {
            AppOpsManager appOps = (AppOpsManager) getContext().getSystemService(Context.APP_OPS_SERVICE);
            if (appOps == null) {
                Log.e(TAG, "AppOpsManager is null");
                return false;
            }

            int mode = appOps.checkOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS,
                    android.os.Process.myUid(), getContext().getPackageName());

            boolean hasPermission = mode == AppOpsManager.MODE_ALLOWED;
            Log.d(TAG, "Usage permission check result: " + hasPermission);
            return hasPermission;
        } catch (Exception e) {
            Log.e(TAG, "Error checking usage permission", e);
            return false;
        }
    }
    
    /**
     * Check the current foreground app
     */
    private void checkCurrentApp() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.LOLLIPOP || usageStatsManager == null) {
            return;
        }
        
        long time = System.currentTimeMillis();
        UsageEvents usageEvents = usageStatsManager.queryEvents(time - 10000, time);
        
        if (usageEvents == null) {
            return;
        }
        
        UsageEvents.Event event = new UsageEvents.Event();
        String currentApp = null;
        
        while (usageEvents.hasNextEvent()) {
            usageEvents.getNextEvent(event);
            
            if (event.getEventType() == UsageEvents.Event.MOVE_TO_FOREGROUND) {
                currentApp = event.getPackageName();
            }
        }
        
        if (currentApp != null && !currentApp.equals(currentForegroundApp)) {
            currentForegroundApp = currentApp;
            
            // Get app name
            String appName = currentApp;
            try {
                PackageManager packageManager = getContext().getPackageManager();
                ApplicationInfo appInfo = packageManager.getApplicationInfo(currentApp, PackageManager.GET_META_DATA);
                appName = packageManager.getApplicationLabel(appInfo).toString();
            } catch (Exception e) {
                Log.e(TAG, "Error getting app name", e);
            }
            
            // Notify JavaScript
            final String finalAppName = appName;
            mainHandler.post(() -> {
                JSObject data = new JSObject();
                data.put("packageName", currentForegroundApp);
                data.put("appName", finalAppName);
                notifyListeners("appChanged", data);
            });
        }
    }
    
    /**
     * Create a new app usage JSON object
     */
    private JSONObject createAppUsageObject(String packageName) throws JSONException {
        JSONObject appUsage = new JSONObject();
        String appName = getAppName(packageName);
        String category = getCategoryForApp(appName);
        String iconBase64 = getAppIconBase64(packageName);
        
        Log.d(TAG, "Creating app usage object for " + appName + 
              " (package: " + packageName + ")" +
              " - Icon available: " + (iconBase64 != null && !iconBase64.isEmpty()));
        
        appUsage.put("packageName", packageName);
        appUsage.put("name", appName);
        appUsage.put("time", 0.0);
        appUsage.put("lastUsed", 0);
        appUsage.put("category", category);
        appUsage.put("icon", iconBase64);
        
        return appUsage;
    }
    
    /**
     * Get the display name of an app from its package name
     */
    private String getAppName(String packageName) {
        PackageManager packageManager = getContext().getPackageManager();
        
        // First check if we have permission to access app info
        if (!hasAppInfoPermission()) {
            Log.w(TAG, "No permission to access app info, requesting permission");
            requestAppInfoPermission();
            // Fall back to package name extraction
            return extractReadableName(packageName);
        }

        try {
            // First try with basic flags
            ApplicationInfo appInfo = packageManager.getApplicationInfo(packageName, 0);
            String appName = packageManager.getApplicationLabel(appInfo).toString();
            Log.d(TAG, "Successfully got app name for " + packageName + ": " + appName);
            return appName;
        } catch (PackageManager.NameNotFoundException e) {
            Log.w(TAG, "First attempt to get app name failed for " + packageName + ", trying alternative methods");
            
            try {
                // Try with GET_META_DATA flag
                ApplicationInfo appInfo = packageManager.getApplicationInfo(packageName, PackageManager.GET_META_DATA);
                String appName = packageManager.getApplicationLabel(appInfo).toString();
                Log.d(TAG, "Successfully got app name with GET_META_DATA for " + packageName + ": " + appName);
                return appName;
            } catch (PackageManager.NameNotFoundException e2) {
                Log.w(TAG, "Second attempt to get app name failed for " + packageName + ", trying package info");
                
                try {
                    // Try getting package info instead
                    android.content.pm.PackageInfo pkgInfo = packageManager.getPackageInfo(packageName, 0);
                    if (pkgInfo != null && pkgInfo.applicationInfo != null) {
                        String appName = packageManager.getApplicationLabel(pkgInfo.applicationInfo).toString();
                        Log.d(TAG, "Successfully got app name from package info for " + packageName + ": " + appName);
                        return appName;
                    }
                } catch (PackageManager.NameNotFoundException e3) {
                    Log.w(TAG, "All attempts to get app name failed for " + packageName);
                }
            }
            
            // If all attempts fail, extract a readable name from the package name
            return extractReadableName(packageName);
        }
    }

    private String extractReadableName(String packageName) {
        String[] parts = packageName.split("\\.");
        if (parts.length > 0) {
            String lastPart = parts[parts.length - 1];
            // Capitalize first letter and replace underscores with spaces
            lastPart = lastPart.replace("_", " ");
            lastPart = lastPart.substring(0, 1).toUpperCase() + 
                      lastPart.substring(1).toLowerCase();
            Log.d(TAG, "Using extracted name for " + packageName + ": " + lastPart);
            return lastPart;
        }
        
        Log.d(TAG, "Using package name as fallback for " + packageName);
        return packageName;
    }
    
    /**
     * Check if an app is a system app that should be excluded from tracking
     */
    public static boolean isSystemApp(Context context, String packageName) {
        try {
            PackageManager packageManager = context.getPackageManager();
            ApplicationInfo appInfo = packageManager.getApplicationInfo(packageName, 0);
            return (appInfo.flags & ApplicationInfo.FLAG_SYSTEM) != 0;
        } catch (PackageManager.NameNotFoundException e) {
            return false;
        }
    }
    
    /**
     * Determine the category of an app based on its name
     */
    private String getCategoryForApp(String appName) {
        String lowerCaseName = appName.toLowerCase();
        
        if (lowerCaseName.contains("instagram") || 
            lowerCaseName.contains("facebook") || 
            lowerCaseName.contains("twitter") || 
            lowerCaseName.contains("tiktok") || 
            lowerCaseName.contains("snapchat") || 
            lowerCaseName.contains("whatsapp") || 
            lowerCaseName.contains("telegram") || 
            lowerCaseName.contains("messenger")) {
            return "Social Media";
        } else if (lowerCaseName.contains("youtube") || 
                  lowerCaseName.contains("netflix") || 
                  lowerCaseName.contains("hulu") || 
                  lowerCaseName.contains("disney") || 
                  lowerCaseName.contains("spotify") || 
                  lowerCaseName.contains("music") || 
                  lowerCaseName.contains("video") || 
                  lowerCaseName.contains("player") || 
                  lowerCaseName.contains("movie")) {
            return "Entertainment";
        } else if (lowerCaseName.contains("chrome") || 
                  lowerCaseName.contains("safari") || 
                  lowerCaseName.contains("firefox") || 
                  lowerCaseName.contains("edge") || 
                  lowerCaseName.contains("browser") || 
                  lowerCaseName.contains("gmail") || 
                  lowerCaseName.contains("outlook") || 
                  lowerCaseName.contains("office") || 
                  lowerCaseName.contains("word") || 
                  lowerCaseName.contains("excel") || 
                  lowerCaseName.contains("powerpoint") || 
                  lowerCaseName.contains("docs")) {
            return "Productivity";
        } else if (lowerCaseName.contains("game") || 
                  lowerCaseName.contains("minecraft") || 
                  lowerCaseName.contains("fortnite") || 
                  lowerCaseName.contains("roblox") || 
                  lowerCaseName.contains("pubg") || 
                  lowerCaseName.contains("cod") || 
                  lowerCaseName.contains("league") || 
                  lowerCaseName.contains("dota")) {
            return "Games";
        } else if (lowerCaseName.contains("duolingo") || 
                  lowerCaseName.contains("khan") || 
                  lowerCaseName.contains("academy") || 
                  lowerCaseName.contains("learn") || 
                  lowerCaseName.contains("course") || 
                  lowerCaseName.contains("study") || 
                  lowerCaseName.contains("school") || 
                  lowerCaseName.contains("education")) {
            return "Education";
        } else {
            return "Other";
        }
    }
    
    /**
     * Get the app icon as a Base64 encoded string
     */
    private String getAppIconBase64(String packageName) {
        try {
            PackageManager packageManager = getContext().getPackageManager();
            
            // First check if we have permission to access app info
            if (!hasAppInfoPermission()) {
                Log.w(TAG, "No permission to access app info, skipping icon for " + packageName);
                return "";
            }
            
            // First try to get the application info
            ApplicationInfo appInfo;
            try {
                appInfo = packageManager.getApplicationInfo(packageName, PackageManager.GET_META_DATA);
            } catch (PackageManager.NameNotFoundException e) {
                Log.w(TAG, "Package not found when getting icon for " + packageName + ", skipping icon");
                return "";
            }
            
            // Try to get the app icon
            android.graphics.drawable.Drawable icon;
            try {
                // First try to get the adaptive icon (Android 8.0+)
                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                    try {
                        android.graphics.drawable.AdaptiveIconDrawable adaptiveIcon = 
                            (android.graphics.drawable.AdaptiveIconDrawable) packageManager.getApplicationIcon(appInfo);
                        if (adaptiveIcon != null) {
                            android.graphics.Bitmap bitmap = android.graphics.Bitmap.createBitmap(
                                192, 192, android.graphics.Bitmap.Config.ARGB_8888);
                            android.graphics.Canvas canvas = new android.graphics.Canvas(bitmap);
                            adaptiveIcon.setBounds(0, 0, 192, 192);
                            adaptiveIcon.draw(canvas);
                            return bitmapToBase64(bitmap);
                        }
                    } catch (Exception e) {
                        Log.d(TAG, "Adaptive icon not available for " + packageName + ", trying regular icon");
                    }
                }
                
                // Try to get the icon from the app's resources
                try {
                    icon = packageManager.getApplicationIcon(appInfo);
                } catch (Exception e) {
                    Log.d(TAG, "Failed to get application icon, trying activity icon for " + packageName);
                    // Try to get the activity icon as fallback
                    android.content.pm.ActivityInfo[] activities = packageManager.getPackageInfo(
                        packageName, PackageManager.GET_ACTIVITIES).activities;
                    if (activities != null && activities.length > 0) {
                        icon = activities[0].loadIcon(packageManager);
                    } else {
                        throw e;
                    }
                }
            } catch (Exception e) {
                Log.w(TAG, "Failed to get any icon for " + packageName + ", skipping icon");
                return "";
            }
            
            // Create a bitmap from the drawable
            android.graphics.Bitmap bitmap;
            
            if (icon instanceof android.graphics.drawable.BitmapDrawable) {
                bitmap = ((android.graphics.drawable.BitmapDrawable) icon).getBitmap();
            } else {
                // Create a new bitmap and draw the icon onto it
                int width = Math.max(icon.getIntrinsicWidth(), 1);
                int height = Math.max(icon.getIntrinsicHeight(), 1);
                
                bitmap = android.graphics.Bitmap.createBitmap(
                    width,
                    height,
                    android.graphics.Bitmap.Config.ARGB_8888
                );
                android.graphics.Canvas canvas = new android.graphics.Canvas(bitmap);
                icon.setBounds(0, 0, canvas.getWidth(), canvas.getHeight());
                icon.draw(canvas);
            }
            
            // Scale the bitmap to a reasonable size
            android.graphics.Bitmap scaledBitmap = android.graphics.Bitmap.createScaledBitmap(
                bitmap, 192, 192, true);
            
            return bitmapToBase64(scaledBitmap);
            
        } catch (Exception e) {
            Log.w(TAG, "Error getting app icon for " + packageName + ", skipping icon", e);
            return "";
        }
    }

    private String bitmapToBase64(android.graphics.Bitmap bitmap) {
        try {
            java.io.ByteArrayOutputStream stream = new java.io.ByteArrayOutputStream();
            bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, stream);
            byte[] bitmapData = stream.toByteArray();
            String base64 = android.util.Base64.encodeToString(bitmapData, android.util.Base64.DEFAULT);
            
            // Log success
            Log.d(TAG, "Successfully converted bitmap to Base64 (size: " + bitmapData.length + " bytes)");
            
            return base64;
        } catch (Exception e) {
            Log.e(TAG, "Error converting bitmap to Base64", e);
            return "";
        }
    }

    private boolean hasAppInfoPermission() {
        try {
            PackageManager packageManager = getContext().getPackageManager();
            // Try to get info for a known system app to test permissions
            packageManager.getApplicationInfo("android", 0);
            return true;
        } catch (PackageManager.NameNotFoundException e) {
            return false;
        }
    }

    private void requestAppInfoPermission() {
        try {
            // First try to open app info settings
            Intent intent = new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS);
            intent.setData(android.net.Uri.parse("package:" + getContext().getPackageName()));
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            getContext().startActivity(intent);
            
            // Also request QUERY_ALL_PACKAGES permission if needed
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.R) {
                Intent queryIntent = new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION);
                queryIntent.setData(android.net.Uri.parse("package:" + getContext().getPackageName()));
                queryIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                getContext().startActivity(queryIntent);
            }
        } catch (Exception e) {
            Log.e(TAG, "Error requesting app info permission", e);
        }
    }

    private long getTotalScreenTimeForToday() {
        try {
            long totalMinutes = 0;
            long startTime = getStartOfDay();
            long endTime = System.currentTimeMillis();
            
            UsageStatsManager usageStatsManager = (UsageStatsManager) getContext()
                .getSystemService(Context.USAGE_STATS_SERVICE);
            
            if (usageStatsManager == null) {
                Log.e(TAG, "UsageStatsManager is null");
                return 0;
            }
            
            // Query for today's usage stats
            Map<String, UsageStats> stats = usageStatsManager
                .queryAndAggregateUsageStats(startTime, endTime);
            
            if (stats == null || stats.isEmpty()) {
                Log.w(TAG, "No usage stats available for today");
                return 0;
            }
            
            // Calculate total time excluding our own app and system apps
            String ourPackage = getContext().getPackageName();
            for (Map.Entry<String, UsageStats> entry : stats.entrySet()) {
                String packageName = entry.getKey();
                UsageStats usageStats = entry.getValue();
                
                // Skip our own app and system apps
                if (packageName.equals(ourPackage) || isSystemApp(getContext(), packageName)) {
                    continue;
                }
                
                // Convert to minutes
                long timeInForeground = usageStats.getTotalTimeInForeground();
                totalMinutes += timeInForeground / (1000 * 60);
            }
            
            return totalMinutes;
        } catch (Exception e) {
            Log.e(TAG, "Error getting total screen time for today", e);
            return 0;
        }
    }

    public static long getStartOfDay() {
        Calendar calendar = Calendar.getInstance();
        calendar.set(Calendar.HOUR_OF_DAY, 0);
        calendar.set(Calendar.MINUTE, 0);
        calendar.set(Calendar.SECOND, 0);
        calendar.set(Calendar.MILLISECOND, 0);
        return calendar.getTimeInMillis();
    }

    private void ensureServiceRunning() {
        try {
            android.app.ActivityManager manager = (android.app.ActivityManager) getContext().getSystemService(Context.ACTIVITY_SERVICE);
            boolean isServiceRunning = false;
            
            for (android.app.ActivityManager.RunningServiceInfo service : manager.getRunningServices(Integer.MAX_VALUE)) {
                if (BackgroundService.class.getName().equals(service.service.getClassName())) {
                    isServiceRunning = true;
                    break;
                }
            }
            
            if (!isServiceRunning) {
                Log.d(TAG, "Service not running, restarting...");
                Intent serviceIntent = new Intent(getContext(), BackgroundService.class);
                serviceIntent.setAction("START_TRACKING");
                
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    getContext().startForegroundService(serviceIntent);
                } else {
                    getContext().startService(serviceIntent);
                }
                
                // Request battery optimization exemption
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                    PowerManager powerManager = (PowerManager) getContext().getSystemService(Context.POWER_SERVICE);
                    if (powerManager != null && !powerManager.isIgnoringBatteryOptimizations(getContext().getPackageName())) {
                        Intent intent = new Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS);
                        intent.setData(android.net.Uri.parse("package:" + getContext().getPackageName()));
                        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                        getContext().startActivity(intent);
                    }
                }
            }
        } catch (Exception e) {
            Log.e(TAG, "Error ensuring service is running", e);
        }
    }

    private final BroadcastReceiver usageUpdateReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            Log.d(TAG, "Received broadcast with action: " + intent.getAction());
            if (intent.hasExtra("usageData")) {
                String usageData = intent.getStringExtra("usageData");
                try {
                    Log.d(TAG, "Processing usage data: " + usageData);
                    JSObject ret = new JSObject();
                    ret.put("usageData", usageData);
                    notifyListeners("appUsageUpdate", ret);
                    Log.d(TAG, "Notified web listeners with usage data");
                } catch (Exception e) {
                    Log.e(TAG, "Error notifying listeners", e);
                }
            } else {
                Log.w(TAG, "Received broadcast without usageData extra");
            }
        }
    };

    @PluginMethod
    public void setScreenTimeLimit(PluginCall call) {
        try {
            JSObject data = call.getData();
            if (!data.has("limit")) {
                call.reject("Missing required parameter: limit");
                return;
            }

            // Try to get the value as a number and convert to long
            long minutes;
            try {
                // Handle both integer and double values from JavaScript
                Object limitValue = data.get("limit");
                if (limitValue instanceof Integer) {
                    minutes = ((Integer) limitValue).longValue();
                } else if (limitValue instanceof Double) {
                    minutes = ((Double) limitValue).longValue();
                } else if (limitValue instanceof Long) {
                    minutes = (Long) limitValue;
                } else {
                    minutes = Long.parseLong(limitValue.toString());
                }
            } catch (Exception e) {
                Log.e(TAG, "Error parsing limit value", e);
                call.reject("Invalid limit value: must be a number");
                return;
            }

            Log.d(TAG, "setScreenTimeLimit: Setting screen time limit to: " + minutes + " minutes");
            
            // Use the static method to ensure consistent updates
            setScreenTimeLimitStatic(getContext(), (int)minutes);
            
            call.resolve();
        } catch (Exception e) {
            Log.e(TAG, "Error setting screen time limit", e);
            call.reject("Failed to set screen time limit: " + e.getMessage());
        }
    }
    
    @PluginMethod
    public void setNotificationFrequency(PluginCall call) {
        try {
            JSObject data = call.getData();
            if (!data.has("frequency")) {
                call.reject("Missing required parameter: frequency");
                return;
            }

            // Try to get the value as a number and convert to long
            long minutes;
            try {
                // Handle both integer and double values from JavaScript
                Object frequencyValue = data.get("frequency");
                if (frequencyValue instanceof Integer) {
                    minutes = ((Integer) frequencyValue).longValue();
                } else if (frequencyValue instanceof Double) {
                    minutes = ((Double) frequencyValue).longValue();
                } else if (frequencyValue instanceof Long) {
                    minutes = (Long) frequencyValue;
                } else {
                    minutes = Long.parseLong(frequencyValue.toString());
                }
            } catch (Exception e) {
                Log.e(TAG, "Error parsing frequency value", e);
                call.reject("Invalid frequency value: must be a number");
                return;
            }

            Log.d(TAG, "setNotificationFrequency: Setting notification frequency to: " + minutes + " minutes");
            
            SharedPreferences.Editor editor = getContext().getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit();
            editor.putLong(KEY_NOTIFICATION_FREQUENCY, minutes);
            editor.apply();
            
            Log.d(TAG, "Notification frequency updated successfully");
            call.resolve();
        } catch (Exception e) {
            Log.e(TAG, "Error setting notification frequency", e);
            call.reject("Failed to set notification frequency: " + e.getMessage());
        }
    }

    @PluginMethod
    public void getNotificationFrequency(PluginCall call) {
        try {
            SharedPreferences prefs = getContext().getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
            long frequency = prefs.getLong(KEY_NOTIFICATION_FREQUENCY, 5L);
            Log.d(TAG, "Getting notification frequency: " + frequency + " minutes");
            
            JSObject ret = new JSObject();
            ret.put("value", frequency);
            call.resolve(ret);
        } catch (Exception e) {
            Log.e(TAG, "Error getting notification frequency", e);
            call.reject("Failed to get notification frequency: " + e.getMessage());
        }
    }

    private void broadcastUsageData(Context context) {
        try {
            // Get the stored values directly from SharedPreferences
            SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
            float totalScreenTime = getSafeScreenTime(prefs);
            long screenTimeLimit = prefs.getLong(KEY_SCREEN_TIME_LIMIT, DEFAULT_SCREEN_TIME_LIMIT);

            // Create JSON object with the data
            JSONObject data = new JSONObject();
            data.put("totalScreenTime", totalScreenTime);
            data.put("screenTimeLimit", screenTimeLimit);
            data.put("timestamp", System.currentTimeMillis());

            // Broadcast to any listening components
            Intent intent = new Intent("com.screentimereminder.app.APP_USAGE_UPDATE");
            intent.putExtra("usageData", data.toString());
            context.sendBroadcast(intent);
            
            Log.d(TAG, "Broadcasting usage data: " + data.toString());
        } catch (Exception e) {
            Log.e(TAG, "Error broadcasting usage data", e);
        }
    }

    private void updateSharedPreferences() {
        try {
            SharedPreferences prefs = getContext().getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
            SharedPreferences.Editor editor = prefs.edit();
            
            // Get current screen time
            int totalMinutes = getTotalScreenTime();
            
            // Update the preferences
            editor.putLong(KEY_SCREEN_TIME_LIMIT, screenTimeLimit);
            editor.putBoolean("userHasSetLimit", true);
            editor.putFloat(KEY_TOTAL_SCREEN_TIME, totalMinutes);
            editor.putLong(KEY_LAST_UPDATE, System.currentTimeMillis());
            editor.apply();
            
            // Update widget with latest data
            updateWidgetWithData(totalMinutes);
            
            // Check for notifications
            checkScreenTimeLimit(totalMinutes);
            
            // Broadcast the update
            broadcastUsageData(getContext());
            
            Log.d(TAG, "Updated shared preferences and broadcasted update");
        } catch (Exception e) {
            Log.e(TAG, "Error updating shared preferences", e);
        }
    }

    private void updateWidgetWithData(int totalMinutes) {
        try {
            // Update widget immediately
            Intent updateIntent = new Intent(getContext(), ScreenTimeWidgetProvider.class);
            updateIntent.setAction("android.appwidget.action.APPWIDGET_UPDATE");
            int[] ids = AppWidgetManager.getInstance(getContext())
                .getAppWidgetIds(new ComponentName(getContext(), ScreenTimeWidgetProvider.class));
            updateIntent.putExtra(AppWidgetManager.EXTRA_APPWIDGET_IDS, ids);
            
            // Add screen time data to the intent
            updateIntent.putExtra("totalScreenTime", totalMinutes);
            updateIntent.putExtra("screenTimeLimit", screenTimeLimit);
            
            getContext().sendBroadcast(updateIntent);
            Log.d(TAG, "Widget updated with screen time: " + totalMinutes + " minutes");
        } catch (Exception e) {
            Log.e(TAG, "Error updating widget", e);
        }
    }

    private void checkScreenTimeLimit(int totalMinutes) {
        try {
            // Get the current screen time limit
            long screenTimeLimit = getScreenTimeLimit();
            Log.d(TAG, "Checking screen time limit - Total: " + totalMinutes + ", Limit: " + screenTimeLimit);

            // Calculate percentage of limit
            float percentOfLimit = (totalMinutes / (float)screenTimeLimit) * 100;
            
            // Get last notification times
            SharedPreferences prefs = getContext().getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
            long lastLimitReached = prefs.getLong(KEY_LAST_LIMIT_NOTIFICATION, 0);
            long lastApproachingLimit = prefs.getLong(KEY_LAST_APPROACHING_NOTIFICATION, 0);
            long currentTime = System.currentTimeMillis();
            
            // Get notification frequency from settings (in minutes)
            long notificationFrequency = prefs.getLong(KEY_NOTIFICATION_FREQUENCY, 5L);
            long NOTIFICATION_COOLDOWN = notificationFrequency * 60 * 1000;
            
            // Check if we need to show notifications
            if (percentOfLimit >= 100) {
                // Check cooldown for limit reached notification
                if (currentTime - lastLimitReached >= NOTIFICATION_COOLDOWN) {
                    showNotification(getContext(), "Screen Time Limit Reached", 
                        String.format("You have reached your daily limit of %d minutes.\nCurrent usage: %d minutes", 
                            screenTimeLimit, totalMinutes));
                    prefs.edit().putLong(KEY_LAST_LIMIT_NOTIFICATION, currentTime).apply();
                }
            } else if (percentOfLimit >= 90) {
                // Check cooldown for approaching limit notification
                if (currentTime - lastApproachingLimit >= NOTIFICATION_COOLDOWN) {
                    showNotification(getContext(), "Approaching Screen Time Limit", 
                        String.format("You have %d minutes remaining.\nCurrent usage: %d minutes\nDaily limit: %d minutes", 
                            Math.round(screenTimeLimit - totalMinutes), totalMinutes, screenTimeLimit));
                    prefs.edit().putLong(KEY_LAST_APPROACHING_NOTIFICATION, currentTime).apply();
                }
            }
        } catch (Exception e) {
            Log.e(TAG, "Error checking screen time limit", e);
        }
    }

    private void showLimitReachedNotification(int totalMinutes) {
        try {
            // Format the time
            String timeString = formatTime(totalMinutes);
            String limitString = formatTime((int)screenTimeLimit);
            
            // Create the notification message
            String notificationMessage = String.format(
                "You have reached your daily limit of %s.\nCurrent usage: %s", 
                limitString,
                timeString
            );
            
            NotificationManager notificationManager = (NotificationManager) getContext().getSystemService(Context.NOTIFICATION_SERVICE);
            
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                NotificationChannel channel = new NotificationChannel(
                    "screen_time_channel",
                    "Screen Time Notifications",
                    NotificationManager.IMPORTANCE_DEFAULT
                );
                notificationManager.createNotificationChannel(channel);
            }
            
            NotificationCompat.Builder builder = new NotificationCompat.Builder(getContext(), "screen_time_channel")
                .setSmallIcon(android.R.drawable.ic_dialog_info)
                .setContentTitle("Screen Time Limit Reached")
                .setContentText(notificationMessage)
                .setStyle(new NotificationCompat.BigTextStyle().bigText(notificationMessage))
                .setPriority(NotificationCompat.PRIORITY_DEFAULT);
            
            notificationManager.notify(1, builder.build());
            Log.d(TAG, "Showing limit reached notification: " + notificationMessage);
        } catch (Exception e) {
            Log.e(TAG, "Error showing notification", e);
        }
    }

    private void showApproachingLimitNotification(int totalMinutes) {
        try {
            // Format the time
            String timeString = formatTime(totalMinutes);
            String limitString = formatTime((int)screenTimeLimit);
            String remainingString = formatTime((int)(screenTimeLimit - totalMinutes));
            
            // Create the notification message
            String notificationMessage = String.format(
                "You have %s remaining.\nCurrent usage: %s\nDaily limit: %s",
                remainingString,
                timeString,
                limitString
            );
            
            NotificationManager notificationManager = (NotificationManager) getContext().getSystemService(Context.NOTIFICATION_SERVICE);
            
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                NotificationChannel channel = new NotificationChannel(
                    "screen_time_channel",
                    "Screen Time Notifications",
                    NotificationManager.IMPORTANCE_DEFAULT
                );
                notificationManager.createNotificationChannel(channel);
            }
            
            NotificationCompat.Builder builder = new NotificationCompat.Builder(getContext(), "screen_time_channel")
                .setSmallIcon(android.R.drawable.ic_dialog_info)
                .setContentTitle("Approaching Screen Time Limit")
                .setContentText(notificationMessage)
                .setStyle(new NotificationCompat.BigTextStyle().bigText(notificationMessage))
                .setPriority(NotificationCompat.PRIORITY_DEFAULT);
            
            notificationManager.notify(2, builder.build());
            Log.d(TAG, "Showing approaching limit notification: " + notificationMessage);
        } catch (Exception e) {
            Log.e(TAG, "Error showing notification", e);
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

    @PluginMethod
    public void startBackgroundService(PluginCall call) {
        try {
            Log.d(TAG, "Starting background service through plugin");
            
            // Start the background service
            Intent serviceIntent = new Intent(getContext(), BackgroundService.class);
            serviceIntent.setAction("START_TRACKING");
            
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                Log.d(TAG, "Starting foreground service for Android O and above");
                getContext().startForegroundService(serviceIntent);
            } else {
                Log.d(TAG, "Starting service for pre-Android O");
                getContext().startService(serviceIntent);
            }

            // Schedule periodic service check
            if (scheduler == null || scheduler.isShutdown()) {
                scheduler = Executors.newSingleThreadScheduledExecutor();
                scheduler.scheduleAtFixedRate(new Runnable() {
                    @Override
                    public void run() {
                        try {
                            ensureServiceRunning();
                            Thread.sleep((long)(Math.random() * 5000));
                        } catch (Exception e) {
                            Log.e(TAG, "Error in service check", e);
                        }
                    }
                }, 1, 5, TimeUnit.MINUTES);
            }

            // Request battery optimization exemption
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                PowerManager powerManager = (PowerManager) getContext().getSystemService(Context.POWER_SERVICE);
                if (powerManager != null && !powerManager.isIgnoringBatteryOptimizations(getContext().getPackageName())) {
                    Intent intent = new Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS);
                    intent.setData(android.net.Uri.parse("package:" + getContext().getPackageName()));
                    intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                    getContext().startActivity(intent);
                }
            }

            JSObject ret = new JSObject();
            ret.put("value", true);
            call.resolve(ret);
        } catch (Exception e) {
            Log.e(TAG, "Error starting background service", e);
            call.reject("Failed to start background service: " + e.getMessage(), e);
        }
    }

    public int getTotalScreenTime() {
        try {
            if (usageStatsManager == null) {
                Log.e(TAG, "UsageStatsManager is null");
                return 0;
            }

            long endTime = System.currentTimeMillis();
            long startTime = getStartOfDay();
            
            Map<String, UsageStats> stats = usageStatsManager.queryAndAggregateUsageStats(startTime, endTime);
            long totalTime = 0;
            
            for (UsageStats stat : stats.values()) {
                if (!isSystemApp(getContext(), stat.getPackageName()) && !stat.getPackageName().equals(getContext().getPackageName())) {
                    totalTime += stat.getTotalTimeInForeground();
                }
            }
            
            // Convert milliseconds to minutes and store in preferences
            int totalMinutes = (int) (totalTime / (60 * 1000));
            prefs.edit()
                .putFloat(KEY_TOTAL_SCREEN_TIME, totalMinutes)
                .putLong(KEY_LAST_UPDATE, System.currentTimeMillis())
                .apply();
            
            return totalMinutes;
        } catch (Exception e) {
            Log.e(TAG, "Error getting total screen time", e);
            return 0;
        }
    }

    public long getScreenTimeLimit() {
        SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        boolean userHasSetLimit = prefs.getBoolean("userHasSetLimit", false);
        
        if (userHasSetLimit) {
            long limit = prefs.getLong(KEY_SCREEN_TIME_LIMIT, DEFAULT_SCREEN_TIME_LIMIT);
            Log.d(TAG, "getScreenTimeLimit: Retrieved user-set limit: " + limit);
            return limit;
        }
        
        long limit = prefs.getLong(KEY_SCREEN_TIME_LIMIT, DEFAULT_SCREEN_TIME_LIMIT);
        Log.d(TAG, "getScreenTimeLimit: Retrieved default limit: " + limit);
        return limit;
    }

    private UsageStatsManager getUsageStatsManager() {
        try {
            if (usageStatsManager == null && getContext() != null) {
                usageStatsManager = (UsageStatsManager) getContext().getSystemService(Context.USAGE_STATS_SERVICE);
                if (usageStatsManager == null) {
                    Log.e(TAG, "Failed to get UsageStatsManager service");
                }
            }
            return usageStatsManager;
        } catch (Exception e) {
            Log.e(TAG, "Error getting UsageStatsManager", e);
            return null;
        }
    }

    private List<UsageStats> getAppUsageData() {
        try {
            UsageStatsManager manager = getUsageStatsManager();
            if (manager == null) {
                Log.e(TAG, "UsageStatsManager is null");
                return new ArrayList<>();
            }

            // Get today's start time
            Calendar calendar = Calendar.getInstance();
            calendar.set(Calendar.HOUR_OF_DAY, 0);
            calendar.set(Calendar.MINUTE, 0);
            calendar.set(Calendar.SECOND, 0);
            calendar.set(Calendar.MILLISECOND, 0);
            long startTime = calendar.getTimeInMillis();
            long endTime = System.currentTimeMillis();

            List<UsageStats> stats = manager.queryUsageStats(
                UsageStatsManager.INTERVAL_DAILY, startTime, endTime);

            if (stats == null) {
                Log.e(TAG, "Usage stats list is null");
                return new ArrayList<>();
            }

            return stats;
        } catch (Exception e) {
            Log.e(TAG, "Error getting app usage data", e);
            return new ArrayList<>();
        }
    }

    @PluginMethod
    public void getTotalScreenTime(PluginCall call) {
        try {
            if (!hasUsagePermission()) {
                call.reject("Usage access not granted");
                return;
            }

            // Use the static method to ensure consistent calculation
            int totalMinutes = getTotalScreenTimeStatic(getContext());
            
            // Update the widget
            Intent updateIntent = new Intent(getContext(), ScreenTimeWidgetProvider.class);
            updateIntent.setAction("android.appwidget.action.APPWIDGET_UPDATE");
            int[] ids = AppWidgetManager.getInstance(getContext())
                .getAppWidgetIds(new ComponentName(getContext(), ScreenTimeWidgetProvider.class));
            updateIntent.putExtra(AppWidgetManager.EXTRA_APPWIDGET_IDS, ids);
            getContext().sendBroadcast(updateIntent);

            JSObject result = new JSObject();
            result.put("totalScreenTime", totalMinutes);
            call.resolve(result);
        } catch (Exception e) {
            Log.e(TAG, "Error getting total screen time", e);
            call.reject("Error getting total screen time: " + e.getMessage());
        }
    }

    /**
     * Check if the app has permission to access usage stats
     */
    public boolean hasUsagePermission() {
        try {
            AppOpsManager appOps = (AppOpsManager) getContext().getSystemService(Context.APP_OPS_SERVICE);
            if (appOps == null) {
                Log.e(TAG, "AppOpsManager is null");
                return false;
            }

            int mode = appOps.checkOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS,
                    android.os.Process.myUid(), getContext().getPackageName());

            return mode == AppOpsManager.MODE_ALLOWED;
        } catch (Exception e) {
            Log.e(TAG, "Error checking usage permission", e);
            return false;
        }
    }

    @PluginMethod
    public void hasUsagePermission(PluginCall call) {
        try {
            boolean hasPermission = checkUsageStatsPermission();
            JSObject ret = new JSObject();
            ret.put("value", hasPermission);
            call.resolve(ret);
        } catch (Exception e) {
            Log.e(TAG, "Error checking usage permission", e);
            call.reject("Error checking usage permission: " + e.getMessage());
        }
    }

    /**
     * Static method to check usage permission that doesn't rely on Plugin context
     */
    public static boolean checkUsagePermissionStatic(Context context) {
        try {
            AppOpsManager appOps = (AppOpsManager) context.getSystemService(Context.APP_OPS_SERVICE);
            if (appOps == null) {
                Log.e(TAG, "AppOpsManager is null");
                return false;
            }

            int mode = appOps.checkOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS,
                    android.os.Process.myUid(), context.getPackageName());

            return mode == AppOpsManager.MODE_ALLOWED;
        } catch (Exception e) {
            Log.e(TAG, "Error checking usage permission", e);
            return false;
        }
    }

    /**
     * Static method to get total screen time that doesn't rely on Plugin context
     * Returns total screen time in minutes for the current day only
     */
    public static int getTotalScreenTimeStatic(Context context) {
        // Check if we're being called recursively
        StackTraceElement[] stackTrace = Thread.currentThread().getStackTrace();
        boolean isRecursive = false;
        int count = 0;
        for (StackTraceElement element : stackTrace) {
            if (element.getMethodName().equals("getTotalScreenTimeStatic")) {
                count++;
                if (count > 1) {
                    isRecursive = true;
                    break;
                }
            }
        }
        
        if (isRecursive) {
            Log.w(TAG, "Preventing recursive call to getTotalScreenTimeStatic");
            // Instead of returning cached value, force a recalculation
            // This ensures we always get the most up-to-date value
            return Math.round(calculateScreenTime(context));
        }

        Log.d(TAG, "Calculating screen time from " + stackTrace[2].getClassName() + "." + stackTrace[2].getMethodName());
        return Math.round(calculateScreenTime(context));
    }

    /**
     * Calculate screen time for today using the most accurate method
     * Returns total screen time in minutes for the current day only
     */
    public static float calculateScreenTime(Context context) {
        try {
            // Get start of day in user's local timezone
            Calendar calendar = Calendar.getInstance();
            calendar.set(Calendar.HOUR_OF_DAY, 0);
            calendar.set(Calendar.MINUTE, 0);
            calendar.set(Calendar.SECOND, 0);
            calendar.set(Calendar.MILLISECOND, 0);
            long startTime = calendar.getTimeInMillis();
            long endTime = System.currentTimeMillis();

            Log.d(TAG, String.format("Calculating screen time from %s to %s", 
                new Date(startTime).toString(), new Date(endTime).toString()));

            UsageStatsManager usageStatsManager = (UsageStatsManager) context.getSystemService(Context.USAGE_STATS_SERVICE);
            if (usageStatsManager == null) {
                Log.e(TAG, "UsageStatsManager is null");
                return getFallbackScreenTime(context);
            }

            // Query usage events for more accurate calculation
            UsageEvents usageEvents = usageStatsManager.queryEvents(startTime, endTime);
            if (usageEvents == null) {
                Log.w(TAG, "No usage events available for today");
                return getFallbackScreenTime(context);
            }

            Map<String, Long> appUsageTimes = new HashMap<>();
            Map<String, Long> lastForegroundTimes = new HashMap<>();
            Set<String> foregroundApps = new HashSet<>();
            UsageEvents.Event event = new UsageEvents.Event();
            String ourPackage = context.getPackageName();

            while (usageEvents.hasNextEvent()) {
                usageEvents.getNextEvent(event);
                String packageName = event.getPackageName();
                long eventTime = event.getTimeStamp();
                
                // Skip events from before today
                if (eventTime < startTime) {
                    continue;
                }
                
                // Skip our own app and system apps
                if (packageName.equals(ourPackage) || isSystemAppStatic(context, packageName)) {
                    continue;
                }
                
                if (event.getEventType() == UsageEvents.Event.MOVE_TO_FOREGROUND) {
                    // Record the start time for this app
                    lastForegroundTimes.put(packageName, eventTime);
                    foregroundApps.add(packageName);
                    
                } else if (event.getEventType() == UsageEvents.Event.MOVE_TO_BACKGROUND) {
                    // Calculate time spent for this app
                    Long appStartTime = lastForegroundTimes.remove(packageName);
                    if (appStartTime != null) {
                        // Make sure we only count time after start of day
                        long effectiveStartTime = Math.max(appStartTime, startTime);
                        long timeSpent = eventTime - effectiveStartTime;
                        if (timeSpent >= 1000 && timeSpent < 4 * 60 * 60 * 1000) {
                            Long currentTotal = appUsageTimes.getOrDefault(packageName, 0L);
                            appUsageTimes.put(packageName, currentTotal + timeSpent);
                            Log.d(TAG, String.format("App session: %s - %.2f minutes", 
                                getAppName(context, packageName), timeSpent / (60f * 1000f)));
                        }
                    }
                    foregroundApps.remove(packageName);
                }
            }

            // Handle still-active apps
            for (String packageName : new HashSet<>(foregroundApps)) {
                Long appStartTime = lastForegroundTimes.get(packageName);
                if (appStartTime != null) {
                    // Make sure we only count time after start of day
                    long effectiveStartTime = Math.max(appStartTime, startTime);
                    long timeSpent = endTime - effectiveStartTime;
                    if (timeSpent >= 1000 && timeSpent < 4 * 60 * 60 * 1000) {
                        Long currentTotal = appUsageTimes.getOrDefault(packageName, 0L);
                        appUsageTimes.put(packageName, currentTotal + timeSpent);
                        Log.d(TAG, String.format("Active app: %s - %.2f minutes", 
                            getAppName(context, packageName), timeSpent / (60f * 1000f)));
                    }
                }
            }

            // Calculate total minutes
            float totalMinutes = 0;
            for (Map.Entry<String, Long> entry : appUsageTimes.entrySet()) {
                float minutes = entry.getValue() / (60f * 1000f);
                totalMinutes += minutes;
                Log.d(TAG, String.format("%s: %.2f minutes", 
                    getAppName(context, entry.getKey()), minutes));
            }

            // Store the calculated value with timestamp
            SharedPreferences.Editor editor = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit();
            editor.putFloat(KEY_TOTAL_SCREEN_TIME, totalMinutes);
            editor.putLong(KEY_LAST_UPDATE, System.currentTimeMillis());
            editor.apply();

            Log.d(TAG, "Total screen time for today: " + totalMinutes + " minutes");
            return totalMinutes;
        } catch (Exception e) {
            Log.e(TAG, "Error getting total screen time", e);
            return getFallbackScreenTime(context);
        }
    }

    private static String getAppName(Context context, String packageName) {
        try {
            PackageManager packageManager = context.getPackageManager();
            ApplicationInfo appInfo = packageManager.getApplicationInfo(packageName, 0);
            return packageManager.getApplicationLabel(appInfo).toString();
        } catch (PackageManager.NameNotFoundException e) {
            return packageName;
        }
    }

    private static float getFallbackScreenTime(Context context) {
        try {
            SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
            float capacitorTime = prefs.getFloat("capacitorScreenTime", 0);
            long lastCapacitorUpdate = prefs.getLong("lastCapacitorUpdate", 0);
            
            // Only use Capacitor time if it's from today
            if (lastCapacitorUpdate > getStartOfDay()) {
                Log.d(TAG, "Using Capacitor fallback time: " + capacitorTime);
                return capacitorTime;
            }
            
            // If no valid fallback, return stored screen time
            return prefs.getFloat(KEY_TOTAL_SCREEN_TIME, 0);
        } catch (Exception e) {
            Log.e(TAG, "Error getting fallback screen time", e);
            return 0;
        }
    }

    /**
     * Static method to check if an app is a system app
     */
    private static boolean isSystemAppStatic(Context context, String packageName) {
        try {
            PackageManager packageManager = context.getPackageManager();
            ApplicationInfo appInfo = packageManager.getApplicationInfo(packageName, 0);
            return (appInfo.flags & ApplicationInfo.FLAG_SYSTEM) != 0;
        } catch (PackageManager.NameNotFoundException e) {
            return false;
        }
    }

    /**
     * Static method to get screen time limit that doesn't rely on Plugin context
     */
    public static int getScreenTimeLimitStatic(Context context) {
        try {
            SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
            boolean userHasSetLimit = prefs.getBoolean("userHasSetLimit", false);
            
            // If user has set a limit, always return that value
            if (userHasSetLimit) {
                long limit = prefs.getLong(KEY_SCREEN_TIME_LIMIT, DEFAULT_SCREEN_TIME_LIMIT);
                Log.d(TAG, "getScreenTimeLimitStatic: Retrieved user-set limit: " + limit);
                return (int) limit;
            }
            
            // Only use default value if user hasn't set a limit yet
            long limit = prefs.getLong(KEY_SCREEN_TIME_LIMIT, DEFAULT_SCREEN_TIME_LIMIT);
            Log.d(TAG, "getScreenTimeLimitStatic: Retrieved default limit: " + limit);
            return (int) limit;
        } catch (Exception e) {
            Log.e(TAG, "Error getting screen time limit", e);
            return (int) DEFAULT_SCREEN_TIME_LIMIT;
        }
    }

    /**
     * Static method to set screen time limit
     */
    public static void setScreenTimeLimitStatic(Context context, int limitMinutes) {
        try {
            Log.d(TAG, "setScreenTimeLimitStatic: Setting screen time limit to: " + limitMinutes + " minutes");
            
            // Always store as long
            SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
            
            // Get current limit to check if it's actually changing
            long currentLimit = prefs.getLong(KEY_SCREEN_TIME_LIMIT, DEFAULT_SCREEN_TIME_LIMIT);
            if (currentLimit == limitMinutes) {
                Log.d(TAG, "Screen time limit unchanged, skipping update");
                return;
            }
            
            SharedPreferences.Editor editor = prefs.edit();
            editor.putLong(KEY_SCREEN_TIME_LIMIT, (long) limitMinutes);
            editor.putBoolean("userHasSetLimit", true); // Add flag to indicate user has set a value
            editor.apply();

            // Update widget immediately
            Intent updateIntent = new Intent(context, ScreenTimeWidgetProvider.class);
            updateIntent.setAction("android.appwidget.action.APPWIDGET_UPDATE");
            int[] ids = AppWidgetManager.getInstance(context)
                .getAppWidgetIds(new ComponentName(context, ScreenTimeWidgetProvider.class));
            updateIntent.putExtra(AppWidgetManager.EXTRA_APPWIDGET_IDS, ids);
            context.sendBroadcast(updateIntent);

            // Broadcast update
            Intent broadcastIntent = new Intent("com.screentimereminder.app.APP_USAGE_UPDATE");
            JSONObject updateData = new JSONObject();
            updateData.put("screenTimeLimit", limitMinutes);
            updateData.put("timestamp", System.currentTimeMillis());
            broadcastIntent.putExtra("usageData", updateData.toString());
            context.sendBroadcast(broadcastIntent);
            
            Log.d(TAG, "Screen time limit updated successfully and broadcasts sent");
        } catch (Exception e) {
            Log.e(TAG, "Error setting screen time limit", e);
        }
    }

    /**
     * Static method to update total screen time
     */
    private static void updateTotalScreenTime(Context context, float totalMinutes) {
        try {
            SharedPreferences.Editor editor = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit();
            editor.putFloat(KEY_TOTAL_SCREEN_TIME, totalMinutes);
            editor.putLong(KEY_LAST_UPDATE, System.currentTimeMillis());
            editor.apply();
            Log.d(TAG, "Updated total screen time to: " + totalMinutes + " minutes");
        } catch (Exception e) {
            Log.e(TAG, "Error updating total screen time", e);
        }
    }

    @PluginMethod
    public void getScreenTimeLimit(PluginCall call) {
        try {
            SharedPreferences prefs = getContext().getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
            boolean userHasSetLimit = prefs.getBoolean("userHasSetLimit", false);
            
            // If user has set a limit, always return that value
            if (userHasSetLimit) {
                long limit = prefs.getLong(KEY_SCREEN_TIME_LIMIT, DEFAULT_SCREEN_TIME_LIMIT);
                Log.d(TAG, "getScreenTimeLimit: Retrieved user-set limit: " + limit);
                JSObject ret = new JSObject();
                ret.put("value", limit);
                call.resolve(ret);
                return;
            }
            
            // Only use default value if user hasn't set a limit yet
            long limit = prefs.getLong(KEY_SCREEN_TIME_LIMIT, DEFAULT_SCREEN_TIME_LIMIT);
            Log.d(TAG, "getScreenTimeLimit: Retrieved default limit: " + limit);
            JSObject ret = new JSObject();
            ret.put("value", limit);
            call.resolve(ret);
        } catch (Exception e) {
            Log.e(TAG, "Error getting screen time limit", e);
            call.reject("Failed to get screen time limit: " + e.getMessage());
        }
    }

    private void showNotification(String title, String message) {
        try {
            // Use the static method which includes cooldown check
            showNotification(getContext(), title, message);
        } catch (Exception e) {
            Log.e(TAG, "Error showing notification", e);
        }
    }

    private static void showNotification(Context context, String title, String message) {
        try {
            // Get notification frequency and last notification time
            SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
            long notificationFrequency = prefs.getLong(KEY_NOTIFICATION_FREQUENCY, 5L);
            long lastNotificationTime = prefs.getLong(KEY_LAST_LIMIT_NOTIFICATION, 0);
            long currentTime = System.currentTimeMillis();
            long NOTIFICATION_COOLDOWN = notificationFrequency * 60 * 1000;
            
            // Check if enough time has passed since the last notification
            if (currentTime - lastNotificationTime >= NOTIFICATION_COOLDOWN) {
                NotificationManager notificationManager = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
                
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    NotificationChannel channel = new NotificationChannel(
                        "screen_time_channel",
                        "Screen Time Notifications",
                        NotificationManager.IMPORTANCE_DEFAULT
                    );
                    notificationManager.createNotificationChannel(channel);
                }
                
                NotificationCompat.Builder builder = new NotificationCompat.Builder(context, "screen_time_channel")
                    .setSmallIcon(android.R.drawable.ic_dialog_info)
                    .setContentTitle(title)
                    .setContentText(message)
                    .setStyle(new NotificationCompat.BigTextStyle().bigText(message))
                    .setPriority(NotificationCompat.PRIORITY_DEFAULT)
                    .setAutoCancel(true);
                
                // Use a consistent notification ID (1) for both types of notifications
                // This ensures that new notifications will replace old ones
                notificationManager.notify(1, builder.build());
                
                // Update last notification time
                prefs.edit().putLong(KEY_LAST_LIMIT_NOTIFICATION, currentTime).apply();
                
                Log.d(TAG, "Showing notification: " + title + " - " + message);
            } else {
                Log.d(TAG, "Skipping notification - " + 
                    ((NOTIFICATION_COOLDOWN - (currentTime - lastNotificationTime)) / 60000) + 
                    " minutes until next notification");
            }
        } catch (Exception e) {
            Log.e(TAG, "Error showing notification", e);
        }
    }

    private static float getSafeScreenTime(SharedPreferences prefs) {
        try {
            return prefs.getFloat(KEY_TOTAL_SCREEN_TIME, 0f);
        } catch (ClassCastException e) {
            // If the stored value is an integer, read it and convert to float
            try {
                int intValue = prefs.getInt(KEY_TOTAL_SCREEN_TIME, 0);
                // Convert the integer value to float and store it back
                SharedPreferences.Editor editor = prefs.edit();
                editor.putFloat(KEY_TOTAL_SCREEN_TIME, (float)intValue);
                editor.apply();
                return (float)intValue;
            } catch (Exception e2) {
                Log.e(TAG, "Error migrating screen time value", e2);
                return 0f;
            }
        }
    }

    /**
     * Static method to get notification frequency that doesn't rely on Plugin context
     */
    public static int getNotificationFrequencyStatic(Context context) {
        try {
            SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
            // First try to get it as a long since that's how it's stored
            long frequencyLong = prefs.getLong(KEY_NOTIFICATION_FREQUENCY, 5L); // Default to 5 minutes
            Log.d(TAG, "Getting notification frequency: " + frequencyLong + " minutes");
            // Convert to int since that's what the app expects
            return (int) frequencyLong;
        } catch (Exception e) {
            Log.e(TAG, "Error getting notification frequency", e);
            return 5; // Default to 5 minutes if there's an error
        }
    }

    /**
     * Static method to set notification frequency
     */
    public static void setNotificationFrequencyStatic(Context context, int frequencyMinutes) {
        try {
            Log.d(TAG, "Setting notification frequency to: " + frequencyMinutes + " minutes");
            SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
            SharedPreferences.Editor editor = prefs.edit();
            // Store as long to maintain consistency
            editor.putLong(KEY_NOTIFICATION_FREQUENCY, (long) frequencyMinutes);
            editor.apply();
            Log.d(TAG, "Successfully saved notification frequency");
        } catch (Exception e) {
            Log.e(TAG, "Error setting notification frequency", e);
        }
    }

    /**
     * Static method to check screen time limit and show notifications
     */
    public static void checkScreenTimeLimitStatic(Context context, int totalMinutes) {
        try {
            // Get the current screen time limit
            long screenTimeLimit = getScreenTimeLimitStatic(context);
            Log.d(TAG, "Checking screen time limit - Total: " + totalMinutes + ", Limit: " + screenTimeLimit);

            // Calculate percentage of limit
            float percentOfLimit = (totalMinutes / (float)screenTimeLimit) * 100;
            
            // Get last notification times and notification frequency
            SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
            long lastLimitReached = prefs.getLong(KEY_LAST_LIMIT_NOTIFICATION, 0);
            long lastApproachingLimit = prefs.getLong(KEY_LAST_APPROACHING_NOTIFICATION, 0);
            long currentTime = System.currentTimeMillis();
            
            // Get notification frequency from settings (in minutes)
            long notificationFrequency = prefs.getLong(KEY_NOTIFICATION_FREQUENCY, 5L); // Default to 5 minutes if not set
            // Convert to milliseconds for comparison
            long NOTIFICATION_COOLDOWN = notificationFrequency * 60 * 1000;
            
            Log.d(TAG, "Notification frequency: " + notificationFrequency + " minutes");
            Log.d(TAG, "Time since last limit notification: " + ((currentTime - lastLimitReached) / 60000) + " minutes");
            
            // Check if we need to show notifications
            if (percentOfLimit >= 100) {
                // Check cooldown for limit reached notification
                if (currentTime - lastLimitReached >= NOTIFICATION_COOLDOWN) {
                    // Only show notification if called from background service
                    if (context instanceof Service) {
                        showNotification(context, "Screen Time Limit Reached", 
                            String.format("You have reached your daily limit of %d minutes.\nCurrent usage: %d minutes", 
                                screenTimeLimit, totalMinutes));
                        prefs.edit().putLong(KEY_LAST_LIMIT_NOTIFICATION, currentTime).apply();
                        Log.d(TAG, "Showing limit reached notification after " + notificationFrequency + " minutes");
                    }
                } else {
                    Log.d(TAG, "Skipping limit reached notification - " + 
                        ((NOTIFICATION_COOLDOWN - (currentTime - lastLimitReached)) / 60000) + 
                        " minutes until next notification");
                }
            } else if (percentOfLimit >= 90) {
                // Check cooldown for approaching limit notification
                if (currentTime - lastApproachingLimit >= NOTIFICATION_COOLDOWN) {
                    // Only show notification if called from background service
                    if (context instanceof Service) {
                        showNotification(context, "Approaching Screen Time Limit", 
                            String.format("You have %d minutes remaining.\nCurrent usage: %d minutes\nDaily limit: %d minutes", 
                                Math.round(screenTimeLimit - totalMinutes), totalMinutes, screenTimeLimit));
                        prefs.edit().putLong(KEY_LAST_APPROACHING_NOTIFICATION, currentTime).apply();
                        Log.d(TAG, "Showing approaching limit notification after " + notificationFrequency + " minutes");
                    }
                } else {
                    Log.d(TAG, "Skipping approaching limit notification - " + 
                        ((NOTIFICATION_COOLDOWN - (currentTime - lastApproachingLimit)) / 60000) + 
                        " minutes until next notification");
                }
            }
        } catch (Exception e) {
            Log.e(TAG, "Error checking screen time limit", e);
        }
    }
} 