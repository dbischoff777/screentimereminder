import React, { createContext, useContext, useState, useEffect, ReactNode } from 'react';
import AppUsageTrackerService, { AppUsage as TrackerAppUsage } from '../services/AppUsageTracker';
import { App } from '@capacitor/app';

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
  
  // Save settings to localStorage whenever they change
  useEffect(() => {
    console.log('Screen time limit changed in context:', screenTimeLimit);
    localStorage.setItem('screenTimeLimit', screenTimeLimit.toString());
    
    // Force a screen time check when limit changes
    const checkLimit = async () => {
      try {
        console.log('Forcing screen time check with new limit:', screenTimeLimit);
        await checkScreenTimeLimit();
      } catch (error) {
        console.error('Error checking screen time limit after limit update:', error);
      }
    };
    
    checkLimit();
  }, [screenTimeLimit]);

  useEffect(() => {
    localStorage.setItem('notificationsEnabled', notificationsEnabled.toString());
  }, [notificationsEnabled]);

  useEffect(() => {
    localStorage.setItem('notificationFrequency', notificationFrequency.toString());
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
    let backgroundTrackingInterval: NodeJS.Timeout | null = null;
    let isTracking = false;

    const startBackgroundTracking = () => {
      if (isTracking) {
        console.log('Background tracking already running');
        return;
      }
      
      console.log('Starting background tracking');
      isTracking = true;

      // Run immediately on start
      console.log('Running initial background check');
      updateAppUsageData().catch(error => {
        console.error('Error in initial background check:', error);
      });

      // Set up a single interval for both app usage updates and screen time checks
      backgroundTrackingInterval = setInterval(async () => {
        try {
          console.log('Background tracking interval triggered');
          const updateSuccess = await updateAppUsageData();
          console.log('Background updateAppUsageData result:', updateSuccess);
          
          if (updateSuccess) {
            await checkScreenTimeLimit();
            console.log('Background checkScreenTimeLimit completed');
          }
        } catch (error) {
          console.error('Background tracking error:', error);
        }
      }, 60000); // Check every 60 seconds to comply with Android requirements
    };

    const stopBackgroundTracking = () => {
      if (backgroundTrackingInterval) {
        console.log('Stopping background tracking');
        clearInterval(backgroundTrackingInterval);
        backgroundTrackingInterval = null;
      }
      isTracking = false;
    };

    // Define handleVisibilityChange in the outer scope
    const handleVisibilityChange = () => {
      const isVisible = document.visibilityState === 'visible';
      console.log('Document visibility changed:', isVisible ? 'visible' : 'hidden');
      if (isVisible) {
        startBackgroundTracking();
      } else {
        // Keep tracking in background
        startBackgroundTracking();
      }
    };

    const setupAppStateListeners = async () => {
      console.log('Setting up app state listeners');
      
      // Add visibility change listener
      document.addEventListener('visibilitychange', handleVisibilityChange);

      // Add Capacitor app state listener
      App.addListener('appStateChange', ({ isActive }) => {
        console.log('App state changed. Is active:', isActive);
        if (isActive) {
          console.log('App is in foreground, starting tracking');
          startBackgroundTracking();
        } else {
          console.log('App is in background, continuing tracking');
          startBackgroundTracking();
        }
      });

      // Start tracking immediately
      console.log('Starting initial tracking');
      startBackgroundTracking();
    };

    setupAppStateListeners();

    // Cleanup function
    return () => {
      console.log('Cleaning up background tracking');
      stopBackgroundTracking();
      // Remove app state listener
      App.removeAllListeners();
      document.removeEventListener('visibilitychange', handleVisibilityChange);
    };
  }, []);

  // Calculate total screen time across all apps
  const getTotalScreenTime = (): number => {
    return appUsageData.reduce((total, app) => total + app.time, 0);
  };

  // Initialize notifications
  useEffect(() => {
    const initializeNotifications = async () => {
      try {
        // Check if we're on a native platform
        const isCapacitorNative = typeof window !== 'undefined' && 
                                 window.Capacitor && 
                                 window.Capacitor.isNativePlatform();
        
        if (isCapacitorNative) {
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
              
              // Test notification to verify channel
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
              });
            } catch (channelError) {
              console.error('Error creating notification channel:', channelError);
            }
          }
        }
      } catch (error) {
        console.error('Error initializing notifications:', error);
      }
    };
    
    if (notificationsEnabled) {
      initializeNotifications();
    }
  }, [notificationsEnabled]);

  // Check if screen time limit is reached and show notification
  const checkScreenTimeLimit = async () => {
    const totalTime = getTotalScreenTime();
    
    // Detailed logging of key values
    console.log('Screen time check details:', {
      totalTime: `${totalTime.toFixed(2)}min`,
      screenTimeLimit: `${screenTimeLimit}min`,
      notificationsEnabled,
      notificationFrequency: `${notificationFrequency}min`,
      timestamp: new Date().toISOString()
    });
    
    if (!notificationsEnabled) {
      console.log('Notifications are disabled, skipping screen time check');
      return;
    }
    
    // Check if we're on a mobile device with Capacitor
    const isCapacitorNative = typeof window !== 'undefined' && 
                             window.Capacitor && 
                             window.Capacitor.isNativePlatform();
    
    if (!isCapacitorNative) {
      console.log('Not on a native platform, skipping notifications');
      return;
    }
    
    try {
      const { LocalNotifications } = await import('@capacitor/local-notifications');
      
      // Check if notifications are actually permitted
      const { display } = await LocalNotifications.checkPermissions();
      console.log('Notification permission status:', display);
      
      if (display !== 'granted') {
        console.log('Notifications permission not granted, skipping notifications');
        return;
      }
      
      // First check if limit is reached
      if (totalTime >= screenTimeLimit) {
        console.log(`Limit reached - Total: ${totalTime}min, Limit: ${screenTimeLimit}min`);
        try {
          // Cancel any existing notifications first
          await LocalNotifications.cancel({ notifications: [{ id: 2 }] });
          
          // Schedule new notification with 1-second delay
          await LocalNotifications.schedule({
            notifications: [{
              title: 'Screen Time Limit Reached',
              body: `Total Screen Time: ${Math.round(totalTime)} minutes\nDaily Limit: ${screenTimeLimit} minutes\nYou have reached your daily limit!`,
              id: 2,
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
          });
          console.log('Limit reached notification scheduled successfully');
        } catch (error) {
          console.error('Error scheduling limit reached notification:', error);
        }
      }
      // Then check if we're approaching the limit
      else if (screenTimeLimit - totalTime <= notificationFrequency) {
        const minutesRemaining = Math.round(screenTimeLimit - totalTime);
        console.log(`Approaching limit - Total: ${totalTime}min, Limit: ${screenTimeLimit}min, Remaining: ${minutesRemaining}min`);
        try {
          // Cancel any existing notifications first
          await LocalNotifications.cancel({ notifications: [{ id: 1 }] });
          
          // Schedule new notification with 1-second delay
          await LocalNotifications.schedule({
            notifications: [{
              title: 'Screen Time Limit Approaching',
              body: `Total Screen Time: ${Math.round(totalTime)} minutes\nDaily Limit: ${screenTimeLimit} minutes\n${minutesRemaining} minutes remaining`,
              id: 1,
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
          });
          console.log('Approaching limit notification scheduled successfully');
        } catch (error) {
          console.error('Error scheduling approaching limit notification:', error);
        }
      }
      // If we're over the limit but haven't shown the limit reached notification yet
      else if (totalTime > screenTimeLimit) {
        console.log(`Over limit - Total: ${totalTime}min, Limit: ${screenTimeLimit}min`);
        try {
          // Cancel any existing notifications first
          await LocalNotifications.cancel({ notifications: [{ id: 2 }] });
          
          // Schedule new notification immediately
          await LocalNotifications.schedule({
            notifications: [{
              title: 'Screen Time Limit Reached',
              body: `Total Screen Time: ${Math.round(totalTime)} minutes\nDaily Limit: ${screenTimeLimit} minutes\nYou have exceeded your daily limit!`,
              id: 2,
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
          });
          console.log('Over limit notification scheduled successfully');
        } catch (error) {
          console.error('Error scheduling over limit notification:', error);
        }
      } else {
        console.log(`No notification needed - Total: ${totalTime}min, Limit: ${screenTimeLimit}min, Next check at: ${screenTimeLimit - notificationFrequency}min`);
      }
    } catch (error) {
      console.error('Error in checkScreenTimeLimit:', error);
      throw error;
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

  // Update app usage data with fresh data from the system
  const updateAppUsageData = async (): Promise<boolean> => {
    try {
      console.log('ScreenTimeContext: Updating app usage data with fresh data from system');
      
      // Get the latest data from the system
      const appUsageTracker = AppUsageTrackerService.getInstance();
      const hasPermission = await appUsageTracker.hasUsagePermission();
      
      if (!hasPermission) {
        console.log('No permission to access usage data, cannot update');
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
        isActive: false
      }));
      
      // Update state with the new data
      setAppUsageData(updatedData);
      
      // Update localStorage
      localStorage.setItem('appUsageData', JSON.stringify(updatedData));
      
      console.log('App usage data updated successfully with today\'s data');
      
      // Check screen time limit after updating data
      try {
        await checkScreenTimeLimit();
        console.log('Screen time limit check completed after data update');
      } catch (error) {
        console.error('Error checking screen time limit after data update:', error);
      }
      
      return true;
    } catch (error) {
      console.error('Error updating app usage data:', error);
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