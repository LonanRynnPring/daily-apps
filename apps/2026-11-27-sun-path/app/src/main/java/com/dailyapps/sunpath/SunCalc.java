package com.dailyapps.sunpath;

/**
 * Sun and moon positions and sun event times.
 * Port of the well known suncalc formulas (Vladimir Agafonkin, BSD licence),
 * based on astronomy formulas from Astronomy Answers.
 */
final class SunCalc {
    static final double RAD = Math.PI / 180.0;
    static final double DAY_MS = 86400000.0;
    static final double J1970 = 2440588.0;
    static final double J2000 = 2451545.0;
    static final double E = RAD * 23.4397;
    static final long NONE = Long.MIN_VALUE;

    /** Altitude thresholds in degrees, indices used by Times.rise / Times.set. */
    static final double[] ANGLES = {-0.833, -4.0, -6.0, -12.0, -18.0, 6.0};
    static final int SUNRISE = 0, BLUE_GOLD = 1, CIVIL = 2, NAUTICAL = 3, ASTRO = 4, GOLDEN = 5;

    static final class Times {
        long noon;
        double noonAlt;
        final long[] rise = new long[ANGLES.length];
        final long[] set = new long[ANGLES.length];
    }

    private SunCalc() {}

    static double toJulian(long ms) { return ms / DAY_MS - 0.5 + J1970; }
    static long fromJulian(double j) { return Math.round((j + 0.5 - J1970) * DAY_MS); }
    static double toDays(long ms) { return toJulian(ms) - J2000; }

    static double rightAscension(double l, double b) {
        return Math.atan2(Math.sin(l) * Math.cos(E) - Math.tan(b) * Math.sin(E), Math.cos(l));
    }

    static double declination(double l, double b) {
        return Math.asin(Math.sin(b) * Math.cos(E) + Math.cos(b) * Math.sin(E) * Math.sin(l));
    }

    static double azimuth(double h, double phi, double dec) {
        return Math.atan2(Math.sin(h), Math.cos(h) * Math.sin(phi) - Math.tan(dec) * Math.cos(phi));
    }

    static double altitude(double h, double phi, double dec) {
        return Math.asin(Math.sin(phi) * Math.sin(dec) + Math.cos(phi) * Math.cos(dec) * Math.cos(h));
    }

    static double siderealTime(double d, double lw) {
        return RAD * (280.16 + 360.9856235 * d) - lw;
    }

    static double astroRefraction(double h) {
        if (h < 0) h = 0;
        return 0.0002967 / Math.tan(h + 0.00312536 / (h + 0.08901179));
    }

    static double solarMeanAnomaly(double d) {
        return RAD * (357.5291 + 0.98560028 * d);
    }

    static double eclipticLongitude(double m) {
        double c = RAD * (1.9148 * Math.sin(m) + 0.02 * Math.sin(2 * m) + 0.0003 * Math.sin(3 * m));
        double p = RAD * 102.9372;
        return m + c + p + Math.PI;
    }

    /** Returns {declination, rightAscension} in radians. */
    static double[] sunCoords(double d) {
        double m = solarMeanAnomaly(d);
        double l = eclipticLongitude(m);
        return new double[]{declination(l, 0), rightAscension(l, 0)};
    }

    /** Returns {altitude degrees, azimuth degrees clockwise from north}. */
    static double[] sunPosition(long ms, double lat, double lng) {
        double lw = RAD * -lng;
        double phi = RAD * lat;
        double d = toDays(ms);
        double[] c = sunCoords(d);
        double h = siderealTime(d, lw) - c[1];
        double az = azimuth(h, phi, c[0]);
        double alt = altitude(h, phi, c[0]);
        return new double[]{alt / RAD, norm360(az / RAD + 180.0)};
    }

    static double norm360(double a) {
        a = a % 360.0;
        if (a < 0) a += 360.0;
        return a;
    }

    private static final double J0 = 0.0009;

    static double julianCycle(double d, double lw) {
        return Math.round(d - J0 - lw / (2 * Math.PI));
    }

    static double approxTransit(double ht, double lw, double n) {
        return J0 + (ht + lw) / (2 * Math.PI) + n;
    }

    static double solarTransitJ(double ds, double m, double l) {
        return J2000 + ds + 0.0053 * Math.sin(m) - 0.0069 * Math.sin(2 * l);
    }

    static double hourAngle(double h, double phi, double d) {
        return Math.acos((Math.sin(h) - Math.sin(phi) * Math.sin(d)) / (Math.cos(phi) * Math.cos(d)));
    }

    /** Sun event times for the solar day nearest to the given moment (pass local noon). */
    static Times times(long ms, double lat, double lng) {
        double lw = RAD * -lng;
        double phi = RAD * lat;
        double d = toDays(ms);
        double n = julianCycle(d, lw);
        double ds = approxTransit(0, lw, n);
        double m = solarMeanAnomaly(ds);
        double l = eclipticLongitude(m);
        double dec = declination(l, 0);
        double jNoon = solarTransitJ(ds, m, l);

        Times t = new Times();
        t.noon = fromJulian(jNoon);
        t.noonAlt = sunPosition(t.noon, lat, lng)[0];
        for (int i = 0; i < ANGLES.length; i++) {
            double w = hourAngle(ANGLES[i] * RAD, phi, dec);
            if (Double.isNaN(w)) {
                t.rise[i] = NONE;
                t.set[i] = NONE;
                continue;
            }
            double a = approxTransit(w, lw, n);
            double jSet = solarTransitJ(a, m, l);
            double jRise = jNoon - (jSet - jNoon);
            t.rise[i] = fromJulian(jRise);
            t.set[i] = fromJulian(jSet);
        }
        return t;
    }

    /** Returns {ra, dec, distKm}. */
    static double[] moonCoords(double d) {
        double l = RAD * (218.316 + 13.176396 * d);
        double m = RAD * (134.963 + 13.064993 * d);
        double f = RAD * (93.272 + 13.229350 * d);
        double lon = l + RAD * 6.289 * Math.sin(m);
        double b = RAD * 5.128 * Math.sin(f);
        double dt = 385001 - 20905 * Math.cos(m);
        return new double[]{rightAscension(lon, b), declination(lon, b), dt};
    }

    /** Returns {altitude degrees (with refraction), azimuth degrees from north}. */
    static double[] moonPosition(long ms, double lat, double lng) {
        double lw = RAD * -lng;
        double phi = RAD * lat;
        double d = toDays(ms);
        double[] c = moonCoords(d);
        double h = siderealTime(d, lw) - c[0];
        double alt = altitude(h, phi, c[1]);
        alt = alt + astroRefraction(alt);
        double az = azimuth(h, phi, c[1]);
        return new double[]{alt / RAD, norm360(az / RAD + 180.0)};
    }

    /** Returns {illuminated fraction 0..1, phase 0..1 (0 new, 0.5 full)}. */
    static double[] moonIllumination(long ms) {
        double d = toDays(ms);
        double[] s = sunCoords(d); // dec, ra
        double[] m = moonCoords(d); // ra, dec, dist
        double sdist = 149598000;
        double sDec = s[0], sRa = s[1];
        double mRa = m[0], mDec = m[1];
        double phi = Math.acos(Math.sin(sDec) * Math.sin(mDec)
                + Math.cos(sDec) * Math.cos(mDec) * Math.cos(sRa - mRa));
        double inc = Math.atan2(sdist * Math.sin(phi), m[2] - sdist * Math.cos(phi));
        double angle = Math.atan2(Math.cos(sDec) * Math.sin(sRa - mRa),
                Math.sin(sDec) * Math.cos(mDec) - Math.cos(sDec) * Math.sin(mDec) * Math.cos(sRa - mRa));
        double fraction = (1 + Math.cos(inc)) / 2;
        double phase = 0.5 + 0.5 * inc * (angle < 0 ? -1 : 1) / Math.PI;
        return new double[]{fraction, phase};
    }

    /** Moonrise / moonset within [start, start+24h) found by sampling. Returns {rise, set} or NONE. */
    static long[] moonRiseSet(long start, double lat, double lng) {
        double h0 = 0.133;
        long rise = NONE, set = NONE;
        long step = 10 * 60000L;
        double prev = moonPosition(start, lat, lng)[0] - h0;
        for (long t = start + step; t <= start + 86400000L; t += step) {
            double cur = moonPosition(t, lat, lng)[0] - h0;
            if ((prev < 0) != (cur < 0)) {
                double frac = prev / (prev - cur);
                long at = t - step + Math.round(frac * step);
                if (cur >= 0 && rise == NONE) rise = at;
                if (cur < 0 && set == NONE) set = at;
            }
            prev = cur;
        }
        return new long[]{rise, set};
    }

    static String phaseName(double p) {
        if (p < 0.03 || p > 0.97) return "New moon";
        if (p < 0.22) return "Waxing crescent";
        if (p < 0.28) return "First quarter";
        if (p < 0.47) return "Waxing gibbous";
        if (p < 0.53) return "Full moon";
        if (p < 0.72) return "Waning gibbous";
        if (p < 0.78) return "Last quarter";
        return "Waning crescent";
    }

    private static final String[] WINDS = {"N", "NNE", "NE", "ENE", "E", "ESE", "SE", "SSE",
            "S", "SSW", "SW", "WSW", "W", "WNW", "NW", "NNW"};

    static String compassPoint(double az) {
        int i = (int) Math.round(norm360(az) / 22.5) % 16;
        return WINDS[i];
    }
}
