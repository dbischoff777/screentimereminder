import { useEffect, useState } from 'react';
import { Paper, Text, Group, Badge, Stack, ScrollArea } from '@mantine/core';
import { FiCheckCircle, FiXCircle, FiAlertCircle, FiClock, FiImage } from 'react-icons/fi';
import AppUsageTracker from '../services/AppUsageTracker';
import { useScreenTime } from '../context/ScreenTimeContext';

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
    missingIcons: string[];
  };
}

const DebugPanel = () => {
  const { appUsageData } = useScreenTime();
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
      missingIcons: []
    }
  });

  const trackerService = AppUsageTracker.getInstance();

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

        // Calculate app icon status
        const missingIcons = appUsageData
          .filter(app => !app.icon)
          .map(app => app.name);
        
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
            missingIcons
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
        {debugInfo.appIconStatus.missingIcons.length > 0 && (
          <div>
            <Text size="sm" style={{ color: '#FF00FF', marginBottom: '0.5rem' }}>
              Apps Missing Icons
            </Text>
            <ScrollArea h={100}>
              <Stack gap="xs">
                {debugInfo.appIconStatus.missingIcons.map((appName, index) => (
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
      </Stack>
    </Paper>
  );
};

export default DebugPanel; 