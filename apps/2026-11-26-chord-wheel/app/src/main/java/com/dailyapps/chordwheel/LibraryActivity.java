package com.dailyapps.chordwheel;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import java.text.DateFormat;
import java.util.Date;
import java.util.List;

/** Lists built-in progressions and the user's saved ones; tapping loads one into the editor. */
public class LibraryActivity extends Activity {
    static final String EX_NAME = "name";
    static final String EX_TONIC = "tonic";
    static final String EX_MINOR = "minor";
    static final String EX_ITEMS = "items";

    private LinearLayout list;
    private int curTonic;
    private boolean curMinor;

    private int dp(float v) {
        return (int) (v * getResources().getDisplayMetrics().density + 0.5f);
    }

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        getWindow().setStatusBarColor(Color.rgb(18, 19, 26));
        curTonic = getIntent().getIntExtra(EX_TONIC, 0);
        curMinor = getIntent().getBooleanExtra(EX_MINOR, false);

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(Color.rgb(18, 19, 26));

        LinearLayout bar = new LinearLayout(this);
        bar.setGravity(Gravity.CENTER_VERTICAL);
        bar.setPadding(dp(16), dp(14), dp(12), dp(8));
        TextView title = new TextView(this);
        title.setText("Library");
        title.setTextColor(Color.WHITE);
        title.setTextSize(24);
        title.setTypeface(Typeface.DEFAULT_BOLD);
        bar.addView(title, new LinearLayout.LayoutParams(0, -2, 1));
        Button back = new Button(this);
        back.setText("Back");
        back.setAllCaps(false);
        back.setTextColor(Color.WHITE);
        back.setBackground(round(Color.rgb(45, 48, 66)));
        back.setOnClickListener(v -> finish());
        bar.addView(back, new LinearLayout.LayoutParams(dp(88), dp(44)));
        root.addView(bar);

        ScrollView sv = new ScrollView(this);
        list = new LinearLayout(this);
        list.setOrientation(LinearLayout.VERTICAL);
        list.setPadding(dp(12), 0, dp(12), dp(24));
        sv.addView(list);
        root.addView(sv, new LinearLayout.LayoutParams(-1, 0, 1));
        setContentView(root);
        rebuild();
    }

    private GradientDrawable round(int color) {
        GradientDrawable g = new GradientDrawable();
        g.setColor(color);
        g.setCornerRadius(dp(12));
        return g;
    }

    private void header(String s) {
        TextView t = new TextView(this);
        t.setText(s);
        t.setTextColor(Color.rgb(255, 184, 77));
        t.setTextSize(13);
        t.setTypeface(Typeface.DEFAULT_BOLD);
        t.setLetterSpacing(0.08f);
        t.setPadding(dp(4), dp(18), 0, dp(6));
        list.addView(t);
    }

    private View row(String title, String sub1, String sub2) {
        LinearLayout r = new LinearLayout(this);
        r.setOrientation(LinearLayout.VERTICAL);
        r.setPadding(dp(14), dp(10), dp(14), dp(10));
        r.setBackground(round(Color.rgb(29, 31, 43)));
        TextView t = new TextView(this);
        t.setText(title);
        t.setTextColor(Color.WHITE);
        t.setTextSize(17);
        t.setTypeface(Typeface.DEFAULT_BOLD);
        r.addView(t);
        TextView a = new TextView(this);
        a.setText(sub1);
        a.setTextColor(Color.rgb(200, 205, 225));
        a.setTextSize(14);
        r.addView(a);
        if (sub2 != null) {
            TextView c = new TextView(this);
            c.setText(sub2);
            c.setTextColor(Color.rgb(138, 143, 163));
            c.setTextSize(12);
            r.addView(c);
        }
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2);
        lp.bottomMargin = dp(8);
        r.setLayoutParams(lp);
        r.setClickable(true);
        return r;
    }

    private void rebuild() {
        list.removeAllViews();
        List<Store.Saved> saved = Store.load(this);
        header("YOUR PROGRESSIONS");
        if (saved.isEmpty()) {
            TextView t = new TextView(this);
            t.setText("Nothing saved yet. Write a progression and tap Save.");
            t.setTextColor(Color.rgb(138, 143, 163));
            t.setPadding(dp(4), dp(4), dp(4), dp(8));
            list.addView(t);
        }
        DateFormat df = DateFormat.getDateInstance(DateFormat.MEDIUM);
        for (int i = 0; i < saved.size(); i++) {
            final Store.Saved s = saved.get(i);
            final int index = i;
            List<Theory.Item> items = Theory.decode(s.items);
            View r = row(s.name, Theory.chordLine(s.tonic, s.minor, items),
                    Theory.keyName(s.tonic, s.minor) + "  ·  " + Theory.romanLine(s.minor, items)
                            + (s.time > 0 ? "  ·  " + df.format(new Date(s.time)) : ""));
            r.setOnClickListener(v -> pick(s.name, s.tonic, s.minor, s.items));
            r.setOnLongClickListener(v -> {
                new AlertDialog.Builder(this)
                        .setTitle(s.name)
                        .setItems(new String[]{"Share as text", "Delete"}, (d, which) -> {
                            if (which == 0) {
                                share(Store.shareText(s.name, s.tonic, s.minor, items));
                            } else {
                                new AlertDialog.Builder(this)
                                        .setMessage("Delete \"" + s.name + "\"?")
                                        .setPositiveButton("Delete", (d2, w2) -> {
                                            Store.remove(this, index);
                                            rebuild();
                                        })
                                        .setNegativeButton("Cancel", null)
                                        .show();
                            }
                        })
                        .show();
                return true;
            });
            list.addView(r);
        }

        header("CLASSIC PROGRESSIONS  (play in your current key)");
        for (final Store.Saved p : Store.presets()) {
            int tonic = curTonic;
            if (p.minor != curMinor) tonic = curMinor ? (curTonic + 3) % 12 : (curTonic + 9) % 12;
            final int t = tonic;
            List<Theory.Item> items = Theory.decode(p.items);
            View r = row(p.name, Theory.romanLine(p.minor, items),
                    "in " + Theory.keyName(t, p.minor) + ":  " + Theory.chordLine(t, p.minor, items));
            r.setOnClickListener(v -> pick(p.name, t, p.minor, p.items));
            list.addView(r);
        }
    }

    private void share(String text) {
        Intent i = new Intent(Intent.ACTION_SEND);
        i.setType("text/plain");
        i.putExtra(Intent.EXTRA_TEXT, text);
        try {
            startActivity(Intent.createChooser(i, "Share progression"));
        } catch (Exception ignored) {
        }
    }

    private void pick(String name, int tonic, boolean minor, String items) {
        Intent data = new Intent();
        data.putExtra(EX_NAME, name);
        data.putExtra(EX_TONIC, tonic);
        data.putExtra(EX_MINOR, minor);
        data.putExtra(EX_ITEMS, items);
        setResult(RESULT_OK, data);
        finish();
    }
}
