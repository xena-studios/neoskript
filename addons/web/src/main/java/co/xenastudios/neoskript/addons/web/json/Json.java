/*
 * Copyright (c) 2026 Xena Studios
 * SPDX-License-Identifier: GPL-3.0-only
 */
package co.xenastudios.neoskript.addons.web.json;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonNull;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.JsonPrimitive;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * JSON as skJson reads and writes it, with the same library (Gson, which Paper ships): text is parsed leniently, as
 * skJson's {@code JsonParser.parseString} does, and values move between JSON and scripts as skJson's {@code Parser}
 * moves them. Where skJson breaks, this doesn't, and the addon's docs list where it differs.
 *
 * <p>Hostile input can't take the server down: Gson refuses JSON nested more than 255 deep, so it's text, not JSON,
 * and turning JSON into variables stops at {@value #MAX_DEPTH} levels.
 */
public final class Json {

    /** How deep JSON goes into list variables: deeper parts are left out. */
    public static final int MAX_DEPTH = 60;
    /** Past this index, a list of numbered variables is written as an object, not an array of mostly nulls. */
    static final int MAX_ARRAY_INDEX = 65_536;

    /** skJson's printer: indented, nulls kept, no HTML escaping. */
    private static final Gson PRETTY = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().serializeNulls()
            .create();
    /** What skJson sends as a request's body: a plain {@code new Gson()}, which escapes HTML characters. */
    private static final Gson BODY = new Gson();
    private static final Pattern WHOLE = Pattern.compile("-?\\d{1,18}");

    private Json() {}

    /**
     * Text as JSON, as skJson's {@code parse ... as json} reads it: JSON if it parses (leniently, so {@code hello}
     * is the string "hello"), else the text as a JSON string.
     *
     * @param text the text
     * @return the JSON
     */
    public static JsonElement parseOrText(String text) {
        try {
            return JsonParser.parseString(text);
        } catch (RuntimeException | StackOverflowError notJson) {
            return new JsonPrimitive(text);
        }
    }

    /**
     * Text as JSON, or {@code null} if it doesn't parse.
     *
     * @param text the text
     * @return the JSON, or {@code null}
     */
    public static JsonElement parse(String text) {
        try {
            return JsonParser.parseString(text);
        } catch (RuntimeException | StackOverflowError notJson) {
            return null;
        }
    }

    /**
     * A script's value as JSON, as skJson's {@code Parser.toJson}: JSON stays as it is, a text is parsed
     * ({@link #parseOrText}), a number or a boolean is itself, and anything else is its text.
     *
     * @param value the value
     * @return the JSON
     */
    public static JsonElement toJson(Object value) {
        return switch (value) {
            case null -> JsonNull.INSTANCE;
            case JsonElement json -> json;
            case Boolean bool -> new JsonPrimitive(bool);
            case Number number -> new JsonPrimitive(number);
            case String text -> parseOrText(text);
            default -> new JsonPrimitive(String.valueOf(value));
        };
    }

    /**
     * JSON as a script's value, as skJson's {@code Parser.fromJson}: a string is text, a boolean a boolean, a number
     * a whole number or a decimal one, null nothing, and an object or a list stays JSON (an object as a copy).
     *
     * @param json the JSON
     * @return the value, or {@code null}
     */
    public static Object fromJson(JsonElement json) {
        if (json == null || json.isJsonNull()) {
            return null;
        }
        if (json instanceof JsonPrimitive primitive) {
            if (primitive.isBoolean()) {
                return primitive.getAsBoolean();
            }
            if (primitive.isNumber()) {
                return number(primitive.getAsString());
            }
            return primitive.getAsString();
        }
        if (json instanceof JsonObject object) {
            return object.deepCopy();
        }
        return json;
    }

    /** A number as scripts have numbers: a whole number if it's written as one, else a decimal one. */
    static Object number(String text) {
        if (WHOLE.matcher(text).matches()) {
            return Long.parseLong(text);
        }
        try {
            BigDecimal exact = new BigDecimal(text);
            if (exact.scale() <= 0 && exact.abs().compareTo(BigDecimal.valueOf(Long.MAX_VALUE)) < 0
                    && !text.contains(".") && !text.toLowerCase().contains("e")) {
                return exact.longValueExact();
            }
            return exact.doubleValue();
        } catch (NumberFormatException | ArithmeticException odd) {
            return Double.NaN;
        }
    }

    /**
     * JSON as list variables, as skJson's {@code map ... to {_x::*}}: an object's keys and a list's positions (from
     * 1) are indices, nested ones make lists under them, null is left out, and parts deeper than
     * {@value #MAX_DEPTH} are left out too.
     *
     * @param json the JSON
     * @return each index with its value, a map or list for a nested one; empty for a lone value
     */
    public static Map<String, Object> toVariables(JsonElement json) {
        Object tree = tree(json, 0);
        if (tree instanceof Map<?, ?> map) {
            @SuppressWarnings("unchecked")
            Map<String, Object> result = (Map<String, Object>) map;
            return result;
        }
        if (tree instanceof List<?> list) {
            Map<String, Object> result = new LinkedHashMap<>();
            for (int i = 0; i < list.size(); i++) {
                result.put(Integer.toString(i + 1), list.get(i));
            }
            return result;
        }
        return Map.of();
    }

    private static Object tree(JsonElement json, int depth) {
        if (json instanceof JsonObject object) {
            if (depth >= MAX_DEPTH) {
                return null;
            }
            Map<String, Object> map = new LinkedHashMap<>();
            for (Map.Entry<String, JsonElement> entry : object.entrySet()) {
                Object value = tree(entry.getValue(), depth + 1);
                if (value != null) {
                    map.put(entry.getKey(), value);
                }
            }
            return map;
        }
        if (json instanceof JsonArray array) {
            if (depth >= MAX_DEPTH) {
                return null;
            }
            // Positions are kept, so a null leaves a gap, as skJson's numbering does.
            List<Object> list = new ArrayList<>(array.size());
            for (JsonElement element : array) {
                list.add(tree(element, depth + 1));
            }
            return list;
        }
        return fromJson(json);
    }

    /**
     * List variables as JSON, as skJson's {@code {_x::*}'s formatted json}: indices are keys, and a nested list whose
     * indices are all numbers is a JSON list, its values read as JSON where they parse ({@link #toJson}). A value
     * that also has a list under it is left out for the list. The top list is always an object.
     *
     * @param keys each value's path below the list ({@code home::x})
     * @param values the values
     * @return the JSON, or {@code null} if the list is empty
     */
    public static JsonElement fromVariables(String[] keys, Object[] values) {
        if (keys.length == 0) {
            return null;
        }
        Node root = new Node();
        for (int i = 0; i < keys.length; i++) {
            Node node = root;
            for (String part : keys[i].split("::", -1)) {
                node = node.children.computeIfAbsent(part, k -> new Node());
            }
            node.value = values[i];
        }
        return object(root);
    }

    /** A list variable's node: its own value and the list under it. */
    private static final class Node {
        Object value;
        final Map<String, Node> children = new LinkedHashMap<>();
    }

    private static JsonObject object(Node node) {
        JsonObject object = new JsonObject();
        for (Map.Entry<String, Node> entry : node.children.entrySet()) {
            Node child = entry.getValue();
            if (child.children.isEmpty()) {
                object.add(entry.getKey(), plain(child.value));
            } else if (numbered(child)) {
                object.add(entry.getKey(), array(child));
            } else {
                object.add(entry.getKey(), object(child));
            }
        }
        return object;
    }

    /** Whether every index under a node is a number, as skJson's {@code isNumericMap} decides. */
    private static boolean numbered(Node node) {
        int max = -1;
        for (String key : node.children.keySet()) {
            Integer index = index(key);
            if (index == null) {
                return false;
            }
            max = Math.max(max, index);
        }
        return max <= MAX_ARRAY_INDEX;
    }

    private static Integer index(String key) {
        try {
            int index = Integer.parseInt(key);
            return index < 0 ? null : index;
        } catch (NumberFormatException notNumber) {
            return null;
        }
    }

    private static JsonArray array(Node node) {
        int max = -1;
        for (String key : node.children.keySet()) {
            max = Math.max(max, index(key));
        }
        JsonElement[] slots = new JsonElement[max + 1];
        for (Map.Entry<String, Node> entry : node.children.entrySet()) {
            Node child = entry.getValue();
            // A value in a list is read as JSON where it parses; a nested list is an object of its plain values.
            slots[index(entry.getKey())] = child.children.isEmpty() ? toJson(child.value) : nested(child);
        }
        int first = 0;
        while (first < slots.length && slots[first] == null) {
            first++;
        }
        JsonArray array = new JsonArray();
        for (int i = first; i < slots.length; i++) {
            array.add(slots[i] == null ? JsonNull.INSTANCE : slots[i]);
        }
        return array;
    }

    /** A list inside a JSON list, as skJson writes it: an object, its own value under "null". */
    private static JsonObject nested(Node node) {
        JsonObject object = new JsonObject();
        if (node.value != null) {
            object.add("null", plain(node.value));
        }
        for (Map.Entry<String, Node> entry : node.children.entrySet()) {
            Node child = entry.getValue();
            object.add(entry.getKey(), child.children.isEmpty() ? plain(child.value) : nested(child));
        }
        return object;
    }

    /** A value as itself in JSON, not parsed: a text is a string. */
    private static JsonElement plain(Object value) {
        return switch (value) {
            case null -> JsonNull.INSTANCE;
            case JsonElement json -> json;
            case Boolean bool -> new JsonPrimitive(bool);
            case Number number -> new JsonPrimitive(number);
            default -> new JsonPrimitive(String.valueOf(value));
        };
    }

    /**
     * JSON indented, as skJson prints it.
     *
     * @param json the JSON
     * @return the text
     */
    public static String pretty(JsonElement json) {
        return PRETTY.toJson(json);
    }

    /**
     * JSON as a request's body, as skJson sends it.
     *
     * @param json the JSON
     * @return the text
     */
    public static String body(JsonElement json) {
        return BODY.toJson(json);
    }

    /**
     * JSON indented and coloured for chat, as skJson's {@code as pretty printed}.
     *
     * @param json the JSON
     * @return the text, with colour codes
     */
    public static String coloured(JsonElement json) {
        StringBuilder out = new StringBuilder();
        coloured(json, out, 0);
        return out.toString();
    }

    private static void coloured(JsonElement element, StringBuilder out, int indent) {
        String spaces = "  ".repeat(indent);
        if (element.isJsonObject()) {
            out.append("§7{§f\n");
            Iterator<Map.Entry<String, JsonElement>> entries = element.getAsJsonObject().entrySet().iterator();
            while (entries.hasNext()) {
                Map.Entry<String, JsonElement> entry = entries.next();
                out.append(spaces).append("  ").append("§2\"").append(entry.getKey()).append("\"§f: ");
                coloured(entry.getValue(), out, indent + 1);
                if (entries.hasNext()) {
                    out.append(",");
                }
                out.append("\n");
            }
            out.append(spaces).append("§7}§f");
        } else if (element.isJsonArray()) {
            out.append("§6[§f\n");
            JsonArray array = element.getAsJsonArray();
            for (int i = 0; i < array.size(); i++) {
                out.append(spaces).append("  ");
                coloured(array.get(i), out, indent + 1);
                if (i < array.size() - 1) {
                    out.append(",");
                }
                out.append("\n");
            }
            out.append(spaces).append("§6]§f");
        } else if (element.isJsonNull()) {
            out.append("§5null§f");
        } else {
            JsonPrimitive primitive = element.getAsJsonPrimitive();
            if (primitive.isBoolean()) {
                out.append(primitive.getAsBoolean() ? "§2true§f" : "§4false§f");
            } else if (primitive.isNumber()) {
                out.append("§9").append(primitive.getAsNumber()).append("§f");
            } else {
                out.append("§2\"").append(primitive.getAsString()).append("\"§f");
            }
        }
    }

    /**
     * The JSON at a path, as skJson's paths: parts split by {@code .}, {@code name[2]} is the list {@code name}'s
     * item at 2, and a list's items are counted from 0.
     *
     * @param json the JSON
     * @param path the path, or {@code null} for the JSON itself
     * @return what's there, or {@code null}
     */
    public static JsonElement at(JsonElement json, String path) {
        if (path == null || path.isEmpty()) {
            return json;
        }
        JsonElement current = json;
        for (String token : path.split(Pattern.quote("."))) {
            if (token.isEmpty()) {
                continue;
            }
            List<String> keys = new ArrayList<>(2);
            if (token.matches(".+\\[\\d+]")) {
                int open = token.indexOf('[');
                keys.add(token.substring(0, open));
                keys.add(token.substring(open + 1, token.length() - 1));
            } else {
                keys.add(token.replaceFirst("\\*", "").replaceFirst("\\[]", ""));
            }
            for (String key : keys) {
                current = step(current, key);
                if (current == null) {
                    return null;
                }
            }
        }
        return current;
    }

    private static JsonElement step(JsonElement json, String key) {
        if (json instanceof JsonObject object) {
            return object.get(key);
        }
        if (json instanceof JsonArray array) {
            int index;
            try {
                index = key.chars().allMatch(Character::isDigit) ? Integer.parseInt(key) : (int) Double.parseDouble(key);
            } catch (NumberFormatException notIndex) {
                return null;
            }
            return index < 0 || index >= array.size() ? null : array.get(index);
        }
        return null;
    }

    /**
     * Each value in JSON, as skJson's {@code values of}: a list's items or an object's values, as script values.
     *
     * @param json the JSON
     * @return the values, none {@code null}
     */
    public static Object[] values(JsonElement json) {
        List<Object> values = new ArrayList<>();
        if (json instanceof JsonArray array) {
            array.forEach(element -> add(values, fromJson(element)));
        } else if (json instanceof JsonObject object) {
            object.entrySet().forEach(entry -> add(values, fromJson(entry.getValue())));
        }
        return values.toArray();
    }

    private static void add(List<Object> values, Object value) {
        if (value != null) {
            values.add(value);
        }
    }
}
