package com.starfall.game;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.widget.Toast;

public class MainMenuActivity extends Activity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        UiUtil.goFullscreen(this);

        setContentView(new MenuView(this, index -> {
            switch (index) {
                case MenuView.PLAY:
                    startActivity(new Intent(this, GameActivity.class));
                    break;
                case MenuView.EXIT:
                    finish();
                    break;
                default:
                    // TODO: Convene Banner, Shop, Leaderboard, Settings screens
                    Toast.makeText(this, "Coming soon", Toast.LENGTH_SHORT).show();
            }
        }));
    }

    @Override
    public void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);
        if (hasFocus) UiUtil.goFullscreen(this);
    }
}
