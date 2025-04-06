import { useState, useEffect } from 'react';
import { useScreenTime } from '../context/ScreenTimeContext';
import { App } from '@capacitor/app';
import { Capacitor } from '@capacitor/core';
import BackgroundUpdateService from '../services/BackgroundUpdateService';
import { Paper, Text, Group, Badge, Stack, ScrollArea } from '@mantine/core';
import { FiCheckCircle, FiXCircle, FiAlertCircle, FiClock, FiImage } from 'react-icons/fi';
import AppUsageTracker from '../services/AppUsageTracker';

interface DebugInfo {
  usagePermission: boolean;
  batteryOptimization: boolean;
  notifications: boolean;
  backgroundTracking: boolean;
  lastUpdate: string;
  errors: string[];
  appIconStatus: {
    totalApps: number;
    appsWithIcons: number;
    appsWithoutIcons: number;
  };
  notificationStatus: {
    lastLimitReached: number;
    lastApproachingLimit: number;
    notificationFrequency: number;
    screenTimeLimit: number;
    currentScreenTime: number;
    remainingMinutes: number;
  };
}

const DebugPanel = () => {
  const { appUsageData, getTotalScreenTime, screenTimeLimit } = useScreenTime();
  const [appState, setAppState] = useState<string>('active');
  const [lastUpdateTime, setLastUpdateTime] = useState<string>('Never');
  const [backgroundServiceStatus, setBackgroundServiceStatus] = useState<string>('Unknown');
  const [lastBackgroundUpdate, setLastBackgroundUpdate] = useState<string>('Never');
  const [updateInterval, setUpdateInterval] = useState<number>(0);
  const [isTracking, setIsTracking] = useState<boolean>(false);
  const [debugInfo, setDebugInfo] = useState<DebugInfo>({
    usagePermission: false,
    batteryOptimization: false,
    notifications: false,
    backgroundTracking: false,
    lastUpdate: 'Never',
    errors: [],
    appIconStatus: {
      totalApps: 0,
      appsWithIcons: 0,
      appsWithoutIcons: 0
    },
    notificationStatus: {
      lastLimitReached: 0,
      lastApproachingLimit: 0,
      notificationFrequency: 0,
      screenTimeLimit: 0,
      currentScreenTime: 0,
      remainingMinutes: 0
    }
  });

  const trackerService = AppUsageTracker.getInstance();

  useEffect(() => {
    let appStateListener: any;
    
    // Listen for app state changes
    App.addListener('appStateChange', ({ isActive }) => {
      setAppState(isActive ? 'active' : 'background');
    }).then(listener => {
      appStateListener = listener;
    });

    // Get background service status
    const backgroundService = BackgroundUpdateService.getInstance();
    const status = backgroundService.getServiceStatus();
    setBackgroundServiceStatus(status.isTracking ? 'Active' : 'Inactive');
    setUpdateInterval(status.updateInterval / 1000); // Convert to seconds
    setIsTracking(status.isTracking);
    setLastBackgroundUpdate(status.lastBackgroundUpdateTime ? 
      new Date(status.lastBackgroundUpdateTime).toLocaleTimeString() : 'Never');

    // Set up interval to update last update time
    const interval = setInterval(() => {
      const now = new Date();
      setLastUpdateTime(now.toLocaleTimeString());
      
      // Update background service status
      const currentStatus = backgroundService.getServiceStatus();
      setBackgroundServiceStatus(currentStatus.isTracking ? 'Active' : 'Inactive');
      setIsTracking(currentStatus.isTracking);
      setLastBackgroundUpdate(currentStatus.lastBackgroundUpdateTime ? 
        new Date(currentStatus.lastBackgroundUpdateTime).toLocaleTimeString() : 'Never');
    }, 1000);

    return () => {
      if (appStateListener) {
        appStateListener.remove();
      }
      clearInterval(interval);
    };
  }, []);

  useEffect(() => {
    const checkStatus = async () => {
      try {
        // Check usage permission
        const hasUsagePermission = await trackerService.hasUsagePermission();
        
        // Check battery optimization
        const isBatteryOptimizationExempt = await trackerService.isBatteryOptimizationExempt();
        
        // Check notifications status from localStorage
        const notificationsEnabled = localStorage.getItem('notificationsEnabled') === 'true';
        
        // Check background tracking status
        const lastUpdate = localStorage.getItem('lastUpdateTime');
        const backgroundTracking = lastUpdate !== null;
        
        // Get recent errors
        const errors = JSON.parse(localStorage.getItem('debugErrors') || '[]');

        // Get notification status
        const prefs = await trackerService.getSharedPreferences();
        const lastLimitReached = prefs.lastLimitReachedNotification;
        const lastApproachingLimit = prefs.lastApproachingLimitNotification;
        const screenTimeLimit = prefs.screenTimeLimit;
        const notificationFrequency = prefs.notificationFrequency;
        const currentScreenTime = prefs.totalScreenTime;
        const remainingMinutes = Math.max(0, screenTimeLimit - currentScreenTime);

        setDebugInfo({
          usagePermission: hasUsagePermission,
          batteryOptimization: isBatteryOptimizationExempt,
          notifications: notificationsEnabled,
          backgroundTracking,
          lastUpdate: lastUpdate ? new Date(parseInt(lastUpdate)).toLocaleTimeString() : 'Never',
          errors,
          appIconStatus: {
            totalApps: appUsageData.length,
            appsWithIcons: appUsageData.filter(app => app.icon).length,
            appsWithoutIcons: appUsageData.filter(app => !app.icon).length
          },
          notificationStatus: {
            lastLimitReached,
            lastApproachingLimit,
            notificationFrequency,
            screenTimeLimit,
            currentScreenTime,
            remainingMinutes
          }
        });
      } catch (error: any) {
        console.error('Error checking debug status:', error);
        setDebugInfo(prev => ({
          ...prev,
          errors: [`Error checking status: ${error.message}`, ...prev.errors].slice(0, 10)
        }));
      }
    };

    // Check immediately
    checkStatus();

    // Then check every 30 seconds
    const interval = setInterval(checkStatus, 30000);

    return () => clearInterval(interval);
  }, [appUsageData]);

  useEffect(() => {
    const checkNotificationStatus = async () => {
      try {
        const prefs = await trackerService.getSharedPreferences();
        setDebugInfo(prev => ({
          ...prev,
          notificationStatus: {
            lastLimitReached: prefs.lastLimitReachedNotification,
            lastApproachingLimit: prefs.lastApproachingLimitNotification,
            notificationFrequency: prefs.notificationFrequency,
            screenTimeLimit: prefs.screenTimeLimit,
            currentScreenTime: prefs.totalScreenTime,
            remainingMinutes: Math.max(0, prefs.screenTimeLimit - prefs.totalScreenTime)
          }
        }));
      } catch (error) {
        console.error('Error getting notification status:', error);
      }
    };

    checkNotificationStatus();
    const interval = setInterval(checkNotificationStatus, 1000);
    return () => clearInterval(interval);
  }, []);

  // Calculate total screen time
  const totalScreenTime = getTotalScreenTime();
  const totalHours = (totalScreenTime / 60).toFixed(2);
  const remainingMinutes = Math.max(0, screenTimeLimit - totalScreenTime);

  // Calculate missing icons list
  const missingIcons = appUsageData
    .filter(app => !app.icon)
    .map(app => app.name);

  return (
    <Paper 
      p="md" 
      style={{ 
        background: 'rgba(0, 0, 40, 0.5)',
        border: '1px solid #FF00FF',
        borderRadius: '8px'
      }}
    >
      <Stack gap="md">
        {/* Status Indicators */}
        <Group justify="space-between">
          <Text size="sm" style={{ color: '#00FFFF' }}>Usage Permission</Text>
          <Badge 
            color={debugInfo.usagePermission ? 'green' : 'red'}
            leftSection={debugInfo.usagePermission ? <FiCheckCircle /> : <FiXCircle />}
          >
            {debugInfo.usagePermission ? 'Granted' : 'Not Granted'}
          </Badge>
        </Group>

        <Group justify="space-between">
          <Text size="sm" style={{ color: '#00FFFF' }}>Battery Optimization</Text>
          <Badge 
            color={debugInfo.batteryOptimization ? 'green' : 'red'}
            leftSection={debugInfo.batteryOptimization ? <FiCheckCircle /> : <FiXCircle />}
          >
            {debugInfo.batteryOptimization ? 'Exempt' : 'Not Exempt'}
          </Badge>
        </Group>

        <Group justify="space-between">
          <Text size="sm" style={{ color: '#00FFFF' }}>Notifications</Text>
          <Badge 
            color={debugInfo.notifications ? 'green' : 'red'}
            leftSection={debugInfo.notifications ? <FiCheckCircle /> : <FiXCircle />}
          >
            {debugInfo.notifications ? 'Enabled' : 'Disabled'}
          </Badge>
        </Group>

        <Group justify="space-between">
          <Text size="sm" style={{ color: '#00FFFF' }}>Background Tracking</Text>
          <Badge 
            color={debugInfo.backgroundTracking ? 'green' : 'red'}
            leftSection={debugInfo.backgroundTracking ? <FiCheckCircle /> : <FiXCircle />}
          >
            {debugInfo.backgroundTracking ? 'Active' : 'Inactive'}
          </Badge>
        </Group>

        <Group justify="space-between">
          <Text size="sm" style={{ color: '#00FFFF' }}>Last Update</Text>
          <Badge 
            color="blue"
            leftSection={<FiClock />}
          >
            {debugInfo.lastUpdate}
          </Badge>
        </Group>

        {/* App Icon Status */}
        <Group justify="space-between">
          <Text size="sm" style={{ color: '#00FFFF' }}>App Icons</Text>
          <Badge 
            color={debugInfo.appIconStatus.appsWithIcons === debugInfo.appIconStatus.totalApps ? 'green' : 'yellow'}
            leftSection={<FiImage />}
          >
            {debugInfo.appIconStatus.appsWithIcons}/{debugInfo.appIconStatus.totalApps}
          </Badge>
        </Group>

        {/* Missing Icons List */}
        {debugInfo.appIconStatus.appsWithoutIcons > 0 && (
          <div>
            <Text size="sm" style={{ color: '#FF00FF', marginBottom: '0.5rem' }}>
              Apps Without Icons ({debugInfo.appIconStatus.appsWithoutIcons}):
            </Text>
            <ScrollArea h={100}>
              <Stack gap="xs">
                {missingIcons.map((appName: string, index: number) => (
                  <Text 
                    key={index} 
                    size="sm" 
                    style={{ color: '#AAAAAA' }}
                  >
                    {appName}
                  </Text>
                ))}
              </Stack>
            </ScrollArea>
          </div>
        )}

        {/* Recent Errors */}
        <div>
          <Text size="sm" style={{ color: '#FF00FF', marginBottom: '0.5rem' }}>
            Recent Errors
          </Text>
          <ScrollArea h={100}>
            <Stack gap="xs">
              {debugInfo.errors.length > 0 ? (
                debugInfo.errors.map((error, index) => (
                  <Text 
                    key={index} 
                    size="xs" 
                    style={{ 
                      color: '#FF0000',
                      display: 'flex',
                      alignItems: 'center',
                      gap: '0.5rem'
                    }}
                  >
                    <FiAlertCircle />
                    {error}
                  </Text>
                ))
              ) : (
                <Text size="xs" style={{ color: '#00FF00' }}>
                  No recent errors
                </Text>
              )}
            </Stack>
          </ScrollArea>
        </div>

        <div style={{ marginBottom: '15px' }}>
          <h4>App State</h4>
          <p>Current State: {appState}</p>
          <p>Last Update: {lastUpdateTime}</p>
          <p>Platform: {Capacitor.getPlatform()}</p>
        </div>

        <div style={{ marginBottom: '15px' }}>
          <h4>Background Service</h4>
          <p>Status: {backgroundServiceStatus}</p>
          <p>Tracking Active: {isTracking ? 'Yes' : 'No'}</p>
          <p>Update Interval: {updateInterval} seconds</p>
          <p>Last Background Update: {lastBackgroundUpdate}</p>
        </div>

        <div style={{ marginBottom: '15px' }}>
          <h4>Screen Time Data</h4>
          <p>Total Screen Time: {totalScreenTime.toFixed(2)} minutes ({totalHours} hours)</p>
          <p>Screen Time Limit: {screenTimeLimit} minutes</p>
          <p>Remaining Minutes: {remainingMinutes}</p>
          <p>Apps Tracked: {appUsageData.length}</p>
        </div>

        <div style={{ marginBottom: '15px' }}>
          <h4>Raw Data</h4>
          <pre style={{ 
            backgroundColor: '#fff', 
            padding: '10px', 
            borderRadius: '4px',
            overflow: 'auto',
            maxHeight: '200px'
          }}>
            {JSON.stringify(appUsageData, null, 2)}
          </pre>
        </div>

        {/* Notification Status Section */}
        <div>
          <Text size="sm" style={{ color: '#FF00FF', marginBottom: '0.5rem' }}>
            Notification Status
          </Text>
          <Stack gap="xs">
            <Group justify="space-between">
              <Text size="sm" style={{ color: '#00FFFF' }}>Last Limit Reached</Text>
              <Text size="sm" style={{ color: '#00FFFF' }}>{debugInfo.notificationStatus.lastLimitReached}</Text>
            </Group>
            <Group justify="space-between">
              <Text size="sm" style={{ color: '#00FFFF' }}>Last Approaching Limit</Text>
              <Text size="sm" style={{ color: '#00FFFF' }}>{debugInfo.notificationStatus.lastApproachingLimit}</Text>
            </Group>
            <Group justify="space-between">
              <Text size="sm" style={{ color: '#00FFFF' }}>Notification Frequency</Text>
              <Text size="sm" style={{ color: '#00FFFF' }}>{debugInfo.notificationStatus.notificationFrequency} minutes</Text>
            </Group>
            <Group justify="space-between">
              <Text size="sm" style={{ color: '#00FFFF' }}>Screen Time Limit</Text>
              <Text size="sm" style={{ color: '#00FFFF' }}>{debugInfo.notificationStatus.screenTimeLimit} minutes</Text>
            </Group>
            <Group justify="space-between">
              <Text size="sm" style={{ color: '#00FFFF' }}>Current Screen Time</Text>
              <Text size="sm" style={{ color: '#00FFFF' }}>{debugInfo.notificationStatus.currentScreenTime.toFixed(2)} minutes</Text>
            </Group>
            <Group justify="space-between">
              <Text size="sm" style={{ color: '#00FFFF' }}>Remaining Minutes</Text>
              <Text size="sm" style={{ color: '#00FFFF' }}>{debugInfo.notificationStatus.remainingMinutes} minutes</Text>
            </Group>
          </Stack>
        </div>
      </Stack>
    </Paper>
  );
};

export default DebugPanel; 