export interface AppUsageTrackerPlugin {
  hasUsagePermission(): Promise<{ value: boolean }>;
  requestUsagePermission(): Promise<void>;
  startTracking(): Promise<{ value: boolean }>;
  stopTracking(): Promise<{ value: boolean }>;
  getAppUsageData(options?: { startTime?: number; endTime?: number }): Promise<{ data: string }>;
  addListener(
    eventName: 'appChanged',
    listenerFunc: (data: { packageName: string; appName: string }) => void
  ): Promise<{ remove: () => void }>;
  addListener(
    eventName: 'usageUpdate',
    listenerFunc: (data: { usageData: string }) => void
  ): Promise<{ remove: () => void }>;
  removeAllListeners(): Promise<void>;
  isBatteryOptimizationExempt(): Promise<{ value: boolean }>;
  requestBatteryOptimizationExemption(): Promise<void>;
  checkScreenTimeLimit(params: {
    totalTime: number;
    limit: number;
    notificationFrequency: number;
  }): Promise<void>;
  getSharedPreferences(): Promise<{
    lastLimitReachedNotification: number;
    lastApproachingLimitNotification: number;
    screenTimeLimit: number;
    notificationFrequency: number;
    totalScreenTime: number;
  }>;
  setScreenTimeLimit(params: { limitMinutes: number }): Promise<void>;
  setNotificationFrequency(params: { frequency: number }): Promise<void>;
  getScreenTimeLimit(): Promise<{ value: number }>;
  getNotificationFrequency(): Promise<{ value: number }>;
} 