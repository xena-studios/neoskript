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
        return parser.section(0, null);
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
            // A line starting with # is a comment, even ### this, where ## would otherwise be a #.
            String code = text.startsWith("#", indentEnd) ? "" : Comments.strip(text.substring(indentEnd)).stripTrailing();
            if (!code.isEmpty()) {
                lines.add(new Line(i + 1, text.substring(0, indentEnd), code));
            }
        }
        return lines;
    }

    /** Skript's warning for a line ending in ':' with nothing under it. */
    static final String EMPTY_SECTION = "Empty configuration section! You might want to indent one or more of the"
            + " subsequent lines to make them belong to this section or remove the colon at the end of the line if you"
            + " don't want this line to start a section.";

    /** One level of indentation, as Skript's config: a tab until the first indented line of a structure says. */
    private String unit = "\t";
    private boolean unitSet;

    /**
     * Skript's {@code SectionNode.load_i}: the lines at {@code level}, each with what's under it, returning at
     * a line of an outer level. A line indented wrongly is reported and left out on its own.
     */
    private List<Node> section(int level, Line header) {
        List<Node> nodes = new ArrayList<>();
        if (level == 1) {
            // Each structure's first indented line sets it again (and it stays for those that indent nothing).
            unitSet = false;
        }
        while (next < lines.size()) {
            Line line = lines.get(next);
            String indent = line.indent();
            if (!unitSet && level == 1 && !indent.isEmpty()) {
                if (indent.chars().allMatch(c -> c == ' ') || indent.chars().allMatch(c -> c == '\t')) {
                    unit = indent;
                    unitSet = true;
                } else {
                    error(line, "indentation error: indent must only consist of either spaces or tabs, but not mixed"
                            + " (found " + readable(indent) + ")");
                    next++;
                    continue;
                }
            }
            String expected = unit.repeat(level);
            if (!indent.equals(expected)) {
                if (indent.startsWith(expected) || !levels(indent)) {
                    int count = level * unit.length();
                    error(line, "indentation error: expected " + count + " " + (unit.charAt(0) == ' ' ? "space" : "tab")
                            + (count == 1 ? "" : "s") + ", but found " + readable(indent));
                    next++;
                    continue;
                }
                // A line of an outer level.
                break;
            }
            next++;
            nodes.add(line.opensSection() ? new Node.Section(line, section(level + 1, line)) : new Node.Simple(line));
        }
        if (header != null && nodes.isEmpty()) {
            diagnostics.warning(header.number(), 0, header.indent().length(), EMPTY_SECTION);
        }
        return nodes;
    }

    /** Whether {@code indent} is whole levels of indentation. */
    private boolean levels(String indent) {
        return indent.length() % unit.length() == 0 && indent.equals(unit.repeat(indent.length() / unit.length()));
    }

    /** Skript's {@code readableWhitespace}. */
    static String readable(String indent) {
        if (!indent.isEmpty() && indent.chars().allMatch(c -> c == ' ')) {
            return indent.length() + " space" + (indent.length() == 1 ? "" : "s");
        }
        if (!indent.isEmpty() && indent.chars().allMatch(c -> c == '\t')) {
            return indent.length() + " tab" + (indent.length() == 1 ? "" : "s");
        }
        return "'" + indent.replace("\t", "->").replace(' ', '_').replaceAll("\\s", "?")
                + "' [-> = tab, _ = space, ? = other whitespace]";
    }

    private void error(Line line, String message) {
        diagnostics.error(line.number(), 0, line.indent().length(), message);
    }
}
