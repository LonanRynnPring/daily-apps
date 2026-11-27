package com.dailyapps.dotnetprep;

import android.content.SharedPreferences;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;
import java.util.TimeZone;

/** Spaced-repetition state, activity log and quiz/mock statistics. */
final class Progress {
    static final int AGAIN = 0, HARD = 1, GOOD = 2, EASY = 3;
    static final int[] INTERVALS = {0, 1, 3, 7, 16, 35, 80};
    static final int MAX_BOX = INTERVALS.length - 1;
    static final int MASTERED_BOX = 3;

    private final SharedPreferences prefs;
    /** id -> {box, dueDay, reps, lapses} */
    final Map<String, int[]> srs = new HashMap<>();
    final Map<Integer, Integer> activity = new HashMap<>();
    final Map<String, int[]> quizStats = new HashMap<>();
    final Set<String> bookmarks = new HashSet<>();
    JSONArray quizHistory = new JSONArray();
    JSONArray mockHistory = new JSONArray();

    Progress(SharedPreferences prefs) {
        this.prefs = prefs;
        try {
            JSONObject o = new JSONObject(prefs.getString("srs", "{}"));
            Iterator<String> it = o.keys();
            while (it.hasNext()) {
                String k = it.next();
                JSONArray a = o.getJSONArray(k);
                srs.put(k, new int[]{a.getInt(0), a.getInt(1), a.optInt(2), a.optInt(3)});
            }
        } catch (Exception ignored) {
        }
        try {
            JSONObject o = new JSONObject(prefs.getString("activity", "{}"));
            Iterator<String> it = o.keys();
            while (it.hasNext()) {
                String k = it.next();
                activity.put(Integer.parseInt(k), o.getInt(k));
            }
        } catch (Exception ignored) {
        }
        try {
            JSONObject o = new JSONObject(prefs.getString("quizStats", "{}"));
            Iterator<String> it = o.keys();
            while (it.hasNext()) {
                String k = it.next();
                JSONArray a = o.getJSONArray(k);
                quizStats.put(k, new int[]{a.getInt(0), a.getInt(1)});
            }
        } catch (Exception ignored) {
        }
        try {
            quizHistory = new JSONArray(prefs.getString("quizHistory", "[]"));
            mockHistory = new JSONArray(prefs.getString("mockHistory", "[]"));
        } catch (Exception ignored) {
        }
        Set<String> bm = prefs.getStringSet("bookmarks", null);
        if (bm != null) bookmarks.addAll(bm);
    }

    static int today() {
        long now = System.currentTimeMillis();
        return (int) ((now + TimeZone.getDefault().getOffset(now)) / 86400000L);
    }

    boolean isNew(String id) { return !srs.containsKey(id); }

    boolean isDue(String id, int day) {
        int[] s = srs.get(id);
        return s != null && s[1] <= day;
    }

    int box(String id) {
        int[] s = srs.get(id);
        return s == null ? -1 : s[0];
    }

    boolean mastered(String id) { return box(id) >= MASTERED_BOX; }

    /** Days until next review if this rating were chosen. */
    int previewDays(String id, int rating) {
        int b = Math.max(0, box(id));
        switch (rating) {
            case AGAIN: return 0;
            case HARD: return Math.max(1, INTERVALS[Math.max(1, b)] / 2);
            case GOOD: return INTERVALS[Math.min(MAX_BOX, b + 1)];
            default: return INTERVALS[Math.min(MAX_BOX, b + 2)];
        }
    }

    void rate(String id, int rating) {
        int day = today();
        int[] s = srs.get(id);
        if (s == null) s = new int[]{0, day, 0, 0};
        int b = s[0];
        switch (rating) {
            case AGAIN:
                b = 0;
                s[3]++;
                break;
            case HARD:
                b = Math.max(1, b);
                break;
            case GOOD:
                b = Math.min(MAX_BOX, b + 1);
                break;
            default:
                b = Math.min(MAX_BOX, b + 2);
                break;
        }
        int days = rating == HARD ? Math.max(1, INTERVALS[b] / 2) : INTERVALS[b];
        s[0] = b;
        s[1] = day + days;
        s[2]++;
        srs.put(id, s);
        logActivity();
        saveSrs();
    }

    void logActivity() {
        int d = today();
        Integer v = activity.get(d);
        activity.put(d, v == null ? 1 : v + 1);
        try {
            JSONObject o = new JSONObject();
            for (Map.Entry<Integer, Integer> e : activity.entrySet()) o.put(String.valueOf(e.getKey()), e.getValue());
            prefs.edit().putString("activity", o.toString()).apply();
        } catch (Exception ignored) {
        }
    }

    private void saveSrs() {
        try {
            JSONObject o = new JSONObject();
            for (Map.Entry<String, int[]> e : srs.entrySet()) {
                int[] s = e.getValue();
                JSONArray a = new JSONArray();
                a.put(s[0]);
                a.put(s[1]);
                a.put(s[2]);
                a.put(s[3]);
                o.put(e.getKey(), a);
            }
            prefs.edit().putString("srs", o.toString()).apply();
        } catch (Exception ignored) {
        }
    }

    void forget(String id) {
        srs.remove(id);
        saveSrs();
    }

    int streak() {
        int d = today();
        if (!activity.containsKey(d)) d--;
        int n = 0;
        while (activity.containsKey(d)) {
            n++;
            d--;
        }
        return n;
    }

    int totalReviews() {
        int n = 0;
        for (int v : activity.values()) n += v;
        return n;
    }

    int activityOn(int day) {
        Integer v = activity.get(day);
        return v == null ? 0 : v;
    }

    void recordQuizAnswer(String cat, boolean correct) {
        int[] s = quizStats.get(cat);
        if (s == null) s = new int[2];
        if (correct) s[0]++;
        s[1]++;
        quizStats.put(cat, s);
        try {
            JSONObject o = new JSONObject();
            for (Map.Entry<String, int[]> e : quizStats.entrySet()) {
                JSONArray a = new JSONArray();
                a.put(e.getValue()[0]);
                a.put(e.getValue()[1]);
                o.put(e.getKey(), a);
            }
            prefs.edit().putString("quizStats", o.toString()).apply();
        } catch (Exception ignored) {
        }
    }

    void addHistory(boolean mock, int score, int total) {
        try {
            JSONObject o = new JSONObject();
            o.put("t", System.currentTimeMillis());
            o.put("s", score);
            o.put("n", total);
            JSONArray arr = mock ? mockHistory : quizHistory;
            arr.put(o);
            JSONArray trimmed = new JSONArray();
            for (int i = Math.max(0, arr.length() - 30); i < arr.length(); i++) trimmed.put(arr.get(i));
            if (mock) mockHistory = trimmed; else quizHistory = trimmed;
            prefs.edit().putString(mock ? "mockHistory" : "quizHistory", trimmed.toString()).apply();
        } catch (Exception ignored) {
        }
        logActivity();
    }

    boolean toggleBookmark(String id) {
        boolean on;
        if (bookmarks.contains(id)) {
            bookmarks.remove(id);
            on = false;
        } else {
            bookmarks.add(id);
            on = true;
        }
        prefs.edit().putStringSet("bookmarks", new HashSet<>(bookmarks)).apply();
        return on;
    }

    void resetAll() {
        srs.clear();
        activity.clear();
        quizStats.clear();
        quizHistory = new JSONArray();
        mockHistory = new JSONArray();
        prefs.edit().remove("srs").remove("activity").remove("quizStats").remove("quizHistory")
                .remove("mockHistory").remove("newDay").remove("newCount").apply();
    }
}
