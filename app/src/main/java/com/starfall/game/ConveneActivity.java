package com.starfall.game;

import android.app.Activity;
import android.os.Build;
import android.os.Bundle;
import android.util.Log;
import android.window.OnBackInvokedDispatcher;

/** Hosts the Convene (recruitment) banner screen. Opened from the CONVENE BANNER button in the menu. */
public class ConveneActivity extends Activity {

    private static final String TAG = "Lifecycle";

    private ConveneView view;
    private MusicPlayer music;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Log.d(TAG, "ConveneActivity onCreate");
        UiUtil.goFullscreen(this);
        GameSettings.load(this);
        PlayerData.load(this);

        music = new MusicPlayer(this, R.raw.menu_music, 0.85f);   // same theme as the menu
        view = new ConveneView(this, this::finish);
        setContentView(view);

        // Back key / back gesture: closes the result or rates window first, then leaves the screen.
        if (Build.VERSION.SDK_INT >= 33) {
            getOnBackInvokedDispatcher().registerOnBackInvokedCallback(
                    OnBackInvokedDispatcher.PRIORITY_DEFAULT,
                    () -> { if (!view.handleBack()) finish(); });
        }
    }

    @Override
    @SuppressWarnings("deprecation")
    public void onBackPressed() {
        if (!view.handleBack()) super.onBackPressed();
    }

    @Override protected void onStart()   { super.onStart();   Log.d(TAG, "ConveneActivity onStart"); }
    @Override protected void onResume()  { super.onResume();  Log.d(TAG, "ConveneActivity onResume"); music.start(); view.invalidate(); }
    @Override protected void onPause()   { super.onPause();   Log.d(TAG, "ConveneActivity onPause"); music.pause(); }
    @Override protected void onStop()    { super.onStop();    Log.d(TAG, "ConveneActivity onStop"); }
    @Override protected void onDestroy() { super.onDestroy(); Log.d(TAG, "ConveneActivity onDestroy"); music.release(); }

    @Override
    public void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);
        if (hasFocus) UiUtil.goFullscreen(this);
    }
}
