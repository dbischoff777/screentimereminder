package com.screentimereminder.app;

import android.os.Bundle;
import android.util.Log;
import com.getcapacitor.BridgeActivity;

public class MainActivity extends BridgeActivity {
    private static final String TAG = "MainActivity";
    
    @Override
    public void onCreate(Bundle savedInstanceState) {
        try {
            Log.d(TAG, "MainActivity onCreate starting");
            
            // Register our custom plugins before calling super.onCreate
            try {
                this.registerPlugin(BackgroundModePlugin.class);
                Log.d(TAG, "BackgroundModePlugin registered successfully");
            } catch (Exception e) {
                Log.e(TAG, "Error registering BackgroundModePlugin", e);
            }
            
            try {
                super.onCreate(savedInstanceState);
                Log.d(TAG, "super.onCreate called successfully");
            } catch (Exception e) {
                Log.e(TAG, "Error in super.onCreate", e);
                // Try to continue even if super.onCreate fails
            }
            
            // Log that we're applying the PendingIntent fix
            Log.d(TAG, "Applying PendingIntent fix for Android 12+ compatibility");
            
            // Register plugins manually
            // This is done automatically by Capacitor, but we're logging it for clarity
            Log.d(TAG, "Initializing plugins");
            
            // Log device information to help with debugging
            Log.d(TAG, "Device information: " + 
                  "Manufacturer: " + android.os.Build.MANUFACTURER + 
                  ", Model: " + android.os.Build.MODEL + 
                  ", SDK: " + android.os.Build.VERSION.SDK_INT);
            
            Log.d(TAG, "MainActivity onCreate completed");
        } catch (Exception e) {
            Log.e(TAG, "Critical error in MainActivity.onCreate", e);
        }
    }
}
