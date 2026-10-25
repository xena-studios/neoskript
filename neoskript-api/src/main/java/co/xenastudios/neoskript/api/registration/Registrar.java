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
     * A section, which can also be written as an effect without code. In its code, the values it returns
     * are the event values of {@code valueType}, like {@code entity} in {@code spawn a pig:}.
     */
    void section(String id, String valueType, ElementFactory<co.xenastudios.neoskript.api.element.Section> factory,
            String... patterns);

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
     * An expression whose patterns name a type in each {@code <...>} part, like Skript's classinfo placeholders:
     * {@code [the] [<.+>] data tag %text% of %objects%}. The types matched reach the factory in
     * {@link co.xenastudios.neoskript.api.element.ElementInfo#types()}.
     */
    void typedExpression(String id, String returnType, ElementFactory<Expression> factory, String... patterns);

    /**
     * Lets expression {@code expressionId} take a section, as Skript's expression sections do:
     * {@code set {_border} to a worldborder:} runs the code under it for the value made, whose type is
     * {@code valueType} ({@code event-worldborder}, or left out where a world border goes), before setting it.
     */
    void sectionExpression(String expressionId, String valueType);

    /**
     * Lets expression {@code expressionId} take a section that runs later, as Skript's test runnables do:
     * {@code set {_r} to a new runnable:}. The expression's value is the section itself, a {@link Runnable}
     * that runs the code under it with the local variables of the trigger it was made in.
     */
    void deferredSectionExpression(String expressionId);

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
     * An event value at a time state, as Skript's: {@code past event-block} ({@code time} -1) is the block before
     * the event, {@code future event-item} (1) the item after it. 0 is the value now, as
     * {@link #eventValue(Class, String, java.util.function.Function)} registers. Where an event has no value for
     * a time state, the value now is used, but a time state can only be written where some value has one.
     */
    <E> void eventValue(Class<E> eventClass, String typeId, int time, java.util.function.Function<? super E, ?> getter);

    /**
     * Events of {@code eventClass} (and subclasses) don't have a value of {@code typeId}, though a superclass
     * does: asking for it is an error with {@code message}, as Skript's {@code event-entity} in a damage event.
     */
    void eventValueExcluded(Class<?> eventClass, String typeId, String message);

    /**
     * An event value of {@code typeId} that's only for a narrower kind, whose values are {@code valueClass}:
     * {@code event-projectile} in a damage event is the projectile that hit, though {@code event-entity} isn't.
     * Where a trigger asks for a kind (see {@link #kindClass}) that an event has no such value for, its plain value
     * is used if it's of that kind.
     */
    <E> void eventValue(Class<E> eventClass, String typeId, Class<?> valueClass, int time,
            java.util.function.Function<? super E, ?> getter);

    /**
     * What class of values a literal of {@code typeId} stands for, as an entity type stands for its entities:
     * {@code event-projectile} asks for an event value of the class {@code projectile} gives.
     */
    void kindClass(String typeId, java.util.function.Function<Object, Class<?>> classOf);

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
     * Words scripts can write as values of {@code typeId}, like item names ({@code 2 stone swords}). The
     * parser returns the value, or {@code null} for text that isn't one. Literals are compiled once and
     * shared, so values should be immutable or copied by the syntax that changes them.
     */
    void literalParser(String typeId, java.util.function.Function<String, ?> parser);

    /**
     * How text people type, such as a command's arguments, becomes a value of {@code typeId}. The parser
     * returns {@code null} for text that isn't one.
     */
    void parser(String typeId, java.util.function.Function<String, ?> parser);

    /**
     * How a value of {@code from} becomes a {@code to} where a placeholder wants one: an entity where a
     * location is expected is the entity's location. The converter returns {@code null} if it can't.
     */
    <F, T> void converter(Class<F> from, Class<T> to, java.util.function.Function<? super F, ? extends T> converter);

    /**
     * How {@code set}/{@code add}/{@code remove}... change a value of {@code owner} that's the target
     * itself, rather than a variable or a changeable expression: {@code give a diamond to player} adds it to
     * the player's inventory. {@code change} gets the owner and the values given.
     */
    <T> void changer(Class<T> owner, co.xenastudios.neoskript.api.element.Changeable.Mode mode,
            java.util.function.BiConsumer<? super T, Object[]> change);

    /**
     * How values of {@code type}, which can be changed in place, are copied when stored in a variable, as
     * Skript copies them: changing an item in a variable doesn't change the item it was set from.
     */
    <T> void cloner(Class<T> type, java.util.function.UnaryOperator<T> cloner);

    /**
     * What {@code is} means between a value of {@code left} and one of {@code right} (checked either way
     * round, with the {@code left} value first): {@code {_entity} is a pig}, {@code lucy axolotl is axolotl}.
     */
    <A, B> void comparison(Class<A> left, Class<B> right, java.util.function.BiPredicate<? super A, ? super B> test);

    /**
     * What {@code operator} ({@code + - * / ^}) gives between a value of {@code left} and one of
     * {@code right}, in that order: {@code {_vector} * 2}. Returning {@code null} gives nothing.
     */
    <A, B> void arithmetic(char operator, Class<A> left, Class<B> right,
            java.util.function.BiFunction<? super A, ? super B, ?> apply);

    /** What a missing operand counts as in arithmetic next to a value of {@code type}, as 0 does for numbers. */
    <T> void arithmeticDefault(Class<T> type, java.util.function.Supplier<? extends T> value);

    /** How values of {@code type} read as text, as when embedded in a string. */
    <T> void text(Class<T> type, java.util.function.Function<? super T, String> toText);
}
