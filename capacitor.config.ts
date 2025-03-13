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
      disableWebViewOptimizations: true
    }
  },
  android: {
    backgroundColor: "#000020"
  }
};

export default config;
