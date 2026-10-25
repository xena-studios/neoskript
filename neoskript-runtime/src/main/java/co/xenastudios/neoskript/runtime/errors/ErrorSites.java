/*
 * Copyright (c) 2026 Xena Studios
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package co.xenastudios.neoskript.runtime.errors;

import co.xenastudios.neoskript.runtime.ScriptCatalog;
import co.xenastudios.neoskript.runtime.ScriptRef;
import java.io.IOException;
import java.io.InputStream;
import java.util.Arrays;
import java.util.Collections;
import java.util.EnumSet;
import java.util.Map;
import java.util.Properties;
import java.util.WeakHashMap;

/**
 * Where in the scripts a runtime error comes from, found only when one is reported, so running code pays
 * nothing for it. The compiler records, for each compiled class, the bytecode offset of every call into a
 * syntax element with that element and its line; reporting walks the stack to the innermost compiled frame
 * and looks up the call it's in.
 */
public final class ErrorSites {

    /** A call into a syntax element: its kind and id, and the script line it's on. */
    public static final class Site {
        private final String kind;
        private final String id;
        private final String script;
        private final int line;
        private final String code;
        private volatile ErrorSource source;

        /**
         * @param kind the syntax kind, as Skript names it: {@code effect}, {@code expression}, ...
         * @param id the element's id (Skript's class name)
         * @param code the line's code, without its comment
         */
        public Site(String kind, String id, String script, int line, String code) {
            this.kind = kind;
            this.id = id;
            this.script = script;
            this.line = line;
            this.code = code;
        }

        public int line() {
            return line;
        }

        /** The source Skript reports for this element, worked out the first time it's needed. */
        public ErrorSource source() {
            ErrorSource made = source;
            if (made == null) {
                made = new ErrorSource(kind, name(id), line, lineText(script, line, code), script);
                source = made;
            }
            return made;
        }
    }

    /** The calls of one compiled class, by offset; with {@code exact} false, only their lines can be trusted. */
    private record Table(int[] offsets, Site[] sites, boolean exact) {}

    private static final Map<Class<?>, Table> TABLES = Collections.synchronizedMap(new WeakHashMap<>());
    private static final StackWalker WALKER = StackWalker.getInstance(
            EnumSet.of(StackWalker.Option.RETAIN_CLASS_REFERENCE, StackWalker.Option.SHOW_HIDDEN_FRAMES));
    private static volatile Properties names;

    private ErrorSites() {}

    /**
     * Records the element calls of a compiled class. {@code offsets} are the calls' bytecode offsets in its
     * code, ascending; when they may be off (the class was rewritten after they were taken) {@code exact} is
     * false and the line numbers are used instead.
     */
    public static void register(Class<?> compiled, int[] offsets, Site[] sites, boolean exact) {
        if (sites.length > 0) {
            TABLES.put(compiled, new Table(offsets, sites, exact));
        }
    }

    /** The element running now in the innermost compiled code on this thread's stack, or null if none. */
    public static Site current() {
        return WALKER.walk(frames -> frames
                .map(frame -> {
                    Table table = TABLES.get(frame.getDeclaringClass());
                    return table == null ? null : find(table, frame.getByteCodeIndex(), frame.getLineNumber());
                })
                .filter(java.util.Objects::nonNull)
                .findFirst()
                .orElse(null));
    }

    private static Site find(Table table, int offset, int line) {
        if (table.exact()) {
            int at = Arrays.binarySearch(table.offsets(), offset);
            if (at >= 0) {
                return table.sites()[at];
            }
        }
        // Not a call into an element (a changer, say): the last element called on the line, its statement's.
        Site found = null;
        for (Site site : table.sites()) {
            if (site.line() == line) {
                found = site;
            }
        }
        return found;
    }

    /** Skript's documentation name for an element, or its id if Skript has none. */
    static String name(String id) {
        Properties loaded = names;
        if (loaded == null) {
            loaded = new Properties();
            try (InputStream in = ErrorSites.class.getResourceAsStream("skript-element-names.properties")) {
                if (in != null) {
                    loaded.load(in);
                }
            } catch (IOException ignored) {
                // Then elements go by their ids.
            }
            names = loaded;
        }
        return loaded.getProperty(id, id);
    }

    /**
     * The line as Skript's node saves it, trimmed: the code and its comment, if the file still has it. Skript
     * keeps the space before the comment with the code, which it trims, so the comment follows straight on.
     */
    static String lineText(String script, int line, String code) {
        String source;
        try {
            source = script == null ? null : ScriptCatalog.get().source(new ScriptRef(script));
        } catch (RuntimeException unreadable) {
            source = null;
        }
        if (source == null) {
            return code;
        }
        String[] lines = source.split("\r\n|\r|\n", -1);
        if (line < 1 || line > lines.length) {
            return code;
        }
        String raw = lines[line - 1].strip();
        if (raw.startsWith(code)) {
            String rest = raw.substring(code.length()).strip();
            if (rest.startsWith("#")) {
                return code + rest;
            }
        }
        return code;
    }
}
