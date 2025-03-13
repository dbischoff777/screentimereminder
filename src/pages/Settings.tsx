import { Container, Title, NumberInput, Button, Text, Switch, Group, Modal } from '@mantine/core';
import { useNavigate } from 'react-router-dom';
import { useScreenTime } from '../context/ScreenTimeContext';
import { useState, useEffect } from 'react';

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
    resetDailyUsage
  } = useScreenTime();
  
  const [isMobile, setIsMobile] = useState(false);
  const [previousNotificationState, setPreviousNotificationState] = useState(notificationsEnabled);
  const [resetModalOpen, setResetModalOpen] = useState(false);

  // Check if running on mobile
  useEffect(() => {
    // Simple check for mobile - can be enhanced
    setIsMobile(/Android|webOS|iPhone|iPad|iPod|BlackBerry|IEMobile|Opera Mini/i.test(navigator.userAgent));
    setPreviousNotificationState(notificationsEnabled);
  }, [notificationsEnabled]);

  // Log modal state changes
  useEffect(() => {
    console.log('Modal state changed:', resetModalOpen);
  }, [resetModalOpen]);

  const handleSaveSettings = () => {
    // If notifications were just enabled or we're on mobile and notifications are enabled,
    // always navigate to the permission page
    if ((notificationsEnabled && !previousNotificationState) || 
        (isMobile && notificationsEnabled)) {
      navigate('/notification-permission');
      return;
    }
    
    // For desktop browsers, check permission status
    if (notificationsEnabled && !isMobile && Notification.permission !== 'granted') {
      navigate('/notification-permission');
    }
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