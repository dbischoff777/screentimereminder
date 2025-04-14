import { Container, Title, Button, Text, Switch, Group, Loader } from '@mantine/core';
import { useNavigate } from 'react-router-dom';
import { useScreenTime } from '../context/ScreenTimeContext';
import { useState, useEffect } from 'react';
import AppUsageTracker from '../services/AppUsageTracker';
import { FiAlertCircle, FiCheckCircle } from 'react-icons/fi';
import CustomDropdown from '../components/CustomDropdown';
import { SettingsConstants } from '../constants/SettingsConstants';

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
  const [isLoadingSettings, setIsLoadingSettings] = useState(true);

  // Get the tracker service instance
  const trackerService = AppUsageTracker.getInstance();

  // Load saved settings from native layer
  useEffect(() => {
    const loadSavedSettings = async () => {
      try {
        setIsLoadingSettings(true);
        
        // Get shared preferences from native layer
        const prefs = await trackerService.getSharedPreferences();
        console.log('Loaded settings from native layer:', prefs);
        
        // Update screen time limit if different
        if (typeof prefs.screenTimeLimit === 'number' && prefs.screenTimeLimit !== screenTimeLimit) {
          setScreenTimeLimit({ limitMinutes: prefs.screenTimeLimit });
        }
        
        // Update notification frequency if different
        if (typeof prefs.notificationFrequency === 'number' && prefs.notificationFrequency !== notificationFrequency) {
          setNotificationFrequency({ frequency: prefs.notificationFrequency });
        }
        
        // Check usage permission
        const hasPermission = await trackerService.hasUsagePermission();
        setPermissionStatus(hasPermission);
        if (hasPermission !== usageAccessEnabled) {
          setUsageAccessEnabled(hasPermission);
        }
        
      } catch (error) {
        console.error('Error loading saved settings:', error);
      } finally {
        setIsLoadingSettings(false);
      }
    };

    loadSavedSettings();
  }, []);

  // Initialize permission states
  useEffect(() => {
    setPreviousNotificationState(notificationsEnabled);
    setPreviousUsageAccessState(usageAccessEnabled);
    checkUsagePermission();
  }, [notificationsEnabled, usageAccessEnabled, setUsageAccessEnabled]);

  // Set up permission check interval
  useEffect(() => {
    let intervalId: NodeJS.Timeout | null = null;
    
    if (isRequestingPermission) {
      intervalId = setInterval(() => {
        checkUsagePermission();
      }, SettingsConstants.PERMISSION_CHECK_INTERVAL);
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
      setPermissionStatus(hasPermission);
      
      if (hasPermission !== usageAccessEnabled) {
        setUsageAccessEnabled(hasPermission);
      }
      
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
      await trackerService.requestUsagePermission();
    } catch (error) {
      console.error('Error requesting usage access permission:', error);
      setIsRequestingPermission(false);
    }
  };

  const handleSaveSettings = async () => {
    // Handle notification permissions
    if (notificationsEnabled && !previousNotificationState) {
      try {
        if (window.Capacitor?.isNativePlatform()) {
          const { LocalNotifications } = await import('@capacitor/local-notifications');
          const permResult = await LocalNotifications.checkPermissions();
          
          if (permResult.display !== 'granted') {
            navigate('/notification-permission');
            return;
          }
        } else if (Notification && Notification.permission !== 'granted') {
          navigate('/notification-permission');
          return;
        }
      } catch (error) {
        console.error('Error checking notification permissions:', error);
        navigate('/notification-permission');
        return;
      }
    }
    
    // Handle usage access permissions
    if (usageAccessEnabled && !previousUsageAccessState) {
      requestUsagePermission();
      return;
    }
    
    try {
      // Update settings in AppUsageTracker
      await trackerService.setScreenTimeLimit(screenTimeLimit);
      await trackerService.setNotificationFrequency({ frequency: notificationFrequency });

      alert('Settings saved successfully!');
      navigate('/');
    } catch (error) {
      console.error('Error saving settings:', error);
      alert('Failed to save settings. Please try again.');
    }
  };

  // Generate screen time limit options
  const screenTimeLimitOptions = [
    { value: SettingsConstants.MIN_SCREEN_TIME_LIMIT.toString(), label: '30m' },
    { value: '60', label: '1h' },
    { value: '90', label: '1.5h' },
    { value: '120', label: '2h' },
    { value: '150', label: '2.5h' },
    { value: '180', label: '3h' },
    { value: '210', label: '3.5h' },
    { value: SettingsConstants.MAX_SCREEN_TIME_LIMIT.toString(), label: '4h' },
  ];

  // Generate notification frequency options
  const notificationFrequencyOptions = [
    { value: SettingsConstants.MIN_NOTIFICATION_FREQUENCY.toString(), label: '5m' },
    { value: '15', label: '15m' },
    { value: '30', label: '30m' },
    { value: '45', label: '45m' },
    { value: SettingsConstants.MAX_NOTIFICATION_FREQUENCY.toString(), label: '60m' }
  ];

  return (
    <Container size="md" py="xl" style={{ background: '#000020', minHeight: '100vh', padding: '1rem' }}>
      <Title order={1} style={{
        fontSize: '2rem',
        marginBottom: '2rem',
        color: '#00FFFF',
        textShadow: '0 0 10px #00FFFF',
        textAlign: 'center',
      }}>
        SYSTEM SETTINGS
      </Title>

      {isLoadingSettings ? (
        <Container style={{ 
          display: 'flex', 
          flexDirection: 'column', 
          alignItems: 'center', 
          justifyContent: 'center',
          minHeight: '200px'
        }}>
          <Loader color="#00FFFF" size="xl" />
          <Text style={{ color: '#00FFFF', marginTop: '1rem' }}>
            Loading settings...
          </Text>
        </Container>
      ) : (
        <Container style={{
          padding: '2rem',
          background: 'transparent',
          borderTop: '1px solid #FF00FF',
          borderBottom: '1px solid #FF00FF',
          marginBottom: '2rem',
        }}>
          <Title order={3} style={{
            color: '#FF00FF',
            marginBottom: '1.5rem',
            textShadow: '0 0 5px #FF00FF',
          }}>
            Screen Time Configuration
          </Title>

          <CustomDropdown
            options={screenTimeLimitOptions}
            value={screenTimeLimit.toString()}
            onChange={(value) => {
              const limitMinutes = typeof value === 'string' ? parseInt(value, 10) : value;
              if (limitMinutes >= SettingsConstants.MIN_SCREEN_TIME_LIMIT && 
                  limitMinutes <= SettingsConstants.MAX_SCREEN_TIME_LIMIT) {
                setScreenTimeLimit({ limitMinutes });
              }
            }}
            label="Daily Screen Time Limit"
          />

          <Group style={{ marginBottom: '2rem', display: 'flex', justifyContent: 'space-between' }}>
            <Text style={{ color: '#00FFFF' }}>Enable Notifications</Text>
            <Switch 
              checked={notificationsEnabled}
              onChange={(event) => setNotificationsEnabled(event.currentTarget.checked)}
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
              <Text style={{ color: '#00FFFF' }}>Enable Usage Access</Text>
              <Switch 
                checked={usageAccessEnabled}
                onChange={(event) => {
                  setUsageAccessEnabled(event.currentTarget.checked);
                  if (event.currentTarget.checked && !permissionStatus) {
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

            {/* Permission Status Indicators */}
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
              <div style={{
                padding: '0.5rem',
                background: 'rgba(0, 255, 0, 0.1)',
                borderLeft: '4px solid #00FF00',
                marginTop: '0.5rem',
              }}>
                <Text size="sm" style={{ color: '#00FF00' }}>
                  <FiCheckCircle style={{ verticalAlign: 'middle', marginRight: '0.5rem' }} />
                  Usage access permission granted
                </Text>
              </div>
            )}
            
            {permissionStatus === false && usageAccessEnabled && !isCheckingPermission && !isRequestingPermission && (
              <div style={{
                padding: '0.5rem',
                background: 'rgba(255, 0, 0, 0.1)',
                borderLeft: '4px solid #FF0000',
                marginTop: '0.5rem',
              }}>
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
          </div>

          {notificationsEnabled && (
            <CustomDropdown
              options={notificationFrequencyOptions}
              value={notificationFrequency.toString()}
              onChange={(value) => {
                const frequency = parseInt(value.toString(), 10);
                if (frequency >= SettingsConstants.MIN_NOTIFICATION_FREQUENCY && 
                    frequency <= SettingsConstants.MAX_NOTIFICATION_FREQUENCY) {
                  setNotificationFrequency({ frequency });
                }
              }}
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
        </Container>
      )}
    </Container>
  );
};

export default Settings; 