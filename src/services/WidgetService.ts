import { Preferences } from '@capacitor/preferences';
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
    private static readonly WIDGET_DATA_KEY = 'widget_data';
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

    private getUpdateEventName(): string {
        return WidgetService.UPDATE_EVENT;
    }

    private handleWidgetUpdate(event: CustomEvent): void {
        try {
            const data = event.detail?.usageData;
            if (data) {
                console.log('WidgetService: Received widget update event:', data);
                this.storeWidgetData(JSON.parse(data));
            }
        } catch (error) {
            console.error('WidgetService: Error handling widget update event:', error);
        }
    }

    private async storeWidgetData(data: WidgetData): Promise<void> {
        try {
            await Preferences.set({
                key: WidgetService.WIDGET_DATA_KEY,
                value: JSON.stringify(data)
            });
            console.log('WidgetService: Successfully stored data:', data);
        } catch (error) {
            console.error('WidgetService: Error storing data:', error);
        }
    }

    public async updateWidgetData(screenTime: number, limit: number): Promise<void> {
        console.log('WidgetService: Starting updateWidgetData with values:', { screenTime, limit });
        
        const data: WidgetData = {
            totalScreenTime: Math.round(screenTime), // Ensure we send integers
            screenTimeLimit: Math.round(limit),
            timestamp: Date.now()
        };

        // Store in preferences
        await this.storeWidgetData(data);

        // Send to native code
        if (Capacitor.isNativePlatform()) {
            try {
                const jsonData = JSON.stringify(data);
                
                // Try direct JavaScript interface first
                if (window.Android?.updateWidget) {
                    console.log('WidgetService: Using Android.updateWidget');
                    window.Android.updateWidget(jsonData);
                } else {
                    // Fallback to event dispatch
                    console.log('WidgetService: Using event dispatch');
                    const event = new CustomEvent(this.getUpdateEventName(), {
                        detail: {
                            usageData: jsonData
                        }
                    });
                    window.dispatchEvent(event);
                }
                console.log('WidgetService: Widget update sent successfully');
            } catch (error) {
                console.error('WidgetService: Error updating widget:', error);
            }
        } else {
            console.log('WidgetService: Not running on native platform, skipping native update');
        }
    }

    public async getWidgetData(): Promise<WidgetData | null> {
        try {
            const { value } = await Preferences.get({ key: WidgetService.WIDGET_DATA_KEY });
            if (!value) {
                console.log('WidgetService: No stored data found');
                return null;
            }
            const data = JSON.parse(value) as WidgetData;
            console.log('WidgetService: Retrieved data:', data);
            return data;
        } catch (error) {
            console.error('WidgetService: Error getting widget data:', error);
            return null;
        }
    }
} 