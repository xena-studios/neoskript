/*
 * Copyright (c) 2026 Xena Studios
 * SPDX-License-Identifier: GPL-3.0-only
 */
package co.xenastudios.neoskript.web;

import co.xenastudios.neoskript.frontend.registry.SyntaxRegistry;
import co.xenastudios.neoskript.frontend.stub.SkriptRegistry;
import co.xenastudios.neoskript.frontend.stub.SkriptSyntax;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * The interim registry: Skript's docs export ({@code skript-syntax/2.17.0.json}), read without Gson and
 * handed to {@link SkriptRegistry#build(SkriptSyntax)}, exactly as the corpus tests get theirs.
 */
final class SkriptDocsRegistry implements RegistrySource {

    @Override
    public String describe(String data) {
        Map<String, Object> source = map(map(Json.parse(data)).get("source"));
        return source.get("name") + " " + source.get("version") + " syntax";
    }

    @Override
    public SyntaxRegistry load(String data) {
        return SkriptRegistry.build(read(data));
    }

    static SkriptSyntax read(String data) {
        Map<String, Object> root = map(Json.parse(data));
        Map<String, String> source = new java.util.LinkedHashMap<>();
        map(root.get("source")).forEach((key, value) -> source.put(key, String.valueOf(value)));
        List<SkriptSyntax.Type> types = new ArrayList<>();
        for (Object item : list(root.get("types"))) {
            Map<String, Object> type = map(item);
            types.add(new SkriptSyntax.Type(string(type, "id"), string(type, "name"), strings(type.get("patterns"))));
        }
        List<SkriptSyntax.Function> functions = new ArrayList<>();
        for (Object item : list(root.get("functions"))) {
            Map<String, Object> function = map(item);
            functions.add(new SkriptSyntax.Function(string(function, "name"), string(function, "signature"),
                    string(function, "returns")));
        }
        return new SkriptSyntax(source, elements(root, "structures"), elements(root, "events"),
                elements(root, "sections"), elements(root, "effects"), elements(root, "conditions"),
                elements(root, "expressions"), types, functions);
    }

    private static List<SkriptSyntax.Element> elements(Map<String, Object> root, String key) {
        List<SkriptSyntax.Element> elements = new ArrayList<>();
        for (Object item : list(root.get(key))) {
            Map<String, Object> element = map(item);
            elements.add(new SkriptSyntax.Element(string(element, "id"), strings(element.get("patterns")),
                    string(element, "returns")));
        }
        return elements;
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> map(Object value) {
        return value == null ? Map.of() : (Map<String, Object>) value;
    }

    @SuppressWarnings("unchecked")
    private static List<Object> list(Object value) {
        return value == null ? List.of() : (List<Object>) value;
    }

    private static List<String> strings(Object value) {
        return list(value).stream().map(String::valueOf).toList();
    }

    private static String string(Map<String, Object> map, String key) {
        Object value = map.get(key);
        return value == null ? null : value.toString();
    }
}
