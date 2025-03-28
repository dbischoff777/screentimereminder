package com.screentimereminder.app;

import android.content.Intent;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import com.getcapacitor.JSObject;
import com.getcapacitor.Plugin;
import com.getcapacitor.PluginCall;
import com.getcapacitor.PluginMethod;
import com.getcapacitor.annotation.CapacitorPlugin;

@CapacitorPlugin(name = "BackgroundMode")
public class BackgroundModePlugin extends Plugin {
    private static final String TAG = "BackgroundModePlugin";
    private BackgroundService backgroundService;
    private Handler mainHandler;
    private long startTime;
    private boolean isTracking = false;

    @Override
    public void load() {
        try {
            mainHandler = new Handler(Looper.getMainLooper());
            startTime = System.currentTimeMillis();
            Log.d(TAG, "Plugin loaded");
        } catch (Exception e) {
            Log.e(TAG, "Error in plugin load", e);
        }
    }

    @PluginMethod
    public void enable(PluginCall call) {
        try {
            Log.d(TAG, "Enabling background mode");
            Intent serviceIntent = new Intent(getContext(), BackgroundService.class);
            
            // Handle different Android versions
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                // Android 8.0 and above
                getContext().startForegroundService(serviceIntent);
            } else {
                // Android 7.1 and below
                getContext().startService(serviceIntent);
            }
            
            // Wait a bit to ensure service is started
            mainHandler.postDelayed(() -> {
                boolean isEnabled = BackgroundService.isRunning();
                Log.d(TAG, "Background mode status after enable: " + isEnabled);
                if (!isEnabled) {
                    // Try to start service again if it failed
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                        getContext().startForegroundService(serviceIntent);
                    } else {
                        getContext().startService(serviceIntent);
                    }
                }
            }, 1000);
            
            call.resolve(new JSObject().put("value", true));
        } catch (Exception e) {
            Log.e(TAG, "Failed to enable background mode", e);
            call.reject("Failed to enable background mode", e);
        }
    }

    @PluginMethod
    public void disable(PluginCall call) {
        try {
            Log.d(TAG, "Disabling background mode");
            Intent serviceIntent = new Intent(getContext(), BackgroundService.class);
            getContext().stopService(serviceIntent);
            
            // Wait a bit to ensure service is stopped
            mainHandler.postDelayed(() -> {
                boolean isEnabled = BackgroundService.isRunning();
                Log.d(TAG, "Background mode status after disable: " + isEnabled);
                if (isEnabled) {
                    // Try to stop service again if it failed
                    getContext().stopService(serviceIntent);
                }
            }, 1000);
            
            call.resolve(new JSObject().put("value", true));
        } catch (Exception e) {
            Log.e(TAG, "Failed to disable background mode", e);
            call.reject("Failed to disable background mode", e);
        }
    }

    @PluginMethod
    public void isEnabled(PluginCall call) {
        try {
            boolean isEnabled = BackgroundService.isRunning();
            Log.d(TAG, "Background mode status: " + isEnabled);
            call.resolve(new JSObject().put("value", isEnabled));
        } catch (Exception e) {
            Log.e(TAG, "Failed to check background mode status", e);
            call.reject("Failed to check background mode status", e);
        }
    }

    @PluginMethod
    public void startTracking(PluginCall call) {
        try {
            Log.d(TAG, "Starting time tracking");
            isTracking = true;
            startTime = System.currentTimeMillis();
            call.resolve(new JSObject().put("value", true));
        } catch (Exception e) {
            Log.e(TAG, "Failed to start tracking", e);
            call.reject("Failed to start tracking", e);
        }
    }

    @PluginMethod
    public void stopTracking(PluginCall call) {
        try {
            Log.d(TAG, "Stopping time tracking");
            isTracking = false;
            call.resolve(new JSObject().put("value", true));
        } catch (Exception e) {
            Log.e(TAG, "Failed to stop tracking", e);
            call.reject("Failed to stop tracking", e);
        }
    }

    @PluginMethod
    public void getCurrentTime(PluginCall call) {
        try {
            if (!isTracking) {
                Log.d(TAG, "Tracking not active, returning 0");
                call.resolve(new JSObject().put("value", 0));
                return;
            }

            long currentTime = System.currentTimeMillis();
            long elapsedTime = currentTime - startTime;
            
            Log.d(TAG, String.format("Time calculation: current=%d, start=%d, elapsed=%d", 
                currentTime, startTime, elapsedTime));
                
            call.resolve(new JSObject().put("value", elapsedTime));
        } catch (Exception e) {
            Log.e(TAG, "Failed to get current time", e);
            call.reject("Failed to get current time", e);
        }
    }

    @PluginMethod
    public void resetTime(PluginCall call) {
        try {
            Log.d(TAG, "Resetting time tracking");
            startTime = System.currentTimeMillis();
            call.resolve(new JSObject().put("value", true));
        } catch (Exception e) {
            Log.e(TAG, "Failed to reset time", e);
            call.reject("Failed to reset time", e);
        }
    }
} 