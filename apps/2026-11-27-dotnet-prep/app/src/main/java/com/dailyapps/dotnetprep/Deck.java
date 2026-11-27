package com.dailyapps.dotnetprep;

import android.content.Context;
import android.content.SharedPreferences;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/** All flashcards and multiple-choice questions, from assets/cards.txt plus the user's own cards. */
final class Deck {
    static final String MY_CARDS = "My cards";

    static final class Card {
        String id, cat, q, a;
        boolean custom;
    }

    static final class Mcq {
        String id, cat, q, expl = "";
        final List<String> options = new ArrayList<>();
        int correct;
    }

    final List<Card> cards = new ArrayList<>();
    final List<Mcq> mcqs = new ArrayList<>();
    final List<String> topics = new ArrayList<>();

    private String cat = "";
    private int mode; // 0 none, 1 question, 2 answer, 3 mcq
    private StringBuilder q, a;
    private Mcq cur;

    static String idFor(String prefix, String text) {
        return prefix + Integer.toHexString(text.trim().hashCode());
    }

    static Deck load(Context c, SharedPreferences prefs) {
        Deck d = new Deck();
        try (BufferedReader br = new BufferedReader(new InputStreamReader(
                c.getAssets().open("cards.txt"), StandardCharsets.UTF_8))) {
            String line;
            while ((line = br.readLine()) != null) d.line(line);
            d.flush();
        } catch (Exception e) {
            // leave whatever was parsed
        }
        d.loadCustom(prefs);
        return d;
    }

    private void line(String line) {
        if (line.startsWith("# ")) {
            flush();
            cat = line.substring(2).trim();
            if (!topics.contains(cat)) topics.add(cat);
            return;
        }
        if (line.startsWith("Q: ")) {
            flush();
            q = new StringBuilder(line.substring(3));
            mode = 1;
            return;
        }
        if (line.startsWith("M: ")) {
            flush();
            cur = new Mcq();
            cur.cat = cat;
            cur.q = line.substring(3);
            mode = 3;
            return;
        }
        switch (mode) {
            case 1:
                if (line.startsWith("A: ")) {
                    a = new StringBuilder(line.substring(3));
                    mode = 2;
                } else {
                    q.append('\n').append(line);
                }
                break;
            case 2:
                if (line.trim().isEmpty()) flush();
                else a.append('\n').append(line);
                break;
            case 3:
                if (line.startsWith("* ")) {
                    cur.correct = cur.options.size();
                    cur.options.add(line.substring(2).trim());
                } else if (line.startsWith("- ")) {
                    cur.options.add(line.substring(2).trim());
                } else if (line.startsWith("E: ")) {
                    cur.expl = line.substring(3).trim();
                } else if (line.trim().isEmpty()) {
                    flush();
                }
                break;
            default:
                break;
        }
    }

    private void flush() {
        if (mode == 2 && q != null && a != null) {
            Card c = new Card();
            c.cat = cat;
            c.q = q.toString().trim();
            c.a = a.toString().trim();
            c.id = idFor("c", c.q);
            cards.add(c);
        } else if (mode == 3 && cur != null && cur.options.size() >= 2) {
            cur.id = idFor("m", cur.q);
            mcqs.add(cur);
        }
        mode = 0;
        q = null;
        a = null;
        cur = null;
    }

    private void loadCustom(SharedPreferences prefs) {
        try {
            JSONArray arr = new JSONArray(prefs.getString("custom", "[]"));
            for (int i = 0; i < arr.length(); i++) {
                JSONObject o = arr.getJSONObject(i);
                Card c = new Card();
                c.cat = o.optString("cat", MY_CARDS);
                c.q = o.optString("q");
                c.a = o.optString("a");
                c.id = o.optString("id", idFor("u", c.q));
                c.custom = true;
                if (c.q.isEmpty()) continue;
                cards.add(c);
                if (!topics.contains(c.cat)) topics.add(c.cat);
            }
        } catch (Exception ignored) {
        }
    }

    void saveCustom(SharedPreferences prefs) {
        try {
            JSONArray arr = new JSONArray();
            for (Card c : cards) {
                if (!c.custom) continue;
                JSONObject o = new JSONObject();
                o.put("id", c.id);
                o.put("cat", c.cat);
                o.put("q", c.q);
                o.put("a", c.a);
                arr.put(o);
            }
            prefs.edit().putString("custom", arr.toString()).apply();
        } catch (Exception ignored) {
        }
    }

    int countIn(String topic) {
        int n = 0;
        for (Card c : cards) if (c.cat.equals(topic)) n++;
        return n;
    }
}
