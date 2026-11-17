/*
 * Copyright (c) 2026 Xena Studios
 * SPDX-License-Identifier: GPL-3.0-only
 */
package co.xenastudios.neoskript.api.element;

import co.xenastudios.neoskript.api.annotation.Experimental;
import co.xenastudios.neoskript.api.annotation.Internal;

/**
 * Warnings an {@link ElementFactory} gives about how its syntax is used, as Skript's elements give them from
 * {@code init}: the script still loads. Each warning is reported once for the line it's on, unless the script
 * suppresses warnings of its kind ({@code suppress deprecated syntax warnings}). Warnings given outside a
 * script being loaded are dropped.
 */
public final class Warnings {

    /** Where warnings go while a script loads. The compiler's. */
    @Internal
    public interface Sink {
        /**
         * Gives a warning.
         *
         * @param tag the tag that suppresses it, or {@code null}
         * @param message the warning
         */
        void warn(String tag, String message);

        /**
         * Whether the script suppresses warnings with this tag where the element is.
         *
         * @param tag the tag
         * @return whether it does
         */
        boolean suppresses(String tag);

        /**
         * Argument {@code index} of the element being set up, as written.
         *
         * @param index the argument's index
         * @return it as written, or {@code null} if that isn't known
         */
        default String argument(int index) {
            return null;
        }

        /**
         * The line (or section header's key) the element is on, as written.
         *
         * @return the line, or {@code null}
         */
        default String line() {
            return null;
        }

        /**
         * Whether the code may have waited before the element, as Skript's {@code isDelayed} isn't false.
         *
         * @return whether it may have
         */
        default boolean delayed() {
            return false;
        }

        /**
         * Whether the code has certainly waited before the element, as Skript's {@code getHasDelayBefore} is true.
         *
         * @return whether it has
         */
        default boolean surelyDelayed() {
            return false;
        }
    }

    private static final ThreadLocal<Sink> SINK = new ThreadLocal<>();

    private Warnings() {}

    /**
     * A warning no script can suppress, as most of Skript's are.
     *
     * @param message the warning
     */
    public static void warn(String message) {
        warn(null, message);
    }

    /**
     * A warning the script can suppress with the tag, one of {@code variable save}'s {@code save},
     * {@code conjunction}, {@code starting}, {@code colon}, {@code deprecated}, {@code unreachable} or
     * {@code constant}.
     *
     * @param tag the tag, or {@code null} for none
     * @param message the warning
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
     *
     * @param message the warning, without the prefix
     */
    public static void deprecated(String message) {
        warn("deprecated", "[Deprecated] " + message);
    }

    /**
     * Whether the script being loaded suppresses warnings with this tag where the element is.
     *
     * @param tag the tag
     * @return whether it does
     */
    public static boolean suppressed(String tag) {
        Sink sink = SINK.get();
        return sink != null && sink.suppresses(tag);
    }

    /**
     * Argument {@code index} of the element being set up, as written in the script ({@code {_book}}), for a
     * warning that quotes it.
     *
     * @param index the argument's index
     * @return it as written, or {@code null} if that isn't known
     */
    @Experimental
    public static String argument(int index) {
        Sink sink = SINK.get();
        return sink == null ? null : sink.argument(index);
    }

    /**
     * The line (or section header's key) the element being set up is on, as written.
     *
     * @return the line, or {@code null}
     */
    @Experimental
    public static String line() {
        Sink sink = SINK.get();
        return sink == null ? null : sink.line();
    }

    /**
     * Whether the code may have waited before the element being set up (Skript's {@code isDelayed} isn't false).
     *
     * @return whether it may have
     */
    @Experimental
    public static boolean delayed() {
        Sink sink = SINK.get();
        return sink != null && sink.delayed();
    }

    /**
     * Whether the code has certainly waited before the element being set up (Skript's {@code getHasDelayBefore}).
     *
     * @return whether it has
     */
    @Experimental
    public static boolean surelyDelayed() {
        Sink sink = SINK.get();
        return sink != null && sink.surelyDelayed();
    }

    /**
     * Sends the warnings given on this thread to {@code sink} (or drops them, if {@code null}) until the
     * returned previous sink is put back. For the compiler.
     *
     * @param sink where warnings go, or {@code null}
     * @return the sink before
     */
    @Internal
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
