package com.dailyapps.pocketpet;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.SharedPreferences;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.os.Handler;
import android.text.InputType;
import android.view.Gravity;
import android.view.HapticFeedbackConstants;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import org.json.JSONObject;

import java.net.SocketTimeoutException;
import java.net.UnknownHostException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.Random;

public class MainActivity extends Activity {
    private static final String DEFAULT_MODEL = "claude-haiku-5-5";
    private static final int DIALOG_THEME = android.R.style.Theme_Material_Dialog_Alert;

    private static final String[] SEEDS = {"grumpy but lovable", "dreamy", "mischievous", "shy", "bouncy",
            "sleepy", "dramatic", "curious", "sassy", "gentle", "adventurous", "greedy", "proud", "silly"};
    private static final String[] FALLBACK_NAMES = {"Momo", "Pixel", "Bean", "Nugget", "Zuzu", "Miso",
            "Tofu", "Pip", "Boba", "Kiki"};
    private static final String[] FALLBACK_PERSONALITY = {"cheeky and bouncy", "shy but sweet",
            "dramatic and proud", "sleepy dreamer", "curious explorer"};
    private static final String[] FALLBACK_QUIRK = {"hums when happy", "hides from the vacuum cleaner",
            "collects shiny things", "naps in odd places"};
    private static final String[] FALLBACK_CATCH = {"Snack time?", "Pet me!", "Boop!", "Hehe!"};

    private interface AiCallback {
        void done(String text, String error);
    }

    private final Random rnd = new Random();
    private final Handler handler = new Handler();
    private final List<String[]> chat = new ArrayList<String[]>();

    private SharedPreferences prefs;
    private PetState pet;
    private PetView petView;
    private TextView nameText, infoText, speech;
    private final ProgressBar[] bars = new ProgressBar[5];
    private boolean hatching, busyChat, deathShown;
    private long lastThought;
    private int lastStage = -1;
    private int ticks;

    private final Runnable tickRunnable = new Runnable() {
        @Override
        public void run() {
            doTick();
            handler.postDelayed(this, 1000);
        }
    };

    private int dp(int v) {
        return (int) (v * getResources().getDisplayMetrics().density + 0.5f);
    }

    // ------------------------------------------------------------------- UI

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        getWindow().setStatusBarColor(0xFF1B1530);
        getWindow().setNavigationBarColor(0xFF1B1530);
        prefs = getSharedPreferences("pocketpet", MODE_PRIVATE);
        pet = new PetState();
        pet.load(prefs);
        buildUi();
    }

    @Override
    protected void onResume() {
        super.onResume();
        handler.removeCallbacks(tickRunnable);
        handler.post(tickRunnable);
    }

    @Override
    protected void onPause() {
        super.onPause();
        handler.removeCallbacks(tickRunnable);
        pet.save(prefs);
    }

    private GradientDrawable rounded(int color, int radiusDp) {
        GradientDrawable g = new GradientDrawable();
        g.setColor(color);
        g.setCornerRadius(dp(radiusDp));
        return g;
    }

    private Button actionButton(String label, View.OnClickListener l) {
        Button btn = new Button(this);
        btn.setText(label);
        btn.setAllCaps(false);
        btn.setTextSize(13);
        btn.setTextColor(Color.WHITE);
        btn.setBackground(rounded(0xFF3A2F63, 12));
        btn.setMinHeight(dp(44));
        btn.setMinimumHeight(dp(44));
        btn.setPadding(dp(4), 0, dp(4), 0);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
        lp.setMargins(dp(3), dp(3), dp(3), dp(3));
        btn.setLayoutParams(lp);
        btn.setOnClickListener(l);
        return btn;
    }

    private void buildUi() {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(0xFF1B1530);
        root.setPadding(dp(12), dp(8), dp(12), dp(8));

        LinearLayout header = new LinearLayout(this);
        header.setOrientation(LinearLayout.HORIZONTAL);
        header.setGravity(Gravity.CENTER_VERTICAL);
        nameText = new TextView(this);
        nameText.setTextColor(Color.WHITE);
        nameText.setTextSize(22);
        nameText.setTypeface(Typeface.DEFAULT_BOLD);
        header.addView(nameText, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
        infoText = new TextView(this);
        infoText.setTextColor(0xFFB7AEE0);
        infoText.setTextSize(14);
        header.addView(infoText);
        root.addView(header);

        speech = new TextView(this);
        speech.setTextColor(0xFF1B1530);
        speech.setTextSize(15);
        speech.setBackground(rounded(0xFFF4F0FF, 14));
        speech.setPadding(dp(12), dp(8), dp(12), dp(8));
        speech.setMinLines(2);
        speech.setGravity(Gravity.CENTER_VERTICAL);
        speech.setTextIsSelectable(true);
        LinearLayout.LayoutParams slp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        slp.setMargins(0, dp(6), 0, dp(6));
        root.addView(speech, slp);

        petView = new PetView(this);
        petView.pet = pet;
        petView.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                onPetTapped();
            }
        });
        root.addView(petView, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f));

        LinearLayout barRow = new LinearLayout(this);
        barRow.setOrientation(LinearLayout.HORIZONTAL);
        int[] icons = {0x1F359, 0x1F60A, 0x26A1, 0x1F9FC, 0x2764};
        int[] colors = {0xFFFFA94D, 0xFFFFD43B, 0xFF74C0FC, 0xFF63E6BE, 0xFFFF6B6B};
        for (int i = 0; i < 5; i++) {
            LinearLayout col = new LinearLayout(this);
            col.setOrientation(LinearLayout.VERTICAL);
            col.setGravity(Gravity.CENTER_HORIZONTAL);
            TextView ic = new TextView(this);
            ic.setText(PetView.emoji(icons[i]));
            ic.setTextSize(16);
            ic.setGravity(Gravity.CENTER);
            col.addView(ic);
            ProgressBar pb = new ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal);
            pb.setMax(100);
            pb.setProgressTintList(ColorStateList.valueOf(colors[i]));
            pb.setProgressBackgroundTintList(ColorStateList.valueOf(0xFF3A2F63));
            pb.setProgressBackgroundTintMode(android.graphics.PorterDuff.Mode.SRC_IN);
            LinearLayout.LayoutParams plp = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT, dp(10));
            plp.setMargins(dp(4), 0, dp(4), 0);
            col.addView(pb, plp);
            bars[i] = pb;
            barRow.addView(col, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
        }
        LinearLayout.LayoutParams blp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        blp.setMargins(0, dp(8), 0, dp(4));
        root.addView(barRow, blp);

        LinearLayout row1 = new LinearLayout(this);
        row1.setOrientation(LinearLayout.HORIZONTAL);
        row1.addView(actionButton(PetView.emoji(0x1F359) + " Feed", new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                feedAction();
            }
        }));
        row1.addView(actionButton(PetView.emoji(0x1F3AE) + " Play", new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                playAction();
            }
        }));
        row1.addView(actionButton(PetView.emoji(0x1F6C1) + " Clean", new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                cleanAction();
            }
        }));
        row1.addView(actionButton(PetView.emoji(0x1F4A1) + " Lights", new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                sleepAction();
            }
        }));
        root.addView(row1);

        LinearLayout row2 = new LinearLayout(this);
        row2.setOrientation(LinearLayout.HORIZONTAL);
        row2.addView(actionButton(PetView.emoji(0x1F48A) + " Heal", new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                healAction();
            }
        }));
        row2.addView(actionButton(PetView.emoji(0x1F4AC) + " Talk", new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                talkAction();
            }
        }));
        row2.addView(actionButton(PetView.emoji(0x1F4D6) + " Diary", new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                showDiary();
            }
        }));
        row2.addView(actionButton(PetView.emoji(0x2699) + " AI", new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                showSettings();
            }
        }));
        root.addView(row2);

        setContentView(root);
        say("...");
    }

    private void say(String s) {
        speech.setText(s);
    }

    private void toast(String s) {
        Toast.makeText(this, s, Toast.LENGTH_SHORT).show();
    }

    private AlertDialog.Builder dialog() {
        return new AlertDialog.Builder(this, DIALOG_THEME);
    }

    // ----------------------------------------------------------------- tick

    private void doTick() {
        long now = System.currentTimeMillis();
        if (!pet.exists) {
            pet.startNewEgg(now);
            lastStage = -1;
            chat.clear();
            say("An egg! Look after it and it will hatch soon.");
            pet.save(prefs);
        }
        if (!pet.hatched) {
            if (now >= pet.bornAt + PetState.HATCH_MS && !hatching) startHatch();
        } else {
            pet.advance(now);
            int st = pet.stage(now);
            if (lastStage != -1 && st > lastStage && !pet.dead) {
                String sn = PetState.STAGE_NAMES[st];
                pet.addEvent(now, "grew up into a " + sn.toLowerCase());
                toast(pet.name + " grew into a " + sn + "!");
                petView.sparkleUntil = now + 3000L;
                petView.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS);
            }
            lastStage = st;
        }
        if (!pet.notices.isEmpty()) {
            for (String n : pet.notices) toast(n);
            pet.notices.clear();
            petView.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY);
        }
        if (pet.dead && !deathShown) {
            deathShown = true;
            showDeathDialog();
        }
        updateUi(now);
        ticks++;
        if (ticks % 15 == 0) pet.save(prefs);
    }

    private void updateUi(long now) {
        if (!pet.hatched) {
            nameText.setText("Egg");
            long left = Math.max(0L, (pet.bornAt + PetState.HATCH_MS - now) / 1000L);
            infoText.setText(hatching ? "hatching..." : "hatches in " + left + "s");
        } else {
            nameText.setText(pet.dead ? pet.name + " (RIP)" : pet.name);
            infoText.setText(PetState.STAGE_NAMES[pet.stage(now)] + " · " + pet.ageText(now));
        }
        bars[0].setProgress(Math.round(pet.full));
        bars[1].setProgress(Math.round(pet.happy));
        bars[2].setProgress(Math.round(pet.energy));
        bars[3].setProgress(Math.round(pet.hygiene()));
        bars[4].setProgress(Math.round(pet.health));
    }

    // ------------------------------------------------------------------- AI

    private String friendlyError(Exception ex) {
        if (ex instanceof UnknownHostException || ex instanceof SocketTimeoutException) {
            return "Can't reach the AI right now. Check your connection.";
        }
        String m = ex.getMessage();
        return m != null ? m : ex.toString();
    }

    private void ask(final String system, final List<String[]> hist, final AiCallback cb) {
        final String key = prefs.getString("key", "");
        final String model = prefs.getString("model", DEFAULT_MODEL);
        final List<String[]> copy = new ArrayList<String[]>(hist);
        new Thread(new Runnable() {
            @Override
            public void run() {
                String out = null;
                String err = null;
                try {
                    out = ApiClient.chat(key, model, system, copy);
                } catch (Exception ex) {
                    err = friendlyError(ex);
                }
                final String fo = out;
                final String fe = err;
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        cb.done(fo, fe);
                    }
                });
            }
        }).start();
    }

    private boolean hasKey() {
        return prefs.getString("key", "").length() > 0;
    }

    private String pick(String[] a) {
        return a[rnd.nextInt(a.length)];
    }

    private void startHatch() {
        hatching = true;
        say("The egg is cracking...");
        if (!hasKey()) {
            finishHatch(null);
            return;
        }
        String sys = "You invent a unique virtual pet for a Tamagotchi-style game. Reply with ONLY a JSON object, "
                + "no other text, with these string fields: \"name\" (a cute invented name, max 8 letters), "
                + "\"personality\" (2 to 5 words), \"likes\" (favourite food, exactly one of: Rice ball, Apple, Cake, Burger, Fish), "
                + "\"quirk\" (one short, funny sentence about a habit), \"catchphrase\" (max 4 words, its first words).";
        List<String[]> h = new ArrayList<String[]>();
        h.add(new String[]{"user", "Create a new pet. Temperament seed: " + pick(SEEDS) + ". Be original."});
        ask(sys, h, new AiCallback() {
            @Override
            public void done(String text, String error) {
                if (error != null) toast("AI unavailable, hatching with a default personality");
                finishHatch(text);
            }
        });
    }

    private void finishHatch(String json) {
        String name = null, personality = null, likes = null, quirk = null, catchphrase = null;
        if (json != null) {
            try {
                int a = json.indexOf('{');
                int z = json.lastIndexOf('}');
                JSONObject o = new JSONObject(json.substring(a, z + 1));
                name = o.optString("name", "").trim();
                personality = o.optString("personality", "").trim();
                likes = o.optString("likes", "").trim();
                quirk = o.optString("quirk", "").trim();
                catchphrase = o.optString("catchphrase", "").trim();
            } catch (Exception ignored) {
            }
        }
        if (name == null || name.length() == 0) name = pick(FALLBACK_NAMES);
        if (name.length() > 10) name = name.substring(0, 10);
        if (personality == null || personality.length() == 0) personality = pick(FALLBACK_PERSONALITY);
        if (quirk == null || quirk.length() == 0) quirk = pick(FALLBACK_QUIRK);
        if (catchphrase == null || catchphrase.length() == 0) catchphrase = pick(FALLBACK_CATCH);
        String matched = null;
        if (likes != null) {
            for (String f : PetState.FOODS) {
                if (likes.toLowerCase().contains(f.toLowerCase())) matched = f;
            }
        }
        if (matched == null) matched = pick(PetState.FOODS);

        long now = System.currentTimeMillis();
        pet.name = name;
        pet.personality = personality;
        pet.likes = matched;
        pet.quirk = quirk;
        pet.catchphrase = catchphrase;
        pet.hue = Math.abs((name + pet.bornAt).hashCode()) % 360;
        pet.hatched = true;
        pet.hatchedAt = now;
        pet.lastTick = now;
        pet.addEvent(now, "hatched from its egg");
        hatching = false;
        lastStage = PetState.BABY;
        petView.sparkleUntil = now + 3000L;
        petView.heartsUntil = now + 2500L;
        petView.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS);
        pet.save(prefs);
        say(catchphrase + "  (I'm " + name + ", " + personality + ".)");
        toast(name + " hatched!");
    }

    private String petSystem(long now) {
        int st = pet.stage(now);
        String style;
        if (st == PetState.BABY) style = "You are a baby: use very simple baby-talk and short words.";
        else if (st == PetState.CHILD) style = "You are a child: curious, enthusiastic, simple words.";
        else if (st == PetState.TEEN) style = "You are a teenager: a little moody and sarcastic, but secretly affectionate.";
        else style = "You are an adult: articulate, warm and a bit wise.";
        return "You are " + pet.name + ", a virtual pet in a Tamagotchi-style app, talking to your owner. "
                + "Personality: " + pet.personality + ". Quirk: " + pet.quirk + ". Catchphrase: \""
                + pet.catchphrase + "\" (use it only now and then). Favourite food: " + pet.likes + ". "
                + "Life stage: " + PetState.STAGE_NAMES[st].toLowerCase() + ", age " + pet.ageText(now) + ". "
                + style + " Your current state: " + pet.describe(now) + ". "
                + "Stay fully in character as the pet. Let your current needs colour what you say "
                + "(mention being hungry, sleepy, dirty or unwell when relevant) but never list numbers. "
                + "Reply with at most 25 words, no stage directions, no markdown, at most one emoji.";
    }

    private boolean ready() {
        if (!pet.exists || !pet.hatched) {
            toast("Wait for the egg to hatch");
            return false;
        }
        if (pet.dead) {
            showDeathDialog();
            return false;
        }
        return true;
    }

    private void onPetTapped() {
        long now = System.currentTimeMillis();
        if (!pet.hatched) {
            toast("Shh, it's almost ready to hatch");
            return;
        }
        if (pet.dead) {
            showDeathDialog();
            return;
        }
        petView.heartsUntil = now + 1500L;
        if (pet.sleeping) {
            say("Zzz...");
            return;
        }
        if (now - lastThought < 12000L || busyChat) {
            say(offlineThought());
            return;
        }
        lastThought = now;
        if (!hasKey()) {
            say(offlineThought());
            return;
        }
        busyChat = true;
        List<String[]> h = new ArrayList<String[]>();
        h.add(new String[]{"user", "(Your owner just tapped you. Say one short thing about how you feel right now.)"});
        ask(petSystem(now), h, new AiCallback() {
            @Override
            public void done(String text, String error) {
                busyChat = false;
                say(error != null ? offlineThought() : text);
            }
        });
    }

    private String offlineThought() {
        int mood = pet.mood();
        if (pet.full < 30f) return "My tummy is rumbling...";
        if (pet.sick) return "I don't feel so good...";
        if (pet.poops >= 2) return "It's getting messy in here!";
        if (pet.energy < 25f) return "So sleepy...";
        if (pet.happy < 30f) return "Play with me?";
        if (mood == PetState.MOOD_HAPPY) return pet.catchphrase.length() > 0 ? pet.catchphrase : "Happy!";
        return "Hi!";
    }

    private void talkAction() {
        if (!ready()) return;
        if (!hasKey()) {
            say("I'd love to chat, but I need an AI key first. Tap the AI button.");
            showSettings();
            return;
        }
        if (pet.sleeping) {
            say("Zzz...");
            return;
        }
        final EditText input = new EditText(this);
        input.setHint("Say something to " + pet.name);
        input.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_CAP_SENTENCES);
        input.setSingleLine(true);
        input.setTextColor(Color.WHITE);
        input.setHintTextColor(0xFF8F86B8);
        dialog().setTitle("Talk to " + pet.name).setView(input)
                .setPositiveButton("Send", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface d, int w) {
                        String t = input.getText().toString().trim();
                        if (t.length() > 0) sendChat(t);
                    }
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void sendChat(final String text) {
        if (busyChat) {
            toast("One moment...");
            return;
        }
        busyChat = true;
        chat.add(new String[]{"user", text});
        say("...");
        long now = System.currentTimeMillis();
        ask(petSystem(now), chat, new AiCallback() {
            @Override
            public void done(String reply, String error) {
                busyChat = false;
                if (error != null) {
                    chat.remove(chat.size() - 1);
                    say(error);
                } else {
                    chat.add(new String[]{"assistant", reply});
                    while (chat.size() > 12) {
                        chat.remove(0);
                        chat.remove(0);
                    }
                    say(reply);
                    petView.heartsUntil = System.currentTimeMillis() + 1500L;
                }
            }
        });
    }

    // -------------------------------------------------------------- actions

    private void feedAction() {
        if (!ready()) return;
        String[] items = new String[PetState.FOODS.length];
        for (int i = 0; i < items.length; i++) {
            items[i] = PetView.emoji(PetState.FOOD_EMOJI[i]) + "  " + PetState.FOODS[i];
        }
        dialog().setTitle("Feed " + pet.name)
                .setItems(items, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface d, int which) {
                        long now = System.currentTimeMillis();
                        int r = pet.feed(which, now);
                        if (r == -2) {
                            say("Zzz...");
                        } else if (r == -1) {
                            say("I'm full!");
                        } else {
                            petView.floatEmoji = PetState.FOOD_EMOJI[which];
                            petView.floatUntil = now + 2500L;
                            if (r == 1) {
                                petView.heartsUntil = now + 3000L;
                                say("My favourite!! " + PetView.emoji(0x1F60B));
                            } else {
                                say("Yum!");
                            }
                            pet.save(prefs);
                        }
                    }
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void playAction() {
        if (!ready()) return;
        if (pet.sleeping) {
            say("Zzz...");
            return;
        }
        if (pet.energy < 15f) {
            say("Too tired to play...");
            return;
        }
        playRound(0, 0);
    }

    private void playRound(final int round, final int wins) {
        final String n = pet.name;
        dialog().setTitle("Play with " + n + " (" + (round + 1) + "/3)")
                .setMessage("Which way will " + n + " jump?")
                .setPositiveButton("Right", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface d, int w) {
                        resolveGuess(round, wins, true);
                    }
                })
                .setNegativeButton("Left", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface d, int w) {
                        resolveGuess(round, wins, false);
                    }
                })
                .setNeutralButton("Quit", null)
                .show();
    }

    private void resolveGuess(int round, int wins, boolean guessedRight) {
        boolean jumpedRight = rnd.nextBoolean();
        boolean win = guessedRight == jumpedRight;
        toast((win ? "Correct! " : "Nope! ") + pet.name + " jumped " + (jumpedRight ? "right" : "left"));
        int w2 = wins + (win ? 1 : 0);
        if (round >= 2) {
            long now = System.currentTimeMillis();
            pet.play(w2, now);
            if (w2 >= 2) {
                petView.heartsUntil = now + 3000L;
                say("That was fun!");
            } else {
                say("Again again!");
            }
            pet.save(prefs);
        } else {
            playRound(round + 1, w2);
        }
    }

    private void cleanAction() {
        if (!ready()) return;
        if (pet.poops == 0 && pet.hygiene() > 90f) {
            toast("Already spotless");
            return;
        }
        pet.clean(System.currentTimeMillis());
        petView.sparkleUntil = System.currentTimeMillis() + 2500L;
        say("So fresh!");
        pet.save(prefs);
    }

    private void sleepAction() {
        if (!ready()) return;
        int r = pet.toggleSleep(System.currentTimeMillis());
        if (r == 0) say("Zzz...");
        else if (r == 1) say("Mmm... morning?");
        else say("I'm not sleepy yet.");
        pet.save(prefs);
    }

    private void healAction() {
        if (!ready()) return;
        if (pet.medicine(System.currentTimeMillis())) {
            petView.heartsUntil = System.currentTimeMillis() + 2500L;
            say("I feel better!");
            pet.save(prefs);
        } else {
            say("I'm not sick!");
        }
    }

    private void showDeathDialog() {
        long now = System.currentTimeMillis();
        dialog().setTitle(pet.name + " has passed away")
                .setMessage(pet.name + " lived for " + pet.ageText(now) + ". Ready to hatch a new egg?")
                .setCancelable(true)
                .setPositiveButton("New egg", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface d, int w) {
                        pet.startNewEgg(System.currentTimeMillis());
                        lastStage = -1;
                        deathShown = false;
                        hatching = false;
                        chat.clear();
                        say("A new egg!");
                        pet.save(prefs);
                    }
                })
                .setNegativeButton("Not yet", null)
                .show();
    }

    // ---------------------------------------------------------------- diary

    private void showDiary() {
        if (!pet.hatched) {
            toast("Wait for the egg to hatch");
            return;
        }
        StringBuilder sb = new StringBuilder();
        if (pet.diary.length() > 0) {
            sb.append(new SimpleDateFormat("EEE d MMM, HH:mm", Locale.getDefault()).format(new Date(pet.diaryAt)))
                    .append("\n\n").append(pet.diary);
        } else {
            sb.append("No entry yet. Ask ").append(pet.name).append(" to write one about their day.");
        }
        if (!pet.memorial.isEmpty()) {
            sb.append("\n\nPast pets: ");
            for (int i = 0; i < pet.memorial.size(); i++) {
                if (i > 0) sb.append(", ");
                sb.append(pet.memorial.get(i));
            }
        }
        final TextView tv = new TextView(this);
        tv.setText(sb.toString());
        tv.setTextColor(Color.WHITE);
        tv.setTextSize(15);
        tv.setPadding(dp(20), dp(8), dp(20), dp(8));
        tv.setTextIsSelectable(true);
        ScrollView sv = new ScrollView(this);
        sv.addView(tv);
        dialog().setTitle(pet.name + "'s diary").setView(sv)
                .setPositiveButton("Write entry", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface d, int w) {
                        writeDiary();
                    }
                })
                .setNegativeButton("Close", null)
                .show();
    }

    private void writeDiary() {
        if (!hasKey()) {
            toast("Add your API key first");
            showSettings();
            return;
        }
        if (busyChat) {
            toast("One moment...");
            return;
        }
        busyChat = true;
        say("Writing in my diary...");
        final long now = System.currentTimeMillis();
        SimpleDateFormat tf = new SimpleDateFormat("EEE HH:mm", Locale.getDefault());
        StringBuilder ev = new StringBuilder();
        for (String e : pet.events) {
            int bar = e.indexOf('|');
            if (bar < 0) continue;
            try {
                long t = Long.parseLong(e.substring(0, bar));
                ev.append("- ").append(tf.format(new Date(t))).append(": ").append(e.substring(bar + 1)).append('\n');
            } catch (NumberFormatException ignored) {
            }
        }
        String sys = "You are " + pet.name + ", a virtual pet with this personality: " + pet.personality
                + ". Quirk: " + pet.quirk + ". Write a short private diary entry (3 to 5 sentences) in the first person, "
                + "in character, about your recent days with your owner. Base it ONLY on the events listed and how you "
                + "feel now: " + pet.describe(now) + ". Do not invent events. No markdown, no title.";
        List<String[]> h = new ArrayList<String[]>();
        h.add(new String[]{"user", "Recent events (oldest first):\n" + (ev.length() > 0 ? ev.toString() : "- nothing much yet")});
        ask(sys, h, new AiCallback() {
            @Override
            public void done(String text, String error) {
                busyChat = false;
                if (error != null) {
                    say(error);
                    return;
                }
                pet.diary = text;
                pet.diaryAt = now;
                pet.save(prefs);
                say("Done! Have a look at my diary.");
                showDiary();
            }
        });
    }

    // ------------------------------------------------------------- settings

    private void showSettings() {
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(dp(20), dp(8), dp(20), dp(0));

        TextView info = new TextView(this);
        info.setText("The AI gives your pet its personality, lets you chat, and writes its diary. "
                + "Paste a Claude API key from console.anthropic.com. It is stored only on this phone and sent only to api.anthropic.com. "
                + "Without a key the pet still works, it just can't talk.");
        info.setTextColor(0xFFD0C9F0);
        info.setTextSize(13);
        box.addView(info);

        final EditText key = new EditText(this);
        key.setHint(hasKey() ? "Key saved (leave blank to keep)" : "API key (sk-ant-...)");
        key.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
        key.setSingleLine(true);
        key.setTextColor(Color.WHITE);
        key.setHintTextColor(0xFF8F86B8);
        box.addView(key);

        final EditText model = new EditText(this);
        model.setText(prefs.getString("model", DEFAULT_MODEL));
        model.setHint("Model");
        model.setSingleLine(true);
        model.setTextColor(Color.WHITE);
        model.setHintTextColor(0xFF8F86B8);
        box.addView(model);

        ScrollView sv = new ScrollView(this);
        sv.addView(box);
        dialog().setTitle("AI settings").setView(sv)
                .setPositiveButton("Save", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface d, int w) {
                        SharedPreferences.Editor ed = prefs.edit();
                        String k = key.getText().toString().trim();
                        if (k.length() > 0) ed.putString("key", k);
                        String m = model.getText().toString().trim();
                        ed.putString("model", m.length() > 0 ? m : DEFAULT_MODEL);
                        ed.apply();
                        toast("Saved");
                    }
                })
                .setNegativeButton("Cancel", null)
                .setNeutralButton("Remove key", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface d, int w) {
                        prefs.edit().remove("key").apply();
                        toast("Key removed");
                    }
                })
                .show();
    }
}
