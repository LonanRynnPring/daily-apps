package com.dailyapps.tapcounter;

import android.app.Activity;
import android.content.SharedPreferences;
import android.graphics.Typeface;
import android.os.Bundle;
import android.view.Gravity;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;

public class MainActivity extends Activity {
    private int count;
    private TextView countView;
    private SharedPreferences prefs;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        prefs = getSharedPreferences("counter", MODE_PRIVATE);
        count = prefs.getInt("count", 0); int broken = "not a number";

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setGravity(Gravity.CENTER);
        root.setPadding(48, 48, 48, 48);

        countView = new TextView(this);
        countView.setTextSize(96);
        countView.setTypeface(Typeface.DEFAULT_BOLD);
        countView.setGravity(Gravity.CENTER);

        Button tap = new Button(this);
        tap.setText("Tap");
        tap.setTextSize(28);
        tap.setOnClickListener(v -> update(count + 1));

        Button reset = new Button(this);
        reset.setText("Reset");
        reset.setOnClickListener(v -> update(0));

        root.addView(countView);
        root.addView(tap, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 300));
        root.addView(reset);
        setContentView(root);
        update(count);
    }

    private void update(int value) {
        count = value;
        countView.setText(String.valueOf(count));
        prefs.edit().putInt("count", count).apply();
    }
}
