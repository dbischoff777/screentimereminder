import { Container, Title, Text, Grid, Tabs, Paper, Card, Badge, Stack, Group } from '@mantine/core';
import { useScreenTime } from '../context/ScreenTimeContext';
import { useState, useEffect } from 'react';
import FocusTimer from '../components/FocusTimer';
import { EmailReportSettings, EmailSettings } from '../components/EmailReportSettings';
import { ReportScheduler } from '../services/reportScheduler';
import { notifications } from '@mantine/notifications';
import { AppUsage } from '../services/AppUsageTracker';

const DetailedAnalytics = () => {
  const { 
    appUsageData, 
    getTotalScreenTime, 
    screenTimeLimit,
    getAppUsageData
  } = useScreenTime();
  const [totalScreenTime, setTotalScreenTime] = useState(0);
  const [activeTab, setActiveTab] = useState<string | null>('heatmap');

  // Add focus session tracking
  const [focusSessions, setFocusSessions] = useState<{
    category: string;
    duration: number;
    timestamp: Date;
  }[]>([]);

  useEffect(() => {
    // Calculate total screen time
    const total = getTotalScreenTime();
    setTotalScreenTime(total);
  }, [appUsageData, getTotalScreenTime]);

  // Filter and sort current day's data
  const getCurrentDayData = async () => {
    const now = new Date();
    const startOfDay = new Date(now.getFullYear(), now.getMonth(), now.getDate());
    const endOfDay = new Date(now.getFullYear(), now.getMonth(), now.getDate(), 23, 59, 59, 999);

    // Get all usage events for today
    const usageData = await getAppUsageData(startOfDay.getTime(), endOfDay.getTime());
    
    // Group usage events by app and calculate total time for each
    const appUsageMap = new Map<string, {
      name: string;
      time: number;
      lastUsed?: Date;
      category: string;
      color: string;
      icon?: string;
      usageInstances: Array<{ startTime: Date; endTime: Date; duration: number }>;
    }>();

    usageData.forEach((app: AppUsage) => {
      if (!app.lastUsed) return;
      
      const usageTime = new Date(app.lastUsed);
      if (usageTime < startOfDay || usageTime > endOfDay) return;

      if (!appUsageMap.has(app.name)) {
        appUsageMap.set(app.name, {
          name: app.name,
          time: 0,
          lastUsed: app.lastUsed,
          category: app.category,
          color: app.color,
          icon: app.icon,
          usageInstances: []
        });
      }
      
      const appData = appUsageMap.get(app.name)!;
      const startTime = new Date(app.lastUsed.getTime() - app.time * 60000);
      
      // If the usage started before midnight, adjust the start time and duration
      if (startTime < startOfDay) {
        const adjustedDuration = (app.lastUsed.getTime() - startOfDay.getTime()) / (60 * 1000);
        if (adjustedDuration > 0) {
          appData.time += adjustedDuration;
          appData.usageInstances.push({
            startTime: startOfDay,
            endTime: app.lastUsed,
            duration: adjustedDuration
          });
        }
      } else {
        appData.time += app.time;
        appData.usageInstances.push({
          startTime,
          endTime: app.lastUsed,
          duration: app.time
        });
      }
    });

    // Convert map to array and sort by last used time
    return Array.from(appUsageMap.values())
      .sort((a, b) => {
        const timeA = a.lastUsed ? a.lastUsed.getTime() : 0;
        const timeB = b.lastUsed ? b.lastUsed.getTime() : 0;
        return timeB - timeA;
      });
  };

  const [sortedTimelineData, setSortedTimelineData] = useState<Array<{
    name: string;
    time: number;
    lastUsed?: Date;
    category: string;
    color: string;
    icon?: string;
    usageInstances: Array<{ startTime: Date; endTime: Date; duration: number }>;
  }>>([]);

  useEffect(() => {
    getCurrentDayData().then(data => {
      setSortedTimelineData(data);
    });
  }, [appUsageData]);

  // Get most used app sorted by time
  const getMostUsedApp = () => {
    return [...sortedTimelineData].sort((a, b) => b.time - a.time)[0];
  };

  const mostUsedApp = getMostUsedApp();

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
      const startTime = new Date(usageTime.getTime() - app.time * 60000);
      
      // If the usage started before midnight, adjust the start time and duration
      if (startTime < startOfDay) {
        const adjustedDuration = (usageTime.getTime() - startOfDay.getTime()) / (60 * 1000);
        if (adjustedDuration > 0) {
          const hour = usageTime.getHours();
          hourlyData[hour] += adjustedDuration;
        }
      } else {
        const hour = usageTime.getHours();
        hourlyData[hour] += app.time;
      }
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

  // Category colors for visualization
  const categoryColors = {
    'Social Media': '#FF1493',    // Deep Pink
    'Entertainment': '#FFD700',   // Gold
    'Productivity': '#00FF00',    // Lime Green
    'Games': '#00FFFF',          // Cyan
    'Education': '#4169E1',      // Royal Blue
    'Communication': '#9932CC',   // Dark Orchid
    'Other': '#FF4500'           // Orange Red
  };

  // Calculate productivity score based on app categories
  const calculateProductivityScore = () => {
    if (sortedTimelineData.length === 0) return 0;

    const categoryScores = {
      'Productivity': 1,
      'Education': 1,
      'Communication': 0.5,
      'Social Media': -0.5,
      'Entertainment': -0.5,
      'Games': -1,
      'Other': 0
    };

    let totalScore = 0;
    let totalTime = 0;

    sortedTimelineData.forEach(app => {
      const score = categoryScores[app.category as keyof typeof categoryScores] || 0;
      totalScore += score * app.time;
      totalTime += app.time;
    });

    return totalTime > 0 ? Math.round((totalScore / totalTime) * 100) : 0;
  };

  const productivityScore = calculateProductivityScore();

  // Get color based on productivity score
  const getProductivityColor = (score: number) => {
    if (score >= 75) return '#00FF00';      // High productivity (green)
    if (score >= 25) return '#00FFFF';      // Moderate productivity (cyan)
    if (score >= -25) return '#FFFFFF';     // Neutral (white)
    if (score >= -75) return '#FF00FF';     // Low productivity (magenta)
    return '#FF0000';                       // Very low productivity (red)
  };

  // Get label based on productivity score
  const getProductivityLabel = (score: number) => {
    if (score >= 75) return 'Highly Productive';
    if (score >= 25) return 'Productive';
    if (score >= -25) return 'Neutral';
    if (score >= -75) return 'Distracting';
    return 'Highly Distracting';
  };

  // Calculate category distribution using native categories
  const calculateCategoryDistribution = () => {
    const distribution = new Map();
    let totalTime = 0;

    sortedTimelineData.forEach(app => {
      const category = app.category || 'Other';
      const current = distribution.get(category) || 0;
      distribution.set(category, current + app.time);
      totalTime += app.time;
    });

    return Array.from(distribution.entries()).map(([category, time]) => ({
      category,
      time,
      percentage: totalTime > 0 ? Math.round((time / totalTime) * 100) : 0
    }));
  };

  const categoryDistribution = calculateCategoryDistribution();

  // Handle completed focus sessions
  const handleFocusSessionComplete = (category: string, duration: number) => {
    setFocusSessions(prev => [
      ...prev,
      {
        category,
        duration,
        timestamp: new Date()
      }
    ]);
  };

  // Calculate total focus time for today
  const getTodayFocusTime = () => {
    const today = new Date();
    const startOfDay = new Date(today.getFullYear(), today.getMonth(), today.getDate());
    
    return focusSessions
      .filter(session => new Date(session.timestamp) >= startOfDay)
      .reduce((total, session) => total + session.duration, 0);
  };

  // Function to ensure popover elements are positioned within the viewport
  const ensureInViewport = (element: HTMLElement | null) => {
    if (!element) return;
    
    const rect = element.getBoundingClientRect();
    const viewportHeight = window.innerHeight;
    
    // If element extends beyond bottom of screen
    if (rect.bottom > viewportHeight) {
      const overflowAmount = rect.bottom - viewportHeight;
      const newTop = Math.max(0, rect.top - overflowAmount - 20); // 20px buffer
      element.style.top = `${newTop}px`;
    }
  };

  // Add event listener to fix popover positioning
  useEffect(() => {
    const checkPopovers = () => {
      const popovers = document.querySelectorAll('.mantine-Select-dropdown, .mantine-Popover-dropdown');
      popovers.forEach(popover => ensureInViewport(popover as HTMLElement));
    };
    
    window.addEventListener('scroll', checkPopovers);
    
    // Check for popovers every 500ms
    const interval = setInterval(checkPopovers, 500);
    
    return () => {
      window.removeEventListener('scroll', checkPopovers);
      clearInterval(interval);
    };
  }, []);

  // Handle email settings
  const handleEmailSettingsSave = async (settings: EmailSettings) => {
    try {
      const scheduler = ReportScheduler.getInstance();
      const totalScreenTime = getTotalScreenTime();
      const productivityScore = calculateProductivityScore();

      await scheduler.updateSchedule(
        settings,
        appUsageData,
        totalScreenTime,
        productivityScore
      );
      
      const scheduleInfo = scheduler.getScheduleInfo(settings.email);
      if (scheduleInfo.isEnabled && scheduleInfo.nextReport) {
        notifications.show({
          title: 'Success',
          message: `Next report scheduled for ${scheduleInfo.nextReport.toLocaleString()}`,
          color: 'teal'
        });
      }
    } catch (error) {
      console.error('Failed to schedule report:', error);
      notifications.show({
        title: 'Error',
        message: error instanceof Error ? error.message : 'Failed to schedule report. Please try again.',
        color: 'red'
      });
      throw error; // Propagate the error to the EmailReportSettings component
    }
  };

  return (
    <Container 
      size="md" 
      py="xl" 
      style={{
        background: '#000020',
        minHeight: '100vh',
        padding: '1rem',
        paddingBottom: '3rem',
        position: 'relative',
        overflowX: 'hidden',
        overflowY: 'visible'
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
        onChange={setActiveTab}
        defaultValue="heatmap"
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
            borderRadius: '8px',
            overflowX: 'auto',
            overflowY: 'hidden',
            scrollbarWidth: 'none', // Firefox
            msOverflowStyle: 'none', // IE/Edge
            '&::-webkit-scrollbar': {
              display: 'none' // Chrome/Safari/Opera
            },
            WebkitOverflowScrolling: 'touch', // Smooth scrolling on iOS
          },
          tab: {
            flex: '0 0 auto',
            minWidth: '120px',
            color: '#00FFFF',
            fontSize: '0.9rem',
            padding: '0.75rem 1rem',
            textAlign: 'center',
            backgroundColor: 'rgba(0, 0, 32, 0.5)',
            transition: 'all 0.3s ease',
            whiteSpace: 'nowrap',
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
            overflowY: 'visible', // Ensure dropdowns are not cut off
            position: 'relative', // Enable proper positioning
            zIndex: 1 // Ensure proper stacking
          },
        }}
        classNames={{
          panel: 'detailed-analytics-panel'
        }}
      >
        <Tabs.List>
          <Tabs.Tab value="heatmap">HEATMAP</Tabs.Tab>
          <Tabs.Tab value="timeline">TIMELINE</Tabs.Tab>
          <Tabs.Tab value="insights">INSIGHTS</Tabs.Tab>
          <Tabs.Tab value="details">DETAILS</Tabs.Tab>
          <Tabs.Tab value="focus">FOCUS</Tabs.Tab>
          <Tabs.Tab value="settings">REPORT</Tabs.Tab>
        </Tabs.List>

        <Tabs.Panel value="heatmap" data-active-tab="heatmap">
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
                  {Math.floor(heatmapData.reduce((sum, val) => sum + val, 0) / 60)}h
                </Text>
                <Text size="sm">
                  {Math.round(heatmapData.reduce((sum, val) => sum + val, 0) % 60)}m
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

        <Tabs.Panel value="timeline" data-active-tab="timeline">
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
                  
                  {sortedTimelineData
                    .flatMap(app => 
                      app.usageInstances.map(instance => ({
                        ...instance,
                        appName: app.name,
                        appIcon: app.icon,
                        appColor: app.color
                      }))
                    )
                    .sort((a, b) => b.startTime.getTime() - a.startTime.getTime())
                    .map((instance, index) => (
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
                          {formatTimelineTime(instance.startTime)}
                        </Text>
                        
                        {/* App icon container */}
                        <div style={{
                          position: 'relative',
                          zIndex: 2,
                          background: 'transparent',
                          borderRadius: '50%',
                          padding: '2px',
                          marginRight: '16px'
                        }}>
                          {instance.appIcon ? (
                            <img
                              src={instance.appIcon}
                              alt={instance.appName}
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
                              background: instance.appColor,
                              display: 'flex',
                              alignItems: 'center',
                              justifyContent: 'center',
                              fontSize: '16px',
                              color: '#FFFFFF'
                            }}>
                              {instance.appName.charAt(0)}
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
                            {instance.appName}
                          </Text>
                          <Text size="sm" style={{ color: '#AAAAAA' }}>
                            {formatDetailedTime(instance.duration)}
                          </Text>
                        </div>
                      </div>
                    ))}
                </div>
              </div>
            )}
          </div>
        </Tabs.Panel>

        <Tabs.Panel value="insights" data-active-tab="insights">
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
              {/* Productivity Score */}
              <Grid.Col span={12}>
                <Paper
                  style={{
                    background: 'rgba(0, 0, 32, 0.3)',
                    padding: '1.5rem',
                    borderRadius: '8px',
                    marginBottom: '1.5rem'
                  }}
                >
                  <div style={{ textAlign: 'center' }}>
                    <Text size="xl" fw={700} style={{ color: '#00FFFF', marginBottom: '1rem' }}>
                      Productivity Score
                    </Text>
                    <div style={{ 
                      display: 'flex', 
                      justifyContent: 'center', 
                      alignItems: 'center',
                      gap: '1rem',
                      marginBottom: '1rem'
                    }}>
                      <Text size="3rem" style={{ 
                        color: getProductivityColor(productivityScore),
                        fontWeight: 700,
                        textShadow: `0 0 10px ${getProductivityColor(productivityScore)}`
                      }}>
                        {productivityScore}
                      </Text>
                      <Badge 
                        size="lg"
                        style={{ 
                          backgroundColor: getProductivityColor(productivityScore),
                          color: '#000000'
                        }}
                      >
                        {getProductivityLabel(productivityScore)}
                      </Badge>
                    </div>
                  </div>

                  {/* Category Distribution */}
                  <div style={{ marginTop: '2rem' }}>
                    <Text fw={500} style={{ color: '#FFFFFF', marginBottom: '1rem' }}>
                      Category Distribution
                    </Text>
                    {categoryDistribution.map(({ category, time, percentage }) => (
                      <div key={category} style={{ marginBottom: '1rem' }}>
                        <div style={{ 
                          display: 'flex', 
                          justifyContent: 'space-between',
                          alignItems: 'center',
                          marginBottom: '0.5rem'
                        }}>
                          <Text size="sm" style={{ color: '#FFFFFF' }}>{category}</Text>
                          <Text size="sm" style={{ color: '#AAAAAA' }}>{formatDetailedTime(time)} ({percentage}%)</Text>
                        </div>
                        <div style={{ 
                          width: '100%',
                          height: '4px',
                          backgroundColor: 'rgba(255, 255, 255, 0.1)',
                          borderRadius: '2px',
                          overflow: 'hidden'
                        }}>
                          <div style={{
                            width: `${percentage}%`,
                            height: '100%',
                            backgroundColor: categoryColors[category as keyof typeof categoryColors] || '#FFFFFF',
                            transition: 'width 0.3s ease'
                          }} />
                        </div>
                      </div>
                    ))}
                  </div>
                </Paper>
              </Grid.Col>

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
                  {mostUsedApp ? (
                    <>
                      <div style={{ display: 'flex', alignItems: 'center', gap: '1rem', marginBottom: '0.5rem' }}>
                        {mostUsedApp.icon ? (
                          <img
                            src={mostUsedApp.icon}
                            alt={mostUsedApp.name}
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
                            backgroundColor: mostUsedApp.color,
                            display: 'flex',
                            alignItems: 'center',
                            justifyContent: 'center',
                            color: '#FFFFFF'
                          }}>
                            {mostUsedApp.name.charAt(0)}
                          </div>
                        )}
                        <Text style={{ color: '#FFFFFF' }}>{mostUsedApp.name}</Text>
                      </div>
                      <Text size="sm" style={{ color: '#AAAAAA' }}>
                        {formatDetailedTime(mostUsedApp.time)} ({Math.round((mostUsedApp.time / totalScreenTime) * 100)}% of total)
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

        <Tabs.Panel value="details" data-active-tab="details">
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
                          src={app.icon}
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
                        <div style={{ display: 'flex', alignItems: 'center', gap: '0.5rem', marginBottom: '0.25rem' }}>
                          <Text style={{ color: '#FFFFFF', fontSize: '1.1rem', fontWeight: 500 }}>
                            {app.name}
                          </Text>
                          <Badge
                            size="sm"
                            style={{
                              backgroundColor: categoryColors[app.category as keyof typeof categoryColors] || '#FFFFFF',
                              color: '#000000'
                            }}
                          >
                            {app.category}
                          </Badge>
                        </div>
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

        <Tabs.Panel value="focus" data-active-tab="focus">
          <div style={{ maxWidth: '600px', margin: '0 auto', paddingBottom: '80px' }}>
            <FocusTimer onSessionComplete={handleFocusSessionComplete} />

            {/* Focus Sessions Summary */}
            {focusSessions.length > 0 && (
              <Paper
                style={{
                  background: 'rgba(0, 0, 32, 0.3)',
                  padding: '1.5rem',
                  borderRadius: '8px',
                  marginTop: '2rem',
                  border: '1px solid rgba(255, 0, 255, 0.1)',
                }}
              >
                <Text size="xl" fw={700} style={{ color: '#00FFFF', marginBottom: '1rem' }}>
                  Today's Focus Sessions
                </Text>
                
                <Text style={{ color: '#FFFFFF', marginBottom: '1rem' }}>
                  Total Focus Time: {formatDetailedTime(getTodayFocusTime())}
                </Text>

                <Stack gap="md">
                  {focusSessions
                    .filter(session => {
                      const sessionDate = new Date(session.timestamp);
                      const today = new Date();
                      return (
                        sessionDate.getDate() === today.getDate() &&
                        sessionDate.getMonth() === today.getMonth() &&
                        sessionDate.getFullYear() === today.getFullYear()
                      );
                    })
                    .map((session, index) => (
                      <Group key={index} justify="space-between" style={{ 
                        padding: '0.5rem',
                        borderRadius: '4px',
                        backgroundColor: 'rgba(255, 255, 255, 0.05)'
                      }}>
                        <div>
                          <Text style={{ color: '#FFFFFF' }}>
                            {session.category}
                          </Text>
                          <Text size="sm" style={{ color: '#AAAAAA' }}>
                            {new Date(session.timestamp).toLocaleTimeString()}
                          </Text>
                        </div>
                        <Badge>{session.duration} minutes</Badge>
                      </Group>
                    ))
                    .reverse()}
                </Stack>
              </Paper>
            )}
          </div>
        </Tabs.Panel>

        <Tabs.Panel value="settings" data-active-tab="settings">
          <div style={{ maxWidth: '600px', margin: '0 auto', paddingBottom: '80px' }}>
            <Title
              order={2}
              style={{
                fontSize: '1.5rem',
                marginBottom: '1.5rem',
                color: '#00FFFF',
              }}
            >
              Report Settings
            </Title>
            
            <Paper
              style={{
                background: 'rgba(0, 0, 32, 0.5)',
                padding: '1.5rem',
                borderRadius: '8px',
                border: '1px solid rgba(255, 0, 255, 0.1)',
                marginBottom: '1.5rem',
              }}
            >
              <Text size="sm" style={{ 
                color: '#FFFFFF', 
                marginBottom: '1.5rem',
                lineHeight: 1.6
              }}>
                Configure when and how you receive screen time reports. These reports include detailed analytics 
                about your app usage patterns and productivity metrics.
              </Text>
              
              <EmailReportSettings onSave={handleEmailSettingsSave} />
            </Paper>
            
            <Card
              style={{
                background: 'rgba(0, 0, 32, 0.3)',
                borderRadius: '8px',
                padding: '1rem',
                marginTop: '1rem',
                border: '1px solid rgba(0, 255, 255, 0.1)',
              }}
            >
              <Group align="center" style={{ color: '#AAAAAA' }}>
                <span role="img" aria-label="info" style={{ fontSize: '1.2rem', marginRight: '0.5rem' }}>ℹ️</span>
                <Text size="sm">
                  Reports are sent securely via email and contain no personally identifiable information beyond your app usage data.
                </Text>
              </Group>
            </Card>
          </div>
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

          /* Fix for dropdown menus */
          .mantine-Select-dropdown {
            z-index: 10000;
            position: fixed;
            max-height: 60vh;
          }

          /* Custom scrollbar for dropdowns */
          .mantine-Select-dropdown::-webkit-scrollbar {
            width: 8px;
          }
          
          .mantine-Select-dropdown::-webkit-scrollbar-track {
            background: rgba(0, 0, 32, 0.5);
          }
          
          .mantine-Select-dropdown::-webkit-scrollbar-thumb {
            background-color: #FF00FF;
            border-radius: 4px;
          }
        `}
      </style>
    </Container>
  );
};

export default DetailedAnalytics; 