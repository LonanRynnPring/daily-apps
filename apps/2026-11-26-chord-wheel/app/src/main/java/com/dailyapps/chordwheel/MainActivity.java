package com.dailyapps.chordwheel;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.text.InputType;
import android.view.Gravity;
import android.view.HapticFeedbackConstants;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.List;

public class MainActivity extends Activity {
    private static final int REQ_LIBRARY = 7;
    private static final int MAX_ITEMS = 32;
    private static final int[] BEAT_CHOICES = {1, 2, 3, 4, 6, 8};
    private static final String[] TONES = {"Soft keys", "Organ", "Pluck"};

    private static final int BG = Color.rgb(18, 19, 26);
    private static final int PANEL = Color.rgb(29, 31, 43);
    private static final int BTN = Color.rgb(45, 48, 66);
    private static final int ACCENT = Color.rgb(255, 184, 77);
    private static final int DIM = Color.rgb(138, 143, 163);

    private final Synth synth = new Synth();
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final List<Theory.Item> items = new ArrayList<>();

    private int tonic = 0;
    private boolean minor = false;
    private boolean sevenths = false;
    private boolean writeMode = true;
    private boolean loop = true;
    private boolean metronome = false;
    private int bpm = 96;
    private int beatsIdx = 3;
    private int toneIdx = 0;
    private String currentName = "";

    private boolean playing = false;
    private int beat = 0;
    private long nextTime = 0;

    private WheelView wheel;
    private ProgressionView progView;
    private TextView keyLabel, progTitle, infoLabel;
    private final Button[] chordBtns = new Button[7];
    private Button playBtn, sevBtn, writeBtn, loopBtn, clickBtn, beatsBtn, toneBtn;
    private TextView bpmLabel;

    private int dp(float v) {
        return (int) (v * getResources().getDisplayMetrics().density + 0.5f);
    }

    private GradientDrawable round(int color, int strokeColor) {
        GradientDrawable g = new GradientDrawable();
        g.setColor(color);
        g.setCornerRadius(dp(12));
        if (strokeColor != 0) g.setStroke(dp(2), strokeColor);
        return g;
    }

    private Button button(String label) {
        Button b = new Button(this);
        b.setText(label);
        b.setAllCaps(false);
        b.setTextColor(Color.WHITE);
        b.setTextSize(14);
        b.setPadding(dp(4), 0, dp(4), 0);
        b.setMinWidth(0);
        b.setMinimumWidth(0);
        b.setBackground(round(BTN, 0));
        return b;
    }

    private LinearLayout.LayoutParams weighted(int heightDp) {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, dp(heightDp), 1);
        lp.setMargins(dp(3), dp(3), dp(3), dp(3));
        return lp;
    }

    private LinearLayout row() {
        LinearLayout r = new LinearLayout(this);
        r.setOrientation(LinearLayout.HORIZONTAL);
        r.setGravity(Gravity.CENTER_VERTICAL);
        return r;
    }

    private TextView label(String s, int sizeSp, int color, boolean bold) {
        TextView t = new TextView(this);
        t.setText(s);
        t.setTextSize(sizeSp);
        t.setTextColor(color);
        if (bold) t.setTypeface(Typeface.DEFAULT_BOLD);
        return t;
    }

    private void haptic(View v) {
        v.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY);
    }

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        getWindow().setStatusBarColor(BG);
        loadState();

        ScrollView scroll = new ScrollView(this);
        scroll.setBackgroundColor(BG);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(12), dp(10), dp(12), dp(24));
        scroll.addView(root);

        // title bar
        LinearLayout bar = row();
        LinearLayout titles = new LinearLayout(this);
        titles.setOrientation(LinearLayout.VERTICAL);
        titles.addView(label("Chord Wheel", 24, Color.WHITE, true));
        keyLabel = label("", 14, ACCENT, false);
        titles.addView(keyLabel);
        bar.addView(titles, new LinearLayout.LayoutParams(0, -2, 1));
        Button lib = button("Library");
        lib.setOnClickListener(v -> {
            stopPlayback();
            Intent i = new Intent(this, LibraryActivity.class);
            i.putExtra(LibraryActivity.EX_TONIC, tonic);
            i.putExtra(LibraryActivity.EX_MINOR, minor);
            startActivityForResult(i, REQ_LIBRARY);
        });
        bar.addView(lib, new LinearLayout.LayoutParams(dp(96), dp(44)));
        root.addView(bar);

        // wheel
        wheel = new WheelView(this);
        wheel.setListener(new WheelView.Listener() {
            @Override
            public void onKeyPicked(int t, boolean m) {
                haptic(wheel);
                boolean changed = t != tonic || m != minor;
                tonic = t;
                minor = m;
                refreshAll();
                playDegree(0, sevenths, 1.2);
                if (changed && !items.isEmpty()) {
                    toast("Progression now in " + Theory.keyName(tonic, minor));
                }
            }

            @Override
            public void onCenterTapped() {
                haptic(wheel);
                if (!items.isEmpty()) togglePlay();
                else playDegree(0, sevenths, 1.2);
            }
        });
        LinearLayout.LayoutParams wl = new LinearLayout.LayoutParams(-1, -2);
        wl.gravity = Gravity.CENTER_HORIZONTAL;
        wl.topMargin = dp(8);
        wl.bottomMargin = dp(4);
        root.addView(wheel, wl);

        infoLabel = label("Tap a ring to change key · outer = major, inner = minor", 12, DIM, false);
        infoLabel.setGravity(Gravity.CENTER);
        root.addView(infoLabel, new LinearLayout.LayoutParams(-1, -2));

        // option toggles
        LinearLayout opts = row();
        opts.setPadding(0, dp(8), 0, 0);
        writeBtn = button("");
        writeBtn.setOnClickListener(v -> {
            writeMode = !writeMode;
            refreshToggles();
        });
        sevBtn = button("");
        sevBtn.setOnClickListener(v -> {
            sevenths = !sevenths;
            refreshChordButtons();
            refreshToggles();
        });
        toneBtn = button("");
        toneBtn.setOnClickListener(v -> {
            toneIdx = (toneIdx + 1) % TONES.length;
            synth.setTone(toneIdx);
            refreshToggles();
            playDegree(0, sevenths, 1.0);
        });
        opts.addView(writeBtn, weighted(40));
        opts.addView(sevBtn, weighted(40));
        opts.addView(toneBtn, weighted(40));
        root.addView(opts);

        // diatonic chord buttons
        LinearLayout r1 = row(), r2 = row();
        for (int i = 0; i < 7; i++) {
            final int deg = i;
            Button cb = button("");
            cb.setTextSize(15);
            cb.setOnClickListener(v -> onChordButton(deg, v));
            cb.setOnLongClickListener(v -> {
                haptic(v);
                playDegree(deg, !sevenths, 1.4);
                return true;
            });
            chordBtns[i] = cb;
            (i < 4 ? r1 : r2).addView(cb, weighted(62));
        }
        root.addView(r1);
        root.addView(r2);
        TextView hint = label("Glowing outline = good next chord · long-press a chord to hear it with/without the 7th",
                11, DIM, false);
        hint.setGravity(Gravity.CENTER);
        hint.setPadding(0, dp(2), 0, dp(6));
        root.addView(hint, new LinearLayout.LayoutParams(-1, -2));

        // progression
        LinearLayout ph = row();
        progTitle = label("", 16, Color.WHITE, true);
        ph.addView(progTitle, new LinearLayout.LayoutParams(0, -2, 1));
        Button undo = button("Undo");
        undo.setOnClickListener(v -> {
            if (!items.isEmpty()) {
                items.remove(items.size() - 1);
                refreshAll();
            }
        });
        Button clear = button("Clear");
        clear.setOnClickListener(v -> {
            if (items.isEmpty()) return;
            new AlertDialog.Builder(this)
                    .setMessage("Clear the whole progression?")
                    .setPositiveButton("Clear", (d, w) -> {
                        stopPlayback();
                        items.clear();
                        currentName = "";
                        refreshAll();
                    })
                    .setNegativeButton("Cancel", null)
                    .show();
        });
        ph.addView(undo, new LinearLayout.LayoutParams(dp(72), dp(38)));
        LinearLayout.LayoutParams cl = new LinearLayout.LayoutParams(dp(72), dp(38));
        cl.leftMargin = dp(6);
        ph.addView(clear, cl);
        LinearLayout.LayoutParams phl = new LinearLayout.LayoutParams(-1, -2);
        phl.topMargin = dp(10);
        root.addView(ph, phl);

        progView = new ProgressionView(this);
        progView.setListener(new ProgressionView.Listener() {
            @Override
            public void onCellTap(int index) {
                haptic(progView);
                Theory.Item it = items.get(index);
                if (!playing) progView.setCurrent(index);
                playDegree(it.degree, it.seventh, 1.3);
            }

            @Override
            public void onCellLongPress(int index) {
                haptic(progView);
                cellMenu(index);
            }
        });
        root.addView(progView, new LinearLayout.LayoutParams(-1, -2));

        // transport
        LinearLayout tr = row();
        tr.setPadding(0, dp(8), 0, 0);
        playBtn = button("▶  Play");
        playBtn.setTextSize(17);
        playBtn.setTypeface(Typeface.DEFAULT_BOLD);
        playBtn.setOnClickListener(v -> {
            haptic(v);
            togglePlay();
        });
        LinearLayout.LayoutParams pl = weighted(54);
        pl.weight = 1.4f;
        tr.addView(playBtn, pl);
        Button minus = button("−");
        minus.setTextSize(22);
        minus.setOnClickListener(v -> changeBpm(-4));
        minus.setOnLongClickListener(v -> {
            changeBpm(-20);
            return true;
        });
        bpmLabel = label("", 16, Color.WHITE, true);
        bpmLabel.setGravity(Gravity.CENTER);
        Button plus = button("+");
        plus.setTextSize(22);
        plus.setOnClickListener(v -> changeBpm(4));
        plus.setOnLongClickListener(v -> {
            changeBpm(20);
            return true;
        });
        tr.addView(minus, weighted(54));
        tr.addView(bpmLabel, weighted(54));
        tr.addView(plus, weighted(54));
        root.addView(tr);

        LinearLayout tr2 = row();
        beatsBtn = button("");
        beatsBtn.setOnClickListener(v -> {
            beatsIdx = (beatsIdx + 1) % BEAT_CHOICES.length;
            refreshToggles();
        });
        loopBtn = button("");
        loopBtn.setOnClickListener(v -> {
            loop = !loop;
            refreshToggles();
        });
        clickBtn = button("");
        clickBtn.setOnClickListener(v -> {
            metronome = !metronome;
            refreshToggles();
        });
        tr2.addView(beatsBtn, weighted(42));
        tr2.addView(loopBtn, weighted(42));
        tr2.addView(clickBtn, weighted(42));
        root.addView(tr2);

        LinearLayout act = row();
        Button save = button("Save");
        save.setOnClickListener(v -> saveDialog());
        Button share = button("Share");
        share.setOnClickListener(v -> shareCurrent());
        Button random = button("Inspire me");
        random.setOnClickListener(v -> inspire());
        act.addView(save, weighted(42));
        act.addView(share, weighted(42));
        act.addView(random, weighted(42));
        root.addView(act);

        setContentView(scroll);
        synth.setTone(toneIdx);
        refreshAll();
    }

    // ---------------------------------------------------------------- actions

    private void onChordButton(int deg, View v) {
        haptic(v);
        playDegree(deg, sevenths, 1.3);
        if (writeMode) {
            if (items.size() >= MAX_ITEMS) {
                toast("Progression is full (" + MAX_ITEMS + " chords)");
                return;
            }
            items.add(new Theory.Item(deg, sevenths));
            refreshAll();
        }
    }

    private void playDegree(int deg, boolean sev, double seconds) {
        synth.playNotes(Theory.midiNotes(tonic, minor, deg, sev), seconds);
        wheel.setHighlight(Theory.chordRoot(tonic, minor, deg), Theory.quality(minor, deg),
                Theory.chordName(tonic, minor, deg, sev));
        wheel.setCenterTop(Theory.roman(minor, deg, sev));
    }

    private void cellMenu(final int index) {
        if (index < 0 || index >= items.size()) return;
        final Theory.Item it = items.get(index);
        String[] opts = {
                it.seventh ? "Make it a triad" : "Add the 7th",
                "Duplicate",
                "Move earlier",
                "Move later",
                "Change chord…",
                "Delete"
        };
        new AlertDialog.Builder(this)
                .setTitle(Theory.roman(minor, it.degree, it.seventh) + "  ·  "
                        + Theory.chordName(tonic, minor, it.degree, it.seventh))
                .setItems(opts, (d, which) -> {
                    switch (which) {
                        case 0:
                            it.seventh = !it.seventh;
                            break;
                        case 1:
                            if (items.size() < MAX_ITEMS) items.add(index + 1, it.copy());
                            break;
                        case 2:
                            if (index > 0) {
                                items.remove(index);
                                items.add(index - 1, it);
                            }
                            break;
                        case 3:
                            if (index < items.size() - 1) {
                                items.remove(index);
                                items.add(index + 1, it);
                            }
                            break;
                        case 4:
                            changeChordDialog(index);
                            return;
                        case 5:
                            items.remove(index);
                            break;
                        default:
                            break;
                    }
                    refreshAll();
                })
                .show();
    }

    private void changeChordDialog(final int index) {
        String[] names = new String[7];
        for (int i = 0; i < 7; i++) {
            names[i] = Theory.roman(minor, i, items.get(index).seventh) + "   "
                    + Theory.chordName(tonic, minor, i, items.get(index).seventh);
        }
        new AlertDialog.Builder(this)
                .setTitle("Replace with")
                .setItems(names, (d, which) -> {
                    if (index < items.size()) {
                        items.get(index).degree = which;
                        playDegree(which, items.get(index).seventh, 1.2);
                        refreshAll();
                    }
                })
                .show();
    }

    private void changeBpm(int delta) {
        bpm = Math.max(40, Math.min(220, bpm + delta));
        refreshToggles();
    }

    private long beatMs() {
        return 60000L / bpm;
    }

    private final Runnable tick = new Runnable() {
        @Override
        public void run() {
            if (!playing) return;
            if (items.isEmpty()) {
                stopPlayback();
                return;
            }
            int bpc = BEAT_CHOICES[beatsIdx];
            int chordIdx = beat / bpc;
            int inChord = beat % bpc;
            if (chordIdx >= items.size()) {
                if (loop) {
                    beat = 0;
                    chordIdx = 0;
                    inChord = 0;
                } else {
                    stopPlayback();
                    return;
                }
            }
            if (inChord == 0) {
                Theory.Item it = items.get(chordIdx);
                double secs = beatMs() * bpc / 1000.0 * 0.97;
                playDegree(it.degree, it.seventh, secs);
                progView.setCurrent(chordIdx);
            }
            if (metronome) synth.click(inChord == 0);
            beat++;
            nextTime += beatMs();
            long now = SystemClock.uptimeMillis();
            if (nextTime < now) nextTime = now;
            handler.postAtTime(this, nextTime);
        }
    };

    private void togglePlay() {
        if (playing) {
            stopPlayback();
            return;
        }
        if (items.isEmpty()) {
            toast("Write a few chords first");
            return;
        }
        playing = true;
        beat = 0;
        nextTime = SystemClock.uptimeMillis();
        handler.post(tick);
        refreshToggles();
    }

    private void stopPlayback() {
        if (!playing) return;
        playing = false;
        handler.removeCallbacks(tick);
        synth.silence();
        progView.setCurrent(-1);
        wheel.clearHighlight();
        refreshToggles();
    }

    private void saveDialog() {
        if (items.isEmpty()) {
            toast("Nothing to save yet");
            return;
        }
        final EditText name = new EditText(this);
        name.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_CAP_SENTENCES);
        name.setText(currentName.length() > 0 ? currentName : "Idea in " + Theory.keyShort(tonic, minor));
        name.selectAll();
        LinearLayout wrap = new LinearLayout(this);
        wrap.setPadding(dp(20), dp(8), dp(20), 0);
        wrap.addView(name, new LinearLayout.LayoutParams(-1, -2));
        new AlertDialog.Builder(this)
                .setTitle("Save progression")
                .setView(wrap)
                .setPositiveButton("Save", (d, w) -> {
                    String n = name.getText().toString().trim();
                    if (n.length() == 0) n = "Untitled";
                    currentName = n;
                    Store.add(this, new Store.Saved(n, tonic, minor, Theory.encode(items),
                            System.currentTimeMillis()));
                    toast("Saved \"" + n + "\"");
                    refreshAll();
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void shareCurrent() {
        if (items.isEmpty()) {
            toast("Nothing to share yet");
            return;
        }
        String n = currentName.length() > 0 ? currentName : "My progression";
        Intent i = new Intent(Intent.ACTION_SEND);
        i.setType("text/plain");
        i.putExtra(Intent.EXTRA_TEXT, Store.shareText(n, tonic, minor, items));
        try {
            startActivity(Intent.createChooser(i, "Share progression"));
        } catch (Exception ignored) {
        }
    }

    /** Builds a fresh 4-chord progression by walking the suggestion graph from the tonic. */
    private void inspire() {
        stopPlayback();
        java.util.Random rnd = new java.util.Random();
        items.clear();
        int deg = 0;
        items.add(new Theory.Item(0, sevenths));
        int len = rnd.nextInt(3) == 0 ? 8 : 4;
        for (int i = 1; i < len; i++) {
            int[] s = Theory.suggestions(minor, deg);
            int next = s.length == 0 ? rnd.nextInt(7) : s[rnd.nextInt(s.length)];
            if (i == len - 1 && next == 0 && s.length > 1) next = s[(rnd.nextInt(s.length - 1) + 1) % s.length];
            if (!minor && next == 6 && rnd.nextBoolean()) next = 4;
            items.add(new Theory.Item(next, sevenths));
            deg = next;
        }
        currentName = "";
        refreshAll();
        togglePlay();
    }

    private void toast(String s) {
        Toast.makeText(this, s, Toast.LENGTH_SHORT).show();
    }

    // ---------------------------------------------------------------- refresh

    private void refreshAll() {
        wheel.setKey(tonic, minor);
        keyLabel.setText("Key: " + Theory.keyName(tonic, minor));
        progView.setData(items, tonic, minor);
        String t = currentName.length() > 0 ? currentName : "Progression";
        progTitle.setText(t + "  (" + items.size() + ")");
        refreshChordButtons();
        refreshToggles();
    }

    private void refreshChordButtons() {
        int last = items.isEmpty() ? -1 : items.get(items.size() - 1).degree;
        int[] sug = Theory.suggestions(minor, last);
        for (int i = 0; i < 7; i++) {
            boolean suggested = false;
            for (int s : sug) if (s == i) suggested = true;
            int q = Theory.quality(minor, i);
            int base;
            if (q == Theory.MAJ) base = Color.rgb(46, 74, 120);
            else if (q == Theory.MIN) base = Color.rgb(78, 52, 110);
            else base = Color.rgb(96, 60, 44);
            chordBtns[i].setBackground(round(base, suggested ? ACCENT : 0));
            chordBtns[i].setText(Theory.roman(minor, i, sevenths) + "\n"
                    + Theory.chordName(tonic, minor, i, sevenths));
        }
    }

    private void refreshToggles() {
        writeBtn.setText(writeMode ? "✎ Write: on" : "✎ Write: off");
        writeBtn.setBackground(round(writeMode ? PANEL : BTN, writeMode ? ACCENT : 0));
        sevBtn.setText(sevenths ? "7ths: on" : "7ths: off");
        sevBtn.setBackground(round(sevenths ? PANEL : BTN, sevenths ? ACCENT : 0));
        toneBtn.setText("♪ " + TONES[toneIdx]);
        bpmLabel.setText(bpm + "\nBPM");
        beatsBtn.setText(BEAT_CHOICES[beatsIdx] + (BEAT_CHOICES[beatsIdx] == 1 ? " beat" : " beats") + "/chord");
        loopBtn.setText(loop ? "Loop: on" : "Loop: off");
        loopBtn.setBackground(round(loop ? PANEL : BTN, loop ? ACCENT : 0));
        clickBtn.setText(metronome ? "Click: on" : "Click: off");
        clickBtn.setBackground(round(metronome ? PANEL : BTN, metronome ? ACCENT : 0));
        playBtn.setText(playing ? "■  Stop" : "▶  Play");
        playBtn.setTextColor(playing ? Color.rgb(20, 20, 28) : Color.WHITE);
        playBtn.setBackground(round(playing ? ACCENT : Color.rgb(40, 110, 80), 0));
    }

    // ---------------------------------------------------------------- lifecycle

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode != REQ_LIBRARY || resultCode != RESULT_OK || data == null) return;
        String enc = data.getStringExtra(LibraryActivity.EX_ITEMS);
        List<Theory.Item> loaded = Theory.decode(enc);
        if (loaded.isEmpty()) return;
        stopPlayback();
        items.clear();
        items.addAll(loaded);
        int t = data.getIntExtra(LibraryActivity.EX_TONIC, tonic);
        if (t >= 0 && t < 12) tonic = t;
        minor = data.getBooleanExtra(LibraryActivity.EX_MINOR, minor);
        String n = data.getStringExtra(LibraryActivity.EX_NAME);
        currentName = n == null ? "" : n;
        refreshAll();
        toast("Loaded \"" + currentName + "\" — tap the wheel to transpose");
    }

    @Override
    protected void onResume() {
        super.onResume();
        synth.start();
    }

    @Override
    protected void onPause() {
        super.onPause();
        stopPlayback();
        synth.stop();
        saveState();
    }

    private void loadState() {
        SharedPreferences p = getSharedPreferences("state", MODE_PRIVATE);
        tonic = p.getInt("tonic", 0);
        if (tonic < 0 || tonic > 11) tonic = 0;
        minor = p.getBoolean("minor", false);
        sevenths = p.getBoolean("sevenths", false);
        writeMode = p.getBoolean("write", true);
        loop = p.getBoolean("loop", true);
        metronome = p.getBoolean("click", false);
        bpm = Math.max(40, Math.min(220, p.getInt("bpm", 96)));
        beatsIdx = p.getInt("beatsIdx", 3);
        if (beatsIdx < 0 || beatsIdx >= BEAT_CHOICES.length) beatsIdx = 3;
        toneIdx = p.getInt("tone", 0);
        if (toneIdx < 0 || toneIdx >= TONES.length) toneIdx = 0;
        currentName = p.getString("name", "");
        items.clear();
        items.addAll(Theory.decode(p.getString("items", "0 4 5 3")));
    }

    private void saveState() {
        getSharedPreferences("state", MODE_PRIVATE).edit()
                .putInt("tonic", tonic)
                .putBoolean("minor", minor)
                .putBoolean("sevenths", sevenths)
                .putBoolean("write", writeMode)
                .putBoolean("loop", loop)
                .putBoolean("click", metronome)
                .putInt("bpm", bpm)
                .putInt("beatsIdx", beatsIdx)
                .putInt("tone", toneIdx)
                .putString("name", currentName)
                .putString("items", Theory.encode(items))
                .apply();
    }
}
