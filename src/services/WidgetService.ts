import { Capacitor } from '@capacitor/core';

// Add type declaration for Android interface and custom event
declare global {
    interface Window {
        Android?: {
            updateWidget: (data: string) => void;
        };
    }

    interface WindowEventMap {
        'com.screentimereminder.app.APP_USAGE_UPDATE': CustomEvent<{ usageData: string }>;
    }
}

interface WidgetData {
    totalScreenTime: number;
    screenTimeLimit: number;
    timestamp: number;
}

export class WidgetService {
    private static instance: WidgetService;
    private static readonly UPDATE_EVENT = 'com.screentimereminder.app.APP_USAGE_UPDATE';

    private constructor() {
        // Initialize event listener for widget updates
        if (Capacitor.isNativePlatform()) {
            window.addEventListener(
                WidgetService.UPDATE_EVENT,
                this.handleWidgetUpdate.bind(this) as EventListener
            );
        }
    }

    public static getInstance(): WidgetService {
        if (!WidgetService.instance) {
            WidgetService.instance = new WidgetService();
        }
        return WidgetService.instance;
    }

    private handleWidgetUpdate(event: CustomEvent): void {
        try {
            const data = event.detail?.usageData;
            if (data) {
                console.log('WidgetService: Received widget update event:', data);
                // We no longer store the data, just trigger a widget refresh
                this.triggerWidgetRefresh();
            }
        } catch (error) {
            console.error('WidgetService: Error handling widget update event:', error);
        }
    }

    private async triggerWidgetRefresh(): Promise<void> {
        if (Capacitor.isNativePlatform() && window.Android?.updateWidget) {
            try {
                // Send an empty update to trigger the widget to refresh from AppUsageTracker
                const refreshTrigger = JSON.stringify({
                    action: 'REFRESH_WIDGET',
                    timestamp: Date.now()
                });
                window.Android.updateWidget(refreshTrigger);
                console.log('WidgetService: Triggered widget refresh');
            } catch (error) {
                console.error('WidgetService: Error triggering widget refresh:', error);
            }
        }
    }

    public async updateWidgetData(screenTime: number, limit: number): Promise<void> {
        console.log('WidgetService: Starting updateWidgetData with values:', { screenTime, limit });
        
        // Simply trigger a widget refresh - the widget will get data from AppUsageTracker
        await this.triggerWidgetRefresh();
    }

    public async getWidgetData(): Promise<WidgetData | null> {
        // This method is kept for backward compatibility
        // but now just triggers a refresh
        await this.triggerWidgetRefresh();
        return null;
    }
} 