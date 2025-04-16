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
import android.os.BatteryManager;

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
import java.io.File;

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
    public static final long DEFAULT_NOTIFICATION_FREQUENCY = 5L; // 5 minutes
    private UsageStatsManager usageStatsManager;
    private ScheduledExecutorService scheduler;
    private String currentForegroundApp = "";
    private Handler mainHandler;
    private NotificationService notificationService;
    private SharedPreferences prefs;
    private final ExecutorService backgroundExecutor = Executors.newSingleThreadExecutor();
    private boolean isTracking = false;
    private long lastUpdateTime = 0;
    
    // Add a static lock for synchronization
    private static final Object settingsLock = new Object();
    
    // Add these class variables for caching
    private static final String ICON_CACHE_DIR = "icon_cache";
    private static Map<String, String> iconCache = new HashMap<>();
    private static long iconCacheLastCleanup = 0;
    private static final long CACHE_CLEANUP_INTERVAL = 24 * 60 * 60 * 1000; // 24 hours
    
    // Add battery management variables
    private BatteryManager batteryManager;
    private static final int LOW_BATTERY_THRESHOLD = 15; // 15%
    private static final int CRITICAL_BATTERY_THRESHOLD = 5; // 5%
    private static final long NORMAL_UPDATE_INTERVAL = 60 * 1000; // 1 minute
    private static final long LOW_BATTERY_UPDATE_INTERVAL = 5 * 60 * 1000; // 5 minutes
    private static final long CRITICAL_BATTERY_UPDATE_INTERVAL = 15 * 60 * 1000; // 15 minutes
    private long currentUpdateInterval = NORMAL_UPDATE_INTERVAL;
    
    // Add constants for pub/sub mechanism
    private static final String PUBSUB_CHANNEL = "app_usage_updates";
    private static final String ACTION_USAGE_UPDATE = "com.screentimereminder.app.APP_USAGE_UPDATE";
    private static final String ACTION_BACKGROUND_DETECTED = "com.screentimereminder.app.BACKGROUND_DETECTED";
    
    // Background detection variables
    private static final long BACKGROUND_DETECTION_INTERVAL = 30 * 1000; // 30 seconds
    private static final long BACKGROUND_USAGE_THRESHOLD = 2 * 60 * 1000; // 2 minutes
    private Map<String, AppUsageInfo> appUsageInfo = new HashMap<>();
    private Handler backgroundDetectionHandler;
    private Runnable backgroundDetectionRunnable;

    /**
     * Class to track app foreground/background state
     */
    private static class AppUsageInfo {
        String packageName;
        String appName;
        long lastForegroundTime = 0;
        long lastBackgroundTime = 0;
        long foregroundDuration = 0;
        long backgroundDuration = 0;
        boolean isInForeground = false;
        
        public AppUsageInfo(String packageName, String appName) {
            this.packageName = packageName;
            this.appName = appName;
        }
        
        public JSONObject toJson() {
            try {
                JSONObject json = new JSONObject();
                json.put("packageName", packageName);
                json.put("appName", appName);
                json.put("foregroundDuration", foregroundDuration / 60000.0); // Convert to minutes
                json.put("backgroundDuration", backgroundDuration / 60000.0); // Convert to minutes
                json.put("isInForeground", isInForeground);
                json.put("lastForegroundTime", lastForegroundTime);
                json.put("lastBackgroundTime", lastBackgroundTime);
                return json;
            } catch (Exception e) {
                Log.e("AppUsageTracker", "Error creating JSON for " + packageName, e);
                return new JSONObject();
            }
        }
    }

    public static AppUsageTracker getInstance(Context context) {
        synchronized (settingsLock) {
            if (instance == null) {
                instance = new AppUsageTracker();
                instance.context = context.getApplicationContext();
                instance.loadScreenTimeLimit();
            }
            return instance;
        }
    }

    private void loadScreenTimeLimit() {
        synchronized (settingsLock) {
            try {
                if (context == null) {
                    Log.e(TAG, "Context is null in loadScreenTimeLimit");
                    return;
                }
                
                prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_MULTI_PROCESS);
                if (prefs == null) {
                    Log.e(TAG, "SharedPreferences is null in loadScreenTimeLimit");
                    return;
                }
                
                // Check if user has already set values
                boolean userHasSetLimit = prefs.getBoolean("userHasSetLimit", false);
                String chainId = prefs.getString("lastSettingsChainId", "NO_CHAIN_ID");
                
                // Only use defaults if user hasn't set values yet
                long screenTimeLimit = userHasSetLimit ? 
                    prefs.getLong(KEY_SCREEN_TIME_LIMIT, DEFAULT_SCREEN_TIME_LIMIT) : 
                    DEFAULT_SCREEN_TIME_LIMIT;
                    
                long notificationFrequency = userHasSetLimit ? 
                    prefs.getLong(KEY_NOTIFICATION_FREQUENCY, DEFAULT_NOTIFICATION_FREQUENCY) : 
                    DEFAULT_NOTIFICATION_FREQUENCY;
                
                Log.d(TAG, String.format("[%s] Loading settings - User has set limit: %b", chainId, userHasSetLimit));
                Log.d(TAG, String.format("[%s] - Screen time limit: %d minutes", chainId, screenTimeLimit));
                Log.d(TAG, String.format("[%s] - Notification frequency: %d minutes", chainId, notificationFrequency));
                
                // Save values back only if they're different from what's stored
                SharedPreferences.Editor editor = prefs.edit();
                boolean needsSave = false;
                
                if (prefs.getLong(KEY_SCREEN_TIME_LIMIT, -1) != screenTimeLimit) {
                    editor.putLong(KEY_SCREEN_TIME_LIMIT, screenTimeLimit);
                    needsSave = true;
                }
                
                if (prefs.getLong(KEY_NOTIFICATION_FREQUENCY, -1) != notificationFrequency) {
                    editor.putLong(KEY_NOTIFICATION_FREQUENCY, notificationFrequency);
                    needsSave = true;
                }
                
                if (!prefs.contains("userHasSetLimit")) {
                    editor.putBoolean("userHasSetLimit", userHasSetLimit);
                    needsSave = true;
                }
                
                if (needsSave) {
                    editor.commit();
                    Log.d(TAG, String.format("[%s] Saved initial settings to SharedPreferences", chainId));
                }
                
                // Update widget immediately with current values
                Intent updateIntent = new Intent(context, ScreenTimeWidgetProvider.class);
                updateIntent.setAction("android.appwidget.action.APPWIDGET_UPDATE");
                int[] ids = AppWidgetManager.getInstance(context)
                    .getAppWidgetIds(new ComponentName(context, ScreenTimeWidgetProvider.class));
                updateIntent.putExtra(AppWidgetManager.EXTRA_APPWIDGET_IDS, ids);
                updateIntent.putExtra("totalScreenTime", calculateScreenTime(context));
                updateIntent.putExtra("screenTimeLimit", screenTimeLimit);
                context.sendBroadcast(updateIntent);
                
                Log.d(TAG, String.format("[%s] Widget updated with loaded settings", chainId));
                
            } catch (Exception e) {
                Log.e(TAG, "Error loading screen time limit", e);
                // Only set defaults if prefs is null or empty
                if (prefs != null && !prefs.contains(KEY_SCREEN_TIME_LIMIT)) {
                    SharedPreferences.Editor editor = prefs.edit();
                    editor.putLong(KEY_SCREEN_TIME_LIMIT, DEFAULT_SCREEN_TIME_LIMIT);
                    editor.putLong(KEY_NOTIFICATION_FREQUENCY, DEFAULT_NOTIFICATION_FREQUENCY);
                    editor.putBoolean("userHasSetLimit", false);
                    editor.commit();
                }
            }
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
            
            // Initialize background detection
            backgroundDetectionHandler = new Handler(Looper.getMainLooper());
            backgroundDetectionRunnable = this::trackBackgroundUsage;
            
            // Initialize other components
            this.prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
            this.usageStatsManager = (UsageStatsManager) context.getSystemService(Context.USAGE_STATS_SERVICE);
            this.notificationService = new NotificationService(context);
            
            // Initialize battery manager
            this.batteryManager = (BatteryManager) context.getSystemService(Context.BATTERY_SERVICE);
            
            // Register battery change receiver
            IntentFilter batteryFilter = new IntentFilter(Intent.ACTION_BATTERY_CHANGED);
            context.registerReceiver(batteryChangeReceiver, batteryFilter);
            
            // Load settings
            loadScreenTimeLimit();
            
            // Register broadcast receiver with proper flags for Android 13+
            IntentFilter filter = new IntentFilter();
            filter.addAction(ACTION_USAGE_UPDATE);
            filter.addAction("com.screentimereminder.app.REFRESH_WIDGET");
            filter.addAction(ACTION_BACKGROUND_DETECTED);
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                context.registerReceiver(refreshReceiver, filter, Context.RECEIVER_NOT_EXPORTED);
            } else {
                context.registerReceiver(refreshReceiver, filter);
            }
            
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
            
            // Unregister battery receiver
            try {
                getContext().unregisterReceiver(batteryChangeReceiver);
            } catch (Exception e) {
                Log.e(TAG, "Error unregistering battery receiver", e);
            }
            
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
            
            // Start background usage detection
            startBackgroundDetection();
            
            // Determine initial update interval based on battery level
            adjustTrackingForBatteryLevel();
            
            // Start periodic updates with battery-aware scheduling
            if (scheduler == null || scheduler.isShutdown()) {
                scheduler = Executors.newSingleThreadScheduledExecutor();
                scheduler.scheduleAtFixedRate(() -> {
                    try {
                        if (isTracking) {
                            updateAppUsage();
                            // Readjust interval for next execution based on current battery
                            adjustSchedulerInterval();
                        }
                    } catch (Exception e) {
                        Log.e(TAG, "Error in periodic update", e);
                    }
                }, 0, currentUpdateInterval, TimeUnit.MILLISECONDS);
            }

            call.resolve();
        } catch (Exception e) {
            Log.e(TAG, "Error starting tracking", e);
            call.reject("Failed to start tracking: " + e.getMessage());
        }
    }
    
    // Battery change receiver to detect battery level changes
    private final BroadcastReceiver batteryChangeReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (intent.getAction().equals(Intent.ACTION_BATTERY_CHANGED)) {
                int level = intent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1);
                int scale = intent.getIntExtra(BatteryManager.EXTRA_SCALE, -1);
                
                if (level != -1 && scale != -1) {
                    int batteryPct = (int) ((level / (float) scale) * 100);
                    Log.d(TAG, "Battery changed: " + batteryPct + "%");
                    
                    // Adjust tracking frequency based on battery level
                    adjustTrackingForBatteryLevel(batteryPct);
                }
            }
        }
    };
    
    /**
     * Adjust tracking frequency based on battery level
     */
    private void adjustTrackingForBatteryLevel() {
        if (batteryManager == null) {
            // Get current battery level if batteryManager is available
            batteryManager = (BatteryManager) getContext().getSystemService(Context.BATTERY_SERVICE);
        }
        
        if (batteryManager != null) {
            int batteryLevel = batteryManager.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY);
            adjustTrackingForBatteryLevel(batteryLevel);
        }
    }
    
    /**
     * Adjust tracking frequency based on specific battery level
     */
    private void adjustTrackingForBatteryLevel(int batteryLevel) {
        // Check if device is charging
        boolean isCharging = isDeviceCharging();
        
        if (!isCharging) {
            if (batteryLevel <= CRITICAL_BATTERY_THRESHOLD) {
                Log.d(TAG, "Critical battery level: " + batteryLevel + "%. Reducing tracking frequency significantly.");
                currentUpdateInterval = CRITICAL_BATTERY_UPDATE_INTERVAL;
            } else if (batteryLevel <= LOW_BATTERY_THRESHOLD) {
                Log.d(TAG, "Low battery level: " + batteryLevel + "%. Reducing tracking frequency.");
                currentUpdateInterval = LOW_BATTERY_UPDATE_INTERVAL;
            } else {
                Log.d(TAG, "Normal battery level: " + batteryLevel + "%. Using standard tracking frequency.");
                currentUpdateInterval = NORMAL_UPDATE_INTERVAL;
            }
        } else {
            // Device is charging, use normal frequency
            Log.d(TAG, "Device is charging. Using standard tracking frequency.");
            currentUpdateInterval = NORMAL_UPDATE_INTERVAL;
        }
        
        // Adjust scheduler if it's already running
        adjustSchedulerInterval();
    }
    
    /**
     * Check if device is currently charging
     */
    private boolean isDeviceCharging() {
        if (batteryManager == null) {
            batteryManager = (BatteryManager) getContext().getSystemService(Context.BATTERY_SERVICE);
        }
        
        if (batteryManager != null) {
            return batteryManager.isCharging();
        }
        
        // Fallback to intent if BatteryManager is unavailable
        IntentFilter ifilter = new IntentFilter(Intent.ACTION_BATTERY_CHANGED);
        Intent batteryStatus = getContext().registerReceiver(null, ifilter);
        if (batteryStatus != null) {
            int status = batteryStatus.getIntExtra(BatteryManager.EXTRA_STATUS, -1);
            return status == BatteryManager.BATTERY_STATUS_CHARGING || 
                   status == BatteryManager.BATTERY_STATUS_FULL;
        }
        
        return false;
    }
    
    /**
     * Adjust scheduler interval based on current battery level
     */
    private void adjustSchedulerInterval() {
        if (scheduler != null && !scheduler.isShutdown()) {
            try {
                scheduler.shutdown();
                scheduler = Executors.newSingleThreadScheduledExecutor();
                scheduler.scheduleAtFixedRate(() -> {
                    try {
                        if (isTracking) {
                            updateAppUsage();
                        }
                    } catch (Exception e) {
                        Log.e(TAG, "Error in periodic update", e);
                    }
                }, 0, currentUpdateInterval, TimeUnit.MILLISECONDS);
                
                Log.d(TAG, "Scheduler adjusted to interval: " + (currentUpdateInterval / 1000) + " seconds");
            } catch (Exception e) {
                Log.e(TAG, "Error adjusting scheduler interval", e);
            }
        }
    }
    
    private void updateAppUsage() {
        try {
            // Get current usage stats
            UsageStatsManager usageStatsManager = (UsageStatsManager) getContext().getSystemService(Context.USAGE_STATS_SERVICE);
            if (usageStatsManager != null) {
                long dayStartTime = getStartOfDay();
                long endTime = System.currentTimeMillis();
                
                // Query usage events for more accurate calculation
                UsageEvents usageEvents = usageStatsManager.queryEvents(dayStartTime, endTime);
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
            
            // Stop background detection
            stopBackgroundDetection();
            
            isTracking = false;
            call.resolve();
        } catch (Exception e) {
            Log.e(TAG, "Error stopping tracking", e);
            call.reject("Failed to stop tracking: " + e.getMessage(), e);
        }
    }
    
    /**
     * Start background usage detection
     */
    private void startBackgroundDetection() {
        // Schedule the background detection
        backgroundDetectionHandler.postDelayed(backgroundDetectionRunnable, BACKGROUND_DETECTION_INTERVAL);
        Log.d(TAG, "Started background usage detection");
    }
    
    /**
     * Stop background usage detection
     */
    private void stopBackgroundDetection() {
        backgroundDetectionHandler.removeCallbacks(backgroundDetectionRunnable);
        Log.d(TAG, "Stopped background usage detection");
    }
    
    /**
     * Track background app usage
     */
    private void trackBackgroundUsage() {
        if (!isTracking || usageStatsManager == null) {
            return;
        }
        
        try {
            long now = System.currentTimeMillis();
            long queryStart = now - BACKGROUND_DETECTION_INTERVAL * 2; // Look back further to catch events
            
            // Get usage events
            UsageEvents events = usageStatsManager.queryEvents(queryStart, now);
            if (events == null) {
                Log.e(TAG, "UsageEvents is null");
                return;
            }
            
            // Process usage events
            UsageEvents.Event event = new UsageEvents.Event();
            Map<String, Long> lastEventTimeMap = new HashMap<>();
            Map<String, AppUsageInfo> updatedApps = new HashMap<>();
            
            while (events.hasNextEvent()) {
                events.getNextEvent(event);
                String packageName = event.getPackageName();
                
                // Skip our own app
                if (packageName.equals(getContext().getPackageName())) {
                    continue;
                }
                
                // Skip system apps
                if (isSystemApp(getContext(), packageName)) {
                    continue;
                }
                
                // Get or create AppUsageInfo
                AppUsageInfo info = appUsageInfo.get(packageName);
                if (info == null) {
                    String appName = getAppName(packageName);
                    info = new AppUsageInfo(packageName, appName);
                    appUsageInfo.put(packageName, info);
                }
                
                if (event.getEventType() == UsageEvents.Event.MOVE_TO_FOREGROUND) {
                    if (!info.isInForeground) {
                        // App moving to foreground
                        info.isInForeground = true;
                        info.lastForegroundTime = event.getTimeStamp();
                        
                        // If it was in background before, calculate background time
                        if (info.lastBackgroundTime > 0) {
                            long bgTime = event.getTimeStamp() - info.lastBackgroundTime;
                            if (bgTime > BACKGROUND_USAGE_THRESHOLD) {
                                // Only count significant background time
                                info.backgroundDuration += bgTime;
                                updatedApps.put(packageName, info);
                                Log.d(TAG, packageName + " was in background for " + (bgTime / 1000) + " seconds");
                            }
                        }
                    }
                } else if (event.getEventType() == UsageEvents.Event.MOVE_TO_BACKGROUND) {
                    if (info.isInForeground) {
                        // App moving to background
                        info.isInForeground = false;
                        info.lastBackgroundTime = event.getTimeStamp();
                        
                        // Calculate foreground duration
                        if (info.lastForegroundTime > 0) {
                            long fgTime = event.getTimeStamp() - info.lastForegroundTime;
                            info.foregroundDuration += fgTime;
                            updatedApps.put(packageName, info);
                            Log.d(TAG, packageName + " was in foreground for " + (fgTime / 1000) + " seconds");
                        }
                    }
                }
            }
            
            // Check for apps still in foreground
            for (AppUsageInfo info : appUsageInfo.values()) {
                if (info.isInForeground && info.lastForegroundTime > 0) {
                    long fgTime = now - info.lastForegroundTime;
                    // Track ongoing foreground usage
                    Log.d(TAG, info.packageName + " is still in foreground for " + (fgTime / 1000) + " seconds");
                    
                    // If significant time has passed, update foreground duration and notify
                    if (fgTime > 60000) { // 1 minute
                        info.foregroundDuration += fgTime;
                        info.lastForegroundTime = now; // Reset the start time
                        updatedApps.put(info.packageName, info);
                    }
                }
            }
            
            // Send updates for changed apps
            if (!updatedApps.isEmpty()) {
                publishBackgroundUsageUpdates(updatedApps);
            }
            
        } catch (Exception e) {
            Log.e(TAG, "Error tracking background usage", e);
        } finally {
            // Schedule next check
            backgroundDetectionHandler.postDelayed(backgroundDetectionRunnable, BACKGROUND_DETECTION_INTERVAL);
        }
    }
    
    /**
     * Publish background usage updates
     */
    private void publishBackgroundUsageUpdates(Map<String, AppUsageInfo> updatedApps) {
        try {
            // Create JSON array of updated apps
            JSONArray appsArray = new JSONArray();
            for (AppUsageInfo info : updatedApps.values()) {
                appsArray.put(info.toJson());
            }
            
            // Create update object
            JSONObject updateData = new JSONObject();
            updateData.put("type", "background_usage");
            updateData.put("timestamp", System.currentTimeMillis());
            updateData.put("apps", appsArray);
            
            // Broadcast update
            Intent intent = new Intent(ACTION_BACKGROUND_DETECTED);
            intent.putExtra("data", updateData.toString());
            getContext().sendBroadcast(intent);
            
            // Notify Capacitor
            JSObject jsData = new JSObject();
            jsData.put("data", updateData.toString());
            notifyListeners("backgroundUsage", jsData);
            
            Log.d(TAG, "Published background usage updates for " + updatedApps.size() + " apps");
        } catch (Exception e) {
            Log.e(TAG, "Error publishing background usage updates", e);
        }
    }

    /**
     * More efficient broadcasting of usage data
     */
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
            data.put("isImportant", true);  // Mark as important update

            // Use more efficient broadcast mechanism
            publishUsageUpdate(data);
            
            Log.d(TAG, "Broadcasting usage data: " + data.toString());
        } catch (Exception e) {
            Log.e(TAG, "Error broadcasting usage data", e);
        }
    }
    
    /**
     * Publish usage update to all listeners
     */
    private void publishUsageUpdate(JSONObject data) {
        try {
            // Broadcast only important updates system-wide
            boolean isImportant = data.optBoolean("isImportant", false);
            if (isImportant) {
                Intent broadcastIntent = new Intent(ACTION_USAGE_UPDATE);
                broadcastIntent.putExtra("usageData", data.toString());
                getContext().sendBroadcast(broadcastIntent);
            }
            
            // Always notify Capacitor
            JSObject jsData = new JSObject();
            jsData.put("data", data.toString());
            notifyListeners("appUsageUpdate", jsData);
            
            Log.d(TAG, "Published usage update: " + (isImportant ? "important" : "regular"));
        } catch (Exception e) {
            Log.e(TAG, "Error publishing usage update", e);
        }
    }
    
    /**
     * Get background usage data for apps
     */
    @PluginMethod
    public void getBackgroundUsageData(PluginCall call) {
        try {
            JSONObject result = new JSONObject();
            JSONArray appsArray = new JSONArray();
            
            for (AppUsageInfo info : appUsageInfo.values()) {
                appsArray.put(info.toJson());
            }
            
            result.put("apps", appsArray);
            result.put("timestamp", System.currentTimeMillis());
            
            JSObject jsResult = new JSObject();
            jsResult.put("data", result.toString());
            call.resolve(jsResult);
        } catch (Exception e) {
            Log.e(TAG, "Error getting background usage data", e);
            call.reject("Failed to get background usage data: " + e.getMessage());
        }
    }
    
    /**
     * Get app usage data for a specific time range
     * 
     * @param call Capacitor plugin call with optional parameters:
     *             - startTime: timestamp in milliseconds for query start
     *             - endTime: timestamp in milliseconds for query end
     *             - includeIcons: boolean, whether to include app icons (default: true)
     *             - minTimeThreshold: minimum time in minutes to include an app (default: 0)
     *             - filterPackages: array of package names to include or exclude
     *             - filterType: "include" or "exclude" for the filterPackages (default: "exclude")
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
                    // Default time range: start of day to now
                    long startTime = getStartOfDay();
                    long endTime = System.currentTimeMillis();
                    
                    // Parse options from the call data
                    JSObject options = call.getData();
                    boolean includeIcons = true;
                    double minTimeThreshold = 0.0; // in minutes
                    Set<String> filterPackages = new HashSet<>();
                    boolean isIncludeFilter = false;
                    
                    if (options != null) {
                        // Time range options
                        if (options.has("startTime")) {
                            startTime = options.getLong("startTime");
                        }
                        if (options.has("endTime")) {
                            endTime = options.getLong("endTime");
                        }
                        
                        // Icon option to reduce payload size if needed
                        if (options.has("includeIcons")) {
                            includeIcons = options.getBoolean("includeIcons");
                        }
                        
                        // Time threshold to filter out briefly used apps
                        if (options.has("minTimeThreshold")) {
                            minTimeThreshold = options.getDouble("minTimeThreshold");
                        }
                        
                        // Handle package filtering
                        if (options.has("filterPackages") && options.has("filterType")) {
                            try {
                                JSONArray filterArray = new JSONArray(options.getString("filterPackages", "[]"));
                                if (filterArray != null) {
                                    for (int i = 0; i < filterArray.length(); i++) {
                                        filterPackages.add(filterArray.getString(i));
                                    }
                                }
                                
                                String filterType = options.getString("filterType", "exclude");
                                isIncludeFilter = "include".equalsIgnoreCase(filterType);
                                
                                Log.d(TAG, String.format("Using %s filter for %d packages", 
                                    isIncludeFilter ? "include" : "exclude", filterPackages.size()));
                            } catch (Exception e) {
                                Log.e(TAG, "Error parsing filter options", e);
                            }
                        }
                    }
                    
                    Log.d(TAG, String.format("Querying app usage from %s to %s", 
                        new Date(startTime).toString(), new Date(endTime).toString()));
                    
                    final boolean finalIncludeIcons = includeIcons;
                    final double finalMinTimeThreshold = minTimeThreshold;
                    final Set<String> finalFilterPackages = filterPackages;
                    final boolean finalIsIncludeFilter = isIncludeFilter;
                    
                    // Use queryUsageStats with try-catch for API compatibility
                    List<UsageStats> stats;
                    try {
                        stats = usageStatsManager.queryUsageStats(
                            UsageStatsManager.INTERVAL_DAILY, startTime, endTime
                        );
                        
                        if (stats == null || stats.isEmpty()) {
                            // Try with INTERVAL_BEST if daily doesn't work
                            stats = usageStatsManager.queryUsageStats(
                                UsageStatsManager.INTERVAL_BEST, startTime, endTime
                            );
                        }
                    } catch (Exception e) {
                        Log.e(TAG, "Error querying usage stats", e);
                        stats = new ArrayList<>();
                    }
                    
                    if (stats == null || stats.isEmpty()) {
                        Log.e(TAG, "Usage stats query returned null or empty");
                        mainHandler.post(() -> {
                            call.reject("Failed to get usage stats, possibly due to permission issues");
                        });
                        return;
                    }
                    
                    // Process stats and create response
                    JSONObject result = new JSONObject();
                    JSONArray appsArray = new JSONArray();
                    long totalScreenTime = 0;
                    
                    // Pre-calculate package names to skip (only our own app)
                    String ourPackageName = getContext().getPackageName();
                    
                    // Use hash map for better performance with larger datasets
                    Map<String, JSONObject> appDataMap = new HashMap<>();
                    
                    // Process all stats first
                    for (UsageStats stat : stats) {
                        String packageName = stat.getPackageName();
                        
                        // First filter: Skip our own app
                        if (packageName.equals(ourPackageName)) {
                            continue;
                        }
                        
                        // Second filter: Check against user-provided filter list
                        if (!finalFilterPackages.isEmpty()) {
                            boolean inFilterList = finalFilterPackages.contains(packageName);
                            if ((finalIsIncludeFilter && !inFilterList) || (!finalIsIncludeFilter && inFilterList)) {
                                continue;
                            }
                        }
                        
                        // Third filter: Skip most system apps but keep browsers and email
                        if (isSystemApp(getContext(), packageName)) {
                            continue;
                        }
                        
                        long timeInForeground = stat.getTotalTimeInForeground();
                        double timeInMinutes = timeInForeground / 60000.0;
                        
                        // Skip apps that weren't used enough
                        if (timeInMinutes < finalMinTimeThreshold) {
                            continue;
                        }
                        
                        if (timeInForeground > 0) {
                            totalScreenTime += timeInForeground;
                            
                            try {
                                String appName = getAppName(packageName);
                                String category = getCategoryForApp(appName);
                                
                                JSONObject appData = new JSONObject();
                                appData.put("name", appName);
                                appData.put("packageName", packageName);
                                appData.put("time", timeInMinutes);
                                appData.put("lastUsed", stat.getLastTimeUsed());
                                appData.put("category", category);
                                
                                // Only get icons if requested (can save bandwidth)
                                if (finalIncludeIcons) {
                                    try {
                                        // Try to get icon with better error handling
                                        String iconBase64 = getAppIconBase64(packageName);
                                        if (iconBase64 != null && !iconBase64.isEmpty()) {
                                            appData.put("icon", iconBase64);
                                            Log.d(TAG, "Successfully retrieved icon for " + packageName + " from events");
                                        } else {
                                            // Use default placeholder if we couldn't get the icon
                                            appData.put("icon", "data:image/png;base64,iVBORw0KGgoAAAANSUhEUgAAAJAAAACQBAMAAAAVaP+LAAAAHlBMVEX///8AAABSUlL09PSjo6M7OzshISGDg4O3t7dpaWmZfZ3LAAABzUlEQVRo3u3aS27kIBTG8XAMS2KP7Ygu+yTsf0UjRVGUDlWBn47a/6+EeHwfsK/NbbmzrjsoQIAAAQIECBAgQIAAAQIECBAgQIAAAfoIUDf3g/Ufo7s78vBoXOD0fc4GptfG2eDQmIfD2aB8bczBEVHV+Dna5XfRP44Zj2I52CMuahsrR7usNl6O1q02Xo6/q42Xg9eFOKKXg8cXiBzx5UD22NGObk8HmR072lF/gTNyNDrHjXXQCQ4yO3Z2EJ3iILNjZgeNrSsctOioo6gTHDPqaGeHDwf9rCNNB7dbnYfD0UHbHfSM59CjjtY7+HVoWh7x4LscPKOjzg6SOmjb9oLaF6jt4DnqaJODxA5a7SDxPLTSUeUOOm/bL9C27Q9o2/YHtG3/B2rb/oa2bbMj1kFYB0sd0Q4SO6IdJHZEO0jqCHeQ1BHtoA9ylNTRxDpI6hhiHTTZUcUOmuwIdhDrIKmDWAdJHcQ6SOoYYx0kdTSxDpI6hlgHzXZUsYPmOIIdxDpI6iDWQVIHsQ6SOsZYB0kdTayDpI4h1kF3+XlsYB0kdRDrIKmDpA5iHSR1EBvWpA5iQ5TUQUIHCR0kdBjHvwAAAP//m1pNlCv43RMAAAAASUVORK5CYII=");
                                            Log.d(TAG, "Using placeholder icon for " + packageName + " from events");
                                        }
                                    } catch (Exception e) {
                                        // Use a fallback placeholder on error
                                        appData.put("icon", "data:image/png;base64,iVBORw0KGgoAAAANSUhEUgAAAJAAAACQBAMAAAAVaP+LAAAAHlBMVEX///8AAABSUlL09PSjo6M7OzshISGDg4O3t7dpaWmZfZ3LAAABzUlEQVRo3u3aS27kIBTG8XAMS2KP7Ygu+yTsf0UjRVGUDlWBn47a/6+EeHwfsK/NbbmzrjsoQIAAAQIECBAgQIAAAQIECBAgQIAAAfoIUDf3g/Ufo7s78vBoXOD0fc4GptfG2eDQmIfD2aB8bczBEVHV+Dna5XfRP44Zj2I52CMuahsrR7usNl6O1q02Xo6/q42Xg9eFOKKXg8cXiBzx5UD22NGObk8HmR072lF/gTNyNDrHjXXQCQ4yO3Z2EJ3iILNjZgeNrSsctOioo6gTHDPqaGeHDwf9rCNNB7dbnYfD0UHbHfSM59CjjtY7+HVoWh7x4LscPKOjzg6SOmjb9oLaF6jt4DnqaJODxA5a7SDxPLTSUeUOOm/bL9C27Q9o2/YHtG3/B2rb/oa2bbMj1kFYB0sd0Q4SO6IdJHZEO0jqCHeQ1BHtoA9ylNTRxDpI6hhiHTTZUcUOmuwIdhDrIKmDWAdJHcQ6SOoYYx0kdTSxDpI6hlgHzXZUsYPmOIIdxDpI6iDWQVIHsQ6SOsZYB0kdTayDpI4h1kF3+XlsYB0kdRDrIKmDpA5iHSR1EBvWpA5iQ5TUQUIHCR0kdBjHvwAAAP//m1pNlCv43RMAAAAASUVORK5CYII=");
                                        Log.e(TAG, "Error retrieving icon for " + packageName + " from events: " + e.getMessage());
                                    }
                                } else {
                                    appData.put("icon", "");
                                }
                                
                                appsArray.put(appData);
                                appDataMap.put(packageName, appData);
                            } catch (Exception e) {
                                Log.e(TAG, "Error processing app " + packageName, e);
                            }
                        }
                    }
                    
                    // If we have insufficient data from UsageStats, try querying events for more accuracy
                    if (totalScreenTime == 0 || appDataMap.isEmpty()) {
                        try {
                            UsageEvents events = usageStatsManager.queryEvents(startTime, endTime);
                            if (events != null) {
                                collectDataFromEvents(events, appDataMap, finalIncludeIcons, finalMinTimeThreshold, 
                                    finalFilterPackages, finalIsIncludeFilter, ourPackageName);
                                
                                // Recalculate total time after events processing
                                totalScreenTime = 0;
                                for (JSONObject appData : appDataMap.values()) {
                                    totalScreenTime += (long)(appData.getDouble("time") * 60000);
                                }
                                
                                // Rebuild the array with updated data
                                appsArray = new JSONArray();
                                for (JSONObject appData : appDataMap.values()) {
                                    appsArray.put(appData);
                                }
                            }
                        } catch (Exception e) {
                            Log.e(TAG, "Error querying events", e);
                        }
                    }
                    
                    result.put("apps", appsArray);
                    result.put("totalScreenTime", totalScreenTime / 60000.0);
                    result.put("timestamp", System.currentTimeMillis());
                    result.put("timeRange", new JSONObject()
                        .put("startTime", startTime)
                        .put("endTime", endTime));
                    
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
     * Helper method to collect app usage data from UsageEvents
     * This can be more accurate than UsageStats in some cases
     */
    private void collectDataFromEvents(UsageEvents events, Map<String, JSONObject> appDataMap, 
            boolean includeIcons, double minTimeThreshold, Set<String> filterPackages, 
            boolean isIncludeFilter, String ourPackageName) throws JSONException {
        
        UsageEvents.Event event = new UsageEvents.Event();
        Map<String, Long> lastEventTimeMap = new HashMap<>();
        Map<String, Double> appTimeMap = new HashMap<>();
        
        while (events.hasNextEvent()) {
            events.getNextEvent(event);
            String packageName = event.getPackageName();
            
            // Skip our own app
            if (packageName.equals(ourPackageName)) {
                continue;
            }
            
            // Apply filters
            if (!filterPackages.isEmpty()) {
                boolean inFilterList = filterPackages.contains(packageName);
                if ((isIncludeFilter && !inFilterList) || (!isIncludeFilter && inFilterList)) {
                    continue;
                }
            }
            
            // Skip system apps but keep browsers and email
            if (isSystemApp(getContext(), packageName)) {
                continue;
            }
            
            if (event.getEventType() == UsageEvents.Event.MOVE_TO_FOREGROUND) {
                lastEventTimeMap.put(packageName, event.getTimeStamp());
            } else if (event.getEventType() == UsageEvents.Event.MOVE_TO_BACKGROUND) {
                Long lastTime = lastEventTimeMap.get(packageName);
                if (lastTime != null) {
                    double timeSpentMinutes = (event.getTimeStamp() - lastTime) / 60000.0;
                    Double currentTime = appTimeMap.getOrDefault(packageName, 0.0);
                    appTimeMap.put(packageName, currentTime + timeSpentMinutes);
                    lastEventTimeMap.remove(packageName);
                }
            }
        }
        
        // Handle apps still in foreground at query end time
        long endTime = System.currentTimeMillis();
        for (Map.Entry<String, Long> entry : lastEventTimeMap.entrySet()) {
            String packageName = entry.getKey();
            long lastTime = entry.getValue();
            double timeSpentMinutes = (endTime - lastTime) / 60000.0;
            Double currentTime = appTimeMap.getOrDefault(packageName, 0.0);
            appTimeMap.put(packageName, currentTime + timeSpentMinutes);
        }
        
        // Add events data to the result map
        for (Map.Entry<String, Double> entry : appTimeMap.entrySet()) {
            String packageName = entry.getKey();
            double timeInMinutes = entry.getValue();
            
            // Skip apps that weren't used enough
            if (timeInMinutes < minTimeThreshold) {
                continue;
            }
            
            // Update existing app data or create new entry
            JSONObject appData = appDataMap.get(packageName);
            if (appData == null) {
                try {
                    String appName = getAppName(packageName);
                    String category = getCategoryForApp(appName);
                    
                    appData = new JSONObject();
                    appData.put("name", appName);
                    appData.put("packageName", packageName);
                    appData.put("time", timeInMinutes);
                    appData.put("lastUsed", System.currentTimeMillis());
                    appData.put("category", category);
                    
                    if (includeIcons) {
                        try {
                            // Try to get icon with better error handling
                            String iconBase64 = getAppIconBase64(packageName);
                            if (iconBase64 != null && !iconBase64.isEmpty()) {
                                appData.put("icon", iconBase64);
                                Log.d(TAG, "Successfully retrieved icon for " + packageName + " from events");
                            } else {
                                // Use default placeholder if we couldn't get the icon
                                appData.put("icon", "data:image/png;base64,iVBORw0KGgoAAAANSUhEUgAAAJAAAACQBAMAAAAVaP+LAAAAHlBMVEX///8AAABSUlL09PSjo6M7OzshISGDg4O3t7dpaWmZfZ3LAAABzUlEQVRo3u3aS27kIBTG8XAMS2KP7Ygu+yTsf0UjRVGUDlWBn47a/6+EeHwfsK/NbbmzrjsoQIAAAQIECBAgQIAAAQIECBAgQIAAAfoIUDf3g/Ufo7s78vBoXOD0fc4GptfG2eDQmIfD2aB8bczBEVHV+Dna5XfRP44Zj2I52CMuahsrR7usNl6O1q02Xo6/q42Xg9eFOKKXg8cXiBzx5UD22NGObk8HmR072lF/gTNyNDrHjXXQCQ4yO3Z2EJ3iILNjZgeNrSsctOioo6gTHDPqaGeHDwf9rCNNB7dbnYfD0UHbHfSM59CjjtY7+HVoWh7x4LscPKOjzg6SOmjb9oLaF6jt4DnqaJODxA5a7SDxPLTSUeUOOm/bL9C27Q9o2/YHtG3/B2rb/oa2bbMj1kFYB0sd0Q4SO6IdJHZEO0jqCHeQ1BHtoA9ylNTRxDpI6hhiHTTZUcUOmuwIdhDrIKmDWAdJHcQ6SOoYYx0kdTSxDpI6hlgHzXZUsYPmOIIdxDpI6iDWQVIHsQ6SOsZYB0kdTayDpI4h1kF3+XlsYB0kdRDrIKmDpA5iHSR1EBvWpA5iQ5TUQUIHCR0kdBjHvwAAAP//m1pNlCv43RMAAAAASUVORK5CYII=");
                                Log.d(TAG, "Using placeholder icon for " + packageName + " from events");
                            }
                        } catch (Exception e) {
                            // Use a fallback placeholder on error
                            appData.put("icon", "data:image/png;base64,iVBORw0KGgoAAAANSUhEUgAAAJAAAACQBAMAAAAVaP+LAAAAHlBMVEX///8AAABSUlL09PSjo6M7OzshISGDg4O3t7dpaWmZfZ3LAAABzUlEQVRo3u3aS27kIBTG8XAMS2KP7Ygu+yTsf0UjRVGUDlWBn47a/6+EeHwfsK/NbbmzrjsoQIAAAQIECBAgQIAAAQIECBAgQIAAAfoIUDf3g/Ufo7s78vBoXOD0fc4GptfG2eDQmIfD2aB8bczBEVHV+Dna5XfRP44Zj2I52CMuahsrR7usNl6O1q02Xo6/q42Xg9eFOKKXg8cXiBzx5UD22NGObk8HmR072lF/gTNyNDrHjXXQCQ4yO3Z2EJ3iILNjZgeNrSsctOioo6gTHDPqaGeHDwf9rCNNB7dbnYfD0UHbHfSM59CjjtY7+HVoWh7x4LscPKOjzg6SOmjb9oLaF6jt4DnqaJODxA5a7SDxPLTSUeUOOm/bL9C27Q9o2/YHtG3/B2rb/oa2bbMj1kFYB0sd0Q4SO6IdJHZEO0jqCHeQ1BHtoA9ylNTRxDpI6hhiHTTZUcUOmuwIdhDrIKmDWAdJHcQ6SOoYYx0kdTSxDpI6hlgHzXZUsYPmOIIdxDpI6iDWQVIHsQ6SOsZYB0kdTayDpI4h1kF3+XlsYB0kdRDrIKmDpA5iHSR1EBvWpA5iQ5TUQUIHCR0kdBjHvwAAAP//m1pNlCv43RMAAAAASUVORK5CYII=");
                            Log.e(TAG, "Error retrieving icon for " + packageName + " from events: " + e.getMessage());
                        }
                    } else {
                        appData.put("icon", "");
                    }
                    
                    appDataMap.put(packageName, appData);
                } catch (Exception e) {
                    Log.e(TAG, "Error processing app from events " + packageName, e);
                }
            } else {
                // Use the maximum time between UsageStats and UsageEvents
                double currentTime = appData.getDouble("time");
                if (timeInMinutes > currentTime) {
                    appData.put("time", timeInMinutes);
                }
            }
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
            // Get the parameters from the call
            float totalTime = call.getFloat("totalTime", 0f);
            
            // Get current settings from SettingsManager for accurate values
            SettingsManager settingsManager = SettingsManager.getInstance(context);
            long screenTimeLimit = settingsManager.getScreenTimeLimit();
            long notificationFrequency = settingsManager.getNotificationFrequency();
            
            Log.d(TAG, "Checking screen time limit with current settings:");
            Log.d(TAG, String.format("- Total screen time: %.2f minutes", totalTime));
            Log.d(TAG, String.format("- Screen time limit: %d minutes", screenTimeLimit));
            Log.d(TAG, String.format("- Notification frequency: %d minutes", notificationFrequency));
            
            // Check screen time limit with current settings
            checkScreenTimeLimitStatic(context, Math.round(totalTime), (int)notificationFrequency);

            // Return current values to Capacitor
            JSObject ret = new JSObject();
            ret.put("totalTime", totalTime);
            ret.put("limit", screenTimeLimit);
            ret.put("notificationFrequency", notificationFrequency);
            call.resolve(ret);
            
        } catch (Exception e) {
            Log.e(TAG, "Error checking screen time limit", e);
            call.reject("Failed to check screen time limit: " + e.getMessage());
        }
    }

    private void broadcastSettingsUpdate(float totalTime, long screenTimeLimit, long notificationFrequency) {
        try {
            // Create update data
            JSONObject updateData = new JSONObject();
            updateData.put("action", "UPDATE_SETTINGS");
            updateData.put("totalScreenTime", totalTime);
            updateData.put("screenTimeLimit", screenTimeLimit);
            updateData.put("notificationFrequency", notificationFrequency);
            updateData.put("timestamp", System.currentTimeMillis());

            // Broadcast to all components
            Intent updateIntent = new Intent(SettingsConstants.ACTION_USAGE_UPDATE);
            updateIntent.putExtra("usageData", updateData.toString());
            context.sendBroadcast(updateIntent);

            // Update widget
            Intent widgetIntent = new Intent(context, ScreenTimeWidgetProvider.class);
            widgetIntent.setAction(AppWidgetManager.ACTION_APPWIDGET_UPDATE);
            int[] ids = AppWidgetManager.getInstance(context)
                .getAppWidgetIds(new ComponentName(context, ScreenTimeWidgetProvider.class));
            widgetIntent.putExtra(AppWidgetManager.EXTRA_APPWIDGET_IDS, ids);
            widgetIntent.putExtra("totalScreenTime", totalTime);
            widgetIntent.putExtra("screenTimeLimit", screenTimeLimit);
            context.sendBroadcast(widgetIntent);

            Log.d(TAG, "Broadcast settings update to all components");
        } catch (Exception e) {
            Log.e(TAG, "Error broadcasting settings update", e);
        }
    }

    /**
     * Static method to check screen time limit and show notifications
     * This consolidated version combines the best features of both previous implementations
     */
    public static void checkScreenTimeLimitStatic(Context context, int totalMinutes, int notificationFrequency) {
        try {
            // Get fresh settings from SettingsManager for consistency
            SettingsManager settingsManager = SettingsManager.getInstance(context);
            long screenTimeLimit = settingsManager.getScreenTimeLimit();
            long currentNotificationFrequency = settingsManager.getNotificationFrequency();
            boolean userHasSetLimit = settingsManager.hasUserSetLimit();
            
            // Use the frequency from SettingsManager instead of parameter for consistency
            notificationFrequency = (int)currentNotificationFrequency;
            
            Log.d(TAG, String.format("Static check with settings from SettingsManager:"));
            Log.d(TAG, String.format("- Total minutes: %d", totalMinutes));
            Log.d(TAG, String.format("- Screen time limit: %d minutes", screenTimeLimit));
            Log.d(TAG, String.format("- Notification frequency: %d minutes", notificationFrequency));
            Log.d(TAG, String.format("- User has set limit: %b", userHasSetLimit));
            
            // Calculate percentage of limit
            float percentOfLimit = (totalMinutes / (float)screenTimeLimit) * 100;
            
            // Get last notification times with MODE_MULTI_PROCESS for consistency
            SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_MULTI_PROCESS);
            long lastLimitReached = prefs.getLong(KEY_LAST_LIMIT_NOTIFICATION, 0);
            long lastApproachingLimit = prefs.getLong(KEY_LAST_APPROACHING_NOTIFICATION, 0);
            long currentTime = System.currentTimeMillis();
            
            // Get notification frequency from settings
            long NOTIFICATION_COOLDOWN = notificationFrequency * 60 * 1000;
            
            // Check if we need to show notifications
            if (percentOfLimit >= 100) {
                if (currentTime - lastLimitReached >= NOTIFICATION_COOLDOWN) {
                    showNotification(context, "Screen Time Limit Reached", 
                        String.format("You have reached your daily limit of %d minutes.\nCurrent usage: %d minutes", 
                            screenTimeLimit, totalMinutes));
                    prefs.edit().putLong(KEY_LAST_LIMIT_NOTIFICATION, currentTime).apply();
                    Log.d(TAG, String.format("Showed limit reached notification at %d minutes", totalMinutes));
                } else {
                    Log.d(TAG, String.format("Skipping limit reached notification - %d minutes until next notification", 
                        ((NOTIFICATION_COOLDOWN - (currentTime - lastLimitReached)) / 60000)));
                }
            } else if (percentOfLimit >= 90) {
                if (currentTime - lastApproachingLimit >= NOTIFICATION_COOLDOWN) {
                    showNotification(context, "Approaching Screen Time Limit", 
                        String.format("You have %d minutes remaining.\nCurrent usage: %d minutes\nDaily limit: %d minutes", 
                            Math.round(screenTimeLimit - totalMinutes), totalMinutes, screenTimeLimit));
                    prefs.edit().putLong(KEY_LAST_APPROACHING_NOTIFICATION, currentTime).apply();
                    Log.d(TAG, String.format("Showed approaching limit notification at %d minutes", totalMinutes));
                } else {
                    Log.d(TAG, String.format("Skipping approaching limit notification - %d minutes until next notification", 
                        ((NOTIFICATION_COOLDOWN - (currentTime - lastApproachingLimit)) / 60000)));
                }
            }

            // Update widget with current values
            Intent updateIntent = new Intent(context, ScreenTimeWidgetProvider.class);
            updateIntent.setAction("android.appwidget.action.APPWIDGET_UPDATE");
            int[] ids = AppWidgetManager.getInstance(context)
                .getAppWidgetIds(new ComponentName(context, ScreenTimeWidgetProvider.class));
            updateIntent.putExtra(AppWidgetManager.EXTRA_APPWIDGET_IDS, ids);
            updateIntent.putExtra("totalScreenTime", totalMinutes);
            updateIntent.putExtra("screenTimeLimit", screenTimeLimit);
            context.sendBroadcast(updateIntent);
            
            // Broadcast update to ensure all components are in sync
            try {
                JSONObject updateData = new JSONObject();
                updateData.put("action", "UPDATE_SETTINGS");
                updateData.put("totalScreenTime", totalMinutes);
                updateData.put("screenTimeLimit", screenTimeLimit);
                updateData.put("notificationFrequency", notificationFrequency);
                updateData.put("timestamp", System.currentTimeMillis());
                updateData.put("userHasSetLimit", userHasSetLimit);

                Intent broadcastIntent = new Intent("com.screentimereminder.app.APP_USAGE_UPDATE");
                broadcastIntent.putExtra("usageData", updateData.toString());
                broadcastIntent.addFlags(Intent.FLAG_RECEIVER_FOREGROUND);
                context.sendOrderedBroadcast(broadcastIntent, null);
                
                Log.d(TAG, "Broadcast settings update to all components");
            } catch (Exception e) {
                Log.e(TAG, "Error broadcasting settings update", e);
            }
            
        } catch (Exception e) {
            Log.e(TAG, "Error checking screen time limit", e);
        }
    }

    private float getTodayScreenTime(Context context) {
        // Use the static calculateScreenTime method which is more accurate
        return calculateScreenTime(context);
    }

    private long getTotalScreenTimeForToday() {
        try {
            // Use the more accurate calculateScreenTime method
            return Math.round(calculateScreenTime(getContext()) * 60 * 1000); // Convert minutes to milliseconds
        } catch (Exception e) {
            Log.e(TAG, "Error getting total screen time for today", e);
            return 0;
        }
    }

    public int getTotalScreenTime() {
        try {
            // Use the static method for consistency
            return Math.round(calculateScreenTime(getContext()));
        } catch (Exception e) {
            Log.e(TAG, "Error getting total screen time", e);
            return 0;
        }
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

            // Get all usage stats for today
            List<UsageStats> stats = usageStatsManager.queryUsageStats(
                UsageStatsManager.INTERVAL_DAILY, startTime, endTime);

            if (stats == null || stats.isEmpty()) {
                Log.w(TAG, "No usage stats available for today");
                return getFallbackScreenTime(context);
            }

            float totalMinutes = 0;
            String ourPackage = context.getPackageName();

            // Calculate total time from UsageStats
            for (UsageStats stat : stats) {
                String packageName = stat.getPackageName();
                
                // Skip our own app and system apps (except common ones)
                if (packageName.equals(ourPackage) || (isSystemApp(context, packageName) && !isCommonApp(packageName))) {
                    continue;
                }
                
                long timeInForeground = stat.getTotalTimeInForeground();
                if (timeInForeground > 0) {
                    totalMinutes += timeInForeground / 60000.0f; // Convert to minutes
                }
            }

            Log.d(TAG, String.format("Calculated total screen time: %.2f minutes", totalMinutes));

            // Store the calculated value
            SharedPreferences.Editor editor = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit();
            editor.putFloat(KEY_TOTAL_SCREEN_TIME, totalMinutes);
            editor.putLong(KEY_LAST_UPDATE, System.currentTimeMillis());
            editor.apply();

            // Update widget
            int screenTimeLimit = getScreenTimeLimitStatic(context);
            Intent updateIntent = new Intent(context, ScreenTimeWidgetProvider.class);
            updateIntent.setAction("android.appwidget.action.APPWIDGET_UPDATE");
            int[] ids = AppWidgetManager.getInstance(context)
                .getAppWidgetIds(new ComponentName(context, ScreenTimeWidgetProvider.class));
            updateIntent.putExtra(AppWidgetManager.EXTRA_APPWIDGET_IDS, ids);
            updateIntent.putExtra("totalScreenTime", totalMinutes);
            updateIntent.putExtra("screenTimeLimit", screenTimeLimit);
            context.sendBroadcast(updateIntent);

            return totalMinutes;
        } catch (Exception e) {
            Log.e(TAG, "Error calculating screen time", e);
            return getFallbackScreenTime(context);
        }
    }

    private static boolean isCommonApp(String packageName) {
        String lowerCase = packageName.toLowerCase();
        return lowerCase.contains("browser") || 
               lowerCase.contains("chrome") || 
               lowerCase.contains("firefox") || 
               lowerCase.contains("opera") || 
               lowerCase.contains("edge") ||
               lowerCase.contains("mail") || 
               lowerCase.contains("gmail") || 
               lowerCase.contains("outlook") || 
               lowerCase.contains("k9") ||
               lowerCase.contains("yahoo") ||
               lowerCase.contains("maps") ||
               lowerCase.contains("navigation") ||
               lowerCase.contains("waze") ||
               lowerCase.contains("google") ||
               lowerCase.contains("youtube") ||
               lowerCase.contains("play") ||
               lowerCase.contains("drive") ||
               lowerCase.contains("photos") ||
               lowerCase.contains("calendar") ||
               lowerCase.contains("contacts") ||
               lowerCase.contains("camera") ||
               lowerCase.contains("gallery") ||
               lowerCase.contains("music") ||
               lowerCase.contains("video") ||
               lowerCase.contains("player");
    }

    private static float getFallbackScreenTime(Context context) {
        SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        long lastUpdate = prefs.getLong(KEY_LAST_UPDATE, 0);
        long currentTime = System.currentTimeMillis();
        
        // If the last update was within the last hour, use the cached value
        if (currentTime - lastUpdate < 60 * 60 * 1000) {
            return prefs.getFloat(KEY_TOTAL_SCREEN_TIME, 0);
        }
        
        return 0;
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
            
            // Check if it's a system app
            boolean isSystem = (appInfo.flags & ApplicationInfo.FLAG_SYSTEM) != 0;
            
            // Explicitly include common browsers, mail apps, and navigation apps even if they are system apps
            if (isSystem) {
                String appNameLower = packageName.toLowerCase();
                if (appNameLower.contains("browser") || 
                    appNameLower.contains("chrome") || 
                    appNameLower.contains("firefox") || 
                    appNameLower.contains("opera") || 
                    appNameLower.contains("edge") ||
                    appNameLower.contains("mail") || 
                    appNameLower.contains("gmail") || 
                    appNameLower.contains("outlook") || 
                    appNameLower.contains("k9") ||
                    appNameLower.contains("yahoo") ||
                    appNameLower.contains("maps") || // Include Google Maps
                    appNameLower.contains("navigation") || // Include other navigation apps
                    appNameLower.contains("waze") || // Include Waze
                    appNameLower.contains("google") || // Include other Google apps
                    appNameLower.contains("youtube") || // Include YouTube
                    appNameLower.contains("play") || // Include Google Play apps
                    appNameLower.contains("drive") || // Include Google Drive
                    appNameLower.contains("photos") || // Include Google Photos
                    appNameLower.contains("calendar") || // Include Calendar apps
                    appNameLower.contains("contacts") || // Include Contacts apps
                    appNameLower.contains("camera") || // Include Camera apps
                    appNameLower.contains("gallery") || // Include Gallery apps
                    appNameLower.contains("music") || // Include Music apps
                    appNameLower.contains("video") || // Include Video apps
                    appNameLower.contains("player")) { // Include Media players
                    // This is a commonly used app, include it in tracking
                    return false;
                }
            }
            
            return isSystem;
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
     * Get the app icon as a Base64 encoded string with caching
     */
    private String getAppIconBase64(String packageName) {
        // If this is a system app that has issues with icon retrieval, return empty string
        if (packageName == null || packageName.isEmpty()) {
            return "";
        }
        
        // For debugging purposes, log the package we're trying to get
        Log.d(TAG, "Getting icon for: " + packageName);
        
        try {
            // For efficiency, check memory cache first
            String cachedIcon = iconCache.get(packageName);
            if (cachedIcon != null && !cachedIcon.isEmpty()) {
                return cachedIcon;
            }
            
            // Generate the icon directly (skip disk cache for now to simplify)
            String iconBase64 = generateAppIconBase64(packageName);
            
            // Only store valid icons in the cache
            if (iconBase64 != null && !iconBase64.isEmpty()) {
                // Update memory cache
                iconCache.put(packageName, iconBase64);
                
                // Skip disk caching for now - we'll focus on reliable in-memory cache
            }
            
            return iconBase64;
        } catch (Exception e) {
            Log.e(TAG, "Error getting app icon for " + packageName, e);
            return ""; // Return empty string on error
        }
    }
    
    /**
     * Generate app icon without caching (original implementation)
     */
    private String generateAppIconBase64(String packageName) {
        try {
            PackageManager packageManager = getContext().getPackageManager();
            ApplicationInfo appInfo = null;
            
            try {
                // Get application info with required flags
                appInfo = packageManager.getApplicationInfo(packageName, 0);
            } catch (Exception e) {
                Log.w(TAG, "Package not found: " + packageName, e);
                return "";
            }
            
            // Get drawable icon directly - simpler and more reliable
            android.graphics.drawable.Drawable icon = appInfo.loadIcon(packageManager);
            if (icon == null) {
                Log.w(TAG, "Icon is null for " + packageName);
                return "";
            }
            
            // Create a bitmap with fixed size to ensure consistency
            android.graphics.Bitmap bitmap = android.graphics.Bitmap.createBitmap(144, 144, 
                    android.graphics.Bitmap.Config.ARGB_8888);
            android.graphics.Canvas canvas = new android.graphics.Canvas(bitmap);
            
            // Set background to transparent
            canvas.drawColor(android.graphics.Color.TRANSPARENT);
            
            // Scale the icon to fit
            icon.setBounds(0, 0, 144, 144);
            icon.draw(canvas);
            
            return bitmapToBase64(bitmap);
        } catch (Exception e) {
            Log.e(TAG, "Error in generateAppIconBase64 for " + packageName, e);
            return "";
        }
    }
    
    /**
     * Convert bitmap to Base64 string with optimized compression
     */
    private String bitmapToBase64(android.graphics.Bitmap bitmap) {
        try {
            // Use a ByteArrayOutputStream to store compressed bitmap data
            java.io.ByteArrayOutputStream outputStream = new java.io.ByteArrayOutputStream();
            
            // Use PNG format with transparency support
            bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 90, outputStream);
            
            // Get byte array
            byte[] bitmapData = outputStream.toByteArray();
            
            // Convert to Base64 with proper data URL format for React/HTML
            String base64 = "data:image/png;base64," + android.util.Base64.encodeToString(bitmapData, android.util.Base64.NO_WRAP);
            
            // Log success and size for debugging
            Log.d(TAG, "Generated icon, size: " + (bitmapData.length / 1024) + "KB");
            
            return base64;
        } catch (Exception e) {
            Log.e(TAG, "Error converting bitmap to Base64", e);
            return "";
        }
    }
    
    /**
     * Load icon from disk cache
     */
    private String loadIconFromDiskCache(String packageName) {
        try {
            // Create cache directory if it doesn't exist
            File cacheDir = new File(getContext().getCacheDir(), ICON_CACHE_DIR);
            if (!cacheDir.exists()) {
                return null;
            }
            
            // Create cache file path
            File cacheFile = new File(cacheDir, packageName.replace(".", "_") + ".txt");
            if (!cacheFile.exists() || !cacheFile.canRead()) {
                return null;
            }
            
            // Read the cache file
            java.io.BufferedReader reader = new java.io.BufferedReader(new java.io.FileReader(cacheFile));
            String line = reader.readLine();
            reader.close();
            
            return line;
        } catch (Exception e) {
            Log.e(TAG, "Error loading icon from disk cache for " + packageName, e);
            return null;
        }
    }
    
    /**
     * Save icon to disk cache
     */
    private void saveIconToDiskCache(String packageName, String iconBase64) {
        try {
            // Create cache directory if it doesn't exist
            File cacheDir = new File(getContext().getCacheDir(), ICON_CACHE_DIR);
            if (!cacheDir.exists()) {
                cacheDir.mkdirs();
            }
            
            // Create cache file path
            File cacheFile = new File(cacheDir, packageName.replace(".", "_") + ".txt");
            
            // Write the cache file
            java.io.FileWriter writer = new java.io.FileWriter(cacheFile);
            writer.write(iconBase64);
            writer.close();
            
            Log.d(TAG, "Saved icon to disk cache for " + packageName);
        } catch (Exception e) {
            Log.e(TAG, "Error saving icon to disk cache for " + packageName, e);
        }
    }
    
    /**
     * Clean up cache if needed
     */
    private void cleanupCacheIfNeeded() {
        long currentTime = System.currentTimeMillis();
        if (currentTime - iconCacheLastCleanup > CACHE_CLEANUP_INTERVAL) {
            // Update the last cleanup time
            iconCacheLastCleanup = currentTime;
            
            // Run cleanup on a background thread
            backgroundExecutor.execute(() -> {
                try {
                    // Create cache directory if it doesn't exist
                    File cacheDir = new File(getContext().getCacheDir(), ICON_CACHE_DIR);
                    if (!cacheDir.exists()) {
                        return;
                    }
                    
                    // Get all cache files
                    File[] cacheFiles = cacheDir.listFiles();
                    if (cacheFiles == null || cacheFiles.length == 0) {
                        return;
                    }
                    
                    // Delete older files if we have too many (keep 100 most recent)
                    if (cacheFiles.length > 100) {
                        // Sort by last modified time
                        java.util.Arrays.sort(cacheFiles, (f1, f2) -> 
                            Long.compare(f2.lastModified(), f1.lastModified()));
                        
                        // Delete older files
                        for (int i = 100; i < cacheFiles.length; i++) {
                            cacheFiles[i].delete();
                        }
                        
                        Log.d(TAG, "Cleaned up icon cache, kept 100 most recent icons");
                    }
                } catch (Exception e) {
                    Log.e(TAG, "Error cleaning up icon cache", e);
                }
            });
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
                    JSONObject data = new JSONObject(usageData);
                    String chainId = data.optString("chainId", "NO_CHAIN_ID");
                    String action = data.optString("action", "UNKNOWN");
                    
                    // Get SharedPreferences instance
                    SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
                    SharedPreferences.Editor editor = prefs.edit();
                    
                    // Get current values before making changes
                    long currentLimit = prefs.getLong(KEY_SCREEN_TIME_LIMIT, DEFAULT_SCREEN_TIME_LIMIT);
                    long currentFrequency = prefs.getLong(KEY_NOTIFICATION_FREQUENCY, DEFAULT_NOTIFICATION_FREQUENCY);
                    String currentChainId = prefs.getString("lastSettingsChainId", "NO_CHAIN_ID");
                    
                    Log.d(TAG, String.format("[%s] Processing settings update:", chainId));
                    Log.d(TAG, String.format("[%s] - Current chain ID: %s", chainId, currentChainId));
                    Log.d(TAG, String.format("[%s] - Current screen time limit: %d minutes", chainId, currentLimit));
                    Log.d(TAG, String.format("[%s] - Current notification frequency: %d minutes", chainId, currentFrequency));
                    
                    // Only update if the new chain ID is newer or if we're getting a direct update
                    boolean shouldUpdate = chainId.compareTo(currentChainId) > 0 || 
                                         "UPDATE_SETTINGS".equals(action) ||
                                         data.has("screenTimeLimit") || 
                                         data.has("notificationFrequency");
                    
                    if (shouldUpdate) {
                        // Always update the chain ID first
                        editor.putString("lastSettingsChainId", chainId);
                        
                        // Update SharedPreferences based on the action
                        if ("UPDATE_SETTINGS".equals(action) || data.has("screenTimeLimit")) {
                            long newLimit = data.getLong("screenTimeLimit");
                            editor.putLong(KEY_SCREEN_TIME_LIMIT, newLimit);
                            editor.putBoolean("userHasSetLimit", true);
                            Log.d(TAG, String.format("[%s] Updated screen time limit to: %d minutes", chainId, newLimit));
                        }
                        
                        if ("UPDATE_SETTINGS".equals(action) || data.has("notificationFrequency")) {
                            long newFrequency = data.getLong("notificationFrequency");
                            editor.putLong(KEY_NOTIFICATION_FREQUENCY, newFrequency);
                            Log.d(TAG, String.format("[%s] Updated notification frequency to: %d minutes", chainId, newFrequency));
                        }
                        
                        // Apply changes immediately
                        editor.commit();
                        
                        // Verify the changes
                        long savedLimit = prefs.getLong(KEY_SCREEN_TIME_LIMIT, DEFAULT_SCREEN_TIME_LIMIT);
                        long savedFrequency = prefs.getLong(KEY_NOTIFICATION_FREQUENCY, DEFAULT_NOTIFICATION_FREQUENCY);
                        String savedChainId = prefs.getString("lastSettingsChainId", "NO_CHAIN_ID");
                        
                        Log.d(TAG, String.format("[%s] Settings synchronized - New values:", chainId));
                        Log.d(TAG, String.format("[%s] - Screen time limit: %d minutes", chainId, savedLimit));
                        Log.d(TAG, String.format("[%s] - Notification frequency: %d minutes", chainId, savedFrequency));
                        Log.d(TAG, String.format("[%s] - Chain ID: %s", chainId, savedChainId));
                        
                        // Broadcast the settings change with high priority to ensure immediate delivery
                        try {
                            JSONObject settingsData = new JSONObject();
                            settingsData.put("chainId", chainId);
                            settingsData.put("action", "UPDATE_SETTINGS");
                            settingsData.put("screenTimeLimit", currentLimit);
                            settingsData.put("notificationFrequency", currentFrequency);
                            settingsData.put("timestamp", System.currentTimeMillis());
                            settingsData.put("userHasSetLimit", true);
                            
                            Intent updateIntent = new Intent("com.screentimereminder.app.APP_USAGE_UPDATE");
                            updateIntent.putExtra("usageData", settingsData.toString());
                            updateIntent.addFlags(Intent.FLAG_RECEIVER_FOREGROUND);
                            context.sendOrderedBroadcast(updateIntent, null);
                            
                            // Update widget immediately with correct values
                            Intent widgetIntent = new Intent(context, ScreenTimeWidgetProvider.class);
                            widgetIntent.setAction(AppWidgetManager.ACTION_APPWIDGET_UPDATE);
                            int[] ids = AppWidgetManager.getInstance(context)
                                .getAppWidgetIds(new ComponentName(context, ScreenTimeWidgetProvider.class));
                            widgetIntent.putExtra(AppWidgetManager.EXTRA_APPWIDGET_IDS, ids);
                            widgetIntent.putExtra("totalScreenTime", calculateScreenTime(context));
                            widgetIntent.putExtra("screenTimeLimit", currentLimit);
                            context.sendBroadcast(widgetIntent);
                            
                            Log.d(TAG, String.format("[%s] Settings change broadcast sent successfully", chainId));
                            
                            // Force an immediate check with new settings
                            float totalTime = calculateScreenTime(context);
                            checkScreenTimeLimitStatic(context, Math.round(totalTime), (int)currentFrequency);
                        } catch (Exception e) {
                            Log.e(TAG, String.format("[%s] Error broadcasting settings change: %s", chainId, e.getMessage()), e);
                        }
                    } else {
                        Log.e(TAG, String.format("[%s] Failed to commit settings changes", chainId));
                    }
                } catch (Exception e) {
                    Log.e(TAG, "Error processing usage update", e);
                }
            } else {
                Log.w(TAG, "Received broadcast without usageData extra");
            }
        }
    };

    @PluginMethod
    public void setScreenTimeLimit(PluginCall call) {
        synchronized (settingsLock) {
            try {
                int limitMinutes = call.getInt("limitMinutes", (int) SettingsConstants.DEFAULT_SCREEN_TIME_LIMIT);
                
                // Validate the limit
                if (limitMinutes < SettingsConstants.MIN_SCREEN_TIME_LIMIT || 
                    limitMinutes > SettingsConstants.MAX_SCREEN_TIME_LIMIT) {
                    call.reject("Screen time limit must be between " + 
                              SettingsConstants.MIN_SCREEN_TIME_LIMIT + " and " + 
                              SettingsConstants.MAX_SCREEN_TIME_LIMIT + " minutes");
                    return;
                }

                // Get current notification frequency
                SettingsManager settingsManager = SettingsManager.getInstance(context);
                long currentFrequency = settingsManager.getNotificationFrequency();
                
                // Update settings through SettingsManager
                settingsManager.updateSettings(limitMinutes, currentFrequency);
                
                // Broadcast update to widget
                updateWidgetWithData(getTotalScreenTime());
                
                call.resolve();
            } catch (Exception e) {
                Log.e(TAG, "Error setting screen time limit", e);
                call.reject("Failed to set screen time limit: " + e.getMessage());
            }
        }
    }

    @PluginMethod
    public void setNotificationFrequency(PluginCall call) {
        synchronized (settingsLock) {
            try {
                int frequency = call.getInt("frequency", (int) SettingsConstants.DEFAULT_NOTIFICATION_FREQUENCY);
                
                // Validate the frequency
                if (frequency < SettingsConstants.MIN_NOTIFICATION_FREQUENCY || 
                    frequency > SettingsConstants.MAX_NOTIFICATION_FREQUENCY) {
                    call.reject("Notification frequency must be between " + 
                              SettingsConstants.MIN_NOTIFICATION_FREQUENCY + " and " + 
                              SettingsConstants.MAX_NOTIFICATION_FREQUENCY + " minutes");
                    return;
                }

                // Get current screen time limit
                SettingsManager settingsManager = SettingsManager.getInstance(context);
                long currentLimit = settingsManager.getScreenTimeLimit();
                
                // Update settings through SettingsManager
                settingsManager.updateSettings(currentLimit, frequency);
                
                call.resolve();
            } catch (Exception e) {
                Log.e(TAG, "Error setting notification frequency", e);
                call.reject("Failed to set notification frequency: " + e.getMessage());
            }
        }
    }

    private void updateSharedPreferences() {
        try {
            SharedPreferences prefs = getContext().getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
            SharedPreferences.Editor editor = prefs.edit();
            
            // Get current screen time
            int totalMinutes = getTotalScreenTime();
            
            // Get current screen time limit
            long screenTimeLimit = prefs.getLong(KEY_SCREEN_TIME_LIMIT, DEFAULT_SCREEN_TIME_LIMIT);
            
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

    private void updateWidgetWithData(float totalMinutes) {
        try {
            SettingsManager settingsManager = SettingsManager.getInstance(context);
            long screenTimeLimit = settingsManager.getScreenTimeLimit();
            
            Intent updateIntent = new Intent(context, ScreenTimeWidgetProvider.class);
            updateIntent.setAction(AppWidgetManager.ACTION_APPWIDGET_UPDATE);
            int[] ids = AppWidgetManager.getInstance(context)
                .getAppWidgetIds(new ComponentName(context, ScreenTimeWidgetProvider.class));
            updateIntent.putExtra(AppWidgetManager.EXTRA_APPWIDGET_IDS, ids);
            updateIntent.putExtra("totalScreenTime", (float)totalMinutes);
            updateIntent.putExtra("screenTimeLimit", screenTimeLimit);
            context.sendBroadcast(updateIntent);
            
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
            long notificationFrequency = prefs.getLong(KEY_NOTIFICATION_FREQUENCY, DEFAULT_NOTIFICATION_FREQUENCY);
            long NOTIFICATION_COOLDOWN = notificationFrequency * 60 * 1000;
            
            // Check if we need to show notifications
            if (percentOfLimit >= 100) {
                // Check cooldown for limit reached notification
                if (currentTime - lastLimitReached >= NOTIFICATION_COOLDOWN) {
                    showNotification(getContext(), "Screen Time Limit Reached", 
                        String.format("You have reached your daily limit of %d minutes.\nCurrent usage: %d minutes", 
                            screenTimeLimit, totalMinutes));
                    prefs.edit().putLong(KEY_LAST_LIMIT_NOTIFICATION, currentTime).apply();
                } else {
                    Log.d(TAG, "Skipping limit reached notification - " + 
                        ((NOTIFICATION_COOLDOWN - (currentTime - lastLimitReached)) / 60000) + 
                        " minutes until next notification");
                }
            } else if (percentOfLimit >= 80) { // Changed from 90% to 80% to give more warning
                // Check cooldown for approaching limit notification
                if (currentTime - lastApproachingLimit >= NOTIFICATION_COOLDOWN) {
                    showNotification(getContext(), "Approaching Screen Time Limit", 
                        String.format("You have %d minutes remaining.\nCurrent usage: %d minutes\nDaily limit: %d minutes", 
                            Math.round(screenTimeLimit - totalMinutes), totalMinutes, screenTimeLimit));
                    prefs.edit().putLong(KEY_LAST_APPROACHING_NOTIFICATION, currentTime).apply();
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

    private void showLimitReachedNotification(int totalMinutes) {
        try {
            // Get current settings from SharedPreferences
            SharedPreferences prefs = getContext().getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
            long screenTimeLimit = prefs.getLong(KEY_SCREEN_TIME_LIMIT, DEFAULT_SCREEN_TIME_LIMIT);
            
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
            // Get current settings from SharedPreferences
            SharedPreferences prefs = getContext().getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
            long screenTimeLimit = prefs.getLong(KEY_SCREEN_TIME_LIMIT, DEFAULT_SCREEN_TIME_LIMIT);
            
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

    public long getScreenTimeLimit() {
        SharedPreferences prefs = getContext().getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        boolean userHasSetLimit = prefs.getBoolean("userHasSetLimit", false);
        
        if (userHasSetLimit) {
            // User has set a limit, always use their setting
            long limit = prefs.getLong(KEY_SCREEN_TIME_LIMIT, DEFAULT_SCREEN_TIME_LIMIT);
            Log.d(TAG, "getScreenTimeLimit: Retrieved user-set limit: " + limit);
            return limit;
        }
        
        // No user setting, use default
        Log.d(TAG, "getScreenTimeLimit: No user setting, using default limit: " + DEFAULT_SCREEN_TIME_LIMIT);
        return DEFAULT_SCREEN_TIME_LIMIT;
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
            long dayStartTime = getStartOfDay();
            long endTime = System.currentTimeMillis();

            List<UsageStats> stats = manager.queryUsageStats(
                UsageStatsManager.INTERVAL_DAILY, dayStartTime, endTime);

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
     * Static method to get screen time limit that doesn't rely on Plugin context
     */
    public static synchronized int getScreenTimeLimitStatic(Context context) {
        SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        return (int) prefs.getLong(KEY_SCREEN_TIME_LIMIT, DEFAULT_SCREEN_TIME_LIMIT);
    }

    /**
     * Static method to set screen time limit
     */
    public static synchronized void setScreenTimeLimitStatic(Context context, int limitMinutes) {
        SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        SharedPreferences.Editor editor = prefs.edit();
        editor.putLong(KEY_SCREEN_TIME_LIMIT, limitMinutes);
        editor.commit();
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
            
            Log.d(TAG, String.format("Successfully showed notification: %s - %s", title, message));
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
    public static synchronized int getNotificationFrequencyStatic(Context context) {
        SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        return (int) prefs.getLong(KEY_NOTIFICATION_FREQUENCY, DEFAULT_NOTIFICATION_FREQUENCY);
    }

    /**
     * Static method to set notification frequency
     */
    public static synchronized void setNotificationFrequencyStatic(Context context, int frequencyMinutes) {
        SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        SharedPreferences.Editor editor = prefs.edit();
        editor.putLong(KEY_NOTIFICATION_FREQUENCY, frequencyMinutes);
        editor.commit();
    }

    /**
     * Get the start of the current day in milliseconds
     */
    private static long getStartOfDay() {
        Calendar calendar = Calendar.getInstance();
        calendar.set(Calendar.HOUR_OF_DAY, 0);
        calendar.set(Calendar.MINUTE, 0);
        calendar.set(Calendar.SECOND, 0);
        calendar.set(Calendar.MILLISECOND, 0);
        return calendar.getTimeInMillis();
    }

    /**
     * Get the display name of an app from its package name
     */
    private static String getAppName(Context context, String packageName) {
        try {
            PackageManager packageManager = context.getPackageManager();
            ApplicationInfo appInfo = packageManager.getApplicationInfo(packageName, 0);
            return packageManager.getApplicationLabel(appInfo).toString();
        } catch (PackageManager.NameNotFoundException e) {
            return packageName;
        }
    }

    // Add a method to safely update settings
    private void updateSettings(String chainId, long screenTimeLimit, long notificationFrequency) {
        synchronized (settingsLock) {
            try {
                SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_MULTI_PROCESS);
                SharedPreferences.Editor editor = prefs.edit();
                
                // Get current values before making changes
                long currentLimit = prefs.getLong(KEY_SCREEN_TIME_LIMIT, DEFAULT_SCREEN_TIME_LIMIT);
                long currentFrequency = prefs.getLong(KEY_NOTIFICATION_FREQUENCY, DEFAULT_NOTIFICATION_FREQUENCY);
                String currentChainId = prefs.getString("lastSettingsChainId", "NO_CHAIN_ID");
                boolean userHasSetLimit = prefs.getBoolean("userHasSetLimit", false);
                
                Log.d(TAG, String.format("[%s] Updating settings - Current values:", chainId));
                Log.d(TAG, String.format("[%s] - Current chain ID: %s", chainId, currentChainId));
                Log.d(TAG, String.format("[%s] - Current screen time limit: %d minutes", chainId, currentLimit));
                Log.d(TAG, String.format("[%s] - Current notification frequency: %d minutes", chainId, currentFrequency));
                Log.d(TAG, String.format("[%s] - New screen time limit: %d minutes", chainId, screenTimeLimit));
                Log.d(TAG, String.format("[%s] - New notification frequency: %d minutes", chainId, notificationFrequency));
                Log.d(TAG, String.format("[%s] - User has set limit: %b", chainId, userHasSetLimit));
                
                // Only update if the new chain ID is newer or if we're getting a direct update
                boolean shouldUpdate = chainId.compareTo(currentChainId) > 0;
                
                if (shouldUpdate) {
                    // Reset notification timestamps when frequency changes
                    if (notificationFrequency != currentFrequency) {
                        editor.putLong(KEY_LAST_LIMIT_NOTIFICATION, System.currentTimeMillis());
                        editor.putLong(KEY_LAST_APPROACHING_NOTIFICATION, System.currentTimeMillis());
                        Log.d(TAG, String.format("[%s] Reset notification timestamps due to frequency change", chainId));
                    }
                    
                    // Update all settings atomically
                    editor.putLong(KEY_SCREEN_TIME_LIMIT, screenTimeLimit);
                    editor.putLong(KEY_NOTIFICATION_FREQUENCY, notificationFrequency);
                    editor.putString("lastSettingsChainId", chainId);
                    editor.putBoolean("userHasSetLimit", true);
                    
                    // Force immediate write and ensure all processes see the update
                    boolean success = editor.commit();
                    
                    if (success) {
                        Log.d(TAG, String.format("[%s] Settings committed successfully", chainId));
                        
                        // Force immediate reload of preferences with MODE_MULTI_PROCESS
                        prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_MULTI_PROCESS);
                        
                        // Verify the changes
                        long verifiedLimit = prefs.getLong(KEY_SCREEN_TIME_LIMIT, -1);
                        long verifiedFrequency = prefs.getLong(KEY_NOTIFICATION_FREQUENCY, -1);
                        boolean verifiedUserSet = prefs.getBoolean("userHasSetLimit", false);
                        
                        Log.d(TAG, String.format("[%s] Verified settings:", chainId));
                        Log.d(TAG, String.format("[%s] - Screen time limit: %d minutes", chainId, verifiedLimit));
                        Log.d(TAG, String.format("[%s] - Notification frequency: %d minutes", chainId, verifiedFrequency));
                        Log.d(TAG, String.format("[%s] - User has set limit: %b", chainId, verifiedUserSet));
                        
                        // Broadcast the settings change with high priority to ensure immediate delivery
                        try {
                            JSONObject settingsData = new JSONObject();
                            settingsData.put("chainId", chainId);
                            settingsData.put("action", "UPDATE_SETTINGS");
                            settingsData.put("screenTimeLimit", screenTimeLimit);
                            settingsData.put("notificationFrequency", notificationFrequency);
                            settingsData.put("timestamp", System.currentTimeMillis());
                            settingsData.put("userHasSetLimit", true);
                            
                            Intent updateIntent = new Intent("com.screentimereminder.app.APP_USAGE_UPDATE");
                            updateIntent.putExtra("usageData", settingsData.toString());
                            updateIntent.addFlags(Intent.FLAG_RECEIVER_FOREGROUND);
                            context.sendOrderedBroadcast(updateIntent, null);
                            
                            // Update widget immediately with correct values
                            Intent widgetIntent = new Intent(context, ScreenTimeWidgetProvider.class);
                            widgetIntent.setAction(AppWidgetManager.ACTION_APPWIDGET_UPDATE);
                            int[] ids = AppWidgetManager.getInstance(context)
                                .getAppWidgetIds(new ComponentName(context, ScreenTimeWidgetProvider.class));
                            widgetIntent.putExtra(AppWidgetManager.EXTRA_APPWIDGET_IDS, ids);
                            widgetIntent.putExtra("totalScreenTime", calculateScreenTime(context));
                            widgetIntent.putExtra("screenTimeLimit", screenTimeLimit);
                            context.sendBroadcast(widgetIntent);
                            
                            Log.d(TAG, String.format("[%s] Settings change broadcast sent successfully", chainId));
                            
                            // Force an immediate check with new settings
                            float totalTime = calculateScreenTime(context);
                            checkScreenTimeLimitStatic(context, Math.round(totalTime), (int)notificationFrequency);
                        } catch (Exception e) {
                            Log.e(TAG, String.format("[%s] Error broadcasting settings change: %s", chainId, e.getMessage()), e);
                        }
                    } else {
                        Log.e(TAG, String.format("[%s] Failed to commit settings changes", chainId));
                    }
                } else {
                    Log.d(TAG, String.format("[%s] Skipping update - current chain ID (%s) is newer", chainId, currentChainId));
                }
            } catch (Exception e) {
                Log.e(TAG, String.format("[%s] Error updating settings: %s", chainId, e.getMessage()));
            }
        }
    }

    private void verifySettingsChanges(SharedPreferences prefs, String chainId, long screenTimeLimit, long notificationFrequency) {
        try {
            // Read back the values to verify they were saved correctly
            long savedLimit = prefs.getLong(KEY_SCREEN_TIME_LIMIT, DEFAULT_SCREEN_TIME_LIMIT);
            long savedFrequency = prefs.getLong(KEY_NOTIFICATION_FREQUENCY, DEFAULT_NOTIFICATION_FREQUENCY);
            String savedChainId = prefs.getString("lastSettingsChainId", "NO_CHAIN_ID");
            boolean userHasSetLimit = prefs.getBoolean("userHasSetLimit", false);
            
            boolean allMatch = savedLimit == screenTimeLimit && 
                              savedFrequency == notificationFrequency && 
                              savedChainId.equals(chainId) &&
                              userHasSetLimit;
            
            if (allMatch) {
                Log.d(TAG, String.format("[%s] Settings verification successful - All values match:", chainId));
            } else {
                Log.e(TAG, String.format("[%s] Settings verification failed - Values don't match:", chainId));
            }
            
            Log.d(TAG, String.format("[%s] - Verified screen time limit: %d minutes", chainId, savedLimit));
            Log.d(TAG, String.format("[%s] - Verified notification frequency: %d minutes", chainId, savedFrequency));
            Log.d(TAG, String.format("[%s] - Verified chain ID: %s", chainId, savedChainId));
            Log.d(TAG, String.format("[%s] - Verified user has set limit: %s", chainId, userHasSetLimit));
        } catch (Exception e) {
            Log.e(TAG, String.format("[%s] Error verifying settings: %s", chainId, e.getMessage()));
        }
    }

    public void handleUsageUpdate(String usageData) {
        synchronized (settingsLock) {
            try {
                JSONObject data = new JSONObject(usageData);
                String chainId = data.optString("chainId", "NO_CHAIN_ID");
                String action = data.optString("action", "UNKNOWN");
                
                // Get current values using MODE_MULTI_PROCESS
                SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_MULTI_PROCESS);
                String currentChainId = prefs.getString("lastSettingsChainId", "NO_CHAIN_ID");
                
                // Only update if the new chain ID is newer or if we're getting a direct update
                boolean shouldUpdate = chainId.compareTo(currentChainId) > 0 || 
                                     data.has("screenTimeLimit") || 
                                     data.has("notificationFrequency");
                
                if (shouldUpdate) {
                    long newLimit = prefs.getLong(KEY_SCREEN_TIME_LIMIT, DEFAULT_SCREEN_TIME_LIMIT);
                    long newFrequency = prefs.getLong(KEY_NOTIFICATION_FREQUENCY, DEFAULT_NOTIFICATION_FREQUENCY);
                    
                    // Update settings based on the action
                    if ("UPDATE_SETTINGS".equals(action) || data.has("screenTimeLimit")) {
                        newLimit = data.getLong("screenTimeLimit");
                        Log.d(TAG, String.format("[%s] Received screen time limit update: %d minutes", chainId, newLimit));
                    }
                    
                    if ("UPDATE_SETTINGS".equals(action) || data.has("notificationFrequency")) {
                        newFrequency = data.getInt("notificationFrequency");
                        Log.d(TAG, String.format("[%s] Received notification frequency update: %d minutes", chainId, newFrequency));
                    }
                    
                    // Update settings through our synchronized method
                    updateSettings(chainId, newLimit, newFrequency);
                } else {
                    Log.d(TAG, String.format("[%s] Skipping update - current chain ID (%s) is newer", chainId, currentChainId));
                }
            } catch (Exception e) {
                Log.e(TAG, "Error handling usage update", e);
            }
        }
    }

    public static synchronized void updateSettingsStatic(Context context, long screenTimeLimit, long notificationFrequency) {
        try {
            SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
            String currentChainId = prefs.getString("lastSettingsChainId", "NO_CHAIN_ID");
            // Use millisecond timestamp for reliable cross-process comparison
            String newChainId = "SETTINGS_CHAIN_" + System.currentTimeMillis();
            
            // Only update if the new chain ID is newer than the current one
            if (newChainId.compareTo(currentChainId) > 0) {
                Log.d(TAG, String.format("[%s] Updating settings:", newChainId));
                Log.d(TAG, String.format("[%s] - Current chain ID: %s", newChainId, currentChainId));
                Log.d(TAG, String.format("[%s] - New screen time limit: %d minutes", newChainId, screenTimeLimit));
                Log.d(TAG, String.format("[%s] - New notification frequency: %d minutes", newChainId, notificationFrequency));
                
                // Use synchronized block to ensure atomic updates
                synchronized (AppUsageTracker.class) {
                    // Double-check the chain ID after getting the lock
                    SharedPreferences prefs2 = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
                    String currentChainId2 = prefs2.getString("lastSettingsChainId", "NO_CHAIN_ID");
                    if (newChainId.compareTo(currentChainId2) > 0) {
                        SharedPreferences.Editor editor = prefs2.edit();
                        editor.putLong(KEY_SCREEN_TIME_LIMIT, screenTimeLimit);
                        editor.putLong(KEY_NOTIFICATION_FREQUENCY, notificationFrequency);
                        editor.putString("lastSettingsChainId", newChainId);
                        editor.putBoolean("userHasSetLimit", true);
                        // Use commit to ensure immediate write to disk
                        boolean success = editor.commit();
                        
                        // Verify the changes
                        long savedLimit = prefs2.getLong(KEY_SCREEN_TIME_LIMIT, DEFAULT_SCREEN_TIME_LIMIT);
                        long savedFrequency = prefs2.getLong(KEY_NOTIFICATION_FREQUENCY, DEFAULT_NOTIFICATION_FREQUENCY);
                        String savedChainId = prefs2.getString("lastSettingsChainId", "NO_CHAIN_ID");
                        
                        Log.d(TAG, String.format("[%s] Settings synchronized - New values (commit success: %b):", newChainId, success));
                        Log.d(TAG, String.format("[%s] - Screen time limit: %d minutes", newChainId, savedLimit));
                        Log.d(TAG, String.format("[%s] - Notification frequency: %d minutes", newChainId, savedFrequency));
                        Log.d(TAG, String.format("[%s] - Chain ID: %s", newChainId, savedChainId));
                        
                        // Broadcast the update with high priority to ensure ordered delivery
                        try {
                            Intent broadcastIntent = new Intent("com.screentimereminder.app.APP_USAGE_UPDATE");
                            JSONObject updateData = new JSONObject();
                            updateData.put("screenTimeLimit", screenTimeLimit);
                            updateData.put("notificationFrequency", notificationFrequency);
                            updateData.put("chainId", newChainId);
                            updateData.put("action", "UPDATE_SETTINGS");
                            broadcastIntent.putExtra("usageData", updateData.toString());
                            context.sendOrderedBroadcast(broadcastIntent, null);
                            
                            // Force widget update with correct settings
                            Intent updateIntent = new Intent(context, ScreenTimeWidgetProvider.class);
                            updateIntent.setAction("android.appwidget.action.APPWIDGET_UPDATE");
                            int[] ids = AppWidgetManager.getInstance(context)
                                .getAppWidgetIds(new ComponentName(context, ScreenTimeWidgetProvider.class));
                            updateIntent.putExtra(AppWidgetManager.EXTRA_APPWIDGET_IDS, ids);
                            updateIntent.putExtra("totalScreenTime", calculateScreenTime(context));
                            updateIntent.putExtra("screenTimeLimit", screenTimeLimit);
                            context.sendBroadcast(updateIntent);
                        } catch (JSONException e) {
                            Log.e(TAG, "Error creating broadcast data", e);
                        }
                    } else {
                        Log.d(TAG, String.format("[%s] Aborting update - after lock, current chain ID (%s) is newer", 
                            newChainId, currentChainId2));
                    }
                }
            } else {
                Log.d(TAG, String.format("[%s] Skipping update - current chain ID (%s) is newer", newChainId, currentChainId));
            }
        } catch (Exception e) {
            Log.e(TAG, "Error updating settings statically", e);
        }
    }

    @PluginMethod
    public void getNotificationFrequency(PluginCall call) {
        try {
            SharedPreferences prefs = getContext().getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
            long frequency = prefs.getLong(KEY_NOTIFICATION_FREQUENCY, DEFAULT_NOTIFICATION_FREQUENCY);
            
            JSObject ret = new JSObject();
            ret.put("value", frequency);
            call.resolve(ret);
        } catch (Exception e) {
            Log.e(TAG, "Error getting notification frequency", e);
            call.reject("Failed to get notification frequency: " + e.getMessage());
        }
    }
} 