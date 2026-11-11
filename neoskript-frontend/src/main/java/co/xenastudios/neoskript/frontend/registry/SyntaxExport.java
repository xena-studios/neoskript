/*
 * Copyright (c) 2026 Xena Studios
 * SPDX-License-Identifier: GPL-3.0-only
 */
package co.xenastudios.neoskript.frontend.registry;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Everything a registry knows that a reader of the syntax wants: each element's patterns as they were written at
 * registration, return types, event names and values, types and built-in functions. The plugin writes it as JSON
 * ({@link #toJson()}) for neoskript.dev's syntax reference; {@link #fromJson(String)} reads it back. Plain JSON, no
 * library, so the browser parser can load the same file.
 *
 * @param format the version of this format; readers should refuse one they don't know
 * @param neoskript the NeoSkript version that wrote it
 * @param server the server it was written on, e.g. {@code Paper 26.3}
 */
public record SyntaxExport(int format, String neoskript, String server, List<Element> elements, List<Type> types,
        List<Function> functions) {

    public static final int FORMAT = 1;

    public SyntaxExport {
        elements = List.copyOf(elements);
        types = List.copyOf(types);
        functions = List.copyOf(functions);
    }

    /**
     * A syntax element.
     *
     * @param kind {@code structure}, {@code event}, {@code section}, {@code effect}, {@code condition} or
     *     {@code expression}
     * @param returnType for expressions, the id of the type they return; {@code null} otherwise
     * @param eventName for events, what Skript calls the event ({@code Break / Mine}), or {@code null} if it has
     *     no counterpart in Skript or isn't an event
     * @param eventValues for server events, the values code under the event can use; empty otherwise
     */
    public record Element(String kind, String id, List<String> patterns, String returnType, String eventName,
            List<EventValue> eventValues) {

        public Element {
            patterns = List.copyOf(patterns);
            eventValues = List.copyOf(eventValues);
        }
    }

    /**
     * A value an event has: {@code event-player}, or with {@code time} -1 {@code past event-block}, 1 for future.
     *
     * @param type the id of the value's type
     */
    public record EventValue(String type, int time) {}

    /**
     * A type.
     *
     * @param literals the values written as words, for a closed set like game modes; empty otherwise
     */
    public record Type(String id, String name, String plural, List<String> literals) {

        public Type {
            literals = List.copyOf(literals);
        }
    }

    /** A built-in function. {@code returnType} is {@code null} if it returns nothing. */
    public record Function(String name, List<FunctionInfo.Parameter> parameters, String returnType,
            boolean returnsPlural) {

        public Function {
            parameters = List.copyOf(parameters);
        }
    }

    /** The lower-case name of a kind, as the export writes it. */
    public static String kindName(SyntaxKind kind) {
        return kind.name().toLowerCase(Locale.ROOT);
    }

    public String toJson() {
        StringBuilder out = new StringBuilder();
        Map<String, Object> root = new LinkedHashMap<>();
        root.put("format", format);
        root.put("neoskript", neoskript);
        root.put("server", server);
        List<Object> elementList = new ArrayList<>();
        for (Element element : elements) {
            Map<String, Object> map = new LinkedHashMap<>();
            map.put("kind", element.kind());
            map.put("id", element.id());
            map.put("patterns", element.patterns());
            if (element.returnType() != null) {
                map.put("returnType", element.returnType());
            }
            if (element.eventName() != null) {
                map.put("eventName", element.eventName());
            }
            if (!element.eventValues().isEmpty()) {
                List<Object> values = new ArrayList<>();
                for (EventValue value : element.eventValues()) {
                    Map<String, Object> v = new LinkedHashMap<>();
                    v.put("type", value.type());
                    v.put("time", value.time());
                    values.add(v);
                }
                map.put("eventValues", values);
            }
            elementList.add(map);
        }
        root.put("elements", elementList);
        List<Object> typeList = new ArrayList<>();
        for (Type type : types) {
            Map<String, Object> map = new LinkedHashMap<>();
            map.put("id", type.id());
            map.put("name", type.name());
            map.put("plural", type.plural());
            if (!type.literals().isEmpty()) {
                map.put("literals", type.literals());
            }
            typeList.add(map);
        }
        root.put("types", typeList);
        List<Object> functionList = new ArrayList<>();
        for (Function function : functions) {
            Map<String, Object> map = new LinkedHashMap<>();
            map.put("name", function.name());
            List<Object> parameters = new ArrayList<>();
            for (FunctionInfo.Parameter parameter : function.parameters()) {
                Map<String, Object> p = new LinkedHashMap<>();
                p.put("name", parameter.name());
                p.put("type", parameter.type());
                p.put("plural", parameter.plural());
                p.put("optional", parameter.optional());
                parameters.add(p);
            }
            map.put("parameters", parameters);
            if (function.returnType() != null) {
                map.put("returnType", function.returnType());
                map.put("returnsPlural", function.returnsPlural());
            }
            functionList.add(map);
        }
        root.put("functions", functionList);
        Json.write(root, out, 0);
        return out.append('\n').toString();
    }

    /** Reads what {@link #toJson()} wrote. */
    @SuppressWarnings("unchecked")
    public static SyntaxExport fromJson(String json) {
        Map<String, Object> root = (Map<String, Object>) Json.read(json);
        int format = ((Number) root.get("format")).intValue();
        if (format != FORMAT) {
            throw new IllegalArgumentException("Unknown syntax export format " + format + ", expected " + FORMAT);
        }
        List<Element> elements = new ArrayList<>();
        for (Object o : (List<Object>) root.get("elements")) {
            Map<String, Object> map = (Map<String, Object>) o;
            List<EventValue> values = new ArrayList<>();
            for (Object v : (List<Object>) map.getOrDefault("eventValues", List.of())) {
                Map<String, Object> value = (Map<String, Object>) v;
                values.add(new EventValue((String) value.get("type"), ((Number) value.get("time")).intValue()));
            }
            elements.add(new Element((String) map.get("kind"), (String) map.get("id"),
                    strings(map.get("patterns")), (String) map.get("returnType"), (String) map.get("eventName"),
                    values));
        }
        List<Type> types = new ArrayList<>();
        for (Object o : (List<Object>) root.get("types")) {
            Map<String, Object> map = (Map<String, Object>) o;
            types.add(new Type((String) map.get("id"), (String) map.get("name"), (String) map.get("plural"),
                    strings(map.getOrDefault("literals", List.of()))));
        }
        List<Function> functions = new ArrayList<>();
        for (Object o : (List<Object>) root.get("functions")) {
            Map<String, Object> map = (Map<String, Object>) o;
            List<FunctionInfo.Parameter> parameters = new ArrayList<>();
            for (Object p : (List<Object>) map.get("parameters")) {
                Map<String, Object> parameter = (Map<String, Object>) p;
                parameters.add(new FunctionInfo.Parameter((String) parameter.get("name"),
                        (String) parameter.get("type"), (Boolean) parameter.get("plural"),
                        (Boolean) parameter.get("optional")));
            }
            functions.add(new Function((String) map.get("name"), parameters, (String) map.get("returnType"),
                    Boolean.TRUE.equals(map.get("returnsPlural"))));
        }
        return new SyntaxExport(format, (String) root.get("neoskript"), (String) root.get("server"), elements,
                types, functions);
    }

    @SuppressWarnings("unchecked")
    private static List<String> strings(Object list) {
        return (List<String>) list;
    }
}
