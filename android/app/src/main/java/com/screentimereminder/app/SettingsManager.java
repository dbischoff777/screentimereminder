package com.screentimereminder.app;

import android.content.Context;
import android.content.SharedPreferences;
import android.content.Intent;
import android.util.Log;
import org.json.JSONObject;
import java.io.File;
import java.io.FileWriter;
import java.io.FileReader;
import java.io.BufferedReader;

/**
 * Singleton class to manage all app settings.
 * This is the single source of truth for all settings.
 */
public class SettingsManager {
    private static final String TAG = "SettingsManager";
    private static SettingsManager instance;
    private final Context context;
    private final SharedPreferences prefs;
    private final String settingsFilePath;
    private static final Object lock = new Object();

    private SettingsManager(Context context) {
        this.context = context.getApplicationContext();
        this.prefs = context.getSharedPreferences(SettingsConstants.PREFS_NAME, Context.MODE_PRIVATE);
        this.settingsFilePath = new File(context.getFilesDir(), "settings.json").getAbsolutePath();
        loadSettings();
    }

    public static SettingsManager getInstance(Context context) {
        if (instance == null) {
            synchronized (lock) {
                if (instance == null) {
                    instance = new SettingsManager(context);
                }
            }
        }
        return instance;
    }

    private void loadSettings() {
        synchronized (lock) {
            try {
                File settingsFile = new File(settingsFilePath);
                if (!settingsFile.exists()) {
                    // Create default settings
                    JSONObject settings = new JSONObject();
                    settings.put("screenTimeLimit", SettingsConstants.DEFAULT_SCREEN_TIME_LIMIT);
                    settings.put("notificationFrequency", SettingsConstants.DEFAULT_NOTIFICATION_FREQUENCY);
                    settings.put("userHasSetLimit", false);
                    settings.put("chainId", "INITIAL_CHAIN_" + System.currentTimeMillis());
                    
                    // Save default settings
                    saveSettingsToFile(settings);
                }
            } catch (Exception e) {
                Log.e(TAG, "Error loading settings", e);
            }
        }
    }

    private void saveSettingsToFile(JSONObject settings) {
        synchronized (lock) {
            try (FileWriter writer = new FileWriter(settingsFilePath)) {
                writer.write(settings.toString(2));
                writer.flush();
                Log.d(TAG, "Settings saved to file: " + settings.toString(2));
            } catch (Exception e) {
                Log.e(TAG, "Error saving settings to file", e);
            }
        }
    }

    private JSONObject readSettingsFromFile() {
        synchronized (lock) {
            try {
                StringBuilder content = new StringBuilder();
                try (BufferedReader reader = new BufferedReader(new FileReader(settingsFilePath))) {
                    String line;
                    while ((line = reader.readLine()) != null) {
                        content.append(line);
                    }
                }
                return new JSONObject(content.toString());
            } catch (Exception e) {
                Log.e(TAG, "Error reading settings from file", e);
                return new JSONObject();
            }
        }
    }

    public void updateSettings(long screenTimeLimit, long notificationFrequency) {
        synchronized (lock) {
            try {
                // Validate settings
                if (screenTimeLimit < SettingsConstants.MIN_SCREEN_TIME_LIMIT || 
                    screenTimeLimit > SettingsConstants.MAX_SCREEN_TIME_LIMIT) {
                    Log.e(TAG, "Invalid screen time limit: " + screenTimeLimit);
                    return;
                }
                if (notificationFrequency < SettingsConstants.MIN_NOTIFICATION_FREQUENCY || 
                    notificationFrequency > SettingsConstants.MAX_NOTIFICATION_FREQUENCY) {
                    Log.e(TAG, "Invalid notification frequency: " + notificationFrequency);
                    return;
                }

                String newChainId = "SETTINGS_CHAIN_" + System.currentTimeMillis();
                JSONObject currentSettings = readSettingsFromFile();
                String currentChainId = currentSettings.optString("chainId", "NO_CHAIN_ID");

                // Only update if new chain ID is newer
                if (newChainId.compareTo(currentChainId) > 0) {
                    // First update SharedPreferences to ensure atomic update
                    SharedPreferences.Editor editor = prefs.edit();
                    editor.putLong(SettingsConstants.KEY_SCREEN_TIME_LIMIT, screenTimeLimit);
                    editor.putLong(SettingsConstants.KEY_NOTIFICATION_FREQUENCY, notificationFrequency);
                    editor.putBoolean(SettingsConstants.KEY_USER_HAS_SET_LIMIT, true);
                    editor.putString(SettingsConstants.KEY_LAST_SETTINGS_CHAIN_ID, newChainId);
                    
                    // Use commit() for immediate write
                    boolean success = editor.commit();
                    
                    if (!success) {
                        Log.e(TAG, String.format("[%s] Failed to save settings to SharedPreferences", newChainId));
                        return;
                    }

                    // Then update JSON file
                    JSONObject newSettings = new JSONObject();
                    newSettings.put("screenTimeLimit", screenTimeLimit);
                    newSettings.put("notificationFrequency", notificationFrequency);
                    newSettings.put("userHasSetLimit", true);
                    newSettings.put("chainId", newChainId);
                    newSettings.put("lastUpdateTime", System.currentTimeMillis());

                    // Save to file
                    saveSettingsToFile(newSettings);

                    // Verify both storages are in sync
                    verifySettings(newChainId, screenTimeLimit, notificationFrequency);

                    Log.d(TAG, String.format("[%s] Settings updated successfully", newChainId));
                    
                    // Broadcast update to ensure all components are notified
                    broadcastSettingsUpdate(context, newSettings);
                }
            } catch (Exception e) {
                Log.e(TAG, "Error updating settings", e);
            }
        }
    }

    private void verifySettings(String chainId, long screenTimeLimit, long notificationFrequency) {
        try {
            // Check SharedPreferences
            long storedLimit = prefs.getLong(SettingsConstants.KEY_SCREEN_TIME_LIMIT, -1);
            long storedFrequency = prefs.getLong(SettingsConstants.KEY_NOTIFICATION_FREQUENCY, -1);
            boolean storedUserSet = prefs.getBoolean(SettingsConstants.KEY_USER_HAS_SET_LIMIT, false);
            String storedChainId = prefs.getString(SettingsConstants.KEY_LAST_SETTINGS_CHAIN_ID, "");

            // Check JSON file
            JSONObject fileSettings = readSettingsFromFile();
            long fileLimit = fileSettings.optLong("screenTimeLimit", -1);
            long fileFrequency = fileSettings.optLong("notificationFrequency", -1);
            boolean fileUserSet = fileSettings.optBoolean("userHasSetLimit", false);
            String fileChainId = fileSettings.optString("chainId", "");

            // Log verification results
            Log.d(TAG, String.format("[%s] Settings verification:", chainId));
            Log.d(TAG, String.format("[%s] SharedPreferences - Limit: %d, Frequency: %d, UserSet: %b", 
                chainId, storedLimit, storedFrequency, storedUserSet));
            Log.d(TAG, String.format("[%s] JSON File - Limit: %d, Frequency: %d, UserSet: %b", 
                chainId, fileLimit, fileFrequency, fileUserSet));

            // Check for mismatches
            if (storedLimit != fileLimit || storedFrequency != fileFrequency || 
                storedUserSet != fileUserSet || !storedChainId.equals(fileChainId)) {
                Log.e(TAG, String.format("[%s] Settings mismatch detected!", chainId));
                
                // Force sync to JSON values
                SharedPreferences.Editor editor = prefs.edit();
                editor.putLong(SettingsConstants.KEY_SCREEN_TIME_LIMIT, fileLimit);
                editor.putLong(SettingsConstants.KEY_NOTIFICATION_FREQUENCY, fileFrequency);
                editor.putBoolean(SettingsConstants.KEY_USER_HAS_SET_LIMIT, fileUserSet);
                editor.putString(SettingsConstants.KEY_LAST_SETTINGS_CHAIN_ID, fileChainId);
                editor.commit();
                
                Log.d(TAG, String.format("[%s] Settings re-synchronized", chainId));
            }
        } catch (Exception e) {
            Log.e(TAG, "Error verifying settings", e);
        }
    }

    private void broadcastSettingsUpdate(Context context, JSONObject settings) {
        try {
            Intent updateIntent = new Intent(SettingsConstants.ACTION_USAGE_UPDATE);
            JSONObject broadcastData = new JSONObject();
            broadcastData.put("action", "UPDATE_SETTINGS");
            broadcastData.put("screenTimeLimit", settings.getLong("screenTimeLimit"));
            broadcastData.put("notificationFrequency", settings.getLong("notificationFrequency"));
            broadcastData.put("chainId", settings.getString("chainId"));
            broadcastData.put("timestamp", System.currentTimeMillis());
            updateIntent.putExtra("usageData", broadcastData.toString());
            context.sendBroadcast(updateIntent);
        } catch (Exception e) {
            Log.e(TAG, "Error broadcasting settings update", e);
        }
    }

    public JSONObject getSettings() {
        synchronized (lock) {
            try {
                return readSettingsFromFile();
            } catch (Exception e) {
                Log.e(TAG, "Error getting settings", e);
                return new JSONObject();
            }
        }
    }

    public long getScreenTimeLimit() {
        try {
            JSONObject settings = readSettingsFromFile();
            return settings.optLong("screenTimeLimit", SettingsConstants.DEFAULT_SCREEN_TIME_LIMIT);
        } catch (Exception e) {
            Log.e(TAG, "Error getting screen time limit", e);
            return SettingsConstants.DEFAULT_SCREEN_TIME_LIMIT;
        }
    }

    public long getNotificationFrequency() {
        try {
            JSONObject settings = readSettingsFromFile();
            return settings.optLong("notificationFrequency", SettingsConstants.DEFAULT_NOTIFICATION_FREQUENCY);
        } catch (Exception e) {
            Log.e(TAG, "Error getting notification frequency", e);
            return SettingsConstants.DEFAULT_NOTIFICATION_FREQUENCY;
        }
    }

    public boolean hasUserSetLimit() {
        try {
            JSONObject settings = readSettingsFromFile();
            return settings.optBoolean("userHasSetLimit", false);
        } catch (Exception e) {
            Log.e(TAG, "Error checking if user has set limit", e);
            return false;
        }
    }
} 