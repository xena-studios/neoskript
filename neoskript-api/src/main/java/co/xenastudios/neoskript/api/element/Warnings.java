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
