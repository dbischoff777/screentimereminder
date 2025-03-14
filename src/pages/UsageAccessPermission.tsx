import { useState, useEffect } from 'react';
import { Container, Title, Text, Button, List, ThemeIcon, Loader } from '@mantine/core';
import { useNavigate } from 'react-router-dom';
import { FiAlertCircle, FiCheckCircle, FiBarChart2 } from 'react-icons/fi';
import AppUsageTracker from '../services/AppUsageTracker';

// Add type declaration for Capacitor on window object
declare global {
  interface Window {
    Capacitor?: {
      isNativePlatform: () => boolean;
    };
  }
}

const UsageAccessPermission = () => {
  const navigate = useNavigate();
  const [permissionStatus, setPermissionStatus] = useState<boolean | null>(null);
  const [isLoading, setIsLoading] = useState(false);
  const [isRequesting, setIsRequesting] = useState(false);
  const [checkCount, setCheckCount] = useState(0);
  
  // Get the tracker service instance
  const trackerService = AppUsageTracker.getInstance();

  // Check permission status on component mount and after permission request
  useEffect(() => {
    checkPermission();
    
    // Set up an interval to check permission status periodically
    // This helps detect when the user returns from the system settings
    const intervalId = setInterval(() => {
      if (isRequesting) {
        checkPermission();
        setCheckCount(prev => prev + 1);
        
        // Stop checking after 10 attempts (about 50 seconds)
        if (checkCount > 10) {
          setIsRequesting(false);
          clearInterval(intervalId);
        }
      }
    }, 5000); // Check every 5 seconds
    
    return () => clearInterval(intervalId);
  }, [isRequesting, checkCount]);

  // Auto-trigger permission request after a short delay
  useEffect(() => {
    if (permissionStatus === false) {
      const timer = setTimeout(() => {
        requestPermission();
      }, 1000);
      return () => clearTimeout(timer);
    }
  }, [permissionStatus]);

  const checkPermission = async () => {
    try {
      setIsLoading(true);
      const hasPermission = await trackerService.hasUsagePermission();
      console.log('Usage access permission status:', hasPermission);
      setPermissionStatus(hasPermission);
    } catch (error) {
      console.error('Error checking usage access permission:', error);
      setPermissionStatus(false);
    } finally {
      setIsLoading(false);
    }
  };

  const requestPermission = async () => {
    try {
      setIsLoading(true);
      setIsRequesting(true);
      
      console.log('Requesting usage access permission...');
      await trackerService.requestUsagePermission();
      
      // We don't set permission status here because the user needs to manually grant it
      // in the system settings. We'll check the status periodically in the useEffect.
      
      console.log('Usage access permission request sent');
    } catch (error) {
      console.error('Error requesting usage access permission:', error);
      setIsRequesting(false);
    } finally {
      setIsLoading(false);
    }
  };

  const testPermission = async () => {
    try {
      setIsLoading(true);
      
      // First check if we have permission
      const hasPermission = await trackerService.hasUsagePermission();
      if (!hasPermission) {
        console.log('No permission to test app usage data');
        setPermissionStatus(false);
        setIsLoading(false);
        return;
      }
      
      // Try to get app usage data for the last hour
      const now = Date.now();
      const oneHourAgo = now - (60 * 60 * 1000);
      const data = await trackerService.getAppUsageData(oneHourAgo, now);
      
      console.log('Test app usage data:', data);
      
      // If we got data, permission is working
      setPermissionStatus(true);
      
      // Navigate to statistics page to show the data
      navigate('/statistics');
    } catch (error) {
      console.error('Error testing app usage data:', error);
    } finally {
      setIsLoading(false);
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
        USAGE ACCESS PERMISSION
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
          <FiBarChart2 size={64} color="#FF00FF" style={{ filter: 'drop-shadow(0 0 10px #FF00FF)' }} />
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
          Why We Need Usage Access Permission
        </Title>

        <Text style={{ color: '#f0f0f0', marginBottom: '1.5rem', lineHeight: 1.6 }}>
          Screen Time Reminder needs permission to access usage statistics for the following reasons:
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
            <Text span style={{ color: '#00FFFF' }}>Track Screen Time:</Text> To monitor how much time you spend on different apps
          </List.Item>
          
          <List.Item style={{ color: '#f0f0f0' }}>
            <Text span style={{ color: '#00FFFF' }}>App Usage Statistics:</Text> To provide detailed analytics about your app usage patterns
          </List.Item>
          
          <List.Item style={{ color: '#f0f0f0' }}>
            <Text span style={{ color: '#00FFFF' }}>Screen Time Limits:</Text> To enforce your daily screen time limits for specific apps
          </List.Item>
          
          <List.Item style={{ color: '#f0f0f0' }}>
            <Text span style={{ color: '#00FFFF' }}>Usage Reports:</Text> To generate accurate weekly and monthly usage reports
          </List.Item>
        </List>

        <Text style={{ color: '#f0f0f0', marginBottom: '2rem', fontSize: '0.9rem' }}>
          <FiAlertCircle style={{ verticalAlign: 'middle', marginRight: '0.5rem' }} color="#FF00FF" />
          We value your privacy. Usage data is processed locally on your device and no personal data is sent to external servers.
        </Text>

        {isLoading && (
          <div style={{ textAlign: 'center', margin: '1rem 0' }}>
            <Loader color="#00FFFF" size="md" />
            <Text style={{ color: '#00FFFF', marginTop: '0.5rem' }}>
              {isRequesting ? 'Waiting for permission...' : 'Checking permission status...'}
            </Text>
          </div>
        )}

        {permissionStatus === true && !isLoading && (
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
              Usage access permission granted! You can now view your app usage statistics.
            </Text>
          </div>
        )}

        {permissionStatus === false && !isLoading && (
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
              Permission not granted. Please enable usage access in your device settings to use this feature.
            </Text>
            
            <Text style={{ color: '#f0f0f0', marginTop: '0.5rem', fontSize: '0.9rem' }}>
              When the settings page opens, find "Screen Time Reminder" in the list and toggle it ON.
              Then return to this app.
            </Text>
          </div>
        )}

        <Button
          fullWidth
          size="lg"
          onClick={requestPermission}
          disabled={permissionStatus === true || isLoading || isRequesting}
          style={{
            background: permissionStatus !== true && !isLoading && !isRequesting
              ? 'linear-gradient(45deg, #FF00FF, #00FFFF)' 
              : 'rgba(100, 100, 100, 0.5)',
          }}
        >
          {permissionStatus === null ? 'ENABLE USAGE ACCESS' : 
           permissionStatus === true ? 'USAGE ACCESS ENABLED' : 
           isRequesting ? 'OPENING SETTINGS...' : 'ENABLE USAGE ACCESS'}
        </Button>
        
        {permissionStatus === true && (
          <Button
            fullWidth
            size="lg"
            onClick={testPermission}
            disabled={isLoading}
            style={{
              marginTop: '1rem',
              background: 'linear-gradient(45deg, #00FFFF, #00FF00)',
            }}
          >
            VIEW APP STATISTICS
          </Button>
        )}
        
        <Button
          fullWidth
          variant="outline"
          size="md"
          onClick={() => navigate('/')}
          style={{
            marginTop: '1rem',
            borderColor: '#00FFFF',
            color: '#00FFFF',
          }}
        >
          BACK TO HOME
        </Button>
      </div>
    </Container>
  );
};

export default UsageAccessPermission; 