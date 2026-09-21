package com.video.rec;

import android.content.Context;
import android.content.SharedPreferences;

/**
 * Stores the only setting the user really needs to choose: the resolution.
 * Frame rate, codec, bitrate and anti-flicker are picked automatically for each phone
 * (see {@link CameraCapabilities}).
 */
public class SettingsManager {
    public static final int DEFAULT_RESOLUTION_WIDTH = 1440; // 1440x1080, the phone picks the closest it supports

    private static final String PREF_NAME = "VideoRecorderPrefs";
    private static final String KEY_RES_WIDTH = "res_width";

    private final SharedPreferences prefs;

    public SettingsManager(Context context) {
        prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
    }

    public void setResolutionWidth(int width) {
        prefs.edit().putInt(KEY_RES_WIDTH, width).apply();
    }

    public int getResolutionWidth() {
        return prefs.getInt(KEY_RES_WIDTH, DEFAULT_RESOLUTION_WIDTH);
    }
}
