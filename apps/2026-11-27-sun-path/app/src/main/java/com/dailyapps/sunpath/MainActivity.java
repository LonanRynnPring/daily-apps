package com.dailyapps.sunpath;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.DatePickerDialog;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.hardware.GeomagneticField;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.InputType;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.ScrollView;
import android.widget.SeekBar;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.TimeZone;

public class MainActivity extends Activity implements SensorEventListener {
    private static final String[] TAB_NAMES = {"Sky", "Times", "Year", "Aim"};

    private SharedPreferences prefs;
    private List<Place> places;
    private int placeIdx;
    private Place place;
    private TimeZone tz;
    private int year, month, day, minute;
    private boolean live = true;
    private long dayStart;
    private SunCalc.Times times;
    private boolean use24;
    private String yearKey = "";

    private Button placeBtn, liveBtn, dateBtn;
    private final Button[] tabBtns = new Button[4];
    private final View[] pages = new View[4];
    private int tab;
    private SkyView sky;
    private YearView yearView;
    private AimView aim;
    private LinearLayout timesList;
    private TextView nowAlt, nowAz, nowShadow, nowLight, nowMoon;
    private SeekBar seek;
    private TextView timeLabel, infoLabel;
    private boolean settingSeek;
    private final StringBuilder shareText = new StringBuilder();

    private final Handler handler = new Handler(Looper.getMainLooper());
    private SensorManager sensors;
    private Sensor rotSensor;
    private boolean listening;
    private final float[] rotM = new float[9];
    private final float[] rotV = new float[4];
    private float sx, sy, sz;
    private boolean haveVec;
    private float declination;

    private final Runnable tick = new Runnable() {
        @Override
        public void run() {
            if (live) goNow();
            handler.postDelayed(this, 20000);
        }
    };

    private float dp(float v) { return v * getResources().getDisplayMetrics().density; }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().setStatusBarColor(Draw.BG);
        getWindow().setNavigationBarColor(Draw.BG);
        prefs = getSharedPreferences("sunpath", MODE_PRIVATE);
        use24 = android.text.format.DateFormat.is24HourFormat(this);
        places = Place.load(prefs);
        placeIdx = Math.max(0, Math.min(places.size() - 1, prefs.getInt("current", 0)));
        tab = Math.max(0, Math.min(3, prefs.getInt("tab", 0)));
        sensors = (SensorManager) getSystemService(SENSOR_SERVICE);
        if (sensors != null) rotSensor = sensors.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR);

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(Draw.BG);

        // header
        LinearLayout header = new LinearLayout(this);
        header.setOrientation(LinearLayout.HORIZONTAL);
        header.setGravity(Gravity.CENTER_VERTICAL);
        header.setPadding((int) dp(10), (int) dp(8), (int) dp(10), (int) dp(2));
        placeBtn = flatButton("", 17, Draw.TEXT);
        placeBtn.setGravity(Gravity.CENTER_VERTICAL | Gravity.START);
        placeBtn.setTypeface(Typeface.DEFAULT_BOLD);
        placeBtn.setOnClickListener(v -> showPlaces());
        header.addView(placeBtn, new LinearLayout.LayoutParams(0, (int) dp(48), 1));
        Button share = flatButton("Share", 14, Draw.MUTED);
        share.setOnClickListener(v -> share());
        header.addView(share, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, (int) dp(48)));
        liveBtn = flatButton("Now", 14, Draw.ACCENT);
        liveBtn.setOnClickListener(v -> { live = true; goNow(); });
        header.addView(liveBtn, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, (int) dp(48)));
        root.addView(header);

        // date row
        LinearLayout dateRow = new LinearLayout(this);
        dateRow.setGravity(Gravity.CENTER_VERTICAL);
        dateRow.setPadding((int) dp(10), 0, (int) dp(10), 0);
        Button prev = flatButton("◀", 16, Draw.TEXT);
        prev.setOnClickListener(v -> shiftDay(-1));
        Button next = flatButton("▶", 16, Draw.TEXT);
        next.setOnClickListener(v -> shiftDay(1));
        dateBtn = flatButton("", 16, Draw.TEXT);
        dateBtn.setOnClickListener(v -> pickDate());
        dateRow.addView(prev, new LinearLayout.LayoutParams((int) dp(56), (int) dp(44)));
        dateRow.addView(dateBtn, new LinearLayout.LayoutParams(0, (int) dp(44), 1));
        dateRow.addView(next, new LinearLayout.LayoutParams((int) dp(56), (int) dp(44)));
        root.addView(dateRow);

        // tabs
        LinearLayout tabRow = new LinearLayout(this);
        tabRow.setPadding((int) dp(10), (int) dp(4), (int) dp(10), (int) dp(4));
        for (int i = 0; i < 4; i++) {
            final int idx = i;
            Button b = flatButton(TAB_NAMES[i], 14, Draw.TEXT);
            b.setOnClickListener(v -> selectTab(idx));
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, (int) dp(40), 1);
            lp.setMargins((int) dp(3), 0, (int) dp(3), 0);
            tabRow.addView(b, lp);
            tabBtns[i] = b;
        }
        root.addView(tabRow);

        // pages
        FrameLayout content = new FrameLayout(this);
        sky = new SkyView(this);
        sky.setListener(ms -> {
            live = false;
            minute = (int) Math.max(0, Math.min(1439, (ms - dayStart) / 60000L));
            refreshTime();
        });
        pages[0] = sky;

        ScrollView sv = new ScrollView(this);
        timesList = new LinearLayout(this);
        timesList.setOrientation(LinearLayout.VERTICAL);
        timesList.setPadding((int) dp(16), (int) dp(4), (int) dp(16), (int) dp(16));
        sv.addView(timesList);
        pages[1] = sv;

        yearView = new YearView(this);
        yearView.setListener((y, m, d) -> {
            live = false;
            year = y;
            month = m;
            day = d;
            refreshDay();
        });
        pages[2] = yearView;

        aim = new AimView(this);
        aim.setHasSensor(rotSensor != null);
        pages[3] = aim;
        for (View p : pages) content.addView(p, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        root.addView(content, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1));

        // time scrubber
        LinearLayout bottom = new LinearLayout(this);
        bottom.setOrientation(LinearLayout.VERTICAL);
        bottom.setBackgroundColor(Draw.PANEL);
        bottom.setPadding((int) dp(16), (int) dp(8), (int) dp(16), (int) dp(10));
        LinearLayout tl = new LinearLayout(this);
        tl.setGravity(Gravity.CENTER_VERTICAL);
        timeLabel = new TextView(this);
        timeLabel.setTextColor(Draw.TEXT);
        timeLabel.setTextSize(22);
        timeLabel.setTypeface(Typeface.DEFAULT_BOLD);
        infoLabel = new TextView(this);
        infoLabel.setTextColor(Draw.MUTED);
        infoLabel.setTextSize(13);
        infoLabel.setGravity(Gravity.END);
        tl.addView(timeLabel, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        tl.addView(infoLabel, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        bottom.addView(tl);
        seek = new SeekBar(this);
        seek.setMax(1439);
        seek.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar s, int p, boolean fromUser) {
                if (settingSeek || !fromUser) return;
                live = false;
                minute = p;
                refreshTime();
            }
            @Override public void onStartTrackingTouch(SeekBar s) {}
            @Override public void onStopTrackingTouch(SeekBar s) {}
        });
        bottom.addView(seek, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, (int) dp(40)));
        root.addView(bottom);

        setContentView(root);
        setPlace(placeIdx);
        selectTab(tab);
    }

    private Button flatButton(String label, int size, int color) {
        Button b = new Button(this);
        b.setText(label);
        b.setTextSize(size);
        b.setTextColor(color);
        b.setAllCaps(false);
        b.setBackgroundColor(0x00000000);
        b.setPadding((int) dp(8), 0, (int) dp(8), 0);
        b.setMinWidth(0);
        b.setMinimumWidth(0);
        return b;
    }

    private GradientDrawable pill(int color) {
        GradientDrawable g = new GradientDrawable();
        g.setColor(color);
        g.setCornerRadius(dp(20));
        return g;
    }

    private void selectTab(int i) {
        tab = i;
        prefs.edit().putInt("tab", i).apply();
        for (int k = 0; k < 4; k++) {
            pages[k].setVisibility(k == i ? View.VISIBLE : View.GONE);
            tabBtns[k].setBackground(pill(k == i ? 0xFF2D333B : Draw.PANEL));
            tabBtns[k].setTextColor(k == i ? Draw.ACCENT : Draw.MUTED);
        }
        updateSensor();
    }

    private void setPlace(int idx) {
        placeIdx = idx;
        place = places.get(idx);
        tz = place.timeZone();
        prefs.edit().putInt("current", idx).apply();
        placeBtn.setText(place.name + "  ▾");
        declination = new GeomagneticField((float) place.lat, (float) place.lon, 0,
                System.currentTimeMillis()).getDeclination();
        if (live) {
            goNow();
        } else {
            refreshDay();
        }
    }

    private void goNow() {
        Calendar c = Calendar.getInstance(tz);
        int y = c.get(Calendar.YEAR), m = c.get(Calendar.MONTH), d = c.get(Calendar.DAY_OF_MONTH);
        minute = c.get(Calendar.HOUR_OF_DAY) * 60 + c.get(Calendar.MINUTE);
        if (y != year || m != month || d != day || times == null || dayStart == 0) {
            year = y;
            month = m;
            day = d;
            refreshDay();
        } else {
            refreshTime();
        }
    }

    private void shiftDay(int delta) {
        live = false;
        Calendar c = Calendar.getInstance(tz);
        c.clear();
        c.set(year, month, day, 12, 0, 0);
        c.add(Calendar.DAY_OF_MONTH, delta);
        year = c.get(Calendar.YEAR);
        month = c.get(Calendar.MONTH);
        day = c.get(Calendar.DAY_OF_MONTH);
        refreshDay();
    }

    private void pickDate() {
        new DatePickerDialog(this, (v, y, m, d) -> {
            live = false;
            year = y;
            month = m;
            day = d;
            refreshDay();
        }, year, month, day).show();
    }

    private long startOf(int y, int m, int d) {
        Calendar c = Calendar.getInstance(tz);
        c.clear();
        c.set(y, m, d, 0, 0, 0);
        return c.getTimeInMillis();
    }

    private SimpleDateFormat timeFmt() {
        SimpleDateFormat f = new SimpleDateFormat(use24 ? "HH:mm" : "h:mm a", Locale.getDefault());
        f.setTimeZone(tz);
        return f;
    }

    private SimpleDateFormat dateFmt(String pattern) {
        SimpleDateFormat f = new SimpleDateFormat(pattern, Locale.getDefault());
        f.setTimeZone(tz);
        return f;
    }

    static String duration(float minutes) {
        int m = Math.round(minutes);
        return (m / 60) + "h " + String.format(Locale.US, "%02d", m % 60) + "m";
    }

    private void refreshDay() {
        dayStart = startOf(year, month, day);
        times = SunCalc.times(dayStart + 12 * 3600000L, place.lat, place.lon);
        Calendar today = Calendar.getInstance(tz);
        boolean isToday = today.get(Calendar.YEAR) == year && today.get(Calendar.MONTH) == month
                && today.get(Calendar.DAY_OF_MONTH) == day;
        dateBtn.setText(dateFmt("EEE d MMM yyyy").format(new Date(dayStart + 12 * 3600000L))
                + (isToday ? "  (today)" : ""));
        sky.setDay(place.lat, place.lon, tz, dayStart, year, times, use24);
        String key = place.encode() + "#" + year;
        if (!key.equals(yearKey)) {
            yearKey = key;
            yearView.setYear(place.lat, place.lon, tz, year);
        }
        yearView.setSelected(year, month, day);
        buildTimes();
        refreshTime();
    }

    private void refreshTime() {
        long t = dayStart + minute * 60000L;
        settingSeek = true;
        seek.setProgress(minute);
        settingSeek = false;
        liveBtn.setText(live ? "● Live" : "Now");
        String ts = timeFmt().format(new Date(t));
        timeLabel.setText(ts);
        double[] sun = SunCalc.sunPosition(t, place.lat, place.lon);
        double[] moon = SunCalc.moonPosition(t, place.lat, place.lon);
        double[] ill = SunCalc.moonIllumination(t);
        infoLabel.setText(String.format(Locale.getDefault(), "Sun %+.1f°  %.0f° %s\n%s",
                sun[0], sun[1], SunCalc.compassPoint(sun[1]), Draw.lightName(sun[0])));
        sky.setNow(sun[0], sun[1], moon[0], moon[1], ill[1]);
        aim.setTargets(sun[0], sun[1], moon[0], moon[1], ill[1], sky.dayAltitudes(), sky.dayAzimuths(), ts);
        if (nowAlt != null) {
            nowAlt.setText(String.format(Locale.getDefault(), "%+.1f°", sun[0]));
            nowAz.setText(String.format(Locale.getDefault(), "%.1f° %s", sun[1], SunCalc.compassPoint(sun[1])));
            if (sun[0] > 0.5) {
                double ratio = 1.0 / Math.tan(Math.toRadians(sun[0]));
                nowShadow.setText(String.format(Locale.getDefault(), "%.1f× height (1.8 m person: %.1f m)",
                        ratio, ratio * 1.8));
            } else {
                nowShadow.setText("No direct sun");
            }
            nowLight.setText(Draw.lightName(sun[0]));
            nowMoon.setText(String.format(Locale.getDefault(), "%+.1f°, %.0f° %s",
                    moon[0], moon[1], SunCalc.compassPoint(moon[1])));
        }
    }

    // ---- Times page ----

    private void section(String title) {
        TextView tv = new TextView(this);
        tv.setText(title);
        tv.setTextColor(Draw.ACCENT);
        tv.setTextSize(13);
        tv.setTypeface(Typeface.DEFAULT_BOLD);
        tv.setPadding(0, (int) dp(18), 0, (int) dp(6));
        timesList.addView(tv);
        shareText.append('\n').append(title).append('\n');
    }

    private TextView row(String label, String value, int dotColor) {
        LinearLayout r = new LinearLayout(this);
        r.setGravity(Gravity.CENTER_VERTICAL);
        r.setPadding((int) dp(12), (int) dp(10), (int) dp(12), (int) dp(10));
        r.setBackground(pillSmall());
        View dot = new View(this);
        GradientDrawable g = new GradientDrawable();
        g.setShape(GradientDrawable.OVAL);
        g.setColor(dotColor);
        dot.setBackground(g);
        LinearLayout.LayoutParams dlp = new LinearLayout.LayoutParams((int) dp(10), (int) dp(10));
        dlp.setMargins(0, 0, (int) dp(12), 0);
        r.addView(dot, dlp);
        TextView l = new TextView(this);
        l.setText(label);
        l.setTextColor(Draw.TEXT);
        l.setTextSize(15);
        r.addView(l, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        TextView v = new TextView(this);
        v.setText(value);
        v.setTextColor(Draw.TEXT);
        v.setTextSize(15);
        v.setTypeface(Typeface.DEFAULT_BOLD);
        v.setGravity(Gravity.END);
        r.addView(v, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.setMargins(0, (int) dp(3), 0, (int) dp(3));
        timesList.addView(r, lp);
        shareText.append(label).append(": ").append(value).append('\n');
        return v;
    }

    private GradientDrawable pillSmall() {
        GradientDrawable g = new GradientDrawable();
        g.setColor(Draw.PANEL);
        g.setCornerRadius(dp(10));
        return g;
    }

    private String fmt(long ms) {
        return ms == SunCalc.NONE ? "—" : timeFmt().format(new Date(ms));
    }

    private String range(long a, long b) {
        if (a == SunCalc.NONE && b == SunCalc.NONE) return "—";
        return fmt(a) + " – " + fmt(b);
    }

    private void buildTimes() {
        timesList.removeAllViews();
        shareText.setLength(0);
        shareText.append("Sun Path — ").append(place.name).append(" (").append(place.coords()).append(")\n");
        shareText.append(dateFmt("EEEE d MMMM yyyy").format(new Date(dayStart + 12 * 3600000L))).append('\n');
        SunCalc.Times t = times;
        long[] r = t.rise, s = t.set;

        TextView coords = new TextView(this);
        coords.setText(place.coords() + "   ·   " + tz.getID());
        coords.setTextColor(Draw.MUTED);
        coords.setTextSize(12);
        coords.setPadding(0, (int) dp(6), 0, 0);
        timesList.addView(coords);

        section("MORNING");
        row("Astronomical dawn", fmt(r[SunCalc.ASTRO]), 0xFF1B2550);
        row("Nautical dawn", fmt(r[SunCalc.NAUTICAL]), 0xFF283A75);
        row("Blue hour", range(r[SunCalc.CIVIL], r[SunCalc.BLUE_GOLD]), Draw.BLUE);
        row("Golden hour", range(r[SunCalc.BLUE_GOLD], r[SunCalc.GOLDEN]), Draw.GOLD);
        String riseVal = fmt(r[SunCalc.SUNRISE]);
        if (r[SunCalc.SUNRISE] != SunCalc.NONE) {
            double az = SunCalc.sunPosition(r[SunCalc.SUNRISE], place.lat, place.lon)[1];
            riseVal += String.format(Locale.getDefault(), "  (%.0f° %s)", az, SunCalc.compassPoint(az));
        }
        row("Sunrise", riseVal, Draw.SUN);

        section("MIDDAY");
        row("Solar noon", fmt(t.noon) + String.format(Locale.getDefault(), "  (%.1f° high)", t.noonAlt), 0xFFFFFFFF);
        float len;
        if (r[SunCalc.SUNRISE] == SunCalc.NONE) {
            len = t.noonAlt > SunCalc.ANGLES[SunCalc.SUNRISE] ? 1440 : 0;
        } else {
            len = (s[SunCalc.SUNRISE] - r[SunCalc.SUNRISE]) / 60000f;
        }
        row("Day length", duration(len), Draw.ACCENT);
        SunCalc.Times y = SunCalc.times(dayStart - 12 * 3600000L, place.lat, place.lon);
        float ylen;
        if (y.rise[SunCalc.SUNRISE] == SunCalc.NONE) {
            ylen = y.noonAlt > SunCalc.ANGLES[SunCalc.SUNRISE] ? 1440 : 0;
        } else {
            ylen = (y.set[SunCalc.SUNRISE] - y.rise[SunCalc.SUNRISE]) / 60000f;
        }
        float diffSec = (len - ylen) * 60f;
        int ds = Math.round(Math.abs(diffSec));
        row("vs. yesterday", (diffSec >= 0 ? "+" : "−") + (ds / 60) + "m " + (ds % 60) + "s", Draw.MUTED);
        if (r[SunCalc.SUNRISE] == SunCalc.NONE) {
            row(t.noonAlt > 0 ? "Midnight sun" : "Polar night",
                    t.noonAlt > 0 ? "Sun never sets" : "Sun never rises", Draw.MUTED);
        }

        section("EVENING");
        row("Golden hour", range(s[SunCalc.GOLDEN], s[SunCalc.BLUE_GOLD]), Draw.GOLD);
        String setVal = fmt(s[SunCalc.SUNRISE]);
        if (s[SunCalc.SUNRISE] != SunCalc.NONE) {
            double az = SunCalc.sunPosition(s[SunCalc.SUNRISE], place.lat, place.lon)[1];
            setVal += String.format(Locale.getDefault(), "  (%.0f° %s)", az, SunCalc.compassPoint(az));
        }
        row("Sunset", setVal, Draw.SUN);
        row("Blue hour", range(s[SunCalc.BLUE_GOLD], s[SunCalc.CIVIL]), Draw.BLUE);
        row("Nautical dusk", fmt(s[SunCalc.NAUTICAL]), 0xFF283A75);
        row("Astronomical dusk", fmt(s[SunCalc.ASTRO]), 0xFF1B2550);

        section("MOON");
        double[] ill = SunCalc.moonIllumination(dayStart + 12 * 3600000L);
        row("Phase", SunCalc.phaseName(ill[1]), Draw.MOON);
        row("Illuminated", Math.round(ill[0] * 100) + "%  (age " + String.format(Locale.getDefault(), "%.1f", ill[1] * 29.53) + " d)", Draw.MOON);
        long[] mrs = SunCalc.moonRiseSet(dayStart, place.lat, place.lon);
        row("Moonrise", fmt(mrs[0]), Draw.MOON);
        row("Moonset", fmt(mrs[1]), Draw.MOON);
        long full = SunCalc.NONE, nw = SunCalc.NONE;
        double prev = ill[1];
        for (int i = 1; i <= 32 && (full == SunCalc.NONE || nw == SunCalc.NONE); i++) {
            long at = dayStart + 12 * 3600000L + i * 86400000L;
            double p = SunCalc.moonIllumination(at)[1];
            if (full == SunCalc.NONE && prev < 0.5 && p >= 0.5) full = at;
            if (nw == SunCalc.NONE && prev > 0.75 && p < 0.25) nw = at;
            prev = p;
        }
        SimpleDateFormat df = dateFmt("EEE d MMM");
        row("Next full moon", full == SunCalc.NONE ? "—" : df.format(new Date(full)), 0xFFFFF4C2);
        row("Next new moon", nw == SunCalc.NONE ? "—" : df.format(new Date(nw)), 0xFF3A3F4B);

        section("AT THE SELECTED TIME");
        nowLight = row("Light", "", Draw.ACCENT);
        nowAlt = row("Sun altitude", "", Draw.SUN);
        nowAz = row("Sun bearing", "", Draw.SUN);
        nowShadow = row("Shadow length", "", 0xFF8B949E);
        nowMoon = row("Moon", "", Draw.MOON);

        TextView note = new TextView(this);
        note.setText("Golden hour: sun between +6° and −4°. Blue hour: −4° to −6°. "
                + "Times are in the place's own time zone. Bearings are true north.");
        note.setTextColor(Draw.MUTED);
        note.setTextSize(12);
        note.setPadding(0, (int) dp(16), 0, 0);
        timesList.addView(note);
    }

    private void share() {
        if (shareText.length() == 0) return;
        StringBuilder sb = new StringBuilder(shareText.toString().replaceAll("(?m)^(Light|Sun altitude|Sun bearing|Shadow length|Moon): .*\\n", ""));
        sb = new StringBuilder(sb.toString().replace("\nAT THE SELECTED TIME\n", ""));
        Intent i = new Intent(Intent.ACTION_SEND);
        i.setType("text/plain");
        i.putExtra(Intent.EXTRA_TEXT, sb.toString());
        startActivity(Intent.createChooser(i, "Share sun times"));
    }

    // ---- Places ----

    private void showPlaces() {
        List<String> items = new ArrayList<>();
        for (int i = 0; i < places.size(); i++) {
            items.add((i == placeIdx ? "●  " : "     ") + places.get(i).name);
        }
        items.add("+  Add a city from the list");
        items.add("+  Add custom coordinates");
        ListView lv = new ListView(this);
        lv.setAdapter(new ArrayAdapter<>(this, android.R.layout.simple_list_item_1, items));
        AlertDialog dlg = new AlertDialog.Builder(this)
                .setTitle("Places  (long-press to remove)")
                .setView(lv)
                .setNegativeButton("Close", null)
                .create();
        lv.setOnItemClickListener((AdapterView<?> parent, View view, int pos, long id) -> {
            dlg.dismiss();
            if (pos < places.size()) {
                setPlace(pos);
            } else if (pos == places.size()) {
                addFromList();
            } else {
                addCustom();
            }
        });
        lv.setOnItemLongClickListener((AdapterView<?> parent, View view, int pos, long id) -> {
            if (pos >= places.size()) return false;
            if (places.size() <= 1) {
                Toast.makeText(this, "Keep at least one place", Toast.LENGTH_SHORT).show();
                return true;
            }
            dlg.dismiss();
            final Place p = places.get(pos);
            new AlertDialog.Builder(this)
                    .setMessage("Remove " + p.name + "?")
                    .setPositiveButton("Remove", (d, w) -> {
                        places.remove(p);
                        Place.save(prefs, places);
                        int ni = placeIdx;
                        if (pos < placeIdx) ni--;
                        if (ni >= places.size()) ni = places.size() - 1;
                        if (ni < 0) ni = 0;
                        setPlace(ni);
                    })
                    .setNegativeButton("Cancel", null)
                    .show();
            return true;
        });
        dlg.show();
    }

    private void addPlace(Place p) {
        places.add(p);
        Place.save(prefs, places);
        setPlace(places.size() - 1);
    }

    private void addFromList() {
        final List<Place> ps = Place.presets();
        String[] names = new String[ps.size()];
        for (int i = 0; i < ps.size(); i++) names[i] = ps.get(i).name;
        new AlertDialog.Builder(this)
                .setTitle("Choose a city")
                .setItems(names, (d, which) -> addPlace(ps.get(which)))
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void addCustom() {
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        int pad = (int) dp(20);
        box.setPadding(pad, (int) dp(8), pad, 0);
        final EditText name = new EditText(this);
        name.setHint("Name (e.g. Favourite beach)");
        name.setSingleLine(true);
        final EditText lat = new EditText(this);
        lat.setHint("Latitude, or paste \"lat, lon\"");
        lat.setSingleLine(true);
        lat.setInputType(InputType.TYPE_CLASS_TEXT);
        final EditText lon = new EditText(this);
        lon.setHint("Longitude (west is negative)");
        lon.setSingleLine(true);
        lon.setInputType(InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_DECIMAL | InputType.TYPE_NUMBER_FLAG_SIGNED);
        TextView tzl = new TextView(this);
        tzl.setText("Time zone");
        tzl.setPadding(0, (int) dp(10), 0, 0);
        final String[] ids = TimeZone.getAvailableIDs();
        Arrays.sort(ids);
        final Spinner sp = new Spinner(this);
        sp.setAdapter(new ArrayAdapter<>(this, android.R.layout.simple_spinner_dropdown_item, ids));
        String cur = place != null ? place.tz : TimeZone.getDefault().getID();
        int sel = Arrays.asList(ids).indexOf(cur);
        if (sel >= 0) sp.setSelection(sel);
        TextView tip = new TextView(this);
        tip.setText("Tip: long-press a spot in your maps app to copy its coordinates.");
        tip.setTextSize(12);
        tip.setPadding(0, (int) dp(8), 0, 0);
        box.addView(name);
        box.addView(lat);
        box.addView(lon);
        box.addView(tzl);
        box.addView(sp);
        box.addView(tip);
        new AlertDialog.Builder(this)
                .setTitle("Custom place")
                .setView(box)
                .setPositiveButton("Add", (d, w) -> {
                    try {
                        String la = lat.getText().toString().trim();
                        String lo = lon.getText().toString().trim();
                        if (la.contains(",")) {
                            String[] parts = la.split(",");
                            la = parts[0].trim();
                            lo = parts[1].trim();
                        }
                        double a = Double.parseDouble(la);
                        double o = Double.parseDouble(lo);
                        if (a < -90 || a > 90 || o < -180 || o > 180) throw new IllegalArgumentException();
                        String n = Place.clean(name.getText().toString());
                        if (n.isEmpty()) n = String.format(Locale.US, "%.3f, %.3f", a, o);
                        addPlace(new Place(n, a, o, (String) sp.getSelectedItem()));
                    } catch (Exception e) {
                        Toast.makeText(this, "Couldn't read those coordinates", Toast.LENGTH_LONG).show();
                    }
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    // ---- Sensor ----

    private void updateSensor() {
        boolean want = tab == 3 && rotSensor != null && hasWindowFocusSafe();
        if (want && !listening) {
            sensors.registerListener(this, rotSensor, SensorManager.SENSOR_DELAY_GAME);
            listening = true;
        } else if (!want && listening) {
            sensors.unregisterListener(this);
            listening = false;
        }
    }

    private boolean resumed;

    private boolean hasWindowFocusSafe() { return resumed; }

    @Override
    protected void onResume() {
        super.onResume();
        resumed = true;
        updateSensor();
        handler.removeCallbacks(tick);
        handler.post(tick);
    }

    @Override
    protected void onPause() {
        super.onPause();
        resumed = false;
        updateSensor();
        handler.removeCallbacks(tick);
    }

    @Override
    public void onSensorChanged(SensorEvent e) {
        if (e.sensor.getType() != Sensor.TYPE_ROTATION_VECTOR) return;
        int n = Math.min(4, e.values.length);
        for (int i = 0; i < 4; i++) rotV[i] = i < n ? e.values[i] : 0f;
        if (n < 4) {
            SensorManager.getRotationMatrixFromVector(rotM, Arrays.copyOf(e.values, 3));
        } else {
            SensorManager.getRotationMatrixFromVector(rotM, rotV);
        }
        float vx = -rotM[2], vy = -rotM[5], vz = -rotM[8];
        if (!haveVec) {
            sx = vx; sy = vy; sz = vz;
            haveVec = true;
        } else {
            float k = 0.18f;
            sx += k * (vx - sx);
            sy += k * (vy - sy);
            sz += k * (vz - sz);
        }
        float len = (float) Math.sqrt(sx * sx + sy * sy + sz * sz);
        if (len < 1e-4f) return;
        float az = (float) Math.toDegrees(Math.atan2(sx, sy)) + declination;
        float el = (float) Math.toDegrees(Math.asin(Math.max(-1f, Math.min(1f, sz / len))));
        aim.setCamera(az, el);
    }

    @Override
    public void onAccuracyChanged(Sensor sensor, int accuracy) {}
}
