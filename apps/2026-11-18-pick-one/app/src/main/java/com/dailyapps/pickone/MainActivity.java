package com.dailyapps.pickone;

import android.app.Activity;
import android.content.SharedPreferences;
import android.graphics.Typeface;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.InputType;
import android.view.Gravity;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.util.Random;

public class MainActivity extends Activity {
    private EditText input;
    private TextView result;
    private SharedPreferences prefs;
    private final Random random = new Random();
    private final Handler handler = new Handler(Looper.getMainLooper());

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        prefs = getSharedPreferences("pickone", MODE_PRIVATE);

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(48, 48, 48, 48);

        TextView hint = new TextView(this);
        hint.setText("Options (one per line)");
        hint.setTextSize(16);

        input = new EditText(this);
        input.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_MULTI_LINE);
        input.setMinLines(6);
        input.setGravity(Gravity.TOP);
        input.setText(prefs.getString("options", "Pizza\nTacos\nSushi"));

        Button pick = new Button(this);
        pick.setText("Pick one!");
        pick.setTextSize(24);
        pick.setOnClickListener(v -> pick());

        result = new TextView(this);
        result.setTextSize(40);
        result.setTypeface(Typeface.DEFAULT_BOLD);
        result.setGravity(Gravity.CENTER);

        root.addView(hint);
        root.addView(input);
        root.addView(pick, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 200));
        root.addView(result, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f));
        setContentView(root);
    }

    private String[] options() {
        String[] raw = input.getText().toString().split("\n");
        java.util.ArrayList<String> list = new java.util.ArrayList<>();
        for (String s : raw) {
            if (!s.trim().isEmpty()) list.add(s.trim());
        }
        return list.toArray(new String[0]);
    }

    private void pick() {
        final String[] opts = options();
        prefs.edit().putString("options", input.getText().toString()).apply();
        if (opts.length == 0) {
            result.setText("Add some options first");
            return;
        }
        handler.removeCallbacksAndMessages(null);
        final int steps = 12;
        for (int i = 0; i <= steps; i++) {
            final boolean last = i == steps;
            handler.postDelayed(() ->
                    result.setText(opts[random.nextInt(opts.length)] + (last ? " ✓" : "")),
                    i * i * 8L + i * 40L);
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        prefs.edit().putString("options", input.getText().toString()).apply();
    }
}
