import { useState, useEffect } from 'react';
import { Container, Title, Text, Button, Divider, Box } from '@mantine/core';
import { useNavigate } from 'react-router-dom';
import { useScreenTime } from '../context/ScreenTimeContext';
import AppUsageTracker from '../services/AppUsageTracker';
import { FiBarChart2, FiSettings } from 'react-icons/fi';

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
  const { usageAccessEnabled } = useScreenTime();
  
  // Get the tracker service instance
  const trackerService = AppUsageTracker.getInstance();

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
  }, [usageAccessEnabled]);

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
      </Box>

      {/* Version Display */}
      <Box
        style={{
          textAlign: 'center',
          padding: '1rem',
          marginTop: '2rem',
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