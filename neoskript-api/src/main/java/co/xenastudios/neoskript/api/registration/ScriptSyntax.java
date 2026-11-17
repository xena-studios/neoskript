/*
 * Copyright (c) 2026 Xena Studios
 * SPDX-License-Identifier: GPL-3.0-only
 */
package co.xenastudios.neoskript.api.registration;

import co.xenastudios.neoskript.api.annotation.Experimental;
import java.util.List;

/**
 * Syntax a script declares, as skript-reflect's {@code effect}, {@code condition} and {@code expression} structures
 * do: what an addon's {@link ScriptSyntaxHandler} reads from one of them. NeoSkript adds it to the syntax every script
 * is parsed with, for that load only, and compiles the code in the structure's {@code body} section as what runs where
 * the syntax is used.
 *
 * @param kind what it is
 * @param patterns its patterns, in Skript's pattern language
 * @param returnType for an expression, the id of the type of what it gives, as patterns name it ({@code string}), or
 *     {@code null} for any value
 * @param plurality for an expression, {@link Plurality#SINGLE}, {@link Plurality#PLURAL}, or
 *     {@link Plurality#AS_ARGUMENTS} when it's several values exactly when a plural argument is
 * @param local whether only the script that declares it can use it
 * @param body the name of the section holding its code ({@code trigger}, {@code check}, {@code get})
 * @param property whether it's a property of its first argument, as Skript's property expressions and conditions: its
 *     code runs once for each of the first argument's values, given alone; an expression gives what each run returns,
 *     and a condition passes if each run passes (any, for an or-list), the opposite for its second pattern, which is
 *     the negated one ({@code is not})
 */
@Experimental
public record ScriptSyntax(Kind kind, List<String> patterns, String returnType, Plurality plurality, boolean local,
        String body, boolean property) {

    /** Copies the patterns. */
    public ScriptSyntax {
        patterns = List.copyOf(patterns);
    }

    /**
     * Syntax that isn't a property.
     *
     * @param kind what it is
     * @param patterns its patterns
     * @param returnType for an expression, its type's id, or {@code null}
     * @param plurality for an expression, whether it's one value or several
     * @param local whether only its own script can use it
     * @param body the name of the section holding its code
     */
    public ScriptSyntax(Kind kind, List<String> patterns, String returnType, Plurality plurality, boolean local,
            String body) {
        this(kind, patterns, returnType, plurality, local, body, false);
    }

    /** What syntax a script can declare. */
    public enum Kind {
        /** A line of its own: its code runs. */
        EFFECT,
        /** A check: it passes if its code {@linkplain co.xenastudios.neoskript.api.element.ScriptSyntaxCall#proceed() proceeds}. */
        CONDITION,
        /** A value: what its code returns. */
        EXPRESSION
    }

    /** Whether an expression gives one value or several, as Skript's {@code isSingle()}. */
    public enum Plurality {
        /** Always one value. */
        SINGLE,
        /** Can be several values. */
        PLURAL,
        /** Several values exactly when a plural argument is ({@code %$strings%} in skript-reflect). */
        AS_ARGUMENTS
    }
}
