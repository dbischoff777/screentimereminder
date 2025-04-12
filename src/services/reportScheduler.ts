import { EmailSettings } from '../components/EmailReportSettings';
import { sendEmailReport } from './emailService';
import { AppUsageData } from '../types/AppUsageData';

interface ScheduledReport {
  settings: EmailSettings;
  timer: NodeJS.Timeout;
}

export class ReportScheduler {
  private static instance: ReportScheduler;
  private scheduledReports: Map<string, ScheduledReport> = new Map();

  private constructor() {}

  public static getInstance(): ReportScheduler {
    if (!ReportScheduler.instance) {
      ReportScheduler.instance = new ReportScheduler();
    }
    return ReportScheduler.instance;
  }

  private getNextScheduleTime(settings: EmailSettings): Date {
    const now = new Date();
    const nextReport = new Date();

    if (settings.frequency === 'daily') {
      const [hours, minutes] = (settings.preferredTime || '18:00').split(':').map(Number);
      nextReport.setHours(hours, minutes, 0, 0);
      if (nextReport <= now) {
        nextReport.setDate(nextReport.getDate() + 1);
      }
    } else if (settings.frequency === 'weekly') {
      const days = ['Sunday', 'Monday', 'Tuesday', 'Wednesday', 'Thursday', 'Friday', 'Saturday'];
      const targetDay = days.indexOf(settings.weeklyDay || 'Monday');
      const currentDay = now.getDay();
      const daysUntilTarget = (targetDay - currentDay + 7) % 7;
      
      nextReport.setDate(now.getDate() + daysUntilTarget);
      const [hours, minutes] = (settings.preferredTime || '18:00').split(':').map(Number);
      nextReport.setHours(hours, minutes, 0, 0);
      
      if (nextReport <= now) {
        nextReport.setDate(nextReport.getDate() + 7);
      }
    } else if (settings.frequency === 'monthly') {
      const targetDate = parseInt(settings.monthlyDate || '1', 10);
      nextReport.setDate(targetDate);
      const [hours, minutes] = (settings.preferredTime || '18:00').split(':').map(Number);
      nextReport.setHours(hours, minutes, 0, 0);
      
      if (nextReport <= now) {
        nextReport.setMonth(nextReport.getMonth() + 1);
      }
    }

    return nextReport;
  }

  private calculateTotalScreenTime(appUsageData: AppUsageData[]): number {
    return appUsageData.reduce((total, app) => total + app.time, 0);
  }

  private calculateProductivityScore(appUsageData: AppUsageData[]): number {
    if (appUsageData.length === 0) return 0;

    const categoryScores: Record<string, number> = {
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

    appUsageData.forEach(app => {
      const category = app.category || 'Other';
      const score = categoryScores[category as keyof typeof categoryScores] || 0;
      totalScore += score * app.time;
      totalTime += app.time;
    });

    return totalTime > 0 ? Math.round((totalScore / totalTime) * 100) : 0;
  }

  // Process app usage data to enrich it with additional information needed for detailed reports
  private processAppUsageData(appUsageData: AppUsageData[]): AppUsageData[] {
    // Ensure each app has a category
    return appUsageData.map(app => {
      // If app already has a category, keep it; otherwise, categorize it
      if (!app.category) {
        app.category = this.categorizeApp(app.name);
      }
      
      // Ensure each app has a color based on its category
      if (!app.color) {
        app.color = this.getCategoryColor(app.category);
      }
      
      return app;
    });
  }
  
  // Assign a category to an app based on its name
  private categorizeApp(appName: string): string {
    const lowerCaseName = appName.toLowerCase();
    
    if (lowerCaseName.includes('instagram') || 
        lowerCaseName.includes('facebook') || 
        lowerCaseName.includes('twitter') || 
        lowerCaseName.includes('tiktok') || 
        lowerCaseName.includes('snapchat') || 
        lowerCaseName.includes('messenger')) {
      return 'Social Media';
    } else if (lowerCaseName.includes('youtube') || 
               lowerCaseName.includes('netflix') || 
               lowerCaseName.includes('spotify') || 
               lowerCaseName.includes('music') || 
               lowerCaseName.includes('video') || 
               lowerCaseName.includes('player') || 
               lowerCaseName.includes('movie')) {
      return 'Entertainment';
    } else if (lowerCaseName.includes('chrome') || 
               lowerCaseName.includes('browser') || 
               lowerCaseName.includes('gmail') || 
               lowerCaseName.includes('outlook') || 
               lowerCaseName.includes('office') || 
               lowerCaseName.includes('word') || 
               lowerCaseName.includes('excel') || 
               lowerCaseName.includes('docs')) {
      return 'Productivity';
    } else if (lowerCaseName.includes('game') || 
               lowerCaseName.includes('minecraft') || 
               lowerCaseName.includes('fortnite') || 
               lowerCaseName.includes('roblox')) {
      return 'Games';
    } else if (lowerCaseName.includes('duolingo') || 
               lowerCaseName.includes('khan') || 
               lowerCaseName.includes('learn') || 
               lowerCaseName.includes('study') || 
               lowerCaseName.includes('education')) {
      return 'Education';
    } else if (lowerCaseName.includes('whatsapp') || 
               lowerCaseName.includes('telegram') || 
               lowerCaseName.includes('signal') || 
               lowerCaseName.includes('chat') || 
               lowerCaseName.includes('mail')) {
      return 'Communication';
    } else {
      return 'Other';
    }
  }
  
  // Get color based on category for visualization
  private getCategoryColor(category: string): string {
    const categoryColors: Record<string, string> = {
      'Social Media': '#FF1493',    // Deep Pink
      'Entertainment': '#FFD700',   // Gold
      'Productivity': '#00FF00',    // Lime Green
      'Games': '#00FFFF',           // Cyan
      'Education': '#4169E1',       // Royal Blue
      'Communication': '#9932CC',   // Dark Orchid
      'Other': '#FF4500'            // Orange Red
    };
    
    return categoryColors[category as keyof typeof categoryColors] || '#FFFFFF';
  }

  private sendReport(
    settings: EmailSettings,
    appUsageData: AppUsageData[],
    totalScreenTime: number,
    productivityScore: number
  ): void {
    try {
      // Process app data to enrich it
      const processedData = this.processAppUsageData(appUsageData);
      
      // Send the report with processed data
      sendEmailReport(
        processedData,
        totalScreenTime,
        productivityScore,
        settings.email,
        settings.frequency
      );

      // Reschedule the next report
      this.scheduleReport(settings, appUsageData, totalScreenTime, productivityScore);
    } catch (error) {
      console.error('Error sending report:', error);
      throw error;
    }
  }

  public sendImmediateReport(email: string, appUsageData: AppUsageData[]): void {
    // Process app data to enrich it
    const processedData = this.processAppUsageData(appUsageData);
    const totalScreenTime = this.calculateTotalScreenTime(processedData);
    const productivityScore = this.calculateProductivityScore(processedData);

    sendEmailReport(
      processedData,
      totalScreenTime,
      productivityScore,
      email,
      'daily' // Use daily format for immediate reports
    );
  }

  public scheduleReport(
    settings: EmailSettings,
    appUsageData: AppUsageData[],
    totalScreenTime: number,
    productivityScore: number
  ): void {
    // Cancel existing schedule if any
    this.cancelSchedule(settings.email);

    if (!settings.enabled) {
      return;
    }

    const nextReportTime = this.getNextScheduleTime(settings);
    const timeUntilNextReport = nextReportTime.getTime() - new Date().getTime();

    const timer = setTimeout(() => {
      this.sendReport(settings, appUsageData, totalScreenTime, productivityScore);
    }, timeUntilNextReport);

    this.scheduledReports.set(settings.email, {
      settings,
      timer
    });

    console.log(`Report scheduled for ${settings.email} at ${nextReportTime.toLocaleString()}`);
  }

  public cancelSchedule(email: string): void {
    const scheduled = this.scheduledReports.get(email);
    if (scheduled) {
      clearTimeout(scheduled.timer);
      this.scheduledReports.delete(email);
      console.log(`Cancelled scheduled report for ${email}`);
    }
  }

  public updateSchedule(
    settings: EmailSettings,
    appUsageData: AppUsageData[],
    totalScreenTime: number,
    productivityScore: number
  ): void {
    if (settings.enabled) {
      this.scheduleReport(settings, appUsageData, totalScreenTime, productivityScore);
    } else {
      this.cancelSchedule(settings.email);
    }
  }

  public getScheduleInfo(email: string): { nextReport: Date | null; isEnabled: boolean } {
    const scheduled = this.scheduledReports.get(email);
    if (!scheduled) {
      return { nextReport: null, isEnabled: false };
    }

    return {
      nextReport: this.getNextScheduleTime(scheduled.settings),
      isEnabled: scheduled.settings.enabled
    };
  }
}

export const reportScheduler = ReportScheduler.getInstance(); 