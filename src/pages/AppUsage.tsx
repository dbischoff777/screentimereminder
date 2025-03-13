import { Container, Title, Text, Group, RingProgress, Stack, Button } from '@mantine/core';
import { useScreenTime } from '../context/ScreenTimeContext';
import { useNavigate } from 'react-router-dom';
import { useState, useEffect } from 'react';

const AppUsage = () => {
  const navigate = useNavigate();
  const { 
    appUsageData, 
    screenTimeLimit,
    getTotalScreenTime
  } = useScreenTime();
  
  const [totalScreenTime, setTotalScreenTime] = useState(0);
  const [percentOfLimit, setPercentOfLimit] = useState(0);
  
  useEffect(() => {
    // Calculate total screen time
    const total = getTotalScreenTime();
    setTotalScreenTime(total);
    
    // Calculate percentage of limit
    const percent = (total / screenTimeLimit) * 100;
    setPercentOfLimit(Math.min(percent, 100)); // Cap at 100%
  }, [appUsageData, screenTimeLimit, getTotalScreenTime]);
  
  // Sort apps by usage time (descending)
  const sortedApps = [...appUsageData].sort((a, b) => b.time - a.time);
  
  // Format time (minutes) to hours and minutes
  const formatTime = (minutes: number) => {
    const hours = Math.floor(minutes / 60);
    const mins = Math.round(minutes % 60);
    
    if (hours === 0) {
      return `${mins} min`;
    } else if (mins === 0) {
      return `${hours} hr`;
    } else {
      return `${hours} hr ${mins} min`;
    }
  };
  
  // Calculate percentage of total for each app
  const calculatePercentage = (appTime: number) => {
    if (totalScreenTime === 0) return 0;
    return (appTime / totalScreenTime) * 100;
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
        APP USAGE STATISTICS
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
        <Group style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: '1.5rem' }}>
          <div>
            <Title order={3} style={{ color: '#FF00FF', marginBottom: '0.5rem', textShadow: '0 0 5px #FF00FF' }}>
              Total Screen Time
            </Title>
            <Text size="xl" style={{ color: '#00FFFF' }}>
              {formatTime(totalScreenTime)}
            </Text>
          </div>
          
          <RingProgress
            size={120}
            thickness={12}
            roundCaps
            sections={[
              { value: percentOfLimit, color: percentOfLimit > 90 ? '#FF3333' : '#00FFFF' },
            ]}
            label={
              <Text size="lg" style={{ color: '#FF00FF', textAlign: 'center' }}>
                {Math.round(percentOfLimit)}%
              </Text>
            }
          />
        </Group>
        
        <Text style={{ color: '#00FFFF', marginBottom: '1rem' }}>
          {percentOfLimit >= 100 
            ? 'You have reached your daily screen time limit!' 
            : `${formatTime(screenTimeLimit - totalScreenTime)} remaining of your ${formatTime(screenTimeLimit)} limit`}
        </Text>
      </div>
      
      <div
        style={{
          padding: '2rem',
          background: 'transparent',
          borderTop: '1px solid #FF00FF',
          borderBottom: '1px solid #FF00FF',
          marginBottom: '2rem',
        }}
      >
        <Title order={3} style={{ color: '#FF00FF', marginBottom: '1.5rem', textShadow: '0 0 5px #FF00FF' }}>
          App Breakdown
        </Title>
        
        {sortedApps.length === 0 ? (
          <Text style={{ color: '#00FFFF', textAlign: 'center', marginBottom: '1rem' }}>
            No app usage data recorded yet. Start tracking apps in Settings.
          </Text>
        ) : (
          <Stack style={{ gap: '16px' }}>
            {sortedApps.map((app, index) => (
              <div
                key={index}
                style={{
                  padding: '1rem',
                  background: 'rgba(0, 0, 40, 0.3)',
                  borderLeft: `4px solid ${app.color}`,
                }}
              >
                <Group style={{ display: 'flex', justifyContent: 'space-between' }}>
                  <div>
                    <Text style={{ color: '#FFFFFF', fontWeight: 700 }}>
                      {app.name}
                    </Text>
                    <Text size="sm" style={{ color: '#AAAAAA' }}>
                      {app.lastUsed 
                        ? `Last used: ${new Date(app.lastUsed).toLocaleDateString()}`
                        : 'No usage data'}
                    </Text>
                  </div>
                  <div style={{ textAlign: 'right' }}>
                    <Text style={{ color: app.color, fontWeight: 700 }}>
                      {formatTime(app.time)}
                    </Text>
                    <Text size="sm" style={{ color: '#AAAAAA' }}>
                      {Math.round(calculatePercentage(app.time))}% of total
                    </Text>
                  </div>
                </Group>
                
                <div 
                  style={{ 
                    height: '6px', 
                    background: '#333333', 
                    marginTop: '0.5rem',
                    borderRadius: '3px',
                    overflow: 'hidden'
                  }}
                >
                  <div 
                    style={{ 
                      height: '100%', 
                      width: `${calculatePercentage(app.time)}%`, 
                      background: app.color 
                    }} 
                  />
                </div>
              </div>
            ))}
          </Stack>
        )}
      </div>
      
      <Button
        fullWidth
        size="lg"
        onClick={() => navigate('/settings')}
        style={{
          background: 'linear-gradient(45deg, #FF00FF, #00FFFF)',
        }}
      >
        BACK TO SETTINGS
      </Button>
    </Container>
  );
};

export default AppUsage; 