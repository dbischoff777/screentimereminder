package com.screentimereminder.app;

/**
 * Centralized settings constants for the app.
 * All components should use these constants instead of defining their own.
 */
public class SettingsConstants {
    // Screen time limits (in minutes)
    public static final long MIN_SCREEN_TIME_LIMIT = 30;  // 30 minutes
    public static final long MAX_SCREEN_TIME_LIMIT = 480; // 8 hours (increased from 4 hours)
    public static final long DEFAULT_SCREEN_TIME_LIMIT = 180; // 3 hours (increased from 2 hours)

    // Notification frequency (in minutes)
    public static final long MIN_NOTIFICATION_FREQUENCY = 5;  // 5 minutes
    public static final long MAX_NOTIFICATION_FREQUENCY = 60; // 1 hour
    public static final long DEFAULT_NOTIFICATION_FREQUENCY = 5; // 5 minutes

    // Shared preferences keys
    public static final String PREFS_NAME = "ScreenTimeReminder";
    public static final String KEY_SCREEN_TIME_LIMIT = "screenTimeLimit";
    public static final String KEY_NOTIFICATION_FREQUENCY = "notificationFrequency";
    public static final String KEY_TOTAL_SCREEN_TIME = "totalScreenTime";
    public static final String KEY_LAST_UPDATE = "lastUpdateTime";
    public static final String KEY_LAST_LIMIT_NOTIFICATION = "lastLimitReachedNotification";
    public static final String KEY_LAST_APPROACHING_NOTIFICATION = "lastApproachingLimitNotification";
    public static final String KEY_USER_HAS_SET_LIMIT = "userHasSetLimit";
    public static final String KEY_LAST_SETTINGS_CHAIN_ID = "lastSettingsChainId";

    // Channel IDs
    public static final String NOTIFICATION_CHANNEL_ID = "screen_time_channel";
    public static final String NOTIFICATION_CHANNEL_NAME = "Screen Time Notifications";
    public static final String NOTIFICATION_CHANNEL_DESCRIPTION = "Notifications for screen time limits and updates";

    // Action constants
    public static final String ACTION_USAGE_UPDATE = "com.screentimereminder.app.APP_USAGE_UPDATE";
    public static final String ACTION_REFRESH_WIDGET = "com.screentimereminder.app.REFRESH_WIDGET";
    public static final String ACTION_BACKGROUND_DETECTED = "com.screentimereminder.app.BACKGROUND_DETECTED";
    public static final String ACTION_RESTART_SERVICE = "com.screentimereminder.app.RESTART_SERVICE";

    // Notification IDs
    public static final int NOTIFICATION_ID_LIMIT_REACHED = 1;
    public static final int NOTIFICATION_ID_APPROACHING_LIMIT = 2;
    public static final int NOTIFICATION_ID_BACKGROUND_SERVICE = 3;

    // Update intervals (in milliseconds)
    public static final long UPDATE_INTERVAL = 60000; // 1 minute
    public static final long WATCHDOG_INTERVAL = 10000; // 10 seconds
    public static final long BACKGROUND_UPDATE_INTERVAL = 60000; // 1 minute
    public static final long PERMISSION_CHECK_INTERVAL = 1000; // 1 second

    // Battery thresholds
    public static final int LOW_BATTERY_THRESHOLD = 15; // 15%
    public static final int CRITICAL_BATTERY_THRESHOLD = 5; // 5%

    // Update thresholds
    public static final long UPDATE_THRESHOLD = 500; // 500ms
    public static final long DEBOUNCE_TIME = 1000; // 1 second

    // Service constants
    public static final int SERVICE_RESTART_ALARM_ID = 1001;

    // Cache settings
    public static final String ICON_CACHE_DIR = "icon_cache";
    public static final int MAX_CACHED_ICONS = 100;

    // Battery-aware update intervals
    public static final long NORMAL_UPDATE_INTERVAL = 60000; // 1 minute
    public static final long LOW_BATTERY_UPDATE_INTERVAL = 300000; // 5 minutes
    public static final long CRITICAL_BATTERY_UPDATE_INTERVAL = 900000; // 15 minutes
    public static final long BACKGROUND_DETECTION_INTERVAL = 30000; // 30 seconds
    public static final long BACKGROUND_USAGE_THRESHOLD = 120000; // 2 minutes
    public static final long NOTIFICATION_COOLDOWN = 300000; // 5 minutes
    public static final long CACHE_CLEANUP_INTERVAL = 86400000; // 24 hours
    public static final long SERVICE_RESTART_INTERVAL = 900000; // 15 minutes

    private SettingsConstants() {
        // Private constructor to prevent instantiation
    }
} 