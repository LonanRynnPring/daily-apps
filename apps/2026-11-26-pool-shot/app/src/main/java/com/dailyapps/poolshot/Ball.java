package com.dailyapps.poolshot;

import android.graphics.Color;

/** A ball on the table, in table units: x along the long rail 0..2, y across 0..1. 0 = cue ball. */
final class Ball {
    float x, y;
    int num;

    Ball(int num, float x, float y) {
        this.num = num;
        this.x = x;
        this.y = y;
    }

    static final String[] NAMES = {
            "Cue ball", "1 · yellow", "2 · blue", "3 · red", "4 · purple",
            "5 · orange", "6 · green", "7 · maroon", "8 · black",
            "9 · yellow stripe", "10 · blue stripe", "11 · red stripe",
            "12 · purple stripe", "13 · orange stripe", "14 · green stripe",
            "15 · maroon stripe"
    };

    static int color(int num) {
        switch (num == 0 ? 0 : (num == 8 ? 8 : ((num - 1) % 8) + 1)) {
            case 0: return Color.rgb(248, 246, 236);
            case 1: return Color.rgb(245, 197, 24);
            case 2: return Color.rgb(31, 79, 191);
            case 3: return Color.rgb(214, 40, 40);
            case 4: return Color.rgb(106, 44, 145);
            case 5: return Color.rgb(247, 127, 0);
            case 6: return Color.rgb(19, 138, 54);
            case 7: return Color.rgb(123, 30, 30);
            default: return Color.rgb(15, 15, 15);
        }
    }

    boolean isStripe() {
        return num > 8;
    }

    String label() {
        return num == 0 ? "cue ball" : "#" + num;
    }
}
