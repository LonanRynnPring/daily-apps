package com.dailyapps.walksteps;

import android.content.SharedPreferences;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

/** Saved routes, newest first, kept as JSON in SharedPreferences. */
final class Store {
    static final int MAX = 30;

    static final class Saved {
        long id;
        String title = "";
        String summary = "";
        long created;
        int position;
        List<Parser.Step> steps = new ArrayList<>();
    }

    private Store() {}

    static List<Saved> load(SharedPreferences prefs) {
        List<Saved> out = new ArrayList<>();
        try {
            JSONArray arr = new JSONArray(prefs.getString("routes", "[]"));
            for (int i = 0; i < arr.length(); i++) {
                JSONObject o = arr.getJSONObject(i);
                Saved s = new Saved();
                s.id = o.optLong("id");
                s.title = o.optString("title");
                s.summary = o.optString("summary");
                s.created = o.optLong("created");
                s.position = o.optInt("pos");
                JSONArray st = o.optJSONArray("steps");
                if (st != null) {
                    for (int k = 0; k < st.length(); k++) {
                        JSONObject so = st.getJSONObject(k);
                        Parser.Step p = new Parser.Step();
                        p.kind = so.optString("k", Parser.OTHER);
                        p.text = so.optString("t");
                        p.road = so.optString("r");
                        p.distance = so.optString("d");
                        p.note = so.optString("n");
                        p.original = so.optString("o");
                        s.steps.add(p);
                    }
                }
                out.add(s);
            }
        } catch (Exception ignored) {
        }
        return out;
    }

    static void save(SharedPreferences prefs, List<Saved> list) {
        try {
            JSONArray arr = new JSONArray();
            for (int i = 0; i < list.size() && i < MAX; i++) {
                Saved s = list.get(i);
                JSONObject o = new JSONObject();
                o.put("id", s.id);
                o.put("title", s.title);
                o.put("summary", s.summary);
                o.put("created", s.created);
                o.put("pos", s.position);
                JSONArray st = new JSONArray();
                for (Parser.Step p : s.steps) {
                    JSONObject so = new JSONObject();
                    so.put("k", p.kind);
                    so.put("t", p.text);
                    so.put("r", p.road);
                    so.put("d", p.distance);
                    so.put("n", p.note);
                    so.put("o", p.original);
                    st.put(so);
                }
                o.put("steps", st);
                arr.put(o);
            }
            prefs.edit().putString("routes", arr.toString()).apply();
        } catch (Exception ignored) {
        }
    }
}
