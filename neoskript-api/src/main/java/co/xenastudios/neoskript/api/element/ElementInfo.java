/*
 * Copyright (c) 2026 Xena Studios
 * SPDX-License-Identifier: GPL-3.0-only
 */
package co.xenastudios.neoskript.api.element;

import co.xenastudios.neoskript.api.annotation.Internal;
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
 * @param eventName what Skript calls the event the element is in, its current event name: {@code on quit},
 *     {@code on break / mine}, {@code command}, {@code function}; or {@code null} if unknown
 * @param eventClasses the classes of that event, or none
 * @param sectionType the type id of the values of the innermost section the element is in whose code runs for
 *     values ({@code entity} in {@code spawn a pig:}, {@code worldborder} in {@code set {_b} to a worldborder:}),
 *     or {@code null}: what Skript calls the current event there
 * @param defaultArguments a bit for each argument that's left out or an event value ({@code event-entity}),
 *     what Skript calls a default expression
 * @param time the time state the element is wanted in: -1 before the event ({@code past tool}), 1 after it
 *     ({@code future gamemode}), 0 now. Only elements that take time states
 *     ({@link co.xenastudios.neoskript.api.registration.Registrar#timeStates}) are ever made with another
 * @param text the element as written ({@code the drops}), as Skript's errors quote it, or {@code null} if unknown
 * @param argumentTexts each argument that's a text in quotes, as a {@link TextTemplate} whose holes have no values
 *     yet, else {@code null}
 * @param argumentElements the id of the registered element each argument is, without its variant
 *     ({@code NeoSkriptDbExprUnsafe}), or {@code null} where it's something else
 */
public record ElementInfo(int pattern, int mark, Set<String> tags, long orArguments, long listArguments,
        java.util.List<TypeArgument> types, java.util.List<String> argumentTypes, long singleArguments,
        java.util.List<String> argumentLiterals, String eventName, java.util.List<Class<?>> eventClasses,
        String sectionType, long defaultArguments, int time, String text,
        java.util.List<TextTemplate> argumentTexts, java.util.List<String> argumentElements) {

    /**
     * A type named in a pattern, as {@code strings} in {@code strings data tag "x" of {_item}}.
     *
     * @param id the type's id
     * @param name the type's name, as Skript writes it in messages
     * @param plural whether it was named in the plural
     * @param javaClass the class its values are
     */
    public record TypeArgument(String id, String name, boolean plural, Class<?> javaClass) {}

    /** Made by the compiler; addons are given one. */
    @Internal
    public ElementInfo {
        tags = Set.copyOf(tags);
        types = java.util.List.copyOf(types);
        argumentTypes = java.util.Collections.unmodifiableList(new java.util.ArrayList<>(argumentTypes));
        argumentLiterals = java.util.Collections.unmodifiableList(new java.util.ArrayList<>(argumentLiterals));
        eventClasses = java.util.List.copyOf(eventClasses);
        argumentTexts = argumentTexts == null ? java.util.List.of()
                : java.util.Collections.unmodifiableList(new java.util.ArrayList<>(argumentTexts));
        argumentElements = argumentElements == null ? java.util.List.of()
                : java.util.Collections.unmodifiableList(new java.util.ArrayList<>(argumentElements));
    }

    /**
     * Made by the compiler for an element outside a trigger; addons are given one.
     *
     * @param pattern the index of the pattern that matched
     * @param mark the parse marks used
     * @param tags the parse tags used
     * @param orArguments a bit for each argument written as an or-list
     * @param listArguments a bit for each argument written as a list
     * @param types the types named in the pattern's type parts
     * @param argumentTypes the type id each argument is known to give
     * @param singleArguments a bit for each argument that's certainly one value
     * @param argumentLiterals each argument's value as written, if it's a literal
     */
    @Internal
    public ElementInfo(int pattern, int mark, Set<String> tags, long orArguments, long listArguments,
            java.util.List<TypeArgument> types, java.util.List<String> argumentTypes, long singleArguments,
            java.util.List<String> argumentLiterals) {
        this(pattern, mark, tags, orArguments, listArguments, types, argumentTypes, singleArguments, argumentLiterals,
                null, java.util.List.of(), null, 0, 0, null, null, null);
    }

    /**
     * This element as it's wanted at {@code time}, a time state: -1 before the event, 1 after it, 0 now.
     *
     * @param time the time state
     * @return the element at that time state
     */
    @Internal
    public ElementInfo withTime(int time) {
        return new ElementInfo(pattern, mark, tags, orArguments, listArguments, types, argumentTypes, singleArguments,
                argumentLiterals, eventName, eventClasses, sectionType, defaultArguments, time, text, argumentTexts,
                argumentElements);
    }

    /**
     * The element's text as written, as an error quotes it.
     *
     * @param written what to give where the text isn't known
     * @return the text, or {@code written}
     */
    public String textOr(String written) {
        return text != null ? text : written;
    }

    /**
     * Whether the element is in an event of one of {@code classes} (or of a subclass), as Skript's
     * {@code isCurrentEvent}.
     *
     * @param classes the event classes
     * @return whether it is
     */
    public boolean isEvent(Class<?>... classes) {
        for (Class<?> current : eventClasses) {
            for (Class<?> wanted : classes) {
                if (wanted.isAssignableFrom(current)) {
                    return true;
                }
            }
        }
        return false;
    }

    /**
     * Whether argument {@code index} was left out or is an event value, as Skript's default expressions are.
     *
     * @param index the argument's index
     * @return whether it is
     */
    public boolean isDefault(int index) {
        return index < 64 && (defaultArguments & (1L << index)) != 0;
    }

    /**
     * Whether argument {@code index} is certainly one value: a literal, a text, a variable that isn't a list, or
     * an element that always gives one (or does when its arguments do), as Skript's isSingle.
     *
     * @param index the argument's index
     * @return whether it is
     */
    public boolean isSingle(int index) {
        return index < 64 && (singleArguments & (1L << index)) != 0;
    }

    /**
     * Argument {@code index} as written, if it's a literal or a plain text.
     *
     * @param index the argument's index
     * @return it as written, or {@code null}
     */
    public String argumentLiteral(int index) {
        return index < argumentLiterals.size() ? argumentLiterals.get(index) : null;
    }

    /**
     * The type argument {@code index} is known to give.
     *
     * @param index the argument's index
     * @return the type's id, or {@code null} if that isn't known until it runs
     */
    public String argumentType(int index) {
        return index < argumentTypes.size() ? argumentTypes.get(index) : null;
    }

    /**
     * Whether argument {@code index} was written as a list ({@code {_x::*}}, {@code a and b}), not one value.
     *
     * @param index the argument's index
     * @return whether it was
     */
    public boolean isList(int index) {
        return index < 64 && (listArguments & (1L << index)) != 0;
    }

    /**
     * Whether argument {@code index} means all its values ({@code a and b}, a list variable), rather than any
     * one of them ({@code a or b}), as conditions check them.
     *
     * @param index the argument's index
     * @return whether it means all
     */
    public boolean and(int index) {
        return index >= 64 || (orArguments & (1L << index)) == 0;
    }

    /**
     * Whether an alternative written with this tag ({@code tag:text}) was used.
     *
     * @param tag the tag
     * @return whether it was
     */
    public boolean hasTag(String tag) {
        return tags.contains(tag);
    }

    /**
     * Argument {@code index} if it's a text written in quotes, with the expressions in it kept apart, for a
     * {@link TemplateAware} element to check while the script loads. The holes have no values yet.
     *
     * @param index the argument's index
     * @return the text, or {@code null} if the argument isn't a text in quotes
     */
    public TextTemplate argumentText(int index) {
        return index < argumentTexts.size() ? argumentTexts.get(index) : null;
    }

    /**
     * The id of the registered element argument {@code index} is, without its variant, as its addon named it:
     * {@code NeoSkriptDbExprUnsafe} for skript-db's {@code unsafe %text%}.
     *
     * @param index the argument's index
     * @return the id, or {@code null} if the argument isn't a registered element (a text, a variable, a literal,
     *     a function call) or was left out
     */
    public String argumentElement(int index) {
        return index < argumentElements.size() ? argumentElements.get(index) : null;
    }
}
