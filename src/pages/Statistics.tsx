import { Container, Title, Text, Grid, RingProgress, Badge, Notification, Loader } from '@mantine/core';
import { BarChart, Bar, XAxis, YAxis, CartesianGrid, Tooltip, ResponsiveContainer } from 'recharts';
import { useScreenTime } from '../context/ScreenTimeContext';
import { useState, useEffect, useRef } from 'react';
import { useLocation } from 'react-router-dom';
import { FiCheckCircle, FiRefreshCw } from 'react-icons/fi';

const Statistics = () => {
  const { appUsageData, getTotalScreenTime, screenTimeLimit, getLastHourUsage } = useScreenTime();
  const [totalScreenTime, setTotalScreenTime] = useState(0);
  const [percentOfLimit, setPercentOfLimit] = useState(0);
  const [showResetNotification, setShowResetNotification] = useState(false);
  const [isPulling, setIsPulling] = useState(false);
  const [pullDistance, setPullDistance] = useState(0);
  const [isRefreshing, setIsRefreshing] = useState(false);
  const containerRef = useRef<HTMLDivElement>(null);
  const startY = useRef<number | null>(null);
  const location = useLocation();
  
  // Check if we're coming from the settings page after a reset
  useEffect(() => {
    console.log('Location state changed:', location.state);
    
    const fromSettings = location.state?.fromReset === true;
    if (fromSettings) {
      console.log('Detected reset from Settings page, timestamp:', location.state?.timestamp);
      
      // Force refresh data
      refreshData();
      
      // Show reset notification
      setShowResetNotification(true);
      
      // Hide notification after 3 seconds
      const timer = setTimeout(() => {
        setShowResetNotification(false);
      }, 3000);
      
      return () => clearTimeout(timer);
    }
  }, [location.state]);
  
  useEffect(() => {
    // Calculate total screen time
    const total = getTotalScreenTime();
    setTotalScreenTime(total);
    
    // Calculate percentage of limit
    const percent = (total / screenTimeLimit) * 100;
    setPercentOfLimit(Math.min(percent, 100)); // Cap at 100%
  }, [appUsageData, screenTimeLimit, getTotalScreenTime]);

  // Setup pull-to-refresh
  useEffect(() => {
    const container = containerRef.current;
    if (!container) return;
    
    const handleTouchStart = (e: TouchEvent) => {
      if (window.scrollY === 0) {
        startY.current = e.touches[0].clientY;
      }
    };
    
    const handleTouchMove = (e: TouchEvent) => {
      if (startY.current !== null) {
        const currentY = e.touches[0].clientY;
        const distance = currentY - startY.current;
        
        if (distance > 0) {
          // Prevent default scrolling behavior when pulling down
          e.preventDefault();
          
          // Apply resistance to make it harder to pull
          const pullWithResistance = Math.min(distance / 2.5, 100);
          setPullDistance(pullWithResistance);
          setIsPulling(true);
        }
      }
    };
    
    const handleTouchEnd = () => {
      if (pullDistance > 70) {
        // Refresh data
        refreshData();
      }
      
      // Reset pull state
      startY.current = null;
      setPullDistance(0);
      setIsPulling(false);
    };
    
    container.addEventListener('touchstart', handleTouchStart, { passive: false });
    container.addEventListener('touchmove', handleTouchMove, { passive: false });
    container.addEventListener('touchend', handleTouchEnd);
    
    return () => {
      container.removeEventListener('touchstart', handleTouchStart);
      container.removeEventListener('touchmove', handleTouchMove);
      container.removeEventListener('touchend', handleTouchEnd);
    };
  }, [pullDistance]);
  
  const refreshData = async () => {
    console.log('Refreshing data...');
    
    // Show loading state
    setIsRefreshing(true);
    
    try {
      // Get the AppUsageTracker service
      const AppUsageTrackerService = (await import('../services/AppUsageTracker')).default;
      const tracker = AppUsageTrackerService.getInstance();
      
      // Check if we have permission to access usage data
      const hasPermission = await tracker.hasUsagePermission();
      console.log('Permission to access usage data:', hasPermission);
      
      if (hasPermission) {
        // Fetch the latest app usage data from Android
        console.log('Fetching latest app usage data from Android...');
        
        // Get last hour usage data directly from the native layer
        const lastHourData = await getLastHourUsage();
        console.log('Last hour usage data:', lastHourData);
        
        if (lastHourData && lastHourData.length > 0) {
          // Update app usage data in context
          // This will update the main UI as well
          const updatedData = [...appUsageData];
          
          lastHourData.forEach(app => {
            const existingAppIndex = updatedData.findIndex(a => a.name === app.name);
            
            if (existingAppIndex >= 0) {
              // Update existing app
              updatedData[existingAppIndex] = {
                ...updatedData[existingAppIndex],
                time: app.time, // Replace with the most accurate data
                lastUsed: app.lastUsed,
                category: app.category,
                color: app.color
              };
            } else {
              // Add new app
              updatedData.push(app);
            }
          });
          
          // Update localStorage with the new data
          localStorage.setItem('appUsageData', JSON.stringify(updatedData));
          
          // Force re-fetch data from context
          const total = getTotalScreenTime();
          console.log('Total screen time:', total);
          setTotalScreenTime(total);
          
          // Calculate percentage of limit
          const percent = (total / screenTimeLimit) * 100;
          setPercentOfLimit(Math.min(percent, 100));
          
          console.log('App usage data updated successfully with real-time data');
        } else {
          console.log('No app usage data found for the last hour');
          
          // Force re-fetch data from context
          const total = getTotalScreenTime();
          console.log('Total screen time:', total);
          setTotalScreenTime(total);
          
          // Calculate percentage of limit
          const percent = (total / screenTimeLimit) * 100;
          setPercentOfLimit(Math.min(percent, 100));
        }
      } else {
        console.log('No permission to access usage data, using cached data');
        
        // Force reload data from localStorage
        const savedData = localStorage.getItem('appUsageData');
        console.log('Data from localStorage:', savedData ? 'Found' : 'Not found');
        
        // Force re-fetch data from context
        const total = getTotalScreenTime();
        console.log('Total screen time:', total);
        setTotalScreenTime(total);
        
        // Calculate percentage of limit
        const percent = (total / screenTimeLimit) * 100;
        setPercentOfLimit(Math.min(percent, 100));
      }
      
      // Hide loading state after a short delay to show the refresh animation
      setTimeout(() => {
        setIsRefreshing(false);
        console.log('Statistics data refreshed successfully');
      }, 500);
    } catch (error) {
      console.error('Error refreshing data:', error);
      setIsRefreshing(false);
    }
  };

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
      ref={containerRef}
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
      {/* Pull to refresh indicator */}
      {isPulling && (
        <div 
          style={{
            position: 'absolute',
            top: 0,
            left: 0,
            right: 0,
            height: `${pullDistance}px`,
            display: 'flex',
            alignItems: 'center',
            justifyContent: 'center',
            backgroundColor: 'rgba(0, 255, 255, 0.1)',
            borderBottom: '1px solid #00FFFF',
            transition: isPulling ? 'none' : 'height 0.3s ease',
            overflow: 'hidden',
            zIndex: 10
          }}
        >
          <div style={{ 
            display: 'flex', 
            alignItems: 'center', 
            gap: '8px',
            color: '#00FFFF',
            transform: `rotate(${pullDistance * 3}deg)`
          }}>
            <FiRefreshCw size={20} />
            <Text>{pullDistance > 70 ? 'Release to refresh' : 'Pull down to refresh'}</Text>
          </div>
        </div>
      )}
      
      {/* Loading indicator */}
      {isRefreshing && (
        <div 
          style={{
            position: 'fixed',
            top: 0,
            left: 0,
            right: 0,
            bottom: 0,
            display: 'flex',
            alignItems: 'center',
            justifyContent: 'center',
            backgroundColor: 'rgba(0, 0, 32, 0.7)',
            zIndex: 100
          }}
        >
          <div style={{
            display: 'flex',
            flexDirection: 'column',
            alignItems: 'center',
            gap: '10px'
          }}>
            <Loader color="#00FFFF" size="lg" variant="bars" />
            <Text style={{ color: '#00FFFF' }}>Refreshing data...</Text>
          </div>
        </div>
      )}
      
      {showResetNotification && (
        <Notification
          icon={<FiCheckCircle size={20} />}
          color="teal"
          title="Success!"
          onClose={() => setShowResetNotification(false)}
          style={{ 
            marginBottom: '1rem',
            backgroundColor: 'rgba(0, 255, 128, 0.2)',
            borderColor: '#00FF80',
            color: '#00FFFF'
          }}
        >
          Your app usage data has been successfully reset.
        </Notification>
      )}
      
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
                data={appUsageData}
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
                {appUsageData.map((app, index) => (
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
                sections={appUsageData.map(app => ({
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