/*
 * Copyright (c) 2026 Xena Studios
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package co.xenastudios.neoskript.frontend.structure;

import co.xenastudios.neoskript.frontend.source.Diagnostics;
import co.xenastudios.neoskript.frontend.source.SourceFile;
import java.util.ArrayList;
import java.util.List;

/**
 * Splits a script into lines and builds the tree of sections from their indentation. This is the first
 * pass: it doesn't know what any line means yet.
 *
 * <p>Indentation is compared exactly as written, so a section's lines must all use the same indentation,
 * and it has to extend the section header's. Mixing tabs and spaces differently between lines is an error.
 */
public final class StructureParser {

    private final List<Line> lines;
    private final Diagnostics diagnostics;
    private int next;

    private StructureParser(List<Line> lines, Diagnostics diagnostics) {
        this.lines = lines;
        this.diagnostics = diagnostics;
    }

    public static List<Node> parse(SourceFile file, Diagnostics diagnostics) {
        StructureParser parser = new StructureParser(lines(file), diagnostics);
        return parser.block("");
    }

    /** The file's code lines, with comments, blank lines and block comments removed. */
    static List<Line> lines(SourceFile file) {
        List<Line> lines = new ArrayList<>();
        boolean inBlockComment = false;
        List<String> raw = file.lines();
        for (int i = 0; i < raw.size(); i++) {
            String text = raw.get(i);
            if (text.strip().equals(Comments.BLOCK_DELIMITER)) {
                inBlockComment = !inBlockComment;
                continue;
            }
            if (inBlockComment) {
                continue;
            }
            int indentEnd = 0;
            while (indentEnd < text.length() && Character.isWhitespace(text.charAt(indentEnd))) {
                indentEnd++;
            }
            String code = Comments.strip(text.substring(indentEnd)).stripTrailing();
            if (!code.isEmpty()) {
                lines.add(new Line(i + 1, text.substring(0, indentEnd), code));
            }
        }
        return lines;
    }

    /** Parses the lines at {@code indent}, returning at a line that belongs to an outer level. */
    private List<Node> block(String indent) {
        List<Node> nodes = new ArrayList<>();
        while (next < lines.size()) {
            Line line = lines.get(next);
            if (line.indent().equals(indent)) {
                next++;
                nodes.add(line.opensSection() ? new Node.Section(line, children(line)) : new Node.Simple(line));
            } else if (indent.startsWith(line.indent())) {
                return nodes;
            } else {
                if (extendsIndent(line.indent(), indent)) {
                    error(line, "Unexpected indentation: only the lines under a section (a line ending in ':')"
                            + " can be indented further");
                } else {
                    error(line, "Indentation error: expected " + describe(indent) + ", but found "
                            + describe(line.indent()));
                }
                skipDeeperThan(indent);
            }
        }
        return nodes;
    }

    private List<Node> children(Line header) {
        String parentIndent = header.indent();
        if (next >= lines.size() || parentIndent.startsWith(lines.get(next).indent())) {
            return List.of();
        }
        String childIndent = lines.get(next).indent();
        if (!extendsIndent(childIndent, parentIndent)) {
            error(lines.get(next), "Indentation error: expected " + describe(parentIndent) + " or more, but found "
                    + describe(childIndent));
            skipDeeperThan(parentIndent);
            return List.of();
        }
        List<Node> children = block(childIndent);
        // The block ended at a line that isn't at the section's level or an outer one.
        if (next < lines.size() && !parentIndent.startsWith(lines.get(next).indent())) {
            error(lines.get(next), "Indentation error: expected " + describe(childIndent) + ", but found "
                    + describe(lines.get(next).indent()));
            skipDeeperThan(parentIndent);
        }
        return children;
    }

    /** Skips lines until one is at {@code indent} or an outer level, so one mistake is reported once. */
    private void skipDeeperThan(String indent) {
        while (next < lines.size() && !indent.startsWith(lines.get(next).indent())) {
            next++;
        }
    }

    private static boolean extendsIndent(String indent, String base) {
        return indent.length() > base.length() && indent.startsWith(base);
    }

    private void error(Line line, String message) {
        diagnostics.error(line.number(), 0, line.indent().length(), message);
    }

    static String describe(String indent) {
        if (indent.isEmpty()) {
            return "no indentation";
        }
        long tabs = indent.chars().filter(c -> c == '\t').count();
        long spaces = indent.length() - tabs;
        List<String> parts = new ArrayList<>();
        if (tabs > 0) {
            parts.add(tabs + (tabs == 1 ? " tab" : " tabs"));
        }
        if (spaces > 0) {
            parts.add(spaces + (spaces == 1 ? " space" : " spaces"));
        }
        return String.join(" and ", parts);
    }
}
