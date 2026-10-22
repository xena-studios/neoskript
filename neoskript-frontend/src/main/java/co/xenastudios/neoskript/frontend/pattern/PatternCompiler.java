/*
 * Copyright (c) 2026 Xena Studios
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package co.xenastudios.neoskript.frontend.pattern;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.regex.PatternSyntaxException;

/**
 * Compiles Skript's pattern language:
 *
 * <ul>
 *   <li>{@code [optional]} and {@code (a|b)} choices, which nest; {@code [a|b]} is an optional choice
 *   <li>{@code %type%} placeholders, with {@code -}, {@code *}, {@code ~} flags, {@code type1/type2}, and
 *       an {@code @n} time state
 *   <li>{@code <regex>}, when a closing {@code >} follows; otherwise {@code <} is plain text
 *   <li>{@code n¦} parse marks and {@code tag:} or {@code :} parse tags at the start of an alternative
 *   <li>{@code \} to escape the next character
 * </ul>
 */
public final class PatternCompiler {

    private final String source;
    private int pos;
    private int placeholders;
    private int regexes;

    private PatternCompiler(String source) {
        this.source = source;
    }

    public static CompiledPattern compile(String pattern) {
        PatternCompiler compiler = new PatternCompiler(pattern);
        PatternElement.Alternative root = compiler.alternative(-1);
        if (compiler.pos < pattern.length()) {
            throw compiler.error("Unexpected '" + pattern.charAt(compiler.pos) + "'");
        }
        PatternElement element = root.mark() == 0 && root.tag() == null
                ? root.body()
                : new PatternElement.Choice(List.of(root));
        return new CompiledPattern(pattern, element, compiler.placeholders, compiler.regexes);
    }

    /** One alternative, up to {@code |}, the {@code close} character, or the end (when close is -1). */
    private PatternElement.Alternative alternative(int close) {
        int mark = 0;
        String tag = null;
        boolean autoTag = false;
        int markEnd = markEnd();
        if (markEnd > 0) {
            mark = Integer.parseInt(source.substring(pos, markEnd));
            pos = markEnd + 1;
        }
        int tagEnd = tagEnd();
        if (tagEnd >= 0) {
            if (tagEnd == pos) {
                autoTag = true;
            } else {
                tag = source.substring(pos, tagEnd).toLowerCase(Locale.ROOT);
            }
            pos = tagEnd + 1;
        }
        PatternElement body = sequence(close);
        if (autoTag) {
            tag = literalText(body).toLowerCase(Locale.ROOT);
        }
        return new PatternElement.Alternative(body, mark, tag);
    }

    /** The index of {@code ¦} if the alternative starts with {@code digits¦}, otherwise -1. */
    private int markEnd() {
        int i = pos;
        while (i < source.length() && Character.isDigit(source.charAt(i))) {
            i++;
        }
        return i > pos && i < source.length() && source.charAt(i) == '¦' ? i : -1;
    }

    /** The index of {@code :} if the alternative starts with {@code word:} or {@code :}, otherwise -1. */
    private int tagEnd() {
        int i = pos;
        while (i < source.length() && (Character.isLetterOrDigit(source.charAt(i)) || source.charAt(i) == '_'
                || source.charAt(i) == '-')) {
            i++;
        }
        return i < source.length() && source.charAt(i) == ':' ? i : -1;
    }

    private PatternElement sequence(int close) {
        List<PatternElement> elements = new ArrayList<>();
        StringBuilder literal = new StringBuilder();
        while (pos < source.length()) {
            char c = source.charAt(pos);
            if (c == '|' || c == close) {
                break;
            }
            if (c == ')' || c == ']') {
                throw error("Unmatched '" + c + "'");
            }
            if (Character.isWhitespace(c)) {
                flush(literal, elements);
                while (pos < source.length() && Character.isWhitespace(source.charAt(pos))) {
                    pos++;
                }
                if (elements.isEmpty() || !(elements.getLast() instanceof PatternElement.Whitespace)) {
                    elements.add(new PatternElement.Whitespace());
                }
                continue;
            }
            switch (c) {
                case '\\' -> {
                    if (pos + 1 >= source.length()) {
                        throw error("Nothing to escape after '\\'");
                    }
                    literal.append(source.charAt(pos + 1));
                    pos += 2;
                }
                case '[', '(' -> {
                    flush(literal, elements);
                    pos++;
                    PatternElement.Choice choice = choice(c == '[' ? ']' : ')');
                    elements.add(c == '[' ? new PatternElement.Optional(choice) : choice);
                }
                case '%' -> {
                    flush(literal, elements);
                    elements.add(placeholder());
                }
                case '<' -> {
                    int end = regexEnd();
                    if (end < 0) {
                        literal.append(c);
                        pos++;
                    } else {
                        flush(literal, elements);
                        elements.add(regex(end));
                    }
                }
                default -> {
                    literal.append(c);
                    pos++;
                }
            }
        }
        flush(literal, elements);
        linkLookahead(elements);
        return elements.size() == 1 ? elements.getFirst() : new PatternElement.Sequence(elements);
    }

    private PatternElement.Choice choice(char close) {
        int open = pos - 1;
        List<PatternElement.Alternative> alternatives = new ArrayList<>();
        while (true) {
            alternatives.add(alternative(close));
            if (pos >= source.length()) {
                pos = open;
                throw error("Unclosed '" + source.charAt(open) + "'");
            }
            char c = source.charAt(pos++);
            if (c == close) {
                return new PatternElement.Choice(alternatives);
            }
        }
    }

    private PatternElement placeholder() {
        int start = pos;
        int end = source.indexOf('%', start + 1);
        if (end < 0) {
            throw error("Unclosed '%'");
        }
        pos = end + 1;
        String body = source.substring(start + 1, end);
        boolean nullable = false;
        boolean literalOnly = false;
        boolean changeableOnly = false;
        int i = 0;
        for (; i < body.length(); i++) {
            char flag = body.charAt(i);
            if (flag == '-') {
                nullable = true;
            } else if (flag == '*') {
                literalOnly = true;
            } else if (flag == '~') {
                changeableOnly = true;
            } else {
                break;
            }
        }
        String types = body.substring(i);
        int time = 0;
        int at = types.indexOf('@');
        if (at >= 0) {
            try {
                time = Integer.parseInt(types.substring(at + 1));
            } catch (NumberFormatException e) {
                pos = start;
                throw error("Invalid time state in '%" + body + "%'");
            }
            types = types.substring(0, at);
        }
        List<String> typeNames = Arrays.stream(types.split("/", -1)).map(String::strip).toList();
        if (typeNames.stream().anyMatch(String::isEmpty)) {
            pos = start;
            throw error("Missing type in '%" + body + "%'");
        }
        return new PatternElement.Placeholder(placeholders++,
                new PlaceholderSpec(typeNames, nullable, literalOnly, changeableOnly, time), null);
    }

    /** The index of the {@code >} closing a regex that starts at {@code pos}, or -1 if there is none. */
    private int regexEnd() {
        for (int i = pos + 1; i < source.length(); i++) {
            char c = source.charAt(i);
            if (c == '\\') {
                i++;
            } else if (c == '>') {
                return i > pos + 1 ? i : -1;
            }
        }
        return -1;
    }

    private PatternElement regex(int end) {
        String regex = source.substring(pos + 1, end);
        try {
            PatternElement element = new PatternElement.Regex(regexes++, java.util.regex.Pattern.compile(regex), null,
                    regex.equals(".+") || regex.equals("(.+)"));
            pos = end + 1;
            return element;
        } catch (PatternSyntaxException e) {
            throw error("Invalid regex <" + regex + ">: " + e.getDescription());
        }
    }

    /** Records the literal after each placeholder and regex in a sequence, skipping whitespace. */
    private static void linkLookahead(List<PatternElement> elements) {
        for (int i = 0; i < elements.size(); i++) {
            if (elements.get(i) instanceof PatternElement.Placeholder placeholder) {
                int next = i + 1;
                if (next < elements.size() && elements.get(next) instanceof PatternElement.Whitespace) {
                    next++;
                }
                if (next < elements.size() && elements.get(next) instanceof PatternElement.Literal literal) {
                    elements.set(i, new PatternElement.Placeholder(placeholder.index(), placeholder.spec(),
                            literal.text()));
                }
            } else if (elements.get(i) instanceof PatternElement.Regex regex) {
                int next = i + 1;
                if (next < elements.size() && elements.get(next) instanceof PatternElement.Whitespace) {
                    next++;
                }
                if (next < elements.size() && elements.get(next) instanceof PatternElement.Literal literal) {
                    elements.set(i, new PatternElement.Regex(regex.index(), regex.regex(), literal.text(),
                            regex.matchesCode()));
                }
            }
        }
    }

    private static void flush(StringBuilder literal, List<PatternElement> elements) {
        if (!literal.isEmpty()) {
            elements.add(new PatternElement.Literal(literal.toString()));
            literal.setLength(0);
        }
    }

    /** The plain text of an element, for {@code :text} tags. */
    private static String literalText(PatternElement element) {
        return switch (element) {
            case PatternElement.Literal literal -> literal.text();
            case PatternElement.Whitespace whitespace -> " ";
            case PatternElement.Sequence sequence -> {
                StringBuilder text = new StringBuilder();
                sequence.elements().forEach(e -> text.append(literalText(e)));
                yield text.toString().strip();
            }
            default -> "";
        };
    }

    private InvalidPatternException error(String message) {
        return new InvalidPatternException(source, pos, message);
    }
}
