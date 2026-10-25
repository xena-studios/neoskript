/*
 * Copyright (c) 2026 Xena Studios
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package co.xenastudios.neoskript.runtime.errors;

import co.xenastudios.neoskript.runtime.ScriptCatalog;
import co.xenastudios.neoskript.runtime.ScriptRef;
import java.io.IOException;
import java.io.InputStream;
import java.util.Collections;
import java.util.EnumSet;
import java.util.Map;
import java.util.Properties;
import java.util.WeakHashMap;

/**
 * Where in the scripts a runtime error comes from, found only when one is reported, so running code pays
 * nothing for it. The compiler records, for each compiled class, its calls into syntax elements: the
 * element's class and the line. Reporting walks the stack to the innermost compiled frame, and takes the call
 * on its line into the class of the frame just above it, the element that's reporting.
 */
public final class ErrorSites {

    /** A call into a syntax element: its kind, id and class, and the script line it's on. */
    public static final class Site {
        private final String kind;
        private final String id;
        private final Class<?> element;
        private final String script;
        private final int line;
        private final String code;
        private volatile ErrorSource source;

        /**
         * @param kind the syntax kind, as Skript names it: {@code effect}, {@code expression}, ...
         * @param id the element's id (Skript's class name)
         * @param element the class of the element called, or of what compiled code reports through itself
         * @param code the line's code, without its comment
         */
        public Site(String kind, String id, Class<?> element, String script, int line, String code) {
            this.kind = kind;
            this.id = id;
            this.element = element;
            this.script = script;
            this.line = line;
            this.code = code;
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

    private static final Map<Class<?>, Site[]> SITES = Collections.synchronizedMap(new WeakHashMap<>());
    private static final StackWalker WALKER = StackWalker.getInstance(
            EnumSet.of(StackWalker.Option.RETAIN_CLASS_REFERENCE, StackWalker.Option.SHOW_HIDDEN_FRAMES));
    private static volatile Properties names;

    private ErrorSites() {}

    /** Records the element calls of a compiled class. */
    public static void register(Class<?> compiled, Site[] sites) {
        if (sites.length > 0) {
            SITES.put(compiled, sites);
        }
    }

    /** The element running now in the innermost compiled code on this thread's stack, or null if none. */
    public static Site current() {
        return WALKER.walk(frames -> {
            Class<?> callee = null;
            for (java.util.Iterator<StackWalker.StackFrame> it = frames.iterator(); it.hasNext(); ) {
                StackWalker.StackFrame frame = it.next();
                Site[] sites = SITES.get(frame.getDeclaringClass());
                if (sites != null) {
                    Site found = find(sites, callee, frame.getLineNumber());
                    if (found != null) {
                        return found;
                    }
                }
                callee = frame.getDeclaringClass();
            }
            return null;
        });
    }

    /**
     * The call on {@code line} into {@code callee} (or a class it's inherited from); failing that, the line's
     * first call, its statement's element: what a changer or other helper reports for.
     */
    private static Site find(Site[] sites, Class<?> callee, int line) {
        Site first = null;
        for (Site site : sites) {
            if (site.line != line) {
                continue;
            }
            if (callee != null && (site.element == callee || callee.isAssignableFrom(site.element))) {
                return site;
            }
            if (first == null) {
                first = site;
            }
        }
        return first;
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
