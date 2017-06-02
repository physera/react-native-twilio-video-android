package com.twiliorn.library.permissions;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.SharedPreferences;
import android.preference.PreferenceManager;

public class SharedPreferencesManager {

    // Permissions
    private static final String HAS_ASKED_FOR_CAMERA_KEY     = "has_asked_for_camera";
    private static final String HAS_ASKED_FOR_MICROPHONE_KEY = "has_asked_for_microphone";

    private final SharedPreferences preferences;

    public SharedPreferencesManager(Context context) {
        this.preferences = PreferenceManager.getDefaultSharedPreferences(context);
    }

    @SuppressLint("CommitPrefEdits")
    public void clearPreferences() {
        preferences.edit()
                   .clear()
                   .commit();
    }

    // ===== PERMISSIONS ===========================================================================

    // ----- Camera ------------------------------------------------------------------------------

    public void setCameraPermissionsAsked() {
        preferences.edit()
                   .putBoolean(HAS_ASKED_FOR_CAMERA_KEY, true)
                   .apply();
    }

    public boolean isCameraPermissionsAsked() {
        return preferences.getBoolean(HAS_ASKED_FOR_CAMERA_KEY, false);
    }

    // ----- Microphone -------------------------------------------------------------------------------

    public void setMicrophonePermissionsAsked() {
        preferences.edit()
                   .putBoolean(HAS_ASKED_FOR_MICROPHONE_KEY, true)
                   .apply();
    }

    public boolean isMicrophonePermissionsAsked() {
        return preferences.getBoolean(HAS_ASKED_FOR_MICROPHONE_KEY, false);
    }
}
