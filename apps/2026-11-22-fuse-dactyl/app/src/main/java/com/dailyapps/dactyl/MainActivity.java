package com.dailyapps.dactyl;

import android.app.Activity;
import android.os.Bundle;
import android.view.WindowManager;

public class MainActivity extends Activity {
    private GameView game;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN
                | WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        game = new GameView(this);
        setContentView(game);
    }

    @Override
    protected void onResume() {
        super.onResume();
        game.onHostResume();
    }

    @Override
    protected void onPause() {
        super.onPause();
        game.onHostPause();
    }
}
