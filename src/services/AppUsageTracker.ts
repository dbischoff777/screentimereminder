import { registerPlugin } from '@capacitor/core';
import { AppUsageTrackerPlugin } from './definitions';
import { SettingsConstants } from '../constants/SettingsConstants';

// Use the existing AppUsage interface from ScreenTimeContext
// This is just for reference - we'll import the actual interface
export interface AppUsage {
  name: string;
  time: number; // time in minutes
  color: string;
  lastUsed?: Date;
  category: string;
  isActive?: boolean;
  icon?: string; // Base64 encoded app icon
}

// Register the AppUsageTracker plugin
const AppUsageTracker = registerPlugin<AppUsageTrackerPlugin>('AppUsageTracker');

// Create a class to handle app usage tracking
export default class AppUsageTrackerService {
  private static instance: AppUsageTrackerService;
  private isTracking = false;
  private listeners: Array<(usageData: AppUsage[]) => void> = [];
  private plugin: any;

  private constructor() {
    this.plugin = AppUsageTracker;
    // Private constructor to enforce singleton
    this.setupBroadcastListener();
  }

  public static getInstance(): AppUsageTrackerService {
    if (!AppUsageTrackerService.instance) {
      AppUsageTrackerService.instance = new AppUsageTrackerService();
    }
    return AppUsageTrackerService.instance;
  }

  /**
   * Set up a listener for broadcast messages from the native side
   */
  private setupBroadcastListener() {
    try {
      // Listen for app usage updates from the native side
      document.addEventListener('com.screentimereminder.APP_USAGE_UPDATE', (event: any) => {
        try {
          const data = JSON.parse(event.detail.usageData);
          // Update total screen time in localStorage
          if (data.totalScreenTime !== undefined) {
            localStorage.setItem('totalTodayScreenTime', data.totalScreenTime.toString());
          }
          // Notify listeners with app usage data
          if (data.apps) {
            this.notifyListeners(data.apps);
          }
        } catch (error) {
          console.error('Error processing app usage update:', error);
        }
      });
    } catch (error) {
      console.error('Error setting up broadcast listener:', error);
    }
  }

  /**
   * Check if the app has permission to access usage stats
   */
  public async hasUsagePermission(): Promise<boolean> {
    try {
      console.log('AppUsageTracker: Checking usage permission');
      const result = await AppUsageTracker.hasUsagePermission();
      console.log('AppUsageTracker: Permission check result:', result.value);
      return result.value;
    } catch (error) {
      console.error('Error checking usage permission:', error);
      return false;
    }
  }

  /**
   * Request permission to access usage stats
   */
  public async requestUsagePermission(): Promise<void> {
    try {
      console.log('AppUsageTracker: Requesting usage permission');
      await AppUsageTracker.requestUsagePermission();
      console.log('AppUsageTracker: Permission request completed');
      
      // Wait a moment for the settings page to open
      await new Promise(resolve => setTimeout(resolve, 500));
      
      // Check if permission was granted after a delay
      setTimeout(async () => {
        const hasPermission = await this.hasUsagePermission();
        console.log('AppUsageTracker: Permission check after request:', hasPermission);
      }, 5000);
    } catch (error) {
      console.error('Error requesting usage permission:', error);
      throw error;
    }
  }

  /**
   * Start tracking app usage
   */
  public async startTracking(): Promise<boolean> {
    try {
      // Check if we have permission
      console.log('AppUsageTracker: Checking permission before starting tracking');
      const hasPermission = await this.hasUsagePermission();
      if (!hasPermission) {
        console.warn('AppUsageTracker: Usage stats permission not granted');
        console.log('AppUsageTracker: Attempting to request permission');
        await this.requestUsagePermission();
        return false;
      }

      // Start tracking
      console.log('AppUsageTracker: Starting tracking');
      const result = await AppUsageTracker.startTracking();
      this.isTracking = result.value;
      console.log('AppUsageTracker: Tracking started, result:', result.value);
      return result.value;
    } catch (error) {
      console.error('Error starting app usage tracking:', error);
      return false;
    }
  }

  /**
   * Stop tracking app usage
   */
  public async stopTracking(): Promise<boolean> {
    try {
      const result = await AppUsageTracker.stopTracking();
      this.isTracking = false;
      return result.value;
    } catch (error) {
      console.error('Error stopping app usage tracking:', error);
      return false;
    }
  }

  /**
   * Get app usage data for a specific time range
   */
  public async getAppUsageData(startTime?: number, endTime?: number): Promise<AppUsage[]> {
    try {
      // Set default time range if not provided (last 24 hours)
      const now = Date.now();
      const start = startTime || (now - 24 * 60 * 60 * 1000);
      const end = endTime || now;
      
      console.log(`AppUsageTracker: Getting app usage data from ${new Date(start).toLocaleString()} to ${new Date(end).toLocaleString()}`);
      
      // First check if we have permission
      const hasPermission = await this.hasUsagePermission();
      if (!hasPermission) {
        console.warn('AppUsageTracker: Usage stats permission not granted');
        return [];
      }

      // Get data from native layer
      const result = await AppUsageTracker.getAppUsageData({ startTime: start, endTime: end });
      if (!result.data) {
        console.warn('AppUsageTracker: No data received from native layer');
        return [];
      }

      // Parse the JSON data
      const parsedData = JSON.parse(result.data);
      if (!parsedData.apps || !Array.isArray(parsedData.apps)) {
        console.warn('AppUsageTracker: Invalid data format received');
        return [];
      }

      // Transform the data into AppUsage format
      const appUsageData: AppUsage[] = parsedData.apps.map((app: any) => {
        // Determine the category based on app name if not provided
        const category = app.category || this.getCategoryForApp(app.name);
        
        // Get color based on category or generate one based on app name
        const color = category !== 'Other' ? 
          this.getCategoryColor(category) : 
          this.getColorForApp(app.name);

        return {
          name: app.name || 'Unknown App',
          time: app.time || 0,
          color: color,
          lastUsed: app.lastUsed ? new Date(app.lastUsed) : undefined,
          category: category,
          isActive: false,
          icon: app.icon || undefined
        };
      });

      // Sort by usage time in descending order
      appUsageData.sort((a, b) => b.time - a.time);

      // Cache the data
      localStorage.setItem('lastAppUsageData', JSON.stringify(appUsageData));
      localStorage.setItem('lastAppUsageUpdate', Date.now().toString());

      console.log('AppUsageTracker: Processed app usage data:', appUsageData);
      return appUsageData;
    } catch (error) {
      console.error('Error getting app usage data:', error);
      return [];
    }
  }

  /**
   * Get app usage data for the last hour
   */
  public async getLastHourUsage(): Promise<AppUsage[]> {
    const now = Date.now();
    const oneHourAgo = now - (60 * 60 * 1000); // 1 hour in milliseconds
    console.log(`AppUsageTracker: Getting last hour usage (${new Date(oneHourAgo).toLocaleTimeString()} to ${new Date(now).toLocaleTimeString()})`);
    
    // Get the data
    const data = await this.getAppUsageData(oneHourAgo, now);
    
    // Filter out apps with very little usage (less than 5 seconds)
    // This helps eliminate background processes that weren't actually used
    return data.filter(app => app.time > 0.08); // 0.08 minutes = ~5 seconds
  }

  /**
   * Get a color for a category
   */
  private getCategoryColor(category: string): string {
    switch (category.toLowerCase()) {
      case 'social media':
        return '#FF00FF';
      case 'entertainment':
        return '#FF5733';
      case 'productivity':
        return '#33FF57';
      case 'games':
        return '#00FFFF';
      case 'education':
        return '#3357FF';
      default:
        return '#A833FF';
    }
  }

  /**
   * Generate a color for an app based on its name
   */
  private getColorForApp(appName: string): string {
    // Simple hash function to generate a consistent color
    let hash = 0;
    for (let i = 0; i < appName.length; i++) {
      hash = appName.charCodeAt(i) + ((hash << 5) - hash);
    }
    
    // Convert to hex color
    let color = '#';
    for (let i = 0; i < 3; i++) {
      const value = (hash >> (i * 8)) & 0xFF;
      color += ('00' + value.toString(16)).substr(-2);
    }
    
    return color;
  }

  /**
   * Determine category for an app based on its name
   */
  private getCategoryForApp(appName: string): string {
    const appNameLower = appName.toLowerCase();
    
    if (/instagram|facebook|twitter|tiktok|snapchat|whatsapp|telegram|messenger/i.test(appNameLower)) {
      return 'Social Media';
    } else if (/youtube|netflix|hulu|disney|spotify|music|video|player|movie/i.test(appNameLower)) {
      return 'Entertainment';
    } else if (/chrome|safari|firefox|edge|browser|gmail|outlook|office|word|excel|powerpoint|docs/i.test(appNameLower)) {
      return 'Productivity';
    } else if (/game|games|gaming|play|puzzle|candy|clash|craft/i.test(appNameLower)) {
      return 'Games';
    } else if (/learn|education|school|course|study|duolingo|khan|quiz/i.test(appNameLower)) {
      return 'Education';
    } else {
      return 'Other';
    }
  }

  /**
   * Add a listener for app usage updates
   */
  public addListener(callback: (usageData: AppUsage[]) => void): () => void {
    this.listeners.push(callback);
    return () => {
      this.listeners = this.listeners.filter(listener => listener !== callback);
    };
  }

  /**
   * Remove all listeners
   */
  public removeAllListeners(): void {
    this.listeners = [];
    AppUsageTracker.removeAllListeners();
  }

  /**
   * Notify all listeners of app usage updates
   */
  private notifyListeners(usageData: AppUsage[]): void {
    this.listeners.forEach(listener => {
      try {
        listener(usageData);
      } catch (error) {
        console.error('Error in app usage listener:', error);
      }
    });
  }

  /**
   * Check if tracking is active
   */
  public isTrackingActive(): boolean {
    return this.isTracking;
  }

  /**
   * Check if the app is exempt from battery optimization
   */
  public async isBatteryOptimizationExempt(): Promise<boolean> {
    try {
      console.log('AppUsageTracker: Checking battery optimization exemption');
      const result = await AppUsageTracker.isBatteryOptimizationExempt();
      console.log('AppUsageTracker: Battery optimization exemption result:', result.value);
      return result.value;
    } catch (error) {
      console.error('Error checking battery optimization exemption:', error);
      return false;
    }
  }

  /**
   * Request exemption from battery optimization
   */
  public async requestBatteryOptimizationExemption(): Promise<void> {
    try {
      console.log('AppUsageTracker: Requesting battery optimization exemption');
      await AppUsageTracker.requestBatteryOptimizationExemption();
      console.log('AppUsageTracker: Battery optimization exemption request completed');
    } catch (error) {
      console.error('Error requesting battery optimization exemption:', error);
      throw error;
    }
  }

  public async checkScreenTimeLimit(params: {
    totalTime: number;
    limit: number;
    notificationFrequency: number;
  }): Promise<void> {
    try {
      await this.plugin.checkScreenTimeLimit(params);
    } catch (error) {
      console.error('Error checking screen time limit:', error);
      throw error;
    }
  }

  /**
   * Get shared preferences from the native layer
   */
  public async getSharedPreferences(): Promise<{
    lastLimitReachedNotification: number;
    lastApproachingLimitNotification: number;
    screenTimeLimit: number;
    notificationFrequency: number;
    totalScreenTime: number;
  }> {
    try {
      console.log('AppUsageTracker: Getting shared preferences');
      const result = await this.plugin.getSharedPreferences();
      console.log('AppUsageTracker: Shared preferences result:', result);
      return result;
    } catch (error) {
      console.error('Error getting shared preferences:', error);
      // Return default values from SettingsConstants
      return {
        lastLimitReachedNotification: 0,
        lastApproachingLimitNotification: 0,
        screenTimeLimit: SettingsConstants.DEFAULT_SCREEN_TIME_LIMIT,
        notificationFrequency: SettingsConstants.DEFAULT_NOTIFICATION_FREQUENCY,
        totalScreenTime: 0
      };
    }
  }

  public async setScreenTimeLimit(limit: number): Promise<void> {
    try {
      console.log('Setting screen time limit in native:', limit);
      await this.plugin.setScreenTimeLimit({ limitMinutes: limit });
    } catch (error) {
      console.error('Error setting screen time limit:', error);
      throw error;
    }
  }

  /**
   * Set notification frequency
   */
  public async setNotificationFrequency(params: { frequency: number }): Promise<void> {
    try {
      console.log('AppUsageTracker: Setting notification frequency to:', params.frequency);
      await this.plugin.setNotificationFrequency(params);
      console.log('AppUsageTracker: Notification frequency set successfully');
    } catch (error) {
      console.error('Error setting notification frequency:', error);
      throw error;
    }
  }

  /**
   * Get notification frequency
   */
  public async getNotificationFrequency(): Promise<number> {
    try {
      console.log('AppUsageTracker: Getting notification frequency');
      const result = await this.plugin.getNotificationFrequency();
      console.log('AppUsageTracker: Got notification frequency:', result.value);
      return result.value;
    } catch (error) {
      console.error('Error getting notification frequency:', error);
      return SettingsConstants.DEFAULT_NOTIFICATION_FREQUENCY; // Default to 5 minutes
    }
  }

  /**
   * Get screen time limit
   */
  public async getScreenTimeLimit(): Promise<number> {
    try {
      console.log('AppUsageTracker: Getting screen time limit');
      const result = await this.plugin.getScreenTimeLimit();
      console.log('AppUsageTracker: Got screen time limit:', result.value);
      return result.value;
    } catch (error) {
      console.error('Error getting screen time limit:', error);
      return SettingsConstants.MIN_SCREEN_TIME_LIMIT; // Default to minimum screen time limit
    }
  }

  /**
   * Get total screen time for the current day
   */
  public async getTotalScreenTime(): Promise<number> {
    try {
      console.log('AppUsageTracker: Getting total screen time');
      const prefs = await this.getSharedPreferences();
      return prefs.totalScreenTime;
    } catch (error) {
      console.error('Error getting total screen time:', error);
      return 0;
    }
  }
} 