import { Container, Title, Button, Text, Switch, Group, Loader } from '@mantine/core';
import { useNavigate } from 'react-router-dom';
import { useScreenTime } from '../context/ScreenTimeContext';
import { useState, useEffect } from 'react';
import AppUsageTracker from '../services/AppUsageTracker';
import { FiAlertCircle, FiCheckCircle, FiBarChart2 } from 'react-icons/fi';
import CustomDropdown from '../components/CustomDropdown';

// Add type declaration for Capacitor on window object if not already defined
declare global {
  interface Window {
    Capacitor?: {
      isNativePlatform: () => boolean;
    };
  }
}

const Settings = () => {
  const navigate = useNavigate();
  const { 
    screenTimeLimit, 
    setScreenTimeLimit, 
    notificationsEnabled, 
    setNotificationsEnabled, 
    notificationFrequency, 
    setNotificationFrequency,
    usageAccessEnabled,
    setUsageAccessEnabled,
  } = useScreenTime();
  
  const [previousNotificationState, setPreviousNotificationState] = useState(notificationsEnabled);
  const [previousUsageAccessState, setPreviousUsageAccessState] = useState(usageAccessEnabled);
  const [isCheckingPermission, setIsCheckingPermission] = useState(false);
  const [isRequestingPermission, setIsRequestingPermission] = useState(false);
  const [permissionStatus, setPermissionStatus] = useState<boolean | null>(null);

  // Get the tracker service instance
  const trackerService = AppUsageTracker.getInstance();

  // Check if running on mobile and initialize permission states
  useEffect(() => {
    // Initialize permission states
    setPreviousNotificationState(notificationsEnabled);
    setPreviousUsageAccessState(usageAccessEnabled);
    
    // Check usage access permission
    checkUsagePermission();
  }, [notificationsEnabled, usageAccessEnabled, setUsageAccessEnabled]);

  // Set up an interval to check permission status periodically when requesting
  useEffect(() => {
    let intervalId: NodeJS.Timeout | null = null;
    
    if (isRequestingPermission) {
      intervalId = setInterval(() => {
        checkUsagePermission();
      }, 3000); // Check every 3 seconds
    }
    
    return () => {
      if (intervalId) clearInterval(intervalId);
    };
  }, [isRequestingPermission]);

  // Check usage access permission
  const checkUsagePermission = async () => {
    try {
      setIsCheckingPermission(true);
      const hasPermission = await trackerService.hasUsagePermission();
      console.log('Usage access permission status:', hasPermission);
      setPermissionStatus(hasPermission);
      
      // Only update if different from current state
      if (hasPermission !== usageAccessEnabled) {
        setUsageAccessEnabled(hasPermission);
      }
      
      // If we were requesting permission and now have it, stop requesting
      if (isRequestingPermission && hasPermission) {
        setIsRequestingPermission(false);
      }
    } catch (error) {
      console.error('Error checking usage access permission:', error);
      setPermissionStatus(false);
    } finally {
      setIsCheckingPermission(false);
    }
  };

  // Request usage access permission
  const requestUsagePermission = async () => {
    try {
      setIsRequestingPermission(true);
      console.log('Requesting usage access permission...');
      await trackerService.requestUsagePermission();
      console.log('Usage access permission request sent');
      
      // We'll check the permission status in the interval
    } catch (error) {
      console.error('Error requesting usage access permission:', error);
      setIsRequestingPermission(false);
    }
  };

  const handleSaveSettings = async () => {
    console.log('Save Settings clicked. Notification state:', notificationsEnabled, 'Previous state:', previousNotificationState);
    console.log('Current screen time limit:', screenTimeLimit);
    
    // Handle notification permissions for mobile devices
    if (notificationsEnabled) {
      try {
        // For mobile devices, we need to use Capacitor's LocalNotifications API
        if (window.Capacitor && window.Capacitor.isNativePlatform()) {
          console.log('Mobile device detected, checking notification permission with Capacitor');
          const { LocalNotifications } = await import('@capacitor/local-notifications');
          
          // Check if we need to request permission
          const permResult = await LocalNotifications.checkPermissions();
          console.log('Current notification permission status:', permResult.display);
          
          if (permResult.display !== 'granted' || !previousNotificationState) {
            console.log('Navigating to notification permission page');
            navigate('/notification-permission');
            return;
          }
        } else {
          // Fallback for browser testing
          if (!previousNotificationState || (Notification && Notification.permission !== 'granted')) {
            console.log('Navigating to notification permission page (browser fallback)');
            navigate('/notification-permission');
            return;
          }
        }
      } catch (error) {
        console.error('Error checking notification permissions:', error);
        // If there's an error, navigate to permission page to be safe
        navigate('/notification-permission');
        return;
      }
    }
    
    // Handle usage access permissions
    if (usageAccessEnabled && !previousUsageAccessState) {
      requestUsagePermission();
      return;
    }
    
    // If we've made it here, all permissions are handled
    // Show a success message or navigate back to home
    console.log('Settings saved successfully. New screen time limit:', screenTimeLimit);
    alert('Settings saved successfully!');
    navigate('/');
  };

  return (
    <Container 
      size="md" 
      py="xl"
      style={{
        background: '#000020', // Dark blue-black background
        minHeight: '100vh',
        padding: '1rem'
      }}
    >
      <Title
        order={1}
        style={{
          fontSize: '2rem',
          marginBottom: '2rem',
          color: '#00FFFF',
          textShadow: '0 0 10px #00FFFF',
          textAlign: 'center',
        }}
      >
        SYSTEM SETTINGS
      </Title>

      <div
        style={{
          padding: '2rem',
          background: 'transparent',
          borderTop: '1px solid #FF00FF',
          borderBottom: '1px solid #FF00FF',
          marginBottom: '2rem',
        }}
      >
        <Title
          order={3}
          style={{
            color: '#FF00FF',
            marginBottom: '1.5rem',
            textShadow: '0 0 5px #FF00FF',
          }}
        >
          Screen Time Configuration
        </Title>
        <CustomDropdown
          options={[
            { value: '30', label: '30m' },
            { value: '60', label: '1h' },
            { value: '120', label: '2h' },
            { value: '180', label: '3h' },
            { value: '240', label: '4h' },
            { value: '300', label: '5h' },
            { value: '360', label: '6h' },
            { value: '420', label: '7h' },
            { value: '480', label: '8h' }
          ]}
          value={screenTimeLimit}
          onChange={setScreenTimeLimit}
          label="Daily Screen Time Limit"
        />

        <Group style={{ marginBottom: '2rem', display: 'flex', justifyContent: 'space-between' }}>
          <Text style={{ color: '#00FFFF' }}>
            Enable Notifications
          </Text>
          <Switch 
            checked={notificationsEnabled}
            onChange={(event) => {
              setNotificationsEnabled(event.currentTarget.checked);
            }}
            color="cyan"
            styles={{
              track: {
                backgroundColor: notificationsEnabled ? 'rgba(0, 255, 255, 0.5)' : 'rgba(100, 100, 100, 0.3)',
                borderColor: notificationsEnabled ? '#00FFFF' : '#666666',
              }
            }}
          />
        </Group>

        {/* Usage Access Permission Section */}
        <div style={{ marginBottom: '2rem' }}>
          <Group style={{ display: 'flex', justifyContent: 'space-between' }}>
            <Text style={{ color: '#00FFFF' }}>
              Enable Usage Access
            </Text>
            <Switch 
              checked={usageAccessEnabled}
              onChange={(event) => {
                setUsageAccessEnabled(event.currentTarget.checked);
                if (event.currentTarget.checked && !permissionStatus) {
                  // If turning on and don't have permission, request it
                  requestUsagePermission();
                }
              }}
              color="cyan"
              styles={{
                track: {
                  backgroundColor: usageAccessEnabled ? 'rgba(0, 255, 255, 0.5)' : 'rgba(100, 100, 100, 0.3)',
                  borderColor: usageAccessEnabled ? '#00FFFF' : '#666666',
                }
              }}
            />
          </Group>
          
          {/* Permission Status Indicator */}
          {isCheckingPermission && (
            <div style={{ textAlign: 'center', margin: '1rem 0' }}>
              <Loader color="#00FFFF" size="sm" />
              <Text size="sm" style={{ color: '#00FFFF', marginTop: '0.5rem' }}>
                Checking permission status...
              </Text>
            </div>
          )}
          
          {isRequestingPermission && !isCheckingPermission && (
            <div style={{ textAlign: 'center', margin: '1rem 0' }}>
              <Loader color="#FF00FF" size="sm" />
              <Text size="sm" style={{ color: '#FF00FF', marginTop: '0.5rem' }}>
                Waiting for permission to be granted...
              </Text>
              <Text size="xs" style={{ color: '#f0f0f0', marginTop: '0.5rem' }}>
                When the settings page opens, find "Screen Time Reminder" in the list and toggle it ON.
                Then return to this app.
              </Text>
            </div>
          )}
          
          {permissionStatus === true && !isCheckingPermission && !isRequestingPermission && (
            <div
              style={{
                padding: '0.5rem',
                background: 'rgba(0, 255, 0, 0.1)',
                borderLeft: '4px solid #00FF00',
                marginTop: '0.5rem',
              }}
            >
              <Text size="sm" style={{ color: '#00FF00' }}>
                <FiCheckCircle style={{ verticalAlign: 'middle', marginRight: '0.5rem' }} />
                Usage access permission granted
              </Text>
            </div>
          )}
          
          {permissionStatus === false && usageAccessEnabled && !isCheckingPermission && !isRequestingPermission && (
            <div
              style={{
                padding: '0.5rem',
                background: 'rgba(255, 0, 0, 0.1)',
                borderLeft: '4px solid #FF0000',
                marginTop: '0.5rem',
              }}
            >
              <Text size="sm" style={{ color: '#FF0000' }}>
                <FiAlertCircle style={{ verticalAlign: 'middle', marginRight: '0.5rem' }} />
                Permission not granted
              </Text>
              <Button
                size="xs"
                onClick={requestUsagePermission}
                style={{
                  marginTop: '0.5rem',
                  background: 'linear-gradient(45deg, #FF00FF, #00FFFF)',
                }}
              >
                Request Permission
              </Button>
            </div>
          )}
          
          {usageAccessEnabled && (
            <Text size="xs" style={{ color: '#f0f0f0', marginTop: '0.5rem' }}>
              <FiBarChart2 style={{ verticalAlign: 'middle', marginRight: '0.5rem' }} color="#FF00FF" />
              Usage access allows the app to track your screen time and provide accurate statistics.
            </Text>
          )}
        </div>

        {notificationsEnabled && (
          <CustomDropdown
            options={[
              { value: '1', label: '1m' },
              { value: '5', label: '5m' },
              { value: '15', label: '15m' },
              { value: '30', label: '30m' },
              { value: '60', label: '60m' }
            ]}
            value={notificationFrequency}
            onChange={setNotificationFrequency}
            label="Notification Frequency"
          />
        )}

        <Button
          fullWidth
          size="lg"
          onClick={handleSaveSettings}
          style={{
            marginTop: '1.5rem',
            background: 'linear-gradient(45deg, #FF00FF, #00FFFF)',
          }}
        >
          SAVE SETTINGS
        </Button>
      </div>
    </Container>
  );
};

export default Settings; 