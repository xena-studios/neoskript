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

    private Secrets() {}

    /**
     * The URL with its passwords as {@code ***}: in a {@code password=} parameter and in {@code user:password@}.
     *
     * @param url a JDBC URL
     * @return it, masked
     */
    static String mask(String url) {
        int[] info = userInfo(url);
        String masked = info == null ? url : url.substring(0, info[1]) + "***" + url.substring(info[2]);
        return PASSWORD_PARAMETER.matcher(masked).replaceAll("$1***");
    }

    /**
     * Where {@code user:password@} is: {@code [the user's start, the password's start, the @]}, or {@code null}.
     * The password runs to the last {@code @} before the query, so one with an {@code @} or a {@code /} in it,
     * not encoded ({@code mysql://root:p@ss/w@host/db}), is still all of it. The user has neither.
     */
    private static int[] userInfo(String url) {
        int slashes = url.indexOf("//");
        if (slashes < 0) {
            return null;
        }
        int from = slashes + 2;
        int query = url.indexOf('?', from);
        int at = url.lastIndexOf('@', (query < 0 ? url.length() : query) - 1);
        int colon = url.indexOf(':', from);
        if (at < from || colon < 0 || colon > at || url.substring(from, colon).contains("/")
                || url.substring(from, colon).contains("@")) {
            return null;
        }
        return new int[] {from, colon + 1, at};
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
        java.util.List<String> secrets = new java.util.ArrayList<>();
        Matcher parameter = PASSWORD_PARAMETER.matcher(url);
        while (parameter.find()) {
            secrets.add(parameter.group(2));
        }
        int[] info = userInfo(url);
        if (info != null) {
            secrets.add(url.substring(info[1], info[2]));
        }
        for (String secret : secrets) {
            for (String form : new String[] {secret, decode(secret)}) {
                // Every length: a short password is still one, and garbling a message beats showing it.
                if (!form.isEmpty() && !form.equals("***")) {
                    hidden = hidden.replace(form, "***");
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
        int[] info = userInfo(rest);
        if (info != null) {
            user = decode(rest.substring(info[0], info[1] - 1));
            password = decode(rest.substring(info[1], info[2]));
            rest = rest.substring(0, info[0]) + rest.substring(info[2] + 1);
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
