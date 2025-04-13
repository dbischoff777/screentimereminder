export const SettingsConstants = {
  // Screen time limits (in minutes)
  MIN_SCREEN_TIME_LIMIT: 30,
  MAX_SCREEN_TIME_LIMIT: 240,
  DEFAULT_SCREEN_TIME_LIMIT: 120,

  // Notification frequency (in minutes)
  MIN_NOTIFICATION_FREQUENCY: 5,
  MAX_NOTIFICATION_FREQUENCY: 60,
  DEFAULT_NOTIFICATION_FREQUENCY: 5,

  // Shared preferences keys
  PREFS_NAME: "ScreenTimeReminder",
  KEY_SCREEN_TIME_LIMIT: "screenTimeLimit",
  KEY_NOTIFICATION_FREQUENCY: "notificationFrequency",
  KEY_TOTAL_SCREEN_TIME: "totalScreenTime",
  KEY_LAST_UPDATE: "lastUpdateTime",
  KEY_LAST_LIMIT_NOTIFICATION: "lastLimitReachedNotification",
  KEY_LAST_APPROACHING_NOTIFICATION: "lastApproachingLimitNotification",
  KEY_USER_HAS_SET_LIMIT: "userHasSetLimit",
  KEY_LAST_SETTINGS_CHAIN_ID: "lastSettingsChainId",

  // Channel IDs
  NOTIFICATION_CHANNEL_ID: "screen_time_channel",
  NOTIFICATION_CHANNEL_NAME: "Screen Time Notifications",

  // Action constants
  ACTION_USAGE_UPDATE: "com.screentimereminder.app.APP_USAGE_UPDATE",
  ACTION_REFRESH_WIDGET: "com.screentimereminder.app.REFRESH_WIDGET",
  ACTION_BACKGROUND_DETECTED: "com.screentimereminder.app.BACKGROUND_DETECTED",
  ACTION_RESTART_SERVICE: "com.screentimereminder.app.RESTART_SERVICE",

  // Notification IDs
  NOTIFICATION_ID_LIMIT_REACHED: 1,
  NOTIFICATION_ID_APPROACHING_LIMIT: 2,
  NOTIFICATION_ID_BACKGROUND_SERVICE: 3,

  // Update intervals (in milliseconds)
  UPDATE_INTERVAL: 60000,
  WATCHDOG_INTERVAL: 10000,
  BACKGROUND_UPDATE_INTERVAL: 60000,
  PERMISSION_CHECK_INTERVAL: 1000,

  // Battery thresholds
  LOW_BATTERY_THRESHOLD: 15,
  CRITICAL_BATTERY_THRESHOLD: 5,

  // Update thresholds
  UPDATE_THRESHOLD: 500,
  DEBOUNCE_TIME: 1000,

  // Service constants
  SERVICE_RESTART_ALARM_ID: 1001,

  // Cache settings
  ICON_CACHE_DIR: "icon_cache",
  MAX_CACHED_ICONS: 100,

  // Battery-aware update intervals
  NORMAL_UPDATE_INTERVAL: 60000,
  LOW_BATTERY_UPDATE_INTERVAL: 300000,
  CRITICAL_BATTERY_UPDATE_INTERVAL: 900000,
  BACKGROUND_DETECTION_INTERVAL: 30000,
  BACKGROUND_USAGE_THRESHOLD: 120000,
  NOTIFICATION_COOLDOWN: 300000,
  CACHE_CLEANUP_INTERVAL: 86400000,
  SERVICE_RESTART_INTERVAL: 900000,
} as const;

export type SettingsConstantsType = typeof SettingsConstants; 