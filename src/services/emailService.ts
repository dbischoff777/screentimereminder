import { AppUsageData } from '../types/AppUsageData';

function formatDuration(minutes: number): string {
  // Round to nearest minute to avoid decimal places
  minutes = Math.round(minutes);
  const hours = Math.floor(minutes / 60);
  const remainingMinutes = minutes % 60;
  return `${hours}h ${remainingMinutes}m`;
}

function formatDetailedTime(minutes: number): string {
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
}

function generateBarGraph(value: number, maxValue: number, width: number = 30): string {
  const barWidth = Math.round((value / maxValue) * width);
  const bar = '█'.repeat(barWidth) + '░'.repeat(width - barWidth);
  return bar;
}

function getProductivityLabel(score: number): string {
  if (score >= 75) return 'Highly Productive';
  if (score >= 25) return 'Productive';
  if (score >= -25) return 'Neutral';
  if (score >= -75) return 'Distracting';
  return 'Highly Distracting';
}

function getProductivityColor(score: number): string {
  if (score >= 75) return 'Green';
  if (score >= 25) return 'Cyan';
  if (score >= -25) return 'White';
  if (score >= -75) return 'Magenta';
  return 'Red';
}

// Generate hourly heatmap data from app usage data
function generateHeatmapData(appUsageData: AppUsageData[]): number[] {
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
    hourlyData[hour] += app.time;
  });

  return hourlyData;
}

// Convert heatmap to ASCII art visualization
function generateHeatmapVisualization(hourlyData: number[]): string {
  const maxUsage = Math.max(...hourlyData, 0.1);
  let visualization = '   ';
  
  // Hour labels
  for (let h = 0; h < 24; h += 3) {
    visualization += `${h.toString().padStart(2, '0')} `;
  }
  visualization += '\n   ';
  
  // Hour markers
  for (let h = 0; h < 24; h += 3) {
    visualization += '┬──';
  }
  visualization += '\n';
  
  // Heatmap intensity
  const intensity = ['░', '▒', '▓', '█'];
  visualization += '   ';
  for (let h = 0; h < 24; h++) {
    if (h % 3 === 0 && h > 0) visualization += ' ';
    const intensityLevel = hourlyData[h] === 0 ? ' ' : 
      intensity[Math.min(3, Math.floor((hourlyData[h] / maxUsage) * 4))];
    visualization += intensityLevel;
  }
  
  return visualization;
}

function generateReportContent(
  appUsageData: AppUsageData[],
  totalScreenTime: number,
  productivityScore: number,
  frequency: string
): string {
  const date = new Date().toLocaleDateString();
  let content = `Screen Time Report - ${date}\n`;
  content += `${'═'.repeat(50)}\n\n`;
  
  // Summary Section
  content += `📊 SUMMARY\n`;
  content += `${'─'.repeat(50)}\n`;
  content += `Report Type: ${frequency.charAt(0).toUpperCase() + frequency.slice(1)}\n`;
  content += `Total Screen Time: ${formatDuration(totalScreenTime)}\n`;
  content += `Productivity Score: ${productivityScore}% (${getProductivityLabel(productivityScore)} - ${getProductivityColor(productivityScore)})\n`;
  
  // Get most used app
  const sortedApps = [...appUsageData].sort((a, b) => b.time - a.time);
  if (sortedApps.length > 0) {
    const mostUsedApp = sortedApps[0];
    const mostUsedPercentage = Math.round((mostUsedApp.time / totalScreenTime) * 100);
    content += `Most Used App: ${mostUsedApp.name} - ${formatDuration(mostUsedApp.time)} (${mostUsedPercentage}%)\n`;
  }
  
  // Get peak usage time (hour with maximum usage)
  const heatmapData = generateHeatmapData(appUsageData);
  const peakHourIndex = heatmapData.indexOf(Math.max(...heatmapData));
  if (peakHourIndex >= 0 && heatmapData[peakHourIndex] > 0) {
    content += `Peak Usage Time: ${peakHourIndex.toString().padStart(2, '0')}:00 - ${formatDuration(heatmapData[peakHourIndex])}\n\n`;
  } else {
    content += `\n`;
  }
  
  // Productivity score with visual indicator
  content += `🎯 PRODUCTIVITY RATING\n`;
  content += `${'─'.repeat(50)}\n`;
  const productivityBar = generateBarGraph(productivityScore + 100, 200, 40);
  content += `${productivityScore}% - ${getProductivityLabel(productivityScore)}\n`;
  content += `${productivityBar}\n\n`;
  
  // Heatmap visualization
  content += `⏰ USAGE HEATMAP\n`;
  content += `${'─'.repeat(50)}\n`;
  content += `Daily activity pattern by hour:\n\n`;
  content += generateHeatmapVisualization(heatmapData);
  content += `\n\n`;
  
  // Group apps by category for category distribution
  content += `📱 APPLICATION USAGE BY CATEGORY\n`;
  content += `${'─'.repeat(50)}\n`;
  
  const categoryMap = new Map<string, AppUsageData[]>();
  appUsageData.forEach(app => {
    const category = app.category || 'Other';
    if (!categoryMap.has(category)) {
      categoryMap.set(category, []);
    }
    categoryMap.get(category)?.push(app);
  });

  // Sort categories by total time
  const sortedCategories = Array.from(categoryMap.entries()).sort((a, b) => {
    const aTime = a[1].reduce((sum, app) => sum + app.time, 0);
    const bTime = b[1].reduce((sum, app) => sum + app.time, 0);
    return bTime - aTime;
  });

  // Find maximum category time for graph scaling
  const maxCategoryTime = Math.max(...sortedCategories.map(([_, apps]) => 
    apps.reduce((sum, app) => sum + app.time, 0)
  ));

  // Category distribution with bar graphs
  sortedCategories.forEach(([category, apps]) => {
    const categoryTime = apps.reduce((sum, app) => sum + app.time, 0);
    const percentage = Math.round((categoryTime / totalScreenTime) * 100);
    const bar = generateBarGraph(categoryTime, maxCategoryTime, 30);
    
    content += `${category}\n`;
    content += `${bar} ${formatDuration(categoryTime)} (${percentage}%)\n`;
    
    // Top 3 apps in category
    const topApps = apps
      .sort((a, b) => b.time - a.time)
      .slice(0, 3);
    
    topApps.forEach((app, index) => {
      const appPercentage = Math.round((app.time / categoryTime) * 100);
      const isLast = index === topApps.length - 1 && apps.length <= 3;
      const appBar = generateBarGraph(app.time, categoryTime, 20);
      content += `  ${isLast ? '└' : '├'}─ ${app.name}\n`;
      content += `  ${isLast ? ' ' : '│'}  ${appBar} ${formatDuration(app.time)} (${appPercentage}%)\n`;
    });
    
    if (apps.length > 3) {
      const otherApps = apps.length - 3;
      content += `  └─ and ${otherApps} more app${otherApps > 1 ? 's' : ''}\n`;
    }
    content += '\n';
  });

  // Detailed Apps List
  content += `📊 TOP 10 MOST USED APPS\n`;
  content += `${'─'.repeat(50)}\n`;
  
  const top10Apps = [...appUsageData]
    .sort((a, b) => b.time - a.time)
    .slice(0, 10);
  
  if (top10Apps.length === 0) {
    content += 'No app usage data available\n\n';
  } else {
    const maxAppTime = top10Apps[0].time;
    top10Apps.forEach((app, index) => {
      const percentage = Math.round((app.time / totalScreenTime) * 100);
      const bar = generateBarGraph(app.time, maxAppTime, 30);
      content += `${(index + 1).toString().padStart(2, ' ')}. ${app.name}\n`;
      content += `   ${bar} ${formatDetailedTime(app.time)} (${percentage}%)\n`;
      if (app.category) {
        content += `   Category: ${app.category}\n`;
      }
      if (app.lastUsed) {
        const lastUsedTime = new Date(app.lastUsed).toLocaleTimeString([], { hour: '2-digit', minute: '2-digit' });
        content += `   Last used: ${lastUsedTime}\n`;
      }
      content += '\n';
    });
  }
  
  // Daily timeline (if data has timestamps)
  const appsWithTimestamps = appUsageData.filter(app => app.lastUsed);
  if (appsWithTimestamps.length > 0) {
    content += `⏳ DAILY TIMELINE\n`;
    content += `${'─'.repeat(50)}\n`;
    
    // Create 6 time blocks (4-hour intervals)
    const timeBlocks = Array(6).fill(0);
    appsWithTimestamps.forEach(app => {
      if (app.lastUsed) {
        const hour = new Date(app.lastUsed).getHours();
        const blockIndex = Math.floor(hour / 4);
        timeBlocks[blockIndex] += app.time;
      }
    });
    
    const maxBlockTime = Math.max(...timeBlocks);
    const timeRanges = [
      '00:00-04:00', '04:00-08:00', '08:00-12:00',
      '12:00-16:00', '16:00-20:00', '20:00-24:00'
    ];
    
    timeBlocks.forEach((time, index) => {
      if (time > 0) {
        const bar = generateBarGraph(time, maxBlockTime, 30);
        content += `${timeRanges[index]}\n`;
        content += `${bar} ${formatDuration(time)}\n\n`;
      }
    });
  }

  content += `\nThis report was generated by Screen Time Reminder\n`;
  content += `Report generated on: ${new Date().toLocaleString()}\n`;
  content += '╚' + '═'.repeat(48) + '╝\n';
  
  return content;
}

export function sendEmailReport(
  appUsageData: AppUsageData[],
  totalScreenTime: number,
  productivityScore: number,
  toEmail: string,
  frequency: string
): void {
  try {
    const subject = `Screen Time Report - ${new Date().toLocaleDateString()}`;
    const body = generateReportContent(appUsageData, totalScreenTime, productivityScore, frequency);
    
    // Create mailto URL
    const mailtoUrl = `mailto:${encodeURIComponent(toEmail)}?subject=${encodeURIComponent(subject)}&body=${encodeURIComponent(body)}`;
    
    // Open default email client
    window.location.href = mailtoUrl;
  } catch (error) {
    console.error('Error opening email client:', error);
    throw error;
  }
}

export function validateEmailSettings(email: string): boolean {
  const emailRegex = /^[^\s@]+@[^\s@]+\.[^\s@]+$/;
  return emailRegex.test(email);
} 