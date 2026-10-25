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
        element = anchorTail(element);
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
            PatternElement first = body instanceof PatternElement.Sequence sequence ? sequence.elements().getFirst() : body;
            PatternElement tagged = tagEach(first);
            if (tagged != null) {
                // ':(a|b)' is '(a:a|b:b)', as in Skript.
                body = first == body ? tagged : replaceFirst((PatternElement.Sequence) body, tagged);
            } else {
                String text = literalText(body).toLowerCase(Locale.ROOT);
                tag = text.isEmpty() ? null : text;
            }
        }
        return new PatternElement.Alternative(body, mark, tag);
    }

    /**
     * {@code (a|b)} or {@code [a|b]} after an empty tag, with each alternative that's plain text tagged with
     * that text, as Skript's {@code :(a|b)} is; or {@code null} if {@code element} isn't such a choice.
     */
    private static PatternElement tagEach(PatternElement element) {
        PatternElement.Choice choice = element instanceof PatternElement.Choice c ? c
                : element instanceof PatternElement.Optional optional ? optional.choice() : null;
        if (choice == null || choice.alternatives().size() < 2) {
            return null;
        }
        List<PatternElement.Alternative> alternatives = new ArrayList<>();
        for (PatternElement.Alternative alternative : choice.alternatives()) {
            String text = plainText(alternative.body());
            // Alternatives with marks or tags of their own, or more than text to them, are left as they are.
            alternatives.add(text == null || text.isEmpty() || alternative.mark() != 0 || alternative.tag() != null
                    ? alternative
                    : new PatternElement.Alternative(alternative.body(), 0, text.toLowerCase(Locale.ROOT)));
        }
        PatternElement.Choice tagged = new PatternElement.Choice(alternatives);
        return element instanceof PatternElement.Optional ? new PatternElement.Optional(tagged) : tagged;
    }

    /** The text of an element that's only text and spaces, or {@code null}. */
    private static String plainText(PatternElement element) {
        return switch (element) {
            case PatternElement.Literal literal -> literal.text();
            case PatternElement.Whitespace whitespace -> " ";
            case PatternElement.Sequence sequence -> {
                StringBuilder text = new StringBuilder();
                for (PatternElement each : sequence.elements()) {
                    String part = plainText(each);
                    if (part == null) {
                        yield null;
                    }
                    text.append(part);
                }
                yield text.toString().strip();
            }
            default -> null;
        };
    }

    private static PatternElement.Sequence replaceFirst(PatternElement.Sequence sequence, PatternElement first) {
        List<PatternElement> elements = new ArrayList<>(sequence.elements());
        elements.set(0, first);
        return new PatternElement.Sequence(elements);
    }

    /** The index of {@code ¦} if the alternative starts with {@code digits¦} or {@code -digits¦}, otherwise -1. */
    private int markEnd() {
        int start = pos < source.length() && source.charAt(pos) == '-' ? pos + 1 : pos;
        int i = start;
        while (i < source.length() && Character.isDigit(source.charAt(i))) {
            i++;
        }
        return i > start && i < source.length() && source.charAt(i) == '¦' ? i : -1;
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
        boolean tagNext = false;
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
                    PatternElement group = c == '[' ? new PatternElement.Optional(choice) : choice;
                    PatternElement tagged = tagNext ? tagEach(group) : null;
                    elements.add(tagged != null ? tagged : group);
                    tagNext = false;
                }
                case ':' -> {
                    // ':(a|b)' within a sequence: each choice tagged with its text, as in Skript.
                    char next = pos + 1 < source.length() ? source.charAt(pos + 1) : 0;
                    if (literal.isEmpty() && (next == '(' || next == '[')) {
                        tagNext = true;
                    } else {
                        literal.append(c);
                    }
                    pos++;
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
                new PlaceholderSpec(typeNames, nullable, literalOnly, changeableOnly, time), null, false);
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
                    regex.equals(".+") || regex.equals("(.+)"), false);
            pos = end + 1;
            return element;
        } catch (PatternSyntaxException e) {
            throw error("Invalid regex <" + regex + ">: " + e.getDescription());
        }
    }

    /** Records the literals that can come after each placeholder and regex in a sequence. */
    private static void linkLookahead(List<PatternElement> elements) {
        for (int i = 0; i < elements.size(); i++) {
            List<String> next = i + 1 < elements.size() ? leadingLiterals(elements, i + 1) : null;
            if (next == null) {
                continue;
            }
            if (elements.get(i) instanceof PatternElement.Placeholder p) {
                elements.set(i, new PatternElement.Placeholder(p.index(), p.spec(), next, p.atEnd()));
            } else if (elements.get(i) instanceof PatternElement.Regex r) {
                elements.set(i, new PatternElement.Regex(r.index(), r.regex(), next, r.matchesCode(), r.atEnd()));
            }
        }
    }

    /**
     * The literals one of which must come first in {@code elements[from...]}, skipping whitespace, or null
     * if that part can start with something else or be empty.
     */
    private static List<String> leadingLiterals(List<PatternElement> elements, int from) {
        int i = from;
        while (i < elements.size() && elements.get(i) instanceof PatternElement.Whitespace) {
            i++;
        }
        if (i >= elements.size()) {
            return null;
        }
        return startLiterals(elements.get(i));
    }

    private static List<String> startLiterals(PatternElement element) {
        return switch (element) {
            case PatternElement.Literal literal -> List.of(literal.text());
            case PatternElement.Sequence sequence -> leadingLiterals(sequence.elements(), 0);
            case PatternElement.Choice choice -> {
                List<String> all = new ArrayList<>();
                for (PatternElement.Alternative alternative : choice.alternatives()) {
                    List<String> literals = startLiterals(alternative.body());
                    if (literals == null) {
                        yield null;
                    }
                    all.addAll(literals);
                }
                yield all;
            }
            default -> null;
        };
    }

    /**
     * Marks the placeholders and regexes that end the pattern: nothing can follow them, so they have to
     * cover the rest of the input. That includes one at the end of a trailing optional part.
     */
    private static PatternElement anchorTail(PatternElement element) {
        return switch (element) {
            case PatternElement.Placeholder p -> new PatternElement.Placeholder(p.index(), p.spec(), null, true);
            case PatternElement.Regex r -> new PatternElement.Regex(r.index(), r.regex(), null, r.matchesCode(), true);
            case PatternElement.Sequence sequence -> {
                List<PatternElement> elements = new ArrayList<>(sequence.elements());
                for (int i = elements.size() - 1; i >= 0; i--) {
                    PatternElement last = elements.get(i);
                    if (last instanceof PatternElement.Whitespace) {
                        continue;
                    }
                    // Only the last element: an earlier optional part may be followed by a later one.
                    elements.set(i, anchorTail(last));
                    break;
                }
                yield new PatternElement.Sequence(elements);
            }
            case PatternElement.Choice choice -> new PatternElement.Choice(choice.alternatives().stream()
                    .map(a -> new PatternElement.Alternative(anchorTail(a.body()), a.mark(), a.tag()))
                    .toList());
            case PatternElement.Optional optional ->
                    new PatternElement.Optional((PatternElement.Choice) anchorTail(optional.choice()));
            default -> element;
        };
    }

    private static void flush(StringBuilder literal, List<PatternElement> elements) {
        if (!literal.isEmpty()) {
            elements.add(new PatternElement.Literal(literal.toString()));
            literal.setLength(0);
        }
    }

    /**
     * The text an element starts with, up to the first part that isn't text, for {@code :text} tags: as in
     * Skript, {@code :foo bar [baz]} is tagged {@code foo bar}.
     */
    private static String literalText(PatternElement element) {
        return switch (element) {
            case PatternElement.Literal literal -> literal.text();
            case PatternElement.Whitespace whitespace -> " ";
            case PatternElement.Sequence sequence -> {
                StringBuilder text = new StringBuilder();
                for (PatternElement each : sequence.elements()) {
                    if (!(each instanceof PatternElement.Literal) && !(each instanceof PatternElement.Whitespace)) {
                        break;
                    }
                    text.append(literalText(each));
                }
                yield text.toString().strip();
            }
            default -> "";
        };
    }

    private InvalidPatternException error(String message) {
        return new InvalidPatternException(source, pos, message);
    }
}
