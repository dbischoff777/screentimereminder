package com.screentimereminder.app;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.net.Uri;
import android.os.Build;
import android.os.IBinder;
import android.os.PowerManager;
import android.provider.Settings;
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
    private boolean isBound = false;
    
    // Service connection object
    private final ServiceConnection serviceConnection = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName name, IBinder service) {
            BackgroundService.LocalBinder binder = (BackgroundService.LocalBinder) service;
            backgroundService = binder.getService();
            isBound = true;
            Log.d(TAG, "Service connected");
        }
        
        @Override
        public void onServiceDisconnected(ComponentName name) {
            isBound = false;
            Log.d(TAG, "Service disconnected");
        }
    };
    
    @Override
    public void load() {
        super.load();
        
        // Bind to the background service if it's already running
        bindBackgroundService();
    }
    
    @PluginMethod
    public void enable(PluginCall call) {
        try {
            // Start the background service
            Intent serviceIntent = new Intent(getContext(), BackgroundService.class);
            
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                getContext().startForegroundService(serviceIntent);
            } else {
                getContext().startService(serviceIntent);
            }
            
            // Bind to the service
            bindBackgroundService();
            
            // Request to ignore battery optimizations
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                Intent intent = new Intent();
                String packageName = getContext().getPackageName();
                PowerManager pm = (PowerManager) getContext().getSystemService(Context.POWER_SERVICE);
                if (!pm.isIgnoringBatteryOptimizations(packageName)) {
                    intent.setAction(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS);
                    intent.setData(Uri.parse("package:" + packageName));
                    getContext().startActivity(intent);
                }
            }
            
            JSObject result = new JSObject();
            result.put("value", true);
            call.resolve(result);
        } catch (Exception e) {
            Log.e(TAG, "Error enabling background mode", e);
            call.reject("Failed to enable background mode", e);
        }
    }
    
    @PluginMethod
    public void disable(PluginCall call) {
        try {
            // Unbind from the service
            if (isBound) {
                getContext().unbindService(serviceConnection);
                isBound = false;
            }
            
            // Stop the service
            Intent serviceIntent = new Intent(getContext(), BackgroundService.class);
            getContext().stopService(serviceIntent);
            
            JSObject result = new JSObject();
            result.put("value", true);
            call.resolve(result);
        } catch (Exception e) {
            Log.e(TAG, "Error disabling background mode", e);
            call.reject("Failed to disable background mode", e);
        }
    }
    
    @PluginMethod
    public void isEnabled(PluginCall call) {
        try {
            boolean isEnabled = isBound && backgroundService != null && backgroundService.isRunning();
            JSObject result = new JSObject();
            result.put("value", isEnabled);
            call.resolve(result);
        } catch (Exception e) {
            Log.e(TAG, "Error checking background mode status", e);
            call.reject("Failed to check background mode status", e);
        }
    }
    
    /**
     * Bind to the background service
     */
    private void bindBackgroundService() {
        try {
            Intent serviceIntent = new Intent(getContext(), BackgroundService.class);
            getContext().bindService(serviceIntent, serviceConnection, Context.BIND_AUTO_CREATE);
        } catch (Exception e) {
            Log.e(TAG, "Error binding to background service", e);
        }
    }
    
    @Override
    protected void handleOnDestroy() {
        // Unbind from the service when the plugin is destroyed
        if (isBound) {
            getContext().unbindService(serviceConnection);
            isBound = false;
        }
        
        super.handleOnDestroy();
    }
} 