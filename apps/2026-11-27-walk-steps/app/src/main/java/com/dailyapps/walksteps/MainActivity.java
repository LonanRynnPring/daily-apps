package com.dailyapps.walksteps;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.speech.tts.TextToSpeech;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class MainActivity extends Activity {
    static final int BG = 0xFF0F1419;
    static final int PANEL = 0xFF1A2129;
    static final int PANEL2 = 0xFF242D37;
    static final int TEXT = 0xFFEEF2F6;
    static final int MUTED = 0xFF8D99A6;
    static final int ACCENT = 0xFF4CC38A;
    static final int DONE = 0xFF56606B;

    private SharedPreferences prefs;
    private List<Store.Saved> routes;
    private Store.Saved current;
    private boolean compact, fullNames, voice;

    private FrameLayout frame;
    private View homeView, routeView;
    private EditText input;
    private TextView status;
    private LinearLayout savedList;

    private TextView routeTitle, routeSummary, stepText, stepSub, stepNote;
    private ArrowView bigArrow;
    private LinearLayout stepList;
    private ScrollView stepScroll;
    private Button voiceBtn, compactBtn, namesBtn, prevBtn, nextBtn;

    private TextToSpeech tts;
    private boolean ttsReady;

    private int dp(float v) { return Math.round(v * getResources().getDisplayMetrics().density); }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().setStatusBarColor(BG);
        getWindow().setNavigationBarColor(BG);
        prefs = getSharedPreferences("walksteps", MODE_PRIVATE);
        routes = Store.load(prefs);
        compact = prefs.getBoolean("compact", false);
        fullNames = prefs.getBoolean("fullNames", true);
        voice = prefs.getBoolean("voice", false);

        frame = new FrameLayout(this);
        frame.setBackgroundColor(BG);
        homeView = buildHome();
        routeView = buildRoute();
        frame.addView(homeView);
        frame.addView(routeView);
        setContentView(frame);
        showHome();
        handleIntent(getIntent());
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        handleIntent(intent);
    }

    private void handleIntent(Intent intent) {
        if (intent == null || !Intent.ACTION_SEND.equals(intent.getAction())) return;
        CharSequence text = intent.getCharSequenceExtra(Intent.EXTRA_TEXT);
        String subject = intent.getStringExtra(Intent.EXTRA_SUBJECT);
        if (text == null) return;
        intent.setAction(Intent.ACTION_MAIN); // don't re-import on rotation
        importText(text.toString(), subject);
    }

    // ---------- helpers ----------

    private GradientDrawable round(int color, float radius) {
        GradientDrawable g = new GradientDrawable();
        g.setColor(color);
        g.setCornerRadius(dp(radius));
        return g;
    }

    private Button button(String label, int bg, int fg, int size) {
        Button b = new Button(this);
        b.setText(label);
        b.setAllCaps(false);
        b.setTextSize(size);
        b.setTextColor(fg);
        b.setBackground(round(bg, 14));
        b.setPadding(dp(12), 0, dp(12), 0);
        b.setMinWidth(0);
        b.setMinimumWidth(0);
        return b;
    }

    private TextView label(String s, int size, int color, boolean bold) {
        TextView t = new TextView(this);
        t.setText(s);
        t.setTextSize(size);
        t.setTextColor(color);
        if (bold) t.setTypeface(Typeface.DEFAULT_BOLD);
        return t;
    }

    private LinearLayout.LayoutParams lp(int w, int h, float weight) {
        return new LinearLayout.LayoutParams(w, h, weight);
    }

    private static final int MATCH = ViewGroup.LayoutParams.MATCH_PARENT;
    private static final int WRAP = ViewGroup.LayoutParams.WRAP_CONTENT;

    // ---------- home ----------

    private View buildHome() {
        ScrollView sv = new ScrollView(this);
        LinearLayout col = new LinearLayout(this);
        col.setOrientation(LinearLayout.VERTICAL);
        col.setPadding(dp(18), dp(20), dp(18), dp(24));
        sv.addView(col);

        col.addView(label("Walk Steps", 28, TEXT, true));
        TextView sub = label("Turn walking directions into a simple list of road names.", 15, MUTED, false);
        sub.setPadding(0, dp(4), 0, dp(16));
        col.addView(sub);

        Button paste = button("Paste directions from clipboard", ACCENT, 0xFF06231A, 17);
        paste.setTypeface(Typeface.DEFAULT_BOLD);
        paste.setOnClickListener(v -> pasteClipboard());
        col.addView(paste, lp(MATCH, dp(58), 0));

        input = new EditText(this);
        input.setHint("…or paste or type the directions here");
        input.setHintTextColor(MUTED);
        input.setTextColor(TEXT);
        input.setTextSize(14);
        input.setMinLines(4);
        input.setMaxLines(10);
        input.setGravity(Gravity.TOP | Gravity.START);
        input.setBackground(round(PANEL, 12));
        input.setPadding(dp(12), dp(10), dp(12), dp(10));
        LinearLayout.LayoutParams ilp = lp(MATCH, WRAP, 0);
        ilp.setMargins(0, dp(12), 0, dp(8));
        col.addView(input, ilp);

        LinearLayout row = new LinearLayout(this);
        Button simplify = button("Simplify", PANEL2, TEXT, 16);
        simplify.setOnClickListener(v -> importText(input.getText().toString(), null));
        Button sample = button("Try an example", PANEL2, MUTED, 14);
        sample.setOnClickListener(v -> input.setText(SAMPLE));
        LinearLayout.LayoutParams a = lp(0, dp(48), 1);
        a.setMargins(0, 0, dp(6), 0);
        row.addView(simplify, a);
        LinearLayout.LayoutParams b = lp(0, dp(48), 1);
        b.setMargins(dp(6), 0, 0, 0);
        row.addView(sample, b);
        col.addView(row);

        status = label("", 14, 0xFFFF9F7A, false);
        status.setPadding(0, dp(8), 0, 0);
        status.setVisibility(View.GONE);
        col.addView(status);

        LinearLayout help = new LinearLayout(this);
        help.setOrientation(LinearLayout.VERTICAL);
        help.setBackground(round(PANEL, 14));
        help.setPadding(dp(14), dp(12), dp(14), dp(14));
        help.addView(label("Getting directions from Google Maps", 15, TEXT, true));
        TextView steps = label(
                "1.  Search for where you're going (e.g. Tesco) and tap Directions, then the walking icon.\n"
                        + "2.  Open the list of steps (tap the route summary at the bottom).\n"
                        + "3.  Tap the ⋮ menu → Share directions, and pick Walk Steps.\n\n"
                        + "Or copy the step text and use the paste button above. "
                        + "A plain map link has no steps in it, so share the directions text, not just the link.",
                14, MUTED, false);
        steps.setPadding(0, dp(6), 0, 0);
        steps.setLineSpacing(dp(2), 1f);
        help.addView(steps);
        LinearLayout.LayoutParams hlp = lp(MATCH, WRAP, 0);
        hlp.setMargins(0, dp(18), 0, 0);
        col.addView(help, hlp);

        TextView sh = label("Saved routes", 15, ACCENT, true);
        sh.setPadding(0, dp(22), 0, dp(6));
        col.addView(sh);
        savedList = new LinearLayout(this);
        savedList.setOrientation(LinearLayout.VERTICAL);
        col.addView(savedList);
        return sv;
    }

    private void refreshSaved() {
        savedList.removeAllViews();
        if (routes.isEmpty()) {
            savedList.addView(label("Routes you simplify are kept here.", 14, MUTED, false));
            return;
        }
        SimpleDateFormat f = new SimpleDateFormat("EEE d MMM, HH:mm", Locale.getDefault());
        for (final Store.Saved s : routes) {
            LinearLayout r = new LinearLayout(this);
            r.setOrientation(LinearLayout.VERTICAL);
            r.setBackground(round(PANEL, 12));
            r.setPadding(dp(14), dp(10), dp(14), dp(10));
            TextView t = label(s.title, 16, TEXT, true);
            t.setSingleLine(true);
            t.setEllipsize(TextUtils.TruncateAt.END);
            r.addView(t);
            String info = s.steps.size() + " steps";
            if (!s.summary.isEmpty()) info += "  ·  " + s.summary;
            if (s.position > 0 && s.position < s.steps.size()) info += "  ·  at step " + (s.position + 1);
            info += "\n" + f.format(new Date(s.created));
            r.addView(label(info, 13, MUTED, false));
            r.setOnClickListener(v -> openRoute(s));
            r.setOnLongClickListener(v -> {
                new AlertDialog.Builder(this)
                        .setTitle(s.title)
                        .setItems(new String[]{"Rename", "Delete"}, (d, which) -> {
                            if (which == 0) renameRoute(s);
                            else {
                                routes.remove(s);
                                Store.save(prefs, routes);
                                refreshSaved();
                            }
                        })
                        .show();
                return true;
            });
            LinearLayout.LayoutParams l = lp(MATCH, WRAP, 0);
            l.setMargins(0, dp(4), 0, dp(4));
            savedList.addView(r, l);
        }
    }

    private void renameRoute(Store.Saved s) {
        final EditText e = new EditText(this);
        e.setText(s.title);
        e.setSingleLine(true);
        new AlertDialog.Builder(this)
                .setTitle("Rename route")
                .setView(e)
                .setPositiveButton("Save", (d, w) -> {
                    String n = e.getText().toString().trim();
                    if (!n.isEmpty()) {
                        s.title = n;
                        Store.save(prefs, routes);
                        refreshSaved();
                        if (current == s) routeTitle.setText(n);
                    }
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void pasteClipboard() {
        ClipboardManager cm = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
        String text = null;
        if (cm != null && cm.hasPrimaryClip()) {
            ClipData clip = cm.getPrimaryClip();
            if (clip != null && clip.getItemCount() > 0) {
                CharSequence cs = clip.getItemAt(0).coerceToText(this);
                if (cs != null) text = cs.toString();
            }
        }
        if (text == null || text.trim().isEmpty()) {
            showStatus("The clipboard is empty. Copy the directions in Google Maps first.");
            return;
        }
        input.setText(text);
        importText(text, null);
    }

    private void showStatus(String s) {
        status.setText(s);
        status.setVisibility(View.VISIBLE);
    }

    private void importText(String text, String subject) {
        Parser.Route r = Parser.parse(text, subject);
        int real = 0;
        for (Parser.Step s : r.steps) if (!Parser.OTHER.equals(s.kind)) real++;
        if (real == 0) {
            showHome();
            input.setText(text);
            if (r.sawLink) {
                showStatus("That looks like just a map link, which doesn't contain the steps. "
                        + "In Google Maps open the step list and use ⋮ → Share directions instead.");
            } else {
                showStatus("Couldn't find any directions in that text. Lines should look like "
                        + "\"Turn left onto Mill Ln\".");
            }
            return;
        }
        status.setVisibility(View.GONE);
        Store.Saved s = new Store.Saved();
        s.id = System.currentTimeMillis();
        s.created = s.id;
        s.title = r.title.isEmpty() ? "Walk on " + new SimpleDateFormat("d MMM", Locale.getDefault()).format(new Date()) : r.title;
        s.summary = r.summary;
        s.steps = r.steps;
        routes.add(0, s);
        while (routes.size() > Store.MAX) routes.remove(routes.size() - 1);
        Store.save(prefs, routes);
        input.setText("");
        openRoute(s);
    }

    private void showHome() {
        current = null;
        homeView.setVisibility(View.VISIBLE);
        routeView.setVisibility(View.GONE);
        getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        refreshSaved();
        if (tts != null) tts.stop();
    }

    // ---------- route ----------

    private View buildRoute() {
        LinearLayout col = new LinearLayout(this);
        col.setOrientation(LinearLayout.VERTICAL);
        col.setPadding(dp(14), dp(10), dp(14), dp(12));

        LinearLayout top = new LinearLayout(this);
        top.setGravity(Gravity.CENTER_VERTICAL);
        Button back = button("◀", PANEL, TEXT, 16);
        back.setOnClickListener(v -> showHome());
        top.addView(back, lp(dp(48), dp(44), 0));
        LinearLayout titles = new LinearLayout(this);
        titles.setOrientation(LinearLayout.VERTICAL);
        titles.setPadding(dp(12), 0, 0, 0);
        routeTitle = label("", 18, TEXT, true);
        routeTitle.setSingleLine(true);
        routeTitle.setEllipsize(TextUtils.TruncateAt.END);
        routeSummary = label("", 13, MUTED, false);
        titles.addView(routeTitle);
        titles.addView(routeSummary);
        top.addView(titles, lp(0, WRAP, 1));
        Button share = button("Share", PANEL, TEXT, 14);
        share.setOnClickListener(v -> shareRoute());
        top.addView(share, lp(WRAP, dp(44), 0));
        col.addView(top);

        LinearLayout toggles = new LinearLayout(this);
        toggles.setPadding(0, dp(10), 0, dp(10));
        voiceBtn = button("", PANEL, TEXT, 13);
        voiceBtn.setOnClickListener(v -> {
            voice = !voice;
            prefs.edit().putBoolean("voice", voice).apply();
            if (voice) speakCurrent();
            else if (tts != null) tts.stop();
            refreshRoute();
        });
        compactBtn = button("", PANEL, TEXT, 13);
        compactBtn.setOnClickListener(v -> {
            compact = !compact;
            prefs.edit().putBoolean("compact", compact).apply();
            refreshRoute();
        });
        namesBtn = button("", PANEL, TEXT, 13);
        namesBtn.setOnClickListener(v -> {
            fullNames = !fullNames;
            prefs.edit().putBoolean("fullNames", fullNames).apply();
            refreshRoute();
        });
        Button[] tb = {voiceBtn, compactBtn, namesBtn};
        for (int i = 0; i < tb.length; i++) {
            LinearLayout.LayoutParams l = lp(0, dp(40), 1);
            l.setMargins(i == 0 ? 0 : dp(4), 0, i == tb.length - 1 ? 0 : dp(4), 0);
            toggles.addView(tb[i], l);
        }
        col.addView(toggles);

        // current step card
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setBackground(round(PANEL2, 18));
        card.setPadding(dp(16), dp(14), dp(16), dp(14));
        LinearLayout cardRow = new LinearLayout(this);
        cardRow.setGravity(Gravity.CENTER_VERTICAL);
        bigArrow = new ArrowView(this);
        cardRow.addView(bigArrow, lp(dp(84), dp(84), 0));
        stepText = label("", 23, TEXT, true);
        stepText.setPadding(dp(14), 0, 0, 0);
        cardRow.addView(stepText, lp(0, WRAP, 1));
        card.addView(cardRow);
        stepSub = label("", 14, MUTED, false);
        stepSub.setPadding(0, dp(8), 0, 0);
        card.addView(stepSub);
        stepNote = label("", 13, 0xFFE8C46A, false);
        card.addView(stepNote);
        card.setOnClickListener(v -> speakCurrent());
        col.addView(card);

        LinearLayout nav = new LinearLayout(this);
        nav.setPadding(0, dp(10), 0, dp(10));
        prevBtn = button("◀  Back", PANEL, TEXT, 17);
        prevBtn.setOnClickListener(v -> move(-1));
        nextBtn = button("Done  ▶", ACCENT, 0xFF06231A, 18);
        nextBtn.setTypeface(Typeface.DEFAULT_BOLD);
        nextBtn.setOnClickListener(v -> move(1));
        LinearLayout.LayoutParams pl = lp(0, dp(62), 1);
        pl.setMargins(0, 0, dp(6), 0);
        nav.addView(prevBtn, pl);
        LinearLayout.LayoutParams nl = lp(0, dp(62), 2);
        nl.setMargins(dp(6), 0, 0, 0);
        nav.addView(nextBtn, nl);
        col.addView(nav);

        stepScroll = new ScrollView(this);
        stepList = new LinearLayout(this);
        stepList.setOrientation(LinearLayout.VERTICAL);
        stepScroll.addView(stepList);
        col.addView(stepScroll, lp(MATCH, 0, 1));
        return col;
    }

    private void openRoute(Store.Saved s) {
        current = s;
        if (current.position < 0 || current.position >= current.steps.size()) current.position = 0;
        homeView.setVisibility(View.GONE);
        routeView.setVisibility(View.VISIBLE);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        refreshRoute();
        if (voice) speakCurrent();
    }

    private void move(int d) {
        if (current == null) return;
        int n = current.steps.size();
        int p = current.position + d;
        if (p < 0) p = 0;
        if (p > n) p = n; // n means finished
        if (p == current.position) return;
        current.position = p;
        Store.save(prefs, routes);
        refreshRoute();
        bigArrow.performHapticFeedback(android.view.HapticFeedbackConstants.VIRTUAL_KEY);
        if (voice) speakCurrent();
    }

    /** Display text for a step, honouring the compact and full-name settings. */
    private String display(Parser.Step s) {
        String road = s.road == null ? "" : s.road;
        String text = s.text;
        if (fullNames && !road.isEmpty()) {
            String full = Parser.expandRoad(road);
            text = text.replace(road, full);
        }
        if (compact) {
            Parser.Step c = new Parser.Step();
            c.kind = s.kind;
            c.text = text;
            c.road = fullNames ? Parser.expandRoad(road) : road;
            return Parser.compact(c);
        }
        return text;
    }

    private void refreshRoute() {
        if (current == null) return;
        voiceBtn.setText(voice ? "🔊 Voice on" : "🔇 Voice off");
        voiceBtn.setBackground(round(voice ? 0xFF1F4D3A : PANEL, 14));
        compactBtn.setText(compact ? "Short list" : "Full sentences");
        namesBtn.setText(fullNames ? "Road → full" : "Rd abbrev.");
        routeTitle.setText(current.title);
        routeSummary.setText(current.summary.isEmpty() ? current.steps.size() + " steps"
                : current.summary + "  ·  " + current.steps.size() + " steps");

        int n = current.steps.size();
        int p = current.position;
        if (p >= n) {
            bigArrow.set(Parser.ARRIVE, ACCENT);
            stepText.setText("You've arrived");
            stepSub.setText("All " + n + " steps done. Tap any step to go back to it.");
            stepNote.setVisibility(View.GONE);
            nextBtn.setEnabled(false);
            nextBtn.setAlpha(0.4f);
        } else {
            Parser.Step s = current.steps.get(p);
            bigArrow.set(s.kind, ACCENT);
            stepText.setText(compact ? display(s) : display(s));
            String sub = "Step " + (p + 1) + " of " + n;
            if (!s.distance.isEmpty()) sub += "  ·  then about " + s.distance;
            if (p + 1 < n) sub += "\nNext: " + display(current.steps.get(p + 1));
            stepSub.setText(sub);
            if (s.note.isEmpty()) {
                stepNote.setVisibility(View.GONE);
            } else {
                stepNote.setVisibility(View.VISIBLE);
                stepNote.setText("Landmark: " + s.note);
            }
            nextBtn.setEnabled(true);
            nextBtn.setAlpha(1f);
            nextBtn.setText(p == n - 1 ? "Arrived  ✓" : "Done  ▶");
        }
        prevBtn.setEnabled(p > 0);
        prevBtn.setAlpha(p > 0 ? 1f : 0.4f);

        stepList.removeAllViews();
        View currentRow = null;
        for (int i = 0; i < n; i++) {
            final int idx = i;
            Parser.Step s = current.steps.get(i);
            boolean done = i < p, now = i == p;
            LinearLayout r = new LinearLayout(this);
            r.setGravity(Gravity.CENTER_VERTICAL);
            r.setPadding(dp(8), dp(8), dp(12), dp(8));
            r.setBackground(round(now ? 0xFF1F4D3A : PANEL, 12));
            TextView num = label(String.valueOf(i + 1), 13, done ? DONE : MUTED, true);
            num.setGravity(Gravity.CENTER);
            r.addView(num, lp(dp(26), WRAP, 0));
            ArrowView av = new ArrowView(this);
            av.set(s.kind, done ? DONE : (now ? ACCENT : TEXT));
            r.addView(av, lp(dp(34), dp(34), 0));
            LinearLayout texts = new LinearLayout(this);
            texts.setOrientation(LinearLayout.VERTICAL);
            texts.setPadding(dp(10), 0, 0, 0);
            TextView t = label(display(s), 16, done ? DONE : TEXT, now);
            texts.addView(t);
            String extra = s.distance;
            if (!s.note.isEmpty()) extra += (extra.isEmpty() ? "" : "  ·  ") + s.note;
            if (!extra.isEmpty()) texts.addView(label(extra, 12, done ? DONE : MUTED, false));
            r.addView(texts, lp(0, WRAP, 1));
            r.setOnClickListener(v -> {
                current.position = idx;
                Store.save(prefs, routes);
                refreshRoute();
                if (voice) speakCurrent();
            });
            r.setOnLongClickListener(v -> {
                editStep(idx);
                return true;
            });
            LinearLayout.LayoutParams l = lp(MATCH, WRAP, 0);
            l.setMargins(0, dp(3), 0, dp(3));
            stepList.addView(r, l);
            if (now) currentRow = r;
        }
        TextView hint = label("Tap a step to jump to it · long-press to edit or delete", 12, MUTED, false);
        hint.setGravity(Gravity.CENTER);
        hint.setPadding(0, dp(10), 0, dp(4));
        stepList.addView(hint);
        if (currentRow != null) {
            final View cr = currentRow;
            stepScroll.post(() -> stepScroll.smoothScrollTo(0, Math.max(0, cr.getTop() - dp(60))));
        }
    }

    private void editStep(final int idx) {
        final Parser.Step s = current.steps.get(idx);
        final EditText e = new EditText(this);
        e.setText(s.text);
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(dp(20), dp(8), dp(20), 0);
        box.addView(e);
        if (s.original != null && !s.original.isEmpty() && !s.original.equals(s.text)) {
            TextView o = new TextView(this);
            o.setText("Original: " + s.original);
            o.setTextSize(12);
            o.setPadding(0, dp(8), 0, 0);
            box.addView(o);
        }
        new AlertDialog.Builder(this)
                .setTitle("Step " + (idx + 1))
                .setView(box)
                .setPositiveButton("Save", (d, w) -> {
                    String t = e.getText().toString().trim();
                    if (t.isEmpty()) return;
                    if (!t.equals(s.text)) {
                        s.text = t;
                        if (s.road != null && !s.road.isEmpty() && !t.contains(s.road)) s.road = "";
                    }
                    Store.save(prefs, routes);
                    refreshRoute();
                })
                .setNeutralButton("Delete step", (d, w) -> {
                    current.steps.remove(idx);
                    if (current.position > idx) current.position--;
                    Store.save(prefs, routes);
                    if (current.steps.isEmpty()) {
                        routes.remove(current);
                        Store.save(prefs, routes);
                        showHome();
                    } else {
                        refreshRoute();
                    }
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void shareRoute() {
        if (current == null) return;
        StringBuilder sb = new StringBuilder();
        sb.append(current.title).append('\n');
        if (!current.summary.isEmpty()) sb.append(current.summary).append('\n');
        sb.append('\n');
        for (int i = 0; i < current.steps.size(); i++) {
            Parser.Step s = current.steps.get(i);
            sb.append(i + 1).append(". ").append(display(s));
            if (!s.distance.isEmpty()) sb.append("  (").append(s.distance).append(')');
            sb.append('\n');
        }
        Intent i = new Intent(Intent.ACTION_SEND);
        i.setType("text/plain");
        i.putExtra(Intent.EXTRA_SUBJECT, current.title);
        i.putExtra(Intent.EXTRA_TEXT, sb.toString());
        startActivity(Intent.createChooser(i, "Share steps"));
    }

    // ---------- speech ----------

    private void speakCurrent() {
        if (current == null) return;
        String say;
        int p = current.position;
        if (p >= current.steps.size()) {
            say = "You've arrived.";
        } else {
            Parser.Step s = current.steps.get(p);
            String road = s.road == null ? "" : s.road;
            say = road.isEmpty() ? s.text : s.text.replace(road, Parser.expandRoad(road));
            if (!s.distance.isEmpty()) say += ". Then about " + spokenDistance(s.distance) + ".";
        }
        final String text = say;
        if (tts == null) {
            tts = new TextToSpeech(this, st -> {
                ttsReady = st == TextToSpeech.SUCCESS;
                if (ttsReady) {
                    tts.setLanguage(Locale.getDefault());
                    tts.speak(text, TextToSpeech.QUEUE_FLUSH, null, "step");
                } else {
                    Toast.makeText(this, "Text-to-speech isn't available", Toast.LENGTH_SHORT).show();
                }
            });
        } else if (ttsReady) {
            tts.speak(text, TextToSpeech.QUEUE_FLUSH, null, "step");
        }
    }

    static String spokenDistance(String d) {
        return d.replaceAll("(?i)\\bkm\\b", "kilometres").replaceAll("(?i)(\\d)\\s*m\\b", "$1 metres")
                .replaceAll("(?i)\\bmi\\b", "miles").replaceAll("(?i)\\bft\\b", "feet").replaceAll("(?i)\\byd\\b", "yards");
    }

    @Override
    public void onBackPressed() {
        if (routeView.getVisibility() == View.VISIBLE) {
            showHome();
        } else {
            super.onBackPressed();
        }
    }

    @Override
    protected void onDestroy() {
        if (tts != null) {
            tts.stop();
            tts.shutdown();
        }
        super.onDestroy();
    }

    private static final String SAMPLE = "Directions to Tesco Superstore\n"
            + "1.3 km, 17 min\nWalk\n"
            + "Head north on Church Rd toward Mill Ln\n200 m\n"
            + "Turn left onto Mill Ln\nPass by Costa Coffee (on the left in 150 m)\n450 m\n"
            + "Slight right to stay on Mill Ln\n90 m\n"
            + "At the roundabout, take the 2nd exit onto High St\n300 m\n"
            + "Turn right onto Station Approach\n120 m\n"
            + "Turn left\nDestination will be on the right\n40 m";
}
