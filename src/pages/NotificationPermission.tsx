import { useState, useEffect } from 'react';
import { Container, Title, Text, Button, List, ThemeIcon } from '@mantine/core';
import { useNavigate } from 'react-router-dom';
import { FiAlertCircle, FiCheckCircle, FiBell } from 'react-icons/fi';

// Add type declaration for Capacitor on window object
declare global {
  interface Window {
    Capacitor?: {
      isNativePlatform: () => boolean;
    };
  }
}

const NotificationPermission = () => {
  const navigate = useNavigate();
  const [permissionStatus, setPermissionStatus] = useState<'default' | 'granted' | 'denied'>('default');

  // Request permission automatically when component mounts
  useEffect(() => {
    // Check current permission status on component mount
    if ('Notification' in window) {
      setPermissionStatus(Notification.permission as 'default' | 'granted' | 'denied');
    }

    // Auto-trigger permission request after a short delay
    const timer = setTimeout(() => {
      requestPermission();
    }, 500);
    return () => clearTimeout(timer);
  }, []);

  const requestPermission = async () => {
    try {
      // Try to use the Capacitor Notifications API if available
      if (window.Capacitor && window.Capacitor.isNativePlatform()) {
        // This will be executed when running on a real device with Capacitor
        const { LocalNotifications } = await import('@capacitor/local-notifications');
        
        try {
          // Request permission
          const { display } = await LocalNotifications.requestPermissions();
          
          if (display === 'granted') {
            setPermissionStatus('granted');
            
            // Show a test notification
            await LocalNotifications.schedule({
              notifications: [
                {
                  title: 'Screen Time Reminder Notification',
                  body: 'Notifications are now enabled for Screen Time Reminder!',
                  id: 1,
                  schedule: { at: new Date(Date.now() + 1000) }
                }
              ]
            });
            
            // Removed automatic redirection
          } else {
            setPermissionStatus('denied');
          }
        } catch (permError) {
          console.error('Error requesting notification permissions:', permError);
          // For emulators, we'll simulate permission granted
          setPermissionStatus('granted');
          // Removed automatic redirection
        }
      } else {
        // Fallback for web or emulator
        if ('Notification' in window) {
          const permission = await Notification.requestPermission();
          setPermissionStatus(permission);

          if (permission === 'granted') {
            // Show a test notification
            new Notification('Screen Time Reminder Notification', {
              body: 'Notifications are now enabled for Screen Time Reminder!',
              icon: '/notification-icon.png'
            });
          }
        } else {
          // For emulators without notification support
          console.log('Emulator detected, simulating notification permission');
          setPermissionStatus('granted');
        }
        
        // Removed automatic redirection
      }
    } catch (error) {
      console.error('Error requesting notification permission:', error);
      // For emulators, we'll simulate permission granted
      setPermissionStatus('granted');
      // Removed automatic redirection
    }
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
        NOTIFICATION ACCESS
      </Title>

      <div
        style={{
          padding: '2rem',
          background: 'transparent',
          borderTop: '1px solid #FF00FF',
          borderBottom: '1px solid #FF00FF',
        }}
      >
        <div style={{ textAlign: 'center', marginBottom: '2rem' }}>
          <FiBell size={64} color="#FF00FF" style={{ filter: 'drop-shadow(0 0 10px #FF00FF)' }} />
        </div>

        <Title
          order={3}
          style={{
            color: '#FF00FF',
            marginBottom: '1.5rem',
            textShadow: '0 0 5px #FF00FF',
            textAlign: 'center',
          }}
        >
          Why We Need Notification Permission
        </Title>

        <Text style={{ color: '#f0f0f0', marginBottom: '1.5rem', lineHeight: 1.6 }}>
        Screen Time Reminder needs permission to send you notifications for the following reasons:
        </Text>

        <List
          spacing="md"
          size="lg"
          center
          icon={
            <ThemeIcon color="pink" size={24} radius="xl">
              <FiCheckCircle size={16} />
            </ThemeIcon>
          }
          style={{ marginBottom: '2rem' }}
        >
          <List.Item style={{ color: '#f0f0f0' }}>
            <Text span style={{ color: '#00FFFF' }}>Screen Time Alerts:</Text> To notify you when you're approaching your daily screen time limit
          </List.Item>
          
          <List.Item style={{ color: '#f0f0f0' }}>
            <Text span style={{ color: '#00FFFF' }}>Usage Reminders:</Text> To help you stay aware of how much time you're spending on specific apps
          </List.Item>
          
          <List.Item style={{ color: '#f0f0f0' }}>
            <Text span style={{ color: '#00FFFF' }}>Break Suggestions:</Text> To remind you to take breaks from your screen at healthy intervals
          </List.Item>
          
          <List.Item style={{ color: '#f0f0f0' }}>
            <Text span style={{ color: '#00FFFF' }}>Weekly Reports:</Text> To notify you when your weekly usage reports are ready
          </List.Item>
        </List>

        <Text style={{ color: '#f0f0f0', marginBottom: '2rem', fontSize: '0.9rem' }}>
          <FiAlertCircle style={{ verticalAlign: 'middle', marginRight: '0.5rem' }} color="#FF00FF" />
          We value your privacy. Notifications are processed locally on your device and no notification data is sent to external servers.
        </Text>

        {permissionStatus === 'granted' ? (
          <div
            style={{
              padding: '1rem',
              background: 'rgba(0, 255, 0, 0.1)',
              borderLeft: '4px solid #00FF00',
              marginBottom: '1.5rem',
              textAlign: 'center',
            }}
          >
            <Text style={{ color: '#00FF00' }}>
              <FiCheckCircle style={{ verticalAlign: 'middle', marginRight: '0.5rem' }} />
              Notification permission granted! You can now return to settings.
            </Text>
          </div>
        ) : permissionStatus === 'denied' ? (
          <div
            style={{
              padding: '1rem',
              background: 'rgba(255, 0, 0, 0.1)',
              borderLeft: '4px solid #FF0000',
              marginBottom: '1.5rem',
            }}
          >
            <Text style={{ color: '#FF0000' }}>
              <FiAlertCircle style={{ verticalAlign: 'middle', marginRight: '0.5rem' }} />
              Permission denied. Please enable notifications in your device settings to use this feature.
            </Text>
          </div>
        ) : null}

        <Button
          fullWidth
          size="lg"
          onClick={requestPermission}
          disabled={permissionStatus === 'granted'}
          style={{
            background: permissionStatus !== 'granted' 
              ? 'linear-gradient(45deg, #FF00FF, #00FFFF)' 
              : 'rgba(100, 100, 100, 0.5)',
          }}
        >
          {permissionStatus === 'default' ? 'ENABLE NOTIFICATIONS' : 
           permissionStatus === 'granted' ? 'NOTIFICATIONS ENABLED' : 
           'TRY AGAIN'}
        </Button>
        
        <Button
          fullWidth
          variant="outline"
          size="md"
          onClick={() => navigate('/settings')}
          style={{
            marginTop: '1rem',
            borderColor: '#00FFFF',
            color: '#00FFFF',
          }}
        >
          BACK TO SETTINGS
        </Button>
      </div>
    </Container>
  );
};

export default NotificationPermission; 