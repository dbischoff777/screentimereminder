import { registerPlugin } from '@capacitor/core';

// Use the existing AppUsage interface from ScreenTimeContext
// This is just for reference - we'll import the actual interface
export interface AppUsage {
  name: string;
  time: number; // time in minutes
  color: string;
  lastUsed?: Date;
  category: string;
  isActive?: boolean;
}

// Define the interface for our AppUsageTracker plugin
export interface AppUsageTrackerPlugin {
  hasUsagePermission(): Promise<{ value: boolean }>;
  requestUsagePermission(): Promise<void>;
  startTracking(): Promise<{ value: boolean }>;
  stopTracking(): Promise<{ value: boolean }>;
  getAppUsageData(options?: { startTime?: number; endTime?: number }): Promise<{ data: string }>;
  addListener(
    eventName: 'appChanged',
    listenerFunc: (data: { packageName: string; appName: string }) => void
  ): Promise<{ remove: () => void }>;
  addListener(
    eventName: 'usageUpdate',
    listenerFunc: (data: { usageData: string }) => void
  ): Promise<{ remove: () => void }>;
  removeAllListeners(): Promise<void>;
  isBatteryOptimizationExempt(): Promise<{ value: boolean }>;
  requestBatteryOptimizationExemption(): Promise<void>;
  checkScreenTimeLimit(params: {
    totalTime: number;
    limit: number;
    remainingMinutes: number;
  }): Promise<{ value: boolean }>;
}

// Register the AppUsageTracker plugin
const AppUsageTracker = registerPlugin<AppUsageTrackerPlugin>('AppUsageTracker');

// Create a class to handle app usage tracking
export default class AppUsageTrackerService {
  private static instance: AppUsageTrackerService;
  private isTracking = false;
  private listeners: Array<(usageData: AppUsage[]) => void> = [];
  private plugin: any;

  private constructor() {
    this.plugin = AppUsageTracker;
    // Private constructor to enforce singleton
    this.setupBroadcastListener();
  }

  public static getInstance(): AppUsageTrackerService {
    if (!AppUsageTrackerService.instance) {
      AppUsageTrackerService.instance = new AppUsageTrackerService();
    }
    return AppUsageTrackerService.instance;
  }

  /**
   * Set up a listener for broadcast messages from the native side
   */
  private setupBroadcastListener() {
    try {
      // Listen for app usage updates from the native side
      document.addEventListener('com.screentimereminder.APP_USAGE_UPDATE', (event: any) => {
        try {
          const usageData = JSON.parse(event.detail.usageData);
          this.notifyListeners(usageData);
        } catch (error) {
          console.error('Error processing app usage update:', error);
        }
      });
    } catch (error) {
      console.error('Error setting up broadcast listener:', error);
    }
  }

  /**
   * Check if the app has permission to access usage stats
   */
  public async hasUsagePermission(): Promise<boolean> {
    try {
      console.log('AppUsageTracker: Checking usage permission');
      const result = await AppUsageTracker.hasUsagePermission();
      console.log('AppUsageTracker: Permission check result:', result.value);
      return result.value;
    } catch (error) {
      console.error('Error checking usage permission:', error);
      return false;
    }
  }

  /**
   * Request permission to access usage stats
   */
  public async requestUsagePermission(): Promise<void> {
    try {
      console.log('AppUsageTracker: Requesting usage permission');
      await AppUsageTracker.requestUsagePermission();
      console.log('AppUsageTracker: Permission request completed');
      
      // Wait a moment for the settings page to open
      await new Promise(resolve => setTimeout(resolve, 500));
      
      // Check if permission was granted after a delay
      setTimeout(async () => {
        const hasPermission = await this.hasUsagePermission();
        console.log('AppUsageTracker: Permission check after request:', hasPermission);
      }, 5000);
    } catch (error) {
      console.error('Error requesting usage permission:', error);
      throw error;
    }
  }

  /**
   * Start tracking app usage
   */
  public async startTracking(): Promise<boolean> {
    try {
      // Check if we have permission
      console.log('AppUsageTracker: Checking permission before starting tracking');
      const hasPermission = await this.hasUsagePermission();
      if (!hasPermission) {
        console.warn('AppUsageTracker: Usage stats permission not granted');
        console.log('AppUsageTracker: Attempting to request permission');
        await this.requestUsagePermission();
        return false;
      }

      // Start tracking
      console.log('AppUsageTracker: Starting tracking');
      const result = await AppUsageTracker.startTracking();
      this.isTracking = result.value;
      console.log('AppUsageTracker: Tracking started, result:', result.value);
      return result.value;
    } catch (error) {
      console.error('Error starting app usage tracking:', error);
      return false;
    }
  }

  /**
   * Stop tracking app usage
   */
  public async stopTracking(): Promise<boolean> {
    try {
      const result = await AppUsageTracker.stopTracking();
      this.isTracking = false;
      return result.value;
    } catch (error) {
      console.error('Error stopping app usage tracking:', error);
      return false;
    }
  }

  /**
   * Get app usage data for a specific time range
   */
  public async getAppUsageData(startTime?: number, endTime?: number): Promise<AppUsage[]> {
    try {
      // Set default time range if not provided (last 24 hours)
      const now = Date.now();
      const start = startTime || (now - 24 * 60 * 60 * 1000);
      const end = endTime || now;
      
      console.log(`AppUsageTracker: Getting app usage data from ${new Date(start).toLocaleString()} to ${new Date(end).toLocaleString()}`);
      
      // First check if we have permission
      const hasPermission = await this.hasUsagePermission();
      if (!hasPermission) {
        console.warn('AppUsageTracker: Usage stats permission not granted');
        
        // Try to get data from localStorage as fallback
        try {
          const cachedData = localStorage.getItem('lastAppUsageData');
          if (cachedData) {
            console.log('AppUsageTracker: Using cached data from localStorage');
            return JSON.parse(cachedData);
          }
        } catch (cacheError) {
          console.error('Error reading cached app usage data:', cacheError);
        }
        
        return [];
      }
      
      // Get the data from the native layer
      console.log('AppUsageTracker: Calling native getAppUsageData method');
      const result = await AppUsageTracker.getAppUsageData({ 
        startTime: start, 
        endTime: end 
      });
      
      console.log('AppUsageTracker: Raw result from native layer:', result);
      
      if (!result || !result.data) {
        console.log('AppUsageTracker: No data returned from native layer');
        return [];
      }
      
      // Parse the JSON string into an array of objects
      let rawData;
      try {
        rawData = JSON.parse(result.data);
        console.log(`AppUsageTracker: Received ${rawData.length} app usage records from native layer`);
      } catch (parseError) {
        console.error('AppUsageTracker: Error parsing JSON data:', parseError);
        console.log('AppUsageTracker: Raw data that failed to parse:', result.data);
        return [];
      }
      
      // Log all data for debugging
      console.log('AppUsageTracker: Full data:', rawData);
      
      // Convert the raw data to AppUsage objects
      const appUsageData = this.convertToAppUsage(rawData);
      console.log(`AppUsageTracker: Converted ${appUsageData.length} app usage records`);
      
      // Store the data in localStorage for offline access
      try {
        localStorage.setItem('lastAppUsageData', JSON.stringify(appUsageData));
        localStorage.setItem('lastAppUsageDataTimestamp', Date.now().toString());
      } catch (storageError) {
        console.error('Error storing app usage data in localStorage:', storageError);
      }
      
      return appUsageData;
    } catch (error) {
      console.error('Error getting app usage data:', error);
      
      // Try to get data from localStorage as fallback
      try {
        const cachedData = localStorage.getItem('lastAppUsageData');
        if (cachedData) {
          console.log('AppUsageTracker: Using cached data from localStorage');
          return JSON.parse(cachedData);
        }
      } catch (cacheError) {
        console.error('Error reading cached app usage data:', cacheError);
      }
      
      return [];
    }
  }

  /**
   * Get app usage data for the last hour
   */
  public async getLastHourUsage(): Promise<AppUsage[]> {
    const now = Date.now();
    const oneHourAgo = now - (60 * 60 * 1000); // 1 hour in milliseconds
    console.log(`AppUsageTracker: Getting last hour usage (${new Date(oneHourAgo).toLocaleTimeString()} to ${new Date(now).toLocaleTimeString()})`);
    
    // Get the data
    const data = await this.getAppUsageData(oneHourAgo, now);
    
    // Filter out apps with very little usage (less than 5 seconds)
    // This helps eliminate background processes that weren't actually used
    return data.filter(app => app.time > 0.08); // 0.08 minutes = ~5 seconds
  }

  /**
   * Convert raw data from the native layer to AppUsage objects
   */
  private convertToAppUsage(rawData: any[]): AppUsage[] {
    try {
      if (!Array.isArray(rawData) || rawData.length === 0) {
        console.log('AppUsageTracker: No raw data to convert');
        return [];
      }
      
      return rawData.map(item => {
        // Extract proper app name from the data
        // The Java code might send the app name in different formats
        let appName = 'Unknown App';
        
        // If we have a proper name field, use it
        if (item.name && typeof item.name === 'string') {
          // Check if the name contains package name (com.example.app)
          if (item.name.includes('.')) {
            // Try to extract the app name from the package
            const nameParts = item.name.split('.');
            // Use the last part of the package name as a fallback
            const lastPart = nameParts[nameParts.length - 1];
            
            // Capitalize the first letter and format the rest
            appName = lastPart.charAt(0).toUpperCase() + lastPart.slice(1);
          } else {
            // If it doesn't look like a package name, use it directly
            appName = item.name;
          }
        } else if (item.packageName && typeof item.packageName === 'string') {
          // If we only have packageName, extract a readable name from it
          const nameParts = item.packageName.split('.');
          const lastPart = nameParts[nameParts.length - 1];
          appName = lastPart.charAt(0).toUpperCase() + lastPart.slice(1);
        }
        
        // Generate a consistent color for this app based on its name or category
        const color = item.category ? 
          this.getCategoryColor(item.category) : 
          this.getColorForApp(appName);
        
        // Create the AppUsage object
        const appUsage: AppUsage = {
          name: appName,
          time: item.time || 0,
          color,
          lastUsed: item.lastUsed ? new Date(item.lastUsed) : undefined,
          category: item.category || this.getCategoryForApp(appName),
          isActive: item.isActive || false
        };
        
        return appUsage;
      });
    } catch (error) {
      console.error('Error converting app usage data:', error);
      return [];
    }
  }

  /**
   * Get a color for a category
   */
  private getCategoryColor(category: string): string {
    switch (category) {
      case 'Social Media': return '#FF00FF';
      case 'Entertainment': return '#FF5733';
      case 'Productivity': return '#33FF57';
      case 'Games': return '#00FFFF';
      case 'Education': return '#3357FF';
      default: return '#A833FF';
    }
  }

  /**
   * Generate a color for an app based on its name
   */
  private getColorForApp(appName: string): string {
    // Simple hash function to generate a consistent color
    let hash = 0;
    for (let i = 0; i < appName.length; i++) {
      hash = appName.charCodeAt(i) + ((hash << 5) - hash);
    }
    
    // Convert to hex color
    let color = '#';
    for (let i = 0; i < 3; i++) {
      const value = (hash >> (i * 8)) & 0xFF;
      color += ('00' + value.toString(16)).substr(-2);
    }
    
    return color;
  }

  /**
   * Determine category for an app based on its name
   */
  private getCategoryForApp(appName: string): string {
    const appNameLower = appName.toLowerCase();
    
    if (/instagram|facebook|twitter|tiktok|snapchat|whatsapp|telegram|messenger/i.test(appNameLower)) {
      return 'Social Media';
    } else if (/youtube|netflix|hulu|disney|spotify|music|video|player|movie/i.test(appNameLower)) {
      return 'Entertainment';
    } else if (/chrome|safari|firefox|edge|browser|gmail|outlook|office|word|excel|powerpoint|docs/i.test(appNameLower)) {
      return 'Productivity';
    } else if (/game|games|gaming|play|puzzle|candy|clash|craft/i.test(appNameLower)) {
      return 'Games';
    } else if (/learn|education|school|course|study|duolingo|khan|quiz/i.test(appNameLower)) {
      return 'Education';
    } else {
      return 'Other';
    }
  }

  /**
   * Add a listener for app usage updates
   */
  public addListener(callback: (usageData: AppUsage[]) => void): () => void {
    this.listeners.push(callback);
    return () => {
      this.listeners = this.listeners.filter(listener => listener !== callback);
    };
  }

  /**
   * Remove all listeners
   */
  public removeAllListeners(): void {
    this.listeners = [];
    AppUsageTracker.removeAllListeners();
  }

  /**
   * Notify all listeners of app usage updates
   */
  private notifyListeners(usageData: AppUsage[]): void {
    this.listeners.forEach(listener => {
      try {
        listener(usageData);
      } catch (error) {
        console.error('Error in app usage listener:', error);
      }
    });
  }

  /**
   * Check if tracking is active
   */
  public isTrackingActive(): boolean {
    return this.isTracking;
  }

  /**
   * Check if the app is exempt from battery optimization
   */
  public async isBatteryOptimizationExempt(): Promise<boolean> {
    try {
      console.log('AppUsageTracker: Checking battery optimization exemption');
      const result = await AppUsageTracker.isBatteryOptimizationExempt();
      console.log('AppUsageTracker: Battery optimization exemption result:', result.value);
      return result.value;
    } catch (error) {
      console.error('Error checking battery optimization exemption:', error);
      return false;
    }
  }

  /**
   * Request exemption from battery optimization
   */
  public async requestBatteryOptimizationExemption(): Promise<void> {
    try {
      console.log('AppUsageTracker: Requesting battery optimization exemption');
      await AppUsageTracker.requestBatteryOptimizationExemption();
      console.log('AppUsageTracker: Battery optimization exemption request completed');
    } catch (error) {
      console.error('Error requesting battery optimization exemption:', error);
      throw error;
    }
  }

  public async checkScreenTimeLimit(params: {
    totalTime: number;
    limit: number;
    remainingMinutes: number;
  }): Promise<boolean> {
    try {
      const result = await this.plugin.checkScreenTimeLimit(params);
      return result.value;
    } catch (error) {
      console.error('Error checking screen time limit:', error);
      throw error;
    }
  }
} 