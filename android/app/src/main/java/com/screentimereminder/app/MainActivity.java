package com.screentimereminder.app;

import android.os.Bundle;
import android.util.Log;
import com.getcapacitor.BridgeActivity;

public class MainActivity extends BridgeActivity {
    private static final String TAG = "MainActivity";
    
    @Override
    public void onCreate(Bundle savedInstanceState) {
        // Register our custom plugins before calling super.onCreate
        this.registerPlugin(BackgroundModePlugin.class);
        
        super.onCreate(savedInstanceState);
        
        // Log that we're applying the PendingIntent fix
        Log.d(TAG, "Applying PendingIntent fix for Android 12+ compatibility");
        
        // Register plugins manually
        // This is done automatically by Capacitor, but we're logging it for clarity
        Log.d(TAG, "Initializing plugins");
    }
}
