package it.pepita.core.util;

import java.util.Locale;

/** Formattazione di numeri e tempi. */
public final class Fmt {
    private Fmt() {}

    private static final String[] SUF = {"", "K", "M", "B", "T", "Qa", "Qi", "Sx", "Sp", "Oc", "No", "Dc", "Ud", "Dd", "Td"};

    public static String num(double v) {
        if (Double.isNaN(v)) return "0";
        if (Double.isInfinite(v)) return "∞";
        boolean neg = v < 0;
        v = Math.abs(v);
        int i = 0;
        while (v >= 1000 && i < SUF.length - 1) {
            v /= 1000;
            i++;
        }
        String s;
        if (i == 0) {
            if (v >= 100 || v == Math.floor(v)) s = String.valueOf((long) Math.floor(v));
            else s = trim(String.format(Locale.US, "%.1f", v));
        } else {
            String f = v >= 100 ? "%.0f" : v >= 10 ? "%.1f" : "%.2f";
            s = trim(String.format(Locale.US, f, Math.floor(v * 100) / 100.0)) + SUF[i];
        }
        return (neg ? "-" : "") + s;
    }

    public static String mult(double v) {
        return "x" + trim(String.format(Locale.US, "%.2f", v));
    }

    public static String pct(double v) {
        return trim(String.format(Locale.US, "%.1f", v * 100)) + "%";
    }

    private static String trim(String s) {
        if (s.contains(".")) {
            s = s.replaceAll("0+$", "");
            if (s.endsWith(".")) s = s.substring(0, s.length() - 1);
        }
        return s;
    }

    /** Durata leggibile: 1h 02m, 4:05 */
    public static String time(long seconds) {
        if (seconds < 0) seconds = 0;
        long h = seconds / 3600, m = (seconds % 3600) / 60, s = seconds % 60;
        if (h > 0) return h + "h " + String.format(Locale.US, "%02dm", m);
        return m + ":" + String.format(Locale.US, "%02d", s);
    }

    /** Legge numeri come 1500, 1.5k, 2m, 3b. Ritorna -1 se non valido. */
    public static double parse(String s) {
        if (s == null || s.isEmpty()) return -1;
        s = s.trim().toLowerCase(Locale.ROOT).replace(",", ".");
        double mul = 1;
        String[] sufs = {"k", "m", "b", "t", "qa", "qi"};
        for (int i = sufs.length - 1; i >= 0; i--) {
            if (s.endsWith(sufs[i])) {
                mul = Math.pow(1000, i + 1);
                s = s.substring(0, s.length() - sufs[i].length());
                break;
            }
        }
        try {
            double v = Double.parseDouble(s) * mul;
            return v < 0 || Double.isNaN(v) || Double.isInfinite(v) ? -1 : v;
        } catch (NumberFormatException e) {
            return -1;
        }
    }

    public static String roman(int n) {
        if (n <= 0) return "0";
        int[] v = {1000, 900, 500, 400, 100, 90, 50, 40, 10, 9, 5, 4, 1};
        String[] r = {"M", "CM", "D", "CD", "C", "XC", "L", "XL", "X", "IX", "V", "IV", "I"};
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < v.length; i++) while (n >= v[i]) {
            n -= v[i];
            sb.append(r[i]);
        }
        return sb.toString();
    }
}
