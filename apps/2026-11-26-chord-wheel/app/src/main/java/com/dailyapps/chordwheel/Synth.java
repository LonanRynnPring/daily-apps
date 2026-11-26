package com.dailyapps.chordwheel;

import android.media.AudioFormat;
import android.media.AudioManager;
import android.media.AudioTrack;

import java.util.ArrayList;

/** Tiny polyphonic additive synth that streams to an AudioTrack on its own thread. */
final class Synth implements Runnable {
    static final int RATE = 22050;
    private static final int CHUNK = 512;

    private static final class Voice {
        double phase;
        double inc;
        float amp;
        float decay;
        int age;
        int len;
        int release;
        boolean click;
    }

    private final ArrayList<Voice> voices = new ArrayList<>();
    private volatile boolean running;
    private Thread thread;
    private volatile int tone = 0; // 0 = soft keys, 1 = organ, 2 = pluck

    void setTone(int t) {
        tone = t;
    }

    synchronized void start() {
        if (running) return;
        running = true;
        thread = new Thread(this, "chord-synth");
        thread.start();
    }

    synchronized void stop() {
        running = false;
        if (thread != null) {
            try {
                thread.join(600);
            } catch (InterruptedException ignored) {
            }
            thread = null;
        }
        synchronized (voices) {
            voices.clear();
        }
    }

    void silence() {
        synchronized (voices) {
            for (Voice v : voices) {
                int remaining = v.len - v.age;
                if (remaining > 600) v.len = v.age + 600;
                v.release = 600;
            }
        }
    }

    void playNotes(int[] midi, double seconds) {
        if (seconds < 0.15) seconds = 0.15;
        if (seconds > 5) seconds = 5;
        float amp = 0.55f / (float) Math.max(3, midi.length);
        float decay;
        if (tone == 1) decay = 0.15f;
        else if (tone == 2) decay = 3.2f;
        else decay = 1.1f;
        synchronized (voices) {
            for (int i = 0; i < midi.length; i++) {
                Voice v = new Voice();
                double f = 440.0 * Math.pow(2.0, (midi[i] - 69) / 12.0);
                v.inc = 2 * Math.PI * f / RATE;
                v.amp = (i == 0) ? amp * 1.1f : amp;
                v.decay = decay;
                v.len = (int) (seconds * RATE);
                v.release = (int) (0.06 * RATE);
                voices.add(v);
            }
            while (voices.size() > 24) voices.remove(0);
        }
    }

    void click(boolean accent) {
        Voice v = new Voice();
        double f = accent ? 1760 : 1175;
        v.inc = 2 * Math.PI * f / RATE;
        v.amp = accent ? 0.22f : 0.13f;
        v.decay = 45f;
        v.len = (int) (0.06 * RATE);
        v.release = 60;
        v.click = true;
        synchronized (voices) {
            voices.add(v);
        }
    }

    @Override
    public void run() {
        AudioTrack track;
        try {
            int min = AudioTrack.getMinBufferSize(RATE, AudioFormat.CHANNEL_OUT_MONO,
                    AudioFormat.ENCODING_PCM_16BIT);
            int size = Math.max(min, CHUNK * 4 * 2);
            track = new AudioTrack(AudioManager.STREAM_MUSIC, RATE, AudioFormat.CHANNEL_OUT_MONO,
                    AudioFormat.ENCODING_PCM_16BIT, size, AudioTrack.MODE_STREAM);
            if (track.getState() != AudioTrack.STATE_INITIALIZED) {
                track.release();
                running = false;
                return;
            }
            track.play();
        } catch (Exception e) {
            running = false;
            return;
        }
        float[] mix = new float[CHUNK];
        short[] out = new short[CHUNK];
        int attack = (int) (0.006 * RATE);
        while (running) {
            for (int i = 0; i < CHUNK; i++) mix[i] = 0f;
            int t = tone;
            synchronized (voices) {
                for (int vi = voices.size() - 1; vi >= 0; vi--) {
                    Voice v = voices.get(vi);
                    for (int i = 0; i < CHUNK && v.age < v.len; i++) {
                        float env = (float) Math.exp(-v.decay * (v.age / (float) RATE));
                        if (v.age < attack) env *= v.age / (float) attack;
                        int left = v.len - v.age;
                        if (left < v.release) env *= left / (float) v.release;
                        double p = v.phase;
                        float s;
                        if (v.click) {
                            s = (float) Math.sin(p);
                        } else if (t == 1) {
                            s = (float) (Math.sin(p) + 0.5 * Math.sin(2 * p) + 0.3 * Math.sin(4 * p));
                        } else if (t == 2) {
                            float bright = (float) Math.exp(-6.0 * v.age / RATE);
                            s = (float) (Math.sin(p) + bright * (0.6 * Math.sin(2 * p) + 0.4 * Math.sin(3 * p)));
                        } else {
                            s = (float) (Math.sin(p) + 0.35 * Math.sin(2 * p) + 0.12 * Math.sin(3 * p));
                        }
                        mix[i] += s * env * v.amp;
                        v.phase = p + v.inc;
                        if (v.phase > 2 * Math.PI) v.phase -= 2 * Math.PI;
                        v.age++;
                    }
                    if (v.age >= v.len) voices.remove(vi);
                }
            }
            for (int i = 0; i < CHUNK; i++) {
                float x = mix[i];
                x = x / (1f + Math.abs(x) * 0.6f);
                if (x > 1f) x = 1f;
                if (x < -1f) x = -1f;
                out[i] = (short) (x * 30000);
            }
            track.write(out, 0, CHUNK);
        }
        try {
            track.stop();
        } catch (Exception ignored) {
        }
        track.release();
    }
}
