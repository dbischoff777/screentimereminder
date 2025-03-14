import { useState, useEffect } from 'react';
import { Container, Title, Text, Button, Box, Group } from '@mantine/core';
import { useNavigate } from 'react-router-dom';
import { useScreenTime } from '../context/ScreenTimeContext';
import AppUsageTracker from '../services/AppUsageTracker';

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
  const [hasUsagePermission, setHasUsagePermission] = useState<boolean | null>(null);
  const { usageAccessEnabled } = useScreenTime();
  
  // Get the tracker service instance
  const trackerService = AppUsageTracker.getInstance();

  useEffect(() => {
    // Select a random welcome message
    const randomIndex = Math.floor(Math.random() * welcomeMessages.length);
    setWelcomeMessage(welcomeMessages[randomIndex]);
    
    // Check usage access permission status
    const checkUsagePermission = async () => {
      try {
        const hasPermission = await trackerService.hasUsagePermission();
        console.log('Home: Usage access permission status:', hasPermission);
        setHasUsagePermission(hasPermission);
      } catch (error) {
        console.error('Home: Error checking usage access permission:', error);
        setHasUsagePermission(false);
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
        padding: '1rem'
      }}
    >
      <Box 
        style={{
          textAlign: 'center',
          padding: '2rem',
          background: 'transparent',
          borderTop: '1px solid #FF00FF',
          borderBottom: '1px solid #FF00FF',
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

        <Group style={{ justifyContent: 'center', gap: '16px' }}>
          <Button
            size="lg"
            onClick={() => navigate('/statistics')}
            style={{
              marginTop: '1rem',
              background: 'linear-gradient(45deg, #FF00FF, #00FFFF)',
            }}
          >
            View Your Stats
          </Button>
          
          <Button
            size="lg"
            onClick={() => navigate('/settings')}
            variant="outline"
            style={{
              marginTop: '1rem',
              borderColor: '#00FFFF',
              color: '#00FFFF',
            }}
          >
            Settings
          </Button>
        </Group>
      </Box>
    </Container>
  );
};

export default Home; 