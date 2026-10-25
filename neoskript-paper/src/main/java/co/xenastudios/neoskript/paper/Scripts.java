/*
 * Copyright (c) 2026 Xena Studios
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package co.xenastudios.neoskript.paper;

import co.xenastudios.neoskript.api.registration.Parameter;
import co.xenastudios.neoskript.api.registration.Registrar;
import co.xenastudios.neoskript.runtime.ScriptCatalog;
import co.xenastudios.neoskript.runtime.ScriptRef;
import co.xenastudios.neoskript.runtime.Values;
import java.io.File;
import java.util.ArrayList;
import java.util.List;

/** Scripts as values: the scripts with a name or in a folder, all of them, and whether they're loaded. */
final class Scripts {

    private Scripts() {}

    static void register(Registrar registrar) {
        // Functions as values: 'function "heal" from {_script}', which can be run, or their result got.
        registrar.type("function", "function", co.xenastudios.neoskript.runtime.FunctionRef.class);
        registrar.parser("function", Scripts::function);
        registrar.expression("ExprFunction", "function", info -> (context, args) -> {
            String script = args[1] instanceof ScriptRef ref ? ref.name() : null;
            java.util.List<Object> found = new java.util.ArrayList<>();
            for (Object name : (Object[]) args[0]) {
                co.xenastudios.neoskript.runtime.FunctionRef function = co.xenastudios.neoskript.runtime.FunctionLookup.find(
                        functionName(Values.text(name)), script);
                if (function != null) {
                    found.add(function);
                }
            }
            return found.size() == 1 ? found.getFirst() : found.toArray();
        }, "[the] function[s] [named] %strings% [(from|in) %script%]");
        registrar.expression("ExprAllFunctions", "function", info -> (context, args) ->
                args[0] instanceof ScriptRef script
                        ? co.xenastudios.neoskript.runtime.FunctionLookup.definedIn(script.name()).toArray() : new Object[0],
                "[all [[of] the]|the] functions (from|in) %script%");
        // 'load script named X', 'disable X': scripts loaded, reloaded, unloaded or disabled while running.
        registrar.effect("EffScriptFile", info -> {
            String action = info.hasTag("reload") ? "reload" : info.hasTag("unload") ? "unload"
                    : info.hasTag("disable") ? "disable" : "load";
            return (context, args) -> {
                for (Object value : (Object[]) args[0]) {
                    ScriptRef script = value instanceof ScriptRef ref ? ref : ScriptCatalog.get().find(Values.text(value));
                    if (script == null) {
                        continue;
                    }
                    switch (action) {
                        case "reload" -> ScriptCatalog.get().reload(script);
                        case "unload" -> ScriptCatalog.get().unload(script);
                        case "disable" -> ScriptCatalog.get().disable(script);
                        default -> ScriptCatalog.get().load(script);
                    }
                }
            };
        }, "(enable|load|reload:reload|unload:unload|disable:disable) [script] [file] [named] %scripts/strings%");
        registrar.effect("EffRun", info -> (context, args) -> {
            if (args[0] instanceof co.xenastudios.neoskript.runtime.FunctionRef function) {
                function.call(args[1] == null ? new Object[0] : (Object[]) args[1]);
            }
        }, "run %function% [with arg[ument]s %objects%]");
        registrar.expression("ExprResult", "object", info -> (context, args) ->
                args[0] instanceof co.xenastudios.neoskript.runtime.FunctionRef function
                        ? function.call(args[1] == null ? new Object[0] : (Object[]) args[1]) : null,
                "[the] result[s] of [running|executing] %function% [with arg[ument]s %objects%]");
        registrar.expression("ExprScriptNamed", "script", info -> {
            boolean folder = info.pattern() == 1;
            return (context, args) -> {
                List<Object> scripts = new ArrayList<>();
                if (folder) {
                    String prefix = Values.text(args[0]);
                    String directory = prefix.endsWith("/") || prefix.endsWith(File.separator) ? prefix : prefix + "/";
                    for (ScriptRef script : ScriptCatalog.get().scripts(true, true)) {
                        if (script.name().startsWith(directory)) {
                            scripts.add(script);
                        }
                    }
                } else {
                    for (Object name : (Object[]) args[0]) {
                        ScriptRef script = ScriptCatalog.get().find(Values.text(name));
                        if (script != null) {
                            scripts.add(script);
                        }
                    }
                }
                return scripts.size() == 1 && !folder ? scripts.getFirst() : scripts.toArray();
            };
        }, "[the] script[s] [named] %texts%", "[the] scripts in [directory|folder] %text%");
        registrar.expression("ExprScripts", "script", info -> {
            int pattern = info.pattern();
            return (context, args) -> ScriptCatalog.get().scripts(pattern != 2, pattern != 1).toArray();
        }, "[all [[of] the]|the] scripts", "[all [[of] the]|the] (enabled|loaded) scripts",
                "[all [[of] the]|the] (disabled|unloaded) scripts");
        registrar.condition("CondIsScriptLoaded", info -> {
            boolean negated = info.hasTag("not");
            return (context, args) -> {
                Object[] scripts = (Object[]) args[0];
                for (Object script : scripts) {
                    if (!(script instanceof ScriptRef ref) || ScriptCatalog.get().loaded(ref) == negated) {
                        return false;
                    }
                }
                return scripts.length > 0;
            };
        }, "%scripts% (is|are)[not:(n't| not)] loaded", "script[s] %scripts% (is|are)[not:(n't| not)] loaded");
        // 'the current script is using "for loops"': the experiments a script turns on with 'using'.
        registrar.condition("CondIsUsingFeature", info -> {
            boolean negated = info.pattern() >= 2;
            return (context, args) -> {
                Object[] names = (Object[]) args[1];
                if (names.length == 0) {
                    return true;
                }
                boolean using = true;
                for (Object script : Values.array(args[0])) {
                    for (Object name : names) {
                        using &= script instanceof ScriptRef ref
                                && co.xenastudios.neoskript.runtime.ScriptFeatures.uses(ref.name(), Values.text(name));
                    }
                }
                return using != negated;
            };
        }, "%script% is using %strings%", "%scripts% are using %strings%", "%script% is(n't| not) using %strings%",
                "%scripts% are(n't| not) using %strings%");
    }

    /** Skript's test-only helpers. */
    static void registerTesting(Registrar registrar) {
        registrar.function("file_separator", List.<Parameter>of(), "string", false, args -> File.separator);
        // Skript's test-only syntax, for its tests of the parser. 'experimental only' needs 'using example feature'.
        registrar.expression("ExprExperimentalOnly", "boolean", info -> {
            if (!co.xenastudios.neoskript.runtime.ScriptFeatures.compilingUses("example feature")) {
                throw new co.xenastudios.neoskript.api.element.SyntaxError("Can't understand this expression: 'experimental only'");
            }
            return (context, args) -> true;
        }, "experimental only");
        registrar.expression("ExprKeyValueSet", "string", info -> new TestKeyValues(info.pattern() == 1),
                "test key values of %~objects%", "test key values");
        registrar.effect("EffTestExprList", info -> (context, args) -> {}, "test expr list %number/booleans%");
        registrar.expression("ExprTestStringLiteral", "string", info -> (context, args) -> args[0],
                "test string literal %*string%");
        // What the last 'returnable ... section' returned (see the compiler).
        registrar.expression("ExprLastReturnValues", "object", info -> (context, args) -> {
            Object values = co.xenastudios.neoskript.runtime.Values.takeReturned();
            return values == null ? new Object[0] : values;
        }, "[the] last return[ed] value[s]");
        // 'a new runnable:' is its section, to run later with 'run runnable X' (see the compiler).
        registrar.expression("ExprSecRunnable", "object", info -> (context, args) -> null, "[a] new runnable");
        registrar.deferredSectionExpression("ExprSecRunnable");
        registrar.effect("EffRunRunnable", info -> (context, args) -> {
            if (args[0] instanceof Runnable runnable) {
                runnable.run();
            }
        }, "run runnable %object%");
        // Its values can't be peeked at with 'next loop-value' when disabled (see the compiler).
        registrar.expression("ExprTestLoopPeeking", "integer", info -> (context, args) -> new Object[] {1L, 2L, 3L, 4L, 5L},
                "test loop peeking disabled", "test loop peeking enabled");
        registrar.condition("CondMethodExists", info -> {
            boolean negated = info.hasTag("dont");
            return (context, args) -> {
                Object[] signatures = (Object[]) args[0];
                for (Object signature : signatures) {
                    if (methodExists(Values.text(signature)) == negated) {
                        return false;
                    }
                }
                return signatures.length > 0;
            };
        }, "method[s] %strings% [dont:do(esn't|n't)] exist[s]");
    }

    /** Skript's test expression giving values with their own indices, or a list's with its indices. */
    private record TestKeyValues(boolean fixed) implements co.xenastudios.neoskript.api.element.Expression,
            co.xenastudios.neoskript.api.element.KeyAware {
        @Override
        public Object get(co.xenastudios.neoskript.api.element.TriggerContext context, Object[] arguments) {
            if (fixed) {
                return new co.xenastudios.neoskript.runtime.KeyedValues(new Object[] {"there", "bar", "b"},
                        new String[] {"hello", "foo", "a"});
            }
            return arguments[0];
        }

        @Override
        public boolean implicitKeys() {
            return true;
        }

        @Override
        public boolean keysRecommended() {
            return true;
        }
    }

    private static final java.util.regex.Pattern SIGNATURE = java.util.regex.Pattern.compile("(.+)#(.+)\\((.*)\\)");

    /** Whether {@code java.lang.String#indexOf(java.lang.String)} names a public method, as Skript's test syntax checks. */
    private static boolean methodExists(String signature) {
        java.util.regex.Matcher matcher = SIGNATURE.matcher(signature);
        if (!matcher.matches()) {
            return false;
        }
        try {
            Class<?> owner = Class.forName(matcher.group(1));
            List<Class<?>> parameters = new java.util.ArrayList<>();
            if (!matcher.group(3).isBlank()) {
                for (String parameter : matcher.group(3).split(",")) {
                    parameters.add(javaClass(parameter.strip()));
                }
            }
            owner.getMethod(matcher.group(2), parameters.toArray(Class<?>[]::new));
            return true;
        } catch (ClassNotFoundException | NoSuchMethodException missing) {
            return false;
        }
    }

    private static Class<?> javaClass(String name) throws ClassNotFoundException {
        if (name.endsWith("[]")) {
            return java.lang.reflect.Array.newInstance(javaClass(name.substring(0, name.length() - 2)), 0).getClass();
        }
        return switch (name) {
            case "byte" -> byte.class;
            case "short" -> short.class;
            case "int" -> int.class;
            case "long" -> long.class;
            case "float" -> float.class;
            case "double" -> double.class;
            case "boolean" -> boolean.class;
            case "char" -> char.class;
            default -> Class.forName(name);
        };
    }

    /**
     * A script's name without {@code .sk}, as {@code the name of the script} is: with its folders, or, for a
     * script using script reflection, without them.
     */
    static String name(ScriptRef script) {
        String name = script.name();
        if (co.xenastudios.neoskript.runtime.ScriptFeatures.uses(name, "script reflection")) {
            name = name.substring(Math.max(name.lastIndexOf('/'), name.lastIndexOf('\\')) + 1);
        }
        return name.endsWith(".sk") ? name.substring(0, name.length() - 3) : name;
    }

    /** A function's name as written in {@code function "heal()"}: without the brackets. */
    private static String functionName(String text) {
        String name = text.strip();
        return name.endsWith("()") ? name.substring(0, name.length() - 2).strip() : name;
    }

    /** The function a reference's text names: {@code heal}, or {@code heal from script.sk} for a local one. */
    private static co.xenastudios.neoskript.runtime.FunctionRef function(String text) {
        int from = text.indexOf(" from ");
        String name = functionName(from < 0 ? text : text.substring(0, from));
        String script = from < 0 ? null : text.substring(from + 6).strip();
        return co.xenastudios.neoskript.runtime.FunctionLookup.find(name, script);
    }
}
