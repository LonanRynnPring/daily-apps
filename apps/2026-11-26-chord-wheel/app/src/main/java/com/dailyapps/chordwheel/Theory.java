package com.dailyapps.chordwheel;

import java.util.ArrayList;
import java.util.List;

/** Small music-theory helper: keys, diatonic chords, names and voicings. */
final class Theory {
    private Theory() {}

    static final String[] SHARP = {"C", "C#", "D", "D#", "E", "F", "F#", "G", "G#", "A", "A#", "B"};
    static final String[] FLAT = {"C", "Db", "D", "Eb", "E", "F", "Gb", "G", "Ab", "A", "Bb", "B"};
    /** Major tonics in circle-of-fifths order, clockwise from the top. */
    static final int[] FIFTHS = {0, 7, 2, 9, 4, 11, 6, 1, 8, 3, 10, 5};
    static final int[] MAJOR_SCALE = {0, 2, 4, 5, 7, 9, 11};
    static final int[] MINOR_SCALE = {0, 2, 3, 5, 7, 8, 10};
    static final int MAJ = 0, MIN = 1, DIM = 2;
    static final int[] MAJOR_Q = {MAJ, MIN, MIN, MAJ, MAJ, MIN, DIM};
    static final int[] MINOR_Q = {MIN, DIM, MAJ, MIN, MIN, MAJ, MAJ};
    static final String[] MAJOR_ROMAN = {"I", "ii", "iii", "IV", "V", "vi", "vii°"};
    static final String[] MINOR_ROMAN = {"i", "ii°", "III", "iv", "v", "VI", "VII"};

    /** Common functional-harmony continuations for each major-key degree. */
    static final int[][] SUGGEST_MAJOR = {
            {3, 4, 5, 1}, {4, 6}, {5, 3}, {4, 0, 1}, {0, 5}, {1, 3, 4}, {0, 2}
    };
    /** Continuations for each natural-minor degree. */
    static final int[][] SUGGEST_MINOR = {
            {5, 3, 6, 4}, {4, 6}, {5, 3}, {4, 0, 6}, {0, 5}, {2, 6, 3}, {2, 0}
    };

    static int[] scale(boolean minor) {
        return minor ? MINOR_SCALE : MAJOR_SCALE;
    }

    static boolean useFlats(int tonic, boolean minor) {
        int major = minor ? (tonic + 3) % 12 : tonic;
        switch (major) {
            case 5: case 10: case 3: case 8: case 1:
                return true;
            default:
                return false;
        }
    }

    static String noteName(int pc, boolean flats) {
        pc = ((pc % 12) + 12) % 12;
        return flats ? FLAT[pc] : SHARP[pc];
    }

    static String keyName(int tonic, boolean minor) {
        return noteName(tonic, useFlats(tonic, minor)) + (minor ? " minor" : " major");
    }

    static String keyShort(int tonic, boolean minor) {
        return noteName(tonic, useFlats(tonic, minor)) + (minor ? "m" : "");
    }

    static int chordRoot(int tonic, boolean minor, int degree) {
        return (tonic + scale(minor)[degree]) % 12;
    }

    static int quality(boolean minor, int degree) {
        return minor ? MINOR_Q[degree] : MAJOR_Q[degree];
    }

    /** Interval (semitones) of the diatonic seventh above the chord root. */
    static int seventhInterval(boolean minor, int degree) {
        int[] s = scale(minor);
        return ((s[(degree + 6) % 7] - s[degree]) + 12) % 12;
    }

    static int[] intervals(boolean minor, int degree, boolean seventh) {
        int q = quality(minor, degree);
        int third = (q == MAJ) ? 4 : 3;
        int fifth = (q == DIM) ? 6 : 7;
        if (!seventh) return new int[]{0, third, fifth};
        return new int[]{0, third, fifth, seventhInterval(minor, degree)};
    }

    static String suffix(boolean minor, int degree, boolean seventh) {
        int q = quality(minor, degree);
        if (!seventh) {
            if (q == MAJ) return "";
            if (q == MIN) return "m";
            return "°";
        }
        int sv = seventhInterval(minor, degree);
        if (q == MAJ) return sv == 11 ? "maj7" : "7";
        if (q == MIN) return sv == 10 ? "m7" : "mMaj7";
        return sv == 10 ? "m7♭5" : "°7";
    }

    static String chordName(int tonic, boolean minor, int degree, boolean seventh) {
        boolean flats = useFlats(tonic, minor);
        return noteName(chordRoot(tonic, minor, degree), flats) + suffix(minor, degree, seventh);
    }

    static String roman(boolean minor, int degree, boolean seventh) {
        String base = minor ? MINOR_ROMAN[degree] : MAJOR_ROMAN[degree];
        if (!seventh) return base;
        int q = quality(minor, degree);
        int sv = seventhInterval(minor, degree);
        if (q == DIM) return base.replace("°", "ø") + "7";
        if (q == MAJ && sv == 11) return base + "maj7";
        return base + "7";
    }

    /** MIDI notes for a playable voicing: bass root plus a close voicing around C3-C4. */
    static int[] midiNotes(int tonic, boolean minor, int degree, boolean seventh) {
        int root = chordRoot(tonic, minor, degree);
        int base = 48 + root;
        if (base > 54) base -= 12;
        int[] iv = intervals(minor, degree, seventh);
        int[] out = new int[iv.length + 1];
        out[0] = base - 12;
        for (int i = 0; i < iv.length; i++) out[i + 1] = base + iv[i];
        return out;
    }

    static int[] suggestions(boolean minor, int lastDegree) {
        if (lastDegree < 0 || lastDegree > 6) return new int[0];
        return minor ? SUGGEST_MINOR[lastDegree] : SUGGEST_MAJOR[lastDegree];
    }

    /** One chord in a progression, stored as a scale degree so it transposes with the key. */
    static final class Item {
        int degree;
        boolean seventh;

        Item(int degree, boolean seventh) {
            this.degree = degree;
            this.seventh = seventh;
        }

        Item copy() {
            return new Item(degree, seventh);
        }
    }

    static String encode(List<Item> items) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < items.size(); i++) {
            if (i > 0) sb.append(' ');
            Item it = items.get(i);
            sb.append(it.degree);
            if (it.seventh) sb.append('s');
        }
        return sb.toString();
    }

    static List<Item> decode(String s) {
        List<Item> out = new ArrayList<>();
        if (s == null) return out;
        String[] parts = s.trim().split("\\s+");
        for (String p : parts) {
            if (p.length() == 0) continue;
            boolean sev = p.endsWith("s");
            String num = sev ? p.substring(0, p.length() - 1) : p;
            try {
                int d = Integer.parseInt(num);
                if (d >= 0 && d <= 6) out.add(new Item(d, sev));
            } catch (NumberFormatException ignored) {
            }
        }
        return out;
    }

    static String romanLine(boolean minor, List<Item> items) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < items.size(); i++) {
            if (i > 0) sb.append(" – ");
            sb.append(roman(minor, items.get(i).degree, items.get(i).seventh));
        }
        return sb.toString();
    }

    static String chordLine(int tonic, boolean minor, List<Item> items) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < items.size(); i++) {
            if (i > 0) sb.append(" – ");
            sb.append(chordName(tonic, minor, items.get(i).degree, items.get(i).seventh));
        }
        return sb.toString();
    }
}
