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
        String id = id(addon, info);
        registrar.expression(id, compat.typeId(returns), element -> {
            ch.njol.skript.lang.Expression<?> expression = setup(compat, addon, info, placeholders, element,
                    "expression");
            return new ExpressionAdapter(addon, expression, changes(expression));
        }, patterns(info));
        priority(registrar, id, info);
        if (returns == Object.class) {
            // What it gives as Skript types it, once set up ('damage type of x' gives a damage type), where it's
            // registered as giving anything: so a word after it ('is magic') is read as that type.
            registrar.returnType(id, element -> {
                ch.njol.skript.lang.Expression<?> expression = quietly(compat, addon, info, placeholders, element);
                String type = expression == null ? null : compat.typeId(expression.getReturnType());
                return "object".equals(type) ? null : type;
            });
        }
    }

    static void effect(Compat compat, Registrar registrar, String addon, SyntaxInfo<?> info) {
        if (walks(info.type())) {
            // It runs the rest of the trigger itself, as an effect that waits does: not supported yet, and running
            // only its execute would silently do nothing.
            throw new UnsupportedEffect();
        }
        List<List<Placeholder>> placeholders = placeholders(compat, info);
        String id = id(addon, info);
        registrar.effect(id, element -> {
            ch.njol.skript.lang.Effect effect = setup(compat, addon, info, placeholders, element, "effect");
            return (Effect) (context, arguments) -> run(addon, effect, arguments,
                    () -> effect.run(CompatEvent.of(context.event())));
        }, patterns(info));
        priority(registrar, id, info);
    }

    /**
     * An addon's event: NeoSkript listens to its event classes, and the event's {@code check} filters each one that
     * fires, with the literals written in the trigger's header ({@code on wave of zombies}).
     */
    static void event(Compat compat, Registrar registrar, String addon, SyntaxInfo<?> info) {
        List<Class<?>> classes = info instanceof org.skriptlang.skript.bukkit.registration.BukkitSyntaxInfos.Event<?> event
                ? List.copyOf(event.events()) : List.of();
        if (classes.isEmpty()) {
            throw new IllegalArgumentException("it has no event classes");
        }
        List<List<Placeholder>> placeholders = placeholders(compat, info);
        String id = id(addon, info);
        registrar.event(id, classes, element -> {
            // Set up once when the script loads, so a header it won't take is an error then, as on Skript.
            setup(compat, addon, info, placeholders, element, "event");
            return new EventCheck(compat, addon, info, placeholders, element);
        }, patterns(info));
    }

    /**
     * A trigger's filter for an addon's event. The event is set up (again) the first time the filter runs, with the
     * header's values, which are constants: a list of them (`on tab complete of "/a" and "/b"`) has no one written
     * value, so it has its values only then, and an event that copies them in {@code init} gets them all. An event
     * whose check throws is logged at most once a minute for the trigger.
     */
    private static final class EventCheck implements Condition {

        private static final long LOG_EVERY = java.util.concurrent.TimeUnit.MINUTES.toNanos(1);

        private final Compat compat;
        private final String addon;
        private final SyntaxInfo<?> info;
        private final List<List<Placeholder>> placeholders;
        private final ElementInfo element;
        private volatile ch.njol.skript.lang.SkriptEvent event;
        private boolean failed;
        private long logged = System.nanoTime() - LOG_EVERY;

        EventCheck(Compat compat, String addon, SyntaxInfo<?> info, List<List<Placeholder>> placeholders,
                ElementInfo element) {
            this.compat = compat;
            this.addon = addon;
            this.info = info;
            this.placeholders = placeholders;
            this.element = element;
        }

        @Override
        public boolean check(TriggerContext context, Object[] arguments) {
            if (!(context.event() instanceof Event fired)) {
                return false;
            }
            ch.njol.skript.lang.SkriptEvent ready = event != null ? event : setUp(arguments);
            if (ready == null) {
                return false;
            }
            try {
                return run(addon, ready, arguments, () -> ready.check(fired));
            } catch (AddonError e) {
                problem(e);
                return false;
            }
        }

        private synchronized ch.njol.skript.lang.SkriptEvent setUp(Object[] arguments) {
            if (event == null && !failed) {
                try {
                    event = CompatArgument.with(arguments,
                            () -> setup(compat, addon, info, placeholders, element, "event", true));
                } catch (VirtualMachineError e) {
                    throw e;
                } catch (Throwable e) {
                    failed = true;
                    problem(e);
                }
            }
            return event;
        }

        private synchronized void problem(Throwable e) {
            long now = System.nanoTime();
            if (now - logged >= LOG_EVERY) {
                logged = now;
                compat.logger().log(Level.WARNING, e.getMessage() + " (in '" + element.textOr("") + "'; more of"
                        + " these in the next minute aren't logged)", e);
            }
        }
    }

    static void condition(Compat compat, Registrar registrar, String addon, SyntaxInfo<?> info) {
        List<List<Placeholder>> placeholders = placeholders(compat, info);
        String id = id(addon, info);
        registrar.condition(id, element -> {
            ch.njol.skript.lang.Condition condition = setup(compat, addon, info, placeholders, element, "condition");
            return (Condition) (context, arguments) -> run(addon, condition, arguments,
                    () -> condition.check(CompatEvent.of(context.event())));
        }, patterns(info));
        priority(registrar, id, info);
    }

    /**
     * Where Skript tries the element: after Skript's own syntax of its priority level, as Skript places an addon's
     * syntax, so a line Skript's own syntax reads stays Skript's with the addon installed.
     */
    private static void priority(Registrar registrar, String id, SyntaxInfo<?> info) {
        registrar.priority(id, CompatPriority.level(info.priority()));
    }

    /**
     * Runs an addon's element with its arguments. What it throws (but the JVM running out of memory or stack) becomes
     * an error naming the addon and the element, so a script's runtime error says whose it is, and whether the
     * addon's own code threw.
     */
    private static <R> R run(String addon, Object element, Object[] arguments, java.util.function.Supplier<R> body) {
        try {
            return CompatArgument.with(arguments, body);
        } catch (VirtualMachineError e) {
            throw e;
        } catch (Throwable e) {
            throw new AddonError(addon, element, e);
        }
    }

    /** An error an addon's element threw while it ran. */
    static final class AddonError extends RuntimeException {

        AddonError(String addon, Object element, Throwable cause) {
            super(addon + "'s " + element.getClass().getSimpleName() + " threw"
                    + (thrownBy(element.getClass(), cause) ? " in " + addon + "'s code" : "") + ": " + cause, cause);
        }

        /**
         * Whether the first frame of {@code cause}'s that isn't Java's, Bukkit's, Paper's or Minecraft's is in the
         * addon's code: in its element's package or one above it (as {@code com.shanebeestudios.skbee}).
         */
        static boolean thrownBy(Class<?> element, Throwable cause) {
            String[] parts = element.getPackageName().split("\\.");
            String root = String.join(".", java.util.Arrays.copyOf(parts, Math.min(3, parts.length))) + ".";
            for (StackTraceElement frame : cause.getStackTrace()) {
                String name = frame.getClassName();
                if (name.startsWith("java.") || name.startsWith("jdk.") || name.startsWith("sun.")
                        || name.startsWith("org.bukkit.") || name.startsWith("io.papermc.")
                        || name.startsWith("net.minecraft.") || name.startsWith("com.google.")
                        || name.startsWith("net.kyori.") || name.startsWith("com.mojang.")) {
                    continue;
                }
                return name.startsWith(root);
            }
            return false;
        }
    }

    /** An effect NeoSkriptCompat leaves out: one that runs the rest of the trigger itself. */
    static final class UnsupportedEffect extends RuntimeException {
        UnsupportedEffect() {
            super(null, null, false, false);
        }
    }

    /** Whether an effect overrides {@code walk}, so it runs the rest of the trigger itself rather than its execute. */
    static boolean walks(Class<?> type) {
        for (Class<?> c = type; c != null && c != ch.njol.skript.lang.Effect.class && c != Object.class;
                c = c.getSuperclass()) {
            try {
                c.getDeclaredMethod("walk", Event.class);
                return true;
            } catch (NoSuchMethodException notHere) {
                // Look further up.
            }
        }
        return false;
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

    /** The element set up for a line as it's parsed, or null if it won't be: nothing is logged. */
    private static <E extends SyntaxElement> E quietly(Compat compat, String addon, SyntaxInfo<?> info,
            List<List<Placeholder>> placeholders, ElementInfo element) {
        try {
            return setup(compat, addon, info, placeholders, element, "expression", true);
        } catch (VirtualMachineError e) {
            throw e;
        } catch (Throwable notNow) {
            return null;
        }
    }

    private static <E extends SyntaxElement> E setup(Compat compat, String addon, SyntaxInfo<?> info,
            List<List<Placeholder>> placeholders, ElementInfo element, String kind) {
        return setup(compat, addon, info, placeholders, element, kind, false);
    }

    /** A new element for a line, set up for it: a syntax error if it won't be, as Skript gives. */
    @SuppressWarnings("unchecked")
    private static <E extends SyntaxElement> E setup(Compat compat, String addon, SyntaxInfo<?> info,
            List<List<Placeholder>> placeholders, ElementInfo element, String kind, boolean quiet) {
        String text = element.textOr("");
        E instance;
        try {
            instance = (E) info.instance();
        } catch (VirtualMachineError e) {
            throw e;
        } catch (Throwable e) {
            // Anything else an addon throws (an AssertionError, a checked exception it sneaks out) fails the line,
            // not NeoSkript.
            if (!quiet) {
                compat.logger().log(Level.WARNING, addon + " couldn't make its " + kind + " " + info.type().getName(), e);
            }
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
            // An event's arguments are all literals, as Skript gives them.
            arguments[i] = argument(i, type, element, instance instanceof ch.njol.skript.lang.SkriptEvent);
        }
        ParseResult result = new ParseResult(text, arguments);
        result.mark = mark(element);
        result.tags.addAll(element.tags());
        ParserInstance parser = ParserInstance.get();
        Class<? extends Event>[] events = parser.getCurrentEvents();
        String eventName = parser.getCurrentEventName();
        parser.setCurrentEvent(element.eventName(), eventClasses(element));
        boolean[] ok = {false};
        String error;
        try {
            error = Compat.withSetupErrors(() -> ok[0] = instance instanceof ch.njol.skript.lang.SkriptEvent event
                    ? event.init(literals(arguments), element.pattern(), result)
                    : instance.preInit() && instance.init(arguments, element.pattern(), Kleenean.FALSE, result));
        } catch (VirtualMachineError e) {
            throw e;
        } catch (Throwable e) {
            if (!quiet) {
                compat.logger().log(Level.WARNING, addon + "'s " + kind + " failed to set up for '" + text + "'", e);
            }
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

    /**
     * The parse mark Skript gives: NeoSkript's ({@code 1¦}), and each numeric tag ({@code 1:}) too, which Skript
     * also counts as a mark ({@code ParseTagPatternElement}).
     */
    static int mark(ElementInfo element) {
        int mark = element.mark();
        for (String tag : element.tags()) {
            if (!tag.isEmpty() && tag.length() < 10 && tag.chars().allMatch(Character::isDigit)) {
                mark ^= Integer.parseInt(tag);
            }
        }
        return mark;
    }

    /** Whether an optional placeholder was left out: Skript gives null for it. */
    private static boolean omitted(ElementInfo element, int index) {
        return element.isDefault(index) && element.argumentType(index) == null && element.argumentLiteral(index) == null;
    }

    private static ch.njol.skript.lang.Literal<?>[] literals(ch.njol.skript.lang.Expression<?>[] arguments) {
        ch.njol.skript.lang.Literal<?>[] literals = new ch.njol.skript.lang.Literal<?>[arguments.length];
        for (int i = 0; i < arguments.length; i++) {
            literals[i] = (ch.njol.skript.lang.Literal<?>) arguments[i];
        }
        return literals;
    }

    private static <T> CompatArgument<T> argument(int index, Class<T> type, ElementInfo element, boolean literal) {
        String written = element.argumentLiteral(index);
        if (written != null || literal) {
            return new CompatArgument.Written<>(index, type, element.isSingle(index), element.and(index),
                    element.isDefault(index), written);
        }
        return new CompatArgument<>(index, type, element.isSingle(index), element.and(index), element.isDefault(index),
                null);
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

        private final String addon;
        private final ch.njol.skript.lang.Expression<?> expression;
        private final Map<ChangeMode, Class<?>[]> changes;
        private final boolean single;

        ExpressionAdapter(String addon, ch.njol.skript.lang.Expression<?> expression,
                Map<ChangeMode, Class<?>[]> changes) {
            this.addon = addon;
            this.expression = expression;
            this.changes = changes;
            this.single = expression.isSingle();
        }

        @Override
        public Object get(TriggerContext context, Object[] arguments) {
            return run(addon, expression, arguments, () -> {
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
            // As NeoSkript's classes: what it gives is made the addon's own again in change (Skript's ItemType, ...).
            Class<?>[] classes = new Class<?>[accepted.length];
            for (int i = 0; i < accepted.length; i++) {
                classes[i] = CompatAdapters.neoskriptClass(accepted[i].isArray() ? accepted[i].getComponentType()
                        : accepted[i]);
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
            if (given != null && given.length == 0 && skriptMode != ChangeMode.DELETE && skriptMode != ChangeMode.RESET) {
                // Setting to nothing deletes, where the expression can be deleted; else nothing happens (EffChange).
                if (skriptMode == ChangeMode.SET && changes.containsKey(ChangeMode.DELETE)) {
                    run(addon, expression, arguments, () -> {
                        expression.change(CompatEvent.of(context.event()), null, ChangeMode.DELETE);
                        return null;
                    });
                }
                return;
            }
            run(addon, expression, arguments, () -> {
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
                // A value that already is one of the classes (once it's the addon's own) stays as it is; only then
                // is it converted, as Skript does: 'efficiency 7' stays an enchantment type, not an enchantment.
                for (int pass = 0; pass < 2 && result == null; pass++) {
                    for (Class<?> type : accepted) {
                        Class<?> component = CompatValuesBridge.boxed(type.isArray() ? type.getComponentType() : type);
                        Object adapted = CompatAdapters.toSkript(value, component);
                        result = pass == 0 ? (component.isInstance(adapted) ? adapted : null)
                                : CompatValuesBridge.as(value, component);
                        if (result != null) {
                            shared = shared == null ? component : commonSuper(shared, component);
                            break;
                        }
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
