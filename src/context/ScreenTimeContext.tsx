import React, { createContext, useContext, useState, useEffect, ReactNode } from 'react';
import BackgroundService from '../services/BackgroundService';
import AppUsageTrackerService, { AppUsage as TrackerAppUsage } from '../services/AppUsageTracker';

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
  // Test data function
  addTestAppUsage: () => void;
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
    return saved ? parseInt(saved, 10) : 120; // Default 2 hours
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
    localStorage.setItem('screenTimeLimit', screenTimeLimit.toString());
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

  // Initialize background service
  useEffect(() => {
    let backgroundService: any = null;
    
    try {
      // Safely get the background service instance
      backgroundService = BackgroundService.getInstance();
      
      // Initialize the background service with error handling
      try {
        backgroundService.initialize();
      } catch (initError) {
        console.error('Failed to initialize background service:', initError);
      }
      
      // Set up tracking callback with error handling
      try {
        backgroundService.setTrackingCallback(async () => {
          try {
            // Update app usage data from system
            await updateAppUsageData();
            
            // Check screen time limit after updating data
            await checkScreenTimeLimit();
            
            // Update active app usage if needed
            if (activeApp && trackingStartTime) {
              const currentTime = Date.now();
              const elapsedMinutes = (currentTime - trackingStartTime) / 60000;
              
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
          } catch (callbackError) {
            console.error('Error in background tracking callback:', callbackError);
          }
        });
      } catch (callbackSetupError) {
        console.error('Failed to set tracking callback:', callbackSetupError);
      }
    } catch (serviceError) {
      console.error('Error accessing background service:', serviceError);
    }
    
    return () => {
      // Clean up background service when component unmounts
      if (backgroundService) {
        try {
          backgroundService.disableBackgroundMode();
        } catch (error) {
          console.error('Error disabling background service:', error);
        }
      }
    };
  }, [activeApp, trackingStartTime]);

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
                importance: 4, // High importance
                visibility: 1,
                sound: 'beep.wav',
                vibration: true,
                lights: true,
                lightColor: '#FF00FF'
              });

              // Create a default channel for other notifications
              await LocalNotifications.createChannel({
                id: 'default',
                name: 'Default',
                description: 'Default notification channel',
                importance: 3,
                visibility: 1,
                sound: 'beep.wav',
                vibration: true,
                lights: true,
                lightColor: '#00FFFF'
              });
              
              console.log('Notification channels created successfully');
              
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
    
    if (notificationsEnabled) {
      console.log('Checking screen time limit:', {
        totalTime,
        screenTimeLimit,
        notificationFrequency,
        shouldNotifyApproaching: totalTime >= (screenTimeLimit - notificationFrequency) && totalTime < screenTimeLimit,
        shouldNotifyReached: totalTime >= screenTimeLimit
      });
      
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
        if (display !== 'granted') {
          console.log('Notifications permission not granted, skipping notifications');
          return;
        }
        
        // Approaching limit notification
        if (totalTime >= (screenTimeLimit - notificationFrequency) && totalTime < screenTimeLimit) {
          try {
            const minutesRemaining = Math.round(screenTimeLimit - totalTime);
            console.log('Scheduling approaching limit notification, minutes remaining:', minutesRemaining);
            
            await LocalNotifications.schedule({
              notifications: [{
                title: 'Screen Time Limit Approaching',
                body: `You're ${minutesRemaining} minutes away from your daily limit.`,
                id: 1,
                channelId: 'screen-time-alerts',
                schedule: { at: new Date() },
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
        // Limit reached notification
        else if (totalTime >= screenTimeLimit) {
          try {
            console.log('Scheduling limit reached notification');
            
            await LocalNotifications.schedule({
              notifications: [{
                title: 'Screen Time Limit Reached',
                body: `You've reached your daily screen time limit of ${screenTimeLimit} minutes.`,
                id: 2,
                channelId: 'screen-time-alerts',
                schedule: { at: new Date() },
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
      } catch (error) {
        console.error('Error in checkScreenTimeLimit:', error);
      }
    } else {
      console.log('Notifications are disabled, skipping screen time check');
    }
  };

  // Check more frequently (every minute)
  useEffect(() => {
    console.log('Setting up screen time limit check interval');
    const limitCheckInterval = setInterval(checkScreenTimeLimit, 60000);
    
    // Initial check
    checkScreenTimeLimit();
    
    return () => {
      console.log('Clearing screen time limit check interval');
      clearInterval(limitCheckInterval);
    };
  }, [appUsageData, screenTimeLimit, notificationsEnabled, notificationFrequency]);

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
      return true;
    } catch (error) {
      console.error('Error updating app usage data:', error);
      return false;
    }
  };

  // Add test app usage data for debugging
  const addTestAppUsage = () => {
    console.log('Adding test app usage data');
    
    // Create test data for common apps
    const testApps: AppUsage[] = [
      {
        name: 'Duolingo',
        time: 15, // 15 minutes
        color: getCategoryColor('Education'),
        category: 'Education',
        lastUsed: new Date(),
        isActive: false
      },
      {
        name: 'YouTube',
        time: 45, // 45 minutes
        color: getCategoryColor('Entertainment'),
        category: 'Entertainment',
        lastUsed: new Date(Date.now() - 30 * 60 * 1000), // 30 minutes ago
        isActive: false
      },
      {
        name: 'Instagram',
        time: 30, // 30 minutes
        color: getCategoryColor('Social Media'),
        category: 'Social Media',
        lastUsed: new Date(Date.now() - 60 * 60 * 1000), // 1 hour ago
        isActive: false
      }
    ];
    
    // Merge with existing data or replace if same app exists
    const updatedData = [...appUsageData];
    
    testApps.forEach(testApp => {
      const existingAppIndex = updatedData.findIndex(app => app.name === testApp.name);
      
      if (existingAppIndex >= 0) {
        // Update existing app
        updatedData[existingAppIndex] = {
          ...updatedData[existingAppIndex],
          time: updatedData[existingAppIndex].time + testApp.time,
          lastUsed: testApp.lastUsed
        };
      } else {
        // Add new app
        updatedData.push(testApp);
      }
    });
    
    // Update state and localStorage
    setAppUsageData(updatedData);
    localStorage.setItem('appUsageData', JSON.stringify(updatedData));
    
    console.log('Test app usage data added successfully');
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
        addTestAppUsage,
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