import React, { createContext, useContext, useState, useEffect, ReactNode } from 'react';
import BackgroundService from '../services/BackgroundService';

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
  // App tracking functionality
  appUsageData: AppUsage[];
  getTotalScreenTime: () => number;
  startTrackingApp: (appName: string, category: string) => void;
  stopTrackingApp: (appName: string) => void;
  resetDailyUsage: () => void;
}

const ScreenTimeContext = createContext<ScreenTimeContextType | undefined>(undefined);

// More realistic app categories with proper categorization
const defaultAppCategories: AppUsage[] = [
  { name: 'Instagram', time: 0, color: '#E1306C', category: 'Social Media', lastUsed: undefined },
  { name: 'Facebook', time: 0, color: '#4267B2', category: 'Social Media', lastUsed: undefined },
  { name: 'TikTok', time: 0, color: '#000000', category: 'Social Media', lastUsed: undefined },
  { name: 'Twitter', time: 0, color: '#1DA1F2', category: 'Social Media', lastUsed: undefined },
  { name: 'YouTube', time: 0, color: '#FF0000', category: 'Entertainment', lastUsed: undefined },
  { name: 'Netflix', time: 0, color: '#E50914', category: 'Entertainment', lastUsed: undefined },
  { name: 'Spotify', time: 0, color: '#1DB954', category: 'Entertainment', lastUsed: undefined },
  { name: 'Chrome', time: 0, color: '#4285F4', category: 'Productivity', lastUsed: undefined },
  { name: 'Gmail', time: 0, color: '#D44638', category: 'Productivity', lastUsed: undefined },
  { name: 'Microsoft Office', time: 0, color: '#D83B01', category: 'Productivity', lastUsed: undefined },
  { name: 'Minecraft', time: 0, color: '#70B237', category: 'Games', lastUsed: undefined },
  { name: 'Fortnite', time: 0, color: '#9D4DFF', category: 'Games', lastUsed: undefined },
  { name: 'Duolingo', time: 0, color: '#58CC02', category: 'Education', lastUsed: undefined },
  { name: 'Khan Academy', time: 0, color: '#14BF96', category: 'Education', lastUsed: undefined }
];

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
    // Reset all app usage data to zero
    const resetData = defaultAppCategories.map(app => ({
      ...app,
      time: 0,
      isActive: false,
      lastUsed: undefined
    }));
    
    // Update state with reset data
    setAppUsageData(resetData);
    
    // Explicitly save to localStorage to ensure persistence
    localStorage.setItem('appUsageData', JSON.stringify(resetData));
    
    // Reset active app tracking
    setActiveApp(null);
    setTrackingStartTime(null);
    
    console.log('App usage data has been reset');
  };

  // Start tracking an app
  const startTrackingApp = (appName: string, category: string) => {
    // Stop tracking any currently active app
    if (activeApp) {
      stopTrackingApp(activeApp);
    }
    
    setActiveApp(appName);
    setTrackingStartTime(Date.now());
    
    // Update app status to active
    setAppUsageData(prevData => {
      // Check if app already exists
      const appExists = prevData.some(app => app.name === appName);
      
      if (appExists) {
        return prevData.map(app => 
          app.name === appName 
            ? { ...app, isActive: true, lastUsed: new Date() }
            : app
        );
      } else {
        // Add new app if it doesn't exist
        return [
          ...prevData,
          {
            name: appName,
            time: 0,
            color: getCategoryColor(category),
            category,
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

  return (
    <ScreenTimeContext.Provider
      value={{
        screenTimeLimit,
        setScreenTimeLimit,
        notificationsEnabled,
        setNotificationsEnabled,
        notificationFrequency,
        setNotificationFrequency,
        appUsageData,
        getTotalScreenTime,
        startTrackingApp,
        stopTrackingApp,
        resetDailyUsage
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