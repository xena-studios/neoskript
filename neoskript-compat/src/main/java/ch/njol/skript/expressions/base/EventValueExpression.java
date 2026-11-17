/*
 * Copyright (c) 2026 Xena Studios
 * SPDX-License-Identifier: GPL-3.0-only
 */
package ch.njol.skript.expressions.base;

import ch.njol.skript.classes.Changer;
import ch.njol.skript.lang.DefaultExpression;
import ch.njol.skript.lang.Expression;
import ch.njol.skript.lang.SkriptParser.ParseResult;
import ch.njol.skript.lang.util.SimpleExpression;
import ch.njol.util.Kleenean;
import java.lang.reflect.Array;
import org.bukkit.event.Event;
import org.skriptlang.skript.bukkit.lang.eventvalue.EventValue;
import org.skriptlang.skript.registration.SyntaxInfo;
import org.skriptlang.skript.util.Priority;

/**
 * An event's value of a type, as {@code event-block}: the event values addons registered with NeoSkriptCompat. A
 * type's default value is NeoSkript's own, not this.
 */
public class EventValueExpression<T> extends SimpleExpression<T> implements DefaultExpression<T> {

    public static final Priority DEFAULT_PRIORITY = Priority.before(SyntaxInfo.PATTERN_MATCHES_EVERYTHING);

    private final Class<? extends T> type;
    private final boolean single;

    public EventValueExpression(Class<? extends T> type) {
        this(type, null, false);
    }

    public EventValueExpression(Class<? extends T> type, boolean exact) {
        this(type, null, exact);
    }

    public EventValueExpression(Class<? extends T> type, Changer<? super T> changer) {
        this(type, changer, false);
    }

    public EventValueExpression(Class<? extends T> type, Changer<? super T> changer, boolean exact) {
        this.type = type.isArray() ? componentOf(type) : type;
        this.single = !type.isArray();
    }

    @SuppressWarnings("unchecked")
    private static <T> Class<? extends T> componentOf(Class<? extends T> type) {
        return (Class<? extends T>) type.getComponentType();
    }

    @Override
    public boolean init(Expression<?>[] expressions, int matchedPattern, Kleenean isDelayed, ParseResult parseResult) {
        return true;
    }

    @Override
    public boolean init() {
        return true;
    }

    @Override
    @SuppressWarnings("unchecked")
    protected T[] get(Event event) {
        for (EventValue<?, ?> value : co.xenastudios.neoskript.compat.Compat.get().eventValues().elements()) {
            if (value.eventClass().isInstance(event) && type.isAssignableFrom(value.valueClass())
                    && value.time() == EventValue.Time.NOW) {
                Object got = ((EventValue<Event, ?>) value).get(event);
                if (got != null) {
                    T[] one = (T[]) Array.newInstance(type, 1);
                    one[0] = (T) got;
                    return one;
                }
            }
        }
        return (T[]) Array.newInstance(type, 0);
    }

    @Override
    public boolean isDefault() {
        return true;
    }

    @Override
    public boolean isSingle() {
        return single;
    }

    @Override
    public Class<? extends T> getReturnType() {
        return type;
    }

    @Override
    public String toString(Event event, boolean debug) {
        return "event-" + type.getSimpleName().toLowerCase(java.util.Locale.ENGLISH);
    }
}
