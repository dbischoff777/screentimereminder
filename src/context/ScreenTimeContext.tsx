import React, { createContext, useContext, useState, useEffect, ReactNode } from 'react';
import AppUsageTrackerService, { AppUsage as TrackerAppUsage } from '../services/AppUsageTracker';
import BackgroundUpdateService from '../services/BackgroundUpdateService';

// Add type declaration for Capacitor on window object
declare global {
  interface Window {
    Capacitor?: {
      isNativePlatform: () => boolean;
    };
  }
}

// Interface for app usage data
interface AppUsage {
  name: string;
  time: number; // time in minutes
  color: string;
  lastUsed?: Date;
  category: string; // Added category field
  isActive?: boolean; // Track if app is currently active
  icon?: string; // Base64 encoded app icon
}

interface ScreenTimeContextType {
  screenTimeLimit: number;
  setScreenTimeLimit: (limit: number) => void;
  notificationsEnabled: boolean;
  setNotificationsEnabled: (enabled: boolean) => void;
  notificationFrequency: number;
  setNotificationFrequency: (frequency: number) => void;
  // Usage access permission
  usageAccessEnabled: boolean;
  setUsageAccessEnabled: (enabled: boolean) => void;
  // App tracking functionality
  appUsageData: AppUsage[];
  getTotalScreenTime: () => number;
  startTrackingApp: (appName: string, category: string) => void;
  stopTrackingApp: (appName: string) => void;
  resetDailyUsage: () => void;
  // App usage data retrieval
  getAppUsageData: (startTime?: number, endTime?: number) => Promise<TrackerAppUsage[]>;
  getLastHourUsage: () => Promise<TrackerAppUsage[]>;
  // New function
  updateAppUsageData: () => Promise<boolean>;
}

const ScreenTimeContext = createContext<ScreenTimeContextType | undefined>(undefined);

// More realistic app categories with proper categorization
const defaultAppCategories: AppUsage[] = [];

// Get category color mapping
const getCategoryColor = (category: string): string => {
  switch (category) {
    case 'Social Media': return '#FF00FF';
    case 'Entertainment': return '#FF5733';
    case 'Productivity': return '#33FF57';
    case 'Games': return '#00FFFF';
    case 'Education': return '#3357FF';
    default: return '#A833FF';
  }
};

export const ScreenTimeProvider: React.FC<{ children: ReactNode }> = ({ children }) => {
  // Screen time limit in minutes
  const [screenTimeLimit, setScreenTimeLimit] = useState<number>(() => {
    const saved = localStorage.getItem('screenTimeLimit');
    const parsed = saved ? parseInt(saved, 10) : 120; // Default 2 hours
    console.log('Initializing screen time limit from localStorage:', saved, 'parsed:', parsed);
    return parsed;
  });

  // Notification settings
  const [notificationsEnabled, setNotificationsEnabled] = useState<boolean>(() => {
    const saved = localStorage.getItem('notificationsEnabled');
    return saved ? saved === 'true' : false;
  });

  const [notificationFrequency, setNotificationFrequency] = useState<number>(() => {
    const saved = localStorage.getItem('notificationFrequency');
    return saved ? parseInt(saved, 10) : 15; // Default 15 minutes
  });

  // Usage access permission setting
  const [usageAccessEnabled, setUsageAccessEnabled] = useState<boolean>(() => {
    const saved = localStorage.getItem('usageAccessEnabled');
    return saved ? saved === 'true' : false;
  });

  // App tracking state
  const [appUsageData, setAppUsageData] = useState<AppUsage[]>(() => {
    const saved = localStorage.getItem('appUsageData');
    return saved ? JSON.parse(saved) : defaultAppCategories;
  });

  // Track currently active app
  const [activeApp, setActiveApp] = useState<string | null>(null);
  const [trackingStartTime, setTrackingStartTime] = useState<number | null>(null);
  
  // Check screen time limit
  const checkLimit = async () => {
    try {
      const appUsageTracker = AppUsageTrackerService.getInstance();
      await appUsageTracker.checkScreenTimeLimit({
        totalTime: getTotalScreenTime(),
        limit: screenTimeLimit,
        notificationFrequency: notificationFrequency
      });
    } catch (error) {
      console.error('Error checking screen time limit:', error);
    }
  };

  // Save settings to localStorage whenever they change
  useEffect(() => {
    console.log('Screen time limit changed in context:', screenTimeLimit);
    localStorage.setItem('screenTimeLimit', screenTimeLimit.toString());
    
    // Sync with native code
    const appUsageTracker = AppUsageTrackerService.getInstance();
    appUsageTracker.setScreenTimeLimit(screenTimeLimit)
      .catch(error => {
        console.error('Error syncing screen time limit with native code:', error);
      });
    
    // Force a screen time check with the new limit
    checkLimit();
  }, [screenTimeLimit]);

  useEffect(() => {
    localStorage.setItem('notificationsEnabled', notificationsEnabled.toString());
  }, [notificationsEnabled]);

  useEffect(() => {
    console.log('Notification frequency changed in context:', notificationFrequency);
    localStorage.setItem('notificationFrequency', notificationFrequency.toString());
    
    // Sync with native code
    const appUsageTracker = AppUsageTrackerService.getInstance();
    appUsageTracker.setNotificationFrequency(notificationFrequency)
      .catch(error => {
        console.error('Error syncing notification frequency with native code:', error);
      });
  }, [notificationFrequency]);

  useEffect(() => {
    localStorage.setItem('usageAccessEnabled', usageAccessEnabled.toString());
  }, [usageAccessEnabled]);

  useEffect(() => {
    localStorage.setItem('appUsageData', JSON.stringify(appUsageData));
  }, [appUsageData]);

  // Check for day change to reset usage
  useEffect(() => {
    const checkForDayChange = () => {
      const lastResetDate = localStorage.getItem('lastResetDate');
      const today = new Date().toDateString();
      
      if (lastResetDate !== today) {
        resetDailyUsage();
        localStorage.setItem('lastResetDate', today);
      }
    };
    
    // Check on initial load
    checkForDayChange();
    
    // Also check periodically
    const dayChangeInterval = setInterval(checkForDayChange, 60000); // Check every minute
    
    return () => clearInterval(dayChangeInterval);
  }, []);

  // Reset daily usage stats
  const resetDailyUsage = () => {
    console.log('ScreenTimeContext: resetDailyUsage called');
    
    try {
      // Reset all app usage data to zero but preserve the app list
      const prevData = [...appUsageData];
      console.log('Previous data count:', prevData.length);
      
      // If we have no previous data, return an empty array
      if (!prevData || prevData.length === 0) {
        console.log('No previous data to reset');
        return true;
      }
      
      // Reset time to zero for all existing apps
      const resetData = prevData.map(app => ({
        ...app,
        time: 0,
        isActive: false,
        lastUsed: undefined
      }));
      
      console.log(`Reset ${resetData.length} apps to zero usage time`);
      
      // Explicitly save to localStorage to ensure persistence
      localStorage.setItem('appUsageData', JSON.stringify(resetData));
      console.log('Reset data saved to localStorage');
      
      // Update state with reset data - this triggers UI updates
      setAppUsageData(resetData);
      
      // Reset active app tracking
      setActiveApp(null);
      setTrackingStartTime(null);
      
      // Set the last reset date to today
      localStorage.setItem('lastResetDate', new Date().toDateString());
      
      console.log('App usage data has been reset successfully');
      return true;
    } catch (error) {
      console.error('Error resetting app usage data:', error);
      return false;
    }
  };

  // Start tracking an app
  const startTrackingApp = (appName: string, category: string = 'Uncategorized') => {
    console.log(`Starting to track app: ${appName} in category: ${category}`);
    
    // Stop tracking any currently active app
    if (activeApp) {
      stopTrackingApp(activeApp);
    }
    
    setActiveApp(appName);
    setTrackingStartTime(Date.now());
    
    // Determine the appropriate category based on app name
    let detectedCategory = category;
    let appColor = '';
    
    // Auto-categorize common apps
    if (/instagram|facebook|twitter|tiktok|snapchat|whatsapp|telegram|messenger/i.test(appName)) {
      detectedCategory = 'Social Media';
    } else if (/youtube|netflix|hulu|disney|spotify|music|video|player|movie/i.test(appName)) {
      detectedCategory = 'Entertainment';
    } else if (/chrome|safari|firefox|edge|browser|gmail|outlook|office|word|excel|powerpoint|docs/i.test(appName)) {
      detectedCategory = 'Productivity';
    } else if (/game|minecraft|fortnite|roblox|pubg|cod|league|dota/i.test(appName)) {
      detectedCategory = 'Games';
    } else if (/duolingo|khan|academy|learn|course|study|school|education/i.test(appName)) {
      detectedCategory = 'Education';
    }
    
    // Get color based on category
    appColor = getCategoryColor(detectedCategory);
    
    // Update app status to active
    setAppUsageData(prevData => {
      // Check if app already exists
      const appExists = prevData.some(app => app.name === appName);
      
      if (appExists) {
        return prevData.map(app => 
          app.name === appName 
            ? { ...app, isActive: true, lastUsed: new Date(), category: detectedCategory, color: appColor }
            : app
        );
      } else {
        // Add new app if it doesn't exist
        console.log(`Adding new app to tracking: ${appName} (${detectedCategory})`);
        return [
          ...prevData,
          {
            name: appName,
            time: 0,
            color: appColor,
            category: detectedCategory,
            lastUsed: new Date(),
            isActive: true
          }
        ];
      }
    });
  };

  // Stop tracking an app
  const stopTrackingApp = (appName: string) => {
    if (activeApp === appName && trackingStartTime) {
      const elapsedMinutes = (Date.now() - trackingStartTime) / 60000;
      
      setAppUsageData(prevData => 
        prevData.map(app => 
          app.name === appName 
            ? { 
                ...app, 
                time: app.time + elapsedMinutes,
                isActive: false,
                lastUsed: new Date()
              }
            : app
        )
      );
      
      setActiveApp(null);
      setTrackingStartTime(null);
    }
  };

  // Update active app tracking more frequently (every 10 seconds)
  useEffect(() => {
    const updateActiveAppUsage = () => {
      if (activeApp && trackingStartTime) {
        const currentTime = Date.now();
        const elapsedMinutes = (currentTime - trackingStartTime) / 60000;
        
        // Update the active app's time
        setAppUsageData(prevData => 
          prevData.map(app => 
            app.name === activeApp 
              ? { ...app, time: app.time + elapsedMinutes, lastUsed: new Date() }
              : app
          )
        );
        
        // Reset tracking start time to now for next interval
        setTrackingStartTime(currentTime);
      }
    };
    
    // Update more frequently (every 10 seconds)
    const trackingInterval = setInterval(updateActiveAppUsage, 10000);
    
    return () => clearInterval(trackingInterval);
  }, [activeApp]);

  // Handle app visibility changes (background/foreground)
  useEffect(() => {
    const handleVisibilityChange = () => {
      if (document.visibilityState === 'hidden') {
        // App going to background - update current app usage
        if (activeApp) {
          const elapsedMinutes = trackingStartTime ? (Date.now() - trackingStartTime) / 60000 : 0;
          
          setAppUsageData(prevData => 
            prevData.map(app => 
              app.name === activeApp 
                ? { ...app, time: app.time + elapsedMinutes, lastUsed: new Date() }
                : app
            )
          );
          
          // Keep tracking start time updated for when app returns to foreground
          setTrackingStartTime(Date.now());
        }
      }
    };
    
    // Listen for visibility changes
    document.addEventListener('visibilitychange', handleVisibilityChange);
    
    return () => {
      document.removeEventListener('visibilitychange', handleVisibilityChange);
    };
  }, [activeApp, trackingStartTime]);

  useEffect(() => {
    // Initialize background update service
    const backgroundService = BackgroundUpdateService.getInstance();
    backgroundService.setUpdateCallback(async () => {
      const success = await updateAppUsageData();
      if (success) {
        const totalTime = getTotalScreenTime();
        console.log('Background update - Total screen time:', {
          minutes: totalTime,
          hours: (totalTime / 60).toFixed(2),
          timestamp: new Date().toISOString()
        });
      }
    });

    // Initial data load
    updateAppUsageData();

    return () => {
      backgroundService.cleanup();
    };
  }, []);

  // Get total screen time for today
  const getTotalScreenTime = () => {
    const now = new Date();
    const startOfDay = new Date(now.getFullYear(), now.getMonth(), now.getDate());
    const endOfDay = new Date(now.getFullYear(), now.getMonth(), now.getDate(), 23, 59, 59, 999);

    // Include all apps used today, regardless of usage percentage
    return appUsageData
      .filter(app => {
        const appTime = app.lastUsed ? new Date(app.lastUsed) : null;
        return appTime && appTime >= startOfDay && appTime <= endOfDay;
      })
      .reduce((total, app) => total + (app.time || 0), 0);
  };

  // Initialize notifications and check battery optimization
  useEffect(() => {
    const initializeApp = async () => {
      try {
        // Check if we're on a native platform
        const isCapacitorNative = typeof window !== 'undefined' && 
                                 window.Capacitor && 
                                 window.Capacitor.isNativePlatform();
        
        if (isCapacitorNative) {
          const appUsageTracker = AppUsageTrackerService.getInstance();
          
          // Check and request battery optimization exemption
          const isExempt = await appUsageTracker.isBatteryOptimizationExempt();
          if (!isExempt) {
            console.log('Requesting battery optimization exemption');
            await appUsageTracker.requestBatteryOptimizationExemption();
          }
          
          // Initialize notifications
          const { LocalNotifications } = await import('@capacitor/local-notifications');
          
          // Request permission
          const { display } = await LocalNotifications.requestPermissions();
          console.log('Notification permission status:', display);
          
          if (display === 'granted') {
            // Register notification channels for Android
            try {
              // Create a high-priority channel for screen time alerts
              await LocalNotifications.createChannel({
                id: 'screen-time-alerts',
                name: 'Screen Time Alerts',
                description: 'Notifications for screen time limits and updates',
                importance: 5, // Max importance
                visibility: 1,
                sound: 'beep.wav',
                vibration: true,
                lights: true,
                lightColor: '#FF00FF'
              });
              
              console.log('Notification channel created successfully');
              
              /* // Test notification to verify channel
              await LocalNotifications.schedule({
                notifications: [{
                  title: 'Screen Time Reminder',
                  body: 'Notification system initialized successfully',
                  id: 999,
                  channelId: 'screen-time-alerts',
                  schedule: { at: new Date(Date.now() + 1000) },
                  sound: 'beep.wav',
                  smallIcon: 'ic_stat_screen_time',
                  largeIcon: 'ic_launcher',
                  autoCancel: true,
                  attachments: undefined,
                  actionTypeId: '',
                  extra: null
                }]
              }); */
            } catch (channelError) {
              console.error('Error creating notification channel:', channelError);
            }
          }
        }
      } catch (error) {
        console.error('Error initializing app:', error);
      }
    };
    
    initializeApp();
  }, []);

  // Add this function near the top of the file, after the imports
  const logError = (error: string) => {
    try {
      const errors = JSON.parse(localStorage.getItem('debugErrors') || '[]');
      // Keep only the last 10 errors
      errors.unshift(error);
      if (errors.length > 10) {
        errors.pop();
      }
      localStorage.setItem('debugErrors', JSON.stringify(errors));
    } catch (e) {
      console.error('Error logging debug error:', e);
    }
  };

  // Get app usage data for a specific time range
  const getAppUsageData = async (startTime?: number, endTime?: number): Promise<TrackerAppUsage[]> => {
    try {
      const appUsageTracker = AppUsageTrackerService.getInstance();
      return await appUsageTracker.getAppUsageData(startTime, endTime);
    } catch (error) {
      console.error('Error getting app usage data:', error);
      return [];
    }
  };

  // Get app usage data for the last hour
  const getLastHourUsage = async (): Promise<TrackerAppUsage[]> => {
    try {
      const appUsageTracker = AppUsageTrackerService.getInstance();
      return await appUsageTracker.getLastHourUsage();
    } catch (error) {
      console.error('Error getting last hour usage:', error);
      return [];
    }
  };

  // Update the updateAppUsageData function to log errors
  const updateAppUsageData = async (): Promise<boolean> => {
    try {
      console.log('ScreenTimeContext: Updating app usage data with fresh data from system');
      
      // Get the latest data from the system
      const appUsageTracker = AppUsageTrackerService.getInstance();
      const hasPermission = await appUsageTracker.hasUsagePermission();
      
      if (!hasPermission) {
        console.log('No permission to access usage data, cannot update');
        logError('No permission to access usage data');
        return false;
      }
      
      // Get data for today only
      const now = Date.now();
      const startOfDay = new Date();
      startOfDay.setHours(0, 0, 0, 0);
      const startOfDayTimestamp = startOfDay.getTime();
      
      console.log('Fetching data from:', new Date(startOfDayTimestamp).toISOString(), 'to', new Date(now).toISOString());
      const latestData = await appUsageTracker.getAppUsageData(startOfDayTimestamp, now);
      
      if (!latestData || latestData.length === 0) {
        console.log('No app usage data found for today');
        logError('No app usage data found for today');
        return false;
      }
      
      console.log(`Received ${latestData.length} app usage records from system for today`);
      
      // Update the app usage data state with only today's data
      const updatedData = latestData.map(app => ({
        name: app.name,
        time: app.time,
        lastUsed: app.lastUsed,
        category: app.category,
        color: getCategoryColor(app.category),
        isActive: false,
        icon: app.icon
      }));
      
      // Update state with the new data
      setAppUsageData(updatedData);
      
      // Update localStorage
      localStorage.setItem('appUsageData', JSON.stringify(updatedData));
      
      console.log('App usage data updated successfully with today\'s data');
      
      // Check screen time limit after updating data
      try {
        await checkLimit();
        console.log('Screen time limit check completed after data update');
      } catch (error: any) {
        console.error('Error checking screen time limit after data update:', error);
        logError(`Screen time limit check failed after data update: ${error.message}`);
      }
      
      return true;
    } catch (error: any) {
      console.error('Error updating app usage data:', error);
      logError(`Failed to update app usage data: ${error.message}`);
      return false;
    }
  };

  return (
    <ScreenTimeContext.Provider
      value={{
        screenTimeLimit,
        setScreenTimeLimit,
        notificationsEnabled,
        setNotificationsEnabled,
        notificationFrequency,
        setNotificationFrequency,
        usageAccessEnabled,
        setUsageAccessEnabled,
        appUsageData,
        getTotalScreenTime,
        startTrackingApp,
        stopTrackingApp,
        resetDailyUsage,
        getAppUsageData,
        getLastHourUsage,
        updateAppUsageData
      }}
    >
      {children}
    </ScreenTimeContext.Provider>
  );
};

export const useScreenTime = (): ScreenTimeContextType => {
  const context = useContext(ScreenTimeContext);
  if (context === undefined) {
    throw new Error('useScreenTime must be used within a ScreenTimeProvider');
  }
  return context;
};

export default ScreenTimeContext; 