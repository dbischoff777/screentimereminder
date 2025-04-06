package com.screentimereminder.app;

// This file contains the AppUsageTracker implementation for tracking app usage on Android devices
// It uses the UsageStatsManager API to retrieve app usage data and provides methods for querying and processing this data

import android.app.AppOpsManager;
import android.app.usage.UsageEvents;
import android.app.usage.UsageStats;
import android.app.usage.UsageStatsManager;
import android.content.Context;
import android.content.Intent;
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

            // Request battery optimization exemption if needed
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                PowerManager powerManager = (PowerManager) getContext().getSystemService(Context.POWER_SERVICE);
                if (powerManager != null && !powerManager.isIgnoringBatteryOptimizations(getContext().getPackageName())) {
                    Intent intent = new Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS);
                    intent.setData(android.net.Uri.parse("package:" + getContext().getPackageName()));
                    intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                    getContext().startActivity(intent);
                }
            }

            // Start the background service
            Intent serviceIntent = new Intent(getContext(), AppUsageService.class);
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
                        ensureServiceRunning();
                    }
                }, 1, 5, TimeUnit.MINUTES);
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
            // Get parameters with null checks and default values
            JSObject data = call.getData();
            Integer totalTime = data.has("totalTime") ? data.getInteger("totalTime") : 0;
            Integer limit = data.has("limit") ? data.getInteger("limit") : 120;
            Integer remainingMinutes = data.has("remainingMinutes") ? data.getInteger("remainingMinutes") : Math.max(0, limit - totalTime);
            
            Log.d(TAG, String.format("Checking screen time with values from JS - Total: %d minutes, Limit: %d minutes, Remaining: %d minutes",
                totalTime, limit, remainingMinutes));
            
            // Save the limit to SharedPreferences
            SharedPreferences prefs = getContext().getSharedPreferences("ScreenTimeReminder", Context.MODE_PRIVATE);
            prefs.edit().putInt("screenTimeLimit", limit).apply();
            
            // Get SharedPreferences for notification tracking
            long currentTime = System.currentTimeMillis();
            
            // Get last notification times
            long lastLimitReachedNotification = prefs.getLong("lastLimitReachedNotification", 0);
            long lastApproachingLimitNotification = prefs.getLong("lastApproachingLimitNotification", 0);
            
            // Define cooldown period (1 minute in milliseconds)
            long NOTIFICATION_COOLDOWN = 60 * 1000;
            
            // Check if enough time has passed since last notifications
            boolean canShowLimitReached = (currentTime - lastLimitReachedNotification) >= NOTIFICATION_COOLDOWN;
            boolean canShowApproaching = (currentTime - lastApproachingLimitNotification) >= NOTIFICATION_COOLDOWN;
            
            if (totalTime >= limit && canShowLimitReached) {
                // Show limit reached notification with exact data from JS
                try {
                    notificationService.showLimitReachedNotification(totalTime, limit);
                    Log.d(TAG, "Limit reached notification sent successfully with data from JS");
                    // Update last notification time
                    prefs.edit().putLong("lastLimitReachedNotification", currentTime).apply();
                } catch (Exception e) {
                    Log.e(TAG, "Error showing limit reached notification", e);
                    throw e;
                }
            } else if (remainingMinutes <= 5 && remainingMinutes > 0 && canShowApproaching) {
                // Show approaching limit notification with exact data from JS
                try {
                    notificationService.showApproachingLimitNotification(totalTime, limit, remainingMinutes);
                    Log.d(TAG, "Approaching limit notification sent successfully with data from JS");
                    // Update last notification time
                    prefs.edit().putLong("lastApproachingLimitNotification", currentTime).apply();
                } catch (Exception e) {
                    Log.e(TAG, "Error showing approaching limit notification", e);
                    throw e;
                }
            } else {
                Log.d(TAG, "Notifications skipped due to cooldown or conditions not met");
            }
            
            JSObject ret = new JSObject();
            ret.put("value", true);
            call.resolve(ret);
            Log.d(TAG, "checkScreenTimeLimit completed successfully");
        } catch (Exception e) {
            Log.e(TAG, "Error in checkScreenTimeLimit", e);
            call.reject("Failed to check screen time limit: " + e.getMessage(), e);
        }
    }
    
    @Override
    protected void handleOnDestroy() {
        if (scheduler != null && !scheduler.isShutdown()) {
            scheduler.shutdown();
            scheduler = null;
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
            for (android.app.ActivityManager.RunningServiceInfo service : manager.getRunningServices(Integer.MAX_VALUE)) {
                if (AppUsageService.class.getName().equals(service.service.getClassName())) {
                    Log.d(TAG, "Service is already running");
                    return;
                }
            }
            
            Log.d(TAG, "Service not running, restarting...");
            Intent serviceIntent = new Intent(getContext(), AppUsageService.class);
            serviceIntent.setAction("START_TRACKING");
            
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                getContext().startForegroundService(serviceIntent);
            } else {
                getContext().startService(serviceIntent);
            }
        } catch (Exception e) {
            Log.e(TAG, "Error ensuring service is running", e);
        }
    }
} 