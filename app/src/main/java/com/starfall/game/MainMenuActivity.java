package com.starfall.game;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.widget.Toast;

public class MainMenuActivity extends Activity {

    private static final String TAG = "Lifecycle";

    private MusicPlayer music;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Log.d(TAG, "MainMenuActivity onCreate");
        UiUtil.goFullscreen(this);

        music = new MusicPlayer(this, R.raw.menu_music, 0.85f);   // menu theme, loops while this screen is open

        MenuView menuView = new MenuView(this, index -> {
            switch (index) {
                case MenuView.PLAY:
                    // Explicit Intent: we name the exact Activity class to open
                    startActivity(new Intent(this, GameActivity.class));
                    break;
                case MenuView.SETTINGS:
                    startActivity(new Intent(this, SettingsActivity.class));
                    break;
                case MenuView.CONVENE:
                    startActivity(new Intent(this, ConveneActivity.class));
                    break;
                case MenuView.EXIT:
                    finish();
                    break;
                default:
                    // TODO: Shop and Leaderboard screens
                    Toast.makeText(this, "Coming soon", Toast.LENGTH_SHORT).show();
            }
        });
        // Fade the actual game menu in instead of showing it abruptly.
        menuView.setAlpha(0f);
        setContentView(menuView);
        menuView.animate().alpha(1f).setDuration(900L).start();
    }

    @Override protected void onStart()   { super.onStart();   Log.d(TAG, "MainMenuActivity onStart"); }
    @Override protected void onResume()  { super.onResume();  Log.d(TAG, "MainMenuActivity onResume"); music.start(); }
    @Override protected void onPause()   { super.onPause();   Log.d(TAG, "MainMenuActivity onPause"); music.pause(); }
    @Override protected void onStop()    { super.onStop();    Log.d(TAG, "MainMenuActivity onStop"); }
    @Override protected void onDestroy() { super.onDestroy(); Log.d(TAG, "MainMenuActivity onDestroy"); music.release(); }

    @Override
    public void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);
        if (hasFocus) UiUtil.goFullscreen(this);
    }
}
