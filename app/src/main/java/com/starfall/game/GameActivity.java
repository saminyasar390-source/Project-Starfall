package com.starfall.game;

import android.app.Activity;
import android.os.Build;
import android.os.Bundle;
import android.util.Log;
import android.window.OnBackInvokedDispatcher;

public class GameActivity extends Activity {

    private static final String TAG = "Lifecycle";

    private GameView gameView;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Log.d(TAG, "GameActivity onCreate");
        UiUtil.goFullscreen(this);
        gameView = new GameView(this);
        setContentView(gameView);

        // Back key / back gesture = ESC: pause the game (or resume it) instead of leaving.
        // Android 13+ uses this callback; older versions use onBackPressed() below.
        if (Build.VERSION.SDK_INT >= 33) {
            getOnBackInvokedDispatcher().registerOnBackInvokedCallback(
                    OnBackInvokedDispatcher.PRIORITY_DEFAULT,
                    () -> { if (!gameView.handleBack()) finish(); });
        }
    }

    @Override
    @SuppressWarnings("deprecation")
    public void onBackPressed() {
        if (!gameView.handleBack()) super.onBackPressed();
    }

    // ---- Lifecycle (Week 8): the game pauses itself whenever the player leaves the screen ----
    @Override
    protected void onStart() {
        super.onStart();
        Log.d(TAG, "GameActivity onStart");
    }

    @Override
    protected void onResume() {
        super.onResume();
        Log.d(TAG, "GameActivity onResume");
        gameView.resume();            // start the game loop thread (the game stays paused if it was paused)
    }

    @Override
    protected void onPause() {
        super.onPause();
        Log.d(TAG, "GameActivity onPause");
        gameView.pauseGame(true);     // show the pause menu so the player returns to a paused game
        gameView.pause();             // stop the game loop thread
    }

    @Override
    protected void onStop() {
        super.onStop();
        Log.d(TAG, "GameActivity onStop");
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        Log.d(TAG, "GameActivity onDestroy");
    }

    @Override
    public void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);
        if (hasFocus) UiUtil.goFullscreen(this);
        else gameView.pauseGame(true);   // e.g. notification shade pulled down, system dialog
    }
}
