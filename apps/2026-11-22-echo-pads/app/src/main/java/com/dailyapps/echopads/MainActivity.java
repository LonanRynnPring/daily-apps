package com.dailyapps.echopads;

import android.app.Activity;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.graphics.Typeface;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;

public class MainActivity extends Activity {
    private SharedPreferences prefs;
    private PadView pads;
    private Tones tones;
    private TextView title, status, scoreText, bestText;
    private Button startBtn, modeBtn, speedBtn, soundBtn;

    private String key() { return "best_" + (pads.reverse ? "rev" : "fwd") + (pads.fast ? "_fast" : "_norm"); }

    private int dp(int v) { return (int) (v * getResources().getDisplayMetrics().density + 0.5f); }

    private Button button(String text) {
        Button b = new Button(this);
        b.setText(text);
        b.setTextColor(Color.WHITE);
        b.setAllCaps(false);
        b.setBackgroundColor(0xFF2A2A2A);
        b.setMinHeight(dp(48));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
        lp.setMargins(dp(4), dp(4), dp(4), dp(4));
        b.setLayoutParams(lp);
        return b;
    }

    @Override
    protected void onCreate(Bundle s) {
        super.onCreate(s);
        prefs = getSharedPreferences("echopads", MODE_PRIVATE);
        tones = new Tones();
        tones.enabled = prefs.getBoolean("sound", true);
        pads = new PadView(this, tones);
        pads.reverse = prefs.getBoolean("reverse", false);
        pads.fast = prefs.getBoolean("fast", false);

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(0xFF121212);
        root.setPadding(dp(12), dp(24), dp(12), dp(12));

        title = new TextView(this);
        title.setText("ECHO PADS");
        title.setTextColor(Color.WHITE);
        title.setTextSize(26);
        title.setTypeface(Typeface.DEFAULT_BOLD);
        title.setGravity(Gravity.CENTER);
        root.addView(title);

        scoreText = new TextView(this);
        scoreText.setTextColor(0xFFFFE082);
        scoreText.setTextSize(20);
        scoreText.setGravity(Gravity.CENTER);
        root.addView(scoreText);

        bestText = new TextView(this);
        bestText.setTextColor(0xFFB0B0B0);
        bestText.setTextSize(14);
        bestText.setGravity(Gravity.CENTER);
        root.addView(bestText);

        status = new TextView(this);
        status.setTextColor(0xFFA5D6A7);
        status.setTextSize(16);
        status.setGravity(Gravity.CENTER);
        status.setPadding(0, dp(6), 0, dp(6));
        root.addView(status);

        root.addView(pads, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f));

        LinearLayout row1 = new LinearLayout(this);
        modeBtn = button("");
        speedBtn = button("");
        row1.addView(modeBtn);
        row1.addView(speedBtn);
        root.addView(row1);

        LinearLayout row2 = new LinearLayout(this);
        soundBtn = button("");
        startBtn = button("Start");
        startBtn.setBackgroundColor(0xFF1E88E5);
        row2.addView(soundBtn);
        row2.addView(startBtn);
        root.addView(row2);
        setContentView(root);

        pads.setListener(new PadView.Listener() {
            public void onRound(int round) { scoreText.setText("Round " + round); }
            public void onStatus(String t) { status.setText(t); }
            public void onGameOver(int score) {
                int best = prefs.getInt(key(), 0);
                String msg = "Game over, you reached " + score;
                if (score > best) {
                    prefs.edit().putInt(key(), score).apply();
                    msg += " - NEW BEST!";
                }
                status.setText(msg);
                scoreText.setText("Score " + score);
                setLocked(false);
                startBtn.setText("Play again");
                refresh();
            }
        });

        modeBtn.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                pads.reverse = !pads.reverse;
                prefs.edit().putBoolean("reverse", pads.reverse).apply();
                refresh();
            }
        });
        speedBtn.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                pads.fast = !pads.fast;
                prefs.edit().putBoolean("fast", pads.fast).apply();
                refresh();
            }
        });
        soundBtn.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                tones.enabled = !tones.enabled;
                prefs.edit().putBoolean("sound", tones.enabled).apply();
                refresh();
            }
        });
        startBtn.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                if (pads.state == PadView.SHOWING || pads.state == PadView.INPUT) {
                    pads.abort();
                    setLocked(false);
                    startBtn.setText("Start");
                    status.setText("Stopped");
                } else {
                    setLocked(true);
                    startBtn.setText("Stop");
                    scoreText.setText("Round 1");
                    pads.start();
                }
            }
        });
        status.setText("Watch the pads, then echo the pattern");
        refresh();
    }

    private void setLocked(boolean locked) {
        modeBtn.setEnabled(!locked);
        speedBtn.setEnabled(!locked);
        modeBtn.setAlpha(locked ? 0.4f : 1f);
        speedBtn.setAlpha(locked ? 0.4f : 1f);
    }

    private void refresh() {
        modeBtn.setText(pads.reverse ? "Mode: Reverse" : "Mode: Classic");
        speedBtn.setText(pads.fast ? "Speed: Fast" : "Speed: Normal");
        soundBtn.setText(tones.enabled ? "Sound: On" : "Sound: Off");
        if (pads.state == PadView.IDLE) scoreText.setText("Ready");
        bestText.setText("Best (" + (pads.reverse ? "Reverse" : "Classic") + ", "
                + (pads.fast ? "Fast" : "Normal") + "): " + prefs.getInt(key(), 0));
    }

    @Override
    protected void onPause() {
        super.onPause();
        if (pads.state == PadView.SHOWING || pads.state == PadView.INPUT) {
            pads.abort();
            setLocked(false);
            startBtn.setText("Start");
            status.setText("Paused - tap Start for a new game");
        }
    }
}
