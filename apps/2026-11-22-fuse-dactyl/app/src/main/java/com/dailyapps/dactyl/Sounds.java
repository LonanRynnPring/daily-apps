package com.dailyapps.dactyl;

import android.media.AudioFormat;
import android.media.AudioManager;
import android.media.AudioTrack;
import android.os.Handler;
import android.os.Looper;

import java.util.Random;

/** Tiny synthesised sound effects, no assets needed. */
final class Sounds {
    private static final int RATE = 22050;
    private static final Handler HANDLER = new Handler(Looper.getMainLooper());

    static short[] defuse;
    static short[] boom;

    private Sounds() {}

    static void init() {
        if (defuse != null) return;

        // Defuse: short rising blip with a little harmonic.
        int n = (int) (RATE * 0.13f);
        defuse = new short[n];
        double phase = 0;
        for (int i = 0; i < n; i++) {
            double t = i / (double) RATE;
            double f = 700 + 900 * (i / (double) n);
            phase += 2 * Math.PI * f / RATE;
            double env = Math.exp(-t * 18.0);
            double v = (Math.sin(phase) + 0.4 * Math.sin(phase * 2)) * env;
            defuse[i] = (short) (v * 0.45 * 32767);
        }

        // Boom: low-passed noise plus a falling thump.
        n = (int) (RATE * 0.8f);
        boom = new short[n];
        Random rnd = new Random(7);
        double lp = 0;
        double ph = 0;
        for (int i = 0; i < n; i++) {
            double t = i / (double) RATE;
            double noise = rnd.nextDouble() * 2 - 1;
            double a = 0.15 + 0.6 * Math.exp(-t * 6.0);
            lp += a * (noise - lp);
            double f = 40 + 90 * Math.exp(-t * 7.0);
            ph += 2 * Math.PI * f / RATE;
            double env = Math.exp(-t * 4.5);
            double v = (lp * 1.4 + Math.sin(ph) * 0.9) * env;
            if (v > 1) v = 1;
            if (v < -1) v = -1;
            boom[i] = (short) (v * 0.8 * 32767);
        }
    }

    static void play(short[] data) {
        if (data == null) return;
        try {
            final AudioTrack track = new AudioTrack(AudioManager.STREAM_MUSIC, RATE,
                    AudioFormat.CHANNEL_OUT_MONO, AudioFormat.ENCODING_PCM_16BIT,
                    data.length * 2, AudioTrack.MODE_STATIC);
            track.write(data, 0, data.length);
            track.play();
            long ms = data.length * 1000L / RATE + 250;
            HANDLER.postDelayed(new Runnable() {
                @Override
                public void run() {
                    try {
                        track.release();
                    } catch (Exception ignored) {
                    }
                }
            }, ms);
        } catch (Exception ignored) {
        }
    }
}
