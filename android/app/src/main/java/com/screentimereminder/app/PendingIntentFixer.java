package com.screentimereminder.app;

import android.app.PendingIntent;
import android.os.Build;

/**
 * Helper class to fix PendingIntent flags for Android 12+ compatibility
 */
public class PendingIntentFixer {
    
    /**
     * Adds the FLAG_IMMUTABLE flag to PendingIntent flags for Android 12+ compatibility
     * 
     * @param flags The original flags
     * @return The updated flags with FLAG_IMMUTABLE added if needed
     */
    public static int fixFlags(int flags) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            flags |= PendingIntent.FLAG_IMMUTABLE;
        }
        return flags;
    }
} 