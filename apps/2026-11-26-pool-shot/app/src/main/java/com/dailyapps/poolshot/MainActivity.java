package com.dailyapps.poolshot;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.ClipData;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.graphics.Matrix;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.media.ExifInterface;
import android.net.Uri;
import android.os.Bundle;
import android.provider.MediaStore;
import android.view.Gravity;
import android.view.HapticFeedbackConstants;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

public class MainActivity extends Activity {
    private static final int REQ_CAMERA = 11;
    private static final int REQ_PICK = 12;
    private static final int RECT_W = 1200, RECT_H = 600;

    private static final int BG = Color.rgb(14, 22, 18);
    private static final int PANEL = Color.rgb(26, 38, 32);
    private static final int BTN = Color.rgb(40, 58, 49);
    private static final int ACCENT = Color.rgb(255, 196, 64);
    private static final int DIM = Color.rgb(150, 170, 160);

    private static final String[] TABLE_NAMES = {"7 ft (bar box)", "8 ft", "9 ft (pro)"};
    private static final float[] TABLE_WIDTH_IN = {39f, 44f, 50f};
    private static final String[] SKILL_NAMES = {"Beginner", "Casual", "Club player", "Strong"};
    private static final double[] SKILL_SIGMA = {0.0055, 0.0042, 0.0030, 0.0020};

    private int screen = 0; // 0 home, 1 corners, 2 table
    private Bitmap photo;
    private Bitmap rectified;
    private final List<Ball> balls = new ArrayList<>();
    private final Shots.Config cfg = new Shots.Config();
    private List<Shots.Shot> shots = new ArrayList<>();
    private int shotIdx = 0;
    private int tableIdx = 2, skillIdx = 1, target = Shots.ANY;
    private boolean banks = true, showPhoto = true;
    private boolean busy = false;

    private CornerView cornerView;
    private TableView tableView;
    private TextView shotTitle, shotDetail, shotCounter;

    private int dp(float v) {
        return (int) (v * getResources().getDisplayMetrics().density + 0.5f);
    }

    private GradientDrawable round(int color, int stroke) {
        GradientDrawable g = new GradientDrawable();
        g.setColor(color);
        g.setCornerRadius(dp(12));
        if (stroke != 0) g.setStroke(dp(2), stroke);
        return g;
    }

    private Button button(String label) {
        Button b = new Button(this);
        b.setText(label);
        b.setAllCaps(false);
        b.setTextColor(Color.WHITE);
        b.setTextSize(14);
        b.setMinWidth(0);
        b.setMinimumWidth(0);
        b.setPadding(dp(6), 0, dp(6), 0);
        b.setBackground(round(BTN, 0));
        return b;
    }

    private LinearLayout.LayoutParams weighted(int hDp) {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, dp(hDp), 1);
        lp.setMargins(dp(3), dp(3), dp(3), dp(3));
        return lp;
    }

    private TextView label(String s, int sp, int color, boolean bold) {
        TextView t = new TextView(this);
        t.setText(s);
        t.setTextSize(sp);
        t.setTextColor(color);
        if (bold) t.setTypeface(Typeface.DEFAULT_BOLD);
        return t;
    }

    private void toast(String s) {
        Toast.makeText(this, s, Toast.LENGTH_LONG).show();
    }

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        getWindow().setStatusBarColor(BG);
        loadSettings();
        showHome();
    }

    // ------------------------------------------------------------------ home

    private void showHome() {
        screen = 0;
        ScrollView sv = new ScrollView(this);
        sv.setBackgroundColor(BG);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(20), dp(28), dp(20), dp(24));
        sv.addView(root);

        root.addView(label("Pool Shot", 32, Color.WHITE, true));
        TextView sub = label("Snap the table, and I'll find the balls and pick the shot you're most likely to make, "
                + "with the aim point, cut angle, pace and where the cue ball goes next.", 15, DIM, false);
        sub.setPadding(0, dp(6), 0, dp(20));
        root.addView(sub);

        Button cam = big("📷  Take a photo of the table", ACCENT, Color.rgb(20, 20, 20));
        cam.setOnClickListener(v -> takePhoto());
        root.addView(cam);
        Button pick = big("🖼  Choose a photo", BTN, Color.WHITE);
        pick.setOnClickListener(v -> pickPhoto());
        root.addView(pick);
        if (hasSaved()) {
            Button resume = big("↺  Resume last table", BTN, Color.WHITE);
            resume.setOnClickListener(v -> {
                loadLayout();
                showTable();
            });
            root.addView(resume);
        }
        Button blank = big("◯  Empty table (place balls by hand)", BTN, Color.WHITE);
        blank.setOnClickListener(v -> {
            balls.clear();
            balls.add(new Ball(0, 1.5f, 0.5f));
            rectified = null;
            showTable();
            toast("Tap the table to add balls, drag to move them");
        });
        root.addView(blank);
        Button set = big("⚙  Settings: " + TABLE_NAMES[tableIdx] + " · " + SKILL_NAMES[skillIdx], PANEL, DIM);
        set.setOnClickListener(v -> settingsDialog(this::showHome));
        root.addView(set);

        TextView tips = label("Tips for a good read\n"
                + "• Stand at the end or side and hold the phone high so the whole table is in frame.\n"
                + "• Even lighting helps. Avoid strong glare on the cloth.\n"
                + "• You can fix anything after: drag balls, tap to add, tap a ball to renumber it.\n"
                + "• A ball the same colour as the cloth (like green on green) may be missed, so add it by hand.",
                13, DIM, false);
        tips.setPadding(dp(4), dp(22), dp(4), 0);
        tips.setLineSpacing(dp(3), 1f);
        root.addView(tips);
        setContentView(sv);
    }

    private Button big(String s, int bg, int fg) {
        Button b = button(s);
        b.setTextSize(16);
        b.setTextColor(fg);
        b.setGravity(Gravity.CENTER_VERTICAL | Gravity.START);
        b.setPadding(dp(18), 0, dp(12), 0);
        b.setBackground(round(bg, 0));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, dp(58));
        lp.bottomMargin = dp(10);
        b.setLayoutParams(lp);
        return b;
    }

    private void takePhoto() {
        try {
            File f = PhotoProvider.captureFile(this);
            if (f.exists()) f.delete();
            Uri uri = PhotoProvider.captureUri();
            Intent i = new Intent(MediaStore.ACTION_IMAGE_CAPTURE);
            i.putExtra(MediaStore.EXTRA_OUTPUT, uri);
            i.setClipData(ClipData.newRawUri("photo", uri));
            i.addFlags(Intent.FLAG_GRANT_WRITE_URI_PERMISSION | Intent.FLAG_GRANT_READ_URI_PERMISSION);
            startActivityForResult(i, REQ_CAMERA);
        } catch (Exception e) {
            toast("No camera app found. Choose a photo instead.");
        }
    }

    private void pickPhoto() {
        try {
            Intent i = new Intent(Intent.ACTION_GET_CONTENT);
            i.setType("image/*");
            i.addCategory(Intent.CATEGORY_OPENABLE);
            startActivityForResult(Intent.createChooser(i, "Choose a table photo"), REQ_PICK);
        } catch (Exception e) {
            toast("No photo picker available");
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (resultCode != RESULT_OK) return;
        Uri uri = null;
        if (requestCode == REQ_CAMERA) {
            File f = PhotoProvider.captureFile(this);
            if (f.exists() && f.length() > 0) uri = Uri.fromFile(f);
            else if (data != null && data.getExtras() != null && data.getExtras().get("data") instanceof Bitmap) {
                photo = (Bitmap) data.getExtras().get("data");
                startCorners();
                return;
            }
        } else if (requestCode == REQ_PICK && data != null) {
            uri = data.getData();
        }
        if (uri == null) {
            toast("Couldn't get the photo");
            return;
        }
        try {
            photo = loadBitmap(uri);
            startCorners();
        } catch (Exception e) {
            toast("Couldn't open that image");
        }
    }

    private Bitmap loadBitmap(Uri uri) throws IOException {
        BitmapFactory.Options o = new BitmapFactory.Options();
        o.inJustDecodeBounds = true;
        try (InputStream in = getContentResolver().openInputStream(uri)) {
            BitmapFactory.decodeStream(in, null, o);
        }
        int sample = 1;
        while (Math.max(o.outWidth, o.outHeight) / sample > 2400) sample *= 2;
        BitmapFactory.Options o2 = new BitmapFactory.Options();
        o2.inSampleSize = sample;
        Bitmap bmp;
        try (InputStream in = getContentResolver().openInputStream(uri)) {
            bmp = BitmapFactory.decodeStream(in, null, o2);
        }
        if (bmp == null) throw new IOException("decode failed");
        int rot = 0;
        try (InputStream in = getContentResolver().openInputStream(uri)) {
            if (in != null) {
                ExifInterface ex = new ExifInterface(in);
                int or = ex.getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL);
                if (or == ExifInterface.ORIENTATION_ROTATE_90) rot = 90;
                else if (or == ExifInterface.ORIENTATION_ROTATE_180) rot = 180;
                else if (or == ExifInterface.ORIENTATION_ROTATE_270) rot = 270;
            }
        } catch (Exception ignored) {
        }
        float sc = 1600f / Math.max(bmp.getWidth(), bmp.getHeight());
        if (sc > 1f) sc = 1f;
        if (rot != 0 || sc < 1f) {
            Matrix m = new Matrix();
            m.postScale(sc, sc);
            m.postRotate(rot);
            Bitmap r = Bitmap.createBitmap(bmp, 0, 0, bmp.getWidth(), bmp.getHeight(), m, true);
            if (r != bmp) bmp.recycle();
            bmp = r;
        }
        return bmp;
    }

    // --------------------------------------------------------------- corners

    private void startCorners() {
        if (photo == null) return;
        float[] c = Vision.autoCorners(photo);
        showCorners(c);
    }

    private void showCorners(float[] corners) {
        screen = 1;
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(BG);
        TextView t = label("Line up the playing surface", 18, Color.WHITE, true);
        t.setPadding(dp(14), dp(14), dp(14), 0);
        root.addView(t);
        TextView h = label("Drag each handle to where the cushion noses meet in a corner pocket. "
                + "The thick gold edges are the long rails, and their dots should sit on the side pockets. "
                + "If they're on the short rails, tap Rotate.", 13, DIM, false);
        h.setPadding(dp(14), dp(4), dp(14), dp(4));
        root.addView(h);
        cornerView = new CornerView(this, photo, corners);
        root.addView(cornerView, new LinearLayout.LayoutParams(-1, 0, 1));
        LinearLayout row = new LinearLayout(this);
        row.setPadding(dp(8), dp(4), dp(8), dp(10));
        Button back = button("Back");
        back.setOnClickListener(v -> showHome());
        Button rot = button("↻ Rotate");
        rot.setOnClickListener(v -> cornerView.setCorners(Vision.rotate(cornerView.getCorners())));
        Button auto = button("Auto");
        auto.setOnClickListener(v -> cornerView.setCorners(Vision.autoCorners(photo)));
        Button go = button("Find shots ▶");
        go.setTypeface(Typeface.DEFAULT_BOLD);
        go.setTextColor(Color.rgb(20, 20, 20));
        go.setBackground(round(ACCENT, 0));
        go.setOnClickListener(v -> analyse());
        row.addView(back, weighted(50));
        row.addView(rot, weighted(50));
        row.addView(auto, weighted(50));
        LinearLayout.LayoutParams gl = weighted(50);
        gl.weight = 1.6f;
        row.addView(go, gl);
        root.addView(row);
        setContentView(root);
    }

    private void analyse() {
        if (busy) return;
        busy = true;
        final float[] corners = cornerView.getCorners();
        final float r = cfg.r;
        toast("Analysing the table…");
        new Thread(() -> {
            Bitmap rect = null;
            List<Ball> found = null;
            try {
                rect = Vision.warp(photo, corners, RECT_W, RECT_H);
                found = Vision.detectBalls(rect, r);
            } catch (Throwable e) {
                found = null;
            }
            final Bitmap fr = rect;
            final List<Ball> ff = found;
            runOnUiThread(() -> {
                busy = false;
                if (fr == null || ff == null) {
                    toast("Analysis failed. Try adjusting the corners.");
                    return;
                }
                rectified = fr;
                balls.clear();
                balls.addAll(ff);
                showTable();
                boolean hasCue = Shots.cue(balls) != null;
                toast("Found " + balls.size() + " ball" + (balls.size() == 1 ? "" : "s")
                        + (hasCue ? "" : ". No cue ball spotted, so tap a ball to set it")
                        + ". Fix any mistakes by tapping or dragging.");
            });
        }).start();
    }

    // ----------------------------------------------------------------- table

    private void showTable() {
        screen = 2;
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(BG);

        LinearLayout bar = new LinearLayout(this);
        bar.setPadding(dp(6), dp(6), dp(6), 0);
        Button back = button("←");
        back.setTextSize(18);
        back.setOnClickListener(v -> {
            saveLayout();
            savePhoto();
            showHome();
        });
        final Button tgt = button("🎯 " + Shots.TARGET_NAMES[target]);
        tgt.setOnClickListener(v -> new AlertDialog.Builder(this)
                .setTitle("Which balls can you shoot at?")
                .setItems(Shots.TARGET_NAMES, (d, w) -> {
                    target = w;
                    tgt.setText("🎯 " + Shots.TARGET_NAMES[target]);
                    saveSettings();
                    recompute();
                })
                .show());
        bar.addView(back, new LinearLayout.LayoutParams(dp(52), dp(44)));
        LinearLayout.LayoutParams tl = new LinearLayout.LayoutParams(0, dp(44), 1.6f);
        tl.leftMargin = dp(6);
        bar.addView(tgt, tl);
        if (rectified != null) {
            final Button ph = button(showPhoto ? "Photo: on" : "Photo: off");
            ph.setOnClickListener(v -> {
                showPhoto = !showPhoto;
                ph.setText(showPhoto ? "Photo: on" : "Photo: off");
                tableView.setShowPhoto(showPhoto);
                saveSettings();
            });
            LinearLayout.LayoutParams pl = new LinearLayout.LayoutParams(0, dp(44), 1f);
            pl.leftMargin = dp(6);
            bar.addView(ph, pl);
        }
        Button set = button("⚙");
        set.setTextSize(18);
        set.setOnClickListener(v -> settingsDialog(() -> {
            tableView.setData(balls, rectified, cfg.r);
            recompute();
        }));
        LinearLayout.LayoutParams sl = new LinearLayout.LayoutParams(dp(52), dp(44));
        sl.leftMargin = dp(6);
        bar.addView(set, sl);
        root.addView(bar);

        tableView = new TableView(this);
        tableView.setData(balls, rectified, cfg.r);
        tableView.setShowPhoto(showPhoto);
        tableView.setListener(new TableView.Listener() {
            @Override
            public void onBallTap(Ball b) {
                tableView.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY);
                ballMenu(b);
            }

            @Override
            public void onEmptyTap(float x, float y) {
                addBallDialog(x, y);
            }

            @Override
            public void onBallMoved() {
                recompute();
            }
        });
        LinearLayout.LayoutParams tvl = new LinearLayout.LayoutParams(-1, 0, 1);
        tvl.topMargin = dp(4);
        root.addView(tableView, tvl);

        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setBackground(round(PANEL, 0));
        card.setPadding(dp(14), dp(10), dp(14), dp(8));
        shotTitle = label("", 17, Color.WHITE, true);
        shotDetail = label("", 13, Color.rgb(205, 220, 212), false);
        shotDetail.setLineSpacing(dp(2), 1f);
        card.addView(shotTitle);
        card.addView(shotDetail);
        LinearLayout nav = new LinearLayout(this);
        nav.setGravity(Gravity.CENTER_VERTICAL);
        Button prev = button("◀  Easier");
        prev.setOnClickListener(v -> stepShot(-1));
        shotCounter = label("", 13, DIM, false);
        shotCounter.setGravity(Gravity.CENTER);
        Button next = button("Next  ▶");
        next.setOnClickListener(v -> stepShot(1));
        nav.addView(prev, weighted(42));
        nav.addView(shotCounter, weighted(42));
        nav.addView(next, weighted(42));
        card.addView(nav);
        LinearLayout.LayoutParams cl = new LinearLayout.LayoutParams(-1, -2);
        cl.setMargins(dp(8), dp(4), dp(8), dp(8));
        root.addView(card, cl);
        setContentView(root);
        recompute();
    }

    private void stepShot(int d) {
        if (shots.isEmpty()) return;
        shotIdx = (shotIdx + d + shots.size()) % shots.size();
        updateShotInfo();
    }

    private void recompute() {
        applyConfig();
        shots = Shots.find(balls, cfg);
        // keep the list readable: drop near-duplicates beyond the top 10
        if (shots.size() > 10) shots = new ArrayList<>(shots.subList(0, 10));
        shotIdx = 0;
        updateShotInfo();
        saveLayout();
    }

    private void updateShotInfo() {
        if (tableView == null) return;
        Ball cue = Shots.cue(balls);
        if (cue == null) {
            tableView.setShot(null);
            shotTitle.setText("No cue ball");
            shotDetail.setText("Tap the cue ball and choose “Make this the cue ball”, or tap empty cloth to add one.");
            shotCounter.setText("");
            return;
        }
        if (shots.isEmpty()) {
            tableView.setShot(null);
            shotTitle.setText("No clear pot");
            shotDetail.setText("Every pocket is blocked or the cuts are too thin for the balls you can hit. "
                    + "Consider playing safe: roll the cue ball behind a blocker or leave your opponent long."
                    + (banks ? "" : "\nTip: turn on bank shots in settings."));
            shotCounter.setText("0 shots");
            return;
        }
        Shots.Shot s = shots.get(shotIdx);
        tableView.setShot(s);
        int pct = (int) Math.round(s.prob * 100);
        String title = (shotIdx == 0 ? "Best: " : "Option " + (shotIdx + 1) + ": ")
                + s.obj.label() + " → " + Shots.POCKET_NAMES[s.pocket] + "  ·  " + pct + "%";
        shotTitle.setText(title);
        StringBuilder sb = new StringBuilder();
        if (s.rail >= 0) sb.append("One-rail bank · ");
        double deg = Math.toDegrees(s.cut);
        if (deg < 4) sb.append("Straight in");
        else sb.append("Cut ").append(Math.round(deg)).append("° ").append(s.cutRight ? "to the right" : "to the left");
        sb.append(" · ").append(Shots.fraction(s.cut)).append(" · ").append(Shots.pace(s));
        sb.append('\n');
        if (deg >= 4) {
            sb.append("Aim the cue ball's centre at the dashed ghost ball, hitting the ")
                    .append(s.cutRight ? "left" : "right").append(" side of the ").append(s.obj.label()).append(".");
        } else {
            sb.append("Hit the ").append(s.obj.label()).append(" dead centre. Use stun to stop the cue ball.");
        }
        sb.append('\n');
        if (s.scratch) {
            sb.append("⚠ With a plain stun hit the cue ball heads for a pocket. Use draw or follow to change its path.");
        } else {
            int np = (int) Math.round(s.next * 100);
            sb.append("Cue ball rolls to the blue circle");
            if (np > 0) sb.append(" and leaves about a ").append(np).append("% next shot.");
            else sb.append(", but leaves no easy next shot.");
        }
        shotDetail.setText(sb.toString());
        shotCounter.setText((shotIdx + 1) + " of " + shots.size());
    }

    private void ballMenu(final Ball b) {
        String[] opts = {"Make this the cue ball", "Change number…", "Delete"};
        new AlertDialog.Builder(this)
                .setTitle(b.num == 0 ? "Cue ball" : Ball.NAMES[b.num])
                .setItems(opts, (d, w) -> {
                    if (w == 0) {
                        for (Ball o : balls) {
                            if (o.num == 0 && o != b) o.num = guessFreeNumber();
                        }
                        b.num = 0;
                    } else if (w == 1) {
                        new AlertDialog.Builder(this)
                                .setTitle("Which ball is it?")
                                .setItems(Ball.NAMES, (d2, w2) -> {
                                    if (w2 == 0) {
                                        for (Ball o : balls) if (o.num == 0 && o != b) o.num = guessFreeNumber();
                                    }
                                    b.num = w2;
                                    tableView.invalidate();
                                    recompute();
                                })
                                .show();
                        return;
                    } else {
                        balls.remove(b);
                    }
                    tableView.invalidate();
                    recompute();
                })
                .show();
    }

    private int guessFreeNumber() {
        boolean[] used = new boolean[16];
        for (Ball o : balls) used[o.num] = true;
        for (int n = 1; n < 16; n++) if (!used[n]) return n;
        return 1;
    }

    private void addBallDialog(final float x, final float y) {
        if (balls.size() >= 16) {
            toast("The table already has 16 balls");
            return;
        }
        new AlertDialog.Builder(this)
                .setTitle("Add a ball here")
                .setItems(Ball.NAMES, (d, w) -> {
                    if (w == 0) {
                        Ball cue = Shots.cue(balls);
                        if (cue != null) {
                            cue.x = x;
                            cue.y = y;
                        } else {
                            balls.add(new Ball(0, x, y));
                        }
                    } else {
                        balls.add(new Ball(w, x, y));
                    }
                    tableView.invalidate();
                    recompute();
                })
                .show();
    }

    // -------------------------------------------------------------- settings

    private void applyConfig() {
        float wIn = TABLE_WIDTH_IN[tableIdx];
        cfg.r = 1.125f / wIn;
        cfg.cornerSlop = (tableIdx == 0 ? 1.55f : 1.4f) / wIn;
        cfg.sideSlop = (tableIdx == 0 ? 1.75f : 1.6f) / wIn;
        cfg.sigma = SKILL_SIGMA[skillIdx];
        cfg.banks = banks;
        cfg.target = target;
    }

    private void settingsDialog(final Runnable after) {
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(dp(20), dp(10), dp(20), 0);
        final Button tb = button("");
        final Button sb = button("");
        final Button bb = button("");
        Runnable refresh = () -> {
            tb.setText("Table: " + TABLE_NAMES[tableIdx]);
            sb.setText("Your level: " + SKILL_NAMES[skillIdx]);
            bb.setText(banks ? "Bank shots: included" : "Bank shots: off");
        };
        refresh.run();
        tb.setOnClickListener(v -> {
            tableIdx = (tableIdx + 1) % TABLE_NAMES.length;
            refresh.run();
        });
        sb.setOnClickListener(v -> {
            skillIdx = (skillIdx + 1) % SKILL_NAMES.length;
            refresh.run();
        });
        bb.setOnClickListener(v -> {
            banks = !banks;
            refresh.run();
        });
        for (Button b : new Button[]{tb, sb, bb}) {
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, dp(50));
            lp.bottomMargin = dp(8);
            box.addView(b, lp);
        }
        TextView note = label("Table size sets the ball and pocket size used in the maths. "
                + "Your level changes how likely each shot is to drop.", 12, DIM, false);
        box.addView(note);
        new AlertDialog.Builder(this)
                .setTitle("Settings")
                .setView(box)
                .setPositiveButton("Done", (d, w) -> {
                    saveSettings();
                    applyConfig();
                    if (after != null) after.run();
                })
                .setOnCancelListener(d -> {
                    saveSettings();
                    applyConfig();
                    if (after != null) after.run();
                })
                .show();
    }

    private void loadSettings() {
        SharedPreferences p = getSharedPreferences("settings", MODE_PRIVATE);
        tableIdx = clampIdx(p.getInt("table", 2), TABLE_NAMES.length);
        skillIdx = clampIdx(p.getInt("skill", 1), SKILL_NAMES.length);
        target = clampIdx(p.getInt("target", Shots.ANY), Shots.TARGET_NAMES.length);
        banks = p.getBoolean("banks", true);
        showPhoto = p.getBoolean("photo", true);
        applyConfig();
    }

    private static int clampIdx(int v, int n) {
        return v < 0 || v >= n ? 0 : v;
    }

    private void saveSettings() {
        getSharedPreferences("settings", MODE_PRIVATE).edit()
                .putInt("table", tableIdx)
                .putInt("skill", skillIdx)
                .putInt("target", target)
                .putBoolean("banks", banks)
                .putBoolean("photo", showPhoto)
                .apply();
    }

    // ---------------------------------------------------------- persistence

    private boolean hasSaved() {
        return getSharedPreferences("layout", MODE_PRIVATE).getString("balls", "").length() > 0;
    }

    private void saveLayout() {
        StringBuilder sb = new StringBuilder();
        for (Ball b : balls) {
            if (sb.length() > 0) sb.append(';');
            sb.append(b.num).append(':').append(b.x).append(':').append(b.y);
        }
        getSharedPreferences("layout", MODE_PRIVATE).edit()
                .putString("balls", sb.toString())
                .putBoolean("hasPhoto", rectified != null)
                .apply();
    }

    private void savePhoto() {
        File f = new File(getFilesDir(), "table.jpg");
        if (rectified == null) {
            if (f.exists()) f.delete();
            return;
        }
        try (FileOutputStream out = new FileOutputStream(f)) {
            rectified.compress(Bitmap.CompressFormat.JPEG, 85, out);
        } catch (IOException ignored) {
        }
    }

    private void loadLayout() {
        SharedPreferences p = getSharedPreferences("layout", MODE_PRIVATE);
        balls.clear();
        String raw = p.getString("balls", "");
        for (String part : raw.split(";")) {
            String[] f = part.split(":");
            if (f.length != 3) continue;
            try {
                int n = Integer.parseInt(f[0]);
                float x = Float.parseFloat(f[1]), y = Float.parseFloat(f[2]);
                if (n >= 0 && n <= 15) balls.add(new Ball(n, x, y));
            } catch (NumberFormatException ignored) {
            }
        }
        rectified = null;
        if (p.getBoolean("hasPhoto", false)) {
            File f = new File(getFilesDir(), "table.jpg");
            if (f.exists()) rectified = BitmapFactory.decodeFile(f.getAbsolutePath());
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        if (screen == 2) {
            saveLayout();
            savePhoto();
        }
    }

    @Override
    public void onBackPressed() {
        if (screen == 2) {
            saveLayout();
            savePhoto();
            showHome();
        } else if (screen == 1) {
            showHome();
        } else {
            super.onBackPressed();
        }
    }
}
