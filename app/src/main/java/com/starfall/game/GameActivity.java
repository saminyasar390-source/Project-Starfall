package com.starfall.game;

import android.app.Activity;
import android.os.Bundle;
import android.util.Log;

public class GameActivity extends Activity {

    private GameView gameView;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        UiUtil.goFullscreen(this);
        gameView = new GameView(this);
        setContentView(gameView);
    }

    // Lifecycle callbacks (Week 8): pause the game loop when the app is hidden
    @Override
    protected void onResume() {
        super.onResume();
        gameView.resume();
    }

    @Override
    protected void onPause() {
        super.onPause();
        gameView.pause();
    }

    @Override
    public void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);
        if (hasFocus) UiUtil.goFullscreen(this);
    }
    @Override protected void onStart()   { super.onStart();   Log.d("Lifecycle", getClass().getSimpleName() + " onStart"); }
    @Override protected void onStop()    { super.onStop();    Log.d("Lifecycle", getClass().getSimpleName() + " onStop"); }
    @Override protected void onDestroy() { super.onDestroy(); Log.d("Lifecycle", getClass().getSimpleName() + " onDestroy"); }
}
