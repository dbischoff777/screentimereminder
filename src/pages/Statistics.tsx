import { Container, Title, Text, Grid, RingProgress, Badge } from '@mantine/core';
import { BarChart, Bar, XAxis, YAxis, CartesianGrid, Tooltip, ResponsiveContainer } from 'recharts';
import { useScreenTime } from '../context/ScreenTimeContext';
import { useState, useEffect } from 'react';
import { useLocation } from 'react-router-dom';

const Statistics = () => {
  const { 
    appUsageData, 
    getTotalScreenTime, 
    screenTimeLimit, 
    updateAppUsageData 
  } = useScreenTime();
  const [totalScreenTime, setTotalScreenTime] = useState(0);
  const [percentOfLimit, setPercentOfLimit] = useState(0);
  const location = useLocation();
  
  // Check if we're coming from the settings page after a reset
  useEffect(() => {
    console.log('Location state changed:', location.state);
    
    const fromSettings = location.state?.fromReset === true;
    if (fromSettings) {
      console.log('Detected reset from Settings page, timestamp:', location.state?.timestamp);
      
      // Force refresh data
      refreshData();
    }
  }, [location.state]);
  
  useEffect(() => {
    // Calculate total screen time
    const total = getTotalScreenTime();
    setTotalScreenTime(total);
    
    // Calculate percentage of limit
    const percent = (total / screenTimeLimit) * 100;
    setPercentOfLimit(Math.min(percent, 100)); // Cap at 100%
    
    console.log('Statistics: Updated UI with latest app usage data');
  }, [appUsageData, screenTimeLimit, getTotalScreenTime]);

  useEffect(() => {
    refreshData();
  }, []);
  
  const refreshData = async () => {
    console.log('Refreshing data...');
    
    try {
      // Use the context's updateAppUsageData function to get fresh data
      const success = await updateAppUsageData();
      
      if (success) {
        console.log('Successfully updated app usage data');
        
        // Show success notification briefly
        setTimeout(() => {
          // No need to reset showResetNotification as it's no longer used
        }, 3000);
      } else {
        console.log('Failed to update app usage data');
        
        // Force re-fetch data from context anyway
        const total = getTotalScreenTime();
        console.log('Total screen time:', total);
        setTotalScreenTime(total);
        
        // Calculate percentage of limit
        const percent = (total / screenTimeLimit) * 100;
        setPercentOfLimit(Math.min(percent, 100));
      }
    } catch (error) {
      console.error('Error refreshing data:', error);
    }
  };

  // Sort apps by usage time (descending) and filter out apps with less than 5% usage
  const sortedApps = [...appUsageData]
    .filter(app => {
      const percentage = (app.time / getTotalScreenTime()) * 100;
      return percentage >= 5; // Only show apps with 5% or more usage
    })
    .sort((a, b) => b.time - a.time);
  
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

  // Get status color based on percentage of limit
  const getStatusColor = (percent: number) => {
    if (percent >= 90) return '#FF3333'; // Red
    if (percent >= 75) return '#FFA500'; // Orange
    if (percent >= 50) return '#FFFF00'; // Yellow
    return '#00FFFF'; // Cyan (default)
  };

  // Get status text based on percentage of limit
  const getStatusText = (percent: number) => {
    if (percent >= 100) return 'Limit Reached';
    if (percent >= 90) return 'Critical';
    if (percent >= 75) return 'Warning';
    if (percent >= 50) return 'Moderate';
    return 'Good';
  };

  // Add permission check on component mount
  useEffect(() => {
    const checkPermission = async () => {
      try {
        const AppUsageTrackerService = (await import('../services/AppUsageTracker')).default;
        const tracker = AppUsageTrackerService.getInstance();
        const permissionStatus = await tracker.hasUsagePermission();
        console.log('Statistics: Permission status =', permissionStatus);
      } catch (error) {
        console.error('Error checking permission:', error);
      }
    };
    
    checkPermission();
  }, []);

  return (
    <Container 
      size="md" 
      py="xl" 
      style={{
        background: '#000020', // Dark blue-black background
        minHeight: '100vh',
        padding: '1rem',
        position: 'relative',
        overflowX: 'hidden'
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
        SCREEN TIME ANALYTICS
      </Title>

      {/* Daily Limit Progress Section */}
      <div
        style={{
          padding: '1.5rem',
          background: 'transparent',
          borderTop: '1px solid #FF00FF',
          borderBottom: '1px solid #FF00FF',
          marginBottom: '1.5rem',
          marginTop: '40px' // Add margin to avoid overlap with permission indicator
        }}
      >
        <Grid>
          <Grid.Col span={6}>
            <Title
              order={3}
              style={{
                color: '#FF00FF',
                marginBottom: '1rem',
                textShadow: '0 0 5px #FF00FF',
              }}
            >
              Daily Limit Progress
            </Title>
            
            <div style={{ display: 'flex', alignItems: 'center', marginBottom: '0.5rem' }}>
              <Text size="xl" style={{ color: '#00FFFF', marginRight: '10px' }}>
                {formatTime(totalScreenTime)} used
              </Text>
              <Badge 
                style={{ 
                  backgroundColor: getStatusColor(percentOfLimit),
                  color: '#000000',
                  fontWeight: 'bold'
                }}
              >
                {getStatusText(percentOfLimit)}
              </Badge>
            </div>
            
            <Text style={{ color: '#00FFFF' }}>
              {percentOfLimit >= 100 
                ? 'You have reached your daily screen time limit!' 
                : `${formatTime(screenTimeLimit - totalScreenTime)} remaining of your ${formatTime(screenTimeLimit)} limit`}
            </Text>

            <Text size="sm" style={{ color: '#AAAAAA', marginTop: '1rem', fontStyle: 'italic' }}>
              Screen time is automatically tracked while you use your device
            </Text>
          </Grid.Col>
          
          <Grid.Col span={6} style={{ display: 'flex', justifyContent: 'center', alignItems: 'center' }}>
            <RingProgress
              size={150}
              thickness={12}
              roundCaps
              sections={[
                { value: percentOfLimit, color: getStatusColor(percentOfLimit) },
              ]}
              label={
                <Text size="lg" style={{ color: '#FF00FF', textAlign: 'center' }}>
                  {Math.round(percentOfLimit)}%
                </Text>
              }
            />
          </Grid.Col>
        </Grid>
      </div>

      {/* App Usage Breakdown Section */}
      <div
        style={{
          padding: '1.5rem',
          background: 'transparent',
          borderTop: '1px solid #FF00FF',
          borderBottom: '1px solid #FF00FF',
          marginBottom: '1.5rem',
        }}
      >
        <Title
          order={3}
          style={{
            color: '#FF00FF',
            marginBottom: '1rem',
            textShadow: '0 0 5px #FF00FF',
          }}
        >
          App Usage Breakdown
        </Title>
        
        {appUsageData.length === 0 || totalScreenTime === 0 ? (
          <Text style={{ color: '#00FFFF', textAlign: 'center', padding: '2rem' }}>
            No app usage data recorded yet. Usage data will appear here as you use your device.
          </Text>
        ) : (
          <div style={{ height: 300 }}>
            <ResponsiveContainer width="100%" height="100%">
              <BarChart
                data={sortedApps}
                margin={{ top: 20, right: 30, left: 20, bottom: 5 }}
              >
                <CartesianGrid strokeDasharray="3 3" stroke="#333" />
                <XAxis dataKey="name" stroke="#00FFFF" hide={true} />
                <YAxis stroke="#00FFFF" label={{ value: 'Minutes', angle: -90, position: 'insideLeft', fill: '#00FFFF' }} />
                <Tooltip 
                  contentStyle={{ 
                    backgroundColor: 'rgba(0, 0, 32, 0.9)', 
                    border: '1px solid #FF00FF',
                    color: '#00FFFF'
                  }}
                  formatter={(value: number, _: any, props: any) => {
                    return [`${formatTime(value)}`, props.payload.name];
                  }}
                />
                <Bar dataKey="time" fill="#FF00FF" />
              </BarChart>
            </ResponsiveContainer>
          </div>
        )}
      </div>

      {/* App Distribution Section */}
      <div
        style={{
          padding: '1.5rem',
          background: 'transparent',
          borderTop: '1px solid #FF00FF',
          borderBottom: '1px solid #FF00FF',
          marginBottom: '1.5rem',
        }}
      >
        <Grid>
          <Grid.Col span={6}>
            <Title
              order={3}
              style={{
                color: '#FF00FF',
                marginBottom: '1rem',
                textShadow: '0 0 5px #FF00FF',
              }}
            >
              App Distribution
            </Title>

            {totalScreenTime === 0 ? (
              <Text style={{ color: '#00FFFF', textAlign: 'center', padding: '2rem' }}>
                No usage data available yet
              </Text>
            ) : (
              <div style={{ marginTop: '1rem' }}>
                {sortedApps.map((app, index) => (
                  <div key={index} style={{ display: 'flex', alignItems: 'center', marginBottom: '0.75rem' }}>
                    <div
                      style={{
                        width: '12px',
                        height: '12px',
                        backgroundColor: app.color,
                        marginRight: '8px',
                        borderRadius: '2px',
                      }}
                    />
                    <Text style={{ color: '#f0f0f0', flex: 1 }}>
                      {app.name}
                    </Text>
                    <Text style={{ color: app.color, fontWeight: 700, marginLeft: '8px' }}>
                      {formatTime(app.time)}
                    </Text>
                    <Text size="sm" style={{ color: '#AAAAAA', width: '50px', textAlign: 'right' }}>
                      {Math.round((app.time / totalScreenTime) * 100)}%
                    </Text>
                  </div>
                ))}
              </div>
            )}
          </Grid.Col>
          
          <Grid.Col span={6} style={{ display: 'flex', justifyContent: 'center', alignItems: 'center' }}>
            {totalScreenTime > 0 && (
              <RingProgress
                size={150}
                thickness={12}
                roundCaps
                sections={sortedApps.map(app => ({
                  value: (app.time / totalScreenTime) * 100,
                  color: app.color,
                }))}
                label={
                  <Text size="lg" style={{ color: '#FF00FF', textAlign: 'center' }}>
                    {formatTime(totalScreenTime)}
                  </Text>
                }
              />
            )}
          </Grid.Col>
        </Grid>
      </div>

      {/* Detailed App Usage Section */}
      <div
        style={{
          padding: '1.5rem',
          background: 'transparent',
          borderTop: '1px solid #FF00FF',
          borderBottom: '1px solid #FF00FF',
          marginBottom: '1.5rem',
        }}
      >
        <Title
          order={3}
          style={{
            color: '#FF00FF',
            marginBottom: '1rem',
            textShadow: '0 0 5px #FF00FF',
          }}
        >
          Detailed App Usage
        </Title>
        
        {sortedApps.length === 0 || totalScreenTime === 0 ? (
          <Text style={{ color: '#00FFFF', textAlign: 'center', padding: '2rem' }}>
            No app usage data recorded yet. Data will appear as you use your device.
          </Text>
        ) : (
          <div style={{ maxHeight: '300px', overflowY: 'auto' }}>
            {sortedApps.map((app, index) => (
              <div
                key={index}
                style={{
                  padding: '1rem',
                  background: 'rgba(0, 0, 40, 0.3)',
                  borderLeft: `4px solid ${app.color}`,
                  marginBottom: '0.75rem',
                }}
              >
                <div style={{ display: 'flex', justifyContent: 'space-between' }}>
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
                      {Math.round((app.time / totalScreenTime) * 100)}% of total
                    </Text>
                  </div>
                </div>
                
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
                      width: `${(app.time / totalScreenTime) * 100}%`, 
                      background: app.color 
                    }} 
                  />
                </div>
              </div>
            ))}
          </div>
        )}
      </div>
    </Container>
  );
};

export default Statistics; 