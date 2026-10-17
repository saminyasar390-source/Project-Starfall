package com.starfall.game;

import android.content.Context;
import android.content.SharedPreferences;

/**
 * The player's settings, kept in static fields so every screen can read them, and saved on the
 * phone (SharedPreferences) so they survive closing the app.
 *
 * load() is safe to call from anywhere, as often as you like: it only reads the file once.
 */
public final class GameSettings {

    public static final int LOW = 0, MEDIUM = 1, HIGH = 2;

    private static final String PREFS = "starfall_settings";

    /** 0..100. 0 means off. */
    public static int musicVolume = 70;
    /** 0..100. 0 means off. */
    public static int sfxVolume = 100;
    /** LOW, MEDIUM or HIGH: how many particles and effects the animated menu draws. */
    public static int quality = HIGH;

    private static boolean loaded;

    private GameSettings() {}

    public static void load(Context c) {
        if (loaded) return;
        SharedPreferences p = c.getApplicationContext().getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        musicVolume = clamp(p.getInt("music", musicVolume), 0, 100);
        sfxVolume = clamp(p.getInt("sfx", sfxVolume), 0, 100);
        quality = clamp(p.getInt("quality", quality), LOW, HIGH);
        loaded = true;
    }

    public static void save(Context c) {
        c.getApplicationContext().getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
                .putInt("music", musicVolume)
                .putInt("sfx", sfxVolume)
                .putInt("quality", quality)
                .apply();
    }

    /** Music volume as 0.0 - 1.0. */
    public static float music() { return musicVolume / 100f; }

    /** Sound effects volume as 0.0 - 1.0. */
    public static float sfx() { return sfxVolume / 100f; }

    /** Share of the maximum particle count to draw. */
    public static float qualityFactor() {
        switch (quality) {
            case LOW:    return 0.35f;
            case MEDIUM: return 0.70f;
            default:     return 1.00f;
        }
    }

    private static int clamp(int v, int lo, int hi) { return v < lo ? lo : (v > hi ? hi : v); }
}
