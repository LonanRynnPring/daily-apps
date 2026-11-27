package com.dailyapps.walksteps;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Turns shared or pasted walking directions (for example Google Maps
 * "Share directions" text) into short road-name steps.
 */
final class Parser {
    static final String START = "START", LEFT = "LEFT", RIGHT = "RIGHT",
            SLIGHT_LEFT = "SLIGHT_LEFT", SLIGHT_RIGHT = "SLIGHT_RIGHT",
            SHARP_LEFT = "SHARP_LEFT", SHARP_RIGHT = "SHARP_RIGHT",
            STRAIGHT = "STRAIGHT", ROUNDABOUT = "ROUNDABOUT", UTURN = "UTURN",
            ARRIVE = "ARRIVE", OTHER = "OTHER";

    static final class Step {
        String kind = OTHER;
        String text = "";      // simplified instruction
        String road = "";      // main road name, may be empty
        String distance = "";  // e.g. "200 m"
        String note = "";      // landmark etc.
        String original = "";
    }

    static final class Route {
        String title = "";
        String summary = "";
        boolean sawLink;
        final List<Step> steps = new ArrayList<>();
    }

    private Parser() {}

    private static final int CI = Pattern.CASE_INSENSITIVE;
    private static final String DIST = "(?:\\d+(?:[.,]\\d+)?\\s*(?:km|m|mi|miles?|ft|feet|yd|yards?|metres?|meters?|kilometres?|kilometers?))";
    private static final Pattern DIST_ONLY = Pattern.compile("^\\(?\\s*(?:go\\s+|walk\\s+|for\\s+)?(" + DIST + ")\\s*\\)?\\.?$", CI);
    private static final Pattern TRAIL_DIST = Pattern.compile("^(.*?)[\\s,\\-–(]+(" + DIST + ")\\s*\\)?\\.?$", CI);
    private static final Pattern TIME = Pattern.compile("\\b\\d+\\s*(?:min|mins|minutes?|hr|hrs|hours?|h)\\b", CI);
    private static final Pattern NUMBERING = Pattern.compile("^\\s*(?:\\d+\\s*[.)](?!\\d)|[-*•·>])\\s*");
    private static final Pattern URL = Pattern.compile("https?://\\S+|\\b(?:maps\\.app\\.goo\\.gl|goo\\.gl/maps)\\S*", CI);
    private static final Pattern TITLE = Pattern.compile("^(?:walking\\s+)?directions\\s+(?:from\\s+(.+?)\\s+)?to\\s+(.+)$", CI);
    private static final Pattern STEP_START = Pattern.compile(
            "^(head|turn|slight|sharp|keep|bear|continue|at the roundabout|at the traffic circle|at the rotary|take|make a u-turn|u-turn|destination|your destination|arrive|walk|go|cross|enter|exit|leave|merge|use|follow|climb|descend|proceed)\\b", CI);
    private static final Pattern PASS = Pattern.compile("^pass(?:\\s+by)?\\s+(.+)$", CI);

    private static final Pattern P_DEST = Pattern.compile("(?:your\\s+)?destination\\s+(?:will\\s+be|is)\\s+on\\s+the\\s+(left|right)", CI);
    private static final Pattern P_ARRIVE = Pattern.compile("^arrive\\s+(?:at\\s+)?(.+)$", CI);
    private static final Pattern P_UTURN = Pattern.compile("^(?:make\\s+a\\s+)?u-?turn(.*)$", CI);
    private static final Pattern P_ROUND = Pattern.compile("^at the (?:roundabout|traffic circle|rotary),?\\s*(?:take the\\s+(\\w+)\\s+exit|(continue straight|go straight|continue))?(.*)$", CI);
    private static final Pattern P_HEAD = Pattern.compile("^head\\s+(north(?:east|west)?|south(?:east|west)?|east|west)(.*)$", CI);
    private static final Pattern P_TURN = Pattern.compile("^(turn|slight|sharp|keep|bear)\\s+(left|right)\\b,?(.*)$", CI);
    private static final Pattern P_CONT = Pattern.compile("^continue(?:\\s+straight)?(.*)$", CI);
    private static final Pattern P_ONTO = Pattern.compile("\\b(?:onto|into)\\s+(.+)$", CI);
    private static final Pattern P_STAY = Pattern.compile("\\bto stay on\\s+(.+)$", CI);
    private static final Pattern P_ON = Pattern.compile("^\\s*(?:on|along)\\s+(.+)$", CI);
    private static final Pattern P_TOWARD = Pattern.compile("\\s*\\btowards?\\s+(.+)$", CI);
    private static final Pattern P_AT = Pattern.compile("^\\s*(at|after|before|past|through)\\s+(.+)$", CI);

    static Route parse(String text, String subject) {
        Route r = new Route();
        if (subject != null) {
            Matcher m = TITLE.matcher(subject.trim());
            if (m.find()) r.title = clean(m.group(2));
        }
        if (text == null) return r;
        text = text.replace("\r", "\n").replace(" ", " ").replace("‎", "").replace("‏", "");
        Step last = null;
        for (String raw : text.split("\n")) {
            String line = raw.trim();
            if (line.isEmpty()) continue;
            if (URL.matcher(line).find()) {
                r.sawLink = true;
                line = URL.matcher(line).replaceAll("").trim();
                if (line.isEmpty()) continue;
            }
            line = NUMBERING.matcher(line).replaceFirst("").trim();
            if (line.isEmpty()) continue;

            Matcher tm = TITLE.matcher(line);
            if (tm.find()) {
                if (r.title.isEmpty()) r.title = clean(tm.group(2));
                continue;
            }
            Matcher dm = DIST_ONLY.matcher(line);
            if (dm.find()) {
                if (last != null && last.distance.isEmpty()) last.distance = tidyDist(dm.group(1));
                else if (last == null && r.summary.isEmpty()) r.summary = line;
                continue;
            }
            if (TIME.matcher(line).find() && line.length() < 40 && !STEP_START.matcher(line).find()) {
                if (r.summary.isEmpty()) r.summary = line.replaceAll("[()]", "").trim();
                continue;
            }
            String lower = line.toLowerCase(Locale.ROOT);
            if (lower.equals("walk") || lower.equals("walking") || lower.startsWith("restricted usage")
                    || lower.startsWith("these directions") || lower.startsWith("use caution")
                    || lower.startsWith("map data") || lower.startsWith("©")) {
                continue;
            }
            Matcher pm = PASS.matcher(line);
            if (pm.find() && last != null) {
                String n = "Pass " + clean(pm.group(1));
                last.note = last.note.isEmpty() ? n : last.note + "; " + n;
                continue;
            }
            if (!STEP_START.matcher(line).find()) {
                // Unknown line: before any step it may be a place name; otherwise keep as an instruction
                if (last == null) {
                    if (r.title.isEmpty() && line.length() < 60 && !line.contains(":")) r.title = clean(line);
                    continue;
                }
                if (line.length() > 120) continue;
            }
            // Split off trailing distance on the same line
            String dist = "";
            Matcher td = TRAIL_DIST.matcher(line);
            if (td.find() && td.group(1).trim().length() > 3) {
                line = td.group(1).trim();
                dist = tidyDist(td.group(2));
            }
            Step s = simplify(line);
            s.distance = dist;
            r.steps.add(s);
            last = s;
        }
        if (r.title.isEmpty()) {
            for (Step s : r.steps) {
                if (ARRIVE.equals(s.kind) && s.text.startsWith("Arrive at ")) r.title = s.road;
            }
        }
        return r;
    }

    static String tidyDist(String d) {
        return d.trim().replaceAll("\\s+", " ").replaceAll("(?i)(\\d)(km|m|mi|ft|yd)$", "$1 $2");
    }

    static String clean(String s) {
        if (s == null) return "";
        s = s.trim();
        while (s.endsWith(".") || s.endsWith(",")) s = s.substring(0, s.length() - 1).trim();
        s = s.replaceAll("(?i)\\s*restricted usage road\\s*", " ").trim();
        return s;
    }

    /** Expands a road-type abbreviation in the last word: "Mill Ln" becomes "Mill Lane". */
    static String expandRoad(String road) {
        if (road == null || road.isEmpty()) return road;
        String[][] map = {{"Rd", "Road"}, {"St", "Street"}, {"Ln", "Lane"}, {"Ave", "Avenue"}, {"Av", "Avenue"},
                {"Dr", "Drive"}, {"Cl", "Close"}, {"Ct", "Court"}, {"Pl", "Place"}, {"Sq", "Square"},
                {"Cres", "Crescent"}, {"Gdns", "Gardens"}, {"Gr", "Grove"}, {"Pk", "Park"}, {"Ter", "Terrace"},
                {"Terr", "Terrace"}, {"Blvd", "Boulevard"}, {"Hwy", "Highway"}, {"Pkwy", "Parkway"},
                {"Mt", "Mount"}, {"Wy", "Way"}, {"Brg", "Bridge"}, {"Hl", "Hill"}};
        String[] parts = road.split(" ");
        if (parts.length < 2) return road;
        String lastW = parts[parts.length - 1].replace(".", "");
        for (String[] e : map) {
            if (lastW.equals(e[0])) {
                parts[parts.length - 1] = e[1];
                return String.join(" ", parts);
            }
        }
        return road;
    }

    private static String ordinal(String w) {
        String l = w.toLowerCase(Locale.ROOT);
        switch (l) {
            case "first": return "1st";
            case "second": return "2nd";
            case "third": return "3rd";
            case "fourth": return "4th";
            case "fifth": return "5th";
            case "sixth": return "6th";
            default: return w;
        }
    }

    private static String cap(String s) {
        if (s.isEmpty()) return s;
        return Character.toUpperCase(s.charAt(0)) + s.substring(1);
    }

    /** Pulls a road name out of the rest of an instruction; returns {road, towards, how}. */
    private static String[] roadFrom(String rest) {
        String road = "", toward = "", how = "";
        Matcher m;
        if ((m = P_STAY.matcher(rest)).find()) {
            road = m.group(1);
            how = "stay";
        } else if ((m = P_ONTO.matcher(rest)).find()) {
            road = m.group(1);
            how = "onto";
        } else if ((m = P_ON.matcher(rest)).find()) {
            road = m.group(1);
            how = "on";
        }
        String src = road.isEmpty() ? rest : road;
        Matcher t = P_TOWARD.matcher(src);
        if (t.find()) {
            toward = clean(t.group(1));
            src = src.substring(0, t.start());
            if (road.isEmpty()) rest = src; else road = src;
        }
        if (road.isEmpty() && toward.isEmpty()) {
            Matcher a = P_AT.matcher(rest);
            if (a.find()) {
                how = a.group(1).toLowerCase(Locale.ROOT);
                road = a.group(2);
            }
        }
        return new String[]{clean(road), toward, how};
    }

    static Step simplify(String line) {
        Step s = new Step();
        s.original = line;
        line = clean(line);
        Matcher m;

        if ((m = P_DEST.matcher(line)).find()) {
            s.kind = ARRIVE;
            String side = m.group(1).toLowerCase(Locale.ROOT);
            String before = clean(line.substring(0, m.start()));
            if (!before.isEmpty() && STEP_START.matcher(before).find()) {
                Step inner = simplify(before);
                s.road = inner.road;
                s.text = inner.text + ", then arrive on the " + side;
            } else {
                s.text = "Arrive — it's on your " + side;
            }
            return s;
        }
        if ((m = P_ARRIVE.matcher(line)).find()) {
            s.kind = ARRIVE;
            s.road = clean(m.group(1));
            s.text = "Arrive at " + s.road;
            return s;
        }
        if ((m = P_UTURN.matcher(line)).find()) {
            s.kind = UTURN;
            String[] rd = roadFrom(m.group(1));
            s.road = rd[0];
            s.text = "U-turn" + (s.road.isEmpty() ? "" : " onto " + s.road);
            return s;
        }
        if ((m = P_ROUND.matcher(line)).find()) {
            s.kind = ROUNDABOUT;
            String[] rd = roadFrom(m.group(3) == null ? "" : m.group(3));
            s.road = rd[0];
            if (m.group(1) != null) {
                s.text = "Roundabout: take the " + ordinal(m.group(1)) + " exit";
            } else {
                s.text = "Roundabout: go straight across";
            }
            if (!s.road.isEmpty()) s.text += ("stay".equals(rd[2]) ? " to stay on " : " onto ") + s.road;
            else if (!rd[1].isEmpty()) s.text += " towards " + rd[1];
            return s;
        }
        if ((m = P_HEAD.matcher(line)).find()) {
            s.kind = START;
            String dir = m.group(1).toLowerCase(Locale.ROOT);
            String[] rd = roadFrom(m.group(2));
            s.road = rd[0];
            if (!s.road.isEmpty()) {
                s.text = "Start on " + s.road + ", heading " + dir;
                if (!rd[1].isEmpty()) s.text += " towards " + rd[1];
            } else if (!rd[1].isEmpty()) {
                s.text = "Head " + dir + " towards " + rd[1];
            } else {
                s.text = "Head " + dir;
            }
            return s;
        }
        if ((m = P_TURN.matcher(line)).find()) {
            String verb = m.group(1).toLowerCase(Locale.ROOT);
            boolean left = m.group(2).equalsIgnoreCase("left");
            String word;
            if (verb.equals("slight") || verb.equals("bear")) {
                s.kind = left ? SLIGHT_LEFT : SLIGHT_RIGHT;
                word = "Bear";
            } else if (verb.equals("sharp")) {
                s.kind = left ? SHARP_LEFT : SHARP_RIGHT;
                word = "Sharp";
            } else if (verb.equals("keep")) {
                s.kind = left ? SLIGHT_LEFT : SLIGHT_RIGHT;
                word = "Keep";
            } else {
                s.kind = left ? LEFT : RIGHT;
                word = "Turn";
            }
            String[] rd = roadFrom(m.group(3));
            s.road = rd[0];
            s.text = word + (left ? " left" : " right");
            if (!s.road.isEmpty()) {
                switch (rd[2]) {
                    case "stay": s.text += " to stay on " + s.road; break;
                    case "onto": case "on": s.text += " onto " + s.road; break;
                    default: s.text += " " + rd[2] + " " + s.road; s.road = ""; break;
                }
            }
            if (!rd[1].isEmpty()) s.text += " towards " + rd[1];
            return s;
        }
        if ((m = P_CONT.matcher(line)).find()) {
            s.kind = STRAIGHT;
            String[] rd = roadFrom(m.group(1));
            s.road = rd[0];
            if (!s.road.isEmpty()) {
                s.text = ("stay".equals(rd[2]) || "on".equals(rd[2]) ? "Carry on along " : "Continue onto ") + s.road;
                if (!rd[1].isEmpty()) s.text += " towards " + rd[1];
            } else if (!rd[1].isEmpty()) {
                s.text = "Carry straight on towards " + rd[1];
            } else {
                s.text = "Carry straight on";
            }
            return s;
        }
        s.kind = OTHER;
        s.text = cap(line);
        return s;
    }

    /** A very short form: arrow symbol plus road name. */
    static String compact(Step s) {
        String arrow;
        switch (s.kind) {
            case LEFT: arrow = "←"; break;
            case RIGHT: arrow = "→"; break;
            case SLIGHT_LEFT: arrow = "↖"; break;
            case SLIGHT_RIGHT: arrow = "↗"; break;
            case SHARP_LEFT: arrow = "↙"; break;
            case SHARP_RIGHT: arrow = "↘"; break;
            case STRAIGHT: case START: arrow = "↑"; break;
            case UTURN: arrow = "↶"; break;
            case ROUNDABOUT: arrow = "↻"; break;
            case ARRIVE: arrow = "⚑"; break;
            default: arrow = "•"; break;
        }
        if (s.road.isEmpty() || ARRIVE.equals(s.kind)) return arrow + "  " + s.text;
        String extra = "";
        if (ROUNDABOUT.equals(s.kind)) {
            Matcher m = Pattern.compile("(\\d\\w\\w) exit").matcher(s.text);
            if (m.find()) extra = m.group(1) + " exit → ";
        }
        return arrow + "  " + extra + s.road;
    }
}
