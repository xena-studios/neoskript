/*
 * Copyright (c) 2026 Xena Studios
 * SPDX-License-Identifier: GPL-3.0-only
 */
package co.xenastudios.neoskript.frontend.registry;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Just enough JSON for {@link SyntaxExport}, so the frontend stays free of libraries: maps, lists, strings,
 * numbers, booleans and null. Writes with two-space indents; reads any valid JSON into those types (numbers as
 * {@link Long} or {@link Double}).
 */
final class Json {

    private Json() {}

    static void write(Object value, StringBuilder out, int indent) {
        switch (value) {
            case null -> out.append("null");
            case String s -> string(s, out);
            case Boolean b -> out.append(b);
            case Number n -> out.append(n);
            case Map<?, ?> map -> {
                if (map.isEmpty()) {
                    out.append("{}");
                    return;
                }
                out.append('{');
                boolean first = true;
                for (Map.Entry<?, ?> entry : map.entrySet()) {
                    out.append(first ? "\n" : ",\n");
                    first = false;
                    out.append("  ".repeat(indent + 1));
                    string((String) entry.getKey(), out);
                    out.append(": ");
                    write(entry.getValue(), out, indent + 1);
                }
                out.append('\n').append("  ".repeat(indent)).append('}');
            }
            case List<?> list -> {
                if (list.isEmpty()) {
                    out.append("[]");
                    return;
                }
                out.append('[');
                boolean first = true;
                for (Object item : list) {
                    out.append(first ? "\n" : ",\n");
                    first = false;
                    out.append("  ".repeat(indent + 1));
                    write(item, out, indent + 1);
                }
                out.append('\n').append("  ".repeat(indent)).append(']');
            }
            default -> throw new IllegalArgumentException("Can't write " + value.getClass() + " as JSON");
        }
    }

    private static void string(String s, StringBuilder out) {
        out.append('"');
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            switch (c) {
                case '"' -> out.append("\\\"");
                case '\\' -> out.append("\\\\");
                case '\n' -> out.append("\\n");
                case '\r' -> out.append("\\r");
                case '\t' -> out.append("\\t");
                default -> {
                    if (c < 0x20) {
                        out.append(String.format("\\u%04x", (int) c));
                    } else {
                        out.append(c);
                    }
                }
            }
        }
        out.append('"');
    }

    static Object read(String text) {
        Reader reader = new Reader(text);
        Object value = reader.value();
        reader.skipSpace();
        if (reader.pos != text.length()) {
            throw reader.error("Unexpected text after the value");
        }
        return value;
    }

    private static final class Reader {
        private final String text;
        private int pos;

        Reader(String text) {
            this.text = text;
        }

        Object value() {
            skipSpace();
            if (pos >= text.length()) {
                throw error("Unexpected end");
            }
            char c = text.charAt(pos);
            return switch (c) {
                case '{' -> object();
                case '[' -> array();
                case '"' -> string();
                case 't' -> word("true", Boolean.TRUE);
                case 'f' -> word("false", Boolean.FALSE);
                case 'n' -> word("null", null);
                default -> number();
            };
        }

        private Map<String, Object> object() {
            Map<String, Object> map = new LinkedHashMap<>();
            pos++;
            skipSpace();
            if (peek() == '}') {
                pos++;
                return map;
            }
            while (true) {
                skipSpace();
                if (peek() != '"') {
                    throw error("Expected a key");
                }
                String key = string();
                skipSpace();
                expect(':');
                map.put(key, value());
                skipSpace();
                if (peek() == ',') {
                    pos++;
                } else {
                    expect('}');
                    return map;
                }
            }
        }

        private List<Object> array() {
            List<Object> list = new ArrayList<>();
            pos++;
            skipSpace();
            if (peek() == ']') {
                pos++;
                return list;
            }
            while (true) {
                list.add(value());
                skipSpace();
                if (peek() == ',') {
                    pos++;
                } else {
                    expect(']');
                    return list;
                }
            }
        }

        private String string() {
            StringBuilder out = new StringBuilder();
            pos++;
            while (true) {
                if (pos >= text.length()) {
                    throw error("Unterminated string");
                }
                char c = text.charAt(pos++);
                if (c == '"') {
                    return out.toString();
                }
                if (c != '\\') {
                    out.append(c);
                    continue;
                }
                char e = text.charAt(pos++);
                switch (e) {
                    case '"', '\\', '/' -> out.append(e);
                    case 'b' -> out.append('\b');
                    case 'f' -> out.append('\f');
                    case 'n' -> out.append('\n');
                    case 'r' -> out.append('\r');
                    case 't' -> out.append('\t');
                    case 'u' -> {
                        out.append((char) Integer.parseInt(text.substring(pos, pos + 4), 16));
                        pos += 4;
                    }
                    default -> throw error("Bad escape \\" + e);
                }
            }
        }

        private Object number() {
            int start = pos;
            while (pos < text.length() && "+-0123456789.eE".indexOf(text.charAt(pos)) >= 0) {
                pos++;
            }
            String number = text.substring(start, pos);
            if (number.isEmpty()) {
                throw error("Unexpected character");
            }
            return number.contains(".") || number.contains("e") || number.contains("E") ? (Object) Double.valueOf(number)
                    : (Object) Long.valueOf(number);
        }

        private Object word(String word, Object value) {
            if (!text.startsWith(word, pos)) {
                throw error("Unexpected character");
            }
            pos += word.length();
            return value;
        }

        private char peek() {
            return pos < text.length() ? text.charAt(pos) : '\0';
        }

        private void expect(char c) {
            if (peek() != c) {
                throw error("Expected '" + c + "'");
            }
            pos++;
        }

        void skipSpace() {
            while (pos < text.length() && Character.isWhitespace(text.charAt(pos))) {
                pos++;
            }
        }

        IllegalArgumentException error(String message) {
            return new IllegalArgumentException(message + " at offset " + pos);
        }
    }
}
