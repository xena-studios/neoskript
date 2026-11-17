/*
 * Copyright (c) 2026 Xena Studios
 * SPDX-License-Identifier: GPL-3.0-only
 */
package co.xenastudios.neoskript.addons.db;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Keeps passwords out of what NeoSkriptDb says: a database's URL as shown, and errors that may quote it.
 */
final class Secrets {

    private static final Pattern PASSWORD_PARAMETER = Pattern.compile("(?i)([?&;](?:password|pass|pwd)=)([^&;]*)");
    private static final Pattern USER_INFO = Pattern.compile("(//[^/@:]+:)([^/@]*)@");

    private Secrets() {}

    /**
     * The URL with its passwords as {@code ***}: in a {@code password=} parameter and in {@code user:password@}.
     *
     * @param url a JDBC URL
     * @return it, masked
     */
    static String mask(String url) {
        String masked = PASSWORD_PARAMETER.matcher(url).replaceAll("$1***");
        return USER_INFO.matcher(masked).replaceAll("$1***@");
    }

    /**
     * {@code message} with each of the URL's passwords replaced by {@code ***}, and the URL itself masked, for an
     * error that quotes either.
     *
     * @param message what a driver said
     * @param url the URL whose passwords to hide
     * @return the message, masked
     */
    static String hide(String message, String url) {
        if (message == null) {
            return null;
        }
        String hidden = message.replace(url, mask(url));
        for (Pattern pattern : new Pattern[] {PASSWORD_PARAMETER, USER_INFO}) {
            Matcher matcher = pattern.matcher(url);
            while (matcher.find()) {
                String secret = java.net.URLDecoder.decode(matcher.group(2), java.nio.charset.StandardCharsets.UTF_8);
                for (String form : new String[] {matcher.group(2), secret}) {
                    if (form.length() >= 3) {
                        hidden = hidden.replace(form, "***");
                    }
                }
            }
        }
        return hidden;
    }
}
