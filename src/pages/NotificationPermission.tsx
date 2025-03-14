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
    const checkPermission = async () => {
      try {
        // For mobile devices, use Capacitor
        if (window.Capacitor && window.Capacitor.isNativePlatform()) {
          const { LocalNotifications } = await import('@capacitor/local-notifications');
          const permResult = await LocalNotifications.checkPermissions();
          console.log('Initial permission check (Capacitor):', permResult.display);
          setPermissionStatus(permResult.display as 'default' | 'granted' | 'denied');
        } 
        // Fallback for browser testing
        else if ('Notification' in window) {
          console.log('Initial permission check (Browser):', Notification.permission);
          setPermissionStatus(Notification.permission as 'default' | 'granted' | 'denied');
        }
      } catch (error) {
        console.error('Error checking initial permission:', error);
      }
    };

    checkPermission();
    
    // Auto-trigger permission request after a short delay
    const timer = setTimeout(() => {
      requestPermission();
    }, 1000); // Increased delay to ensure UI is fully loaded
    
    return () => clearTimeout(timer);
  }, []);

  const requestPermission = async () => {
    console.log('Requesting notification permission...');
    try {
      // For mobile devices, use Capacitor
      if (window.Capacitor && window.Capacitor.isNativePlatform()) {
        console.log('Using Capacitor LocalNotifications API');
        const { LocalNotifications } = await import('@capacitor/local-notifications');
        
        try {
          // Request permission
          console.log('Calling LocalNotifications.requestPermissions()');
          const { display } = await LocalNotifications.requestPermissions();
          console.log('Permission result from Capacitor:', display);
          
          if (display === 'granted') {
            setPermissionStatus('granted');
            console.log('Permission granted, scheduling test notification');
            
            // Show a test notification with a slight delay
            setTimeout(async () => {
              try {
                await LocalNotifications.schedule({
                  notifications: [
                    {
                      title: 'Screen Time Reminder',
                      body: 'Notifications are now enabled!',
                      id: 1,
                      schedule: { at: new Date(Date.now() + 1000) }
                    }
                  ]
                });
                console.log('Test notification scheduled successfully');
              } catch (notifError) {
                console.error('Error scheduling test notification:', notifError);
              }
            }, 1500);
          } else {
            console.log('Permission denied or not determined:', display);
            setPermissionStatus(display as 'default' | 'granted' | 'denied');
          }
        } catch (permError) {
          console.error('Error requesting notification permissions:', permError);
          // Check permission status after error
          try {
            const currentStatus = await LocalNotifications.checkPermissions();
            console.log('Permission status after error:', currentStatus.display);
            setPermissionStatus(currentStatus.display as 'default' | 'granted' | 'denied');
          } catch (checkError) {
            console.error('Error checking permissions after error:', checkError);
          }
        }
      } 
      // Fallback for browser testing
      else if ('Notification' in window) {
        console.log('Using Web Notification API (for testing only)');
        console.log('Current permission status:', Notification.permission);
        
        if (Notification.permission !== 'granted') {
          console.log('Calling Notification.requestPermission()');
          const permission = await Notification.requestPermission();
          console.log('New permission status:', permission);
          setPermissionStatus(permission);

          if (permission === 'granted') {
            console.log('Permission granted, showing test notification');
            new Notification('Screen Time Reminder', {
              body: 'Notifications are now enabled!',
              icon: '/notification-icon.png'
            });
          }
        } else {
          console.log('Permission already granted');
          setPermissionStatus('granted');
        }
      } else {
        console.log('No notification API available, simulating granted permission');
        setPermissionStatus('granted');
      }
    } catch (error) {
      console.error('Critical error in requestPermission:', error);
      // Try to recover by checking current status
      try {
        if (window.Capacitor && window.Capacitor.isNativePlatform()) {
          const { LocalNotifications } = await import('@capacitor/local-notifications');
          const currentStatus = await LocalNotifications.checkPermissions();
          console.log('Permission status after critical error:', currentStatus.display);
          setPermissionStatus(currentStatus.display as 'default' | 'granted' | 'denied');
        }
      } catch (recoveryError) {
        console.error('Error during recovery attempt:', recoveryError);
        // Last resort - assume default
        setPermissionStatus('default');
      }
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