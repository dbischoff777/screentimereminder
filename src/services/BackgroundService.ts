import { BackgroundMode } from '@ionic-native/background-mode';

class BackgroundService {
  private static instance: BackgroundService;
  private isInitialized = false;
  private updateInterval: number | null = null;
  private trackingCallback: (() => void) | null = null;

  private constructor() {
    // Private constructor to enforce singleton
  }

  public static getInstance(): BackgroundService {
    if (!BackgroundService.instance) {
      BackgroundService.instance = new BackgroundService();
    }
    return BackgroundService.instance;
  }

  public initialize(): void {
    if (this.isInitialized) return;

    try {
      // Initialize background mode
      if (typeof BackgroundMode !== 'undefined') {
        BackgroundMode.enable();
        
        // Configure background mode
        BackgroundMode.setDefaults({
          title: 'Screen Time Reminder',
          text: 'Tracking your screen time in background',
          icon: 'notification_icon',
          color: '#000020',
          resume: true,
          hidden: false
        });

        // Handle events
        BackgroundMode.on('activate').subscribe(() => {
          console.log('Background mode activated');
          this.startBackgroundTracking();
        });

        BackgroundMode.on('deactivate').subscribe(() => {
          console.log('Background mode deactivated');
          this.stopBackgroundTracking();
        });

        // Prevent the app from being paused
        BackgroundMode.disableWebViewOptimizations();
        
        this.isInitialized = true;
        console.log('Background service initialized');
      } else {
        console.warn('BackgroundMode plugin not available');
      }
    } catch (error) {
      console.error('Error initializing background service:', error);
    }
  }

  public setTrackingCallback(callback: () => void): void {
    this.trackingCallback = callback;
  }

  private startBackgroundTracking(): void {
    if (!this.trackingCallback) {
      console.warn('No tracking callback set');
      return;
    }

    // Run the tracking callback immediately
    this.trackingCallback();

    // Set up interval to run the tracking callback every 10 seconds
    this.updateInterval = window.setInterval(() => {
      if (this.trackingCallback) {
        this.trackingCallback();
      }
    }, 10000);

    console.log('Background tracking started');
  }

  private stopBackgroundTracking(): void {
    if (this.updateInterval) {
      clearInterval(this.updateInterval);
      this.updateInterval = null;
    }
    console.log('Background tracking stopped');
  }

  public disableBackgroundMode(): void {
    if (typeof BackgroundMode !== 'undefined') {
      this.stopBackgroundTracking();
      BackgroundMode.disable();
    }
  }
}

export default BackgroundService; 