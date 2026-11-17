/*
 * Copyright (c) 2026 Xena Studios
 * SPDX-License-Identifier: GPL-3.0-only
 */
package co.xenastudios.neoskript.frontend.registry;

/**
 * Which event values code can use, so the parser can turn down reading words as an event value where there's
 * none and try another reading, as Skript's event value expressions fail to initialize there: {@code amount
 * of slots of {_inv}} in {@code on load} is the inventory's number of slots, not the amount of the event's slot.
 *
 * <p>Every method may return {@code null} when it can't tell, and then anything goes, as without this.
 */
public interface EventValues {

    /** What code under an event header ({@code on join}) can use. */
    Scope event(SyntaxInfo event);

    /** What a function's code can use. */
    Scope function();

    /** What a command's code can use. */
    Scope command();

    /** What code in another structure ({@code test "x":}) can use. */
    Scope structure(SyntaxInfo structure);

    /**
     * Whether code in a section with {@code header} can only use what the code around it can, as in {@code if}
     * and {@code loop}; otherwise the section may give values of its own ({@code spawn a pig:}).
     */
    boolean keepsEventValues(SyntaxInfo header);

    /**
     * What code in a section with {@code header} can use, if the section's code is in an event of its own, as a spawn
     * section's is in Skript; else {@code null}, and it may use anything, as the section may give values of its own.
     */
    default Scope section(SyntaxInfo header) {
        return null;
    }

    /**
     * How many times the parser has used up an event value's error so far ({@code Use 'attacker' and/or 'victim'...}),
     * which Skript gives only the first time any script asks: which script that is depends on the order scripts are
     * read in.
     */
    default long usedUp() {
        return 0;
    }

    /** {@link #usedUp}, on this thread only: what the script being parsed here used up. */
    default long usedUpHere() {
        return 0;
    }

    /** What's been used up so far, to {@link #restore} it: see {@link #usedUp}. */
    default Object save() {
        return null;
    }

    /** Puts back what was used up when {@link #save} was called. */
    default void restore(Object saved) {}

    /**
     * What the code of a section that runs later, for events of its own, can use, as an addon's {@code make gui
     * slot 0 with dirt:}; or {@code null} if {@code header} isn't such a section. Its code also starts with no delay.
     */
    default Scope later(SyntaxInfo header) {
        return null;
    }

    /** The event values some code can use. */
    interface Scope {

        /**
         * Whether {@code syntax}, matched with {@code type} as its type and {@code words} as its text, can be
         * had here. True for anything that isn't an event value.
         */
        boolean allows(SyntaxInfo syntax, TypeRef type, String words);

        /**
         * Whether a placeholder can be left out here: not if only the event's value can stand for it and there's
         * none, as Skript's event value default expressions fail to initialize ({@code hide the particles}).
         */
        default boolean allowsLeftOut(SyntaxInfo syntax, co.xenastudios.neoskript.frontend.pattern.PlaceholderSpec spec) {
            return true;
        }

        /**
         * Whether pattern {@code pattern} of {@code syntax} works here: not if it only works in some events and
         * this isn't one of them, as Skript's elements whose set-up checks the event. The parser then tries the
         * text's other readings first, and takes this one only if none fits.
         */
        default boolean allowsSyntax(SyntaxInfo syntax, int pattern) {
            return true;
        }

        /**
         * Skript's error for an event value {@link #allows} turns down, as its event value expression's init gives
         * it ({@code There's no player in an on damage event.}), or {@code null} if it can't tell.
         */
        default String missing(SyntaxInfo syntax, TypeRef type, String words) {
            return null;
        }

        /**
         * Skript's error for {@code event-<words>} where the words name no type, as its event expression asks for a
         * value named so, which the event hasn't ({@code There's no message in an on chat event.}), or {@code null}
         * if it can't tell.
         */
        default String unnamed(String words) {
            return null;
        }
    }
}
