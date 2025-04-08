package com.example.screentimereminder;

import android.app.PendingIntent;
import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProvider;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.util.Log;
import android.widget.RemoteViews;
import org.json.JSONObject;

public class ScreenTimeWidgetProvider extends AppWidgetProvider {
    private static final String TAG = "ScreenTimeWidget";
    private static final String WIDGET_DATA_KEY = "widget_data";
    private static final String REFRESH_ACTION = "com.example.screentimereminder.REFRESH_WIDGET";
    private static final String UPDATE_ACTION = "com.example.screentimereminder.UPDATE_WIDGET";
    
    // Resource IDs
    private static final int LAYOUT_WIDGET = 0x7f0b0001;
    private static final int ID_SCREEN_TIME_TEXT = 0x7f0a0001;
    private static final int ID_LIMIT_TEXT = 0x7f0a0002;
    private static final int ID_REFRESH_BUTTON = 0x7f0a0003;

    @Override
    public void onUpdate(Context context, AppWidgetManager appWidgetManager, int[] appWidgetIds) {
        for (int appWidgetId : appWidgetIds) {
            updateAppWidget(context, appWidgetManager, appWidgetId);
        }
    }

    @Override
    public void onReceive(Context context, Intent intent) {
        super.onReceive(context, intent);
        Log.d(TAG, "Received action: " + intent.getAction());
        
        if (REFRESH_ACTION.equals(intent.getAction())) {
            Log.d(TAG, "Handling refresh action");
            AppWidgetManager appWidgetManager = AppWidgetManager.getInstance(context);
            int[] appWidgetIds = appWidgetManager.getAppWidgetIds(
                new android.content.ComponentName(context, ScreenTimeWidgetProvider.class));
            onUpdate(context, appWidgetManager, appWidgetIds);
        } else if (UPDATE_ACTION.equals(intent.getAction()) && intent.hasExtra("widget_data")) {
            try {
                String data = intent.getStringExtra("widget_data");
                Log.d(TAG, "Received widget data: " + data);
                JSONObject jsonData = new JSONObject(data);
                
                // Store the data in SharedPreferences
                SharedPreferences prefs = context.getSharedPreferences(WIDGET_DATA_KEY, Context.MODE_PRIVATE);
                prefs.edit().putString(WIDGET_DATA_KEY, jsonData.toString()).apply();
                
                // Update all widgets
                AppWidgetManager appWidgetManager = AppWidgetManager.getInstance(context);
                int[] appWidgetIds = appWidgetManager.getAppWidgetIds(
                    new android.content.ComponentName(context, ScreenTimeWidgetProvider.class));
                onUpdate(context, appWidgetManager, appWidgetIds);
            } catch (Exception e) {
                Log.e(TAG, "Error updating widget data", e);
            }
        }
    }

    static void updateAppWidget(Context context, AppWidgetManager appWidgetManager, int appWidgetId) {
        try {
            RemoteViews views = new RemoteViews(context.getPackageName(), LAYOUT_WIDGET);
            
            // Set up the refresh button click intent
            Intent refreshIntent = new Intent(context, ScreenTimeWidgetProvider.class);
            refreshIntent.setAction(REFRESH_ACTION);
            PendingIntent refreshPendingIntent = PendingIntent.getBroadcast(
                context, 0, refreshIntent, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
            views.setOnClickPendingIntent(ID_REFRESH_BUTTON, refreshPendingIntent);
            
            // Set up the widget click intent to open the app
            Intent appIntent = context.getPackageManager().getLaunchIntentForPackage(context.getPackageName());
            PendingIntent appPendingIntent = PendingIntent.getActivity(
                context, 0, appIntent, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
            views.setOnClickPendingIntent(ID_SCREEN_TIME_TEXT, appPendingIntent);
            
            // Get and display the widget data
            SharedPreferences prefs = context.getSharedPreferences(WIDGET_DATA_KEY, Context.MODE_PRIVATE);
            String data = prefs.getString(WIDGET_DATA_KEY, "{\"totalScreenTime\":0,\"screenTimeLimit\":0}");
            JSONObject json = new JSONObject(data);
            
            int screenTime = json.optInt("totalScreenTime", 0);
            int limit = json.optInt("screenTimeLimit", 0);
            
            Log.d(TAG, "Updating widget with screenTime: " + screenTime + ", limit: " + limit);
            
            views.setTextViewText(ID_SCREEN_TIME_TEXT, "Screen Time: " + screenTime + " min");
            views.setTextViewText(ID_LIMIT_TEXT, "Limit: " + limit + " min");
            
            // Update the widget
            appWidgetManager.updateAppWidget(appWidgetId, views);
            Log.d(TAG, "Widget updated successfully");
        } catch (Exception e) {
            Log.e(TAG, "Error updating widget UI", e);
        }
    }
} 