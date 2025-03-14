import { Container, Title, NumberInput, Button, Text, Switch, Group, Modal, Loader } from '@mantine/core';
import { useNavigate } from 'react-router-dom';
import { useScreenTime } from '../context/ScreenTimeContext';
import { useState, useEffect } from 'react';
import AppUsageTracker from '../services/AppUsageTracker';
import { FiAlertCircle, FiCheckCircle, FiBarChart2 } from 'react-icons/fi';

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
    resetDailyUsage
  } = useScreenTime();
  
  const [previousNotificationState, setPreviousNotificationState] = useState(notificationsEnabled);
  const [resetModalOpen, setResetModalOpen] = useState(false);
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

  // Log modal state changes
  useEffect(() => {
    console.log('Modal state changed:', resetModalOpen);
  }, [resetModalOpen]);

  const handleSaveSettings = async () => {
    console.log('Save Settings clicked. Notification state:', notificationsEnabled, 'Previous state:', previousNotificationState);
    
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
    alert('Settings saved successfully!');
    navigate('/');
  };

  const handleResetUsage = () => {
    console.log('Reset button clicked - handleResetUsage function triggered');
    
    // Verify that resetDailyUsage exists
    if (!resetDailyUsage) {
      console.error('resetDailyUsage function is not available!');
      alert('Error: Reset function not available. Please reload the app.');
      return;
    }
    
    try {
      console.log('Attempting to reset app usage data...');
      
      // Call the reset function with direct feedback
      const resetResult = resetDailyUsage();
      console.log('Reset function returned:', resetResult);
      
      // Close the modal
      setResetModalOpen(false);
      
      // Show an alert for immediate feedback
      alert('App usage data has been reset successfully!');
      
      // Force a reload of the app data from localStorage
      localStorage.setItem('forceRefresh', Date.now().toString());
      
      // Navigate to Statistics page to show the reset data
      console.log('Navigating to statistics page with reset state');
      navigate('/statistics', { state: { fromReset: true, timestamp: Date.now() } });
      
      console.log('Reset completed and navigating to statistics');
    } catch (error) {
      console.error('Error resetting data:', error);
      alert('There was an error resetting your data. Please try again.');
    }
  };

  // Frequency options
  const frequencyOptions = [
    { value: '5', label: '5m' },
    { value: '15', label: '15m' },
    { value: '30', label: '30m' },
    { value: '60', label: '60m' }
  ];

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

        <Text style={{ color: '#00FFFF', marginBottom: '0.5rem' }}>
          Daily Screen Time Limit (minutes)
        </Text>
        <NumberInput
          value={screenTimeLimit}
          onChange={(val) => setScreenTimeLimit(Number(val) || 0)}
          min={5}
          max={1440}
          step={5}
          style={{ marginBottom: '2rem' }}
          styles={{
            input: {
              backgroundColor: 'rgba(0, 0, 40, 0.3)',
              color: '#FFFFFF',
              borderColor: '#FF00FF',
              '&:focus': {
                borderColor: '#00FFFF',
              }
            },
            control: {
              backgroundColor: 'rgba(0, 0, 40, 0.5)',
              borderColor: '#FF00FF',
              color: '#FFFFFF',
              '&:hover': {
                backgroundColor: 'rgba(255, 0, 255, 0.2)',
              }
            }
          }}
        />

        <Group style={{ marginBottom: '2rem', display: 'flex', justifyContent: 'space-between' }}>
          <Text style={{ color: '#00FFFF' }}>
            Enable Notifications
          </Text>
          <Switch 
            checked={notificationsEnabled}
            onChange={(event) => {
              setNotificationsEnabled(event.currentTarget.checked);
              // Remove immediate navigation - let the Save Settings button handle this
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
          <>
            <Text style={{ color: '#00FFFF', marginBottom: '1rem' }}>
              Notification Frequency (minutes before limit)
            </Text>
            
            <div style={{ marginBottom: '2rem' }}>
              <Group style={{ display: 'flex', justifyContent: 'space-between', gap: '8px' }}>
                {frequencyOptions.map((option) => (
                  <Button
                    key={option.value}
                    variant={notificationFrequency === parseInt(option.value) ? "filled" : "outline"}
                    onClick={() => setNotificationFrequency(parseInt(option.value))}
                    style={{
                      flex: 1,
                      backgroundColor: notificationFrequency === parseInt(option.value) 
                        ? '#FF00FF' 
                        : 'transparent',
                      borderColor: '#FF00FF',
                      color: notificationFrequency === parseInt(option.value) 
                        ? '#000020' 
                        : '#00FFFF',
                      fontWeight: 'bold',
                    }}
                  >
                    {option.label}
                  </Button>
                ))}
              </Group>
            </div>
          </>
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

      {/* Data Management Section */}
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
          Data Management
        </Title>

        <Text style={{ color: '#00FFFF', marginBottom: '1.5rem' }}>
          Reset your daily app usage data to start fresh. This action cannot be undone.
        </Text>

        <Button
          fullWidth
          size="lg"
          color="red"
          onClick={() => {
            console.log('RESET DAILY USAGE DATA button clicked');
            console.log('Current resetModalOpen state:', resetModalOpen);
            setResetModalOpen(true);
            console.log('New resetModalOpen state:', true);
          }}
          style={{
            backgroundColor: 'rgba(255, 0, 0, 0.7)',
            color: 'white',
          }}
        >
          RESET DAILY USAGE DATA
        </Button>
      </div>

      {/* Reset Confirmation Modal */}
      <Modal
        opened={resetModalOpen}
        onClose={() => {
          console.log('Modal close button clicked');
          setResetModalOpen(false);
        }}
        title="Reset Daily Usage Data"
        styles={{
          title: { color: '#FF00FF', fontWeight: 'bold' },
          body: { backgroundColor: '#000030' },
          header: { backgroundColor: '#000030' },
          close: { color: '#00FFFF' },
        }}
        overlayProps={{ opacity: 0.7, blur: 3 }}
        zIndex={1000}
      >
        <Text style={{ color: '#FFFFFF', marginBottom: '1.5rem' }}>
          Are you sure you want to reset your daily app usage data? This action cannot be undone.
        </Text>
        <Group style={{ display: 'flex', justifyContent: 'space-between' }}>
          <Button
            variant="outline"
            color="cyan"
            onClick={() => setResetModalOpen(false)}
            style={{ borderColor: '#00FFFF', color: '#00FFFF' }}
          >
            CANCEL
          </Button>
          <Button
            color="red"
            onClick={(e) => {
              console.log('Reset button in modal clicked');
              e.preventDefault();
              handleResetUsage();
            }}
            style={{
              backgroundColor: 'rgba(255, 0, 0, 0.7)'
            }}
          >
            RESET DATA
          </Button>
        </Group>
      </Modal>
      
      {/* Fallback DIV-based Modal */}
      {resetModalOpen && (
        <div 
          style={{
            position: 'fixed',
            top: 0,
            left: 0,
            right: 0,
            bottom: 0,
            backgroundColor: 'rgba(0, 0, 0, 0.8)',
            display: 'flex',
            justifyContent: 'center',
            alignItems: 'center',
            zIndex: 2000
          }}
        >
          <div 
            style={{
              backgroundColor: '#000030',
              padding: '20px',
              borderRadius: '8px',
              width: '80%',
              maxWidth: '500px',
              border: '1px solid #FF00FF'
            }}
          >
            <div style={{ display: 'flex', justifyContent: 'space-between', marginBottom: '15px' }}>
              <h2 style={{ color: '#FF00FF', margin: 0 }}>Reset Daily Usage Data</h2>
              <button 
                onClick={() => setResetModalOpen(false)}
                style={{ 
                  background: 'none', 
                  border: 'none', 
                  color: '#00FFFF', 
                  fontSize: '20px',
                  cursor: 'pointer'
                }}
              >
                ✕
              </button>
            </div>
            
            <p style={{ color: '#FFFFFF', marginBottom: '20px' }}>
              Are you sure you want to reset your daily app usage data? This action cannot be undone.
            </p>
            
            <div style={{ display: 'flex', justifyContent: 'space-between' }}>
              <button
                onClick={() => setResetModalOpen(false)}
                style={{ 
                  padding: '8px 16px',
                  backgroundColor: 'transparent',
                  color: '#00FFFF',
                  border: '1px solid #00FFFF',
                  borderRadius: '4px',
                  cursor: 'pointer'
                }}
              >
                CANCEL
              </button>
              <button
                onClick={() => {
                  console.log('Reset button in DIV modal clicked');
                  handleResetUsage();
                }}
                style={{ 
                  padding: '8px 16px',
                  backgroundColor: 'rgba(255, 0, 0, 0.7)',
                  color: 'white',
                  border: 'none',
                  borderRadius: '4px',
                  cursor: 'pointer'
                }}
              >
                RESET DATA
              </button>
            </div>
          </div>
        </div>
      )}
    </Container>
  );
};

export default Settings; 