package com.video.rec;

import android.content.Context;
import android.content.SharedPreferences;

public class SettingsManager {
    private static final String PREF_NAME = "VideoRecorderPrefs";
    private SharedPreferences prefs;

    public SettingsManager(Context context) {
        prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
    }

    public void setResolutionWidth(int width) { prefs.edit().putInt("res_width", width).apply(); }
    public int getResolutionWidth() { return prefs.getInt("res_width", 1440); } // Default highest 4:3 (1440x1080)

    public void setFps(int fps) { prefs.edit().putInt("fps", fps).apply(); }
    public int getFps() { return prefs.getInt("fps", 30); } // Default 30 FPS

    public void setCodec(String codec) { prefs.edit().putString("codec", codec).apply(); }
    public String getCodec() { return prefs.getString("codec", "H.264"); } // Default H.264

    public void setAntiFlicker(int mode) { prefs.edit().putInt("flicker", mode).apply(); }
    public int getAntiFlicker() { return prefs.getInt("flicker", 0); } // 0 = Auto

    // Calculated bitrate based on selected resolution and frame rate
    public int getCalculatedBitrate() {
        int width = getResolutionWidth();
        int fps = getFps();
        int baseBitrate;

        if (width <= 320) baseBitrate = 800_000;          // 800 kbps
        else if (width <= 640) baseBitrate = 2_000_000;   // 2 Mbps
        else if (width <= 960) baseBitrate = 4_000_000;   // 4 Mbps
        else if (width <= 1440) baseBitrate = 6_000_000;  // 6 Mbps
        else if (width <= 1600) baseBitrate = 8_000_000; // 8 Mbps
        else baseBitrate = 24_000_000;                   // 24 Mbps

        return (int) (baseBitrate * (fps / 30.0f));
    }
}
