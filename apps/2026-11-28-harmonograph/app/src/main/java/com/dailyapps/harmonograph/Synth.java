package com.dailyapps.harmonograph;

import android.media.AudioFormat;
import android.media.AudioManager;
import android.media.AudioTrack;

/** Plays the pendulum frequencies as a decaying chord so you can hear the ratios. */
public final class Synth {
    private Synth() {}

    private static AudioTrack current;
    private static final int SR = 22050;

    public static void play(final Params p) {
        new Thread(new Runnable() {
            @Override
            public void run() {
                playBlocking(p);
            }
        }).start();
    }

    private static synchronized void stopCurrent() {
        if (current != null) {
            try {
                current.stop();
            } catch (Exception ignored) {
            }
            try {
                current.release();
            } catch (Exception ignored) {
            }
            current = null;
        }
    }

    private static void playBlocking(Params p) {
        float seconds = 4f;
        int n = (int) (SR * seconds);
        float[] mix = new float[n];
        float base = 110f;
        // pendulums active
        for (int i = 0; i < Params.N; i++) {
            if (p.a[i] <= 0.001f) continue;
            double hz = base * p.f[i];
            double amp = p.a[i];
            double decay = 0.4 + p.d[i] * 150.0;
            double w = 2 * Math.PI * hz / SR;
            for (int k = 0; k < n; k++) {
                double t = k / (double) SR;
                double env = Math.exp(-decay * t);
                if (t < 0.01) env *= t / 0.01;
                mix[k] += (float) (amp * env * (Math.sin(w * k) + 0.25 * Math.sin(2 * w * k)));
            }
        }
        float peak = 0.0001f;
        for (int k = 0; k < n; k++) peak = Math.max(peak, Math.abs(mix[k]));
        short[] pcm = new short[n];
        float g = 0.8f * 32767f / peak;
        for (int k = 0; k < n; k++) {
            float fade = k > n - 2000 ? (n - k) / 2000f : 1f;
            pcm[k] = (short) (mix[k] * g * fade);
        }
        stopCurrent();
        try {
            AudioTrack track = new AudioTrack(AudioManager.STREAM_MUSIC, SR,
                    AudioFormat.CHANNEL_OUT_MONO, AudioFormat.ENCODING_PCM_16BIT,
                    n * 2, AudioTrack.MODE_STATIC);
            track.write(pcm, 0, n);
            synchronized (Synth.class) {
                current = track;
            }
            track.play();
        } catch (Exception ignored) {
        }
    }

    public static void release() {
        stopCurrent();
    }
}
