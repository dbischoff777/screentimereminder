package com.screentimereminder.app;

import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.webkit.JavascriptInterface;
import com.getcapacitor.BridgeActivity;
import com.getcapacitor.Plugin;
import java.util.ArrayList;
import java.util.List;
import org.json.JSONObject;

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
                
                // Register the AppUsageTracker plugin
                this.registerPlugin(AppUsageTracker.class);
                Log.d(TAG, "AppUsageTracker plugin registered successfully");
            } catch (Exception e) {
                Log.e(TAG, "Error registering plugins", e);
            }
            
            try {
                super.onCreate(savedInstanceState);
                Log.d(TAG, "super.onCreate called successfully");
            } catch (Exception e) {
                Log.e(TAG, "Error in super.onCreate", e);
            }
            
            // Add JavaScript interface for widget updates
            this.bridge.getWebView().addJavascriptInterface(new Object() {
                @JavascriptInterface
                public void updateWidget(String data) {
                    try {
                        Log.d(TAG, "Received widget update data: " + data);
                        JSONObject jsonData = new JSONObject(data);
                        Log.d(TAG, "Parsed JSON data: " + jsonData.toString());
                        
                        // Store in SharedPreferences
                        android.content.SharedPreferences prefs = getSharedPreferences("widget_data", MODE_PRIVATE);
                        prefs.edit().putString("widget_data", jsonData.toString()).apply();
                        Log.d(TAG, "Stored data in SharedPreferences");
                        
                        // Broadcast the update
                        Intent intent = new Intent("com.screentimereminder.app.APP_USAGE_UPDATE");
                        intent.putExtra("usageData", jsonData.toString());
                        Log.d(TAG, "Sending broadcast with action: " + intent.getAction());
                        sendBroadcast(intent);
                    } catch (Exception e) {
                        Log.e(TAG, "Error updating widget", e);
                    }
                }
            }, "Android");
            
            Log.d(TAG, "MainActivity onCreate completed");
        } catch (Exception e) {
            Log.e(TAG, "Critical error in MainActivity.onCreate", e);
        }
    }

    private void registerPlugins() {
        List<Class<? extends Plugin>> plugins = new ArrayList<>();
        registerPlugins(plugins);
    }
}
