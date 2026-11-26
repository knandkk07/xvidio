package com.xvideo.app.util;

import android.content.Context;
import android.content.SharedPreferences;
import com.xvideo.app.core.Config;

public class SharedPrefManager {
    private final SharedPreferences prefs;
    private final SharedPreferences.Editor editor;

    public SharedPrefManager(Context context) {
        prefs = context.getSharedPreferences(Config.PREF_NAME, Context.MODE_PRIVATE);
        editor = prefs.edit();
    }

    public void setString(String key, String value) {
        editor.putString(key, value);
        editor.apply();
    }

    public String getString(String key) {
        return prefs.getString(key, null);
    }

    public String getString(String key, String defaultValue) {
        return prefs.getString(key, defaultValue);
    }

    public void setBoolean(String key, boolean value) {
        editor.putBoolean(key, value);
        editor.apply();
    }

    public boolean getBoolean(String key) {
        return prefs.getBoolean(key, false);
    }

    public void setInt(String key, int value) {
        editor.putInt(key, value);
        editor.apply();
    }

    public int getInt(String key) {
        return prefs.getInt(key, 0);
    }

    public void setLong(String key, long value) {
        editor.putLong(key, value);
        editor.apply();
    }

    public long getLong(String key) {
        return prefs.getLong(key, 0L);
    }

    public void remove(String key) {
        editor.remove(key);
        editor.apply();
    }

    public void clear() {
        editor.clear();
        editor.apply();
    }
}
