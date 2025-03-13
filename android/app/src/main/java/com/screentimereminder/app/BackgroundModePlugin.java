package com.screentimereminder.app;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.Build;
import android.os.IBinder;
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
        Log.d(TAG, "Enabling background mode");
        
        try {
            Context context = getContext();
            Intent serviceIntent = new Intent(context, BackgroundService.class);
            
            // Start the service
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(serviceIntent);
            } else {
                context.startService(serviceIntent);
            }
            
            // Bind to the service if not already bound
            if (!isBound) {
                bindBackgroundService();
            }
            
            JSObject ret = new JSObject();
            ret.put("value", true);
            call.resolve(ret);
        } catch (Exception e) {
            Log.e(TAG, "Error enabling background mode", e);
            call.reject("Error enabling background mode: " + e.getMessage(), e);
        }
    }
    
    @PluginMethod
    public void disable(PluginCall call) {
        Log.d(TAG, "Disabling background mode");
        
        try {
            // Unbind from the service
            if (isBound) {
                getContext().unbindService(serviceConnection);
                isBound = false;
            }
            
            // Stop the service
            getContext().stopService(new Intent(getContext(), BackgroundService.class));
            
            JSObject ret = new JSObject();
            ret.put("value", true);
            call.resolve(ret);
        } catch (Exception e) {
            Log.e(TAG, "Error disabling background mode", e);
            call.reject("Error disabling background mode: " + e.getMessage(), e);
        }
    }
    
    @PluginMethod
    public void isEnabled(PluginCall call) {
        Log.d(TAG, "Checking if background mode is enabled");
        
        JSObject ret = new JSObject();
        ret.put("value", isBound && backgroundService != null && backgroundService.isRunning());
        call.resolve(ret);
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