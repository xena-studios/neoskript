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
                    // Every length: a short password is still one, and garbling a message beats showing it.
                    if (!form.isEmpty() && !form.equals("***")) {
                        hidden = hidden.replace(form, "***");
                    }
                }
            }
        }
        return hidden;
    }

    /**
     * A URL without its passwords, and the password and user it had: so a pool, which logs its URL when debugging,
     * is given the password on its own, which it never logs.
     *
     * @param url the URL without passwords
     * @param user the user named before {@code @}, or {@code null} (one in a {@code user=} parameter stays there)
     * @param password the password, or {@code null}
     */
    record Split(String url, String user, String password) {}

    /**
     * Takes the passwords out of a JDBC URL: {@code user:password@} and a {@code password=} parameter.
     *
     * @param url a JDBC URL
     * @return the URL without them, and what was taken out
     */
    static Split split(String url) {
        String user = null;
        String password = null;
        String rest = url;
        Matcher info = USER_INFO.matcher(rest);
        if (info.find()) {
            user = decode(info.group(1).substring(2, info.group(1).length() - 1));
            password = decode(info.group(2));
            rest = rest.substring(0, info.start()) + "//" + rest.substring(info.end());
        }
        int query = rest.indexOf('?');
        if (query >= 0) {
            StringBuilder kept = new StringBuilder();
            for (String pair : rest.substring(query + 1).split("&", -1)) {
                String key = pair.contains("=") ? pair.substring(0, pair.indexOf('=')) : pair;
                if (key.equalsIgnoreCase("password") || key.equalsIgnoreCase("pass") || key.equalsIgnoreCase("pwd")) {
                    password = decode(pair.substring(pair.indexOf('=') + 1));
                } else if (!pair.isEmpty()) {
                    kept.append(kept.isEmpty() ? "" : "&").append(pair);
                }
            }
            rest = rest.substring(0, query) + (kept.isEmpty() ? "" : "?" + kept);
        }
        return new Split(rest, user, password);
    }

    private static String decode(String text) {
        try {
            return java.net.URLDecoder.decode(text, java.nio.charset.StandardCharsets.UTF_8);
        } catch (IllegalArgumentException notEncoded) {
            return text;
        }
    }
}
