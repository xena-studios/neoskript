/*
 * Copyright (c) 2026 Xena Studios
 * SPDX-License-Identifier: GPL-3.0-only
 */
package co.xenastudios.neoskript.compat;

import ch.njol.skript.classes.ClassInfo;
import ch.njol.skript.classes.Parser;
import ch.njol.skript.lang.ParseContext;
import co.xenastudios.neoskript.api.NeoSkript;
import co.xenastudios.neoskript.api.registration.Parameter;
import co.xenastudios.neoskript.api.registration.Registrar;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;
import org.bukkit.plugin.java.JavaPlugin;
import org.skriptlang.skript.bukkit.lang.eventvalue.EventValue;
import org.skriptlang.skript.common.function.DefaultFunction;
import org.skriptlang.skript.lang.comparator.Relation;
import org.skriptlang.skript.registration.SyntaxInfo;
import org.skriptlang.skript.registration.SyntaxRegistry;

/**
 * What an addon registered, as NeoSkript's syntax: its types, conversions, comparisons, functions and event values,
 * then its expressions, effects and conditions. Each goes on its own: one that fails is logged and left out.
 */
final class CompatRegistration {

    /** How far each of Skript's shared registries has gone to NeoSkript, so each item goes once. */
    private static final class Sent {
        int types;
        int conversions;
        int comparisons;
        int functions;
        int eventValues;
        int skriptSyntax;
    }

    private static final Map<Compat, Sent> SENT = java.util.Collections.synchronizedMap(new java.util.WeakHashMap<>());

    private final Compat compat;
    private final Registrar registrar;
    private final String addon;
    private final Map<String, Integer> counts = new LinkedHashMap<>();
    private final Map<String, Integer> skipped = new LinkedHashMap<>();

    private CompatRegistration(Compat compat, Registrar registrar, String addon) {
        this.compat = compat;
        this.registrar = registrar;
        this.addon = addon;
    }

    /** Registers {@code addon} with NeoSkript: its syntax goes when NeoSkript takes addons' syntax. */
    static void toNeoSkript(Compat compat, CompatAddon addon) {
        NeoSkript.get().addon(addon.name(), version(addon.source()), registrar -> {
            if (Compat.running() && Compat.get() == compat) {
                new CompatRegistration(compat, registrar, addon.name()).register(addon);
            }
        });
    }

    private static String version(Class<?> source) {
        try {
            return JavaPlugin.getProvidingPlugin(source).getPluginMeta().getVersion();
        } catch (RuntimeException notAPlugin) {
            return "";
        }
    }

    private void register(CompatAddon addon) {
        Sent sent = SENT.computeIfAbsent(compat, key -> new Sent());
        synchronized (sent) {
            List<ClassInfo<?>> types = compat.types().registered();
            for (; sent.types < types.size(); sent.types++) {
                ClassInfo<?> info = types.get(sent.types);
                attempt("type", info.getCodeName(), () -> type(info));
            }
            List<CompatConversions.Conversion> conversions = compat.conversions().all();
            for (; sent.conversions < conversions.size(); sent.conversions++) {
                CompatConversions.Conversion conversion = conversions.get(sent.conversions);
                attempt("conversion", conversion.from().getSimpleName() + " to " + conversion.to().getSimpleName(),
                        () -> conversion(conversion));
            }
            List<CompatComparisons.Comparison> comparisons = compat.comparisons().all();
            for (; sent.comparisons < comparisons.size(); sent.comparisons++) {
                CompatComparisons.Comparison comparison = comparisons.get(sent.comparisons);
                attempt("comparison", comparison.first().getSimpleName() + " and "
                        + comparison.second().getSimpleName(), () -> comparison(comparison));
            }
            List<DefaultFunction<?>> functions = compat.functions().all();
            for (; sent.functions < functions.size(); sent.functions++) {
                DefaultFunction<?> function = functions.get(sent.functions);
                attempt("function", function.name(), () -> function(function));
            }
            List<EventValue<?, ?>> eventValues = compat.eventValues().elements();
            for (; sent.eventValues < eventValues.size(); sent.eventValues++) {
                EventValue<?, ?> value = eventValues.get(sent.eventValues);
                attempt("event value", value.valueClass().getSimpleName() + " of "
                        + value.eventClass().getSimpleName(), () -> eventValue(value));
            }
            List<Map.Entry<SyntaxRegistry.Key<?>, SyntaxInfo<?>>> skript =
                    compat.skript().compatSyntaxRegistry().ordered();
            syntax(skript.subList(sent.skriptSyntax, skript.size()));
            sent.skriptSyntax = skript.size();
        }
        syntax(addon.compatSyntaxRegistry().ordered());
        StringBuilder summary = new StringBuilder();
        counts.forEach((kind, count) -> summary.append(summary.isEmpty() ? "" : ", ").append(count).append(' ')
                .append(kind));
        compat.logger().info(addon + " on NeoSkript: " + (summary.isEmpty() ? "nothing" : summary));
        if (!skipped.isEmpty()) {
            StringBuilder left = new StringBuilder();
            skipped.forEach((kind, count) -> left.append(left.isEmpty() ? "" : ", ").append(count).append(' ')
                    .append(kind));
            compat.logger().info(addon + " also registered what NeoSkriptCompat doesn't support yet, so scripts can't"
                    + " use it: " + left);
        }
    }

    private boolean attempt(String kind, String what, Runnable registration) {
        try {
            registration.run();
            return true;
        } catch (RuntimeException | LinkageError e) {
            compat.logger().log(Level.WARNING, addon + "'s " + kind + " " + what + " couldn't be registered", e);
            return false;
        }
    }

    private void count(String kind) {
        counts.merge(kind, 1, Integer::sum);
    }

    private void skip(String kind) {
        skipped.merge(kind, 1, Integer::sum);
    }

    // ------------------------------------------------------------ types

    private <T> void type(ClassInfo<T> info) {
        if (compat.types().neoskriptHas(info)) {
            return;
        }
        String id = info.getCodeName();
        Class<T> type = info.getC();
        registrar.type(id, id, type);
        @SuppressWarnings("unchecked")
        Parser<T> parser = (Parser<T>) info.getParser();
        if (parser != null) {
            if (parser.canParse(ParseContext.DEFAULT)) {
                registrar.literalParser(id, text -> parse(parser, text, ParseContext.DEFAULT));
            }
            if (parser.canParse(ParseContext.COMMAND)) {
                registrar.parser(id, text -> parse(parser, text, ParseContext.COMMAND));
            }
            registrar.text(type, value -> parser.toString(value, 0));
            registrar.variableNameText(type, parser::toVariableNameString);
        }
        if (info.getSupplier() != null) {
            registrar.allValues(id, () -> {
                List<Object> all = new ArrayList<>();
                Iterator<T> values = info.getSupplier().get();
                values.forEachRemaining(all::add);
                return all;
            });
        }
        if (info.getCloner() != null) {
            registrar.cloner(type, info::clone);
        }
        @SuppressWarnings("unchecked")
        ch.njol.skript.classes.Serializer<? super T> serializer =
                (ch.njol.skript.classes.Serializer<? super T>) info.getSerializer();
        if (serializer != null && !(serializer instanceof ch.njol.skript.classes.EnumSerializer)) {
            // Saved under its code name, with its fields, as Skript saves it in variables.csv.
            registrar.storage(id, type, new CompatStorage<>(info, serializer));
        } else {
            registrar.unsavable(type);
        }
        count("types");
    }

    private Object parse(Parser<?> parser, String text, ParseContext context) {
        try {
            return parser.parse(text, context);
        } catch (RuntimeException | LinkageError e) {
            compat.logger().log(Level.FINE, addon + " couldn't parse '" + text + "'", e);
            return null;
        }
    }

    /** Whether NeoSkript has no type for exactly this class: conversions and comparisons go only for those. */
    private boolean addonOnly(Class<?> type) {
        return compat.library().exactTypeOf(type) == null;
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private void conversion(CompatConversions.Conversion conversion) {
        if (!addonOnly(conversion.from()) && !addonOnly(conversion.to())) {
            return;
        }
        registrar.converter((Class) conversion.from(), (Class) conversion.to(),
                value -> conversion.converter().convert(value), conversion.flags());
        count("conversions");
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private void comparison(CompatComparisons.Comparison comparison) {
        if (!addonOnly(comparison.first()) && !addonOnly(comparison.second())) {
            return;
        }
        registrar.comparison((Class) comparison.first(), (Class) comparison.second(),
                (a, b) -> comparison.comparator().compare(a, b) == Relation.EQUAL);
        count("comparisons");
    }

    private void function(DefaultFunction<?> function) {
        if (!(function instanceof CompatFunction<?> compatFunction)) {
            skip("functions");
            return;
        }
        List<Parameter> parameters = new ArrayList<>();
        for (CompatFunction.Param<?> parameter : compatFunction.parameters()) {
            parameters.add(new Parameter(parameter.name(), compat.typeId(parameter.type()), parameter.type().isArray(),
                    parameter.hasModifier(org.skriptlang.skript.common.function.Parameter.Modifier.OPTIONAL)));
        }
        Class<?> returns = compatFunction.returnType();
        boolean none = returns == null || returns == void.class || returns == Void.class;
        registrar.function(function.name(), parameters, none ? null : compat.typeId(returns),
                !none && returns.isArray(), arguments -> CompatValuesBridge.result(compatFunction.call(
                        CompatFunction.byName(compatFunction.parameters(),
                                CompatValuesBridge.functionArguments(compatFunction.parameters(), arguments)))));
        count("functions");
    }

    /**
     * An addon's event value, where NeoSkript has none of that type for the event: NeoSkript's own event values stay
     * as they are, and an addon's events (and NeoSkript's events missing one) get the addon's.
     */
    private <E extends org.bukkit.event.Event> void eventValue(EventValue<E, ?> value) {
        String type = compat.typeId(value.valueClass());
        if ("object".equals(type)) {
            skip("event values of types NeoSkript doesn't have");
            return;
        }
        // NeoSkript's class for the value: an array class for a list (event-entities), as the addon has it.
        Class<?> valueClass = value.valueClass();
        Class<?> component = CompatAdapters.neoskriptClass(valueClass.isArray() ? valueClass.getComponentType()
                : valueClass);
        Class<?> neoskriptClass = valueClass.isArray() ? component.arrayType() : component;
        if (compat.library().hasEventValue(value.eventClass(), type, neoskriptClass, value.time().value())) {
            return;
        }
        registrar.eventValue(value.eventClass(), type, neoskriptClass, value.time().value(),
                event -> typed(CompatValuesBridge.result(value.get(event)), neoskriptClass));
        count("event values");
    }

    /** A list value as an array of its class ({@code Entity[]}), as NeoSkript looks event values up by class. */
    private static Object typed(Object value, Class<?> valueClass) {
        if (!(value instanceof Object[] values) || !valueClass.isArray() || valueClass.isInstance(value)) {
            return value;
        }
        Object[] typed = (Object[]) java.lang.reflect.Array.newInstance(valueClass.getComponentType(), values.length);
        int count = 0;
        for (Object element : values) {
            if (valueClass.getComponentType().isInstance(element)) {
                typed[count++] = element;
            }
        }
        return count == typed.length ? typed : java.util.Arrays.copyOf(typed, count);
    }

    // ------------------------------------------------------------ syntax

    private void syntax(List<Map.Entry<SyntaxRegistry.Key<?>, SyntaxInfo<?>>> entries) {
        List<Map.Entry<SyntaxRegistry.Key<?>, SyntaxInfo<?>>> sorted = new ArrayList<>(entries);
        // Registered by priority level, registration order breaking ties; NeoSkript places each after Skript's own
        // syntax of its level (CompatElements.priority).
        sorted.sort(Comparator.comparing(entry -> CompatPriority.level(entry.getValue().priority())));
        for (Map.Entry<SyntaxRegistry.Key<?>, SyntaxInfo<?>> entry : sorted) {
            String kind = entry.getKey() instanceof CompatSyntaxRegistry.Named<?> named ? named.name() : "unknown";
            SyntaxInfo<?> info = entry.getValue();
            boolean registered = switch (kind) {
                case "expression" -> attempt("expression", info.type().getName(),
                        () -> CompatElements.expression(compat, registrar, addon, info));
                case "effect" -> {
                    if (CompatElements.walks(info.type())) {
                        skip("effects that run the rest of the trigger themselves");
                        yield false;
                    }
                    yield attempt("effect", info.type().getName(),
                            () -> CompatElements.effect(compat, registrar, addon, info));
                }
                case "condition" -> attempt("condition", info.type().getName(),
                        () -> CompatElements.condition(compat, registrar, addon, info));
                case "event" -> attempt("event", info.type().getName(),
                        () -> CompatElements.event(compat, registrar, addon, info));
                default -> {
                    skip(kind + "s");
                    yield false;
                }
            };
            if (registered) {
                count(kind + "s");
            }
        }
    }
}
