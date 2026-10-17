package com.starfall.game;

import android.app.Activity;
import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.util.Log;
import android.widget.Toast;

import com.starfall.game.databinding.ActivitySettingsBinding;

/**
 * Week 8 demo screen: XML layout + view binding + click listeners + implicit Intents.
 */
public class SettingsActivity extends Activity {

    private static final String TAG = "Lifecycle";
    private static final String REPO_URL = "https://github.com/saminyasar390-source/Project-Starfall";

    // View binding: generated class with one field per view id in activity_settings.xml
    private ActivitySettingsBinding binding;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Log.d(TAG, "SettingsActivity onCreate");
        UiUtil.goFullscreen(this);

        binding = ActivitySettingsBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        // Event handling: listener for a switch being toggled
        binding.switchMusic.setOnCheckedChangeListener((button, isChecked) ->
                binding.tvStatus.setText("Music: " + (isChecked ? "ON" : "OFF")));
        binding.switchSfx.setOnCheckedChangeListener((button, isChecked) ->
                binding.tvStatus.setText("Sound effects: " + (isChecked ? "ON" : "OFF")));

        // Click listener + IMPLICIT Intent: we only say WHAT we want (view a web page);
        // Android picks the app (browser) that can do it.
        binding.btnGithub.setOnClickListener(v -> {
            Intent view = new Intent(Intent.ACTION_VIEW, Uri.parse(REPO_URL));
            try {
                startActivity(view);
            } catch (ActivityNotFoundException e) {
                Toast.makeText(this, "No browser found", Toast.LENGTH_SHORT).show();
            }
        });

        // Click listener + IMPLICIT Intent: share text with any app that accepts it
        binding.btnShare.setOnClickListener(v -> {
            Intent send = new Intent(Intent.ACTION_SEND);
            send.setType("text/plain");
            send.putExtra(Intent.EXTRA_TEXT, "Check out Project Starfall: " + REPO_URL);
            startActivity(Intent.createChooser(send, "Share Project Starfall"));
        });

        binding.btnBack.setOnClickListener(v -> finish());
    }

    @Override protected void onStart()   { super.onStart();   Log.d(TAG, "SettingsActivity onStart"); }
    @Override protected void onResume()  { super.onResume();  Log.d(TAG, "SettingsActivity onResume"); }
    @Override protected void onPause()   { super.onPause();   Log.d(TAG, "SettingsActivity onPause"); }
    @Override protected void onStop()    { super.onStop();    Log.d(TAG, "SettingsActivity onStop"); }
    @Override protected void onDestroy() { super.onDestroy(); Log.d(TAG, "SettingsActivity onDestroy"); }

    @Override
    public void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);
        if (hasFocus) UiUtil.goFullscreen(this);
    }
}
