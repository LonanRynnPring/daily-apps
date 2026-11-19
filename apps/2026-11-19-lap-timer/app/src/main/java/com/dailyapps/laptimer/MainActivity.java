package com.dailyapps.laptimer;

import android.app.Activity;
import android.graphics.Typeface;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.view.Gravity;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.Locale;

public class MainActivity extends Activity {
    private final Handler handler = new Handler(Looper.getMainLooper());
    private boolean running;
    private long startAt;      // uptime when current run began
    private long accumulated;  // elapsed before current run
    private long lastLapTotal;
    private final ArrayList<String> laps = new ArrayList<>();
    private TextView timeView, lapsView;
    private Button startStop, lapReset;

    private final Runnable ticker = new Runnable() {
        @Override
        public void run() {
            timeView.setText(format(elapsed()));
            if (running) handler.postDelayed(this, 30);
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setGravity(Gravity.CENTER_HORIZONTAL);
        root.setPadding(48, 96, 48, 48);

        timeView = new TextView(this);
        timeView.setTextSize(56);
        timeView.setTypeface(Typeface.MONOSPACE, Typeface.BOLD);
        timeView.setGravity(Gravity.CENTER);

        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        startStop = new Button(this);
        startStop.setTextSize(22);
        startStop.setOnClickListener(v -> toggle());
        lapReset = new Button(this);
        lapReset.setTextSize(22);
        lapReset.setOnClickListener(v -> lapOrReset());
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, 220, 1f);
        row.addView(lapReset, lp);
        row.addView(startStop, new LinearLayout.LayoutParams(0, 220, 1f));

        lapsView = new TextView(this);
        lapsView.setTextSize(20);
        lapsView.setTypeface(Typeface.MONOSPACE);
        lapsView.setPadding(0, 32, 0, 0);
        ScrollView scroll = new ScrollView(this);
        scroll.addView(lapsView);

        root.addView(timeView, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 300));
        root.addView(row, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));
        root.addView(scroll, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f));
        setContentView(root);
        refresh();
    }

    private long elapsed() {
        return running ? accumulated + (SystemClock.elapsedRealtime() - startAt) : accumulated;
    }

    private void toggle() {
        if (running) {
            accumulated = elapsed();
            running = false;
        } else {
            startAt = SystemClock.elapsedRealtime();
            running = true;
            handler.post(ticker);
        }
        refresh();
    }

    private void lapOrReset() {
        if (running) {
            long total = elapsed();
            laps.add(0, String.format(Locale.US, "#%02d  %s   %s",
                    laps.size() + 1, format(total - lastLapTotal), format(total)));
            lastLapTotal = total;
        } else {
            accumulated = 0;
            lastLapTotal = 0;
            laps.clear();
        }
        refresh();
    }

    private void refresh() {
        startStop.setText(running ? "Stop" : "Start");
        lapReset.setText(running ? "Lap" : "Reset");
        timeView.setText(format(elapsed()));
        StringBuilder sb = new StringBuilder();
        for (String l : laps) sb.append(l).append('\n');
        lapsView.setText(sb.toString());
    }

    private static String format(long ms) {
        long min = ms / 60000;
        long sec = (ms / 1000) % 60;
        long cs = (ms / 10) % 100;
        return String.format(Locale.US, "%02d:%02d.%02d", min, sec, cs);
    }

    @Override
    protected void onDestroy() {
        handler.removeCallbacks(ticker);
        super.onDestroy();
    }
}
