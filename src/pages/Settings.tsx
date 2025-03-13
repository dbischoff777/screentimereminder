import { Container, Title, NumberInput, Button, Text, Switch, Group } from '@mantine/core';
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
    setNotificationFrequency
  } = useScreenTime();
  
  const [isMobile, setIsMobile] = useState(false);
  const [previousNotificationState, setPreviousNotificationState] = useState(notificationsEnabled);

  // Check if running on mobile
  useEffect(() => {
    // Simple check for mobile - can be enhanced
    setIsMobile(/Android|webOS|iPhone|iPad|iPod|BlackBerry|IEMobile|Opera Mini/i.test(navigator.userAgent));
    setPreviousNotificationState(notificationsEnabled);
  }, [notificationsEnabled]);

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
    </Container>
  );
};

export default Settings; 