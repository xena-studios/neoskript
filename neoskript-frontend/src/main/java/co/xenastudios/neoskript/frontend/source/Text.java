/*
 * Copyright (c) 2026 Xena Studios
 * SPDX-License-Identifier: GPL-3.0-only
 */
package co.xenastudios.neoskript.frontend.source;

import java.util.Locale;

/** Text helpers for the parser's hot paths. */
public final class Text {

    private Text() {}

    /**
     * {@code text.toLowerCase(Locale.ROOT)}, with a fast path for ASCII. In the browser parser (TeaVM) the
     * locale-aware call goes through the browser's locale machinery each time, which made it the parser's
     * biggest cost.
     */
    public static String lower(String text) {
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (c >= 0x80) {
                return text.toLowerCase(Locale.ROOT);
            }
            if (c >= 'A' && c <= 'Z') {
                char[] chars = text.toCharArray();
                for (int j = i; j < chars.length; j++) {
                    char d = chars[j];
                    if (d >= 0x80) {
                        return text.toLowerCase(Locale.ROOT);
                    }
                    if (d >= 'A' && d <= 'Z') {
                        chars[j] = (char) (d + ('a' - 'A'));
                    }
                }
                return new String(chars);
            }
        }
        return text;
    }
}
