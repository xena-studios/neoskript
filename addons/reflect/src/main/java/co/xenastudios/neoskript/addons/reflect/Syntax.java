/*
 * Copyright (c) 2026 Xena Studios
 * SPDX-License-Identifier: GPL-3.0-only
 */
package co.xenastudios.neoskript.addons.reflect;

import co.xenastudios.neoskript.api.NeoSkript;
import co.xenastudios.neoskript.api.element.Changeable;
import co.xenastudios.neoskript.api.element.Condition;
import co.xenastudios.neoskript.api.element.EventFilter;
import co.xenastudios.neoskript.api.element.Expression;
import co.xenastudios.neoskript.api.element.SyntaxError;
import co.xenastudios.neoskript.api.element.TriggerContext;
import co.xenastudios.neoskript.api.registration.Registrar;
import co.xenastudios.neoskript.api.storage.BinaryReader;
import co.xenastudios.neoskript.api.storage.BinaryWriter;
import co.xenastudios.neoskript.api.storage.VariableCodec;
import java.lang.reflect.Array;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Member;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Stream;
import org.bukkit.Bukkit;
import org.bukkit.event.Event;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.java.JavaPlugin;

/** skript-reflect's syntax for calling Java, with its patterns. */
final class Syntax {

    private static final String PRIMITIVES = "boolean|byte|char|double|float|int|long|short";

    private Syntax() {}

    static void register(Registrar registrar, Imports imports) {
        types(registrar, imports);
        elements(registrar, imports);
    }

    static void elements(Registrar registrar, Imports imports) {
        registrar.structure("NeoSkriptReflectStructImport", imports, "import");
        // skript-reflect turns Skript's 'disable variable missing and/or warnings' on as it enables, so a server with it
        // gives no "List is missing 'and' or 'or'" warning at all, in any script.
        registrar.noMissingAndOrWarnings();

        // Calls: as an expression, an effect, and a condition.
        registrar.expression("NeoSkriptReflectExprJavaCall", "object", info -> JavaCall.create(info, imports),
                JavaCall.PATTERNS);
        registrar.effect("NeoSkriptReflectEffJavaCall", info -> {
            JavaCall call = JavaCall.create(info, imports);
            return (context, arguments) -> call.get(context, arguments);
        }, JavaCall.PATTERNS);
        registrar.condition("NeoSkriptReflectCondJavaCall", info -> {
            JavaCall call = JavaCall.create(info, imports);
            return (context, arguments) -> JavaCall.truthy(call.get(context, arguments));
        }, JavaCall.PATTERNS);

        // Arguments written as a function's, so 'Math.max(3, 7)' isn't warned about, as in Skript.
        for (String id : List.of("NeoSkriptReflectExprJavaCall", "NeoSkriptReflectEffJavaCall",
                "NeoSkriptReflectCondJavaCall")) {
            registrar.argumentsLikeFunctions(id);
            // Tried after all of Skript's own syntax, as skript-reflect's SHADOW_REALM priority puts them:
            // '{_n} + {_l}.size()' is Skript's arithmetic around a call, not a call on '{_n} + {_l}'.
            registrar.priority(id, co.xenastudios.neoskript.api.registration.SyntaxPriority.PATTERN_MATCHES_EVERYTHING);
        }

        registrar.expression("NeoSkriptReflectExprNull", "null", info -> (context, arguments) -> Null.NULL, "null");

        // Classes.
        registrar.expression("NeoSkriptReflectExprJavaType", "javatype", info -> (context, arguments) -> {
            Class<?> type = arguments[0] instanceof String name ? imports.load(name) : null;
            return type == null ? null : new JavaType(type);
        }, "[the] [java] class %string%");
        registrar.property("NeoSkriptReflectExprJavaTypeOf", "javatype", "[java] class[es]", "objects", Object.class,
                value -> new JavaType(Coercion.classOf(value)));
        registrar.expression("NeoSkriptReflectExprClassReference", "object", info -> {
            if (info.pattern() == 0) {
                String text = info.text() == null ? "" : info.text().strip();
                Class<?> primitive = Coercion.PRIMITIVES.get(text.substring(0, Math.max(0, text.length() - 6)));
                return (context, arguments) -> primitive;
            }
            return (context, arguments) -> arguments[0] instanceof JavaType type ? type.javaClass() : null;
        }, "<(" + PRIMITIVES + ")>.class", "%javatype%.class");

        // Arrays.
        registrar.expression("NeoSkriptReflectExprNewArray", "javaobject", info -> {
            Class<?> primitive = info.pattern() == 0 ? primitiveIn(info.text()) : null;
            return (context, arguments) -> {
                Class<?> component = primitive != null ? primitive
                        : arguments[0] instanceof JavaType type ? type.javaClass() : null;
                Object size = arguments[info.pattern() == 0 ? 0 : 1];
                if (component == null || !(size instanceof Number number) || number.intValue() < 0) {
                    return null;
                }
                return ObjectWrapper.of(Array.newInstance(component, number.intValue()));
            };
        }, "new <(" + PRIMITIVES + ")>\\[%number%\\]", "new %javatype%\\[%number%\\]");
        registrar.expression("NeoSkriptReflectExprArrayAccess", "object", info -> new ArrayAccess(),
                "%javaobject%\\[%number%\\]");
        registrar.expression("NeoSkriptReflectExprCollect", "javaobject", info -> (context, arguments) ->
                collect(arguments[0], arguments[1]), "\\[%objects%[ as %-javatype%[ ]]\\]");
        registrar.argumentsLikeFunctions("NeoSkriptReflectExprCollect");
        registrar.expression("NeoSkriptReflectExprSpread", "object", info -> (context, arguments) ->
                spread(arguments[0]), "...%object%", "…%object%");

        registrar.condition("NeoSkriptReflectCondIsInstanceOf", info -> (context, arguments) -> {
            Object[] values = arguments[0] instanceof Object[] many ? many : new Object[] {arguments[0]};
            Object[] types = arguments[1] instanceof Object[] many ? many : new Object[] {arguments[1]};
            boolean holds = Condition.check(values, info.and(0), value -> Condition.check(types, info.and(1),
                    type -> type instanceof JavaType javaType && javaType.javaClass().isInstance(Coercion.toJava(value))));
            return info.pattern() == 1 ? !holds : holds;
        }, "%objects% (is|are) [a[n]] instance[s] of %javatypes%",
                "%objects% (is not|isn't|are not|aren't) [a[n]] instance[s] of %javatypes%");

        // Looking around.
        registrar.expression("NeoSkriptReflectExprMembers", "string", info -> (context, arguments) ->
                members(arguments[0], info.hasTag("fields") ? 0 : info.hasTag("methods") ? 1 : 2),
                "[the] (fields:fields|methods:methods|constructors:constructors) of %objects%",
                "%objects%'[s] (fields:fields|methods:methods|constructors:constructors)");
        registrar.expression("NeoSkriptReflectExprMemberNames", "string", info -> (context, arguments) ->
                memberNames(arguments[0], info.hasTag("field")),
                "[the] (field:field|method:method) names of %objects%",
                "%objects%'[s] (field:field|method:method) names");
        registrar.expression("NeoSkriptReflectExprPlugin", "object", info -> {
            String literal = info.argumentLiterals().isEmpty() ? null : info.argumentLiterals().getFirst();
            JavaType named = literal == null ? null
                    : imports.lookup(NeoSkript.get().scriptBeingLoaded(), literal.strip());
            if (named != null && (!JavaPlugin.class.isAssignableFrom(named.javaClass())
                    || JavaPlugin.class.equals(named.javaClass()))) {
                throw new SyntaxError("The class " + named.javaClass().getSimpleName() + " is not a plugin class");
            }
            return (context, arguments) -> plugin(arguments[0]);
        }, "[(an|the)] instance of [the] plugin %javatype/string%");

        registrar.expression("NeoSkriptReflectExprEvent", "event", info -> (context, arguments) -> context.event(),
                "[the] event");
        registrar.expression("NeoSkriptReflectExprJavaError", "object", info -> (context, arguments) ->
                LastError.get(context), "[the] [last] [java] (throwable|exception|error)");

        // Any Bukkit event by its imported class: 'on EnderDragonChangePhaseEvent:'.
        registrar.event("NeoSkriptReflectEvtByReflection", List.of(Event.class), info -> {
            List<Class<?>> classes = eventClasses(info.text(), imports);
            return EventFilter.of(classes, (context, arguments) -> true);
        }, "%javatypes%");
    }

    static void types(Registrar registrar, Imports imports) {
        registrar.type("javatype", "java type", JavaType.class);
        registrar.alias("javatype", "java type", "java types");
        // As in Skript: 'Location' where a class goes is the import, not the event's location.
        registrar.strictType("javatype");
        registrar.text(JavaType.class, type -> type.javaClass().getName());
        registrar.variableNameText(JavaType.class, type -> "type:" + type.javaClass().getName());
        registrar.literalParser("javatype", text -> imports.lookup(NeoSkript.get().scriptBeingLoaded(), text.strip()));
        // Saved as skript-reflect saves them, so variables.csv reads the same on both.
        registrar.storage("javatype", JavaType.class, new VariableCodec<>() {
            @Override
            public void encode(JavaType value, BinaryWriter out) {
                out.beginObject(1);
                out.stringField("type", value.javaClass().getName());
            }

            @Override
            public JavaType decode(BinaryReader in) {
                in.beginObject();
                Class<?> type = imports.load(in.stringField("type"));
                if (type == null) {
                    throw new UnsupportedOperationException("the class isn't there");
                }
                return new JavaType(type);
            }
        });

        registrar.type("null", "null", Null.class);
        registrar.text(Null.class, value -> "null");
        registrar.variableNameText(Null.class, value -> "null");
        registrar.storage("null", Null.class, new VariableCodec<>() {
            @Override
            public void encode(Null value, BinaryWriter out) {
                out.beginObject(0);
            }

            @Override
            public Null decode(BinaryReader in) {
                in.beginObject();
                return Null.NULL;
            }
        });

        // Bukkit's events as values, as skript-reflect's 'event' type: what 'the event' gives.
        registrar.type("event", "event", Event.class);
        registrar.alias("event", "event", "events");
        registrar.text(Event.class, Event::getEventName);

        registrar.type("javaobject", "java object", ObjectWrapper.class);
        registrar.alias("javaobject", "java object", "java objects");
        registrar.text(ObjectWrapper.class, wrapper -> wrapper.text(value -> value == null ? "null"
                : NeoSkript.get().values().text(Coercion.fromJava(value))));
    }

    private static Class<?> primitiveIn(String text) {
        if (text == null) {
            return null;
        }
        String rest = text.strip().substring("new ".length()).strip();
        int bracket = rest.indexOf('[');
        return bracket < 0 ? null : Coercion.PRIMITIVES.get(rest.substring(0, bracket).strip());
    }

    /** {@code [a, b]}: the values in an array of their closest common class, or of {@code as}'s. */
    private static Object collect(Object values, Object as) {
        Object[] all = values instanceof Object[] many ? many : values == null ? new Object[0] : new Object[] {values};
        List<Object> items = new ArrayList<>();
        Class<?> only = as instanceof JavaType type ? type.javaClass() : null;
        for (Object value : all) {
            Object item = Coercion.toJava(value);
            if (only == null || item == null || only.isInstance(item)) {
                items.add(item);
            }
        }
        Class<?> common = null;
        for (Object item : items) {
            if (item == null) {
                continue;
            }
            Class<?> type = item.getClass();
            if (common == null) {
                common = type;
            } else {
                while (!common.isAssignableFrom(type)) {
                    common = common.getSuperclass();
                }
            }
        }
        Object[] array = (Object[]) Array.newInstance(common == null ? Object.class : common, items.size());
        return new ObjectWrapper(items.toArray(array));
    }

    /** {@code ...x}: the contents of an array, a collection, an iterable, an iterator or a stream. */
    private static Object[] spread(Object value) {
        Object object = Coercion.toJava(value);
        if (object instanceof Collection<?> collection) {
            object = collection.toArray();
        } else if (object instanceof Iterable<?> iterable) {
            object = toArray(iterable.iterator());
        } else if (object instanceof Stream<?> stream) {
            object = toArray(stream.iterator());
        } else if (object instanceof Iterator<?> iterator) {
            object = toArray(iterator);
        }
        if (object == null || !object.getClass().isArray()) {
            return new Object[0];
        }
        Object[] boxed = (Object[]) Coercion.boxPrimitiveArray(object);
        Object[] values = new Object[boxed.length];
        for (int i = 0; i < boxed.length; i++) {
            values[i] = Coercion.fromJava(boxed[i]);
        }
        return values;
    }

    private static Object[] toArray(Iterator<?> iterator) {
        List<Object> list = new ArrayList<>();
        iterator.forEachRemaining(list::add);
        return list.toArray();
    }

    private static Object[] members(Object targets, int which) {
        Set<String> all = new LinkedHashSet<>();
        for (Object target : targets instanceof Object[] many ? many : new Object[] {targets}) {
            if (target == null) {
                continue;
            }
            Class<?> type = target instanceof JavaType javaType ? javaType.javaClass() : Coercion.classOf(target);
            List<? extends Member> members;
            try {
                members = switch (which) {
                    case 0 -> Members.fields(type);
                    case 1 -> Members.methods(type);
                    default -> Members.constructors(type);
                };
            } catch (LinkageError broken) {
                // A class whose members name one that isn't there: it has none that can be listed.
                NeoSkript.get().runtimeWarning(Coercion.debugName(type) + "'s members can't be looked at: " + broken);
                continue;
            }
            for (Member member : members) {
                all.add(switch (member) {
                    case Field field -> field.toGenericString();
                    case Method method -> method.toGenericString();
                    case Constructor<?> constructor -> constructor.toGenericString();
                    default -> member.toString();
                });
            }
        }
        return all.toArray();
    }

    private static Object[] memberNames(Object targets, boolean fields) {
        Set<String> all = new LinkedHashSet<>();
        for (Object target : targets instanceof Object[] many ? many : new Object[] {targets}) {
            if (target == null) {
                continue;
            }
            Class<?> type = target instanceof JavaType javaType ? javaType.javaClass() : Coercion.classOf(target);
            try {
                (fields ? Members.fields(type).stream().map(Field::getName)
                        : Members.methods(type).stream().map(Method::getName)).forEach(all::add);
            } catch (LinkageError broken) {
                NeoSkript.get().runtimeWarning(Coercion.debugName(type) + "'s members can't be looked at: " + broken);
            }
        }
        return all.toArray();
    }

    private static Object plugin(Object named) {
        if (named instanceof String name) {
            for (Plugin plugin : Bukkit.getPluginManager().getPlugins()) {
                if (plugin.getName().equalsIgnoreCase(name)) {
                    return plugin;
                }
            }
            return null;
        }
        if (named instanceof JavaType type && JavaPlugin.class.isAssignableFrom(type.javaClass())
                && !JavaPlugin.class.equals(type.javaClass())) {
            try {
                return JavaPlugin.getPlugin(type.javaClass().asSubclass(JavaPlugin.class));
            } catch (RuntimeException notLoaded) {
                return null;
            }
        }
        return null;
    }

    /** The event classes an event's header names, by the script's imports: {@code A}, {@code A and B}. */
    static List<Class<?>> eventClasses(String header, Imports imports) {
        String script = NeoSkript.get().scriptBeingLoaded();
        String text = header == null ? "" : header.strip();
        if (text.regionMatches(true, 0, "on ", 0, 3)) {
            text = text.substring(3).strip();
        }
        List<Class<?>> classes = new ArrayList<>();
        for (String part : text.split("\\s*,\\s*(?:and\\s+|or\\s+)?|\\s+(?:and|or)\\s+")) {
            JavaType type = imports.lookup(script, part.strip());
            if (type == null || !Event.class.isAssignableFrom(type.javaClass())) {
                throw new SyntaxError(part.strip() + " is not an event class");
            }
            if (!hasHandlerList(type.javaClass())) {
                // Bukkit can't listen to it (an abstract event like PlayerEvent): say so here, not when it's listened to.
                throw new SyntaxError(part.strip() + " can't be listened to, as it has no handler list: use one of its"
                        + " subclasses");
            }
            classes.add(type.javaClass());
        }
        return classes;
    }

    /** Whether Bukkit can listen to {@code event}: it or a superclass declares {@code static getHandlerList()}. */
    static boolean hasHandlerList(Class<?> event) {
        for (Class<?> type = event; type != null && type != Event.class; type = type.getSuperclass()) {
            try {
                java.lang.reflect.Method list = type.getDeclaredMethod("getHandlerList");
                if (java.lang.reflect.Modifier.isStatic(list.getModifiers())) {
                    return true;
                }
            } catch (NoSuchMethodException | LinkageError e) {
                // Not this one: its superclass, as Bukkit looks.
            }
        }
        return false;
    }

    /** {@code {_array}[i]}: an element of an array, which can be set. */
    private static final class ArrayAccess implements Expression, Changeable {

        @Override
        public Object get(TriggerContext context, Object[] arguments) {
            Object array = arguments[0] instanceof ObjectWrapper wrapper ? wrapper.array() : null;
            if (array == null || !(arguments[1] instanceof Number index)) {
                return null;
            }
            int i = index.intValue();
            if (i < 0 || i >= Array.getLength(array)) {
                return null;
            }
            return Coercion.fromJava(Array.get(array, i));
        }

        @Override
        public Class<?>[] acceptChange(Mode mode) {
            return mode == Mode.SET || mode == Mode.DELETE ? new Class<?>[] {Object.class} : null;
        }

        @Override
        public void change(TriggerContext context, Object[] arguments, Object[] delta, Mode mode) {
            Object array = arguments[0] instanceof ObjectWrapper wrapper ? wrapper.array() : null;
            if (array == null || !(arguments[1] instanceof Number index)) {
                return;
            }
            int i = index.intValue();
            if (i < 0 || i >= Array.getLength(array)) {
                return;
            }
            Class<?> component = array.getClass().getComponentType();
            Object value = mode == Mode.SET && delta != null && delta.length > 0 ? delta[0] : Null.NULL;
            if (!Coercion.canConvert(value, component)) {
                NeoSkript.get().runtimeWarning(JavaCall.argumentsText(value) + " can't be put in an array of "
                        + Coercion.debugName(component));
                return;
            }
            Array.set(array, i, Coercion.convert(value, component));
        }
    }
}
