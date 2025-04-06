import { Capacitor } from '@capacitor/core';
import { App } from '@capacitor/app';
import { registerPlugin } from '@capacitor/core';

interface AppUsageTrackerPlugin {
  addListener(eventName: string, callback: (event: any) => void): void;
  removeAllListeners(): void;
  startTracking(): Promise<void>;
  stopTracking(): Promise<void>;
  getAppUsageData(): Promise<{ value: string }>;
  getSharedPreferences(): Promise<{ 
    totalScreenTime: number;
    lastUpdateTime: number;
    screenTimeLimit: number;
    notificationFrequency: number;
    lastLimitReachedNotification: number;
    lastApproachingLimitNotification: number;
  }>;
}

const AppUsageTracker = registerPlugin<AppUsageTrackerPlugin>('AppUsageTracker');

class BackgroundUpdateService {
  private static instance: BackgroundUpdateService;
  private updateCallback: (() => void) | null = null;
  private lastUpdateTime: number = 0;
  private isTracking: boolean = false;
  private readonly UPDATE_INTERVAL = 30000; // 30 seconds
  private appStateListener: any = null;
  private usageUpdateListener: any = null;
  private lastBackgroundUpdateTime: number = 0;
  private backgroundUpdateInterval: any = null;
  private sharedPreferences: any = null;

  private constructor() {
    this.setupEventListeners();
  }

  public static getInstance(): BackgroundUpdateService {
    if (!BackgroundUpdateService.instance) {
      BackgroundUpdateService.instance = new BackgroundUpdateService();
    }
    return BackgroundUpdateService.instance;
  }

  private setupEventListeners() {
    console.log('Setting up event listeners for background updates');
    
    // Listen for app state changes
    this.appStateListener = App.addListener('appStateChange', ({ isActive }) => {
      console.log('App state changed:', isActive ? 'active' : 'background');
      
      if (!isActive) {
        // App went to background, start background updates
        this.startBackgroundUpdates();
      } else {
        // App came to foreground, stop background updates
        this.stopBackgroundUpdates();
      }
      
      if (this.updateCallback) {
        this.updateCallback();
      }
    });

    // Listen for app usage updates from native
    if (Capacitor.isNativePlatform()) {
      console.log('Setting up native platform listeners');
      
      // Start tracking in native code
      AppUsageTracker.startTracking().catch(error => {
        console.error('Error starting tracking:', error);
      });

      this.usageUpdateListener = AppUsageTracker.addListener('appUsageUpdate', (event: any) => {
        try {
          const currentTime = Date.now();
          console.log('Received app usage update event:', event);
          
          // Parse the usage data
          let usageData;
          try {
            usageData = JSON.parse(event.usageData);
          } catch (e) {
            console.error('Error parsing usage data:', e);
            return;
          }
          
          const totalMinutes = usageData.totalMinutes;
          const timestamp = usageData.timestamp;
          
          // Update shared preferences
          this.sharedPreferences = {
            ...this.sharedPreferences,
            totalScreenTime: totalMinutes,
            lastUpdateTime: timestamp
          };
          
          // Always update the last background update time
          this.lastBackgroundUpdateTime = timestamp;
          console.log('Background update - Total screen time:', {
            minutes: totalMinutes,
            hours: (totalMinutes / 60).toFixed(2),
            timestamp: new Date(timestamp).toISOString()
          });
          
          // Only trigger callback if enough time has passed
          if (currentTime - this.lastUpdateTime >= this.UPDATE_INTERVAL) {
            if (this.updateCallback) {
              this.updateCallback();
              this.lastUpdateTime = currentTime;
            }
          }
        } catch (error) {
          console.error('Error processing background update:', error);
        }
      });
    } else {
      console.log('Not a native platform, skipping native listeners');
    }
  }

  private async startBackgroundUpdates() {
    if (this.backgroundUpdateInterval) {
      console.log('Background updates already running');
      return;
    }

    console.log('Starting background updates');
    
    // Get initial shared preferences
    try {
      const prefs = await AppUsageTracker.getSharedPreferences();
      this.sharedPreferences = prefs;
      console.log('Initial shared preferences:', prefs);
      
      // Check if we need to show notifications based on initial data
      this.checkAndTriggerNotifications(prefs);
    } catch (error) {
      console.error('Error getting initial shared preferences:', error);
    }

    this.backgroundUpdateInterval = setInterval(async () => {
      try {
        // Get latest shared preferences from system
        const prefs = await AppUsageTracker.getSharedPreferences();
        this.sharedPreferences = prefs;
        console.log('Updated shared preferences from system:', prefs);
        
        // Check if we need to show notifications based on updated data
        this.checkAndTriggerNotifications(prefs);
        
        if (this.updateCallback) {
          this.updateCallback();
        }
      } catch (error) {
        console.error('Error in background update:', error);
      }
    }, this.UPDATE_INTERVAL);
  }

  private checkAndTriggerNotifications(prefs: any) {
    try {
      const totalMinutes = prefs.totalScreenTime;
      const screenTimeLimit = prefs.screenTimeLimit;
      const notificationFrequency = prefs.notificationFrequency;
      const lastLimitReached = prefs.lastLimitReachedNotification;
      const lastApproachingLimit = prefs.lastApproachingLimitNotification;
      
      const currentTime = Date.now();
      const NOTIFICATION_COOLDOWN = 60 * 1000; // 1 minute cooldown
      
      // Calculate remaining minutes
      const remainingMinutes = Math.max(0, screenTimeLimit - totalMinutes);
      
      // Check if enough time has passed since last notifications
      const canShowLimitReached = (currentTime - lastLimitReached) >= NOTIFICATION_COOLDOWN;
      const canShowApproaching = (currentTime - lastApproachingLimit) >= NOTIFICATION_COOLDOWN;
      
      console.log('Checking notifications:', {
        totalMinutes,
        screenTimeLimit,
        remainingMinutes,
        canShowLimitReached,
        canShowApproaching
      });
      
      if (totalMinutes >= screenTimeLimit && canShowLimitReached) {
        console.log('Triggering limit reached notification');
        // Trigger notification through native code
        AppUsageTracker.startTracking().catch(error => {
          console.error('Error triggering notification:', error);
        });
      } else if (remainingMinutes <= notificationFrequency && remainingMinutes > 0 && canShowApproaching) {
        console.log('Triggering approaching limit notification');
        // Trigger notification through native code
        AppUsageTracker.startTracking().catch(error => {
          console.error('Error triggering notification:', error);
        });
      }
    } catch (error) {
      console.error('Error checking notifications:', error);
    }
  }

  private stopBackgroundUpdates() {
    if (this.backgroundUpdateInterval) {
      console.log('Stopping background updates');
      clearInterval(this.backgroundUpdateInterval);
      this.backgroundUpdateInterval = null;
    }
  }

  public setUpdateCallback(callback: () => void) {
    if (!this.isTracking) {
      console.log('Starting background tracking');
      this.isTracking = true;
      this.updateCallback = callback;
      
      // Initial update
      if (callback) {
        console.log('Performing initial update');
        callback();
      }
    } else {
      console.log('Background tracking already active');
    }
  }

  public removeUpdateCallback() {
    if (this.isTracking) {
      console.log('Stopping background tracking');
      this.isTracking = false;
      this.updateCallback = null;
      
      // Stop tracking in native code
      if (Capacitor.isNativePlatform()) {
        AppUsageTracker.stopTracking().catch(error => {
          console.error('Error stopping tracking:', error);
        });
      }
    }
  }

  public cleanup() {
    console.log('Cleaning up background service');
    this.removeUpdateCallback();
    this.stopBackgroundUpdates();
    if (this.appStateListener) {
      this.appStateListener.remove();
      this.appStateListener = null;
    }
    if (this.usageUpdateListener) {
      this.usageUpdateListener.remove();
      this.usageUpdateListener = null;
    }
  }

  public getServiceStatus() {
    return {
      isTracking: this.isTracking,
      updateInterval: this.UPDATE_INTERVAL,
      lastUpdateTime: this.lastUpdateTime,
      lastBackgroundUpdateTime: this.lastBackgroundUpdateTime,
      lastUpdateTimeFormatted: new Date(this.lastUpdateTime).toISOString(),
      lastBackgroundUpdateTimeFormatted: this.lastBackgroundUpdateTime ? 
        new Date(this.lastBackgroundUpdateTime).toISOString() : 'Never',
      isBackgroundUpdateRunning: !!this.backgroundUpdateInterval,
      sharedPreferences: this.sharedPreferences
    };
  }

  public async getCurrentScreenTime(): Promise<number> {
    try {
      // Always get fresh data from system
      const prefs = await AppUsageTracker.getSharedPreferences();
      this.sharedPreferences = prefs;
      return prefs.totalScreenTime;
    } catch (error) {
      console.error('Error getting current screen time:', error);
      return 0;
    }
  }

  public async getScreenTimeLimit(): Promise<number> {
    try {
      const prefs = await AppUsageTracker.getSharedPreferences();
      return prefs.screenTimeLimit;
    } catch (error) {
      console.error('Error getting screen time limit:', error);
      return 120; // Default 2 hours
    }
  }

  public async getNotificationFrequency(): Promise<number> {
    try {
      const prefs = await AppUsageTracker.getSharedPreferences();
      return prefs.notificationFrequency;
    } catch (error) {
      console.error('Error getting notification frequency:', error);
      return 15; // Default 15 minutes
    }
  }

  public async getLastNotificationTimes(): Promise<{
    lastLimitReached: number;
    lastApproachingLimit: number;
  }> {
    try {
      const prefs = await AppUsageTracker.getSharedPreferences();
      return {
        lastLimitReached: prefs.lastLimitReachedNotification,
        lastApproachingLimit: prefs.lastApproachingLimitNotification
      };
    } catch (error) {
      console.error('Error getting last notification times:', error);
      return {
        lastLimitReached: 0,
        lastApproachingLimit: 0
      };
    }
  }
}

export default BackgroundUpdateService; 