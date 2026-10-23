/*
 * Copyright (c) 2026 Xena Studios
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package co.xenastudios.neoskript.frontend.parse;

import java.util.Locale;
import java.util.regex.Pattern;

/** Recognizes the literals every Skript script can use, whatever syntax is registered. */
final class Literals {

    private static final Pattern INTEGER = Pattern.compile("[-+]?\\d+(_\\d+)*");
    /** Decimals, percentages, and scientific notation ({@code 1.23e+4}). */
    private static final Pattern DECIMAL = Pattern.compile("[-+]?(\\d+(_\\d+)*)?\\.\\d+(_\\d+)*|[-+]?\\d+(_\\d+)*(\\.\\d+(_\\d+)*)?%"
            + "|[-+]?(\\d+(_\\d+)*(\\.\\d+(_\\d+)*)?|\\.\\d+(_\\d+)*)[eE][-+]?\\d+");
    private static final Pattern BOOLEAN = Pattern.compile("true|false|yes|no|on|off", Pattern.CASE_INSENSITIVE);

    private static final String AMOUNT = "(?:\\d+(?:\\.\\d+)?|an?|one|two|three|four|five|six|seven|eight|nine|ten)";
    private static final String UNIT =
            "(?:(?:real|rl|irl|minecraft|mc) )?(?:ticks?|seconds?|minutes?|hours?|days?|weeks?|months?|years?)";
    private static final String PART = AMOUNT + " ?" + UNIT;
    private static final Pattern TIMESPAN =
            Pattern.compile(PART + "(?:(?:,? and |, | )" + PART + ")*", Pattern.CASE_INSENSITIVE);
    private static final Pattern TIME = Pattern.compile(
            "\\d{1,2}:\\d{2}(?: ?[ap]\\.?m\\.?)?|\\d{1,2} ?[ap]\\.?m\\.?", Pattern.CASE_INSENSITIVE);
    /** Text that might name something: words, digits, and the punctuation item and entity names use. */
    private static final Pattern WORDS =
            Pattern.compile("(?=[^\\[]*\\p{L})[\\p{L}\\p{N}_][\\p{L}\\p{N}_'.:\\- ]*(\\[[\\p{L}\\p{N}_=,;:. ]*\\])?");
    /**
     * {@code value (type)}, a literal whose type is spelled out: {@code black (wolf variant)}. The space before
     * the bracket keeps {@code f(player)} a function call.
     */
    static final Pattern SPECIFIED = Pattern.compile("(.+?)\\s+\\(\\s*([\\p{L} ]+?)\\s*\\)");

    private Literals() {}

    /** The type of a number literal ({@code integer} or {@code number}), or {@code null} if it isn't one. */
    static String numberType(String text) {
        if (INTEGER.matcher(text).matches()) {
            return "integer";
        }
        return DECIMAL.matcher(text).matches() ? "number" : null;
    }

    static boolean isBoolean(String text) {
        return BOOLEAN.matcher(text).matches();
    }

    static boolean isTimespan(String text) {
        return TIMESPAN.matcher(text).matches();
    }

    static boolean isTime(String text) {
        return TIME.matcher(text).matches();
    }

    static boolean isWords(String text) {
        return WORDS.matcher(text).matches();
    }

    /** Lower case, single-spaced, without a leading article: how closed-set literals are looked up. */
    static String normalize(String text) {
        String lower = text.strip().toLowerCase(Locale.ROOT);
        if (lower.indexOf("  ") >= 0 || lower.indexOf('\t') >= 0) {
            lower = lower.replaceAll("\\s+", " ");
        }
        for (String article : new String[] {"the ", "an ", "a "}) {
            if (lower.startsWith(article)) {
                return lower.substring(article.length());
            }
        }
        return lower;
    }
}
