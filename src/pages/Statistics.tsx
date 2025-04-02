import { Container, Title, Text, Grid, RingProgress, Badge, Tabs, Paper } from '@mantine/core';
import { BarChart, Bar, XAxis, YAxis, CartesianGrid, Tooltip, ResponsiveContainer } from 'recharts';
import { useScreenTime } from '../context/ScreenTimeContext';
import { useState, useEffect } from 'react';
import { useLocation } from 'react-router-dom';
import BackgroundService from '../services/BackgroundService';

const Statistics = () => {
  const { 
    appUsageData, 
    getTotalScreenTime, 
    screenTimeLimit, 
    updateAppUsageData 
  } = useScreenTime();
  const [totalScreenTime, setTotalScreenTime] = useState(0);
  const [percentOfLimit, setPercentOfLimit] = useState(0);
  const [activeTab, setActiveTab] = useState('timeline');
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
    // Initialize background service and set up tracking
    const backgroundService = BackgroundService.getInstance();
    backgroundService.initialize();
    
    // Set up the tracking callback
    backgroundService.setTrackingCallback(() => {
      console.log('Background tracking update received');
      refreshData();
    });

    // Initial data refresh
    refreshData();

    // Cleanup on unmount
    return () => {
      backgroundService.setTrackingCallback(null);
      backgroundService.disableBackgroundMode();
    };
  }, []);
  
  const refreshData = async () => {
    console.log('Refreshing data...');
    
    try {
      // Use the context's updateAppUsageData function to get fresh data
      const success = await updateAppUsageData();
      
      if (success) {
        console.log('Successfully updated app usage data');
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

  // Filter and sort current day's data
  const getCurrentDayData = () => {
    const now = new Date();
    const startOfDay = new Date(now.getFullYear(), now.getMonth(), now.getDate());
    const endOfDay = new Date(now.getFullYear(), now.getMonth(), now.getDate(), 23, 59, 59, 999);

    return [...appUsageData]
      .filter(app => {
        const appTime = app.lastUsed ? new Date(app.lastUsed) : null;
        return appTime && appTime >= startOfDay && appTime <= endOfDay;
      })
      .sort((a, b) => {
        // Sort by lastUsed timestamp in descending order (newest first)
        const timeA = a.lastUsed ? new Date(a.lastUsed).getTime() : 0;
        const timeB = b.lastUsed ? new Date(b.lastUsed).getTime() : 0;
        return timeB - timeA;
      });
  };

  const sortedTimelineData = getCurrentDayData();

  // Format time for display (e.g., "2 minutes 24 seconds")
  const formatDetailedTime = (minutes: number) => {
    if (minutes < 1/60) { // Less than 1 minute
      const seconds = Math.round(minutes * 60);
      return `${seconds} second${seconds !== 1 ? 's' : ''}`;
    } else if (minutes < 1) { // Less than 1 minute but more than 1 second
      const seconds = Math.round(minutes * 60);
      return `${seconds} second${seconds !== 1 ? 's' : ''}`;
    } else if (minutes < 60) { // Less than 1 hour
      const mins = Math.floor(minutes);
      const secs = Math.round((minutes - mins) * 60);
      return secs > 0 ? `${mins} minute${mins !== 1 ? 's' : ''} ${secs} second${secs !== 1 ? 's' : ''}` : `${mins} minute${mins !== 1 ? 's' : ''}`;
    } else { // 1 hour or more
      const hours = Math.floor(minutes / 60);
      const mins = Math.round(minutes % 60);
      return mins > 0 ? `${hours} hour${hours !== 1 ? 's' : ''} ${mins} minute${mins !== 1 ? 's' : ''}` : `${hours} hour${hours !== 1 ? 's' : ''}`;
    }
  };

  // Format time for timeline (HH:mm)
  const formatTimelineTime = (timestamp: Date | string | null | undefined) => {
    if (!timestamp) return '--:--';
    const date = typeof timestamp === 'string' ? new Date(timestamp) : timestamp;
    return date.toLocaleTimeString('en-US', {
      hour: '2-digit',
      minute: '2-digit',
      hour12: false
    });
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

  // Generate heatmap data with proper intensity values
  const generateHeatmapData = () => {
    const hourlyData = Array(24).fill(0);
    const now = new Date();
    const startOfDay = new Date(now.getFullYear(), now.getMonth(), now.getDate());
    const endOfDay = new Date(now.getFullYear(), now.getMonth(), now.getDate(), 23, 59, 59, 999);

    // Get today's data
    const todayData = appUsageData.filter(app => {
      const usageTime = app.lastUsed ? new Date(app.lastUsed) : null;
      return usageTime && usageTime >= startOfDay && usageTime <= endOfDay;
    });

    // Aggregate usage by hour
    todayData.forEach(app => {
      if (!app.lastUsed) return;
      const usageTime = new Date(app.lastUsed);
      const hour = usageTime.getHours();
      hourlyData[hour] += app.time || 0;
    });

    return hourlyData;
  };

  // Get the maximum usage for scaling
  const getMaxUsage = (data: number[]) => {
    return Math.max(...data, 0.1); // Avoid division by zero
  };

  // Format hour for display
  const formatHour = (hour: number) => {
    return `${hour.toString().padStart(2, '0')}:00`;
  };

  const heatmapData = generateHeatmapData();
  const maxUsage = getMaxUsage(heatmapData);

  // Get color based on intensity
  const getHeatmapColor = (value: number, maxValue: number) => {
    if (value === 0) return 'rgba(240, 240, 255, 0.05)';
    
    const ratio = value / maxValue;
    
    // Define gradient colors
    const colors = {
      low: { r: 200, g: 200, b: 255 },
      medium: { r: 100, g: 255, b: 100 },
      high: { r: 255, g: 50, b: 50 }
    };

    let startColor, endColor, localRatio;

    if (ratio < 0.3) {
      // Cool blue range
      startColor = { r: 240, g: 240, b: 255 };
      endColor = colors.low;
      localRatio = ratio / 0.3;
    } else if (ratio < 0.6) {
      // Blue to green range
      startColor = colors.low;
      endColor = colors.medium;
      localRatio = (ratio - 0.3) / 0.3;
    } else {
      // Green to red range
      startColor = colors.medium;
      endColor = colors.high;
      localRatio = (ratio - 0.6) / 0.4;
    }

    // Interpolate between colors
    const r = Math.round(startColor.r + (endColor.r - startColor.r) * localRatio);
    const g = Math.round(startColor.g + (endColor.g - startColor.g) * localRatio);
    const b = Math.round(startColor.b + (endColor.b - startColor.b) * localRatio);

    return `rgba(${r}, ${g}, ${b}, ${0.3 + ratio * 0.7})`;
  };

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
          marginBottom: '1rem',
          color: '#00FFFF',
          textShadow: '0 0 10px #00FFFF',
          textAlign: 'center',
        }}
      >
        SCREEN TIME ANALYTICS
      </Title>

      <Tabs
        value={activeTab}
        onChange={(value) => setActiveTab(value as string)}
        style={{ marginBottom: '2rem' }}
        styles={{
          root: {
            borderBottom: '1px solid #FF00FF',
            maxWidth: '100%',
            overflow: 'hidden'
          },
          tab: {
            color: '#00FFFF',
            fontSize: '1rem',
            padding: '1rem 2rem',
            '&[data-active]': {
              color: '#FF00FF',
              borderColor: '#FF00FF',
            },
          },
          panel: {
            color: '#00FFFF',
            padding: '1rem 0',
          },
        }}
      >
        <Tabs.List grow>
          <Tabs.Tab value="heatmap">HEATMAP VIEW</Tabs.Tab>
          <Tabs.Tab value="timeline">TIMELINE VIEW</Tabs.Tab>
          <Tabs.Tab value="detailed">DETAILED VIEW</Tabs.Tab>
        </Tabs.List>

        <Tabs.Panel value="timeline">
          <Title
            order={2}
            style={{
              fontSize: '1.5rem',
              marginBottom: '0.5rem',
              color: '#00FFFF',
            }}
          >
            Usage history
          </Title>
          <Text size="sm" style={{ color: '#AAAAAA', marginBottom: '2rem' }}>
            Daily
          </Text>

          <div style={{ maxHeight: '600px', overflowY: 'auto', padding: '0 1rem' }}>
            {sortedTimelineData.length === 0 ? (
              <Text style={{ color: '#00FFFF', textAlign: 'center', padding: '2rem' }}>
                No activity history available for today.
              </Text>
            ) : (
              <div style={{ position: 'relative' }}>
                {/* Date header */}
                <div style={{ 
                  display: 'flex', 
                  alignItems: 'center',
                  marginBottom: '1.5rem',
                  marginLeft: '80px'
                }}>
                  <Text style={{ color: '#FFFFFF', fontWeight: 500 }}>
                    {new Date().toLocaleDateString('en-US', { 
                      year: 'numeric',
                      month: 'long',
                      day: 'numeric'
                    })}
                  </Text>
                </div>

                {/* Timeline entries */}
                <div style={{ position: 'relative' }}>
                  {/* Continuous timeline line */}
                  <div
                    style={{
                      position: 'absolute',
                      left: '80px',
                      top: 0,
                      bottom: 0,
                      width: '2px',
                      background: '#FF4444',
                    }}
                  />
                  
                  {sortedTimelineData.map((app, index) => (
                    <div
                      key={index}
                      style={{
                        display: 'flex',
                        alignItems: 'center',
                        marginBottom: '1.5rem',
                        position: 'relative',
                      }}
                    >
                      {/* Time */}
                      <Text style={{ 
                        width: '60px', 
                        color: '#333333',
                        fontSize: '0.9rem',
                        marginRight: '20px',
                        textAlign: 'right',
                        fontWeight: 500
                      }}>
                        {formatTimelineTime(app.lastUsed)}
                      </Text>
                      
                      {/* App icon container */}
                      <div style={{
                        position: 'relative',
                        zIndex: 2,
                        background: '#FFFFFF',
                        borderRadius: '50%',
                        padding: '2px',
                        marginRight: '16px'
                      }}>
                        <div style={{
                          width: '32px',
                          height: '32px',
                          borderRadius: '50%',
                          background: app.color,
                          display: 'flex',
                          alignItems: 'center',
                          justifyContent: 'center',
                          fontSize: '16px',
                          color: '#FFFFFF'
                        }}>
                          {app.name.charAt(0)}
                        </div>
                      </div>
                      
                      {/* App info */}
                      <div style={{ flex: 1 }}>
                        <Text style={{ 
                          color: '#FFFFFF', 
                          fontWeight: 500,
                          marginBottom: '4px'
                        }}>
                          {app.name}
                        </Text>
                        <Text size="sm" style={{ color: '#AAAAAA' }}>
                          {formatDetailedTime(app.time)}
                        </Text>
                      </div>
                    </div>
                  ))}
                </div>
              </div>
            )}
          </div>
        </Tabs.Panel>

        <Tabs.Panel value="heatmap">
          <div style={{ 
            maxWidth: '100%',
            margin: '0 auto',
            padding: '0 1rem'
          }}>
            <Title
              order={2}
              style={{
                fontSize: '1.5rem',
                marginBottom: '0.5rem',
                color: '#00FFFF',
              }}
            >
              Usage Heatmap
            </Title>
            <Text size="sm" style={{ color: '#AAAAAA', marginBottom: '1rem' }}>
              Daily activity pattern
            </Text>

            {/* Total usage circle */}
            <div style={{
              display: 'flex',
              justifyContent: 'center',
              marginBottom: '2rem'
            }}>
              <div style={{
                width: '120px',
                height: '120px',
                borderRadius: '50%',
                background: '#6B46C1',
                display: 'flex',
                flexDirection: 'column',
                alignItems: 'center',
                justifyContent: 'center',
                color: '#FFFFFF',
              }}>
                <Text size="xl" fw={700}>
                  {Math.floor(totalScreenTime / 60)}h
                </Text>
                <Text size="sm">
                  {Math.round(totalScreenTime % 60)}m
                </Text>
              </div>
            </div>

            <div style={{ 
              width: '100%',
              overflowX: 'auto',
              overflowY: 'hidden',
              padding: '1rem 0'
            }}>
              {heatmapData.every(value => value === 0) ? (
                <Text style={{ color: '#00FFFF', textAlign: 'center', padding: '2rem' }}>
                  No activity data available for today.
                </Text>
              ) : (
                <>
                  <div style={{ 
                    display: 'grid',
                    gridTemplateColumns: 'repeat(24, minmax(60px, 1fr))',
                    gap: '4px',
                    marginBottom: '2rem',
                    background: 'rgba(0, 0, 20, 0.3)',
                    padding: '16px',
                    borderRadius: '8px',
                    minWidth: 'fit-content'
                  }}>
                    {heatmapData.map((value, hour) => {
                      const bgColor = getHeatmapColor(value, maxUsage);
                      return (
                        <div
                          key={hour}
                          style={{
                            aspectRatio: '1',
                            backgroundColor: bgColor,
                            padding: '8px',
                            display: 'flex',
                            flexDirection: 'column',
                            alignItems: 'center',
                            justifyContent: 'center',
                            cursor: 'pointer',
                            transition: 'all 0.2s ease',
                            position: 'relative',
                            boxShadow: value > 0 ? '0 0 15px rgba(255, 255, 255, 0.1)' : 'none',
                            minWidth: '60px',
                            borderRadius: '4px',
                            transform: 'scale(1)'
                          }}
                          onMouseEnter={(e) => {
                            e.currentTarget.style.transform = 'scale(1.05)';
                          }}
                          onMouseLeave={(e) => {
                            e.currentTarget.style.transform = 'scale(1)';
                          }}
                        >
                          <Text 
                            size="sm" 
                            style={{ 
                              color: '#FFFFFF',
                              fontWeight: 500,
                              marginBottom: '2px',
                              textShadow: value > maxUsage / 2 ? '0 0 4px rgba(0,0,0,0.5)' : 'none'
                            }}
                          >
                            {formatHour(hour)}
                          </Text>
                          <Text 
                            size="xs" 
                            style={{ 
                              color: '#FFFFFF',
                              opacity: 0.8,
                              textShadow: value > maxUsage / 2 ? '0 0 4px rgba(0,0,0,0.5)' : 'none'
                            }}
                          >
                            {value > 0 ? `${Math.round(value)}m` : '-'}
                          </Text>
                        </div>
                      );
                    })}
                  </div>

                  {/* Legend */}
                  <div style={{
                    display: 'flex',
                    alignItems: 'center',
                    justifyContent: 'center',
                    gap: '24px',
                    marginBottom: '2rem',
                    flexWrap: 'wrap'
                  }}>
                    <div style={{ 
                      display: 'flex', 
                      alignItems: 'center',
                      gap: '8px' 
                    }}>
                      <div style={{
                        width: '16px',
                        height: '16px',
                        backgroundColor: getHeatmapColor(maxUsage * 0.15, maxUsage),
                        borderRadius: '2px'
                      }} />
                      <Text size="sm" style={{ color: '#AAAAAA' }}>
                        Low ({`< ${Math.round(maxUsage / 3)}m`})
                      </Text>
                    </div>
                    <div style={{ 
                      display: 'flex', 
                      alignItems: 'center',
                      gap: '8px'
                    }}>
                      <div style={{
                        width: '16px',
                        height: '16px',
                        backgroundColor: getHeatmapColor(maxUsage * 0.45, maxUsage),
                        borderRadius: '2px'
                      }} />
                      <Text size="sm" style={{ color: '#AAAAAA' }}>
                        Medium ({`${Math.round(maxUsage / 3)}m - ${Math.round(maxUsage * 2 / 3)}m`})
                      </Text>
                    </div>
                    <div style={{ 
                      display: 'flex', 
                      alignItems: 'center',
                      gap: '8px'
                    }}>
                      <div style={{
                        width: '16px',
                        height: '16px',
                        backgroundColor: getHeatmapColor(maxUsage * 0.8, maxUsage),
                        borderRadius: '2px'
                      }} />
                      <Text size="sm" style={{ color: '#AAAAAA' }}>
                        High ({`> ${Math.round(maxUsage * 2 / 3)}m`})
                      </Text>
                    </div>
                  </div>

                  {/* Summary */}
                  <Paper
                    style={{
                      background: 'rgba(0, 0, 40, 0.3)',
                      padding: '1.5rem',
                      marginBottom: '1rem',
                      borderRadius: '8px'
                    }}
                  >
                    <Text style={{ color: '#FFFFFF', marginBottom: '0.5rem', fontSize: '1.1rem' }}>
                      Peak Usage Time: {formatHour(heatmapData.indexOf(maxUsage))}
                    </Text>
                    <Text style={{ color: '#AAAAAA' }}>
                      Most active hour with {Math.round(maxUsage)} minutes of screen time
                    </Text>
                  </Paper>
                </>
              )}
            </div>
          </div>
        </Tabs.Panel>

        <Tabs.Panel value="detailed">
          <Text style={{ color: '#00FFFF', textAlign: 'center', padding: '2rem' }}>
            Detailed view coming soon
          </Text>
        </Tabs.Panel>
      </Tabs>

      {/* Daily Limit Progress Section */}
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