package com.dailyapps.pocketpet;

import android.content.SharedPreferences;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/** All pet data plus the care simulation. Time advances in one-minute steps, even while the app is closed. */
final class PetState {
    static final int EGG = 0, BABY = 1, CHILD = 2, TEEN = 3, ADULT = 4;
    static final String[] STAGE_NAMES = {"Egg", "Baby", "Child", "Teen", "Adult"};

    static final int MOOD_DEAD = 0, MOOD_SLEEP = 1, MOOD_SICK = 2, MOOD_SAD = 3, MOOD_OK = 4, MOOD_HAPPY = 5;

    static final String[] FOODS = {"Rice ball", "Apple", "Cake", "Burger", "Fish"};
    static final int[] FOOD_EMOJI = {0x1F359, 0x1F34E, 0x1F370, 0x1F354, 0x1F41F};
    static final int[] FOOD_FULL = {30, 15, 12, 35, 25};
    static final int[] FOOD_HAPPY = {2, 5, 15, 6, 8};

    static final long HATCH_MS = 60000L;
    private static final long HOUR = 3600000L;

    boolean exists, hatched, dead, sleeping, sick;
    long bornAt, hatchedAt, diedAt, lastTick, diaryAt;
    String name = "", personality = "", likes = "", quirk = "", catchphrase = "", diary = "";
    float full = 80f, happy = 80f, energy = 100f, dirt = 0f, health = 100f;
    int poops, poopTimer, hue = 200;
    final List<String> events = new ArrayList<String>();
    final List<String> memorial = new ArrayList<String>();
    final List<String> notices = new ArrayList<String>();
    private final Random rnd = new Random();

    // ------------------------------------------------------------ persistence

    void load(SharedPreferences p) {
        exists = p.getBoolean("exists", false);
        hatched = p.getBoolean("hatched", false);
        dead = p.getBoolean("dead", false);
        sleeping = p.getBoolean("sleeping", false);
        sick = p.getBoolean("sick", false);
        bornAt = p.getLong("bornAt", 0L);
        hatchedAt = p.getLong("hatchedAt", 0L);
        diedAt = p.getLong("diedAt", 0L);
        lastTick = p.getLong("lastTick", 0L);
        diaryAt = p.getLong("diaryAt", 0L);
        name = p.getString("name", "");
        personality = p.getString("personality", "");
        likes = p.getString("likes", "");
        quirk = p.getString("quirk", "");
        catchphrase = p.getString("catchphrase", "");
        diary = p.getString("diary", "");
        full = p.getFloat("full", 80f);
        happy = p.getFloat("happy", 80f);
        energy = p.getFloat("energy", 100f);
        dirt = p.getFloat("dirt", 0f);
        health = p.getFloat("health", 100f);
        poops = p.getInt("poops", 0);
        poopTimer = p.getInt("poopTimer", 0);
        hue = p.getInt("hue", 200);
        events.clear();
        String ev = p.getString("events", "");
        if (ev.length() > 0) {
            for (String s : ev.split("\n")) {
                if (s.length() > 0) events.add(s);
            }
        }
        memorial.clear();
        String mem = p.getString("memorial", "");
        if (mem.length() > 0) {
            for (String s : mem.split("\n")) {
                if (s.length() > 0) memorial.add(s);
            }
        }
    }

    void save(SharedPreferences p) {
        StringBuilder ev = new StringBuilder();
        for (int i = 0; i < events.size(); i++) {
            if (i > 0) ev.append('\n');
            ev.append(events.get(i));
        }
        StringBuilder mem = new StringBuilder();
        for (int i = 0; i < memorial.size(); i++) {
            if (i > 0) mem.append('\n');
            mem.append(memorial.get(i));
        }
        p.edit()
                .putBoolean("exists", exists)
                .putBoolean("hatched", hatched)
                .putBoolean("dead", dead)
                .putBoolean("sleeping", sleeping)
                .putBoolean("sick", sick)
                .putLong("bornAt", bornAt)
                .putLong("hatchedAt", hatchedAt)
                .putLong("diedAt", diedAt)
                .putLong("lastTick", lastTick)
                .putLong("diaryAt", diaryAt)
                .putString("name", name)
                .putString("personality", personality)
                .putString("likes", likes)
                .putString("quirk", quirk)
                .putString("catchphrase", catchphrase)
                .putString("diary", diary)
                .putFloat("full", full)
                .putFloat("happy", happy)
                .putFloat("energy", energy)
                .putFloat("dirt", dirt)
                .putFloat("health", health)
                .putInt("poops", poops)
                .putInt("poopTimer", poopTimer)
                .putInt("hue", hue)
                .putString("events", ev.toString())
                .putString("memorial", mem.toString())
                .apply();
    }

    // ---------------------------------------------------------------- lifecycle

    void startNewEgg(long now) {
        if (dead && name.length() > 0) {
            memorial.add(name + " (" + ageText(diedAt) + ")");
            while (memorial.size() > 8) memorial.remove(0);
        }
        exists = true;
        hatched = false;
        dead = false;
        sleeping = false;
        sick = false;
        bornAt = now;
        hatchedAt = 0L;
        diedAt = 0L;
        lastTick = now;
        name = "";
        personality = "";
        likes = "";
        quirk = "";
        catchphrase = "";
        diary = "";
        diaryAt = 0L;
        full = 80f;
        happy = 80f;
        energy = 100f;
        dirt = 0f;
        health = 100f;
        poops = 0;
        poopTimer = 0;
        events.clear();
        notices.clear();
    }

    int stage(long now) {
        if (!hatched) return EGG;
        long age = (dead ? diedAt : now) - hatchedAt;
        if (age < 3 * HOUR) return BABY;
        if (age < 24 * HOUR) return CHILD;
        if (age < 72 * HOUR) return TEEN;
        return ADULT;
    }

    String ageText(long now) {
        long end = dead ? diedAt : now;
        long age = Math.max(0L, end - hatchedAt);
        long mins = age / 60000L;
        if (mins < 60) return mins + " min";
        long hours = mins / 60;
        if (hours < 48) return hours + " h";
        return (hours / 24) + " days";
    }

    float hygiene() {
        return clamp(100f - poops * 20f - dirt);
    }

    int mood() {
        if (dead) return MOOD_DEAD;
        if (sleeping) return MOOD_SLEEP;
        if (sick) return MOOD_SICK;
        float lowest = Math.min(Math.min(full, happy), Math.min(energy, hygiene()));
        if (lowest < 30f) return MOOD_SAD;
        float avg = (full + happy + energy + hygiene()) / 4f;
        return avg > 70f ? MOOD_HAPPY : MOOD_OK;
    }

    boolean needsAttention() {
        if (!hatched || dead || sleeping) return false;
        return sick || poops >= 2 || full < 30f || happy < 30f || hygiene() < 30f;
    }

    String describe(long now) {
        return "fullness " + Math.round(full) + "/100, happiness " + Math.round(happy)
                + "/100, energy " + Math.round(energy) + "/100, cleanliness " + Math.round(hygiene())
                + "/100, health " + Math.round(health) + "/100, sick: " + (sick ? "yes" : "no")
                + ", messes lying around: " + poops + ", currently sleeping: " + (sleeping ? "yes" : "no");
    }

    // --------------------------------------------------------------- simulation

    void advance(long now) {
        if (!exists || !hatched || dead) {
            lastTick = now;
            return;
        }
        if (lastTick == 0L) lastTick = now;
        long maxBack = 24L * HOUR;
        if (now - lastTick > maxBack) lastTick = now - maxBack;
        boolean offline = (now - lastTick) > 5 * 60000L;
        while (lastTick + 60000L <= now) {
            lastTick += 60000L;
            stepMinute(lastTick, offline);
            if (dead) break;
        }
    }

    private void stepMinute(long t, boolean offline) {
        float d = 1f / 60f;
        if (sleeping) {
            energy += 30f * d;
            full -= 5f * d;
            if (energy >= 100f) {
                energy = 100f;
                sleeping = false;
                addEvent(t, "woke up feeling rested");
            }
        } else {
            full -= 10f * d;
            happy -= 7f * d;
            energy -= 6f * d;
            dirt += 2f * d;
            if (energy <= 0f) {
                energy = 0f;
                sleeping = true;
                addEvent(t, "fell asleep from exhaustion");
                notices.add(name + " fell asleep");
            }
            poopTimer++;
            if (poopTimer >= 120 + rnd.nextInt(60) && full > 15f) {
                poopTimer = 0;
                if (poops < 4) {
                    poops++;
                    addEvent(t, "made a mess");
                    notices.add(name + " made a mess");
                }
            }
        }
        float hyg = hygiene();
        if (!sick && (poops >= 3 || hyg < 25f) && rnd.nextInt(100) < 1) {
            sick = true;
            addEvent(t, "got sick");
            notices.add(name + " is sick!");
        }
        if (full <= 0f) health -= 12f * d;
        if (sick) {
            health -= 8f * d;
            happy -= 5f * d;
        }
        if (happy <= 0f) health -= 3f * d;
        if (!sick && full > 40f && hyg > 40f && health < 100f) health += 6f * d;

        full = clamp(full);
        happy = clamp(happy);
        energy = clamp(energy);
        health = clamp(health);
        if (dirt < 0f) dirt = 0f;
        if (dirt > 100f) dirt = 100f;

        if (offline && health < 5f) health = 5f;
        if (health <= 0f) {
            dead = true;
            diedAt = t;
            addEvent(t, "passed away");
            notices.add(name + " has passed away");
        }
    }

    // ----------------------------------------------------------------- actions

    /** Returns -2 sleeping, -1 not hungry, 0 fed, 1 fed their favourite. */
    int feed(int food, long now) {
        if (sleeping) return -2;
        if (full >= 95f) return -1;
        full = clamp(full + FOOD_FULL[food]);
        happy = clamp(happy + FOOD_HAPPY[food]);
        boolean liked = likes.length() > 0 && likes.toLowerCase().contains(FOODS[food].toLowerCase());
        if (liked) happy = clamp(happy + 15f);
        addEvent(now, "ate " + FOODS[food].toLowerCase() + (liked ? " (a favourite!)" : ""));
        return liked ? 1 : 0;
    }

    void play(int wins, long now) {
        happy = clamp(happy + 8f + wins * 9f);
        energy = clamp(energy - 12f);
        full = clamp(full - 6f);
        addEvent(now, "played a jumping game with its owner (won " + wins + " of 3)");
    }

    void clean(long now) {
        poops = 0;
        dirt = 0f;
        happy = clamp(happy + 3f);
        addEvent(now, "got cleaned up");
    }

    boolean medicine(long now) {
        if (!sick) return false;
        sick = false;
        health = clamp(health + 25f);
        addEvent(now, "took medicine and feels better");
        return true;
    }

    /** Returns 0 ok (now asleep), 1 woke up, -1 not tired. */
    int toggleSleep(long now) {
        if (sleeping) {
            sleeping = false;
            addEvent(now, "was woken up");
            return 1;
        }
        if (energy >= 90f) return -1;
        sleeping = true;
        addEvent(now, "went to sleep");
        return 0;
    }

    void addEvent(long t, String text) {
        events.add(t + "|" + text);
        while (events.size() > 40) events.remove(0);
    }

    private static float clamp(float v) {
        if (v < 0f) return 0f;
        if (v > 100f) return 100f;
        return v;
    }
}
