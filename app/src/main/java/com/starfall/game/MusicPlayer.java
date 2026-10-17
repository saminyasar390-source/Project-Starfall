package com.starfall.game;

import android.content.Context;
import android.media.AudioAttributes;
import android.media.AudioManager;
import android.media.MediaPlayer;

/**
 * Plays one background music track on a loop (the main menu theme).
 *
 * Usage: create it once, call start() when the screen becomes visible, pause() when it is left,
 * and release() when the screen is destroyed.
 *
 * Music is a long track, so it uses MediaPlayer (SoundPool, used by SoundManager, is only for short effects).
 */
public class MusicPlayer {

    /** Controlled by the Music switch in SettingsActivity. */
    public static boolean musicEnabled = true;

    private final Context context;
    private final int resId;
    private final float volume;
    private MediaPlayer player;

    /** @param resId the track in res/raw, e.g. R.raw.menu_music   @param volume 0.0 (silent) to 1.0 (full) */
    public MusicPlayer(Context context, int resId, float volume) {
        this.context = context.getApplicationContext();
        this.resId = resId;
        this.volume = volume;
    }

    /** Starts the track, or resumes it from where it was paused. Does nothing if music is switched off. */
    public void start() {
        if (!musicEnabled) return;
        if (player == null) {
            AudioAttributes attrs = new AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_GAME)
                    .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                    .build();
            player = MediaPlayer.create(context, resId, attrs, AudioManager.AUDIO_SESSION_ID_GENERATE);
            if (player == null) return;          // the file could not be opened or decoded
            player.setLooping(true);             // restarts by itself when the track ends
            player.setVolume(volume, volume);
        }
        if (!player.isPlaying()) player.start();
    }

    /** Pauses the track; the next start() continues from the same spot. */
    public void pause() {
        if (player != null && player.isPlaying()) player.pause();
    }

    /** Frees the player. Call from onDestroy(). */
    public void release() {
        if (player != null) {
            player.release();
            player = null;
        }
    }
}
