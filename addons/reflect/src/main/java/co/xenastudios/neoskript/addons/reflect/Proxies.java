/*
 * Copyright (c) 2026 Xena Studios
 * SPDX-License-Identifier: GPL-3.0-only
 */
package co.xenastudios.neoskript.addons.reflect;

import co.xenastudios.neoskript.api.NeoSkript;
import co.xenastudios.neoskript.api.element.Expression;
import co.xenastudios.neoskript.api.element.KeyAware;
import co.xenastudios.neoskript.api.element.Keyed;
import co.xenastudios.neoskript.api.element.SyntaxError;
import co.xenastudios.neoskript.api.element.TriggerContext;
import co.xenastudios.neoskript.api.registration.Registrar;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * skript-reflect's function references ({@code function reference "name"}) and proxies
 * ({@code new proxy instance of Runnable using {_functions::*}}): a Java object implementing interfaces, each method
 * running the script function its list names.
 *
 * <p>A proxy runs a function by its name each time a method is called, through {@code Scripts.call}, so it always
 * runs the function's current version and holds none of a script's compiled code: a proxy Java keeps after a reload
 * (a scheduled task, a listener) doesn't keep the old script loaded. The function runs on the thread that calls the
 * method, as skript-reflect's does: on Folia, what it touches must be that thread's.
 */
final class Proxies {

    private Proxies() {}

    /** A script function by name, with arguments given first: skript-reflect's {@code FunctionWrapper}. */
    record FunctionReference(String name, Object[] arguments) {

        @Override
        public String toString() {
            return "function " + name;
        }
    }

    private static final Pattern LIST_VARIABLE = Pattern.compile("\\{[^{}]*::\\*}");
    private static final Pattern PROXY_TEXT = Pattern.compile("(?i).*\\b(?:using|from)\\s+(.+)$");

    static void register(Registrar registrar, Imports imports) {
        registrar.expression("NeoSkriptReflectExprFunction", "object", info -> (context, arguments) -> {
            Object[] names = values(arguments[0]);
            Object[] given = arguments[1] == null ? new Object[0] : values(arguments[1]);
            List<Object> references = new ArrayList<>();
            for (Object name : names) {
                if (name instanceof String text) {
                    references.add(new FunctionReference(text, given));
                }
            }
            return references.size() == 1 ? references.getFirst() : references.toArray();
        }, "[the] function(s| [reference[s]]) %strings% [called with [[the] [arg[ument][s]]] %-objects%]");

        registrar.expression("NeoSkriptReflectExprProxy", "object", info -> {
            // skript-reflect's checks: the methods come from a list variable, and only interfaces can be implemented.
            String text = info.text() == null ? "" : info.text().strip();
            Matcher written = PROXY_TEXT.matcher(text);
            String list = written.matches() ? written.group(1).strip() : "";
            if (!LIST_VARIABLE.matcher(list).matches()) {
                throw new SyntaxError(list + " is not a list variable.");
            }
            String types = info.argumentLiterals().isEmpty() ? null : info.argumentLiterals().getFirst();
            if (types != null) {
                java.util.Map<String, JavaType> known = imports.of(NeoSkript.get().scriptBeingLoaded());
                for (String name : types.split("\\s*,\\s*|\\s+(?:and|or)\\s+")) {
                    JavaType type = known.get(name.strip());
                    if (type != null && !type.javaClass().isInterface()) {
                        NeoSkript.get().runtimeWarning(name.strip() + " is not an interface");
                    }
                }
            }
            return new ProxyExpression();
        }, "[a] [new] proxy [instance] of %javatypes% (using|from) %objects%");
    }

    private static Object[] values(Object value) {
        return value instanceof Object[] several ? several : value == null ? new Object[0] : new Object[] {value};
    }

    /** The proxy expression: it's given the list variable with its indices, the methods' names. */
    private static final class ProxyExpression implements Expression, KeyAware {

        @Override
        public boolean implicitKeys() {
            return true;
        }

        @Override
        public Object get(TriggerContext context, Object[] arguments) {
            List<Class<?>> interfaces = new ArrayList<>();
            for (Object type : values(arguments[0])) {
                if (type instanceof JavaType javaType && javaType.javaClass().isInterface()) {
                    interfaces.add(javaType.javaClass());
                }
            }
            if (interfaces.isEmpty() || !(arguments[1] instanceof Keyed methods)) {
                return null;
            }
            // By method name in lower case, as skript-reflect looks them up.
            Map<String, FunctionReference> handlers = new HashMap<>();
            String[] keys = methods.keys();
            Object[] values = methods.values();
            for (int i = 0; i < keys.length && i < values.length; i++) {
                if (values[i] instanceof FunctionReference reference) {
                    handlers.put(keys[i].toLowerCase(Locale.ROOT), reference);
                }
            }
            ClassLoader loader = loaderFor(interfaces);
            return Proxy.newProxyInstance(loader, interfaces.toArray(Class<?>[]::new), new Handler(handlers));
        }
    }

    /**
     * A loader that sees every interface: the first one's own if it sees the rest, else this addon's, which sees every
     * plugin's classes. The proxy class is defined in it and goes when it does.
     */
    private static ClassLoader loaderFor(List<Class<?>> interfaces) {
        ClassLoader first = interfaces.getFirst().getClassLoader();
        if (first != null && sees(first, interfaces)) {
            return first;
        }
        return Proxies.class.getClassLoader();
    }

    private static boolean sees(ClassLoader loader, List<Class<?>> interfaces) {
        for (Class<?> type : interfaces) {
            try {
                if (Class.forName(type.getName(), false, loader) != type) {
                    return false;
                }
            } catch (ClassNotFoundException | LinkageError e) {
                return false;
            }
        }
        return true;
    }

    /** skript-reflect's {@code VariableInvocationHandler}: each method to its function. */
    private record Handler(Map<String, FunctionReference> handlers) implements InvocationHandler {

        @Override
        public Object invoke(Object proxy, Method method, Object[] arguments) throws Throwable {
            FunctionReference reference = handlers.get(method.getName().toLowerCase(Locale.ROOT));
            if (reference == null) {
                if (method.isDefault()) {
                    return InvocationHandler.invokeDefault(proxy, method, arguments);
                }
                return switch (method.getName()) {
                    case "toString" -> method.getParameterCount() == 0
                            ? proxy.getClass().getName() + "@" + Integer.toHexString(proxy.hashCode()) : null;
                    case "hashCode" -> method.getParameterCount() == 0 ? System.identityHashCode(proxy) : null;
                    case "equals" -> method.getParameterCount() == 1 ? proxy == arguments[0] : null;
                    default -> Coercion.WRAPPERS.containsKey(method.getReturnType()) ? zero(method.getReturnType())
                            : null;
                };
            }
            // The function gets the reference's arguments, then the proxy itself, then the method's arguments.
            List<Object> given = new ArrayList<>(List.of(reference.arguments()));
            given.add(proxy);
            if (arguments != null) {
                for (Object argument : arguments) {
                    given.add(argument == null ? null : Coercion.fromJava(argument));
                }
            }
            Object returned;
            try {
                returned = NeoSkript.get().scripts().call(reference.name(), given.toArray());
            } catch (IllegalArgumentException missing) {
                NeoSkript.get().runtimeWarning("The function " + reference.name() + " doesn't exist, so the proxy's "
                        + method.getName() + " does nothing");
                returned = null;
            }
            Class<?> type = method.getReturnType();
            if (type == void.class) {
                return null;
            }
            if (returned instanceof Object[] several) {
                returned = several.length == 0 ? null : several[0];
            }
            if (returned != null && Coercion.canConvert(returned, type)) {
                return Coercion.convert(returned, type);
            }
            return type.isPrimitive() ? zero(type) : null;
        }

        /** A primitive's zero, for a method that must return one where the function gave none. */
        private static Object zero(Class<?> type) {
            if (type == boolean.class) {
                return false;
            }
            if (type == char.class) {
                return '\0';
            }
            return Coercion.convert(0L, type);
        }
    }
}
