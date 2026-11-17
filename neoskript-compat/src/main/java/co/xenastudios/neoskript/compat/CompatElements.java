/*
 * Copyright (c) 2026 Xena Studios
 * SPDX-License-Identifier: GPL-3.0-only
 */
package co.xenastudios.neoskript.compat;

import ch.njol.skript.classes.Changer.ChangeMode;
import ch.njol.skript.classes.ClassInfo;
import ch.njol.skript.lang.SkriptParser.ParseResult;
import ch.njol.skript.lang.SyntaxElement;
import ch.njol.skript.lang.parser.ParserInstance;
import ch.njol.util.Kleenean;
import co.xenastudios.neoskript.api.element.Changeable;
import co.xenastudios.neoskript.api.element.Condition;
import co.xenastudios.neoskript.api.element.Effect;
import co.xenastudios.neoskript.api.element.ElementInfo;
import co.xenastudios.neoskript.api.element.Expression;
import co.xenastudios.neoskript.api.element.SyntaxError;
import co.xenastudios.neoskript.api.element.TriggerContext;
import co.xenastudios.neoskript.api.registration.Registrar;
import java.lang.reflect.Array;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;
import org.bukkit.event.Event;
import org.skriptlang.skript.registration.DefaultSyntaxInfos;
import org.skriptlang.skript.registration.SyntaxInfo;

/**
 * An addon's expressions, effects and conditions as NeoSkript's. Where a script uses one, a new element is made and
 * set up ({@code init}) with its arguments as expressions; when the line runs, NeoSkript works the arguments out and
 * the element is run with them.
 */
final class CompatElements {

    private static final Set<String> IDS = ConcurrentHashMap.newKeySet();

    private CompatElements() {}

    /** A placeholder of a pattern: the class its values are. */
    record Placeholder(Class<?> type) {}

    static void expression(Compat compat, Registrar registrar, String addon, SyntaxInfo<?> info) {
        Class<?> returns = info instanceof DefaultSyntaxInfos.Expression<?, ?> expression ? expression.returnType()
                : Object.class;
        List<List<Placeholder>> placeholders = placeholders(compat, info);
        registrar.expression(id(addon, info), compat.typeId(returns), element -> {
            ch.njol.skript.lang.Expression<?> expression = setup(compat, addon, info, placeholders, element,
                    "expression");
            return new ExpressionAdapter(expression, changes(expression));
        }, patterns(info));
    }

    static void effect(Compat compat, Registrar registrar, String addon, SyntaxInfo<?> info) {
        List<List<Placeholder>> placeholders = placeholders(compat, info);
        registrar.effect(id(addon, info), element -> {
            ch.njol.skript.lang.Effect effect = setup(compat, addon, info, placeholders, element, "effect");
            return (Effect) (context, arguments) -> CompatArgument.with(arguments,
                    () -> effect.run(CompatEvent.of(context.event())));
        }, patterns(info));
    }

    static void condition(Compat compat, Registrar registrar, String addon, SyntaxInfo<?> info) {
        List<List<Placeholder>> placeholders = placeholders(compat, info);
        registrar.condition(id(addon, info), element -> {
            ch.njol.skript.lang.Condition condition = setup(compat, addon, info, placeholders, element, "condition");
            return (Condition) (context, arguments) -> CompatArgument.with(arguments,
                    () -> condition.check(CompatEvent.of(context.event())));
        }, patterns(info));
    }

    /** The element's id: the addon's name and the element's class, as {@code SkBee:ExprWorldHeight}. */
    private static String id(String addon, SyntaxInfo<?> info) {
        String base = addon + ":" + info.type().getSimpleName();
        String id = base;
        for (int i = 2; !IDS.add(id); i++) {
            id = base + "#" + i;
        }
        return id;
    }

    private static String[] patterns(SyntaxInfo<?> info) {
        return info.patterns().toArray(String[]::new);
    }

    /** A new element for a line, set up for it: a syntax error if it won't be, as Skript gives. */
    @SuppressWarnings("unchecked")
    private static <E extends SyntaxElement> E setup(Compat compat, String addon, SyntaxInfo<?> info,
            List<List<Placeholder>> placeholders, ElementInfo element, String kind) {
        String text = element.textOr("");
        E instance;
        try {
            instance = (E) info.instance();
        } catch (RuntimeException | LinkageError e) {
            compat.logger().log(Level.WARNING, addon + " couldn't make its " + kind + " " + info.type().getName(), e);
            throw new SyntaxError(addon + "'s " + kind + " couldn't be used: " + e);
        }
        List<Placeholder> pattern = element.pattern() < placeholders.size() ? placeholders.get(element.pattern())
                : List.of();
        int count = Math.max(pattern.size(), element.argumentTypes().size());
        ch.njol.skript.lang.Expression<?>[] arguments = new ch.njol.skript.lang.Expression<?>[count];
        for (int i = 0; i < count; i++) {
            if (i >= element.argumentTypes().size() || omitted(element, i)) {
                continue;
            }
            Class<?> type = i < pattern.size() ? pattern.get(i).type() : null;
            if (type == null || type == Object.class) {
                String id = element.argumentType(i);
                Class<?> javaClass = id == null ? null : compat.library().javaClass(id);
                javaClass = javaClass == null ? null : CompatAdapters.skriptClass(javaClass);
                type = javaClass == null ? Object.class : javaClass;
            }
            arguments[i] = argument(i, type, element);
        }
        ParseResult result = new ParseResult(text, arguments);
        result.mark = element.mark();
        result.tags.addAll(element.tags());
        ParserInstance parser = ParserInstance.get();
        Class<? extends Event>[] events = parser.getCurrentEvents();
        String eventName = parser.getCurrentEventName();
        parser.setCurrentEvent(element.eventName(), eventClasses(element));
        boolean[] ok = {false};
        String error;
        try {
            error = Compat.withSetupErrors(() -> ok[0] = instance.preInit()
                    && instance.init(arguments, element.pattern(), Kleenean.FALSE, result));
        } catch (RuntimeException | LinkageError e) {
            compat.logger().log(Level.WARNING, addon + "'s " + kind + " failed to set up for '" + text + "'", e);
            throw new SyntaxError(addon + "'s " + kind + " failed to set up for '" + text + "': " + e);
        } finally {
            parser.setCurrentEvent(eventName, events);
            if (events == null) {
                parser.reset();
            }
        }
        if (!ok[0]) {
            throw new SyntaxError(error != null ? error : "Can't understand this " + kind + ": '" + text + "'");
        }
        return instance;
    }

    /** Whether an optional placeholder was left out: Skript gives null for it. */
    private static boolean omitted(ElementInfo element, int index) {
        return element.isDefault(index) && element.argumentType(index) == null && element.argumentLiteral(index) == null;
    }

    private static <T> CompatArgument<T> argument(int index, Class<T> type, ElementInfo element) {
        return new CompatArgument<>(index, type, element.isSingle(index), element.and(index), element.isDefault(index),
                element.argumentLiteral(index));
    }

    @SuppressWarnings("unchecked")
    private static Class<? extends Event>[] eventClasses(ElementInfo element) {
        List<Class<? extends Event>> events = new ArrayList<>();
        for (Class<?> event : element.eventClasses()) {
            if (Event.class.isAssignableFrom(event)) {
                events.add((Class<? extends Event>) event);
            }
        }
        return events.toArray(Class[]::new);
    }

    /** What the expression can be changed with, by mode, asked while it's set up as Skript asks. */
    private static Map<ChangeMode, Class<?>[]> changes(ch.njol.skript.lang.Expression<?> expression) {
        Map<ChangeMode, Class<?>[]> changes = new EnumMap<>(ChangeMode.class);
        for (ChangeMode mode : ChangeMode.values()) {
            Class<?>[] accepted = expression.acceptChange(mode);
            if (accepted != null) {
                changes.put(mode, accepted.clone());
            }
        }
        return changes;
    }

    // ------------------------------------------------------------ placeholders

    /** Each pattern's placeholders, in order: the class of each, from its first type. */
    private static List<List<Placeholder>> placeholders(Compat compat, SyntaxInfo<?> info) {
        List<List<Placeholder>> all = new ArrayList<>();
        for (String pattern : info.patterns()) {
            all.add(placeholders(compat, pattern));
        }
        return all;
    }

    static List<Placeholder> placeholders(Compat compat, String pattern) {
        List<Placeholder> placeholders = new ArrayList<>();
        for (int i = 0; i < pattern.length(); i++) {
            char c = pattern.charAt(i);
            if (c == '\\') {
                i++;
                continue;
            }
            if (c != '%') {
                continue;
            }
            int end = pattern.indexOf('%', i + 1);
            if (end < 0) {
                break;
            }
            placeholders.add(new Placeholder(placeholderClass(compat, pattern.substring(i + 1, end))));
            i = end;
        }
        return placeholders;
    }

    private static Class<?> placeholderClass(Compat compat, String placeholder) {
        String types = placeholder.replaceFirst("^[-*~]+", "");
        int time = types.indexOf('@');
        if (time >= 0) {
            types = types.substring(0, time);
        }
        Class<?> common = null;
        for (String type : types.split("/")) {
            Class<?> javaClass = null;
            for (String candidate : CompatTypes.singulars(type.strip())) {
                ClassInfo<?> info = compat.types().byCodeName(candidate);
                if (info != null) {
                    javaClass = info.getC();
                    break;
                }
            }
            if (javaClass == null) {
                return Object.class;
            }
            common = common == null ? javaClass : commonSuper(common, javaClass);
        }
        return common == null ? Object.class : common;
    }

    private static Class<?> commonSuper(Class<?> a, Class<?> b) {
        if (a.isAssignableFrom(b)) {
            return a;
        }
        if (b.isAssignableFrom(a)) {
            return b;
        }
        return Object.class;
    }

    // ------------------------------------------------------------ expressions

    /** An addon's expression as NeoSkript's: its values, and changing it. */
    private static final class ExpressionAdapter implements Expression, Changeable {

        private final ch.njol.skript.lang.Expression<?> expression;
        private final Map<ChangeMode, Class<?>[]> changes;
        private final boolean single;

        ExpressionAdapter(ch.njol.skript.lang.Expression<?> expression, Map<ChangeMode, Class<?>[]> changes) {
            this.expression = expression;
            this.changes = changes;
            this.single = expression.isSingle();
        }

        @Override
        public Object get(TriggerContext context, Object[] arguments) {
            return CompatArgument.with(arguments, () -> {
                Object[] values = expression.getArray(CompatEvent.of(context.event()));
                if (single) {
                    return values == null || values.length == 0 ? null : CompatValuesBridge.result(values[0]);
                }
                return CompatValuesBridge.result(values);
            });
        }

        @Override
        public Class<?>[] acceptChange(Mode mode) {
            Class<?>[] accepted = changes.get(ChangeMode.valueOf(mode.name()));
            if (accepted == null) {
                return null;
            }
            Class<?>[] classes = new Class<?>[accepted.length];
            for (int i = 0; i < accepted.length; i++) {
                classes[i] = accepted[i].isArray() ? accepted[i].getComponentType() : accepted[i];
            }
            return classes;
        }

        @Override
        public boolean takesOne(Mode mode) {
            Class<?>[] accepted = changes.get(ChangeMode.valueOf(mode.name()));
            if (accepted == null || accepted.length == 0) {
                return false;
            }
            for (Class<?> type : accepted) {
                if (type.isArray()) {
                    return false;
                }
            }
            return true;
        }

        @Override
        public void change(TriggerContext context, Object[] arguments, Object[] delta, Mode mode) {
            ChangeMode skriptMode = ChangeMode.valueOf(mode.name());
            Object[] given = delta == null ? null : convert(delta, changes.getOrDefault(skriptMode, new Class<?>[0]));
            CompatArgument.with(arguments, () -> {
                expression.change(CompatEvent.of(context.event()), given, skriptMode);
                return null;
            });
        }

        /** The values as the classes the expression takes, as Skript converts them, in an array of that class. */
        private static Object[] convert(Object[] delta, Class<?>[] accepted) {
            if (accepted.length == 0) {
                return delta;
            }
            Object[] converted = new Object[delta.length];
            Class<?> shared = null;
            int count = 0;
            for (Object value : delta) {
                Object result = null;
                for (Class<?> type : accepted) {
                    Class<?> component = type.isArray() ? type.getComponentType() : type;
                    result = CompatValuesBridge.as(value, component);
                    if (result != null) {
                        shared = shared == null ? component : commonSuper(shared, component);
                        break;
                    }
                }
                if (result != null) {
                    converted[count++] = result;
                }
            }
            Object[] typed = (Object[]) Array.newInstance(shared == null ? Object.class
                    : java.lang.invoke.MethodType.methodType(shared).wrap().returnType(), count);
            System.arraycopy(converted, 0, typed, 0, count);
            return typed;
        }
    }
}
