/*
 * Copyright (c) 2026 Xena Studios
 * SPDX-License-Identifier: GPL-3.0-only
 */
package co.xenastudios.neoskript.frontend.parse;

import co.xenastudios.neoskript.frontend.source.Text;

/**
 * NeoSkript's opt-in language extensions: syntax Skript doesn't have, which a script only gets by asking for it with
 * {@code using neoskript <name>}, Skript's own {@code using} structure. A script without that line is parsed and run
 * exactly as Skript would, so its syntax isn't affected by any extension. See {@code docs/LANGUAGE-EXTENSIONS.md}.
 *
 * <p>An extension is the syntax elements registered as experimental with the experiment {@code neoskript <name>}
 * ({@link co.xenastudios.neoskript.frontend.registry.SyntaxRegistry#languageExtensions}), so it's known exactly when
 * some syntax needs it. On Skript, {@code using neoskript <name>} gives Skript's warning that the experimental feature
 * wasn't found, and the extension's syntax isn't understood, so it's clear the script won't run there.
 */
public final class LanguageExtensions {

    /** What {@code using} names an extension with: {@code using neoskript <name>}. */
    public static final String PREFIX = "neoskript ";

    private LanguageExtensions() {}

    /**
     * The extension {@code using <usingName>} asks for, in lower case with single spaces, or {@code null} if it's not
     * one ({@code using script reflection} is one of Skript's experiments).
     */
    public static String extensionName(String usingName) {
        if (usingName == null) {
            return null;
        }
        String normal = Text.lower(usingName.strip()).replaceAll("\\s+", " ");
        if (!normal.startsWith(PREFIX)) {
            return null;
        }
        String name = normal.substring(PREFIX.length()).strip();
        return name.isEmpty() ? null : name;
    }

    /** The experiment an extension's syntax is registered with. */
    public static String experiment(String extension) {
        return PREFIX + extension;
    }

    /** The error for {@code using neoskript <name>} when there's no such extension. */
    static String unknown(String name) {
        return "NeoSkript has no language extension called '" + name + "'";
    }
}
