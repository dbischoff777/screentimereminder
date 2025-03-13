/**
 * BackgroundService.ts
 * A service to handle background operations for the app
 */

import { registerPlugin } from '@capacitor/core';

// Define the interface for our BackgroundMode plugin
interface BackgroundModePlugin {
  enable(): Promise<{ value: boolean }>;
  disable(): Promise<{ value: boolean }>;
  isEnabled(): Promise<{ value: boolean }>;
}

// Register the BackgroundMode plugin
const BackgroundMode = registerPlugin<BackgroundModePlugin>('BackgroundMode');

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
      // Check if we're in a mobile environment
      const isMobileEnvironment = this.isMobileEnvironment();
      
      if (!isMobileEnvironment) {
        console.log('Not in a mobile environment, skipping background mode initialization');
        return;
      }

      // Enable background mode
      this.enableBackgroundMode();
      
      this.isInitialized = true;
    } catch (error) {
      console.error('Critical error in background service initialization:', error);
    }
  }

  private isMobileEnvironment(): boolean {
    try {
      return typeof window !== 'undefined' && 
        (this.isCordovaEnvironment() || this.isCapacitorEnvironment());
    } catch (e) {
      console.error('Error checking mobile environment:', e);
      return false;
    }
  }

  private isCordovaEnvironment(): boolean {
    try {
      return typeof window !== 'undefined' && 
        'cordova' in window;
    } catch (e) {
      console.error('Error checking Cordova environment:', e);
      return false;
    }
  }

  private isCapacitorEnvironment(): boolean {
    try {
      return typeof window !== 'undefined' && 
        'Capacitor' in window;
    } catch (e) {
      console.error('Error checking Capacitor environment:', e);
      return false;
    }
  }

  private async enableBackgroundMode(): Promise<void> {
    try {
      console.log('Enabling background mode...');
      
      // Check if BackgroundMode plugin is available
      if (!BackgroundMode) {
        console.warn('BackgroundMode plugin not available');
        return;
      }
      
      // Enable background mode
      const result = await BackgroundMode.enable();
      console.log('Background mode enabled:', result.value);
    } catch (error) {
      console.error('Error enabling background mode:', error);
    }
  }

  public setTrackingCallback(callback: (() => void) | null): void {
    if (!callback) {
      console.warn('Null tracking callback provided');
      return;
    }
    
    this.trackingCallback = callback;
    console.log('Tracking callback set');
    
    // If already initialized, start tracking immediately
    if (this.isInitialized && !this.updateInterval) {
      this.startBackgroundTracking();
    }
  }

  private startBackgroundTracking(): void {
    // Clear any existing interval first to prevent duplicates
    this.stopBackgroundTracking();
    
    if (!this.trackingCallback) {
      console.warn('No tracking callback set, cannot start background tracking');
      return;
    }

    try {
      // Run the tracking callback immediately
      this.trackingCallback();

      // Set up interval with a more conservative update frequency (30 seconds)
      // to reduce battery usage and potential crashes
      this.updateInterval = window.setInterval(() => {
        try {
          if (this.trackingCallback) {
            this.trackingCallback();
          }
        } catch (callbackError) {
          console.error('Error in tracking callback:', callbackError);
          // Don't let a single callback error crash the entire tracking
        }
      }, 30000); // 30 seconds to reduce battery drain and potential crashes

      console.log('Background tracking started with 30-second interval');
    } catch (trackingError) {
      console.error('Error starting background tracking:', trackingError);
    }
  }

  private stopBackgroundTracking(): void {
    try {
      if (this.updateInterval) {
        clearInterval(this.updateInterval);
        this.updateInterval = null;
        console.log('Background tracking stopped');
      }
    } catch (error) {
      console.error('Error stopping background tracking:', error);
    }
  }

  public disableBackgroundMode(): void {
    try {
      this.stopBackgroundTracking();
      
      if (BackgroundMode) {
        BackgroundMode.disable()
          .then(() => console.log('Background mode disabled'))
          .catch(error => console.error('Error disabling background mode:', error));
      }
    } catch (error) {
      console.error('Error in disableBackgroundMode:', error);
    }
  }
}

export default BackgroundService; 