package com.screentimereminder.app;

import android.content.Context;
import android.content.Intent;
import android.util.Log;
import android.webkit.JavascriptInterface;
import org.json.JSONObject;

public class WidgetJavaScriptInterface {
    private static final String TAG = "WidgetJSInterface";
    private final Context context;

    public WidgetJavaScriptInterface(Context context) {
        this.context = context;
    }

    @JavascriptInterface
    public void updateWidget(String data) {
        try {
            Log.d(TAG, "Received widget update request with data: " + data);
            
            // Parse the incoming data to ensure it's valid JSON
            JSONObject jsonData = new JSONObject(data);
            
            // Create and send broadcast intent
            Intent intent = new Intent("com.screentimereminder.app.APP_USAGE_UPDATE");
            intent.putExtra("usageData", jsonData.toString());
            context.sendBroadcast(intent);
            
            Log.d(TAG, "Broadcast sent successfully with data: " + jsonData.toString());
        } catch (Exception e) {
            Log.e(TAG, "Error updating widget from JavaScript", e);
        }
    }
} 