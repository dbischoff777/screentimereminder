package com.screentimereminder.app;

import android.content.Context;
import android.content.Intent;
import android.util.Log;
import android.webkit.JavascriptInterface;

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
            
            // Create and send broadcast intent
            Intent intent = new Intent("com.screentimereminder.app.APP_USAGE_UPDATE");
            intent.putExtra("usageData", data);
            context.sendBroadcast(intent);
            
            Log.d(TAG, "Broadcast sent successfully");
        } catch (Exception e) {
            Log.e(TAG, "Error updating widget from JavaScript", e);
        }
    }
} 