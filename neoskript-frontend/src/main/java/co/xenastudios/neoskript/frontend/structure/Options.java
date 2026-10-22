/*
 * Copyright (c) 2026 Xena Studios
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package co.xenastudios.neoskript.frontend.structure;

import co.xenastudios.neoskript.frontend.source.Diagnostics;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * A script's {@code options:} sections. Each entry is {@code name: value}, and nested sections give
 * dotted names: {@code {@colors.primary}}. Every {@code {@name}} in the rest of the script is replaced
 * with its value as plain text before anything else reads the line, wherever the options are defined.
 */
public final class Options {

    static final String SECTION = "options";
    private static final String ENTRY_SEPARATOR = ": ";

    private Options() {}

    /** Collects the entries of every top-level {@code options:} section. Later entries win. */
    public static Map<String, String> collect(List<Node> nodes, Diagnostics diagnostics) {
        Map<String, String> options = new LinkedHashMap<>();
        for (Node node : nodes) {
            if (node instanceof Node.Section section && isOptionsSection(section)) {
                collect(section.children(), "", options, diagnostics);
            }
        }
        return options;
    }

    static boolean isOptionsSection(Node.Section section) {
        return section.line().key().equalsIgnoreCase(SECTION);
    }

    private static void collect(List<Node> nodes, String prefix, Map<String, String> options,
            Diagnostics diagnostics) {
        for (Node node : nodes) {
            Line line = node.line();
            switch (node) {
                case Node.Section section -> collect(section.children(), prefix + line.key() + ".", options,
                        diagnostics);
                case Node.Simple simple -> {
                    int separator = line.text().indexOf(ENTRY_SEPARATOR);
                    if (separator <= 0) {
                        diagnostics.error(line.number(), line.column(), line.text().length(),
                                "Invalid option: options are written as 'name: value'");
                        continue;
                    }
                    String name = line.text().substring(0, separator).strip();
                    String value = line.text().substring(separator + ENTRY_SEPARATOR.length());
                    options.put(prefix + name, value);
                }
            }
        }
    }

    /** Returns {@code nodes} with options replaced in every line outside {@code options:} sections. */
    public static List<Node> apply(List<Node> nodes, Map<String, String> options, Diagnostics diagnostics) {
        List<Node> result = new ArrayList<>(nodes.size());
        for (Node node : nodes) {
            if (node instanceof Node.Section section && isOptionsSection(section)) {
                result.add(node);
            } else {
                result.add(applyTo(node, options, diagnostics));
            }
        }
        return result;
    }

    private static Node applyTo(Node node, Map<String, String> options, Diagnostics diagnostics) {
        Line line = node.line();
        Line replaced = new Line(line.number(), line.indent(), substitute(line, options, diagnostics));
        return switch (node) {
            case Node.Simple simple -> new Node.Simple(replaced);
            case Node.Section section -> {
                List<Node> children = new ArrayList<>(section.children().size());
                for (Node child : section.children()) {
                    children.add(applyTo(child, options, diagnostics));
                }
                yield new Node.Section(replaced, children);
            }
        };
    }

    static String substitute(Line line, Map<String, String> options, Diagnostics diagnostics) {
        String text = line.text();
        int start = text.indexOf("{@");
        if (start < 0) {
            return text;
        }
        StringBuilder out = new StringBuilder(text.length());
        int copied = 0;
        while (start >= 0) {
            int end = text.indexOf('}', start);
            if (end < 0) {
                break;
            }
            String name = text.substring(start + 2, end);
            String value = options.get(name);
            out.append(text, copied, start);
            if (value == null) {
                diagnostics.error(line.number(), line.column() + start, end + 1 - start,
                        "Undefined option {@" + name + "}");
                out.append(text, start, end + 1);
            } else {
                out.append(value);
            }
            copied = end + 1;
            start = text.indexOf("{@", copied);
        }
        return out.append(text, copied, text.length()).toString();
    }
}
