/*
 * Copyright (c) 2026 Xena Studios
 * SPDX-License-Identifier: GPL-3.0-only
 */
package co.xenastudios.neoskript.harness.scenario;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.stream.Stream;

/**
 * The {@code @Example}s in the reference Skript's source, each as a script. Examples that are only
 * statements go inside a trigger that never runs on a server without players, so loading them checks
 * how they parse without running anything.
 */
final class DocExamples {

    record Example(String name, String script) {}

    /** Lines that start a structure, so the example is a whole script already. */
    private static final List<String> STRUCTURES = List.of(
            "on ", "command ", "function ", "local function ", "every ", "at ", "options:", "aliases:", "variables:",
            "import ", "using ");

    private DocExamples() {}

    /** Every example under {@code sources} (Skript's {@code src/main/java}), sorted by element and position. */
    static List<Example> load(Path sources) throws IOException {
        List<Path> files;
        try (Stream<Path> walk = Files.walk(sources)) {
            files = walk.filter(path -> path.toString().endsWith(".java")).sorted().toList();
        }
        List<Example> examples = new ArrayList<>();
        for (Path file : files) {
            String element = file.getFileName().toString().replace(".java", "");
            List<String> found = examples(Files.readString(file));
            for (int i = 0; i < found.size(); i++) {
                examples.add(new Example(element + "-" + (i + 1), script(found.get(i))));
            }
        }
        return examples;
    }

    /** The examples' texts in one Java source file. */
    static List<String> examples(String source) {
        List<String> examples = new ArrayList<>();
        int at = 0;
        while ((at = source.indexOf("@Example", at)) >= 0) {
            boolean several = source.startsWith("@Examples(", at);
            if (!several && !source.startsWith("@Example(", at)) {
                at++;
                continue;
            }
            int lineStart = source.lastIndexOf('\n', at) + 1;
            at = source.indexOf('(', at) + 1;
            if (source.substring(lineStart, at).stripLeading().startsWith("*")) {
                continue; // In a Javadoc comment.
            }
            List<String> parts = new ArrayList<>();
            int[] end = {at};
            while (true) {
                int quote = skipToLiteral(source, end[0]);
                if (quote < 0) {
                    break;
                }
                parts.add(literal(source, quote, end));
                char next = nextSignificant(source, end[0]);
                if (next == '+' || next == ',' || next == '{') {
                    end[0] = source.indexOf(next, end[0]) + 1;
                    continue;
                }
                break;
            }
            if (!parts.isEmpty()) {
                examples.add(several ? String.join("\n", parts) : String.join("", parts));
            }
            at = end[0];
        }
        return examples;
    }

    private static int skipToLiteral(String source, int from) {
        for (int i = from; i < source.length(); i++) {
            char c = source.charAt(i);
            if (c == '"') {
                return i;
            }
            if (!Character.isWhitespace(c) && c != '{' && c != ',') {
                return -1;
            }
        }
        return -1;
    }

    private static char nextSignificant(String source, int from) {
        for (int i = from; i < source.length(); i++) {
            if (!Character.isWhitespace(source.charAt(i))) {
                return source.charAt(i);
            }
        }
        return 0;
    }

    /** Reads the string literal or text block starting at {@code quote}, and sets {@code end[0]} after it. */
    private static String literal(String source, int quote, int[] end) {
        if (source.startsWith("\"\"\"", quote)) {
            int start = source.indexOf('\n', quote) + 1;
            int close = start;
            while (true) {
                close = source.indexOf("\"\"\"", close);
                if (source.charAt(close - 1) != '\\') {
                    break;
                }
                close += 3;
            }
            end[0] = close + 3;
            return source.substring(start, close).stripIndent().translateEscapes();
        }
        int i = quote + 1;
        while (source.charAt(i) != '"') {
            i += source.charAt(i) == '\\' ? 2 : 1;
        }
        end[0] = i + 1;
        return source.substring(quote + 1, i).translateEscapes();
    }

    /** The example as a script: unescaped from the docs' HTML, and inside a trigger if it's only statements. */
    static String script(String example) {
        String text = example.replace("&lt;", "<").replace("&gt;", ">").replace("&quot;", "\"").replace("&amp;", "&");
        List<String> lines = text.lines().toList();
        String first = lines.stream()
                .filter(line -> !line.isBlank() && !line.stripLeading().startsWith("#"))
                .findFirst()
                .orElse("");
        String lower = first.toLowerCase(Locale.ROOT);
        if (!first.isEmpty() && !Character.isWhitespace(first.charAt(0))
                && STRUCTURES.stream().anyMatch(lower::startsWith)) {
            return text.endsWith("\n") ? text : text + "\n";
        }
        StringBuilder script = new StringBuilder(trigger(text.toLowerCase(Locale.ROOT))).append('\n');
        for (String line : lines) {
            script.append(line.isBlank() ? "" : "\t" + line).append('\n');
        }
        return script.toString();
    }

    /** A trigger with the event values the example likely reads. None of them fire on a server without players. */
    private static String trigger(String text) {
        if (text.contains("victim") || text.contains("attacker") || text.contains("damage")) {
            return "on damage:";
        }
        if (text.contains("clicked") || text.contains("event-block") || text.contains("targeted block")) {
            return "on right click:";
        }
        if (text.contains("message") || text.contains("chat")) {
            return "on chat:";
        }
        return "on join:";
    }
}
