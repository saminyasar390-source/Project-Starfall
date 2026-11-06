package com.starfall.game;

import android.app.Activity;
import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;

import com.starfall.game.databinding.ActivitySettingsBinding;

/**
 * Settings screen: music and sound-effect volume sliders, graphics quality, plus the GitHub / Share extras.
 *
 * Changes apply immediately, so you can hear the effect volume as you drag. OK keeps and saves them;
 * BACK (or the phone's back gesture) puts everything back the way it was when the screen opened.
 */
public class SettingsActivity extends Activity {

    private static final String TAG = "Lifecycle";
    private static final String REPO_URL = "https://github.com/saminyasar390-source/Project-Starfall";
    private static final int STEP = 10;      // how much the - and + buttons move a slider

    // View binding: generated class with one field per view id in activity_settings.xml
    private ActivitySettingsBinding binding;
    private SoundManager sounds;

    // Values when the screen opened, so BACK can undo
    private int startMusic, startSfx, startQuality;
    private boolean committed;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Log.d(TAG, "SettingsActivity onCreate");
        UiUtil.goFullscreen(this);

        GameSettings.load(this);
        startMusic = GameSettings.musicVolume;
        startSfx = GameSettings.sfxVolume;
        startQuality = GameSettings.quality;
        sounds = new SoundManager(this);

        binding = ActivitySettingsBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        // Volume rows: slider + minus / plus buttons
        setupVolume(binding.seekMusic, binding.tvMusicLabel, R.string.settings_music,
                binding.btnMusicDown, binding.btnMusicUp, true);
        setupVolume(binding.seekSfx, binding.tvSfxLabel, R.string.settings_sfx,
                binding.btnSfxDown, binding.btnSfxUp, false);

        // Graphics quality
        binding.rgQuality.check(radioIdFor(GameSettings.quality));
        binding.rgQuality.setOnCheckedChangeListener((group, checkedId) -> {
            if (checkedId == binding.rbLow.getId()) GameSettings.quality = GameSettings.LOW;
            else if (checkedId == binding.rbMedium.getId()) GameSettings.quality = GameSettings.MEDIUM;
            else GameSettings.quality = GameSettings.HIGH;
            sounds.click();
        });

        // Click listener + IMPLICIT Intent: we only say WHAT we want (view a web page);
        // Android picks the app (browser) that can do it.
        binding.btnGithub.setOnClickListener(v -> {
            sounds.click();
            Intent view = new Intent(Intent.ACTION_VIEW, Uri.parse(REPO_URL));
            try {
                startActivity(view);
            } catch (ActivityNotFoundException e) {
                Toast.makeText(this, "No browser found", Toast.LENGTH_SHORT).show();
            }
        });

        // Click listener + IMPLICIT Intent: share text with any app that accepts it
        binding.btnShare.setOnClickListener(v -> {
            sounds.click();
            Intent send = new Intent(Intent.ACTION_SEND);
            send.setType("text/plain");
            send.putExtra(Intent.EXTRA_TEXT, "Check out Project Starfall: " + REPO_URL);
            startActivity(Intent.createChooser(send, "Share Project Starfall"));
        });

        // OK keeps the changes (and saves them to the phone); BACK throws them away (see finish()).
        binding.btnOk.setOnClickListener(v -> {
            sounds.click();
            committed = true;
            GameSettings.save(this);
            finish();
        });
        binding.btnBack.setOnClickListener(v -> {
            sounds.click();
            finish();
        });
    }

    /** Wires one slider row: slider, "Label  70%" text above it, and the - / + buttons beside it. */
    private void setupVolume(SeekBar bar, TextView label, int nameRes, View down, View up, boolean music) {
        bar.setMax(100);
        bar.setProgress(music ? GameSettings.musicVolume : GameSettings.sfxVolume);
        updateLabel(label, nameRes, bar.getProgress());

        bar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                if (music) GameSettings.musicVolume = progress;
                else GameSettings.sfxVolume = progress;
                updateLabel(label, nameRes, progress);
            }
            @Override public void onStartTrackingTouch(SeekBar seekBar) { }
            @Override public void onStopTrackingTouch(SeekBar seekBar) {
                if (!music) sounds.click();       // let the player hear how loud effects are now
            }
        });

        down.setOnClickListener(v -> {
            bar.setProgress(Math.max(0, bar.getProgress() - STEP));
            sounds.click();
        });
        up.setOnClickListener(v -> {
            bar.setProgress(Math.min(100, bar.getProgress() + STEP));
            sounds.click();
        });
    }

    private void updateLabel(TextView label, int nameRes, int percent) {
        label.setText(getString(R.string.volume_label, getString(nameRes), percent));
    }

    private int radioIdFor(int quality) {
        if (quality == GameSettings.LOW) return binding.rbLow.getId();
        if (quality == GameSettings.MEDIUM) return binding.rbMedium.getId();
        return binding.rbHigh.getId();
    }

    /** Every way of leaving this screen (BACK, OK, the back gesture) goes through finish(). */
    @Override
    public void finish() {
        if (!committed) {                       // BACK / back gesture: undo what was changed
            GameSettings.musicVolume = startMusic;
            GameSettings.sfxVolume = startSfx;
            GameSettings.quality = startQuality;
        }
        super.finish();
    }

    @Override protected void onStart()   { super.onStart();   Log.d(TAG, "SettingsActivity onStart"); }
    @Override protected void onResume()  { super.onResume();  Log.d(TAG, "SettingsActivity onResume"); }
    @Override protected void onPause()   { super.onPause();   Log.d(TAG, "SettingsActivity onPause"); }
    @Override protected void onStop()    { super.onStop();    Log.d(TAG, "SettingsActivity onStop"); }
    @Override protected void onDestroy() {
        super.onDestroy();
        Log.d(TAG, "SettingsActivity onDestroy");
        sounds.release();
    }

    @Override
    public void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);
        if (hasFocus) UiUtil.goFullscreen(this);
    }
}
