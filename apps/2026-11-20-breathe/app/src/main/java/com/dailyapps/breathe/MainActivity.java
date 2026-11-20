package com.dailyapps.breathe;

import android.animation.ValueAnimator;
import android.app.Activity;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.os.Handler;
import android.view.Gravity;
import android.view.View;
import android.view.animation.LinearInterpolator;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

public class MainActivity extends Activity {
    // Box breathing: inhale, hold, exhale, hold
    private static final String[] PHASES = {"Breathe in", "Hold", "Breathe out", "Hold"};
    private static final int[] SECONDS = {4, 4, 4, 4};

    private final Handler handler = new Handler();
    private SharedPreferences prefs;
    private View circle;
    private TextView phaseView, countView, statsView;
    private Button toggle;
    private boolean running;
    private int phase, remaining, cycles;
    private ValueAnimator animator;

    private final Runnable tick = new Runnable() {
        @Override public void run() {
            if (!running) return;
            remaining--;
            if (remaining <= 0) {
                phase = (phase + 1) % 4;
                if (phase == 0) {
                    cycles++;
                    prefs.edit().putInt("total", prefs.getInt("total", 0) + 1).apply();
                    updateStats();
                }
                startPhase();
            } else {
                countView.setText(String.valueOf(remaining));
            }
            handler.postDelayed(this, 1000);
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        prefs = getSharedPreferences("breathe", MODE_PRIVATE);

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setGravity(Gravity.CENTER);
        root.setPadding(48, 48, 48, 48);
        root.setBackgroundColor(Color.parseColor("#102A43"));

        phaseView = new TextView(this);
        phaseView.setTextSize(32);
        phaseView.setTextColor(Color.WHITE);
        phaseView.setTypeface(Typeface.DEFAULT_BOLD);
        phaseView.setGravity(Gravity.CENTER);
        phaseView.setText("Ready?");
        root.addView(phaseView);

        FrameLayout stage = new FrameLayout(this);
        circle = new View(this);
        GradientDrawable d = new GradientDrawable();
        d.setShape(GradientDrawable.OVAL);
        d.setColor(Color.parseColor("#4FC3F7"));
        circle.setBackground(d);
        int size = 560;
        FrameLayout.LayoutParams cp = new FrameLayout.LayoutParams(size, size, Gravity.CENTER);
        stage.addView(circle, cp);
        circle.setScaleX(0.4f);
        circle.setScaleY(0.4f);

        countView = new TextView(this);
        countView.setTextSize(64);
        countView.setTextColor(Color.parseColor("#102A43"));
        countView.setTypeface(Typeface.DEFAULT_BOLD);
        stage.addView(countView, new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.WRAP_CONTENT, FrameLayout.LayoutParams.WRAP_CONTENT, Gravity.CENTER));
        root.addView(stage, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 760));

        toggle = new Button(this);
        toggle.setText("Start");
        toggle.setTextSize(22);
        toggle.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { if (running) stop(); else start(); }
        });
        root.addView(toggle);

        statsView = new TextView(this);
        statsView.setTextSize(16);
        statsView.setTextColor(Color.parseColor("#BCCCDC"));
        statsView.setGravity(Gravity.CENTER);
        statsView.setPadding(0, 32, 0, 0);
        root.addView(statsView);
        updateStats();

        setContentView(root);
    }

    private void updateStats() {
        statsView.setText("This session: " + cycles + " cycles  |  All time: "
                + prefs.getInt("total", 0));
    }

    private void start() {
        running = true;
        phase = 0;
        toggle.setText("Stop");
        startPhase();
        handler.postDelayed(tick, 1000);
    }

    private void stop() {
        running = false;
        handler.removeCallbacks(tick);
        if (animator != null) animator.cancel();
        toggle.setText("Start");
        phaseView.setText("Ready?");
        countView.setText("");
        circle.setScaleX(0.4f);
        circle.setScaleY(0.4f);
    }

    private void startPhase() {
        remaining = SECONDS[phase];
        phaseView.setText(PHASES[phase]);
        countView.setText(String.valueOf(remaining));
        float from = circle.getScaleX();
        float to = from;
        if (phase == 0) to = 1f;
        else if (phase == 2) to = 0.4f;
        if (animator != null) animator.cancel();
        animator = ValueAnimator.ofFloat(from, to);
        animator.setDuration(SECONDS[phase] * 1000L);
        animator.setInterpolator(new LinearInterpolator());
        animator.addUpdateListener(new ValueAnimator.AnimatorUpdateListener() {
            @Override public void onAnimationUpdate(ValueAnimator a) {
                float s = (Float) a.getAnimatedValue();
                circle.setScaleX(s);
                circle.setScaleY(s);
            }
        });
        animator.start();
    }

    @Override
    protected void onPause() {
        super.onPause();
        if (running) stop();
    }
}
