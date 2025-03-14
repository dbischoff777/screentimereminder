import type { CapacitorConfig } from '@capacitor/cli';

const config: CapacitorConfig = {
  appId: 'com.screentimereminder.app',
  appName: 'Screen Time Reminder',
  webDir: 'dist',
  plugins: {
    LocalNotifications: {
      smallIcon: "ic_stat_icon_config_sample",
      iconColor: "#FF00FF",
      sound: "beep.wav",
    },
    BackgroundMode: {
      enable: true,
      title: "Screen Time Reminder",
      text: "Tracking your screen time",
      icon: "notification_icon",
      color: "#000020",
      resume: true,
      hidden: false,
      disableWebViewOptimizations: false,
      moveToBackground: false,
      overrideUserActivity: false,
      allowBluetooth: false,
      disableBatteryOptimizations: false
    },
    Notification: {
      sound: "beep.wav",
      smallIcon: "ic_stat_icon_config_sample",
      iconColor: "#FF00FF"
    },
    AppUsageTracker: {
      enabled: true
    }
  },
  android: {
    backgroundColor: "#000020",
    allowMixedContent: true,
    captureInput: true,
    webContentsDebuggingEnabled: true
  },
  server: {
    cleartext: true,
    hostname: "localhost"
  },
  cordova: {
    preferences: {
      "BackgroundMode": "true",
      "BackgroundModeEnable": "true",
      "KeepRunning": "true",
      "AllowInlineMediaPlayback": "true",
      "LoadUrlTimeoutValue": "60000",
      "android-minSdkVersion": "23",
      "android-targetSdkVersion": "33"
    }
  }
};

export default config;
