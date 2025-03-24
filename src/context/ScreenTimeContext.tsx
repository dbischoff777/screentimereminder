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
        // Continue execution even if initialization fails
      }
      
      // Set up tracking callback with error handling
      try {
        backgroundService.setTrackingCallback(() => {
          try {
            // Update active app usage
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
            
            // Also run the simulation in background with error handling
            try {
              simulateAppUsage();
            } catch (simError) {
              console.error('Error in app usage simulation:', simError);
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
          // Continue cleanup even if this fails
        }
      }
    };
  }, []);

  // Extract simulateAppUsage function to be used in background service
  const simulateAppUsage = () => {
    // Disabled - we're now tracking actual app usage instead of simulating it
    console.log('App usage simulation is disabled - tracking real usage only');
    return;
    
    /* Original simulation code removed
    try {
      // Simulate more realistic usage patterns
      setAppUsageData(prevData => {
        try {
          // Create a copy of the data
          const updatedData = [...prevData];
          
          // Determine which apps to update based on time of day
          const hour = new Date().getHours();
          let categoriesToUpdate: string[] = [];
          
          // Morning (6-12): Productivity, Social Media
          if (hour >= 6 && hour < 12) {
            categoriesToUpdate = ['Productivity', 'Social Media'];
          }
          // Afternoon (12-18): Entertainment, Social Media, Games
          else if (hour >= 12 && hour < 18) {
            categoriesToUpdate = ['Entertainment', 'Social Media', 'Games'];
          }
          // Evening (18-23): Entertainment, Games, Social Media
          else if (hour >= 18 && hour < 23) {
            categoriesToUpdate = ['Entertainment', 'Games', 'Social Media'];
          }
          // Night (23-6): Entertainment, Social Media
          else {
            categoriesToUpdate = ['Entertainment', 'Social Media'];
          }
          
          // Update apps in the selected categories
          categoriesToUpdate.forEach(category => {
            try {
              const appsInCategory = updatedData.filter(app => app.category === category);
              if (appsInCategory.length > 0) {
                // Randomly select an app from this category
                const randomApp = appsInCategory[Math.floor(Math.random() * appsInCategory.length)];
                const appIndex = updatedData.findIndex(app => app.name === randomApp.name);
                
                if (appIndex !== -1) {
                  // Add 1-3 minutes of usage
                  const additionalTime = (Math.random() * 2 + 1) / 6; // 1-3 minutes divided by 6 (10-second intervals)
                  
                  updatedData[appIndex] = {
                    ...updatedData[appIndex],
                    time: updatedData[appIndex].time + additionalTime,
                    lastUsed: new Date()
                  };
                }
              }
            } catch (categoryError) {
              console.error(`Error updating category ${category}:`, categoryError);
              // Continue with other categories even if one fails
            }
          });
          
          return updatedData;
        } catch (dataError) {
          console.error('Error updating app usage data:', dataError);
          // Return original data if update fails
          return prevData;
        }
      });
    } catch (error) {
      console.error('Critical error in simulateAppUsage:', error);
    }
    */
  };

  // Calculate total screen time across all apps
  const getTotalScreenTime = (): number => {
    return appUsageData.reduce((total, app) => total + app.time, 0);
  };

  // Check if screen time limit is reached and show notification
  useEffect(() => {
    const checkScreenTimeLimit = async () => {
      const totalTime = getTotalScreenTime();
      
      if (notificationsEnabled) {
        // Check if we're on a mobile device with Capacitor
        const isCapacitorNative = typeof window !== 'undefined' && 
                                 window.Capacitor && 
                                 window.Capacitor.isNativePlatform();
        
        // Approaching limit notification
        if (totalTime >= (screenTimeLimit - notificationFrequency) && totalTime < screenTimeLimit) {
          if (isCapacitorNative) {
            try {
              // Use Capacitor LocalNotifications for mobile
              const { LocalNotifications } = await import('@capacitor/local-notifications');
              await LocalNotifications.schedule({
                notifications: [
                  {
                    title: 'Screen Time Limit Approaching',
                    body: `You're ${Math.round(screenTimeLimit - totalTime)} minutes away from your daily limit.`,
                    id: 1,
                    schedule: { at: new Date(Date.now()) }
                  }
                ]
              });
              console.log('Capacitor notification sent: approaching limit');
            } catch (error) {
              console.error('Error showing Capacitor notification:', error);
            }
          } else if (typeof window !== 'undefined' && 'Notification' in window && Notification.permission === 'granted') {
            // Fallback to web Notification API
            try {
              new Notification('Screen Time Limit Approaching', {
                body: `You're ${Math.round(screenTimeLimit - totalTime)} minutes away from your daily limit.`,
                icon: '/notification-icon.png'
              });
              console.log('Web notification sent: approaching limit');
            } catch (error) {
              console.error('Error showing web notification:', error);
            }
          } else {
            console.log('Notification would be shown: approaching limit');
          }
        }
        // Limit reached notification
        else if (totalTime >= screenTimeLimit) {
          if (isCapacitorNative) {
            try {
              // Use Capacitor LocalNotifications for mobile
              const { LocalNotifications } = await import('@capacitor/local-notifications');
              await LocalNotifications.schedule({
                notifications: [
                  {
                    title: 'Screen Time Limit Reached',
                    body: `You've reached your daily screen time limit of ${screenTimeLimit} minutes.`,
                    id: 2,
                    schedule: { at: new Date(Date.now()) }
                  }
                ]
              });
              console.log('Capacitor notification sent: limit reached');
            } catch (error) {
              console.error('Error showing Capacitor notification:', error);
            }
          } else if (typeof window !== 'undefined' && 'Notification' in window && Notification.permission === 'granted') {
            // Fallback to web Notification API
            try {
              new Notification('Screen Time Limit Reached', {
                body: `You've reached your daily screen time limit of ${screenTimeLimit} minutes.`,
                icon: '/notification-icon.png'
              });
              console.log('Web notification sent: limit reached');
            } catch (error) {
              console.error('Error showing web notification:', error);
            }
          } else {
            console.log('Notification would be shown: limit reached');
          }
        }
      }
    };
    
    // Check more frequently
    const limitCheckInterval = setInterval(checkScreenTimeLimit, 60000); // Check every minute
    
    return () => clearInterval(limitCheckInterval);
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
      const startOfDay = new Date().setHours(0, 0, 0, 0);
      const latestData = await appUsageTracker.getAppUsageData(startOfDay, now);
      
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