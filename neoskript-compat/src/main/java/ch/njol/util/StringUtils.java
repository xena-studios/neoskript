/*
 * Copyright (c) 2026 Xena Studios
 * SPDX-License-Identifier: GPL-3.0-only
 */
package ch.njol.util;

import java.util.Locale;

/** Skript's text helpers that addons use. */
public abstract class StringUtils {

    public StringUtils() {}

    public static String join(Object[] strings) {
        return join(strings, "", 0, strings.length);
    }

    public static String join(Object[] strings, String delimiter) {
        return join(strings, delimiter, 0, strings.length);
    }

    public static String join(Object[] strings, String delimiter, int start, int end) {
        StringBuilder text = new StringBuilder();
        for (int i = start; i < end; i++) {
            if (i != start) {
                text.append(delimiter);
            }
            text.append(strings[i]);
        }
        return text.toString();
    }

    public static String join(Iterable<?> strings) {
        return join(strings, "");
    }

    public static String join(Iterable<?> strings, String delimiter) {
        StringBuilder text = new StringBuilder();
        boolean first = true;
        for (Object value : strings) {
            if (!first) {
                text.append(delimiter);
            }
            first = false;
            text.append(value);
        }
        return text.toString();
    }

    public static boolean contains(String haystack, String needle, boolean caseSensitive) {
        return caseSensitive ? haystack.contains(needle)
                : haystack.toLowerCase(Locale.ENGLISH).contains(needle.toLowerCase(Locale.ENGLISH));
    }

    public static boolean startsWithIgnoreCase(String string, String start) {
        return string.regionMatches(true, 0, start, 0, start.length());
    }

    public static boolean endsWithIgnoreCase(String string, String end) {
        return string.regionMatches(true, string.length() - end.length(), end, 0, end.length());
    }

    public static String toString(double d, int accuracy) {
        java.math.BigDecimal value = java.math.BigDecimal.valueOf(d).setScale(accuracy, java.math.RoundingMode.HALF_UP)
                .stripTrailingZeros();
        return value.scale() < 0 ? value.setScale(0).toPlainString() : value.toPlainString();
    }

    public static String fancyOrderNumber(int i) {
        int iModTen = i % 10;
        int iModHundred = i % 100;
        if (iModTen == 1 && iModHundred != 11) {
            return i + "st";
        }
        if (iModTen == 2 && iModHundred != 12) {
            return i + "nd";
        }
        if (iModTen == 3 && iModHundred != 13) {
            return i + "rd";
        }
        return i + "th";
    }

    public static byte[] hexStringToByteArray(String s) {
        byte[] data = new byte[s.length() / 2];
        for (int i = 0; i < data.length; i++) {
            data[i] = (byte) Integer.parseInt(s.substring(2 * i, 2 * i + 2), 16);
        }
        return data;
    }
}
