/*
 * Copyright (c) 2026 Xena Studios
 * SPDX-License-Identifier: GPL-3.0-only
 */
package co.xenastudios.neoskript.api.registration;

import co.xenastudios.neoskript.api.element.Condition;
import co.xenastudios.neoskript.api.element.Effect;
import co.xenastudios.neoskript.api.element.ElementFactory;
import co.xenastudios.neoskript.api.element.Expression;
import co.xenastudios.neoskript.api.element.Function;
import co.xenastudios.neoskript.api.annotation.Experimental;
import co.xenastudios.neoskript.api.annotation.Internal;
import java.util.List;

/**
 * Registers types and syntax. NeoSkript's standard library registers through this, as addons do (see
 * {@link co.xenastudios.neoskript.api.NeoSkript#addon}).
 *
 * <p>Patterns use Skript's pattern language: {@code [optional]}, {@code (one|other)}, {@code %type%} placeholders
 * ({@code %players%} for several, {@code %-player%} for one that may be left out), parse marks ({@code 1¦}) and
 * tags ({@code tag:}). Type names in patterns are a type's id or name, singular or plural. Elements are tried in
 * the order they're registered, NeoSkript's own first.
 *
 * <p><b>Ids.</b> Each element has an id, unique among elements of its kind, that names it in the syntax export and
 * in later calls about it ({@link #affinity}, {@link #timeStates}). Start an addon's ids with the addon's name
 * ({@code MyAddonEffWave}), and leave {@code -} out of them: NeoSkript's own use it for variants.
 *
 * <p><b>Factories.</b> Each element is made by an {@link ElementFactory} for every place a script uses it, while the
 * script loads, from how it matched there. Factories may be called more than once for one place and on whichever
 * thread loads scripts, so they mustn't have side effects; the element they return runs on the trigger's thread.
 *
 * <p>Registration runs once, before scripts load. Calling a registrar after that throws
 * {@link IllegalStateException}.
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
     * @param <T> the values' class
     * @param id the code name used in patterns
     * @param name the display name
     * @param javaClass the class values of this type have
     * @param values each value by its lower case name
     */
    <T> void namedType(String id, String name, Class<T> javaClass, java.util.Map<String, ? extends T> values);

    /**
     * Lets scripts list every value of type {@code id} with {@code all <type>}, like {@code all biomes}, for
     * types that aren't a fixed set of names. {@code values} is asked each time, when scripts run, so it may
     * read the server's registries.
     *
     * @param id the type's id
     * @param values every value of the type now
     */
    void allValues(String id, java.util.function.Supplier<? extends Iterable<?>> values);

    /**
     * Another name for a type in patterns and scripts, like {@code int} for {@code integer}.
     *
     * @param typeId the type's id
     * @param singular the other name
     * @param plural the other name in the plural
     */
    void alias(String typeId, String singular, String plural);

    /**
     * Values of type {@code sub} can be used where {@code supertype} is expected. Types whose classes are subclasses
     * are subtypes already: a player is an entity.
     *
     * @param sub the subtype's id
     * @param supertype the supertype's id
     */
    void subtype(String sub, String supertype);

    /**
     * An effect, a line that does something: {@code wave at %players%}.
     *
     * @param id the effect's id
     * @param factory makes the effect for each place it's used
     * @param patterns its patterns; {@link co.xenastudios.neoskript.api.element.ElementInfo#pattern()} says which
     *     matched
     */
    void effect(String id, ElementFactory<Effect> factory, String... patterns);

    /**
     * A condition, for {@code if}, {@code else if} and a line of its own that stops the trigger when it fails:
     * {@code %players% (is|are) waving}. A pattern for its negation is the element's to account for, by
     * {@link co.xenastudios.neoskript.api.element.ElementInfo#pattern()}.
     *
     * @param id the condition's id
     * @param factory makes the condition for each place it's used
     * @param patterns its patterns
     */
    void condition(String id, ElementFactory<Condition> factory, String... patterns);

    /**
     * A section, which can also be written as an effect without code. In its code, the values it returns
     * are the event values of {@code valueType}, like {@code entity} in {@code spawn a pig:}.
     *
     * @param id the section's id
     * @param valueType the id of the type of the values its code runs for, or {@code null} for none
     * @param factory makes the section for each place it's used
     * @param patterns its patterns
     */
    void section(String id, String valueType, ElementFactory<co.xenastudios.neoskript.api.element.Section> factory,
            String... patterns);

    /**
     * A property condition, {@code %owners% (is|are) <property>} and its negation (see
     * {@link Patterns#isProperty}). It's true when {@code test} holds for every owner, or for none when
     * negated.
     *
     * @param <T> the owners' class
     * @param id the condition's id
     * @param property the property's words, as {@code waving}
     * @param owners the owners' type, plural, as in a placeholder: {@code players}
     * @param ownerClass the owners' class; others make it fail
     * @param test whether an owner has the property
     */
    default <T> void propertyCondition(String id, String property, String owners, Class<T> ownerClass,
            java.util.function.Predicate<? super T> test) {
        condition(id, info -> (context, arguments) -> Properties.test(arguments[0], ownerClass, test,
                info.pattern() == 1), Patterns.isProperty(property, owners));
    }

    /**
     * An expression, which gives values: {@code [the] wave count of %player%}.
     *
     * @param id the expression's id
     * @param returnType the id of the type the expression returns, or {@code object} for any
     * @param factory makes the expression for each place it's used. An expression that can be changed with
     *     {@code set} and the like also implements {@link co.xenastudios.neoskript.api.element.Changeable}
     * @param patterns its patterns
     */
    void expression(String id, String returnType, ElementFactory<Expression> factory, String... patterns);

    /**
     * An effect that pauses the trigger until the work it starts is done, as {@code wait} does: {@code execute
     * %text% in %database%}. The trigger carries on from the next line when the element's stage completes, with its
     * local variables and the loops it's in, on the thread it was on: the main thread on Paper; on Folia, the
     * thread of what started the trigger (a player's region, the global thread for {@code on load}), or of the
     * {@code run on} section it's in. If the stage has completed already, the trigger carries straight on.
     *
     * <p>For Skript's rules it's a delay, as {@code wait} is: after it, the event has passed, so it can't be
     * cancelled, and {@code is delayed} is true. A script reloaded or unloaded while a trigger waits doesn't stop
     * it: it carries on with the code it started with, as after a {@code wait}. When the server stops, what
     * {@code on unload} triggers pause for gets a few seconds to finish; then triggers still waiting are dropped
     * without carrying on. Their stages aren't cancelled: finishing or stopping the work is the addon's.
     *
     * <p>{@code start} runs on the trigger's thread and mustn't block. The thread that completes the stage mustn't
     * use the {@code TriggerContext}, the event, or anything a region owns. If {@code start} throws, it's a failed
     * stage: a runtime error, after which the trigger carries on.
     *
     * <p>On Folia, it's given the entities, blocks and locations of the trigger's own thread only: given one another
     * thread owns, it's a runtime error and doesn't start (see {@link #affinity}).
     *
     * @param id the effect's id
     * @param factory makes the effect for each place it's used
     * @param patterns its patterns
     * @see co.xenastudios.neoskript.api.element.PausingEffect
     */
    void pausingEffect(String id, ElementFactory<co.xenastudios.neoskript.api.element.PausingEffect> factory,
            String... patterns);

    /**
     * An expression whose value takes a while to arrive. Where a line uses it, the trigger pauses before the line
     * until the element's stage completes, then runs the line with its value, as {@link #pausingEffect} describes.
     *
     * @param id the expression's id
     * @param returnType the id of the type it returns, or {@code object} for any
     * @param factory makes the expression for each place it's used
     * @param patterns its patterns
     * @see co.xenastudios.neoskript.api.element.PausingExpression
     */
    void pausingExpression(String id, String returnType,
            ElementFactory<co.xenastudios.neoskript.api.element.PausingExpression> factory, String... patterns);

    /**
     * How long a trigger waits for pausing effect or expression {@code id} before it gives up: a runtime error on
     * the line, after which the trigger carries on without the result, and NeoSkript tries to cancel the stage.
     * Without this, it's one minute. An element whose work has a time limit of its own (a query, a request) should
     * set this to match it, so scripts see the addon's error rather than NeoSkript's.
     *
     * @param id the element's id: one this addon registered with {@link #pausingEffect} or
     *     {@link #pausingExpression}
     * @param timeout how long, more than zero
     * @throws IllegalArgumentException if the timeout isn't more than zero, or the id isn't one of this addon's
     *     pausing elements
     */
    void timeout(String id, java.time.Duration timeout);

    /**
     * An expression whose patterns name a type in each {@code <...>} part, like Skript's classinfo placeholders:
     * {@code [the] [<.+>] data tag %text% of %objects%}. The types matched reach the factory in
     * {@link co.xenastudios.neoskript.api.element.ElementInfo#types()}.
     *
     * @param id the expression's id
     * @param returnType the id of the type it returns, or {@code object}; see {@link #returnType} for one that
     *     depends on the types matched
     * @param factory makes the expression for each place it's used
     * @param patterns its patterns
     */
    void typedExpression(String id, String returnType, ElementFactory<Expression> factory, String... patterns);

    /**
     * Lets expression {@code expressionId} take a section, as Skript's expression sections do:
     * {@code set {_border} to a worldborder:} runs the code under it for the value made, whose type is
     * {@code valueType} ({@code event-worldborder}, or left out where a world border goes), before setting it.
     *
     * @param expressionId the expression's id
     * @param valueType the id of the type of the value made
     */
    void sectionExpression(String expressionId, String valueType);

    /**
     * The code in expression {@code expressionId}'s section is in an event of its own, called {@code eventName}
     * in errors, as Skript's section expressions load it: the trigger's event values can't be used there
     * ({@code There's no player in a create bossbar event.}), only the value being made.
     *
     * @param expressionId the expression's id
     * @param eventName the event's name in errors
     */
    @Experimental
    default void sectionEvent(String expressionId, String eventName) {}

    /**
     * A {@code loop-<name>} for loops over expression {@code expressionId}, as skript-yaml's {@code loop-node} in
     * {@code loop yaml node keys "x" from "config"}. Like {@code loop-value}, it can be written {@code loop-node-2} for
     * the second such loop out. Only loops over that expression have it: elsewhere the line has Skript's error,
     * "There's no loop that matches 'loop-node'". In a loop over that expression it comes before a type of the same
     * name, as Skript ends up with skript-yaml's {@code loop-node} though Skript has a {@code node} type. Names Skript
     * gives every loop ({@code value}, {@code index}, {@code counter}, {@code iteration}) can't be used.
     * @param expressionId the expression's id, one of the addon's own
     * @param name the name after {@code loop-}, in lower case, as {@code node}
     * @param factory makes the value for each place it's used, from how the looped expression was matched there;
     *     it throws {@link co.xenastudios.neoskript.api.element.SyntaxError} where that loop has no such value
     */
    @Experimental
    void loopValue(String expressionId, String name,
            ElementFactory<co.xenastudios.neoskript.api.element.LoopValue> factory);

    /**
     * Marks expression {@code expressionId} as an event value, as Skript's {@code EventValueExpression}s are
     * ({@code the created damage source}): as an argument, it counts as a default one
     * ({@link co.xenastudios.neoskript.api.element.ElementInfo#isDefault(int)}).
     *
     * @param expressionId the expression's id
     */
    @Experimental
    default void eventValueExpression(String expressionId) {}

    /**
     * Lets expression {@code expressionId} take a section that runs later, as Skript's test runnables do:
     * {@code set {_r} to a new runnable:}. The expression's value is the section itself, a {@link Runnable}
     * that runs the code under it with the local variables of the trigger it was made in.
     *
     * @param expressionId the expression's id
     */
    void deferredSectionExpression(String expressionId);

    /**
     * Makes the elements registered as {@code id} experimental, as Skript's experimental syntax: only scripts
     * {@code using} the experiment (by its code name, like {@code reflection}) can use them. Elsewhere they don't
     * match, and if nothing else does, the line's error says to enable it.
     *
     * @param id the elements' id
     * @param experiment the experiment's code name
     */
    default void experimental(String id, String experiment) {
        experimental(id, experiment, false);
    }

    /**
     * {@link #experimental(String, String)}, but if {@code silent}, without saying so in the line's error.
     *
     * @param id the elements' id
     * @param experiment the experiment's code name
     * @param silent whether the line's error leaves it out
     */
    default void experimental(String id, String experiment, boolean silent) {}

    /**
     * Marks where the library's syntax that matches almost anything begins, as Skript's
     * {@code PATTERN_MATCHES_EVERYTHING} expressions ({@code 3 of stone}): what's registered after this is tried
     * after the built-in syntax Skript tries before those, as time states ({@code past level of player}).
     */
    @Internal
    default void lateSyntax() {}

    /**
     * Where Skript's parser tries syntax element {@code id}, one of this addon's: after Skript's own syntax of the same
     * priority, and before syntax of a later one, as Skript orders an addon's syntax. Without it, an addon's syntax is
     * tried after the built-in syntax registered before it. Use it where an addon's syntax is a Skript addon's, so a
     * line that both it and a built-in element match is read as Skript reads it: skJson's {@code keys of %json%},
     * {@link SyntaxPriority#SIMPLE}, is tried before Skript's {@code key of %minecraft tag%}, a property.
     *
     * @param id the element's id, of any kind
     * @param priority its priority, as the Skript addon registers it
     */
    @Experimental
    default void priority(String id, SyntaxPriority priority) {}

    /**
     * Has effect {@code effectId} tried before effect {@code otherId}, as Skript tries its effects in its own order:
     * {@code clear drops} is its cancel drops effect, which comes before its change effect.
     *
     * @param effectId the effect tried first
     * @param otherId the effect tried after it
     */
    @Internal
    default void effectBefore(String effectId, String otherId) {}

    /**
     * Lets expression {@code expressionId} be written with a time state, as Skript's {@code setTime}:
     * {@code past tool}, {@code the future gamemode of player}, {@code X was Y}. {@code accepts} gets the
     * element as matched with the time state wanted ({@link co.xenastudios.neoskript.api.element.ElementInfo#time()},
     * -1 or 1) and says whether it has one there; where it does, the element is made with that
     * {@code ElementInfo} and gives its value before or after the event. Where it doesn't, the script has an
     * error, "... does not have a past state".
     *
     * @param expressionId the expression's id
     * @param accepts whether it has the time state wanted, where it's matched
     */
    void timeStates(String expressionId,
            java.util.function.Predicate<co.xenastudios.neoskript.api.element.ElementInfo> accepts);


    /**
     * {@link #timeStates(String, java.util.function.Predicate)} in events of {@code eventClasses} only, and
     * where {@code defaultArgument} isn't negative, only when that argument is left out or an event value
     * ({@code player}, not {@code {_p}}), as Skript's {@code setTime(time, getExpr(), events...)}.
     *
     * @param expressionId the expression's id
     * @param defaultArgument the argument that has to be left out or an event value, or -1 for none
     * @param eventClasses the events it has time states in
     */
    default void timeStates(String expressionId, int defaultArgument, Class<?>... eventClasses) {
        timeStates(expressionId, info -> (defaultArgument < 0 || info.isDefault(defaultArgument))
                && info.isEvent(eventClasses));
    }

    /**
     * A property expression, {@code [the] <property> of %owners%} and {@code %owners%'[s] <property>}: the
     * value of {@code getter} for each owner of {@code ownerClass}. Owners of other classes, and
     * {@code null} results, are left out.
     *
     * @param <T> the owners' class
     * @param id the expression's id
     * @param returnType the id of the type it returns
     * @param property the property's words, as {@code wave count}
     * @param owners the owners' type, plural, as in a placeholder: {@code players}
     * @param ownerClass the owners' class
     * @param getter the property of an owner, or {@code null} for none
     */
    default <T> void property(String id, String returnType, String property, String owners, Class<T> ownerClass,
            java.util.function.Function<? super T, ?> getter) {
        expression(id, returnType, info -> (context, arguments) -> Properties.map(arguments[0], ownerClass, getter),
                Patterns.property(property, owners));
    }

    /**
     * An event NeoSkript fires itself, like a script loading.
     *
     * @param id the event's id
     * @param event which of NeoSkript's events it is
     * @param patterns its patterns, without {@code on}: {@code [script] load}
     */
    void event(String id, ScriptEvent event, String... patterns);

    /**
     * A server event. Triggers run when an event of one of {@code eventClasses} fires, and {@code filter},
     * if there is one, passes. The filter gets the values of the pattern's placeholders, as in
     * {@code on break of stone}. The event classes are Bukkit's {@code Event} subclasses, an addon's own among them,
     * which need the usual {@code getHandlerList}.
     *
     * @param id the event's id
     * @param eventClasses the classes of the events it's for
     * @param filter makes the filter for each trigger, or {@code null} for none; one that also narrows the classes
     *     listened to is a {@link co.xenastudios.neoskript.api.element.EventFilter}
     * @param patterns its patterns, without {@code on}: {@code wave [at %-players%]}
     */
    void event(String id, List<Class<?>> eventClasses, ElementFactory<Condition> filter, String... patterns);

    /**
     * Says that pattern {@code pattern} of syntax {@code id} only works in events of {@code eventClasses} (or their
     * subclasses), as Skript's elements whose set-up checks the event they're in. Elsewhere, a line's other readings
     * are tried first, as Skript tries the next element when one fails to set up: {@code the result} is a
     * placeholder's result in a placeholder and the smelted item in a smelting event. Only if none fits is this one
     * used, and its factory gives its error.
     *
     * @param id the syntax's id
     * @param pattern the index of the pattern, or -1 for all of them
     * @param eventClasses the classes of the events it works in
     */
    @Experimental
    default void onlyInEvents(String id, int pattern, Class<?>... eventClasses) {}

    /**
     * Tells the addon which triggers of its server event {@code id} are loaded, for an event it runs itself with
     * {@link LoadedTrigger#run} rather than firing it through Bukkit: {@code loaded} gets every such trigger of the
     * loaded scripts, in their order, each time scripts load, reload or unload (an empty list when none are left).
     * It's called on the thread that loaded them, the global thread on Folia; an exception from it is logged. Each
     * list replaces the one before: drop the old triggers, which run nothing once their script has unloaded.
     *
     * @param id the id of one of this addon's server events
     * @param loaded what gets the loaded triggers
     */
    default void eventTriggers(String id, java.util.function.Consumer<? super List<LoadedTrigger>> loaded) {}

    /**
     * One of Skript's events, as Skript names it in errors ({@code On Break / Mine}), whether NeoSkript has it or
     * not: a trigger of an event registered with the same patterns is {@code an on break / mine event} in errors,
     * and syntax that only works in some events names those registered so, in the order they were registered.
     *
     * @param name the event's name, as Skript writes it
     * @param eventClasses its classes
     * @param patterns its patterns
     */
    @Internal
    default void eventName(String name, List<Class<?>> eventClasses, String... patterns) {}

    /**
     * A value triggers can get from events of {@code eventClass} (or its subclasses), as {@code event-<type>}
     * or by the type's name ({@code player}). The most specific event class wins.
     *
     * @param <E> the event's class
     * @param eventClass the event's class
     * @param typeId the id of the value's type
     * @param getter the value of an event, or {@code null} for none
     */
    <E> void eventValue(Class<E> eventClass, String typeId, java.util.function.Function<? super E, ?> getter);

    /**
     * An event value at a time state, as Skript's: {@code past event-block} ({@code time} -1) is the block before
     * the event, {@code future event-item} (1) the item after it. 0 is the value now, as
     * {@link #eventValue(Class, String, java.util.function.Function)} registers. Where an event has no value for
     * a time state, the value now is used, but a time state can only be written where some value has one.
     *
     * @param <E> the event's class
     * @param eventClass the event's class
     * @param typeId the id of the value's type
     * @param time -1 for the value before the event, 0 now, 1 after it
     * @param getter the value of an event, or {@code null} for none
     */
    <E> void eventValue(Class<E> eventClass, String typeId, int time, java.util.function.Function<? super E, ?> getter);

    /**
     * Events of {@code eventClass} (and subclasses) don't have a value of {@code typeId}, though a superclass
     * does: asking for it is an error with {@code message}, as Skript's {@code event-entity} in a damage event.
     *
     * @param eventClass the event's class
     * @param typeId the id of the value's type
     * @param message the error
     */
    @Experimental
    void eventValueExcluded(Class<?> eventClass, String typeId, String message);

    /**
     * The time states of events of {@code eventClass}'s values of {@code typeId} are only for the type's bare name
     * ({@code former world}), not {@code event-<type>}, as in Skript, where the bare name is an expression with
     * time states of its own ({@code ExprWorld}), and the event value has none.
     *
     * @param eventClass the event's class
     * @param typeId the id of the value's type
     */
    @Internal
    default void bareTimeStates(Class<?> eventClass, String typeId) {}

    /**
     * An event value of {@code typeId} that's only for a narrower kind, whose values are {@code valueClass}:
     * {@code event-projectile} in a damage event is the projectile that hit, though {@code event-entity} isn't.
     * Where a trigger asks for a kind (see {@link #kindClass}) that an event has no such value for, its plain value
     * is used if it's of that kind.
     *
     * @param <E> the event's class
     * @param eventClass the event's class
     * @param typeId the id of the value's type
     * @param valueClass the class of the narrower kind
     * @param time -1 for the value before the event, 0 now, 1 after it
     * @param getter the value of an event, or {@code null} for none
     */
    @Experimental
    <E> void eventValue(Class<E> eventClass, String typeId, Class<?> valueClass, int time,
            java.util.function.Function<? super E, ?> getter);

    /**
     * What class of values a literal of {@code typeId} stands for, as an entity type stands for its entities:
     * {@code event-projectile} asks for an event value of the class {@code projectile} gives.
     *
     * @param typeId the type's id
     * @param classOf the class a literal stands for, or {@code null} if it doesn't stand for one
     */
    @Experimental
    void kindClass(String typeId, java.util.function.Function<Object, Class<?>> classOf);

    /**
     * Where a placeholder of {@code typeId} sits in an optional part that wasn't used, it gets the event's
     * value of that type instead, if the event has one: {@code send "hi"} goes to the event's player.
     *
     * @param typeId the type's id
     */
    void eventDefault(String typeId);

    /**
     * {@link #eventDefault(String)} with the event's value of another class, as Skript's default expressions may
     * be: a left-out audience is the event's command sender, since an event may have several audiences.
     *
     * @param typeId the type's id
     * @param valueClass the class of the event value used
     */
    @Experimental
    default void eventDefault(String typeId, Class<?> valueClass) {
        eventDefault(typeId);
    }

    /**
     * Where a placeholder of {@code typeId} sits in an optional part that wasn't used and the event has no
     * value of it, it gets {@code value}, as Skript's literal default expressions: a number left out is 1.
     *
     * @param typeId the type's id
     * @param value the value
     */
    void defaultValue(String typeId, Object value);

    /**
     * Registers a built-in function. Scripts can define their own functions with the same name, which
     * take precedence.
     *
     * @param name the function's name, as scripts call it
     * @param parameters its parameters, in order
     * @param returnType the id of the type it returns, or {@code null} if it returns nothing
     * @param returnsPlural whether it returns several values
     * @param function what it does
     */
    void function(String name, List<Parameter> parameters, String returnType, boolean returnsPlural,
            Function function);

    /**
     * How values of {@code type} are saved in variables files, as Skript's type {@code name} (its type
     * column in {@code variables.csv}, e.g. {@code location}). An addon's own type needs a name Skript doesn't use,
     * and an addon can't register a codec for a class NeoSkript (or another addon) already saves.
     *
     * @param <T> the values' class
     * @param name the name saved with each value
     * @param type the values' class
     * @param codec how they're written and read
     * @throws IllegalArgumentException for an addon, if {@code type} already has a codec
     */
    <T> void storage(String name, Class<T> type, co.xenastudios.neoskript.api.storage.VariableCodec<T> codec);

    /**
     * Marks {@code typeId} as one Skript can't save in variables, for its warning where a script sets a global
     * variable to one: "{@code named} cannot be saved...". {@code named} is the type's name with its article, as
     * Skript writes it ({@code an entity}).
     *
     * @param typeId the type's id
     * @param named the type's name with its article
     */
    @Experimental
    default void unsavable(String typeId, String named) {}

    /**
     * Values of {@code type} (and its subclasses) aren't saved in variables files, even where they're also of a
     * type that is, as Skript has a type for them that it can't save: money is a number, but isn't saved.
     *
     * @param type the values' class
     */
    @Experimental
    default void unsavable(Class<?> type) {}

    /**
     * Types that have Skript 2.17's type property {@code property} ({@code name}, {@code amount}...), as its types
     * declare them, for {@link #propertySyntax}.
     *
     * @param property the property's name
     * @param typeIds the types' ids
     */
    @Experimental
    default void typeProperty(String property, String... typeIds) {}

    /**
     * Marks syntax {@code syntaxId} as one of Skript 2.17's type properties: where the values of its argument
     * {@code argument} are of a type that can't have the property, it doesn't parse, with Skript's error
     * ("The expression '5' returns the following types that do not have the name property: integer").
     *
     * @param property the property's name by the tags the syntax was written with, as Skript's errors name it
     *     and {@link #typeProperty} registers the types that have it
     * @param counts whether, given several values and not written in the plural ({@code amount of X and Y}), the
     *     syntax counts them instead, as Skript's amount, size and number do
     * @param syntaxId the syntax's id
     * @param argument the index of the argument whose values have to have the property
     */
    @Experimental
    default void propertySyntax(String syntaxId, int argument, java.util.function.Function<java.util.Set<String>, String> property,
            boolean counts) {}

    /**
     * Words scripts can write as values of {@code typeId}, like item names ({@code 2 stone swords}). The
     * parser returns the value, or {@code null} for text that isn't one. Literals are compiled once and
     * shared, so values should be immutable or copied by the syntax that changes them.
     *
     * @param typeId the type's id
     * @param parser the value written, or {@code null}
     */
    void literalParser(String typeId, java.util.function.Function<String, ?> parser);

    /**
     * What's wrong with words written as a value that no literal parser understands, for an error clearer than
     * Skript's "can't understand", as Skript gives for some: {@code dust particle} needs its colour. {@code problem}
     * returns the error, or {@code null} if it has nothing to say about the words.
     *
     * @param problem the error for words, or {@code null}
     */
    @Experimental
    void literalError(java.util.function.Function<String, String> problem);

    /**
     * How Skript writes syntax {@code id} in its messages, its {@code toString}, where that isn't the syntax as
     * written: {@code the IP address} for {@code IP-address}, as its warnings quote it ("the IP address is
     * already a text"). Without one, messages quote the syntax as written.
     *
     * @param id the syntax's id
     * @param describer how it's written
     */
    @Experimental
    default void describe(String id, Describer describer) {}

    /**
     * What expression {@code id} gives, by how it's matched, where it's registered as giving any type: as Skript's
     * {@code getReturnType} once set up ({@code the clicked block} is a block). {@code type} returns a type's id, or
     * {@code null} for any type.
     *
     * @param id the expression's id
     * @param type the id of the type it gives where it's matched so, or {@code null}
     */
    @Experimental
    default void returnType(String id, java.util.function.Function<co.xenastudios.neoskript.api.element.ElementInfo, String> type) {}

    /** Writes an element as Skript's {@code toString} does. */
    @Experimental
    @FunctionalInterface
    interface Describer {

        /**
         * Writes an element.
         *
         * @param info how the element was matched
         * @param arguments each argument written as Skript writes it, or {@code null} where it was left out
         * @return the element as Skript writes it
         */
        String describe(co.xenastudios.neoskript.api.element.ElementInfo info, List<String> arguments);
    }

    /**
     * How text people type, such as a command's arguments, becomes a value of {@code typeId}. The parser
     * returns {@code null} for text that isn't one.
     *
     * @param typeId the type's id
     * @param parser the value typed, or {@code null}
     */
    void parser(String typeId, java.util.function.Function<String, ?> parser);

    /**
     * How a value of {@code from} becomes a {@code to} where a placeholder wants one: an entity where a
     * location is expected is the entity's location. The converter returns {@code null} if it can't.
     *
     * @param <F> the class converted from
     * @param <T> the class converted to
     * @param from the class converted from
     * @param to the class converted to
     * @param converter the value as a {@code to}, or {@code null}
     */
    <F, T> void converter(Class<F> from, Class<T> to, java.util.function.Function<? super F, ? extends T> converter);

    /** A converter that may follow another in a chain of converters, but not come before one: Skript's flag. */
    int NO_LEFT_CHAINING = 1;
    /** A converter that may come before another in a chain, but not follow one: Skript's flag. */
    int NO_RIGHT_CHAINING = 2;
    /** A converter that isn't chained with others. */
    int NO_CHAINING = NO_LEFT_CHAINING | NO_RIGHT_CHAINING;
    /** A converter used even where it would only be for some of the values, as Skript's flag. */
    int ALLOW_UNSAFE_CASTS = 4;
    /**
     * A converter Skript doesn't have, used only where a placeholder wants a value of the other type: not to get
     * event values ({@code event-block} isn't the block at the event's location) as Skript's converters are.
     */
    @Internal
    int PLACEHOLDERS_ONLY = 16;
    /**
     * One of Skript's converters NeoSkript uses only to get event values ({@code event-entitytype} in a join event
     * is the player's type), not where placeholders want the type.
     */
    @Internal
    int EVENT_VALUES_ONLY = 32;

    /**
     * {@link #converter(Class, Class, java.util.function.Function)} with Skript's flags for chaining it with
     * others ({@link #NO_LEFT_CHAINING}, {@link #NO_RIGHT_CHAINING}, ...). Converters are chained as Skript's
     * are when looking for how to get an event value of a type the event has none of.
     *
     * @param <F> the class converted from
     * @param <T> the class converted to
     * @param from the class converted from
     * @param to the class converted to
     * @param converter the value as a {@code to}, or {@code null}
     * @param flags the flags, or 0 for none
     */
    default <F, T> void converter(Class<F> from, Class<T> to, java.util.function.Function<? super F, ? extends T> converter,
            int flags) {
        converter(from, to, converter);
    }

    /**
     * How {@code set}/{@code add}/{@code remove}... change a value of {@code owner} that's the target
     * itself, rather than a variable or a changeable expression: {@code give a diamond to player} adds it to
     * the player's inventory. {@code change} gets the owner and the values given.
     *
     * @param <T> the owner's class
     * @param owner the owner's class
     * @param mode how it's changed
     * @param change changes an owner with the values given ({@code null} for delete and reset)
     */
    <T> void changer(Class<T> owner, co.xenastudios.neoskript.api.element.Changeable.Mode mode,
            java.util.function.BiConsumer<? super T, Object[]> change);

    /**
     * What a value of {@code owner} that's the target itself takes for {@code mode}, as Skript's type's changer
     * says: what's given is checked against it when the script loads, with Skript's error if it can't be one
     * ({@code 'a plank' can't be added to an inventory because the former is neither an item type nor an
     * inventory}). Without it, what's given is only checked when it runs.
     *
     * @param owner the owner's class
     * @param mode how it's changed
     * @param accepted the classes of the values it takes
     */
    @Experimental
    default void changerAccepts(Class<?> owner, co.xenastudios.neoskript.api.element.Changeable.Mode mode,
            Class<?>... accepted) {}

    /**
     * How values of {@code type}, which can be changed in place, are copied when stored in a variable, as
     * Skript copies them: changing an item in a variable doesn't change the item it was set from.
     *
     * @param <T> the values' class
     * @param type the values' class
     * @param cloner a copy of a value
     */
    <T> void cloner(Class<T> type, java.util.function.UnaryOperator<T> cloner);

    /**
     * What {@code is} means between a value of {@code left} and one of {@code right} (checked either way
     * round, with the {@code left} value first): {@code {_entity} is a pig}, {@code lucy axolotl is axolotl}.
     *
     * @param <A> the left value's class
     * @param <B> the right value's class
     * @param left the left value's class
     * @param right the right value's class
     * @param test whether they're equal
     */
    <A, B> void comparison(Class<A> left, Class<B> right, java.util.function.BiPredicate<? super A, ? super B> test);

    /**
     * What {@code operator} ({@code + - * / ^}) gives between a value of {@code left} and one of
     * {@code right}, in that order: {@code {_vector} * 2}. Returning {@code null} gives nothing.
     *
     * @param <A> the left operand's class
     * @param <B> the right operand's class
     * @param operator one of {@code + - * / ^}
     * @param left the left operand's class
     * @param right the right operand's class
     * @param apply the result, or {@code null}
     */
    <A, B> void arithmetic(char operator, Class<A> left, Class<B> right,
            java.util.function.BiFunction<? super A, ? super B, ?> apply);

    /**
     * What a missing operand counts as in arithmetic next to a value of {@code type}, as 0 does for numbers.
     *
     * @param <T> the values' class
     * @param type the values' class
     * @param value the operand
     */
    <T> void arithmeticDefault(Class<T> type, java.util.function.Supplier<? extends T> value);

    /**
     * How values of {@code type} read as text, as when embedded in a string ({@code "%{_x}%"}).
     *
     * @param <T> the values' class
     * @param type the values' class
     * @param toText the value as text
     */
    <T> void text(Class<T> type, java.util.function.Function<? super T, String> toText);

    /**
     * How values of {@code type} read in variable names ({@code {_x::%block%}}) where that isn't their text,
     * as with Skript's {@code toVariableNameString}.
     *
     * @param <T> the values' class
     * @param type the values' class
     * @param toText the value as it reads in a variable name
     */
    <T> void variableNameText(Class<T> type, java.util.function.Function<? super T, String> toText);

    /**
     * Which thread element {@code id} has to run on, on Folia. Without this, it's {@link ThreadAffinity#OWNER}.
     * On Paper everything runs on the main thread, and this changes nothing.
     *
     * @param id the element's id: one this addon registered, without {@code -}
     * @param affinity which thread it needs
     * @throws IllegalArgumentException if the id has a {@code -}, or isn't one of this addon's elements, or is also
     *     the id of one of NeoSkript's elements or another addon's
     */
    default void affinity(String id, ThreadAffinity affinity) {}
}
