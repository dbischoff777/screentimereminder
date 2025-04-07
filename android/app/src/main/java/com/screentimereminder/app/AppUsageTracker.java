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

import androidx.core.app.NotificationCompat;

@CapacitorPlugin(name = "AppUsageTracker")
public class AppUsageTracker extends Plugin {
    private static final String TAG = "AppUsageTracker";
    private UsageStatsManager usageStatsManager;
    private ScheduledExecutorService scheduler;
    private String currentForegroundApp = "";
    private Handler mainHandler;
    private NotificationService notificationService;
    
    @Override
    public void load() {
        super.load();
        mainHandler = new Handler(Looper.getMainLooper());
        
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP_MR1) {
            usageStatsManager = (UsageStatsManager) getContext().getSystemService(Context.USAGE_STATS_SERVICE);
        }
        
        // Initialize notification service
        notificationService = new NotificationService(getContext());

        // Register broadcast receiver for background updates
        IntentFilter filter = new IntentFilter("com.screentimereminder.APP_USAGE_UPDATE");
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            getContext().registerReceiver(usageUpdateReceiver, filter, Context.RECEIVER_NOT_EXPORTED);
        } else {
            getContext().registerReceiver(usageUpdateReceiver, filter);
        }
    }
    
    /**
     * Check if the app has permission to access usage stats
     */
    @PluginMethod
    public void hasUsagePermission(PluginCall call) {
        Log.d(TAG, "hasUsagePermission: Checking if app has usage stats permission");
        boolean hasPermission = checkUsageStatsPermission();
        
        Log.d(TAG, "hasUsagePermission: Permission status = " + hasPermission);
        JSObject ret = new JSObject();
        ret.put("value", hasPermission);
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
            // Check if we have permission
            if (!checkUsageStatsPermission()) {
                call.reject("Usage stats permission not granted");
                return;
            }

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

            // Schedule periodic updates
            if (scheduler == null || scheduler.isShutdown()) {
                scheduler = Executors.newSingleThreadScheduledExecutor();
                scheduler.scheduleAtFixedRate(new Runnable() {
                    @Override
                    public void run() {
                        try {
                            // Update shared preferences with latest screen time data
                            updateSharedPreferences();
                            // Add a small random delay to avoid exact intervals
                            Thread.sleep((long)(Math.random() * 5000));
                        } catch (Exception e) {
                            Log.e(TAG, "Error in update cycle", e);
                        }
                    }
                }, 1, 5, TimeUnit.MINUTES); // Check every 5 minutes
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

            // Request usage access permission if not granted
            if (!checkUsageStatsPermission()) {
                Intent intent = new Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS);
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                getContext().startActivity(intent);
            }

            JSObject ret = new JSObject();
            ret.put("value", true);
            call.resolve(ret);
        } catch (Exception e) {
            Log.e(TAG, "Error starting tracking", e);
            call.reject("Failed to start tracking: " + e.getMessage(), e);
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
        if (!checkUsageStatsPermission()) {
            Log.e(TAG, "Usage stats permission not granted");
            call.reject("Usage stats permission not granted. Please grant permission in Settings > Apps > Special access > Usage access");
            return;
        }
        
        try {
            // Get time range from parameters or use default (last 24 hours)
            long endTime = System.currentTimeMillis();
            long startTime = getStartOfDay(); // Use start of day instead of 24 hours ago
            
            if (call.hasOption("startTime")) {
                startTime = call.getLong("startTime");
            }
            if (call.hasOption("endTime")) {
                endTime = call.getLong("endTime");
            }
            
            Log.d(TAG, "Getting app usage data from " + new java.util.Date(startTime) + 
                  " to " + new java.util.Date(endTime));

            // First try to get data from the AppUsageService
            Intent serviceIntent = new Intent(getContext(), AppUsageService.class);
            serviceIntent.setAction("GET_USAGE_DATA");
            getContext().startService(serviceIntent);
            
            // Query usage stats directly
            List<UsageStats> statsList = usageStatsManager.queryUsageStats(
                UsageStatsManager.INTERVAL_BEST, 
                startTime, 
                endTime
            );
            
            if (statsList == null || statsList.isEmpty()) {
                Log.w(TAG, "No usage data found with INTERVAL_BEST, trying INTERVAL_DAILY");
                statsList = usageStatsManager.queryUsageStats(
                    UsageStatsManager.INTERVAL_DAILY,
                    startTime,
                    endTime
                );
            }
            
            if (statsList == null || statsList.isEmpty()) {
                Log.w(TAG, "No usage data found with any interval");
                JSONArray emptyArray = new JSONArray();
                JSObject result = new JSObject();
                result.put("data", emptyArray.toString());
                call.resolve(result);
                return;
            }
            
            Log.d(TAG, "Found " + statsList.size() + " app usage records");
            
            // Convert the stats to our JSON format
            JSONArray usageData = new JSONArray();
            for (UsageStats stats : statsList) {
                String packageName = stats.getPackageName();
                
                // Skip system apps and our own app
                if (isSystemApp(packageName) || packageName.equals(getContext().getPackageName())) {
                    continue;
                }
                
                // Only include apps that were actually used
                long timeInForeground = stats.getTotalTimeInForeground();
                if (timeInForeground > 0) {
                    // Convert to minutes
                    double timeInMinutes = timeInForeground / 60000.0;
                    
                    // Create app usage object
                    JSONObject appUsage = createAppUsageObject(packageName);
                    appUsage.put("time", timeInMinutes);
                    appUsage.put("lastUsed", stats.getLastTimeUsed());
                    
                    // Add additional usage details
                    appUsage.put("firstTimeStamp", stats.getFirstTimeStamp());
                    appUsage.put("lastTimeStamp", stats.getLastTimeStamp());
                    appUsage.put("totalTimeInForeground", timeInMinutes);
                    
                    usageData.put(appUsage);
                    
                    Log.d(TAG, String.format("App %s used for %.2f minutes (last used: %s)", 
                        getAppName(packageName), 
                        timeInMinutes,
                        new java.util.Date(stats.getLastTimeUsed())
                    ));
                }
            }
            
            // Log the final data for debugging
            Log.d(TAG, "Final JSON data to return: " + usageData.toString());
            
            JSObject result = new JSObject();
            result.put("data", usageData.toString());
            call.resolve(result);
        } catch (Exception e) {
            Log.e(TAG, "Error getting app usage data", e);
            call.reject("Failed to get app usage data: " + e.getMessage(), e);
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
            JSObject data = call.getData();
            float totalTime = 0;
            if (data.has("totalTime")) {
                totalTime = (float) data.getDouble("totalTime");
            }
            int limit = data.getInteger("limit", 60);
            int notificationFrequency = data.getInteger("notificationFrequency", 5);
            
            SharedPreferences prefs = getContext().getSharedPreferences("ScreenTimeReminder", Context.MODE_PRIVATE);
            long lastLimitReached = prefs.getLong("lastLimitReachedNotification", 0);
            long lastApproachingLimit = prefs.getLong("lastApproachingLimitNotification", 0);
            long currentTime = System.currentTimeMillis();
            
            // Calculate remaining time
            float remainingMinutes = limit - totalTime;
            
            // Update total screen time
            prefs.edit().putFloat("totalScreenTime", totalTime).apply();
            
            // Define cooldown period (1 minute in milliseconds)
            long NOTIFICATION_COOLDOWN = 60 * 1000;
            
            // Check if we should show notifications
            if (remainingMinutes <= 0) {
                // Check if enough time has passed since last limit reached notification
                if (currentTime - lastLimitReached >= NOTIFICATION_COOLDOWN) {
                    // Show limit reached notification
                    showNotification("Screen Time Limit Reached", 
                        "You have reached your daily screen time limit of " + limit + " minutes.");
                    
                    // Update last limit reached timestamp
                    prefs.edit().putLong("lastLimitReachedNotification", currentTime).apply();
                    Log.d(TAG, "Updated lastLimitReachedNotification to: " + currentTime);
                }
            } else if (remainingMinutes <= notificationFrequency) {
                // Check if enough time has passed since last approaching limit notification
                if (currentTime - lastApproachingLimit >= NOTIFICATION_COOLDOWN) {
                    // Show approaching limit notification
                    showNotification("Approaching Screen Time Limit", 
                        "You have " + Math.round(remainingMinutes) + " minutes remaining.");
                    
                    // Update last approaching limit timestamp
                    prefs.edit().putLong("lastApproachingLimitNotification", currentTime).apply();
                    Log.d(TAG, "Updated lastApproachingLimitNotification to: " + currentTime);
                }
            }
            
            call.resolve();
        } catch (Exception e) {
            Log.e(TAG, "Error checking screen time limit", e);
            call.reject("Failed to check screen time limit: " + e.getMessage(), e);
        }
    }
    
    @PluginMethod
    public void getSharedPreferences(PluginCall call) {
        try {
            SharedPreferences prefs = getContext().getSharedPreferences("ScreenTimeReminder", Context.MODE_PRIVATE);
            
            // Get values with correct defaults
            long lastLimitReached = prefs.getLong("lastLimitReachedNotification", 0);
            long lastApproachingLimit = prefs.getLong("lastApproachingLimitNotification", 0);
            int screenTimeLimit = prefs.getInt("screenTimeLimit", 60); // Default to 60 minutes
            int notificationFrequency = prefs.getInt("notificationFrequency", 5); // Default to 5 minutes
            float totalScreenTime = prefs.getFloat("totalScreenTime", 0);
            
            // If timestamps are 0, set them to current time
            long currentTime = System.currentTimeMillis();
            if (lastLimitReached == 0) {
                lastLimitReached = currentTime;
                prefs.edit().putLong("lastLimitReachedNotification", currentTime).apply();
            }
            if (lastApproachingLimit == 0) {
                lastApproachingLimit = currentTime;
                prefs.edit().putLong("lastApproachingLimitNotification", currentTime).apply();
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
            call.reject("Failed to get shared preferences: " + e.getMessage(), e);
        }
    }
    
    @Override
    protected void handleOnDestroy() {
        if (scheduler != null && !scheduler.isShutdown()) {
            scheduler.shutdown();
            scheduler = null;
        }
        // Unregister broadcast receiver
        try {
            getContext().unregisterReceiver(usageUpdateReceiver);
        } catch (Exception e) {
            Log.e(TAG, "Error unregistering broadcast receiver", e);
        }
        super.handleOnDestroy();
    }
    
    /**
     * Check if the app has permission to access usage stats
     */
    private boolean checkUsageStatsPermission() {
        try {
            Context context = getContext();
            AppOpsManager appOps = (AppOpsManager) context.getSystemService(Context.APP_OPS_SERVICE);
            int mode = appOps.checkOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS, 
                                            android.os.Process.myUid(), 
                                            context.getPackageName());
            
            boolean hasPermission = mode == AppOpsManager.MODE_ALLOWED;
            
            Log.d(TAG, "checkUsageStatsPermission: Permission granted = " + hasPermission + 
                  " (mode = " + mode + ", MODE_ALLOWED = " + AppOpsManager.MODE_ALLOWED + ")");
            
            return hasPermission;
        } catch (Exception e) {
            Log.e(TAG, "Error checking usage stats permission", e);
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
     * Check if an app is a system app
     */
    private boolean isSystemApp(String packageName) {
        PackageManager packageManager = getContext().getPackageManager();
        try {
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
                if (packageName.equals(ourPackage) || isSystemApp(packageName)) {
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

    private long getStartOfDay() {
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
            int limit = call.getInt("limit", 60);
            Log.d(TAG, "Setting screen time limit to: " + limit);
            
            SharedPreferences prefs = getContext().getSharedPreferences("ScreenTimeReminder", Context.MODE_PRIVATE);
            prefs.edit()
                .putInt("screenTimeLimit", limit)
                .apply();
            
            Log.d(TAG, "Screen time limit updated successfully");
            call.resolve();
        } catch (Exception e) {
            Log.e(TAG, "Error setting screen time limit", e);
            call.reject("Failed to set screen time limit: " + e.getMessage(), e);
        }
    }
    
    @PluginMethod
    public void setNotificationFrequency(PluginCall call) {
        try {
            int frequency = call.getInt("frequency", 5);
            Log.d(TAG, "Setting notification frequency to: " + frequency);
            
            SharedPreferences prefs = getContext().getSharedPreferences("ScreenTimeReminder", Context.MODE_PRIVATE);
            prefs.edit()
                .putInt("notificationFrequency", frequency)
                .apply();
            
            Log.d(TAG, "Notification frequency updated successfully");
            call.resolve();
        } catch (Exception e) {
            Log.e(TAG, "Error setting notification frequency", e);
            call.reject("Failed to set notification frequency: " + e.getMessage(), e);
        }
    }

    private void updateSharedPreferences() {
        try {
            Log.d(TAG, "Updating shared preferences with latest screen time data");
            
            // Get current total screen time
            long totalTime = getTotalScreenTimeForToday();
            float totalMinutes = totalTime / (1000f * 60f);
            
            // Get shared preferences
            SharedPreferences prefs = getContext().getSharedPreferences("ScreenTimeReminder", Context.MODE_PRIVATE);
            
            // Update total screen time and timestamp
            prefs.edit()
                .putFloat("totalScreenTime", totalMinutes)
                .putLong("lastUpdateTime", System.currentTimeMillis())
                .apply();
            
            Log.d(TAG, String.format("Updated shared preferences - Total screen time: %.2f minutes", totalMinutes));
            
            // Check screen time limit and show notifications if needed
            checkScreenTimeLimit(totalMinutes);
            
            // Broadcast the update
            broadcastUsageUpdate(totalMinutes);
        } catch (Exception e) {
            Log.e(TAG, "Error updating shared preferences", e);
        }
    }

    private void broadcastUsageUpdate(float totalMinutes) {
        try {
            Log.d(TAG, "Broadcasting usage update");
            
            // Create a JSON object with the usage data
            JSONObject usageData = new JSONObject();
            usageData.put("totalMinutes", totalMinutes);
            usageData.put("timestamp", System.currentTimeMillis());
            
            // Broadcast the update
            Intent intent = new Intent("com.screentimereminder.APP_USAGE_UPDATE");
            intent.putExtra("usageData", usageData.toString());
            getContext().sendBroadcast(intent);
            
            Log.d(TAG, "Broadcasted usage update with data: " + usageData.toString());
        } catch (Exception e) {
            Log.e(TAG, "Error broadcasting usage update", e);
        }
    }

    private void checkScreenTimeLimit(float totalMinutes) {
        try {
            // Get screen time limit from SharedPreferences
            SharedPreferences prefs = getContext().getSharedPreferences("ScreenTimeReminder", Context.MODE_PRIVATE);
            int screenTimeLimit = prefs.getInt("screenTimeLimit", 60); // Default 60 minutes
            
            // Get current time for notification tracking
            long currentTime = System.currentTimeMillis();
            
            // Get last notification times
            long lastLimitReachedNotification = prefs.getLong("lastLimitReachedNotification", 0);
            long lastApproachingLimitNotification = prefs.getLong("lastApproachingLimitNotification", 0);
            
            // Define cooldown period (1 minute in milliseconds)
            long NOTIFICATION_COOLDOWN = 60 * 1000;
            
            // Calculate remaining minutes and percentage used
            float remainingMinutes = Math.max(0, screenTimeLimit - totalMinutes);
            float percentageUsed = (totalMinutes / screenTimeLimit) * 100;
            
            // Check if enough time has passed since last notifications
            boolean canShowLimitReached = (currentTime - lastLimitReachedNotification) >= NOTIFICATION_COOLDOWN;
            boolean canShowApproaching = (currentTime - lastApproachingLimitNotification) >= NOTIFICATION_COOLDOWN;
            
            Log.d(TAG, String.format("Checking screen time limit - Total: %.2f, Limit: %d, Remaining: %.2f, Percentage: %.1f%%, CanShowLimit: %b, CanShowApproaching: %b",
                totalMinutes, screenTimeLimit, remainingMinutes, percentageUsed, canShowLimitReached, canShowApproaching));
            
            // First check if limit is reached
            if (totalMinutes >= screenTimeLimit && canShowLimitReached) {
                Log.d(TAG, "Showing limit reached notification");
                showNotification("Screen Time Limit Reached", 
                    "You have reached your daily screen time limit of " + screenTimeLimit + " minutes.");
                prefs.edit().putLong("lastLimitReachedNotification", currentTime).apply();
            } 
            // Only show approaching limit if we haven't reached the limit yet and percentage is >= 80%
            else if (totalMinutes < screenTimeLimit && percentageUsed >= 80 && canShowApproaching) {
                Log.d(TAG, "Showing approaching limit notification");
                showNotification("Approaching Screen Time Limit", 
                    "You have " + Math.round(remainingMinutes) + " minutes remaining (" + Math.round(percentageUsed) + "% of limit used).");
                prefs.edit().putLong("lastApproachingLimitNotification", currentTime).apply();
            }
        } catch (Exception e) {
            Log.e(TAG, "Error checking screen time limit", e);
        }
    }

    private void showNotification(String title, String message) {
        try {
            // Get current total screen time
            SharedPreferences prefs = getContext().getSharedPreferences("ScreenTimeReminder", Context.MODE_PRIVATE);
            float totalScreenTime = prefs.getFloat("totalScreenTime", 0);
            
            // Format the time
            int hours = (int) (totalScreenTime / 60);
            int minutes = (int) (totalScreenTime % 60);
            String timeString = String.format("%d hours %d minutes", hours, minutes);
            
            // Create the notification message
            String notificationMessage = String.format("%s\nCurrent screen time: %s", message, timeString);
            
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
                .setContentTitle(title)
                .setContentText(notificationMessage)
                .setStyle(new NotificationCompat.BigTextStyle().bigText(notificationMessage))
                .setPriority(NotificationCompat.PRIORITY_DEFAULT);
            
            notificationManager.notify(1, builder.build());
            Log.d(TAG, "Showing notification: " + notificationMessage);
        } catch (Exception e) {
            Log.e(TAG, "Error showing notification", e);
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
} 