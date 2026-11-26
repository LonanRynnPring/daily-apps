package com.dailyapps.chordwheel;

import android.content.Context;
import android.content.SharedPreferences;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

/** Saved progressions, kept as a JSON array in SharedPreferences, plus built-in presets. */
final class Store {
    private Store() {}

    static final class Saved {
        String name;
        int tonic; // -1 = use current key (presets)
        boolean minor;
        String items;
        long time;

        Saved(String name, int tonic, boolean minor, String items, long time) {
            this.name = name;
            this.tonic = tonic;
            this.minor = minor;
            this.items = items;
            this.time = time;
        }
    }

    static List<Saved> presets() {
        List<Saved> p = new ArrayList<>();
        p.add(new Saved("Pop axis", -1, false, "0 4 5 3", 0));
        p.add(new Saved("50s doo-wop", -1, false, "0 5 3 4", 0));
        p.add(new Saved("Jazz ii–V–I", -1, false, "1s 4s 0s 0s", 0));
        p.add(new Saved("12-bar blues", -1, false, "0 0 0 0 3 3 0 0 4 3 0 4", 0));
        p.add(new Saved("Canon", -1, false, "0 4 5 2 3 0 3 4", 0));
        p.add(new Saved("Royal road", -1, false, "3s 4s 2s 5", 0));
        p.add(new Saved("Sensitive ballad", -1, false, "5 3 0 4", 0));
        p.add(new Saved("Circle turnaround", -1, false, "5s 1s 4s 0s", 0));
        p.add(new Saved("Plagal hymn", -1, false, "0 3 0 3 5 3 0 0", 0));
        p.add(new Saved("Epic minor", -1, true, "0 5 2 6", 0));
        p.add(new Saved("Descending minor", -1, true, "0 6 5 4", 0));
        p.add(new Saved("Minor loop", -1, true, "0 3 6 2", 0));
        return p;
    }

    private static SharedPreferences prefs(Context c) {
        return c.getSharedPreferences("library", Context.MODE_PRIVATE);
    }

    static List<Saved> load(Context c) {
        List<Saved> out = new ArrayList<>();
        String raw = prefs(c).getString("items", "[]");
        try {
            JSONArray arr = new JSONArray(raw);
            for (int i = 0; i < arr.length(); i++) {
                JSONObject o = arr.getJSONObject(i);
                out.add(new Saved(o.optString("name", "Untitled"), o.optInt("tonic", 0),
                        o.optBoolean("minor", false), o.optString("items", ""), o.optLong("time", 0)));
            }
        } catch (JSONException ignored) {
        }
        return out;
    }

    static void saveAll(Context c, List<Saved> list) {
        JSONArray arr = new JSONArray();
        try {
            for (Saved s : list) {
                JSONObject o = new JSONObject();
                o.put("name", s.name);
                o.put("tonic", s.tonic);
                o.put("minor", s.minor);
                o.put("items", s.items);
                o.put("time", s.time);
                arr.put(o);
            }
        } catch (JSONException ignored) {
        }
        prefs(c).edit().putString("items", arr.toString()).apply();
    }

    /** Adds or replaces (by name, case-insensitive) and keeps newest first. */
    static void add(Context c, Saved s) {
        List<Saved> list = load(c);
        for (int i = list.size() - 1; i >= 0; i--) {
            if (list.get(i).name.equalsIgnoreCase(s.name)) list.remove(i);
        }
        list.add(0, s);
        saveAll(c, list);
    }

    static void remove(Context c, int index) {
        List<Saved> list = load(c);
        if (index >= 0 && index < list.size()) {
            list.remove(index);
            saveAll(c, list);
        }
    }

    static String shareText(String name, int tonic, boolean minor, List<Theory.Item> items) {
        StringBuilder sb = new StringBuilder();
        sb.append(name).append(" — ").append(Theory.keyName(tonic, minor)).append('\n');
        sb.append(Theory.romanLine(minor, items)).append('\n');
        sb.append(Theory.chordLine(tonic, minor, items)).append('\n');
        sb.append("(made with Chord Wheel)");
        return sb.toString();
    }
}
