package com.dailyapps.harmonograph;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.MediaStore;
import android.text.InputType;
import android.view.Gravity;
import android.view.HapticFeedbackConstants;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.HorizontalScrollView;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;

import java.io.OutputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Random;

public class MainActivity extends Activity {
    private static final int BG = 0xFF101218;
    private static final int PANEL = 0xFF181B24;
    private static final int CARD = 0xFF222633;
    private static final int ACCENT = 0xFF8AB4FF;
    private static final int TEXT = 0xFFE8EAF0;
    private static final int SUB = 0xFF9AA0B0;
    private static final int REQ_WRITE = 7;

    private Params params = new Params();
    private int sel = Params.X1;
    private int speed = 3;
    private final Random rnd = new Random();
    private final Handler ui = new Handler(Looper.getMainLooper());

    private HarmonographView hview;
    private View editorRoot;
    private boolean inGallery = false;
    private boolean updating = false;

    private final TextView[] pendChips = new TextView[Params.N];
    private final List<TextView> paletteChips = new ArrayList<>();
    private SliderRow sFreq, sPhase, sAmp, sDamp, sWidth, sLength, sSpeed;

    private static class SliderRow {
        TextView label;
        SeekBar bar;
        String name;
    }

    private interface OnSlide {
        void changed(int progress);
    }

    private SharedPreferences prefs() {
        return getSharedPreferences("harmonograph", MODE_PRIVATE);
    }

    private int dp(float v) {
        return (int) (v * getResources().getDisplayMetrics().density + 0.5f);
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().setStatusBarColor(BG);
        getWindow().setNavigationBarColor(BG);
        Params saved = Params.decode(prefs().getString("current", null));
        if (saved != null) params = saved;
        speed = prefs().getInt("speed", 3);
        editorRoot = buildEditor();
        setContentView(editorRoot);
        refreshControls();
        hview.setSpeed(speed);
        hview.animateParams(params);
    }

    @Override
    protected void onPause() {
        super.onPause();
        prefs().edit().putString("current", params.encode()).putInt("speed", speed).apply();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        Synth.release();
    }

    @Override
    public void onBackPressed() {
        if (inGallery) {
            showEditor();
        } else {
            super.onBackPressed();
        }
    }

    // ---------------------------------------------------------------- UI helpers

    private GradientDrawable rounded(int color, float radiusDp) {
        GradientDrawable g = new GradientDrawable();
        g.setColor(color);
        g.setCornerRadius(dp(radiusDp));
        return g;
    }

    private TextView button(String label, View.OnClickListener l) {
        TextView b = new TextView(this);
        b.setText(label);
        b.setTextColor(TEXT);
        b.setTextSize(14);
        b.setTypeface(Typeface.DEFAULT_BOLD);
        b.setGravity(Gravity.CENTER);
        b.setPadding(dp(10), dp(10), dp(10), dp(10));
        b.setMinHeight(dp(44));
        b.setBackground(rounded(CARD, 10));
        b.setOnClickListener(l);
        return b;
    }

    private TextView chip(String label, View.OnClickListener l) {
        TextView c = new TextView(this);
        c.setText(label);
        c.setTextSize(13);
        c.setGravity(Gravity.CENTER);
        c.setPadding(dp(14), dp(8), dp(14), dp(8));
        c.setMinHeight(dp(38));
        c.setOnClickListener(l);
        setChip(c, false);
        return c;
    }

    private void setChip(TextView c, boolean on) {
        c.setBackground(rounded(on ? ACCENT : CARD, 19));
        c.setTextColor(on ? 0xFF0B1020 : TEXT);
        c.setTypeface(on ? Typeface.DEFAULT_BOLD : Typeface.DEFAULT);
    }

    private LinearLayout.LayoutParams weightLp(float w) {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, w);
        lp.setMargins(dp(3), dp(3), dp(3), dp(3));
        return lp;
    }

    private TextView heading(String s) {
        TextView t = new TextView(this);
        t.setText(s);
        t.setTextColor(SUB);
        t.setTextSize(12);
        t.setTypeface(Typeface.DEFAULT_BOLD);
        t.setLetterSpacing(0.08f);
        t.setPadding(dp(4), dp(14), dp(4), dp(4));
        return t;
    }

    private SliderRow slider(LinearLayout parent, String name, int max, final OnSlide cb) {
        final SliderRow r = new SliderRow();
        r.name = name;
        r.label = new TextView(this);
        r.label.setTextColor(TEXT);
        r.label.setTextSize(13);
        r.label.setPadding(dp(4), dp(6), dp(4), 0);
        parent.addView(r.label);
        r.bar = new SeekBar(this);
        r.bar.setMax(max);
        r.bar.setPadding(dp(16), dp(10), dp(16), dp(10));
        r.bar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar s, int progress, boolean fromUser) {
                if (updating || !fromUser) return;
                cb.changed(progress);
            }

            @Override
            public void onStartTrackingTouch(SeekBar s) {
            }

            @Override
            public void onStopTrackingTouch(SeekBar s) {
            }
        });
        parent.addView(r.bar, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        return r;
    }

    // ---------------------------------------------------------------- editor

    private View buildEditor() {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(BG);

        // top bar
        LinearLayout top = new LinearLayout(this);
        top.setOrientation(LinearLayout.HORIZONTAL);
        top.setGravity(Gravity.CENTER_VERTICAL);
        top.setPadding(dp(14), dp(8), dp(8), dp(4));
        TextView title = new TextView(this);
        title.setText("Harmonograph");
        title.setTextColor(TEXT);
        title.setTextSize(20);
        title.setTypeface(Typeface.create("serif", Typeface.BOLD));
        top.addView(title, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        TextView gal = button("Gallery", new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                showGallery();
            }
        });
        top.addView(gal, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        TextView more = button("Share", new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                showShareMenu();
            }
        });
        LinearLayout.LayoutParams mlp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        mlp.leftMargin = dp(6);
        top.addView(more, mlp);
        root.addView(top);

        hview = new HarmonographView(this);
        LinearLayout.LayoutParams hlp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 5);
        hlp.setMargins(dp(10), dp(4), dp(10), dp(6));
        root.addView(hview, hlp);

        // action row
        LinearLayout actions = new LinearLayout(this);
        actions.setOrientation(LinearLayout.HORIZONTAL);
        actions.setPadding(dp(7), 0, dp(7), 0);
        actions.addView(button("Random", new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                int pal = params.palette;
                float w = params.width, len = params.length;
                params.randomize(rnd);
                params.palette = pal;
                params.width = w;
                params.length = len;
                refreshControls();
                hview.animateParams(params);
            }
        }), weightLp(1));
        actions.addView(button("Mutate", new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                params.mutate(rnd);
                refreshControls();
                hview.animateParams(params);
            }
        }), weightLp(1));
        actions.addView(button("Replay", new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                hview.animateParams(params);
            }
        }), weightLp(1));
        actions.addView(button("Listen", new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Synth.play(params.copy());
            }
        }), weightLp(1));
        actions.addView(button("★ Save", new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                v.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS);
                saveToGallery();
            }
        }), weightLp(1.1f));
        root.addView(actions);

        // controls panel
        ScrollView sv = new ScrollView(this);
        sv.setBackground(rounded(PANEL, 16));
        LinearLayout.LayoutParams svlp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 4);
        svlp.setMargins(dp(8), dp(6), dp(8), dp(8));
        root.addView(sv, svlp);
        LinearLayout panel = new LinearLayout(this);
        panel.setOrientation(LinearLayout.VERTICAL);
        panel.setPadding(dp(10), dp(4), dp(10), dp(16));
        sv.addView(panel);

        panel.addView(heading("PENDULUM"));
        LinearLayout pend = new LinearLayout(this);
        pend.setOrientation(LinearLayout.HORIZONTAL);
        for (int i = 0; i < Params.N; i++) {
            final int idx = i;
            pendChips[i] = chip(Params.NAMES[i], new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    sel = idx;
                    refreshControls();
                }
            });
            pend.addView(pendChips[i], weightLp(i == Params.ROT ? 1.5f : 1f));
        }
        panel.addView(pend);

        sFreq = slider(panel, "Frequency", 775, new OnSlide() {
            @Override
            public void changed(int pr) {
                params.f[sel] = Params.F_MIN + pr / 100f;
                edited();
            }
        });
        LinearLayout fine = new LinearLayout(this);
        fine.setOrientation(LinearLayout.HORIZONTAL);
        fine.addView(button("− 0.01", new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                nudgeFreq(-0.01f);
            }
        }), weightLp(1));
        fine.addView(button("Snap to ratio", new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                params.f[sel] = Params.clamp(Math.round(params.f[sel] * 2f) / 2f, Params.F_MIN, Params.F_MAX);
                edited();
                refreshControls();
            }
        }), weightLp(1.6f));
        fine.addView(button("+ 0.01", new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                nudgeFreq(0.01f);
            }
        }), weightLp(1));
        panel.addView(fine);

        sPhase = slider(panel, "Phase", 360, new OnSlide() {
            @Override
            public void changed(int pr) {
                params.p[sel] = pr;
                edited();
            }
        });
        sAmp = slider(panel, "Amplitude", 100, new OnSlide() {
            @Override
            public void changed(int pr) {
                params.a[sel] = pr / 100f;
                edited();
            }
        });
        sDamp = slider(panel, "Damping", 300, new OnSlide() {
            @Override
            public void changed(int pr) {
                params.d[sel] = pr / 10000f;
                edited();
            }
        });

        panel.addView(heading("COLOUR"));
        HorizontalScrollView hs = new HorizontalScrollView(this);
        hs.setHorizontalScrollBarEnabled(false);
        LinearLayout pals = new LinearLayout(this);
        pals.setOrientation(LinearLayout.HORIZONTAL);
        for (int i = 0; i < Palettes.COUNT; i++) {
            final int idx = i;
            TextView c = chip(Palettes.NAMES[i], new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    params.palette = idx;
                    refreshControls();
                    edited();
                }
            });
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            lp.setMargins(dp(3), dp(3), dp(3), dp(3));
            pals.addView(c, lp);
            paletteChips.add(c);
        }
        hs.addView(pals);
        panel.addView(hs);

        panel.addView(heading("DRAWING"));
        sWidth = slider(panel, "Line width", 75, new OnSlide() {
            @Override
            public void changed(int pr) {
                params.width = 0.5f + pr / 10f;
                edited();
            }
        });
        sLength = slider(panel, "Run time", 380, new OnSlide() {
            @Override
            public void changed(int pr) {
                params.length = 20 + pr;
                edited();
            }
        });
        sSpeed = slider(panel, "Pen speed", 5, new OnSlide() {
            @Override
            public void changed(int pr) {
                speed = pr;
                hview.setSpeed(speed);
                refreshControls();
            }
        });

        panel.addView(heading("PRESETS"));
        HorizontalScrollView ph = new HorizontalScrollView(this);
        ph.setHorizontalScrollBarEnabled(false);
        LinearLayout prs = new LinearLayout(this);
        prs.setOrientation(LinearLayout.HORIZONTAL);
        final String[] names = Presets.NAMES;
        for (int i = 0; i < names.length; i++) {
            final int idx = i;
            TextView c = chip(names[i], new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    params = Presets.get(idx);
                    refreshControls();
                    hview.animateParams(params);
                }
            });
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            lp.setMargins(dp(3), dp(3), dp(3), dp(3));
            prs.addView(c, lp);
        }
        ph.addView(prs);
        panel.addView(ph);

        TextView tip = new TextView(this);
        tip.setText("Tip: tap the drawing to pause or replay. Frequencies near simple ratios (2:3, 3:4) make closed figures; a tiny detune (2.00 vs 3.01) makes them slowly rotate into woven patterns. Damping shrinks each swing over time.");
        tip.setTextColor(SUB);
        tip.setTextSize(12);
        tip.setPadding(dp(4), dp(14), dp(4), 0);
        panel.addView(tip);
        return root;
    }

    private void nudgeFreq(float delta) {
        params.f[sel] = Params.clamp(Math.round((params.f[sel] + delta) * 100f) / 100f, Params.F_MIN, Params.F_MAX);
        edited();
        refreshControls();
    }

    private void edited() {
        updateLabels();
        hview.showParams(params);
    }

    private void refreshControls() {
        updating = true;
        for (int i = 0; i < Params.N; i++) setChip(pendChips[i], i == sel);
        for (int i = 0; i < paletteChips.size(); i++) setChip(paletteChips.get(i), i == params.palette);
        sFreq.bar.setProgress(Math.round((params.f[sel] - Params.F_MIN) * 100f));
        sPhase.bar.setProgress(Math.round(params.p[sel]));
        sAmp.bar.setProgress(Math.round(params.a[sel] * 100f));
        sDamp.bar.setProgress(Math.round(params.d[sel] * 10000f));
        sWidth.bar.setProgress(Math.round((params.width - 0.5f) * 10f));
        sLength.bar.setProgress(Math.round(params.length - 20));
        sSpeed.bar.setProgress(speed);
        updating = false;
        updateLabels();
    }

    private void updateLabels() {
        String pn = Params.NAMES[sel];
        sFreq.label.setText(String.format(Locale.US, "%s frequency   %.2f", pn, params.f[sel]));
        sPhase.label.setText(String.format(Locale.US, "%s phase   %.0f°", pn, params.p[sel]));
        sAmp.label.setText(String.format(Locale.US, "%s amplitude   %.0f%%%s", pn, params.a[sel] * 100f,
                params.a[sel] <= 0 ? "  (off)" : ""));
        sDamp.label.setText(String.format(Locale.US, "%s damping   %.4f", pn, params.d[sel]));
        sWidth.label.setText(String.format(Locale.US, "Line width   %.1f", params.width));
        sLength.label.setText(String.format(Locale.US, "Run time   %.0f", params.length));
        String[] sp = {"Instant", "Slow", "Relaxed", "Normal", "Fast", "Very fast"};
        sSpeed.label.setText("Pen speed   " + sp[Math.max(0, Math.min(5, speed))]);
    }

    private void showEditor() {
        inGallery = false;
        setContentView(editorRoot);
    }

    // ---------------------------------------------------------------- gallery

    private static class Entry {
        String name;
        String code;
    }

    private List<Entry> loadGallery() {
        List<Entry> list = new ArrayList<>();
        String raw = prefs().getString("gallery", "");
        if (raw == null || raw.isEmpty()) return list;
        for (String line : raw.split("\n")) {
            int bar = line.indexOf('|');
            if (bar <= 0) continue;
            Entry e = new Entry();
            e.name = line.substring(0, bar);
            e.code = line.substring(bar + 1);
            if (Params.decode(e.code) != null) list.add(e);
        }
        return list;
    }

    private void storeGallery(List<Entry> list) {
        StringBuilder sb = new StringBuilder();
        for (Entry e : list) {
            if (sb.length() > 0) sb.append('\n');
            sb.append(e.name.replace("|", "/").replace("\n", " ")).append('|').append(e.code);
        }
        prefs().edit().putString("gallery", sb.toString()).apply();
    }

    private void saveToGallery() {
        List<Entry> list = loadGallery();
        int counter = prefs().getInt("counter", 0) + 1;
        prefs().edit().putInt("counter", counter).apply();
        Entry e = new Entry();
        e.name = "Harmonogram #" + counter;
        e.code = params.encode();
        list.add(0, e);
        storeGallery(list);
        Toast.makeText(this, "Saved to gallery as " + e.name, Toast.LENGTH_SHORT).show();
    }

    private void showGallery() {
        inGallery = true;
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(BG);

        LinearLayout top = new LinearLayout(this);
        top.setOrientation(LinearLayout.HORIZONTAL);
        top.setGravity(Gravity.CENTER_VERTICAL);
        top.setPadding(dp(8), dp(8), dp(8), dp(4));
        top.addView(button("← Back", new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                showEditor();
            }
        }));
        TextView title = new TextView(this);
        title.setText("  Gallery");
        title.setTextColor(TEXT);
        title.setTextSize(20);
        title.setTypeface(Typeface.create("serif", Typeface.BOLD));
        top.addView(title, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        top.addView(button("Import code", new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                importDialog();
            }
        }));
        root.addView(top);

        final List<Entry> list = loadGallery();
        ScrollView sv = new ScrollView(this);
        LinearLayout col = new LinearLayout(this);
        col.setOrientation(LinearLayout.VERTICAL);
        col.setPadding(dp(8), dp(4), dp(8), dp(16));
        sv.addView(col);
        root.addView(sv, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1));

        if (list.isEmpty()) {
            TextView empty = new TextView(this);
            empty.setText("Nothing saved yet.\n\nTap ★ Save on a drawing you like and it will appear here. You can also import a code someone shared with you.");
            empty.setTextColor(SUB);
            empty.setTextSize(15);
            empty.setGravity(Gravity.CENTER);
            empty.setPadding(dp(24), dp(60), dp(24), dp(24));
            col.addView(empty);
        }

        final int thumbPx = (getResources().getDisplayMetrics().widthPixels - dp(40)) / 2;
        final List<ImageView> images = new ArrayList<>();
        LinearLayout row = null;
        for (int i = 0; i < list.size(); i++) {
            if (i % 2 == 0) {
                row = new LinearLayout(this);
                row.setOrientation(LinearLayout.HORIZONTAL);
                col.addView(row);
            }
            final int idx = i;
            final Entry e = list.get(i);
            LinearLayout cell = new LinearLayout(this);
            cell.setOrientation(LinearLayout.VERTICAL);
            cell.setBackground(rounded(CARD, 12));
            cell.setPadding(dp(6), dp(6), dp(6), dp(6));
            ImageView iv = new ImageView(this);
            iv.setScaleType(ImageView.ScaleType.FIT_CENTER);
            Params ep = Params.decode(e.code);
            iv.setBackgroundColor(ep != null ? Palettes.BG[ep.palette] : Color.BLACK);
            cell.addView(iv, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, thumbPx - dp(12)));
            images.add(iv);
            TextView name = new TextView(this);
            name.setText(e.name);
            name.setTextColor(TEXT);
            name.setTextSize(13);
            name.setSingleLine(true);
            name.setPadding(dp(2), dp(6), dp(2), dp(2));
            cell.addView(name);
            cell.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    Params lp = Params.decode(e.code);
                    if (lp != null) {
                        params = lp;
                        showEditor();
                        refreshControls();
                        hview.animateParams(params);
                    }
                }
            });
            cell.setOnLongClickListener(new View.OnLongClickListener() {
                @Override
                public boolean onLongClick(View v) {
                    entryMenu(list, idx);
                    return true;
                }
            });
            row.addView(cell, weightLp(1));
        }
        if (list.size() % 2 == 1 && row != null) {
            View spacer = new View(this);
            row.addView(spacer, weightLp(1));
        }
        if (!list.isEmpty()) {
            TextView hint = new TextView(this);
            hint.setText("Tap to open. Long-press to rename, copy code or delete.");
            hint.setTextColor(SUB);
            hint.setTextSize(12);
            hint.setGravity(Gravity.CENTER);
            hint.setPadding(0, dp(12), 0, 0);
            col.addView(hint);
        }
        setContentView(root);

        // render thumbnails off the UI thread
        final int size = Math.max(64, Math.min(400, thumbPx));
        new Thread(new Runnable() {
            @Override
            public void run() {
                for (int i = 0; i < list.size(); i++) {
                    Params ep = Params.decode(list.get(i).code);
                    if (ep == null) continue;
                    final Bitmap b = Renderer.renderBitmap(ep, size, 2);
                    final ImageView iv = images.get(i);
                    ui.post(new Runnable() {
                        @Override
                        public void run() {
                            iv.setImageBitmap(b);
                        }
                    });
                }
            }
        }).start();
    }

    private void entryMenu(final List<Entry> list, final int idx) {
        final Entry e = list.get(idx);
        String[] opts = {"Rename", "Copy code", "Delete"};
        new AlertDialog.Builder(this)
                .setTitle(e.name)
                .setItems(opts, (d, which) -> {
                    if (which == 0) {
                        final EditText et = new EditText(this);
                        et.setText(e.name);
                        et.setSingleLine(true);
                        new AlertDialog.Builder(this)
                                .setTitle("Rename")
                                .setView(et)
                                .setPositiveButton("OK", (d2, w2) -> {
                                    String n = et.getText().toString().trim();
                                    if (!n.isEmpty()) {
                                        e.name = n;
                                        storeGallery(list);
                                        showGallery();
                                    }
                                })
                                .setNegativeButton("Cancel", null)
                                .show();
                    } else if (which == 1) {
                        copyText(e.code);
                    } else {
                        new AlertDialog.Builder(this)
                                .setTitle("Delete " + e.name + "?")
                                .setPositiveButton("Delete", (d3, w3) -> {
                                    list.remove(idx);
                                    storeGallery(list);
                                    showGallery();
                                })
                                .setNegativeButton("Cancel", null)
                                .show();
                    }
                })
                .show();
    }

    private void importDialog() {
        final EditText et = new EditText(this);
        et.setHint("Paste a code starting with H1;");
        et.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_MULTI_LINE);
        ClipboardManager cm = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
        if (cm != null && cm.hasPrimaryClip() && cm.getPrimaryClip() != null && cm.getPrimaryClip().getItemCount() > 0) {
            CharSequence cs = cm.getPrimaryClip().getItemAt(0).getText();
            if (cs != null && cs.toString().contains("H1;")) et.setText(cs);
        }
        new AlertDialog.Builder(this)
                .setTitle("Import harmonogram")
                .setView(et)
                .setPositiveButton("Import", (d, w) -> {
                    Params p = Params.decode(et.getText().toString());
                    if (p == null) {
                        Toast.makeText(this, "That doesn't look like a harmonograph code", Toast.LENGTH_LONG).show();
                        return;
                    }
                    List<Entry> list = loadGallery();
                    Entry e = new Entry();
                    e.name = "Imported " + (list.size() + 1);
                    e.code = p.encode();
                    list.add(0, e);
                    storeGallery(list);
                    showGallery();
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    // ---------------------------------------------------------------- sharing

    private void copyText(String s) {
        ClipboardManager cm = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
        if (cm != null) {
            cm.setPrimaryClip(ClipData.newPlainText("harmonograph", s));
            Toast.makeText(this, "Code copied", Toast.LENGTH_SHORT).show();
        }
    }

    private void showShareMenu() {
        String[] opts = {"Save image to Pictures", "Share image", "Copy settings code", "Share settings code"};
        new AlertDialog.Builder(this)
                .setTitle("Share this drawing")
                .setItems(opts, (d, which) -> {
                    if (which == 0) exportImage(false);
                    else if (which == 1) exportImage(true);
                    else if (which == 2) copyText(params.encode());
                    else {
                        Intent i = new Intent(Intent.ACTION_SEND);
                        i.setType("text/plain");
                        i.putExtra(Intent.EXTRA_TEXT, "My harmonograph (" + params.ratioText().replaceAll("\\s+", " ")
                                + "). Import this code in the Harmonograph app: " + params.encode());
                        startActivity(Intent.createChooser(i, "Share code"));
                    }
                })
                .show();
    }

    private boolean pendingShare = false;

    private void exportImage(final boolean share) {
        if (Build.VERSION.SDK_INT < 29
                && checkSelfPermission(Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
            pendingShare = share;
            requestPermissions(new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE}, REQ_WRITE);
            return;
        }
        Toast.makeText(this, "Rendering 2048 × 2048…", Toast.LENGTH_SHORT).show();
        final Params p = params.copy();
        new Thread(new Runnable() {
            @Override
            public void run() {
                Uri uri = null;
                String err = null;
                try {
                    Bitmap bmp = Renderer.renderBitmap(p, 2048, 1);
                    uri = saveBitmap(bmp);
                    bmp.recycle();
                } catch (Throwable t) {
                    err = t.getMessage();
                }
                final Uri fu = uri;
                final String fe = err;
                ui.post(new Runnable() {
                    @Override
                    public void run() {
                        if (fu == null) {
                            Toast.makeText(MainActivity.this, "Could not save image" + (fe != null ? ": " + fe : ""), Toast.LENGTH_LONG).show();
                            return;
                        }
                        if (share) {
                            Intent i = new Intent(Intent.ACTION_SEND);
                            i.setType("image/png");
                            i.putExtra(Intent.EXTRA_STREAM, fu);
                            i.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
                            startActivity(Intent.createChooser(i, "Share image"));
                        } else {
                            Toast.makeText(MainActivity.this, "Saved to Pictures/Harmonograph", Toast.LENGTH_LONG).show();
                        }
                    }
                });
            }
        }).start();
    }

    private Uri saveBitmap(Bitmap bmp) throws Exception {
        String name = "harmonograph_" + System.currentTimeMillis() + ".png";
        ContentResolver cr = getContentResolver();
        ContentValues cv = new ContentValues();
        cv.put(MediaStore.MediaColumns.DISPLAY_NAME, name);
        cv.put(MediaStore.MediaColumns.MIME_TYPE, "image/png");
        if (Build.VERSION.SDK_INT >= 29) {
            cv.put(MediaStore.MediaColumns.RELATIVE_PATH, "Pictures/Harmonograph");
            cv.put(MediaStore.MediaColumns.IS_PENDING, 1);
        }
        Uri uri = cr.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, cv);
        if (uri == null) throw new Exception("no media store entry");
        OutputStream os = cr.openOutputStream(uri);
        if (os == null) throw new Exception("cannot open output");
        try {
            bmp.compress(Bitmap.CompressFormat.PNG, 100, os);
        } finally {
            os.close();
        }
        if (Build.VERSION.SDK_INT >= 29) {
            ContentValues done = new ContentValues();
            done.put(MediaStore.MediaColumns.IS_PENDING, 0);
            cr.update(uri, done, null, null);
        }
        return uri;
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == REQ_WRITE) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                exportImage(pendingShare);
            } else {
                Toast.makeText(this, "Storage permission is needed to save images on this Android version", Toast.LENGTH_LONG).show();
            }
        }
    }
}
