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
    public int getResolutionWidth() { return prefs.getInt("res_width", 640); }

    public void setFps(int fps) { prefs.edit().putInt("fps", fps).apply(); }
    public int getFps() { return prefs.getInt("fps", 30); }

    public void setCodec(String codec) { prefs.edit().putString("codec", codec).apply(); }
    public String getCodec() { return prefs.getString("codec", "H.264"); }

    public void setAntiFlicker(int mode) { prefs.edit().putInt("flicker", mode).apply(); }
    public int getAntiFlicker() { return prefs.getInt("flicker", 0); } // 0 = Auto
}
