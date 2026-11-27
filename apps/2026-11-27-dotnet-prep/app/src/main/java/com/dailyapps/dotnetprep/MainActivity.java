package com.dailyapps.dotnetprep;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.DatePickerDialog;
import android.content.SharedPreferences;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.speech.tts.TextToSpeech;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.Gravity;
import android.view.HapticFeedbackConstants;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.HorizontalScrollView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import org.json.JSONArray;
import org.json.JSONObject;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.Date;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.TimeZone;

public class MainActivity extends Activity {
    private static final int MATCH = ViewGroup.LayoutParams.MATCH_PARENT;
    private static final int WRAP = ViewGroup.LayoutParams.WRAP_CONTENT;
    private static final int HOME = 0, REVIEW = 1, QUIZ = 2, MOCK = 3, BROWSE = 4, STATS = 5;

    private SharedPreferences prefs;
    private Deck deck;
    private Progress progress;
    private final Set<String> excluded = new HashSet<>();
    private boolean voice;

    private FrameLayout holder;
    private int screen = HOME;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private TextToSpeech tts;
    private boolean ttsReady;
    private String pendingSpeech;

    // review state
    private final List<Deck.Card> session = new ArrayList<>();
    private int sessionDone;
    private boolean revealed;

    // quiz state
    private final List<Deck.Mcq> quiz = new ArrayList<>();
    private final List<int[]> quizOrder = new ArrayList<>();
    private final List<Integer> quizPicks = new ArrayList<>();
    private int quizIndex, quizScore;

    // mock state
    private final List<Deck.Card> mock = new ArrayList<>();
    private final List<Integer> mockRatings = new ArrayList<>();
    private int mockIndex;
    private long mockStart;
    private boolean mockRevealed;
    private Charts.RingView ring;
    private final Runnable mockTick = new Runnable() {
        @Override
        public void run() {
            updateRing();
            if (screen == MOCK && !mockRevealed) handler.postDelayed(this, 250);
        }
    };

    // browse state
    private String browseFilter = "";
    private String browseQuery = "";
    private LinearLayout browseList;
    private final Set<String> expanded = new HashSet<>();

    private int dp(float v) { return Math.round(v * getResources().getDisplayMetrics().density); }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().setStatusBarColor(Theme.BG);
        getWindow().setNavigationBarColor(Theme.BG);
        prefs = getSharedPreferences("dotnetprep", MODE_PRIVATE);
        deck = Deck.load(this, prefs);
        progress = new Progress(prefs);
        Set<String> ex = prefs.getStringSet("excluded", null);
        if (ex != null) excluded.addAll(ex);
        voice = prefs.getBoolean("voice", false);
        holder = new FrameLayout(this);
        holder.setBackgroundColor(Theme.BG);
        setContentView(holder);
        showHome();
    }

    // ================= UI helpers =================

    private GradientDrawable round(int color, float r) {
        GradientDrawable g = new GradientDrawable();
        g.setColor(color);
        g.setCornerRadius(dp(r));
        return g;
    }

    private TextView text(String s, float size, int color, boolean bold) {
        TextView t = new TextView(this);
        t.setText(s);
        t.setTextSize(size);
        t.setTextColor(color);
        if (bold) t.setTypeface(Typeface.DEFAULT_BOLD);
        return t;
    }

    private Button button(String s, int bg, int fg, float size) {
        Button b = new Button(this);
        b.setText(s);
        b.setAllCaps(false);
        b.setTextSize(size);
        b.setTextColor(fg);
        b.setBackground(round(bg, 14));
        b.setPadding(dp(10), 0, dp(10), 0);
        b.setMinWidth(0);
        b.setMinimumWidth(0);
        b.setMinHeight(0);
        b.setMinimumHeight(0);
        return b;
    }

    private LinearLayout.LayoutParams lp(int w, int h, float weight) {
        return new LinearLayout.LayoutParams(w, h, weight);
    }

    private LinearLayout.LayoutParams margins(int w, int h, int top, int bottom) {
        LinearLayout.LayoutParams l = new LinearLayout.LayoutParams(w, h);
        l.setMargins(0, dp(top), 0, dp(bottom));
        return l;
    }

    private LinearLayout column() {
        LinearLayout l = new LinearLayout(this);
        l.setOrientation(LinearLayout.VERTICAL);
        return l;
    }

    private LinearLayout card() {
        LinearLayout l = column();
        l.setBackground(round(Theme.PANEL, 16));
        l.setPadding(dp(16), dp(14), dp(16), dp(14));
        return l;
    }

    /** A screen: top bar with back + title, scrolling body and an optional fixed footer. */
    private LinearLayout[] frame(String title, String subtitle) {
        LinearLayout root = column();
        LinearLayout bar = new LinearLayout(this);
        bar.setGravity(Gravity.CENTER_VERTICAL);
        bar.setPadding(dp(12), dp(10), dp(12), dp(6));
        Button back = button("←", Theme.PANEL, Theme.TEXT, 18);
        back.setOnClickListener(v -> goBack());
        bar.addView(back, lp(dp(46), dp(42), 0));
        LinearLayout titles = column();
        titles.setPadding(dp(12), 0, 0, 0);
        titles.addView(text(title, 18, Theme.TEXT, true));
        if (subtitle != null) titles.addView(text(subtitle, 13, Theme.MUTED, false));
        bar.addView(titles, lp(0, WRAP, 1));
        root.addView(bar);
        ScrollView sv = new ScrollView(this);
        sv.setFillViewport(true);
        LinearLayout body = column();
        body.setPadding(dp(16), dp(6), dp(16), dp(16));
        sv.addView(body);
        root.addView(sv, lp(MATCH, 0, 1));
        LinearLayout footer = column();
        footer.setPadding(dp(16), dp(6), dp(16), dp(12));
        root.addView(footer);
        LinearLayout extra = bar; // allows adding icons to the bar
        return new LinearLayout[]{root, body, footer, extra};
    }

    private void setScreen(int s, View v) {
        screen = s;
        holder.removeAllViews();
        holder.addView(v);
        if (s != MOCK) handler.removeCallbacks(mockTick);
        if (s != REVIEW && s != MOCK && tts != null) tts.stop();
    }

    /** Adds text that may contain ``` fenced code blocks. */
    private void addRich(LinearLayout parent, String s, float size, int color) {
        String[] lines = s.split("\n", -1);
        StringBuilder buf = new StringBuilder();
        boolean code = false;
        for (String line : lines) {
            if (line.trim().startsWith("```")) {
                flushRich(parent, buf, code, size, color);
                code = !code;
                continue;
            }
            buf.append(line).append('\n');
        }
        flushRich(parent, buf, code, size, color);
    }

    private void flushRich(LinearLayout parent, StringBuilder buf, boolean code, float size, int color) {
        String s = buf.toString();
        buf.setLength(0);
        while (s.endsWith("\n")) s = s.substring(0, s.length() - 1);
        if (s.trim().isEmpty()) return;
        if (code) {
            HorizontalScrollView hs = new HorizontalScrollView(this);
            hs.setBackground(round(Theme.CODE_BG, 10));
            TextView t = text(s, size - 3, Theme.CODE_FG, false);
            t.setTypeface(Typeface.MONOSPACE);
            t.setPadding(dp(12), dp(10), dp(12), dp(10));
            hs.addView(t);
            parent.addView(hs, margins(MATCH, WRAP, 6, 6));
        } else {
            TextView t = text(s, size, color, false);
            t.setLineSpacing(dp(3), 1f);
            parent.addView(t, margins(MATCH, WRAP, 2, 2));
        }
    }

    private static String plain(String s) {
        return s.replaceAll("(?s)```.*?```", " (see the code on screen) ").replace("`", "");
    }

    private boolean topicOn(String cat) { return !excluded.contains(cat); }

    private String fmtDays(int d) {
        if (d <= 0) return "now";
        if (d < 14) return d + "d";
        if (d < 60) return Math.round(d / 7f) + "w";
        return Math.round(d / 30f) + "mo";
    }

    private void haptic(View v) { v.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY); }

    // ================= speech =================

    private void speak(String s) {
        if (!voice) return;
        String say = plain(s);
        if (tts == null) {
            pendingSpeech = say;
            tts = new TextToSpeech(this, st -> {
                ttsReady = st == TextToSpeech.SUCCESS;
                if (ttsReady) {
                    tts.setLanguage(Locale.UK);
                    if (pendingSpeech != null) tts.speak(pendingSpeech, TextToSpeech.QUEUE_FLUSH, null, "q");
                }
                pendingSpeech = null;
            });
        } else if (ttsReady) {
            tts.speak(say, TextToSpeech.QUEUE_FLUSH, null, "q");
        }
    }

    // ================= home =================

    private int interviewDay() { return prefs.getInt("interviewDay", -1); }

    private int newPerDaySetting() { return prefs.getInt("newPerDay", 10); }

    private int newIntroducedToday() {
        return prefs.getInt("newDay", -1) == Progress.today() ? prefs.getInt("newCount", 0) : 0;
    }

    private int unseenCount() {
        int n = 0;
        for (Deck.Card c : deck.cards) if (topicOn(c.cat) && progress.isNew(c.id)) n++;
        return n;
    }

    private int newPerDayTarget() {
        int base = newPerDaySetting();
        int iv = interviewDay();
        int left = iv - Progress.today();
        if (iv >= 0 && left > 0) {
            int need = (int) Math.ceil(unseenCount() / (double) left);
            return Math.max(base, need);
        }
        return base;
    }

    private int dueCount() {
        int day = Progress.today(), n = 0;
        for (Deck.Card c : deck.cards) if (topicOn(c.cat) && progress.isDue(c.id, day)) n++;
        return n;
    }

    private void showHome() {
        ScrollView sv = new ScrollView(this);
        LinearLayout col = column();
        col.setPadding(dp(18), dp(20), dp(18), dp(24));
        sv.addView(col);

        LinearLayout top = new LinearLayout(this);
        top.setGravity(Gravity.CENTER_VERTICAL);
        LinearLayout titles = column();
        titles.addView(text(".NET Interview Prep", 24, Theme.TEXT, true));
        titles.addView(text(deck.cards.size() + " flashcards · " + deck.mcqs.size() + " quiz questions", 13, Theme.MUTED, false));
        top.addView(titles, lp(0, WRAP, 1));
        int streak = progress.streak();
        TextView st = text("🔥 " + streak, 20, streak > 0 ? Theme.WARN : Theme.MUTED, true);
        top.addView(st);
        col.addView(top);

        // interview countdown
        LinearLayout iv = card();
        int ivDay = interviewDay();
        int left = ivDay - Progress.today();
        String ivTitle, ivSub;
        if (ivDay < 0) {
            ivTitle = "When's your interview?";
            ivSub = "Set the date and the app will pace new cards so you've seen everything by then.";
        } else if (left > 0) {
            ivTitle = "Interview in " + left + (left == 1 ? " day" : " days");
            ivSub = unseenCount() + " cards not seen yet · aiming for " + newPerDayTarget() + " new cards a day";
        } else if (left == 0) {
            ivTitle = "Interview day — good luck!";
            ivSub = "Skim your bookmarked cards and do one short mock interview to warm up.";
        } else {
            ivTitle = "Interview done";
            ivSub = "Tap to set a new date.";
        }
        iv.addView(text(ivTitle, 17, Theme.TEXT, true));
        TextView ivs = text(ivSub, 13, Theme.MUTED, false);
        ivs.setPadding(0, dp(4), 0, 0);
        iv.addView(ivs);
        iv.setOnClickListener(v -> pickInterviewDate());
        col.addView(iv, margins(MATCH, WRAP, 16, 0));

        // today
        LinearLayout today = card();
        today.setBackground(round(Theme.PANEL2, 16));
        int due = dueCount();
        int newLeft = Math.max(0, Math.min(unseenCount(), newPerDayTarget() - newIntroducedToday()));
        today.addView(text("Today", 14, Theme.ACCENT, true));
        LinearLayout nums = new LinearLayout(this);
        nums.setPadding(0, dp(6), 0, dp(10));
        nums.addView(stat(String.valueOf(due), "due for review"), lp(0, WRAP, 1));
        nums.addView(stat(String.valueOf(newLeft), "new cards"), lp(0, WRAP, 1));
        nums.addView(stat(String.valueOf(progress.activityOn(Progress.today())), "done today"), lp(0, WRAP, 1));
        today.addView(nums);
        Button start;
        if (due + newLeft > 0) {
            start = button("Start flashcards (" + (due + newLeft) + ")", Theme.ACCENT, Theme.ACCENT_DARK, 17);
            start.setOnClickListener(v -> startReview(false));
        } else {
            start = button("All caught up — practise 10 random cards", Theme.PANEL, Theme.TEXT, 15);
            start.setOnClickListener(v -> startReview(true));
        }
        start.setTypeface(Typeface.DEFAULT_BOLD);
        today.addView(start, lp(MATCH, dp(54), 0));
        col.addView(today, margins(MATCH, WRAP, 12, 0));

        // mode grid
        String[][] modes = {
                {"❓  Quiz", "10 multiple-choice questions"},
                {"🎙  Mock interview", "Timed questions, answer out loud"},
                {"🔍  Browse", "Search every card, add your own"},
                {"📊  Progress", "Mastery by topic and streak"}};
        for (int row = 0; row < 2; row++) {
            LinearLayout r = new LinearLayout(this);
            for (int k = 0; k < 2; k++) {
                final int idx = row * 2 + k;
                LinearLayout m = card();
                m.addView(text(modes[idx][0], 16, Theme.TEXT, true));
                TextView d = text(modes[idx][1], 12, Theme.MUTED, false);
                d.setPadding(0, dp(4), 0, 0);
                m.addView(d);
                m.setOnClickListener(v -> {
                    if (idx == 0) startQuiz();
                    else if (idx == 1) startMock();
                    else if (idx == 2) showBrowse();
                    else showStats();
                });
                LinearLayout.LayoutParams l = lp(0, dp(96), 1);
                l.setMargins(k == 0 ? 0 : dp(6), 0, k == 0 ? dp(6) : 0, 0);
                r.addView(m, l);
            }
            col.addView(r, margins(MATCH, WRAP, 12, 0));
        }

        // settings
        LinearLayout set = card();
        set.addView(text("Settings", 14, Theme.ACCENT, true));
        int on = 0;
        for (String t : deck.topics) if (topicOn(t)) on++;
        set.addView(settingRow("Topics", on + " of " + deck.topics.size() + " selected", v -> pickTopics()));
        set.addView(settingRow("New cards per day", String.valueOf(newPerDaySetting()), v -> {
            int[] opts = {5, 10, 15, 20, 30};
            int cur = newPerDaySetting(), next = opts[0];
            for (int i = 0; i < opts.length; i++) if (opts[i] == cur) next = opts[(i + 1) % opts.length];
            prefs.edit().putInt("newPerDay", next).apply();
            showHome();
        }));
        set.addView(settingRow("Read questions aloud", voice ? "On" : "Off", v -> {
            voice = !voice;
            prefs.edit().putBoolean("voice", voice).apply();
            showHome();
        }));
        set.addView(settingRow("Mock answer time", (prefs.getInt("mockSecs", 120) / 60) + " min", v -> {
            int s = prefs.getInt("mockSecs", 120);
            s = s >= 180 ? 60 : s + 60;
            prefs.edit().putInt("mockSecs", s).apply();
            showHome();
        }));
        col.addView(set, margins(MATCH, WRAP, 12, 0));

        setScreen(HOME, sv);
    }

    private LinearLayout stat(String big, String small) {
        LinearLayout l = column();
        l.addView(text(big, 26, Theme.TEXT, true));
        l.addView(text(small, 12, Theme.MUTED, false));
        return l;
    }

    private View settingRow(String label, String value, View.OnClickListener click) {
        LinearLayout r = new LinearLayout(this);
        r.setGravity(Gravity.CENTER_VERTICAL);
        r.setPadding(0, dp(12), 0, dp(12));
        r.addView(text(label, 15, Theme.TEXT, false), lp(0, WRAP, 1));
        r.addView(text(value + "  ›", 15, Theme.ACCENT, true));
        r.setOnClickListener(click);
        return r;
    }

    private void pickTopics() {
        final String[] names = new String[deck.topics.size()];
        final boolean[] checked = new boolean[names.length];
        for (int i = 0; i < names.length; i++) {
            String t = deck.topics.get(i);
            names[i] = t + "  (" + deck.countIn(t) + ")";
            checked[i] = topicOn(t);
        }
        new AlertDialog.Builder(this)
                .setTitle("Topics to study")
                .setMultiChoiceItems(names, checked, (d, which, isChecked) -> checked[which] = isChecked)
                .setPositiveButton("Save", (d, w) -> {
                    excluded.clear();
                    for (int i = 0; i < names.length; i++) if (!checked[i]) excluded.add(deck.topics.get(i));
                    if (excluded.size() == deck.topics.size()) {
                        excluded.clear();
                        Toast.makeText(this, "Pick at least one topic — all selected", Toast.LENGTH_SHORT).show();
                    }
                    prefs.edit().putStringSet("excluded", new HashSet<>(excluded)).apply();
                    showHome();
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void pickInterviewDate() {
        Calendar c = Calendar.getInstance();
        int ivDay = interviewDay();
        if (ivDay >= 0) c.setTimeInMillis(ivDay * 86400000L + 12 * 3600000L);
        else c.add(Calendar.DAY_OF_MONTH, 14);
        DatePickerDialog dlg = new DatePickerDialog(this, (v, y, m, d) -> {
            Calendar utc = Calendar.getInstance(TimeZone.getTimeZone("UTC"));
            utc.clear();
            utc.set(y, m, d, 0, 0, 0);
            int day = (int) (utc.getTimeInMillis() / 86400000L);
            prefs.edit().putInt("interviewDay", day).apply();
            showHome();
        }, c.get(Calendar.YEAR), c.get(Calendar.MONTH), c.get(Calendar.DAY_OF_MONTH));
        dlg.setButton(DatePickerDialog.BUTTON_NEUTRAL, "Clear", (d, w) -> {
            prefs.edit().remove("interviewDay").apply();
            showHome();
        });
        dlg.show();
    }

    private void goBack() {
        if (screen == HOME) {
            finish();
        } else {
            showHome();
        }
    }

    @Override
    public void onBackPressed() {
        goBack();
    }

    // ================= flashcard review =================

    private void startReview(boolean practice) {
        session.clear();
        sessionDone = 0;
        int day = Progress.today();
        List<Deck.Card> due = new ArrayList<>();
        List<Deck.Card> fresh = new ArrayList<>();
        List<Deck.Card> all = new ArrayList<>();
        for (Deck.Card c : deck.cards) {
            if (!topicOn(c.cat)) continue;
            all.add(c);
            if (progress.isDue(c.id, day)) due.add(c);
            else if (progress.isNew(c.id)) fresh.add(c);
        }
        if (practice) {
            Collections.shuffle(all);
            session.addAll(all.subList(0, Math.min(10, all.size())));
        } else {
            Collections.sort(due, (a, b) -> Integer.compare(progress.srs.get(a.id)[1], progress.srs.get(b.id)[1]));
            session.addAll(due);
            Collections.shuffle(fresh, new Random(day));
            int n = Math.max(0, Math.min(fresh.size(), newPerDayTarget() - newIntroducedToday()));
            session.addAll(fresh.subList(0, n));
        }
        if (session.isEmpty()) {
            Toast.makeText(this, "No cards in the selected topics", Toast.LENGTH_SHORT).show();
            return;
        }
        revealed = false;
        showCard();
    }

    private void showCard() {
        if (session.isEmpty()) {
            showReviewDone();
            return;
        }
        final Deck.Card c = session.get(0);
        LinearLayout[] f = frame("Flashcards", session.size() + " left · " + sessionDone + " done");
        LinearLayout body = f[1], footer = f[2], bar = f[3];

        final Button star = button(progress.bookmarks.contains(c.id) ? "★" : "☆", Theme.PANEL, Theme.WARN, 20);
        star.setOnClickListener(v -> star.setText(progress.toggleBookmark(c.id) ? "★" : "☆"));
        bar.addView(star, lp(dp(46), dp(42), 0));
        Button say = button("🔊", Theme.PANEL, Theme.TEXT, 16);
        say.setOnClickListener(v -> {
            boolean was = voice;
            voice = true;
            speak(revealed ? c.a : c.q);
            voice = was;
        });
        LinearLayout.LayoutParams sl = lp(dp(46), dp(42), 0);
        sl.setMargins(dp(6), 0, 0, 0);
        bar.addView(say, sl);

        LinearLayout qc = card();
        String status = progress.isNew(c.id) ? "NEW" : "Box " + progress.box(c.id);
        qc.addView(text(c.cat.toUpperCase(Locale.ROOT) + "  ·  " + status, 12, Theme.ACCENT, true));
        LinearLayout qbody = column();
        qbody.setPadding(0, dp(8), 0, 0);
        addRich(qbody, c.q, 19, Theme.TEXT);
        if (qbody.getChildAt(0) instanceof TextView) {
            ((TextView) qbody.getChildAt(0)).setTypeface(Typeface.DEFAULT_BOLD);
        }
        qc.addView(qbody);
        body.addView(qc, margins(MATCH, WRAP, 4, 0));

        if (!revealed) {
            TextView hint = text("Answer it in your head (or out loud), then reveal.", 13, Theme.MUTED, false);
            hint.setGravity(Gravity.CENTER);
            hint.setPadding(0, dp(18), 0, 0);
            body.addView(hint);
            Button show = button("Show answer", Theme.ACCENT, Theme.ACCENT_DARK, 18);
            show.setTypeface(Typeface.DEFAULT_BOLD);
            show.setOnClickListener(v -> {
                revealed = true;
                showCard();
            });
            footer.addView(show, lp(MATCH, dp(58), 0));
            setScreen(REVIEW, f[0]);
            speak(c.q);
            return;
        }

        LinearLayout ac = card();
        ac.setBackground(round(Theme.PANEL2, 16));
        ac.addView(text("ANSWER", 12, Theme.GOOD, true));
        LinearLayout abody = column();
        abody.setPadding(0, dp(6), 0, 0);
        addRich(abody, c.a, 16, Theme.TEXT);
        ac.addView(abody);
        body.addView(ac, margins(MATCH, WRAP, 10, 0));

        TextView how = text("How well did you know it?", 13, Theme.MUTED, false);
        how.setGravity(Gravity.CENTER);
        footer.addView(how, margins(MATCH, WRAP, 0, 6));
        LinearLayout row = new LinearLayout(this);
        String[] names = {"Again", "Hard", "Good", "Easy"};
        int[] cols = {0xFF5A2A35, 0xFF4A3D1F, 0xFF1F4D3B, 0xFF2A3E6B};
        for (int i = 0; i < 4; i++) {
            final int rating = i;
            Button b = button(names[i] + "\n" + fmtDays(progress.previewDays(c.id, i)), cols[i], Theme.TEXT, 15);
            b.setOnClickListener(v -> {
                haptic(v);
                rate(c, rating);
            });
            LinearLayout.LayoutParams l = lp(0, dp(62), 1);
            l.setMargins(i == 0 ? 0 : dp(3), 0, i == 3 ? 0 : dp(3), 0);
            row.addView(b, l);
        }
        footer.addView(row);
        setScreen(REVIEW, f[0]);
    }

    private void rate(Deck.Card c, int rating) {
        if (progress.isNew(c.id)) {
            int day = Progress.today();
            int count = newIntroducedToday() + 1;
            prefs.edit().putInt("newDay", day).putInt("newCount", count).apply();
        }
        progress.rate(c.id, rating);
        session.remove(0);
        if (rating == Progress.AGAIN) {
            session.add(Math.min(session.size(), 3), c);
        } else {
            sessionDone++;
        }
        revealed = false;
        showCard();
    }

    private void showReviewDone() {
        LinearLayout[] f = frame("Session complete", null);
        LinearLayout body = f[1];
        LinearLayout c = card();
        c.addView(text("✅  Nice work!", 24, Theme.TEXT, true));
        TextView t = text(sessionDone + " cards reviewed. Streak: " + progress.streak() + " day"
                + (progress.streak() == 1 ? "" : "s") + ".\n\nCards you found hard will come back sooner; "
                + "easy ones are spaced further apart.", 15, Theme.MUTED, false);
        t.setPadding(0, dp(8), 0, 0);
        c.addView(t);
        body.addView(c, margins(MATCH, WRAP, 8, 0));
        Button q = button("Test yourself with a quiz", Theme.ACCENT, Theme.ACCENT_DARK, 16);
        q.setOnClickListener(v -> startQuiz());
        body.addView(q, margins(MATCH, dp(54), 16, 0));
        Button h = button("Home", Theme.PANEL, Theme.TEXT, 16);
        h.setOnClickListener(v -> showHome());
        body.addView(h, margins(MATCH, dp(54), 10, 0));
        setScreen(REVIEW, f[0]);
    }

    // ================= quiz =================

    private void startQuiz() {
        List<Deck.Mcq> pool = new ArrayList<>();
        for (Deck.Mcq m : deck.mcqs) if (topicOn(m.cat)) pool.add(m);
        if (pool.isEmpty()) pool.addAll(deck.mcqs);
        Collections.shuffle(pool);
        quiz.clear();
        quizOrder.clear();
        quizPicks.clear();
        for (int i = 0; i < Math.min(10, pool.size()); i++) {
            Deck.Mcq m = pool.get(i);
            quiz.add(m);
            List<Integer> o = new ArrayList<>();
            for (int k = 0; k < m.options.size(); k++) o.add(k);
            Collections.shuffle(o);
            int[] arr = new int[o.size()];
            for (int k = 0; k < arr.length; k++) arr[k] = o.get(k);
            quizOrder.add(arr);
        }
        quizIndex = 0;
        quizScore = 0;
        showQuizQuestion(-1);
    }

    private void showQuizQuestion(int picked) {
        if (quizIndex >= quiz.size()) {
            showQuizDone();
            return;
        }
        final Deck.Mcq m = quiz.get(quizIndex);
        int[] order = quizOrder.get(quizIndex);
        LinearLayout[] f = frame("Quiz", "Question " + (quizIndex + 1) + " of " + quiz.size() + " · score " + quizScore);
        LinearLayout body = f[1], footer = f[2];

        // progress dots
        LinearLayout dots = new LinearLayout(this);
        for (int i = 0; i < quiz.size(); i++) {
            View d = new View(this);
            int col = Theme.PANEL2;
            if (i < quizPicks.size()) col = quizPicks.get(i) == quiz.get(i).correct ? Theme.GOOD : Theme.BAD;
            else if (i == quizIndex) col = Theme.ACCENT;
            d.setBackground(round(col, 4));
            LinearLayout.LayoutParams l = lp(0, dp(6), 1);
            l.setMargins(dp(2), 0, dp(2), 0);
            dots.addView(d, l);
        }
        body.addView(dots, margins(MATCH, WRAP, 0, 10));

        LinearLayout qc = card();
        qc.addView(text(m.cat.toUpperCase(Locale.ROOT), 12, Theme.ACCENT, true));
        LinearLayout qb = column();
        qb.setPadding(0, dp(6), 0, 0);
        addRich(qb, m.q, 18, Theme.TEXT);
        qc.addView(qb);
        body.addView(qc);

        for (int k = 0; k < order.length; k++) {
            final int opt = order[k];
            int bg = Theme.PANEL;
            int fg = Theme.TEXT;
            String prefix = (char) ('A' + k) + "   ";
            if (picked >= 0) {
                if (opt == m.correct) {
                    bg = 0xFF1F4D3B;
                    prefix = "✓  ";
                } else if (opt == picked) {
                    bg = 0xFF5A2A35;
                    prefix = "✗  ";
                } else {
                    fg = Theme.MUTED;
                }
            }
            Button b = button(prefix + m.options.get(opt), bg, fg, 15);
            b.setGravity(Gravity.CENTER_VERTICAL | Gravity.START);
            b.setPadding(dp(16), dp(14), dp(16), dp(14));
            if (picked < 0) {
                b.setOnClickListener(v -> {
                    haptic(v);
                    boolean ok = opt == m.correct;
                    if (ok) quizScore++;
                    quizPicks.add(opt);
                    progress.recordQuizAnswer(m.cat, ok);
                    showQuizQuestion(opt);
                });
            }
            body.addView(b, margins(MATCH, WRAP, 8, 0));
        }

        if (picked >= 0) {
            boolean ok = picked == m.correct;
            LinearLayout fb = card();
            fb.setBackground(round(Theme.PANEL2, 16));
            fb.addView(text(ok ? "Correct!" : "Not quite", 16, ok ? Theme.GOOD : Theme.BAD, true));
            if (!m.expl.isEmpty()) {
                TextView e = text(m.expl, 14, Theme.TEXT, false);
                e.setPadding(0, dp(4), 0, 0);
                fb.addView(e);
            }
            body.addView(fb, margins(MATCH, WRAP, 14, 0));
            Button next = button(quizIndex + 1 < quiz.size() ? "Next question" : "See results",
                    Theme.ACCENT, Theme.ACCENT_DARK, 17);
            next.setTypeface(Typeface.DEFAULT_BOLD);
            next.setOnClickListener(v -> {
                quizIndex++;
                showQuizQuestion(-1);
            });
            footer.addView(next, lp(MATCH, dp(56), 0));
        }
        setScreen(QUIZ, f[0]);
    }

    private void showQuizDone() {
        progress.addHistory(false, quizScore, quiz.size());
        LinearLayout[] f = frame("Quiz results", null);
        LinearLayout body = f[1], footer = f[2];
        LinearLayout c = card();
        int pct = quiz.isEmpty() ? 0 : Math.round(100f * quizScore / quiz.size());
        c.addView(text(quizScore + " / " + quiz.size(), 40, pct >= 70 ? Theme.GOOD : (pct >= 40 ? Theme.WARN : Theme.BAD), true));
        String msg = pct >= 90 ? "Excellent — interview ready on these topics."
                : pct >= 70 ? "Solid. Review the misses below."
                : pct >= 40 ? "Getting there. The flashcards will help lock these in."
                : "Tough round. Focus your flashcards on these topics.";
        c.addView(text(msg, 15, Theme.MUTED, false));
        body.addView(c);
        boolean any = false;
        for (int i = 0; i < quiz.size() && i < quizPicks.size(); i++) {
            Deck.Mcq m = quiz.get(i);
            if (quizPicks.get(i) == m.correct) continue;
            if (!any) {
                TextView h = text("Review your misses", 14, Theme.ACCENT, true);
                h.setPadding(0, dp(18), 0, dp(4));
                body.addView(h);
                any = true;
            }
            LinearLayout r = card();
            r.addView(text(plain(m.q), 15, Theme.TEXT, true));
            r.addView(text("✗ " + m.options.get(quizPicks.get(i)), 14, Theme.BAD, false));
            r.addView(text("✓ " + m.options.get(m.correct), 14, Theme.GOOD, false));
            if (!m.expl.isEmpty()) r.addView(text(m.expl, 13, Theme.MUTED, false));
            body.addView(r, margins(MATCH, WRAP, 6, 0));
        }
        LinearLayout row = new LinearLayout(this);
        Button again = button("New quiz", Theme.ACCENT, Theme.ACCENT_DARK, 16);
        again.setOnClickListener(v -> startQuiz());
        Button home = button("Home", Theme.PANEL, Theme.TEXT, 16);
        home.setOnClickListener(v -> showHome());
        LinearLayout.LayoutParams a = lp(0, dp(54), 1);
        a.setMargins(0, 0, dp(6), 0);
        row.addView(again, a);
        LinearLayout.LayoutParams b = lp(0, dp(54), 1);
        b.setMargins(dp(6), 0, 0, 0);
        row.addView(home, b);
        footer.addView(row);
        setScreen(QUIZ, f[0]);
    }

    // ================= mock interview =================

    private void startMock() {
        List<Deck.Card> tech = new ArrayList<>();
        List<Deck.Card> beh = new ArrayList<>();
        for (Deck.Card c : deck.cards) {
            if (c.cat.equals("Behavioural")) beh.add(c);
            else if (topicOn(c.cat)) tech.add(c);
        }
        if (tech.isEmpty()) {
            for (Deck.Card c : deck.cards) if (!c.cat.equals("Behavioural")) tech.add(c);
        }
        Collections.shuffle(tech);
        Collections.shuffle(beh);
        mock.clear();
        mockRatings.clear();
        if (!beh.isEmpty()) mock.add(beh.get(0));
        // prefer a spread of topics
        Map<String, Deck.Card> byTopic = new LinkedHashMap<>();
        for (Deck.Card c : tech) if (!byTopic.containsKey(c.cat)) byTopic.put(c.cat, c);
        List<Deck.Card> spread = new ArrayList<>(byTopic.values());
        Collections.shuffle(spread);
        for (Deck.Card c : spread) if (mock.size() < 6) mock.add(c);
        for (Deck.Card c : tech) if (mock.size() < 6 && !mock.contains(c)) mock.add(c);
        if (beh.size() > 1) mock.add(beh.get(1));
        mockIndex = 0;
        showMockIntro();
    }

    private void showMockIntro() {
        LinearLayout[] f = frame("Mock interview", mock.size() + " questions");
        LinearLayout body = f[1], footer = f[2];
        LinearLayout c = card();
        c.addView(text("How it works", 17, Theme.TEXT, true));
        TextView t = text("You'll get " + mock.size() + " questions, a mix of technical topics plus behavioural ones. "
                + "For each, answer out loud as you would in the interview, within "
                + (prefs.getInt("mockSecs", 120) / 60) + " min. Then reveal a model answer and rate yourself honestly.\n\n"
                + "Tips: structure your answer (define it, explain why it matters, give a real example from your work), "
                + "and use STAR for behavioural questions."
                + (voice ? "\n\nQuestions will be read aloud." : "\n\nTurn on \"Read questions aloud\" in settings for a more realistic feel."),
                15, Theme.MUTED, false);
        t.setPadding(0, dp(6), 0, 0);
        t.setLineSpacing(dp(3), 1f);
        c.addView(t);
        body.addView(c);
        Button go = button("Start interview", Theme.ACCENT, Theme.ACCENT_DARK, 18);
        go.setTypeface(Typeface.DEFAULT_BOLD);
        go.setOnClickListener(v -> showMockQuestion(false));
        footer.addView(go, lp(MATCH, dp(58), 0));
        setScreen(MOCK, f[0]);
    }

    private void showMockQuestion(boolean reveal) {
        if (mockIndex >= mock.size()) {
            showMockDone();
            return;
        }
        final Deck.Card c = mock.get(mockIndex);
        mockRevealed = reveal;
        if (!reveal) mockStart = System.currentTimeMillis();
        LinearLayout[] f = frame("Mock interview", "Question " + (mockIndex + 1) + " of " + mock.size());
        LinearLayout body = f[1], footer = f[2];

        ring = new Charts.RingView(this);
        LinearLayout.LayoutParams rl = new LinearLayout.LayoutParams(dp(120), dp(120));
        rl.gravity = Gravity.CENTER_HORIZONTAL;
        rl.setMargins(0, dp(4), 0, dp(10));
        body.addView(ring, rl);
        updateRing();

        LinearLayout qc = card();
        qc.addView(text("INTERVIEWER  ·  " + c.cat.toUpperCase(Locale.ROOT), 12, Theme.ACCENT, true));
        LinearLayout qb = column();
        qb.setPadding(0, dp(6), 0, 0);
        addRich(qb, c.q, 19, Theme.TEXT);
        qc.addView(qb);
        body.addView(qc);

        if (!reveal) {
            TextView hint = text("Answer out loud now. Take a breath, structure it, give an example.", 13, Theme.MUTED, false);
            hint.setGravity(Gravity.CENTER);
            hint.setPadding(0, dp(14), 0, 0);
            body.addView(hint);
            Button done = button("I've answered — show model answer", Theme.ACCENT, Theme.ACCENT_DARK, 16);
            done.setTypeface(Typeface.DEFAULT_BOLD);
            done.setOnClickListener(v -> showMockQuestion(true));
            footer.addView(done, lp(MATCH, dp(58), 0));
            setScreen(MOCK, f[0]);
            handler.removeCallbacks(mockTick);
            handler.post(mockTick);
            speak(c.q);
            return;
        }

        LinearLayout ac = card();
        ac.setBackground(round(Theme.PANEL2, 16));
        ac.addView(text("WHAT A GOOD ANSWER COVERS", 12, Theme.GOOD, true));
        LinearLayout ab = column();
        ab.setPadding(0, dp(6), 0, 0);
        addRich(ab, c.a, 15, Theme.TEXT);
        ac.addView(ab);
        body.addView(ac, margins(MATCH, WRAP, 10, 0));

        LinearLayout row = new LinearLayout(this);
        String[] names = {"Missed it", "Partly", "Nailed it"};
        int[] cols = {0xFF5A2A35, 0xFF4A3D1F, 0xFF1F4D3B};
        final int[] srsMap = {Progress.AGAIN, Progress.HARD, Progress.GOOD};
        for (int i = 0; i < 3; i++) {
            final int score = i;
            Button b = button(names[i], cols[i], Theme.TEXT, 15);
            b.setOnClickListener(v -> {
                haptic(v);
                mockRatings.add(score);
                progress.rate(c.id, srsMap[score]);
                mockIndex++;
                showMockQuestion(false);
            });
            LinearLayout.LayoutParams l = lp(0, dp(58), 1);
            l.setMargins(i == 0 ? 0 : dp(3), 0, i == 2 ? 0 : dp(3), 0);
            row.addView(b, l);
        }
        footer.addView(row);
        setScreen(MOCK, f[0]);
        updateRing();
    }

    private void updateRing() {
        if (ring == null) return;
        int limit = prefs.getInt("mockSecs", 120);
        long elapsed = (System.currentTimeMillis() - mockStart) / 1000;
        long remaining = limit - elapsed;
        if (mockRevealed) {
            ring.set(Math.max(0, remaining) / (float) limit, elapsed / 60 + ":" + String.format(Locale.US, "%02d", elapsed % 60), Theme.MUTED);
            return;
        }
        if (remaining >= 0) {
            int col = remaining > limit / 4 ? Theme.ACCENT : Theme.WARN;
            ring.set(remaining / (float) limit, remaining / 60 + ":" + String.format(Locale.US, "%02d", remaining % 60), col);
            if (remaining == 0) ring.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS);
        } else {
            long over = -remaining;
            ring.set(1f, "+" + over / 60 + ":" + String.format(Locale.US, "%02d", over % 60), Theme.BAD);
        }
    }

    private void showMockDone() {
        int total = mockRatings.size() * 2, got = 0;
        for (int r : mockRatings) got += r;
        int pct = total == 0 ? 0 : Math.round(100f * got / total);
        progress.addHistory(true, got, total);
        LinearLayout[] f = frame("Interview summary", null);
        LinearLayout body = f[1], footer = f[2];
        LinearLayout c = card();
        c.addView(text(pct + "%", 44, pct >= 70 ? Theme.GOOD : (pct >= 40 ? Theme.WARN : Theme.BAD), true));
        c.addView(text(pct >= 80 ? "Strong interview. Keep it fresh with daily flashcards."
                : pct >= 50 ? "Decent. Missed questions have been added to your review queue."
                : "Good practice. The questions you missed will come up in your flashcards today.", 15, Theme.MUTED, false));
        body.addView(c);
        String[] names = {"Missed", "Partly", "Nailed"};
        int[] cols = {Theme.BAD, Theme.WARN, Theme.GOOD};
        for (int i = 0; i < mockRatings.size() && i < mock.size(); i++) {
            LinearLayout r = card();
            int s = mockRatings.get(i);
            r.addView(text(names[s] + "  ·  " + mock.get(i).cat, 12, cols[s], true));
            String q = plain(mock.get(i).q);
            r.addView(text(q.length() > 140 ? q.substring(0, 140) + "…" : q, 15, Theme.TEXT, false));
            body.addView(r, margins(MATCH, WRAP, 6, 0));
        }
        LinearLayout row = new LinearLayout(this);
        Button again = button("Another round", Theme.ACCENT, Theme.ACCENT_DARK, 16);
        again.setOnClickListener(v -> startMock());
        Button home = button("Home", Theme.PANEL, Theme.TEXT, 16);
        home.setOnClickListener(v -> showHome());
        LinearLayout.LayoutParams a = lp(0, dp(54), 1);
        a.setMargins(0, 0, dp(6), 0);
        row.addView(again, a);
        LinearLayout.LayoutParams b = lp(0, dp(54), 1);
        b.setMargins(dp(6), 0, 0, 0);
        row.addView(home, b);
        footer.addView(row);
        setScreen(MOCK, f[0]);
    }

    // ================= browse =================

    private void showBrowse() {
        LinearLayout[] f = frame("Browse cards", null);
        LinearLayout body = f[1];
        EditText search = new EditText(this);
        search.setHint("Search questions and answers");
        search.setHintTextColor(Theme.MUTED);
        search.setTextColor(Theme.TEXT);
        search.setSingleLine(true);
        search.setBackground(round(Theme.PANEL, 12));
        search.setPadding(dp(14), dp(10), dp(14), dp(10));
        search.setText(browseQuery);
        search.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int a, int b, int c) {}
            @Override public void onTextChanged(CharSequence s, int a, int b, int c) {}
            @Override
            public void afterTextChanged(Editable s) {
                browseQuery = s.toString();
                fillBrowse();
            }
        });
        body.addView(search, lp(MATCH, dp(48), 0));

        HorizontalScrollView hs = new HorizontalScrollView(this);
        hs.setHorizontalScrollBarEnabled(false);
        LinearLayout chips = new LinearLayout(this);
        chips.setPadding(0, dp(10), 0, dp(4));
        List<String> filters = new ArrayList<>();
        filters.add("");
        filters.add("★");
        filters.addAll(deck.topics);
        for (final String fl : filters) {
            String label = fl.isEmpty() ? "All" : (fl.equals("★") ? "★ Saved" : fl);
            boolean on = fl.equals(browseFilter);
            Button b = button(label, on ? Theme.ACCENT : Theme.PANEL, on ? Theme.ACCENT_DARK : Theme.TEXT, 13);
            b.setOnClickListener(v -> {
                browseFilter = fl;
                showBrowse();
            });
            LinearLayout.LayoutParams l = lp(WRAP, dp(36), 0);
            l.setMargins(0, 0, dp(6), 0);
            chips.addView(b, l);
        }
        hs.addView(chips);
        body.addView(hs);

        Button add = button("+ Add your own card", Theme.PANEL2, Theme.ACCENT, 14);
        add.setOnClickListener(v -> editCustom(null));
        body.addView(add, margins(MATCH, dp(44), 6, 6));

        browseList = column();
        body.addView(browseList);
        fillBrowse();
        setScreen(BROWSE, f[0]);
    }

    private void fillBrowse() {
        if (browseList == null) return;
        browseList.removeAllViews();
        String q = browseQuery.trim().toLowerCase(Locale.ROOT);
        int shown = 0;
        int day = Progress.today();
        for (final Deck.Card c : deck.cards) {
            if (browseFilter.equals("★")) {
                if (!progress.bookmarks.contains(c.id)) continue;
            } else if (!browseFilter.isEmpty() && !c.cat.equals(browseFilter)) {
                continue;
            }
            if (!q.isEmpty() && !c.q.toLowerCase(Locale.ROOT).contains(q) && !c.a.toLowerCase(Locale.ROOT).contains(q)) continue;
            shown++;
            final LinearLayout r = card();
            LinearLayout head = new LinearLayout(this);
            head.setGravity(Gravity.CENTER_VERTICAL);
            View dot = new View(this);
            int col = progress.isNew(c.id) ? Theme.PANEL2 : (progress.mastered(c.id) ? Theme.GOOD : 0xFF3E5C8A);
            GradientDrawable g = new GradientDrawable();
            g.setShape(GradientDrawable.OVAL);
            g.setColor(col);
            dot.setBackground(g);
            LinearLayout.LayoutParams dl = lp(dp(10), dp(10), 0);
            dl.setMargins(0, 0, dp(10), 0);
            head.addView(dot, dl);
            String first = c.q.split("\n")[0];
            head.addView(text(first, 15, Theme.TEXT, true), lp(0, WRAP, 1));
            if (progress.bookmarks.contains(c.id)) head.addView(text(" ★", 15, Theme.WARN, false));
            r.addView(head);
            r.addView(text(c.cat + (c.custom ? " (yours)" : ""), 12, Theme.MUTED, false));
            if (expanded.contains(c.id)) {
                LinearLayout full = column();
                full.setPadding(0, dp(8), 0, 0);
                if (c.q.contains("\n")) addRich(full, c.q, 15, Theme.TEXT);
                TextView al = text("ANSWER", 11, Theme.GOOD, true);
                al.setPadding(0, dp(6), 0, 0);
                full.addView(al);
                addRich(full, c.a, 15, Theme.TEXT);
                String st;
                if (progress.isNew(c.id)) st = "Not studied yet";
                else {
                    int[] s = progress.srs.get(c.id);
                    st = "Box " + s[0] + " · next review " + (s[1] <= day ? "now" : "in " + fmtDays(s[1] - day))
                            + " · seen " + s[2] + "×";
                }
                TextView stv = text(st, 12, Theme.MUTED, false);
                stv.setPadding(0, dp(8), 0, 0);
                full.addView(stv);
                LinearLayout acts = new LinearLayout(this);
                acts.setPadding(0, dp(8), 0, 0);
                Button star = button(progress.bookmarks.contains(c.id) ? "★ Saved" : "☆ Save", Theme.PANEL2, Theme.WARN, 13);
                star.setOnClickListener(v -> {
                    progress.toggleBookmark(c.id);
                    fillBrowse();
                });
                acts.addView(star, lp(0, dp(40), 1));
                Button reset = button("Reset", Theme.PANEL2, Theme.TEXT, 13);
                reset.setOnClickListener(v -> {
                    progress.forget(c.id);
                    fillBrowse();
                });
                LinearLayout.LayoutParams rl2 = lp(0, dp(40), 1);
                rl2.setMargins(dp(6), 0, 0, 0);
                acts.addView(reset, rl2);
                if (c.custom) {
                    Button ed = button("Edit", Theme.PANEL2, Theme.ACCENT, 13);
                    ed.setOnClickListener(v -> editCustom(c));
                    LinearLayout.LayoutParams el = lp(0, dp(40), 1);
                    el.setMargins(dp(6), 0, 0, 0);
                    acts.addView(ed, el);
                }
                full.addView(acts);
                r.addView(full);
            }
            r.setOnClickListener(v -> {
                if (expanded.contains(c.id)) expanded.remove(c.id); else expanded.add(c.id);
                fillBrowse();
            });
            browseList.addView(r, margins(MATCH, WRAP, 4, 4));
            if (shown >= 250) break;
        }
        if (shown == 0) {
            TextView t = text(browseFilter.equals("★") ? "No saved cards yet. Tap ☆ on a card to save it."
                    : "No cards match.", 14, Theme.MUTED, false);
            t.setPadding(0, dp(16), 0, 0);
            browseList.addView(t);
        }
    }

    private void editCustom(final Deck.Card existing) {
        LinearLayout box = column();
        box.setPadding(dp(20), dp(8), dp(20), 0);
        final Spinner topic = new Spinner(this);
        final List<String> topics = new ArrayList<>(deck.topics);
        if (!topics.contains(Deck.MY_CARDS)) topics.add(0, Deck.MY_CARDS);
        topic.setAdapter(new ArrayAdapter<>(this, android.R.layout.simple_spinner_dropdown_item, topics));
        topic.setSelection(Math.max(0, topics.indexOf(existing != null ? existing.cat : Deck.MY_CARDS)));
        final EditText q = new EditText(this);
        q.setHint("Question");
        final EditText a = new EditText(this);
        a.setHint("Answer (wrap code in ``` lines)");
        a.setMinLines(3);
        if (existing != null) {
            q.setText(existing.q);
            a.setText(existing.a);
        }
        box.addView(topic);
        box.addView(q);
        box.addView(a);
        AlertDialog.Builder b = new AlertDialog.Builder(this)
                .setTitle(existing == null ? "New card" : "Edit card")
                .setView(box)
                .setPositiveButton("Save", (d, w) -> {
                    String qs = q.getText().toString().trim(), as = a.getText().toString().trim();
                    if (qs.isEmpty() || as.isEmpty()) {
                        Toast.makeText(this, "Both question and answer are needed", Toast.LENGTH_SHORT).show();
                        return;
                    }
                    Deck.Card c = existing;
                    if (c == null) {
                        c = new Deck.Card();
                        c.custom = true;
                        c.id = Deck.idFor("u", qs + System.currentTimeMillis());
                        deck.cards.add(c);
                    }
                    c.q = qs;
                    c.a = as;
                    c.cat = (String) topic.getSelectedItem();
                    if (!deck.topics.contains(c.cat)) deck.topics.add(c.cat);
                    deck.saveCustom(prefs);
                    expanded.add(c.id);
                    showBrowse();
                })
                .setNegativeButton("Cancel", null);
        if (existing != null) {
            b.setNeutralButton("Delete", (d, w) -> {
                deck.cards.remove(existing);
                progress.forget(existing.id);
                deck.saveCustom(prefs);
                showBrowse();
            });
        }
        b.show();
    }

    // ================= stats =================

    private void showStats() {
        LinearLayout[] f = frame("Progress", null);
        LinearLayout body = f[1];
        int seen = 0, mastered = 0;
        for (Deck.Card c : deck.cards) {
            if (!progress.isNew(c.id)) seen++;
            if (progress.mastered(c.id)) mastered++;
        }
        LinearLayout tiles = new LinearLayout(this);
        String[][] t = {
                {String.valueOf(progress.streak()), "day streak"},
                {String.valueOf(progress.totalReviews()), "activities"},
                {seen + "/" + deck.cards.size(), "cards seen"},
                {String.valueOf(mastered), "mastered"}};
        for (int i = 0; i < t.length; i++) {
            LinearLayout c = card();
            c.setPadding(dp(10), dp(10), dp(10), dp(10));
            c.addView(text(t[i][0], 18, Theme.TEXT, true));
            c.addView(text(t[i][1], 11, Theme.MUTED, false));
            LinearLayout.LayoutParams l = lp(0, WRAP, 1);
            l.setMargins(i == 0 ? 0 : dp(3), 0, i == t.length - 1 ? 0 : dp(3), 0);
            tiles.addView(c, l);
        }
        body.addView(tiles);

        LinearLayout hc = card();
        hc.addView(text("Study activity — last 16 weeks", 14, Theme.ACCENT, true));
        Charts.HeatmapView hm = new Charts.HeatmapView(this);
        hm.setProgress(progress);
        hc.addView(hm, margins(MATCH, WRAP, 10, 0));
        body.addView(hc, margins(MATCH, WRAP, 12, 0));

        LinearLayout mc = card();
        mc.addView(text("Mastery by topic", 14, Theme.ACCENT, true));
        TextView mexp = text("Mastered = remembered well enough to be spaced a week or more apart.", 12, Theme.MUTED, false);
        mexp.setPadding(0, dp(2), 0, dp(10));
        mc.addView(mexp);
        List<Charts.MasteryView.Row> rows = new ArrayList<>();
        for (String topic : deck.topics) {
            Charts.MasteryView.Row r = new Charts.MasteryView.Row();
            r.name = topic;
            for (Deck.Card c : deck.cards) {
                if (!c.cat.equals(topic)) continue;
                r.total++;
                if (progress.mastered(c.id)) r.mastered++;
                else if (!progress.isNew(c.id)) r.learning++;
            }
            rows.add(r);
        }
        Charts.MasteryView mv = new Charts.MasteryView(this);
        mv.setRows(rows);
        mc.addView(mv);
        body.addView(mc, margins(MATCH, WRAP, 12, 0));

        LinearLayout qc = card();
        qc.addView(text("Quiz accuracy by topic", 14, Theme.ACCENT, true));
        boolean anyQ = false;
        String weakest = null;
        float weakestPct = 2f;
        for (String topic : deck.topics) {
            int[] s = progress.quizStats.get(topic);
            if (s == null || s[1] == 0) continue;
            anyQ = true;
            float p = s[0] / (float) s[1];
            if (s[1] >= 3 && p < weakestPct) {
                weakestPct = p;
                weakest = topic;
            }
            LinearLayout r = new LinearLayout(this);
            r.setPadding(0, dp(6), 0, 0);
            r.addView(text(topic, 14, Theme.TEXT, false), lp(0, WRAP, 1));
            r.addView(text(Math.round(p * 100) + "%  (" + s[0] + "/" + s[1] + ")", 14,
                    p >= 0.7f ? Theme.GOOD : (p >= 0.4f ? Theme.WARN : Theme.BAD), true));
            qc.addView(r);
        }
        if (!anyQ) qc.addView(text("Take a quiz to see your accuracy here.", 13, Theme.MUTED, false));
        if (weakest != null) {
            final String w = weakest;
            TextView tip = text("Weakest topic: " + weakest + ". Tap to browse its cards.", 13, Theme.WARN, false);
            tip.setPadding(0, dp(10), 0, 0);
            tip.setOnClickListener(v -> {
                browseFilter = w;
                showBrowse();
            });
            qc.addView(tip);
        }
        body.addView(qc, margins(MATCH, WRAP, 12, 0));

        LinearLayout hist = card();
        hist.addView(text("Recent scores", 14, Theme.ACCENT, true));
        addHistory(hist, "Quiz", progress.quizHistory);
        addHistory(hist, "Mock interview", progress.mockHistory);
        body.addView(hist, margins(MATCH, WRAP, 12, 0));

        Button reset = button("Reset all progress", Theme.PANEL, Theme.BAD, 14);
        reset.setOnClickListener(v -> new AlertDialog.Builder(this)
                .setMessage("Erase all review history, streaks and scores? Your own cards and bookmarks are kept.")
                .setPositiveButton("Reset", (d, w) -> {
                    progress.resetAll();
                    showStats();
                })
                .setNegativeButton("Cancel", null)
                .show());
        body.addView(reset, margins(MATCH, dp(46), 16, 0));
        setScreen(STATS, f[0]);
    }

    private void addHistory(LinearLayout parent, String label, JSONArray arr) {
        TextView h = text(label, 13, Theme.TEXT, true);
        h.setPadding(0, dp(8), 0, dp(2));
        parent.addView(h);
        if (arr.length() == 0) {
            parent.addView(text("None yet", 13, Theme.MUTED, false));
            return;
        }
        SimpleDateFormat f = new SimpleDateFormat("d MMM", Locale.getDefault());
        StringBuilder sb = new StringBuilder();
        for (int i = arr.length() - 1; i >= Math.max(0, arr.length() - 5); i--) {
            try {
                JSONObject o = arr.getJSONObject(i);
                int s = o.getInt("s"), n = o.getInt("n");
                int pct = n == 0 ? 0 : Math.round(100f * s / n);
                if (sb.length() > 0) sb.append('\n');
                sb.append(f.format(new Date(o.getLong("t")))).append("   ").append(pct).append('%');
                if (label.equals("Quiz")) sb.append("  (").append(s).append('/').append(n).append(')');
            } catch (Exception ignored) {
            }
        }
        parent.addView(text(sb.toString(), 13, Theme.MUTED, false));
    }

    @Override
    protected void onDestroy() {
        handler.removeCallbacksAndMessages(null);
        if (tts != null) {
            tts.stop();
            tts.shutdown();
        }
        super.onDestroy();
    }
}
