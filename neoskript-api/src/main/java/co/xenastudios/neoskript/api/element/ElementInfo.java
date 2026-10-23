/*
 * Copyright (c) 2026 Xena Studios
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package co.xenastudios.neoskript.api.element;

import java.util.Set;

/**
 * How a syntax element was matched, for its factory to set it up.
 *
 * @param pattern the index of the pattern that matched
 * @param mark the parse marks of the alternatives used ({@code 2¦text}), combined with XOR
 * @param tags the parse tags of the alternatives used ({@code tag:text})
 * @param orArguments a bit for each argument written as an or-list ({@code 1, 2 or 3}), lowest bit first
 * @param listArguments a bit for each argument written as a list: a list variable or several values
 * @param types the types named in the pattern's type parts ({@code string data tag}), in order
 * @param argumentTypes the type id each argument is known to give before running, or {@code null} where it
 *     isn't known, as for variables
 * @param singleArguments a bit for each argument that's certainly one value: a literal, a text, a variable that
 *     isn't a list
 * @param argumentLiterals each argument's value as written, if it's a literal or a text with nothing in it,
 *     else {@code null}
 * @param eventName the event the element is in, as written without {@code on} ({@code quit}), or {@code null}
 * @param eventClasses the classes of that event, or none
 */
public record ElementInfo(int pattern, int mark, Set<String> tags, long orArguments, long listArguments,
        java.util.List<TypeArgument> types, java.util.List<String> argumentTypes, long singleArguments,
        java.util.List<String> argumentLiterals, String eventName, java.util.List<Class<?>> eventClasses) {

    /**
     * A type named in a pattern, as {@code strings} in {@code strings data tag "x" of {_item}}.
     *
     * @param id the type's id
     * @param name the type's name, as Skript writes it in messages
     * @param plural whether it was named in the plural
     * @param javaClass the class its values are
     */
    public record TypeArgument(String id, String name, boolean plural, Class<?> javaClass) {}

    public ElementInfo {
        tags = Set.copyOf(tags);
        types = java.util.List.copyOf(types);
        argumentTypes = java.util.Collections.unmodifiableList(new java.util.ArrayList<>(argumentTypes));
        argumentLiterals = java.util.Collections.unmodifiableList(new java.util.ArrayList<>(argumentLiterals));
        eventClasses = java.util.List.copyOf(eventClasses);
    }

    public ElementInfo(int pattern, int mark, Set<String> tags, long orArguments, long listArguments,
            java.util.List<TypeArgument> types, java.util.List<String> argumentTypes, long singleArguments,
            java.util.List<String> argumentLiterals) {
        this(pattern, mark, tags, orArguments, listArguments, types, argumentTypes, singleArguments, argumentLiterals,
                null, java.util.List.of());
    }

    public ElementInfo(int pattern, int mark, Set<String> tags, long orArguments, long listArguments,
            java.util.List<TypeArgument> types, java.util.List<String> argumentTypes) {
        this(pattern, mark, tags, orArguments, listArguments, types, argumentTypes, 0, java.util.List.of());
    }

    public ElementInfo(int pattern, int mark, Set<String> tags) {
        this(pattern, mark, tags, 0, 0, java.util.List.of(), java.util.List.of(), 0, java.util.List.of());
    }

    public ElementInfo(int pattern, int mark, Set<String> tags, long orArguments, long listArguments) {
        this(pattern, mark, tags, orArguments, listArguments, java.util.List.of(), java.util.List.of(), 0,
                java.util.List.of());
    }

    /** Whether argument {@code index} is certainly one value: a literal, a text, or a variable that isn't a list. */
    public boolean isSingle(int index) {
        return index < 64 && (singleArguments & (1L << index)) != 0;
    }

    /** Argument {@code index} as written, if it's a literal or a plain text, else {@code null}. */
    public String argumentLiteral(int index) {
        return index < argumentLiterals.size() ? argumentLiterals.get(index) : null;
    }

    public ElementInfo(int pattern, int mark, Set<String> tags, long orArguments, long listArguments,
            java.util.List<TypeArgument> types) {
        this(pattern, mark, tags, orArguments, listArguments, types, java.util.List.of());
    }

    /** The type id argument {@code index} is known to give, or {@code null} if that isn't known until it runs. */
    public String argumentType(int index) {
        return index < argumentTypes.size() ? argumentTypes.get(index) : null;
    }

    /** Whether argument {@code index} was written as a list ({@code {_x::*}}, {@code a and b}), not one value. */
    public boolean isList(int index) {
        return index < 64 && (listArguments & (1L << index)) != 0;
    }

    /**
     * Whether argument {@code index} means all its values ({@code a and b}, a list variable), rather than any
     * one of them ({@code a or b}), as conditions check them.
     */
    public boolean and(int index) {
        return index >= 64 || (orArguments & (1L << index)) == 0;
    }

    public boolean hasTag(String tag) {
        return tags.contains(tag);
    }
}
