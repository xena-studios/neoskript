/*
 * Copyright (c) 2026 Xena Studios
 * SPDX-License-Identifier: GPL-3.0-only
 */
package co.xenastudios.neoskript.runtime;

import java.util.Map;

/**
 * The messages of Skript's language files that scripts show, as config.yml's {@code language} (Skript's
 * {@code language} option) has them: what nothing is in text, and a command's default cooldown message. English
 * until the plugin loads another.
 */
public final class Language {

    /** Skript's English messages that scripts show, by their key in its language files. */
    public static final Map<String, String> ENGLISH = Map.of(
            "none", "<none>",
            "commands.cooldown message", "You are using this command too often, please try again later");

    private static volatile Map<String, String> messages = ENGLISH;
    private static volatile String none = ENGLISH.get("none");

    private Language() {}

    /** Uses {@code messages}, by key; keys it doesn't have stay English. */
    public static void use(Map<String, String> messages) {
        java.util.HashMap<String, String> all = new java.util.HashMap<>(ENGLISH);
        messages.forEach((key, value) -> {
            if (ENGLISH.containsKey(key) && value != null) {
                all.put(key, value);
            }
        });
        Language.messages = Map.copyOf(all);
        none = all.get("none");
    }

    /** The message for {@code key}, such as {@code commands.cooldown message}. */
    public static String get(String key) {
        return messages.getOrDefault(key, key);
    }

    /** What nothing is in text: {@code <none>} in English. */
    public static String none() {
        return none;
    }
}
