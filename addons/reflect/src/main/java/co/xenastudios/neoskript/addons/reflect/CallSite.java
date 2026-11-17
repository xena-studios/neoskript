/*
 * Copyright (c) 2026 Xena Studios
 * SPDX-License-Identifier: GPL-3.0-only
 */
package co.xenastudios.neoskript.addons.reflect;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.lang.reflect.Array;
import java.lang.reflect.Constructor;
import java.lang.reflect.Executable;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.WeakHashMap;
import java.util.concurrent.ConcurrentHashMap;

/**
 * One call in a script ({@code player.getName()}), compiled once: the member it calls is looked up and its overload
 * chosen the first time each combination of the target's class and the arguments' classes is seen, and kept as a
 * method handle of an exact type, called with {@code invokeExact}. skript-reflect looks the overload up again and calls
 * {@code invokeWithArguments}, Java's slowest way, on every call.
 *
 * <p>Kept with the compiled script, and dropped with it when the script reloads, so nothing of another plugin's
 * classes outlives it.
 */
final class CallSite {

    enum Kind {
        FIELD, METHOD, CONSTRUCTOR;

        @Override
        public String toString() {
            return name().toLowerCase(java.util.Locale.ROOT);
        }
    }

    /** A member, ready to call with its arguments in one array, the target first for one that isn't static. */
    record Target(MethodHandle invoker, Class<?>[] parameters, boolean varargs, boolean setter) {}

    /** What was chosen for one combination of classes. */
    private record Entry(Class<?> target, Descriptor descriptor, Class<?>[] arguments, Target chosen) {}

    private static final MethodHandles.Lookup LOOKUP = MethodHandles.lookup();
    private static final int CACHE = 4;

    private final Kind kind;
    private final Entry[] cache = new Entry[CACHE];
    private int next;

    CallSite(Kind kind) {
        this.kind = kind;
    }

    Kind kind() {
        return kind;
    }

    /**
     * The member to call for these classes, or {@code null} if none matches. {@code arguments} doesn't include the
     * target; {@code setter} asks for a field's setter.
     */
    Target find(Descriptor descriptor, Class<?> targetClass, boolean isStatic, Object[] arguments, boolean setter) {
        Entry[] entries = cache;
        for (Entry entry : entries) {
            if (entry != null && entry.target == targetClass && entry.descriptor.equals(descriptor)
                    && entry.chosen.setter == setter && sameClasses(entry.arguments, arguments)) {
                // Checked against these values too: some conversions depend on the value, not only its class (a text
                // is a char only if it's one letter), so the same classes can need another member, or none.
                if (matches(entry.chosen, arguments)) {
                    return entry.chosen;
                }
                break;
            }
        }
        Target chosen = choose(descriptor, isStatic, arguments, setter);
        if (chosen != null) {
            synchronized (this) {
                cache[next] = new Entry(targetClass, descriptor, classes(arguments), chosen);
                next = (next + 1) % CACHE;
            }
        }
        return chosen;
    }

    private static boolean sameClasses(Class<?>[] classes, Object[] arguments) {
        if (classes.length != arguments.length) {
            return false;
        }
        for (int i = 0; i < arguments.length; i++) {
            if (classOf(arguments[i]) != classes[i]) {
                return false;
            }
        }
        return true;
    }

    /** What an argument is for choosing a member: an array by the array's class, not by its wrapper. */
    private static Class<?> classOf(Object argument) {
        if (argument == null || argument == Null.NULL) {
            return null;
        }
        return argument instanceof ObjectWrapper wrapper ? wrapper.array().getClass() : argument.getClass();
    }

    private static Class<?>[] classes(Object[] arguments) {
        Class<?>[] classes = new Class<?>[arguments.length];
        for (int i = 0; i < arguments.length; i++) {
            classes[i] = classOf(arguments[i]);
        }
        return classes;
    }

    /**
     * The member whose parameters take the arguments, one without varargs first, as skript-reflect. Where several do,
     * skript-reflect takes whichever Java lists first, which isn't fixed: {@code Math.max(3, 7)} could be the
     * {@code double} one. This takes the closest: a parameter of the argument's own class, then a superclass, then a
     * number of the same kind (a whole number for a whole number), then any conversion.
     */
    private Target choose(Descriptor descriptor, boolean isStatic, Object[] arguments, boolean setter) {
        Target best = null;
        long bestScore = Long.MAX_VALUE;
        for (Target candidate : candidates(descriptor, isStatic, setter)) {
            if (!matches(candidate, arguments)) {
                continue;
            }
            long score = (candidate.varargs ? 1L << 40 : 0) + distance(candidate, arguments);
            if (score < bestScore) {
                best = candidate;
                bestScore = score;
            }
        }
        return best;
    }

    /** How far the arguments are from the parameters: 0 for each of its own class, more the more it's converted. */
    private static long distance(Target target, Object[] arguments) {
        Class<?>[] parameters = target.parameters;
        int last = parameters.length - 1;
        long total = 0;
        for (int i = 0; i < arguments.length; i++) {
            Class<?> parameter = target.varargs && i >= last ? parameters[last].getComponentType() : parameters[i];
            Object argument = Coercion.toJava(arguments[i]);
            if (argument == null) {
                total += 2;
                continue;
            }
            Class<?> type = argument.getClass();
            Class<?> boxed = parameter.isPrimitive() ? Coercion.WRAPPERS.get(parameter) : parameter;
            if (boxed == type) {
                continue;
            }
            if (boxed.isAssignableFrom(type)) {
                total += 1 + depth(type, boxed);
            } else if (argument instanceof Number && wholeNumber(type) == wholeNumber(boxed)) {
                total += 50;
            } else {
                total += 100;
            }
        }
        return total;
    }

    private static int depth(Class<?> from, Class<?> to) {
        int depth = 0;
        for (Class<?> c = from; c != null && c != to; c = c.getSuperclass()) {
            depth++;
        }
        return Math.min(depth, 40);
    }

    private static boolean wholeNumber(Class<?> type) {
        return type == Long.class || type == Integer.class || type == Short.class || type == Byte.class;
    }

    /** What {@link #candidates} looked up for a class: a kind of member, its name and parameter types, static or not. */
    private record Lookup(Kind kind, String name, List<Class<?>> parameterTypes, boolean isStatic, boolean setter) {}

    /**
     * Each class's members, looked up once, for every call site: a call site that sees many classes
     * ({@code loop-entity.getHandle()}) looks each one's up once rather than at every call. Kept with the class, so they
     * go when it's unloaded, and dropped when the addon disables ({@link #forgetAll}), as a {@code ClassValue}'s values
     * would otherwise keep this addon's classes for as long as a class of the JDK's is loaded.
     */
    private static final ClassValue<Map<Lookup, List<Target>>> MEMBERS = new ClassValue<>() {
        @Override
        protected Map<Lookup, List<Target>> computeValue(Class<?> type) {
            synchronized (LOOKED_AT) {
                LOOKED_AT.add(type);
            }
            return new ConcurrentHashMap<>();
        }
    };

    /** The classes {@link #MEMBERS} has something for, held weakly. */
    private static final Set<Class<?>> LOOKED_AT = Collections.newSetFromMap(new WeakHashMap<>());

    /** Forgets every class's members, as the addon disables. */
    static void forgetAll() {
        List<Class<?>> all;
        synchronized (LOOKED_AT) {
            all = new ArrayList<>(LOOKED_AT);
            LOOKED_AT.clear();
        }
        all.forEach(MEMBERS::remove);
    }

    /**
     * Every member the descriptor names, as targets: each made accessible where Java allows. Looked up once per class;
     * a class whose members can't be looked at (a signature naming a class that isn't there) throws
     * {@link LinkageError} every time, and nothing is kept.
     */
    List<Target> candidates(Descriptor descriptor, boolean isStatic, boolean setter) {
        Lookup key = new Lookup(kind, descriptor.name(), descriptor.parameterTypes() == null ? null
                : List.of(descriptor.parameterTypes()), isStatic, setter);
        Map<Lookup, List<Target>> known = MEMBERS.get(descriptor.javaClass());
        List<Target> found = known.get(key);
        if (found == null) {
            found = List.copyOf(lookUp(descriptor, isStatic, setter));
            known.put(key, found);
        }
        return found;
    }

    /** How many times members were looked up rather than found in {@link #MEMBERS}. For tests. */
    static final java.util.concurrent.atomic.AtomicLong LOOK_UPS = new java.util.concurrent.atomic.AtomicLong();

    private List<Target> lookUp(Descriptor descriptor, boolean isStatic, boolean setter) {
        LOOK_UPS.incrementAndGet();
        Class<?> type = descriptor.javaClass();
        List<Target> targets = new ArrayList<>();
        switch (kind) {
            case FIELD -> {
                for (Field field : Members.fields(type)) {
                    if (!field.getName().equals(descriptor.name()) || Members.isStatic(field) != isStatic) {
                        continue;
                    }
                    Field open = Members.access(field);
                    if (open == null) {
                        continue;
                    }
                    try {
                        MethodHandle handle = setter ? LOOKUP.unreflectSetter(open) : LOOKUP.unreflectGetter(open);
                        targets.add(target(handle, setter ? new Class<?>[] {open.getType()} : new Class<?>[0], false,
                                setter, isStatic));
                    } catch (IllegalAccessException e) {
                        // A final field has no setter: there's nothing to set.
                    }
                    if (!targets.isEmpty()) {
                        break;
                    }
                }
            }
            case METHOD -> {
                for (Method method : Members.methods(type)) {
                    if (!method.getName().equals(descriptor.name()) || Members.isStatic(method) != isStatic
                            || (descriptor.parameterTypes() != null
                                    && !java.util.Arrays.equals(method.getParameterTypes(), descriptor.parameterTypes()))) {
                        continue;
                    }
                    Method open = Members.access(method);
                    if (open == null) {
                        continue;
                    }
                    try {
                        targets.add(target(LOOKUP.unreflect(open).asFixedArity(), open.getParameterTypes(),
                                open.isVarArgs(), false, isStatic));
                    } catch (IllegalAccessException e) {
                        // Not open to us after all: skipped, as in skript-reflect.
                    }
                }
            }
            case CONSTRUCTOR -> {
                for (Constructor<?> constructor : Members.constructors(type)) {
                    Constructor<?> open = Members.access(constructor);
                    if (open == null) {
                        continue;
                    }
                    try {
                        targets.add(target(LOOKUP.unreflectConstructor(open).asFixedArity(),
                                open.getParameterTypes(), open.isVarArgs(), false, true));
                    } catch (IllegalAccessException e) {
                        // As above.
                    }
                }
            }
        }
        return targets;
    }

    /** A handle taking (and returning) objects, its arguments in one array, the target first if there is one. */
    private static Target target(MethodHandle handle, Class<?>[] parameters, boolean varargs, boolean setter,
            boolean isStatic) {
        int count = handle.type().parameterCount();
        MethodHandle generic = handle.asType(MethodType.genericMethodType(count));
        MethodHandle spread = generic.asSpreader(Object[].class, count);
        return new Target(spread, parameters, varargs, setter);
    }

    /** Whether {@code target} takes {@code arguments}, as skript-reflect's {@code matchesArgs}. */
    static boolean matches(Target target, Object[] arguments) {
        Class<?>[] parameters = target.parameters;
        int last = parameters.length - 1;
        if (arguments.length != parameters.length && !(target.varargs && arguments.length >= last)) {
            return false;
        }
        for (int i = 0; i < arguments.length; i++) {
            boolean inVarargs = target.varargs && i >= last;
            Class<?> parameter = inVarargs ? parameters[last].getComponentType() : parameters[i];
            if (!Coercion.canConvert(arguments[i], parameter)) {
                if (inVarargs && arguments.length == parameters.length
                        && Coercion.canConvert(arguments[i], parameters[last])) {
                    continue;
                }
                return false;
            }
        }
        return true;
    }

    /**
     * Calls {@code target} with {@code arguments} converted to its parameters, varargs gathered into their array;
     * {@code receiver} first unless it's static ({@code null}).
     */
    static Object call(Target target, Object receiver, boolean isStatic, Object[] arguments) throws Throwable {
        Class<?>[] parameters = target.parameters;
        int offset = isStatic ? 0 : 1;
        // One array: the receiver, then the arguments converted, as the handle spreads them.
        Object[] all = parameters.length + offset == 0 ? NONE : new Object[parameters.length + offset];
        if (!isStatic) {
            all[0] = receiver;
        }
        int last = parameters.length - 1;
        if (target.varargs && !(arguments.length == parameters.length
                && Coercion.canConvert(arguments[last], parameters[last])
                && Coercion.toJava(arguments[last]) != null
                && Coercion.toJava(arguments[last]).getClass().isArray())) {
            Class<?> component = parameters[last].getComponentType();
            for (int i = 0; i < last; i++) {
                all[offset + i] = Coercion.convert(arguments[i], parameters[i]);
            }
            Object rest = Array.newInstance(component, arguments.length - last);
            for (int i = last; i < arguments.length; i++) {
                Array.set(rest, i - last, Coercion.convert(arguments[i], component));
            }
            all[offset + last] = rest;
        } else {
            for (int i = 0; i < arguments.length; i++) {
                all[offset + i] = Coercion.convert(arguments[i], parameters[i]);
            }
        }
        return (Object) target.invoker.invokeExact(all);
    }

    private static final Object[] NONE = new Object[0];

    /** The members named {@code name}, for skript-reflect's suggestions: executables of this kind. */
    static List<Executable> executables(Kind kind, Class<?> type) {
        List<Executable> all = new ArrayList<>();
        if (kind == Kind.METHOD) {
            all.addAll(Members.methods(type));
        } else if (kind == Kind.CONSTRUCTOR) {
            all.addAll(Members.constructors(type));
        }
        return all;
    }
}
