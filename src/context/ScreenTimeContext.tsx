import React, { createContext, useContext, useState, useEffect, ReactNode, useCallback } from 'react';
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
  setScreenTimeLimit: (limit: number | { limitMinutes: number }) => void;
  notificationsEnabled: boolean;
  setNotificationsEnabled: (enabled: boolean) => void;
  notificationFrequency: number;
  setNotificationFrequency: (params: number | { frequency: number }) => void;
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
  const [screenTimeLimitState, setScreenTimeLimitState] = useState<number>(() => {
    // Default to 120 minutes (2 hours) initially, will be updated from native storage
    return 120;
  });

  // Notification settings
  const [notificationsEnabled, setNotificationsEnabled] = useState<boolean>(() => {
    const saved = localStorage.getItem('notificationsEnabled');
    return saved ? saved === 'true' : false;
  });

  const [notificationFrequency, setNotificationFrequency] = useState<number>(() => {
    // Default to 5 minutes initially, will be updated from native storage
    return 5;
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
        limit: screenTimeLimitState,
        notificationFrequency
      });
    } catch (error) {
      console.error('Error checking screen time limit:', error);
    }
  };

  // Load saved settings from native layer on app initialization
  useEffect(() => {
    const loadSavedSettings = async () => {
      try {
        console.log('Loading saved settings from native layer...');
        const appUsageTracker = AppUsageTrackerService.getInstance();

        // Load screen time limit
        const savedLimit = await appUsageTracker.getScreenTimeLimit();
        console.log('Loaded screen time limit:', savedLimit);
        setScreenTimeLimitState(savedLimit);

        // Load notification frequency
        const savedFrequency = await appUsageTracker.getNotificationFrequency();
        console.log('Loaded notification frequency:', savedFrequency);
        setNotificationFrequency(savedFrequency);

        // Check usage permission
        const hasPermission = await appUsageTracker.hasUsagePermission();
        console.log('Has usage permission:', hasPermission);
        setUsageAccessEnabled(hasPermission);

        // Start tracking if we have permission
        if (hasPermission) {
          await appUsageTracker.startTracking();
          console.log('Usage tracking started');
        }

      } catch (error) {
        console.error('Error loading saved settings:', error);
      }
    };

    loadSavedSettings();
  }, []);

  // Save settings to localStorage whenever they change
  useEffect(() => {
    console.log('Screen time limit changed in context:', screenTimeLimitState);
    localStorage.setItem('screenTimeLimit', screenTimeLimitState.toString());
    
    // Force a screen time check with the new limit
    checkLimit();
  }, [screenTimeLimitState]);

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

  // Update app usage data
  const updateAppUsageData = async (): Promise<boolean> => {
    try {
      console.log('ScreenTimeContext: Updating app usage data');
      const appUsageTracker = AppUsageTrackerService.getInstance();
      
      // Get fresh settings from native layer
      const currentLimit = await appUsageTracker.getScreenTimeLimit();
      const currentFrequency = await appUsageTracker.getNotificationFrequency();
      
      // Get fresh data from native layer
      const data = await appUsageTracker.getAppUsageData();
      console.log('ScreenTimeContext: Received app usage data:', data);
      
      if (data && Array.isArray(data)) {
        // Process and enhance the data
        const processedData = data.map(app => {
          // Determine the category based on app name if not provided
          let category = app.category;
          if (!category) {
            if (/instagram|facebook|twitter|tiktok|snapchat|whatsapp|telegram|messenger/i.test(app.name)) {
              category = 'Social Media';
            } else if (/youtube|netflix|hulu|disney|spotify|music|video|player|movie/i.test(app.name)) {
              category = 'Entertainment';
            } else if (/chrome|safari|firefox|edge|browser|gmail|outlook|office|word|excel|powerpoint|docs/i.test(app.name)) {
              category = 'Productivity';
            } else if (/game|games|gaming|play|puzzle|candy|clash|craft/i.test(app.name)) {
              category = 'Games';
            } else if (/learn|education|school|course|study|duolingo|khan|quiz/i.test(app.name)) {
              category = 'Education';
            } else {
              category = 'Other';
            }
          }

          // Get color based on category
          const color = getCategoryColor(category);

          return {
            name: app.name || 'Unknown App',
            time: app.time || 0,
            color: color,
            lastUsed: app.lastUsed ? new Date(app.lastUsed) : undefined,
            category: category,
            isActive: false,
            icon: app.icon
          };
        });

        // Update state with processed data
        setAppUsageData(processedData);
        
        // Get total screen time using the native method for accuracy
        const totalTime = await appUsageTracker.getTotalScreenTime();
        console.log('ScreenTimeContext: Total screen time:', totalTime);
        
        // Check screen time limit with current settings from native layer
        await appUsageTracker.checkScreenTimeLimit({
          totalTime,
          limit: currentLimit,
          notificationFrequency: currentFrequency
        });
        
        return true;
      }
      
      return false;
    } catch (error) {
      console.error('Error updating app usage data:', error);
      return false;
    }
  };

  // Set up periodic updates
  useEffect(() => {
    if (usageAccessEnabled) {
      console.log('ScreenTimeContext: Setting up periodic updates');
      
      // Initial update
      updateAppUsageData();
      
      // Set up interval for periodic updates
      const updateInterval = setInterval(() => {
        updateAppUsageData();
      }, 60000); // Update every minute
      
      return () => clearInterval(updateInterval);
    }
  }, [usageAccessEnabled]);

  // Listen for native updates
  useEffect(() => {
    const handleUsageUpdate = (event: CustomEvent) => {
      try {
        const data = JSON.parse(event.detail.usageData);
        if (data.apps && Array.isArray(data.apps)) {
          console.log('ScreenTimeContext: Received usage update from native layer:', data);
          setAppUsageData(data.apps.map((app: any) => {
            // Determine the category based on app name if not provided
            let category = app.category;
            if (!category) {
              if (/instagram|facebook|twitter|tiktok|snapchat|whatsapp|telegram|messenger/i.test(app.name)) {
                category = 'Social Media';
              } else if (/youtube|netflix|hulu|disney|spotify|music|video|player|movie/i.test(app.name)) {
                category = 'Entertainment';
              } else if (/chrome|safari|firefox|edge|browser|gmail|outlook|office|word|excel|powerpoint|docs/i.test(app.name)) {
                category = 'Productivity';
              } else if (/game|games|gaming|play|puzzle|candy|clash|craft/i.test(app.name)) {
                category = 'Games';
              } else if (/learn|education|school|course|study|duolingo|khan|quiz/i.test(app.name)) {
                category = 'Education';
              } else {
                category = 'Other';
              }
            }

            // Get color based on category
            const color = getCategoryColor(category);

            return {
              name: app.name || 'Unknown App',
              time: app.time || 0,
              color: color,
              lastUsed: app.lastUsed ? new Date(app.lastUsed) : undefined,
              category: category,
              isActive: false,
              icon: app.icon
            };
          }));
        }
      } catch (error) {
        console.error('Error processing usage update:', error);
      }
    };

    window.addEventListener('com.screentimereminder.app.APP_USAGE_UPDATE', handleUsageUpdate as EventListener);
    return () => {
      window.removeEventListener('com.screentimereminder.app.APP_USAGE_UPDATE', handleUsageUpdate as EventListener);
    };
  }, []);

  const setScreenTimeLimit = useCallback((limit: number | { limitMinutes: number }) => {
    const limitMinutes = typeof limit === 'number' ? limit : limit.limitMinutes;
    setScreenTimeLimitState(limitMinutes);
    
    // Sync with native code
    const appUsageTracker = AppUsageTrackerService.getInstance();
    appUsageTracker.setScreenTimeLimit(limitMinutes)
      .catch(error => {
        console.error('Error syncing screen time limit with native code:', error);
      });
  }, []);

  const setNotificationFrequencyWithSync = useCallback((params: number | { frequency: number }) => {
    const frequency = typeof params === 'number' ? params : params.frequency;
    
    // Only update if the frequency is actually different
    if (frequency !== notificationFrequency) {
      console.log('Setting notification frequency from:', notificationFrequency, 'to:', frequency);
      
      // Update local state first
      setNotificationFrequency(frequency);
      
      // Sync with native code
      const appUsageTracker = AppUsageTrackerService.getInstance();
      appUsageTracker.setNotificationFrequency({ frequency })
        .catch(error => {
          console.error('Error syncing notification frequency with native code:', error);
          // Revert to previous value on error
          setNotificationFrequency(notificationFrequency);
        });
    }
  }, [notificationFrequency]);

  return (
    <ScreenTimeContext.Provider
      value={{
        screenTimeLimit: screenTimeLimitState,
        setScreenTimeLimit,
        notificationsEnabled,
        setNotificationsEnabled,
        notificationFrequency,
        setNotificationFrequency: setNotificationFrequencyWithSync,
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