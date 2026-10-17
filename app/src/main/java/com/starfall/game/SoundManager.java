package com.starfall.game;

import android.content.Context;
import android.media.AudioAttributes;
import android.media.SoundPool;

/**
 * Plays short sound effects with SoundPool (made for quick, overlapping sounds).
 * Each screen creates its own SoundManager and calls release() when it closes.
 */
public class SoundManager {

    /** Controlled by the Sound effects switch in SettingsActivity. */
    public static boolean sfxEnabled = true;

    private final SoundPool pool;
    private final int shoot, hurt, pickup, levelUp, gameOver, click;

    public SoundManager(Context c) {
        AudioAttributes attrs = new AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_GAME)
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .build();
        pool = new SoundPool.Builder().setMaxStreams(8).setAudioAttributes(attrs).build();

        // Files live in res/raw. Loading happens in the background.
        shoot    = pool.load(c, R.raw.sfx_shoot, 1);
        hurt     = pool.load(c, R.raw.sfx_hurt, 1);
        pickup   = pool.load(c, R.raw.sfx_pickup, 1);
        levelUp  = pool.load(c, R.raw.sfx_level_up, 1);
        gameOver = pool.load(c, R.raw.sfx_game_over, 1);
        click    = pool.load(c, R.raw.sfx_click, 1);
    }

    private void play(int id, float volume) {
        if (sfxEnabled) pool.play(id, volume, volume, 1, 0, 1f);
    }

    public void shoot()    { play(shoot, 0.35f); }
    public void hurt()     { play(hurt, 0.6f); }
    public void pickup()   { play(pickup, 0.3f); }
    public void levelUp()  { play(levelUp, 0.8f); }
    public void gameOver() { play(gameOver, 0.9f); }
    public void click()    { play(click, 0.8f); }

    public void release() {
        pool.release();
    }
}
