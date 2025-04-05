import { EmailSettings } from '../components/EmailReportSettings';
import { sendEmailReport } from './emailService';
import { AppUsageData } from '../types/AppUsageData';

interface ScheduledReport {
  settings: EmailSettings;
  timer: NodeJS.Timeout;
}

export class ReportScheduler {
  private static instance: ReportScheduler;
  private scheduledReports: Map<string, ScheduledReport>;

  private constructor() {
    this.scheduledReports = new Map();
  }

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

    appUsageData.forEach(app => {
      const score = categoryScores[app.category as keyof typeof categoryScores] || 0;
      totalScore += score * app.time;
      totalTime += app.time;
    });

    return totalTime > 0 ? Math.round((totalScore / totalTime) * 100) : 0;
  }

  private sendReport(
    settings: EmailSettings,
    appUsageData: AppUsageData[],
    totalScreenTime: number,
    productivityScore: number
  ): void {
    try {
      sendEmailReport(
        appUsageData,
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
    const totalScreenTime = this.calculateTotalScreenTime(appUsageData);
    const productivityScore = this.calculateProductivityScore(appUsageData);

    sendEmailReport(
      appUsageData,
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