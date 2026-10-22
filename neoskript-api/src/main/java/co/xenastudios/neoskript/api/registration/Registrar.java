/*
 * Copyright (c) 2026 Xena Studios
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package co.xenastudios.neoskript.api.registration;

import co.xenastudios.neoskript.api.element.Condition;
import co.xenastudios.neoskript.api.element.Effect;
import co.xenastudios.neoskript.api.element.ElementFactory;
import co.xenastudios.neoskript.api.element.Expression;
import co.xenastudios.neoskript.api.element.Function;
import java.util.List;

/**
 * Registers types and syntax. NeoSkript's standard library registers through this, as addons will.
 *
 * <p>Patterns use Skript's pattern language. Type names in patterns are a type's id or name, singular or
 * plural. Elements are tried in registration order.
 */
public interface Registrar {

    /**
     * Registers a type.
     *
     * @param id the code name used in patterns, e.g. {@code commandsender}
     * @param name the display name, e.g. {@code command sender}; also usable in patterns
     * @param javaClass the class values of this type have
     */
    void type(String id, String name, Class<?> javaClass);

    /**
     * A type whose values have names, like game modes. Scripts can write the names ({@code creative}), and
     * the type's parser accepts them.
     *
     * @param values each value by its lower case name
     */
    <T> void namedType(String id, String name, Class<T> javaClass, java.util.Map<String, ? extends T> values);

    /** Another name for a type in patterns and scripts, like {@code int} for {@code integer}. */
    void alias(String typeId, String singular, String plural);

    /** Values of type {@code sub} can be used where {@code supertype} is expected. */
    void subtype(String sub, String supertype);

    void effect(String id, ElementFactory<Effect> factory, String... patterns);

    void condition(String id, ElementFactory<Condition> factory, String... patterns);

    /**
     * A property condition, {@code %owners% (is|are) <property>} and its negation (see
     * {@link Patterns#isProperty}). It's true when {@code test} holds for every owner, or for none when
     * negated.
     */
    default <T> void propertyCondition(String id, String property, String owners, Class<T> ownerClass,
            java.util.function.Predicate<? super T> test) {
        condition(id, info -> (context, arguments) -> Properties.test(arguments[0], ownerClass, test,
                info.pattern() == 1), Patterns.isProperty(property, owners));
    }

    /** @param returnType the id of the type the expression returns */
    void expression(String id, String returnType, ElementFactory<Expression> factory, String... patterns);

    /**
     * A property expression, {@code [the] <property> of %owners%} and {@code %owners%'[s] <property>}: the
     * value of {@code getter} for each owner of {@code ownerClass}. Owners of other classes, and
     * {@code null} results, are left out.
     */
    default <T> void property(String id, String returnType, String property, String owners, Class<T> ownerClass,
            java.util.function.Function<? super T, ?> getter) {
        expression(id, returnType, info -> (context, arguments) -> Properties.map(arguments[0], ownerClass, getter),
                Patterns.property(property, owners));
    }

    /** An event NeoSkript fires itself, like a script loading. */
    void event(String id, ScriptEvent event, String... patterns);

    /**
     * A server event. Triggers run when an event of one of {@code eventClasses} fires, and {@code filter},
     * if there is one, passes. The filter gets the values of the pattern's placeholders, as in
     * {@code on break of stone}.
     */
    void event(String id, List<Class<?>> eventClasses, ElementFactory<Condition> filter, String... patterns);

    /**
     * A value triggers can get from events of {@code eventClass} (or its subclasses), as {@code event-<type>}
     * or by the type's name ({@code player}). The most specific event class wins.
     *
     * @param typeId the id of the value's type
     */
    <E> void eventValue(Class<E> eventClass, String typeId, java.util.function.Function<? super E, ?> getter);

    /**
     * Where a placeholder of {@code typeId} sits in an optional part that wasn't used, it gets the event's
     * value of that type instead, if the event has one: {@code send "hi"} goes to the event's player.
     */
    void eventDefault(String typeId);

    /**
     * Registers a built-in function. Scripts can define their own functions with the same name, which
     * take precedence.
     *
     * @param returnType the id of the type it returns, or {@code null} if it returns nothing
     */
    void function(String name, List<Parameter> parameters, String returnType, boolean returnsPlural,
            Function function);

    /**
     * How values of {@code type} are saved in variables files, as Skript's type {@code name} (its type
     * column in {@code variables.csv}, e.g. {@code location}).
     */
    <T> void storage(String name, Class<T> type, co.xenastudios.neoskript.api.storage.VariableCodec<T> codec);

    /**
     * How text people type, such as a command's arguments, becomes a value of {@code typeId}. The parser
     * returns {@code null} for text that isn't one.
     */
    void parser(String typeId, java.util.function.Function<String, ?> parser);

    /** How values of {@code type} read as text, as when embedded in a string. */
    <T> void text(Class<T> type, java.util.function.Function<? super T, String> toText);
}
