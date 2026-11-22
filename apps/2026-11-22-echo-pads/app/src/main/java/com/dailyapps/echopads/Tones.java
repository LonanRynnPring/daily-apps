package com.dailyapps.echopads;

import android.media.AudioAttributes;
import android.media.AudioFormat;
import android.media.AudioTrack;
import android.os.Handler;
import android.os.Looper;

/** Tiny synthesiser: builds short PCM tones and plays them with AudioTrack. */
public class Tones {
    private static final int RATE = 22050;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final short[][] pads = new short[4][];
    private final short[] buzz;
    public boolean enabled = true;

    public Tones() {
        double[] freqs = {329.63, 415.30, 493.88, 659.25};
        for (int i = 0; i < 4; i++) pads[i] = make(freqs[i], 380, false);
        buzz = make(110, 600, true);
    }

    private short[] make(double freq, int ms, boolean saw) {
        int n = RATE * ms / 1000;
        short[] d = new short[n];
        for (int i = 0; i < n; i++) {
            double t = (double) i / RATE;
            double env = Math.min(1.0, i / 200.0) * Math.pow(1.0 - (double) i / n, 1.5);
            double s;
            if (saw) {
                double ph = (t * freq) % 1.0;
                s = (ph * 2 - 1) * 0.6;
            } else {
                s = Math.sin(2 * Math.PI * freq * t) + 0.3 * Math.sin(4 * Math.PI * freq * t);
                s *= 0.7;
            }
            d[i] = (short) (s * env * 20000);
        }
        return d;
    }

    public void playPad(int i) { play(pads[i]); }

    public void playBuzz() { play(buzz); }

    private void play(short[] data) {
        if (!enabled) return;
        try {
            final AudioTrack track = new AudioTrack.Builder()
                    .setAudioAttributes(new AudioAttributes.Builder()
                            .setUsage(AudioAttributes.USAGE_GAME)
                            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                            .build())
                    .setAudioFormat(new AudioFormat.Builder()
                            .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                            .setSampleRate(RATE)
                            .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                            .build())
                    .setBufferSizeInBytes(data.length * 2)
                    .setTransferMode(AudioTrack.MODE_STATIC)
                    .build();
            track.write(data, 0, data.length);
            track.play();
            handler.postDelayed(new Runnable() {
                public void run() {
                    try { track.release(); } catch (Exception ignored) { }
                }
            }, data.length * 1000L / RATE + 300);
        } catch (Exception ignored) {
        }
    }
}
