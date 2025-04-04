import { Container, Title, Text, Grid, Tabs, Paper, Card } from '@mantine/core';
import { useScreenTime } from '../context/ScreenTimeContext';
import { useState, useEffect } from 'react';

const DetailedAnalytics = () => {
  const { 
    appUsageData, 
    getTotalScreenTime, 
    screenTimeLimit
  } = useScreenTime();
  const [totalScreenTime, setTotalScreenTime] = useState(0);
  const [activeTab, setActiveTab] = useState<string | null>('heatmap');

  useEffect(() => {
    // Calculate total screen time
    const total = getTotalScreenTime();
    setTotalScreenTime(total);
  }, [appUsageData, getTotalScreenTime]);

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
        const timeA = a.lastUsed ? new Date(a.lastUsed).getTime() : 0;
        const timeB = b.lastUsed ? new Date(b.lastUsed).getTime() : 0;
        return timeB - timeA;
      });
  };

  const sortedTimelineData = getCurrentDayData();

  // Format time for display
  const formatDetailedTime = (minutes: number) => {
    if (minutes < 1/60) {
      const seconds = Math.round(minutes * 60);
      return `${seconds} second${seconds !== 1 ? 's' : ''}`;
    } else if (minutes < 1) {
      const seconds = Math.round(minutes * 60);
      return `${seconds} second${seconds !== 1 ? 's' : ''}`;
    } else if (minutes < 60) {
      const mins = Math.floor(minutes);
      const secs = Math.round((minutes - mins) * 60);
      return secs > 0 ? `${mins} minute${mins !== 1 ? 's' : ''} ${secs} second${secs !== 1 ? 's' : ''}` : `${mins} minute${mins !== 1 ? 's' : ''}`;
    } else {
      const hours = Math.floor(minutes / 60);
      const mins = Math.round(minutes % 60);
      return mins > 0 ? `${hours} hour${hours !== 1 ? 's' : ''} ${mins} minute${mins !== 1 ? 's' : ''}` : `${hours} hour${hours !== 1 ? 's' : ''}`;
    }
  };

  // Format time for timeline
  const formatTimelineTime = (timestamp: Date | string | null | undefined) => {
    if (!timestamp) return '--:--';
    const date = typeof timestamp === 'string' ? new Date(timestamp) : timestamp;
    return date.toLocaleTimeString('en-US', {
      hour: '2-digit',
      minute: '2-digit',
      hour12: false
    });
  };

  // Generate heatmap data
  const generateHeatmapData = () => {
    const hourlyData = Array(24).fill(0);
    const now = new Date();
    const startOfDay = new Date(now.getFullYear(), now.getMonth(), now.getDate());
    const endOfDay = new Date(now.getFullYear(), now.getMonth(), now.getDate(), 23, 59, 59, 999);

    const todayData = appUsageData.filter(app => {
      const usageTime = app.lastUsed ? new Date(app.lastUsed) : null;
      return usageTime && usageTime >= startOfDay && usageTime <= endOfDay;
    });

    todayData.forEach(app => {
      if (!app.lastUsed) return;
      const usageTime = new Date(app.lastUsed);
      const hour = usageTime.getHours();
      hourlyData[hour] += app.time || 0;
    });

    return hourlyData;
  };

  const getMaxUsage = (data: number[]) => {
    return Math.max(...data, 0.1);
  };

  const formatHour = (hour: number) => {
    return `${hour.toString().padStart(2, '0')}:00`;
  };

  const heatmapData = generateHeatmapData();
  const maxUsage = getMaxUsage(heatmapData);

  // Get color based on intensity with glow effect
  const getHeatmapColor = (value: number, maxValue: number) => {
    if (value === 0) return 'transparent';
    
    const ratio = value / maxValue;
    let color;
    
    if (ratio < 0.3) {
      color = 'rgba(255, 255, 255, 0.4)';
    } else if (ratio < 0.6) {
      color = 'rgba(100, 255, 100, 0.6)';
    } else {
      color = 'rgba(255, 50, 50, 0.8)';
    }

    return color;
  };

  return (
    <Container 
      size="md" 
      py="xl" 
      style={{
        background: '#000020',
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
        DETAILED ANALYTICS
      </Title>

      <Tabs
        value={activeTab}
        onChange={(value) => setActiveTab(value === activeTab ? null : value as string)}
        style={{ marginBottom: '2rem' }}
        styles={{
          root: {
            borderBottom: '1px solid #FF00FF',
            maxWidth: '100%',
            overflow: 'hidden'
          },
          list: {
            display: 'flex',
            flexDirection: 'row',
            width: '100%',
            gap: '2px',
            backgroundColor: 'rgba(0, 0, 32, 0.3)',
            padding: '2px',
            borderRadius: '8px'
          },
          tab: {
            flex: '1',
            color: '#00FFFF',
            fontSize: '0.9rem',
            padding: '0.75rem 0.5rem',
            textAlign: 'center',
            backgroundColor: 'rgba(0, 0, 32, 0.5)',
            transition: 'all 0.3s ease',
            '&:first-of-type': {
              borderTopLeftRadius: '6px',
              borderBottomLeftRadius: '6px',
            },
            '&:last-of-type': {
              borderTopRightRadius: '6px',
              borderBottomRightRadius: '6px',
            },
            '&[data-active]': {
              color: '#FF00FF',
              backgroundColor: 'rgba(255, 0, 255, 0.1)',
              borderColor: '#FF00FF',
            },
            '&:hover': {
              backgroundColor: 'rgba(255, 0, 255, 0.05)',
            }
          },
          panel: {
            color: '#00FFFF',
            padding: '1rem 0',
            animation: 'fadeIn 0.3s ease',
          },
        }}
      >
        <Tabs.List>
          <Tabs.Tab value="heatmap">HEATMAP</Tabs.Tab>
          <Tabs.Tab value="timeline">TIMELINE</Tabs.Tab>
          <Tabs.Tab value="insights">INSIGHTS</Tabs.Tab>
          <Tabs.Tab value="details">DETAILS</Tabs.Tab>
        </Tabs.List>

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
                    position: 'relative',
                    width: '100%',
                    height: '600px',
                    background: 'rgba(0, 0, 20, 0.3)',
                    padding: '16px',
                    borderRadius: '8px',
                    marginBottom: '2rem',
                    display: 'flex',
                    flexDirection: 'column'
                  }}>
                    {/* Time labels */}
                    {Array.from({ length: 12 }).map((_, i) => (
                      <div
                        key={i}
                        style={{
                          position: 'absolute',
                          left: 0,
                          top: `${(i * 100) / 12}%`,
                          color: '#AAAAAA',
                          fontSize: '0.8rem',
                          transform: 'translateY(-50%)',
                          padding: '0 8px'
                        }}
                      >
                        {`${(i * 2).toString().padStart(2, '0')}:00`}
                      </div>
                    ))}

                    {/* Horizontal grid lines */}
                    {Array.from({ length: 12 }).map((_, i) => (
                      <div
                        key={i}
                        style={{
                          position: 'absolute',
                          left: '40px',
                          right: 0,
                          top: `${(i * 100) / 12}%`,
                          height: '1px',
                          background: 'rgba(255, 255, 255, 0.1)'
                        }}
                      />
                    ))}

                    {/* Heatmap points */}
                    <div style={{
                      position: 'absolute',
                      left: '40px',
                      right: 0,
                      top: 0,
                      bottom: 0,
                      padding: '16px'
                    }}>
                      {heatmapData.map((value, hour) => {
                        if (value === 0) return null;
                        const yPosition = (hour * 100) / 24;
                        const intensity = value / maxUsage;
                        const size = 20 + (intensity * 30);
                        const color = getHeatmapColor(value, maxUsage);
                        
                        return (
                          <div
                            key={hour}
                            style={{
                              position: 'absolute',
                              left: '50%',
                              top: `${yPosition}%`,
                              width: `${size}px`,
                              height: `${size}px`,
                              transform: 'translate(-50%, -50%)',
                              background: color,
                              borderRadius: '50%',
                              filter: `blur(${10 + (intensity * 10)}px)`,
                              opacity: 0.8,
                            }}
                          />
                        );
                      })}
                    </div>

                    {/* Current time indicator */}
                    <div style={{
                      position: 'absolute',
                      left: '40px',
                      right: 0,
                      top: `${(new Date().getHours() * 100) / 24}%`,
                      width: '100%',
                      height: '2px',
                      background: 'rgba(255, 255, 255, 0.5)',
                      zIndex: 2
                    }} />
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
                        background: 'rgba(255, 255, 255, 0.4)',
                        borderRadius: '50%',
                        filter: 'blur(4px)'
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
                        background: 'rgba(100, 255, 100, 0.6)',
                        borderRadius: '50%',
                        filter: 'blur(4px)'
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
                        background: 'rgba(255, 50, 50, 0.8)',
                        borderRadius: '50%',
                        filter: 'blur(4px)'
                      }} />
                      <Text size="sm" style={{ color: '#AAAAAA' }}>
                        High ({`> ${Math.round(maxUsage * 2 / 3)}m`})
                      </Text>
                    </div>
                  </div>
                </>
              )}
            </div>
          </div>
        </Tabs.Panel>

        <Tabs.Panel value="timeline">
          <Title
            order={2}
            style={{
              fontSize: '1.5rem',
              marginBottom: '0.5rem',
              color: '#00FFFF',
            }}
          >
            Usage Timeline
          </Title>
          <Text size="sm" style={{ color: '#AAAAAA', marginBottom: '2rem' }}>
            Daily Activity Log
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
                        color: '#AAAAAA',
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
                        {app.icon ? (
                          <img
                            src={`data:image/png;base64,${app.icon}`}
                            alt={app.name}
                            style={{
                              width: '32px',
                              height: '32px',
                              borderRadius: '50%',
                              objectFit: 'cover'
                            }}
                          />
                        ) : (
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
                        )}
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

        <Tabs.Panel value="insights">
          {/* Usage Insights Dashboard */}
          <div style={{
            padding: '1.5rem',
            background: 'transparent',
            borderTop: '1px solid #FF00FF',
            borderBottom: '1px solid #FF00FF',
            marginBottom: '1.5rem',
          }}>
            <Title
              order={3}
              style={{
                color: '#FF00FF',
                marginBottom: '1.5rem',
                textShadow: '0 0 5px #FF00FF',
              }}
            >
              Usage Insights
            </Title>

            <Grid>
              {/* Most Used App */}
              <Grid.Col span={6}>
                <Paper
                  style={{
                    background: 'rgba(0, 0, 32, 0.3)',
                    padding: '1rem',
                    borderRadius: '8px',
                    height: '100%'
                  }}
                >
                  <Text size="lg" fw={700} style={{ color: '#00FFFF', marginBottom: '0.5rem' }}>
                    Most Used App
                  </Text>
                  {sortedTimelineData[0] ? (
                    <>
                      <div style={{ display: 'flex', alignItems: 'center', gap: '1rem', marginBottom: '0.5rem' }}>
                        {sortedTimelineData[0].icon ? (
                          <img
                            src={`data:image/png;base64,${sortedTimelineData[0].icon}`}
                            alt={sortedTimelineData[0].name}
                            style={{
                              width: '32px',
                              height: '32px',
                              borderRadius: '8px'
                            }}
                          />
                        ) : (
                          <div style={{
                            width: '32px',
                            height: '32px',
                            borderRadius: '8px',
                            backgroundColor: sortedTimelineData[0].color,
                            display: 'flex',
                            alignItems: 'center',
                            justifyContent: 'center',
                            color: '#FFFFFF'
                          }}>
                            {sortedTimelineData[0].name.charAt(0)}
                          </div>
                        )}
                        <Text style={{ color: '#FFFFFF' }}>{sortedTimelineData[0].name}</Text>
                      </div>
                      <Text size="sm" style={{ color: '#AAAAAA' }}>
                        {formatDetailedTime(sortedTimelineData[0].time)} ({Math.round((sortedTimelineData[0].time / totalScreenTime) * 100)}% of total)
                      </Text>
                    </>
                  ) : (
                    <Text style={{ color: '#AAAAAA' }}>No data available</Text>
                  )}
                </Paper>
              </Grid.Col>

              {/* Peak Usage Time */}
              <Grid.Col span={6}>
                <Paper
                  style={{
                    background: 'rgba(0, 0, 32, 0.3)',
                    padding: '1rem',
                    borderRadius: '8px',
                    height: '100%'
                  }}
                >
                  <Text size="lg" fw={700} style={{ color: '#00FFFF', marginBottom: '0.5rem' }}>
                    Peak Usage Time
                  </Text>
                  {heatmapData.some(value => value > 0) ? (
                    <>
                      <Text style={{ color: '#FFFFFF', marginBottom: '0.5rem' }}>
                        {formatHour(heatmapData.indexOf(Math.max(...heatmapData)))}
                      </Text>
                      <Text size="sm" style={{ color: '#AAAAAA' }}>
                        {Math.round(Math.max(...heatmapData))} minutes of activity
                      </Text>
                    </>
                  ) : (
                    <Text style={{ color: '#AAAAAA' }}>No data available</Text>
                  )}
                </Paper>
              </Grid.Col>

              {/* Usage Pattern */}
              <Grid.Col span={6}>
                <Paper
                  style={{
                    background: 'rgba(0, 0, 32, 0.3)',
                    padding: '1rem',
                    borderRadius: '8px',
                    height: '100%'
                  }}
                >
                  <Text size="lg" fw={700} style={{ color: '#00FFFF', marginBottom: '0.5rem' }}>
                    Usage Pattern
                  </Text>
                  {totalScreenTime > 0 ? (
                    <>
                      <Text style={{ color: '#FFFFFF', marginBottom: '0.5rem' }}>
                        {totalScreenTime > screenTimeLimit ? 'Heavy' :
                         totalScreenTime > screenTimeLimit * 0.75 ? 'Moderate' : 'Light'} Usage
                      </Text>
                      <Text size="sm" style={{ color: '#AAAAAA' }}>
                        {Math.round((totalScreenTime / screenTimeLimit) * 100)}% of daily limit
                      </Text>
                    </>
                  ) : (
                    <Text style={{ color: '#AAAAAA' }}>No data available</Text>
                  )}
                </Paper>
              </Grid.Col>

              {/* App Diversity */}
              <Grid.Col span={6}>
                <Paper
                  style={{
                    background: 'rgba(0, 0, 32, 0.3)',
                    padding: '1rem',
                    borderRadius: '8px',
                    height: '100%'
                  }}
                >
                  <Text size="lg" fw={700} style={{ color: '#00FFFF', marginBottom: '0.5rem' }}>
                    App Diversity
                  </Text>
                  {sortedTimelineData.length > 0 ? (
                    <>
                      <Text style={{ color: '#FFFFFF', marginBottom: '0.5rem' }}>
                        {sortedTimelineData.length} apps used today
                      </Text>
                      <Text size="sm" style={{ color: '#AAAAAA' }}>
                        Most active: {sortedTimelineData.slice(0, 3).map(app => app.name).join(', ')}
                      </Text>
                    </>
                  ) : (
                    <Text style={{ color: '#AAAAAA' }}>No data available</Text>
                  )}
                </Paper>
              </Grid.Col>
            </Grid>
          </div>
        </Tabs.Panel>

        <Tabs.Panel value="details">
          <Title
            order={2}
            style={{
              fontSize: '1.5rem',
              marginBottom: '0.5rem',
              color: '#00FFFF',
            }}
          >
            Detailed App Usage
          </Title>
          <Text size="sm" style={{ color: '#AAAAAA', marginBottom: '2rem' }}>
            Detailed breakdown of app usage
          </Text>

          <Grid>
            {sortedTimelineData.length === 0 ? (
              <Grid.Col>
                <Text style={{ color: '#00FFFF', textAlign: 'center', padding: '2rem' }}>
                  No app usage data available for today.
                </Text>
              </Grid.Col>
            ) : (
              sortedTimelineData.map((app, index) => (
                <Grid.Col key={index} span={6}>
                  <Card
                    style={{
                      background: 'rgba(0, 0, 32, 0.3)',
                      border: '1px solid rgba(255, 0, 255, 0.1)',
                      borderRadius: '8px',
                      padding: '1rem',
                      height: '100%',
                      transition: 'all 0.3s ease',
                      '&:hover': {
                        borderColor: 'rgba(255, 0, 255, 0.3)',
                        transform: 'translateY(-2px)',
                      }
                    }}
                  >
                    <div style={{ display: 'flex', alignItems: 'center', gap: '1rem', marginBottom: '1rem' }}>
                      {app.icon ? (
                        <img
                          src={`data:image/png;base64,${app.icon}`}
                          alt={app.name}
                          style={{
                            width: '48px',
                            height: '48px',
                            borderRadius: '12px'
                          }}
                        />
                      ) : (
                        <div style={{
                          width: '48px',
                          height: '48px',
                          borderRadius: '12px',
                          backgroundColor: app.color || '#FF00FF',
                          display: 'flex',
                          alignItems: 'center',
                          justifyContent: 'center',
                          fontSize: '24px',
                          color: '#FFFFFF'
                        }}>
                          {app.name.charAt(0)}
                        </div>
                      )}
                      <div>
                        <Text style={{ color: '#FFFFFF', fontSize: '1.1rem', fontWeight: 500 }}>
                          {app.name}
                        </Text>
                        <Text size="sm" style={{ color: '#AAAAAA' }}>
                          Last used: {formatTimelineTime(app.lastUsed)}
                        </Text>
                      </div>
                    </div>

                    <div style={{ marginTop: 'auto' }}>
                      <Text style={{ color: '#00FFFF', marginBottom: '0.5rem' }}>
                        {formatDetailedTime(app.time)}
                      </Text>
                      <Text size="sm" style={{ color: '#AAAAAA' }}>
                        {Math.round((app.time / totalScreenTime) * 100)}% of total screen time
                      </Text>
                    </div>
                  </Card>
                </Grid.Col>
              ))
            )}
          </Grid>
        </Tabs.Panel>
      </Tabs>

      <style>
        {`
          @keyframes fadeIn {
            from {
              opacity: 0;
              transform: translateY(-10px);
            }
            to {
              opacity: 1;
              transform: translateY(0);
            }
          }
        `}
      </style>
    </Container>
  );
};

export default DetailedAnalytics; 