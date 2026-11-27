package com.dailyapps.sunpath;

import android.content.SharedPreferences;

import java.util.ArrayList;
import java.util.List;
import java.util.TimeZone;

/** A named location with its time zone, plus saving and presets. */
final class Place {
    final String name;
    final double lat;
    final double lon;
    final String tz;

    Place(String name, double lat, double lon, String tz) {
        this.name = name;
        this.lat = lat;
        this.lon = lon;
        this.tz = tz;
    }

    TimeZone timeZone() {
        return TimeZone.getTimeZone(tz);
    }

    String encode() {
        return clean(name) + "|" + lat + "|" + lon + "|" + tz;
    }

    static String clean(String s) {
        return s.replace("|", " ").replace("\n", " ").trim();
    }

    static Place decode(String line) {
        try {
            String[] p = line.split("\\|");
            if (p.length < 4) return null;
            return new Place(p[0], Double.parseDouble(p[1]), Double.parseDouble(p[2]), p[3]);
        } catch (Exception e) {
            return null;
        }
    }

    String coords() {
        return String.format(java.util.Locale.US, "%.3f°%s, %.3f°%s",
                Math.abs(lat), lat >= 0 ? "N" : "S", Math.abs(lon), lon >= 0 ? "E" : "W");
    }

    static final String[] PRESETS = {
            "London|51.5074|-0.1278|Europe/London",
            "Edinburgh|55.9533|-3.1883|Europe/London",
            "Douglas, Isle of Man|54.1523|-4.4861|Europe/Isle_of_Man",
            "Dublin|53.3498|-6.2603|Europe/Dublin",
            "Paris|48.8566|2.3522|Europe/Paris",
            "Amsterdam|52.3676|4.9041|Europe/Amsterdam",
            "Berlin|52.52|13.405|Europe/Berlin",
            "Madrid|40.4168|-3.7038|Europe/Madrid",
            "Lisbon|38.7223|-9.1393|Europe/Lisbon",
            "Rome|41.9028|12.4964|Europe/Rome",
            "Athens|37.9838|23.7275|Europe/Athens",
            "Stockholm|59.3293|18.0686|Europe/Stockholm",
            "Oslo|59.9139|10.7522|Europe/Oslo",
            "Helsinki|60.1699|24.9384|Europe/Helsinki",
            "Reykjavik|64.1466|-21.9426|Atlantic/Reykjavik",
            "Tromsø|69.6492|18.9553|Europe/Oslo",
            "Istanbul|41.0082|28.9784|Europe/Istanbul",
            "Moscow|55.7558|37.6173|Europe/Moscow",
            "Cairo|30.0444|31.2357|Africa/Cairo",
            "Nairobi|-1.2921|36.8219|Africa/Nairobi",
            "Cape Town|-33.9249|18.4241|Africa/Johannesburg",
            "Dubai|25.2048|55.2708|Asia/Dubai",
            "Mumbai|19.076|72.8777|Asia/Kolkata",
            "Delhi|28.6139|77.209|Asia/Kolkata",
            "Singapore|1.3521|103.8198|Asia/Singapore",
            "Hong Kong|22.3193|114.1694|Asia/Hong_Kong",
            "Shanghai|31.2304|121.4737|Asia/Shanghai",
            "Seoul|37.5665|126.978|Asia/Seoul",
            "Tokyo|35.6762|139.6503|Asia/Tokyo",
            "Perth|-31.9505|115.8605|Australia/Perth",
            "Sydney|-33.8688|151.2093|Australia/Sydney",
            "Melbourne|-37.8136|144.9631|Australia/Melbourne",
            "Auckland|-36.8485|174.7633|Pacific/Auckland",
            "Honolulu|21.3069|-157.8583|Pacific/Honolulu",
            "Anchorage|61.2181|-149.9003|America/Anchorage",
            "Vancouver|49.2827|-123.1207|America/Vancouver",
            "San Francisco|37.7749|-122.4194|America/Los_Angeles",
            "Los Angeles|34.0522|-118.2437|America/Los_Angeles",
            "Denver|39.7392|-104.9903|America/Denver",
            "Chicago|41.8781|-87.6298|America/Chicago",
            "Toronto|43.6532|-79.3832|America/Toronto",
            "New York|40.7128|-74.006|America/New_York",
            "Mexico City|19.4326|-99.1332|America/Mexico_City",
            "Bogotá|4.711|-74.0721|America/Bogota",
            "Lima|-12.0464|-77.0428|America/Lima",
            "São Paulo|-23.5505|-46.6333|America/Sao_Paulo",
            "Buenos Aires|-34.6037|-58.3816|America/Argentina/Buenos_Aires",
            "Ushuaia|-54.8019|-68.303|America/Argentina/Ushuaia",
    };

    static List<Place> presets() {
        List<Place> out = new ArrayList<>();
        for (String s : PRESETS) {
            Place p = decode(s);
            if (p != null) out.add(p);
        }
        return out;
    }

    static List<Place> load(SharedPreferences prefs) {
        List<Place> out = new ArrayList<>();
        String all = prefs.getString("places", null);
        if (all != null) {
            for (String line : all.split("\n")) {
                Place p = decode(line);
                if (p != null) out.add(p);
            }
        }
        if (out.isEmpty()) {
            String dev = TimeZone.getDefault().getID();
            Place pick = null;
            for (Place p : presets()) {
                if (p.tz.equals(dev)) { pick = p; break; }
            }
            if (pick == null) pick = decode(PRESETS[0]);
            out.add(pick);
        }
        return out;
    }

    static void save(SharedPreferences prefs, List<Place> places) {
        StringBuilder sb = new StringBuilder();
        for (Place p : places) {
            if (sb.length() > 0) sb.append('\n');
            sb.append(p.encode());
        }
        prefs.edit().putString("places", sb.toString()).apply();
    }
}
