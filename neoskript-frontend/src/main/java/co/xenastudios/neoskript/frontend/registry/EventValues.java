/*
 * Copyright (c) 2026 Xena Studios
 * SPDX-License-Identifier: GPL-3.0-or-later
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

    /** The event values some code can use. */
    interface Scope {

        /**
         * Whether {@code syntax}, matched with {@code type} as its type and {@code words} as its text, can be
         * had here. True for anything that isn't an event value.
         */
        boolean allows(SyntaxInfo syntax, TypeRef type, String words);
    }
}
