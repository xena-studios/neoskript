/*
 * Copyright (c) 2026 Xena Studios
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package co.xenastudios.neoskript.api.element;

/**
 * Warnings an {@link ElementFactory} gives about how its syntax is used, as Skript's elements give them from
 * {@code init}: the script still loads. Each warning is reported once for the line it's on, unless the script
 * suppresses warnings of its kind ({@code suppress deprecated syntax warnings}). Warnings given outside a
 * script being loaded are dropped.
 */
public final class Warnings {

    /** Where warnings go while a script loads: the tag that suppresses them (or {@code null}) and the message. */
    public interface Sink {
        void warn(String tag, String message);

        /** Whether the script suppresses warnings with this tag where the element is. */
        boolean suppresses(String tag);

        /** Argument {@code index} of the element being set up, as written, or {@code null} if that isn't known. */
        default String argument(int index) {
            return null;
        }

        /** The line (or section header's key) the element is on, as written, or {@code null}. */
        default String line() {
            return null;
        }

        /** Whether the code may have waited before the element, as Skript's {@code isDelayed} isn't false. */
        default boolean delayed() {
            return false;
        }

        /** Whether the code has certainly waited before the element, as Skript's {@code getHasDelayBefore} is true. */
        default boolean surelyDelayed() {
            return false;
        }
    }

    private static final ThreadLocal<Sink> SINK = new ThreadLocal<>();

    private Warnings() {}

    /** A warning no script can suppress, as most of Skript's are. */
    public static void warn(String message) {
        warn(null, message);
    }

    /**
     * A warning the script can suppress with the tag, one of {@code variable save}'s {@code save},
     * {@code conjunction}, {@code starting}, {@code colon}, {@code deprecated}, {@code unreachable} or
     * {@code constant}.
     */
    public static void warn(String tag, String message) {
        Sink sink = SINK.get();
        if (sink != null) {
            sink.warn(tag, message);
        }
    }

    /**
     * Skript's {@code ScriptWarning.printDeprecationWarning}: the message, prefixed with {@code [Deprecated] },
     * unless the script suppresses deprecated syntax warnings.
     */
    public static void deprecated(String message) {
        warn("deprecated", "[Deprecated] " + message);
    }

    /** Whether the script being loaded suppresses warnings with this tag where the element is. */
    public static boolean suppressed(String tag) {
        Sink sink = SINK.get();
        return sink != null && sink.suppresses(tag);
    }

    /**
     * Argument {@code index} of the element being set up, as written in the script ({@code {_book}}), for a
     * warning that quotes it, or {@code null} if that isn't known.
     */
    public static String argument(int index) {
        Sink sink = SINK.get();
        return sink == null ? null : sink.argument(index);
    }

    /** The line (or section header's key) the element being set up is on, as written, or {@code null}. */
    public static String line() {
        Sink sink = SINK.get();
        return sink == null ? null : sink.line();
    }

    /** Whether the code may have waited before the element being set up (Skript's {@code isDelayed} isn't false). */
    public static boolean delayed() {
        Sink sink = SINK.get();
        return sink != null && sink.delayed();
    }

    /** Whether the code has certainly waited before the element being set up (Skript's {@code getHasDelayBefore}). */
    public static boolean surelyDelayed() {
        Sink sink = SINK.get();
        return sink != null && sink.surelyDelayed();
    }

    /**
     * Sends the warnings given on this thread to {@code sink} (or drops them, if {@code null}) until the
     * returned previous sink is put back. For the compiler.
     */
    public static Sink install(Sink sink) {
        Sink previous = SINK.get();
        if (sink == null) {
            SINK.remove();
        } else {
            SINK.set(sink);
        }
        return previous;
    }
}
