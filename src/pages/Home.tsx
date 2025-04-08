import { useState, useEffect } from 'react';
import { Container, Title, Text, Button, Divider, Box } from '@mantine/core';
import { useNavigate } from 'react-router-dom';
import { useScreenTime } from '../context/ScreenTimeContext';
import AppUsageTracker from '../services/AppUsageTracker';
import { FiBarChart2, FiSettings } from 'react-icons/fi';
import TipsSection from '../components/TipsSection';
//import { WidgetService } from '../services/WidgetService';

// Welcome messages array
const welcomeMessages = [
  "Welcome to Screen Time Reminder, your digital reality check",
  "Time flies in the digital realm. Track it with Screen Time Reminder",
  "Greetings, user. Ready to optimize your digital existence?",
  "Screen Time Reminder: Because even in cyberspace, time is finite",
  "Welcome back to Screen Time Reminder. Your digital footprint awaits"
];

const Home = () => {
  const navigate = useNavigate();
  const [welcomeMessage, setWelcomeMessage] = useState('');
  const [appVersion, setAppVersion] = useState('');
  const { usageAccessEnabled, screenTimeLimit, getTotalScreenTime } = useScreenTime();
  //const widgetService = WidgetService.getInstance();
  
  // Get the tracker service instance
  const trackerService = AppUsageTracker.getInstance();

  // Test notification function
  /* const testNotification = async () => {
    try {
      const { LocalNotifications } = await import('@capacitor/local-notifications');
      
      // Check if notifications are permitted
      const { display } = await LocalNotifications.checkPermissions();
      if (display !== 'granted') {
        console.log('Notifications permission not granted');
        return;
      }

      // Schedule a test notification
      await LocalNotifications.schedule({
        notifications: [{
          title: 'Test Notification',
          body: `Total Screen Time: ${Math.round(getTotalScreenTime())} minutes\nScreen Time Limit: ${screenTimeLimit} minutes\nNotification Frequency: ${notificationFrequency} minutes`,
          id: 999,
          channelId: 'screen-time-alerts',
          schedule: { at: new Date(Date.now() + 1000) },
          sound: 'beep.wav',
          smallIcon: 'ic_stat_screen_time',
          largeIcon: 'ic_launcher',
          autoCancel: true,
          attachments: undefined,
          actionTypeId: '',
          extra: null
        }]
      });
      
      console.log('Test notification scheduled successfully');
    } catch (error) {
      console.error('Error scheduling test notification:', error);
    }
  }; */

  // Test function to simulate reaching notification threshold
  /* 
  
    try {
      const { LocalNotifications } = await import('@capacitor/local-notifications');
      
      // Check if notifications are permitted
      const { display } = await LocalNotifications.checkPermissions();
      if (display !== 'granted') {
        console.log('Notifications permission not granted');
        return;
      }

      // Get current screen time
      const totalTime = getTotalScreenTime();
      
      console.log('Current screen time:', {
        totalTime,
        screenTimeLimit,
        notificationFrequency,
        approachingThreshold: screenTimeLimit - notificationFrequency,
        currentTime: new Date().toISOString()
      });

      // Cancel any existing notifications first
      await LocalNotifications.cancel({ notifications: [{ id: 1 }, { id: 2 }] });

      // If we're approaching the limit
      if (screenTimeLimit - totalTime <= notificationFrequency) {
        const minutesRemaining = Math.round(screenTimeLimit - totalTime);
        console.log(`Approaching limit - Total: ${totalTime}min, Limit: ${screenTimeLimit}min, Remaining: ${minutesRemaining}min`);
        
        await LocalNotifications.schedule({
          notifications: [{
            title: 'Screen Time Limit Approaching',
            body: `Total Screen Time: ${Math.round(totalTime)} minutes\nDaily Limit: ${screenTimeLimit} minutes\n${minutesRemaining} minutes remaining`,
            id: 1,
            channelId: 'screen-time-alerts',
            schedule: { at: new Date(Date.now() + 1000) },
            sound: 'beep.wav',
            smallIcon: 'ic_stat_screen_time',
            largeIcon: 'ic_launcher',
            autoCancel: true,
            attachments: undefined,
            actionTypeId: '',
            extra: null
          }]
        });
        console.log('Approaching limit notification scheduled successfully');
      }
      // If we're over the limit
      else if (totalTime > screenTimeLimit) {
        console.log(`Over limit - Total: ${totalTime}min, Limit: ${screenTimeLimit}min`);
        
        await LocalNotifications.schedule({
          notifications: [{
            title: 'Screen Time Limit Reached',
            body: `Total Screen Time: ${Math.round(totalTime)} minutes\nDaily Limit: ${screenTimeLimit} minutes\nYou have exceeded your daily limit!`,
            id: 2,
            channelId: 'screen-time-alerts',
            schedule: { at: new Date(Date.now() + 1000) },
            sound: 'beep.wav',
            smallIcon: 'ic_stat_screen_time',
            largeIcon: 'ic_launcher',
            autoCancel: true,
            attachments: undefined,
            actionTypeId: '',
            extra: null
          }]
        });
        console.log('Over limit notification scheduled successfully');
      } else {
        console.log(`No notification needed - Total: ${totalTime}min, Limit: ${screenTimeLimit}min, Next check at: ${screenTimeLimit - notificationFrequency}min`);
      }
    } catch (error) {
      console.error('Error scheduling test threshold notifications:', error);
    }
  };
 */
  useEffect(() => {
    // Select a random welcome message
    const randomIndex = Math.floor(Math.random() * welcomeMessages.length);
    setWelcomeMessage(welcomeMessages[randomIndex]);
    
    // Get app version from Capacitor
    const getAppVersion = async () => {
      try {
        const { App } = await import('@capacitor/app');
        const info = await App.getInfo();
        setAppVersion(`v${info.version} (${info.build})`);
      } catch (error) {
        console.error('Error getting app version:', error);
        setAppVersion('v1.0.0 (1)');
      }
    };
    
    getAppVersion();
    
    // Check usage access permission status
    const checkUsagePermission = async () => {
      try {
        await trackerService.hasUsagePermission();
      } catch (error) {
        console.error('Home: Error checking usage access permission:', error);
      }
    };
    
    checkUsagePermission();

    // Update widget data when screen time changes
    /* const updateWidget = async () => {
      const totalTime = getTotalScreenTime();
      console.log('Home: Updating widget with data:', { totalTime, screenTimeLimit });
      await widgetService.updateWidgetData(Math.round(totalTime), screenTimeLimit);
    };
    
    updateWidget();
    const widgetInterval = setInterval(updateWidget, 60000); // Update every minute
    
    return () => clearInterval(widgetInterval); */
  }, [getTotalScreenTime, screenTimeLimit, usageAccessEnabled]);

  return (
    <Container 
      className="home-container" 
      size="md" 
      py="xl"
      style={{
        background: '#000020', // Dark blue-black background
        minHeight: '100vh',
        padding: '1rem',
        display: 'flex',
        flexDirection: 'column'
      }}
    >
      <Box 
        style={{
          textAlign: 'center',
          padding: '2rem',
          background: 'transparent',
          flex: 1
        }}
      >
        <Title
          order={1}
          style={{
            fontSize: '2.5rem',
            marginBottom: '2rem',
            color: '#00FFFF',
            textShadow: '0 0 10px #FF00FF, 0 0 20px #FF00FF',
            fontFamily: 'Orbitron, sans-serif',
            animation: 'glow 3s infinite',
          }}
        >
          Screen Time Reminder
        </Title>
        
        <Text
          size="xl"
          style={{
            marginBottom: '2rem',
            color: '#FF00FF',
            textShadow: '0 0 5px #FF00FF',
          }}
        >
          {welcomeMessage}
        </Text>

        {/* Test Buttons */}
        {/* <Box mb="xl">
          <Group align="center" gap="md">
            <Button
              size="md"
              onClick={testNotification}
              style={{
                background: 'linear-gradient(45deg, #FF00FF, #00FFFF)',
                color: '#000',
                fontWeight: 'bold',
              }}
            >
              <FiBell style={{ marginRight: '0.5rem', verticalAlign: 'middle' }} />
              Test Notification
            </Button>
            <Button
              size="md"
              onClick={testNotificationThreshold}
              style={{
                background: 'linear-gradient(45deg, #00FFFF, #FF00FF)',
                color: '#000',
                fontWeight: 'bold',
              }}
            >
              <FiBell style={{ marginRight: '0.5rem', verticalAlign: 'middle' }} />
              Test Threshold
            </Button>
          </Group>
        </Box> */}

        {/* Usage Access Permission Section */}
        <Box mt="xl" style={{ marginTop: '2rem' }}>
          <Divider 
            my="md" 
            label={
              <Text size="sm" style={{ color: '#FF00FF' }}>
                <FiBarChart2 style={{ verticalAlign: 'middle', marginRight: '0.5rem' }} />
                Enable Usage Access
              </Text>
            } 
            labelPosition="center"
            style={{ borderColor: '#FF00FF' }}
          />
          
          <Text size="sm" style={{ color: '#f0f0f0', marginBottom: '1rem' }}>
            To track your screen time and provide accurate statistics, 
            the app needs permission to access usage data.
          </Text>
          
          <Button
            size="lg"
            onClick={() => navigate('/settings')}
            style={{
              background: 'linear-gradient(45deg, #00FFFF, #FF00FF)',
              color: '#000',
              fontWeight: 'bold',
            }}
            fullWidth
          >
            <FiSettings style={{ marginRight: '0.5rem', verticalAlign: 'middle' }} />
            Enable Usage Access in Settings
          </Button>
        </Box>

        {/* Tips Section */}
        <TipsSection />
      </Box>

      {/* Version Display */}
      <Box
        style={{
          textAlign: 'center',
          padding: '2rem',
        }}
      >
        <Text
          size="sm"
          style={{
            color: '#AAAAAA',
            fontFamily: 'monospace',
          }}
        >
          {appVersion}
        </Text>
      </Box>
    </Container>
  );
};

export default Home; 