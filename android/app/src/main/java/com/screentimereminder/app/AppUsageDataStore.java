package com.screentimereminder.app;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Log;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class AppUsageDataStore {
    private static final String TAG = "AppUsageDataStore";
    private static final String PREFS_NAME = "AppUsageData";
    private static final String KEY_APP_USAGE = "app_usage";
    
    private final SharedPreferences prefs;
    private final Map<String, Long> appUsageTimes;
    
    public AppUsageDataStore(Context context) {
        prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        appUsageTimes = new HashMap<>();
        loadData();
    }
    
    private void loadData() {
        try {
            String jsonString = prefs.getString(KEY_APP_USAGE, "[]");
            JSONArray jsonArray = new JSONArray(jsonString);
            
            for (int i = 0; i < jsonArray.length(); i++) {
                JSONObject obj = jsonArray.getJSONObject(i);
                String packageName = obj.getString("packageName");
                long timeInForeground = obj.getLong("timeInForeground");
                appUsageTimes.put(packageName, timeInForeground);
            }
            
            Log.d(TAG, "Loaded " + appUsageTimes.size() + " app usage records");
        } catch (JSONException e) {
            Log.e(TAG, "Error loading app usage data", e);
        }
    }
    
    public void saveData() {
        try {
            JSONArray jsonArray = new JSONArray();
            for (Map.Entry<String, Long> entry : appUsageTimes.entrySet()) {
                JSONObject obj = new JSONObject();
                obj.put("packageName", entry.getKey());
                obj.put("timeInForeground", entry.getValue());
                jsonArray.put(obj);
            }
            
            prefs.edit().putString(KEY_APP_USAGE, jsonArray.toString()).apply();
            Log.d(TAG, "Saved " + appUsageTimes.size() + " app usage records");
        } catch (JSONException e) {
            Log.e(TAG, "Error saving app usage data", e);
        }
    }
    
    public void updateAppUsage(String packageName, long timeInForeground) {
        appUsageTimes.put(packageName, timeInForeground);
        saveData();
    }
    
    public long getAppUsage(String packageName) {
        return appUsageTimes.getOrDefault(packageName, 0L);
    }
    
    public Map<String, Long> getAllAppUsage() {
        return new HashMap<>(appUsageTimes);
    }
    
    public void resetDailyUsage() {
        appUsageTimes.clear();
        saveData();
        Log.d(TAG, "Reset all app usage data");
    }
    
    public long getTotalScreenTime() {
        return appUsageTimes.values().stream().mapToLong(Long::longValue).sum();
    }
} 