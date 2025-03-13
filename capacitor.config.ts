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
      text: "Tracking your screen time in background",
      icon: "notification_icon",
      color: "#000020",
      resume: true,
      hidden: false,
      disableWebViewOptimizations: true,
      moveToBackground: false,
      overrideUserActivity: true,
      allowBluetooth: true,
      disableBatteryOptimizations: true
    },
    Notification: {
      sound: "beep.wav",
      smallIcon: "ic_stat_icon_config_sample",
      iconColor: "#FF00FF"
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
      "android-minSdkVersion": "21",
      "android-targetSdkVersion": "33"
    }
  }
};

export default config;
