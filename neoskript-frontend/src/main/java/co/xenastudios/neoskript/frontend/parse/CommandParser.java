/*
 * Copyright (c) 2026 Xena Studios
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package co.xenastudios.neoskript.frontend.parse;

import co.xenastudios.neoskript.frontend.registry.SyntaxRegistry;
import co.xenastudios.neoskript.frontend.registry.TypeRef;
import co.xenastudios.neoskript.frontend.source.Diagnostics;
import co.xenastudios.neoskript.frontend.structure.Line;
import co.xenastudios.neoskript.frontend.structure.Node;
import co.xenastudios.neoskript.frontend.structure.Structure;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Parses {@code command} structures: the header's pattern, entries, triggers and subcommands, with Skript's
 * checks and error messages.
 */
final class CommandParser {

    private static final Pattern HEADER = Pattern.compile("command\\s+/?(\\S+)(?:\\s+(.*))?",
            Pattern.CASE_INSENSITIVE | Pattern.DOTALL);
    /** The {@code suggestions:} entry, which can also be written {@code tab completions:} or {@code tab complete:}. */
    private static final Set<String> SUGGESTIONS = Set.of("suggestions", "tab completions", "tab complete");
    private static final Pattern SUBCOMMAND = Pattern.compile("subcommand(?:\\s+(.*))?", Pattern.CASE_INSENSITIVE);
    private static final Pattern ARGUMENT_NAME = Pattern.compile("\\s*([\\p{L}_][\\p{L}\\p{N}_]*)\\s*:(.*)",
            Pattern.DOTALL);
    private static final Pattern BETWEEN = Pattern.compile("between\\s+(\\S+)\\s+and\\s+(\\S+)");
    private static final Pattern MIN = Pattern.compile("(?:above|over|greater than|more than|at least)\\s+(\\S+)");
    private static final Pattern MAX = Pattern.compile("(?:below|under|less than|fewer than|at most)\\s+(\\S+)");
    private static final Pattern LIST_SEPARATOR = Pattern.compile("\\s*(?:,\\s*(?:and\\s+|or\\s+)?|\\s+and\\s+|\\s+or\\s+)\\s*");

    /** Entries that are plain text. */
    private static final Set<String> ENTRIES = Set.of("usage", "description", "aliases", "permission",
            "executable by", "cooldown", "cooldown bypass", "cooldown storage", "prefix");
    /** Entries that are text with expressions in them. */
    private static final Set<String> MESSAGES = Set.of("permission message", "cooldown message");

    /** Who can run a command, from its {@code executable by} entry. */
    enum Executor {
        PLAYERS("players"),
        CONSOLE("the console"),
        OPERATORS("operators"),
        BLOCKS("command blocks"),
        ENTITIES("entities");

        final String description;

        Executor(String description) {
            this.description = description;
        }
    }

    private static final String MISSING_TRIGGER = "You must have a 'trigger' entry if there are no subcommands!";

    private final SyntaxRegistry registry;
    private final StatementParser statements;
    private final Diagnostics diagnostics;

    CommandParser(SyntaxRegistry registry, StatementParser statements, Diagnostics diagnostics) {
        this.registry = registry;
        this.statements = statements;
        this.diagnostics = diagnostics;
    }

    ParsedStructure.Command parse(Structure structure) {
        Node.Section section = structure.section();
        Line line = section.line();
        Matcher header = HEADER.matcher(line.key().strip());
        if (!header.matches()) {
            report(line, "Invalid command header: " + line.key());
            return null;
        }
        Set<String> names = new HashSet<>();
        List<CommandPattern.Part> pattern = pattern(line, header.group(2) == null ? "" : header.group(2), names);
        if (pattern == null) {
            return null;
        }
        ParsedStructure.CommandNode root = node(section, false, pattern, names, null);
        return root == null ? null : new ParsedStructure.Command(structure, header.group(1), root);
    }

    /** Named arguments, as {@code <x:number>}, start out as their types for the type hints. */
    private Map<String, java.util.Set<String>> hints(List<CommandPattern.Part> parts) {
        Map<String, java.util.Set<String>> hints = new java.util.HashMap<>();
        for (CommandPattern.Part part : parts) {
            if (part instanceof CommandPattern.Argument argument && argument.name() != null) {
                co.xenastudios.neoskript.frontend.registry.TypeRef ref = registry.resolveType(argument.type());
                if (ref != null) {
                    hints.put("_" + argument.name() + (argument.plural() || ref.plural() ? "::*" : ""),
                            java.util.Set.of(ref.type().id()));
                }
            } else if (part instanceof CommandPattern.Optional optional) {
                hints.putAll(hints(optional.parts()));
            }
        }
        return hints;
    }

    /**
     * A command, subcommand or group and what's under it, or {@code null} (having reported why) if it has
     * no code to run.
     *
     * @param executors who its parent can be run by, or {@code null} for anyone
     */
    private ParsedStructure.CommandNode node(Node.Section section, boolean group, List<CommandPattern.Part> pattern,
            Set<String> names, Set<Executor> executors) {
        Map<String, String> entries = new LinkedHashMap<>();
        Map<String, Expr> messages = new LinkedHashMap<>();
        List<Statement> trigger = null;
        List<Statement> suggestions = null;
        Line suggestionsLine = null;
        List<Node.Section> children = new ArrayList<>();
        boolean unexpected = false;
        for (Node child : section.children()) {
            Line entry = child.line();
            String key = entry.key().strip().toLowerCase(Locale.ROOT);
            if (child instanceof Node.Section s && (key.equals("trigger") || key.equals("subcommands")
                    || SUGGESTIONS.contains(key) || SUBCOMMAND.matcher(key).matches())) {
                if (SUGGESTIONS.contains(key)) {
                    // Its code runs while the command is typed: named arguments aren't local variables there.
                    suggestions = statements.body(s.children());
                    suggestionsLine = entry;
                } else if (key.equals("trigger")) {
                    trigger = statements.body(s.children(), hints(pattern));
                } else {
                    children.add(s);
                }
                continue;
            }
            int separator = entry.text().indexOf(':');
            String name = separator <= 0 ? "" : entry.text().substring(0, separator).strip().toLowerCase(Locale.ROOT);
            String value = separator <= 0 ? "" : entry.text().substring(separator + 1).strip();
            if (ENTRIES.contains(name) && !(group && name.equals("aliases"))) {
                entries.put(name, value);
            } else if (MESSAGES.contains(name)) {
                entries.put(name, value);
                Expr message = statements.expression(entry, '"' + value.replace("\"", "\"\"") + '"', "string", false);
                if (message != null) {
                    messages.put(name, message);
                }
            } else {
                // As Skript's entry validator: each is reported, then the command isn't loaded.
                report(entry, "Unexpected entry '" + entry.key().strip() + "'. Check whether it's spelled correctly or"
                        + " remove it");
                unexpected = true;
            }
        }
        if (unexpected) {
            return null;
        }

        Set<Executor> own = executors;
        if (entries.containsKey("executable by")) {
            own = executors(section.line(), entries.get("executable by"), executors);
            if (own == null) {
                return null;
            }
        }

        List<ParsedStructure.CommandNode> subcommands = new ArrayList<>();
        for (Node.Section child : children) {
            String key = child.line().key().strip();
            ParsedStructure.CommandNode node;
            if (key.equalsIgnoreCase("subcommands")) {
                node = node(child, true, List.of(), names, own);
            } else {
                Matcher m = SUBCOMMAND.matcher(key);
                m.matches();
                Set<String> scope = new HashSet<>(names);
                List<CommandPattern.Part> parts = pattern(child.line(), m.group(1) == null ? "" : m.group(1), scope);
                node = parts == null ? null : node(child, false, parts, scope, own);
            }
            if (node != null) {
                subcommands.add(node);
            }
        }

        if (group) {
            return subcommands.isEmpty() ? null
                    : new ParsedStructure.CommandNode(section.line(), true, pattern, entries, messages, null,
                            subcommands, suggestions, suggestionsLine);
        }
        List<ParsedStructure.CommandNode> level = new ArrayList<>();
        flatten(subcommands, level);
        long optional = level.stream().filter(s -> CommandPattern.canBeEmpty(s.pattern())).count();
        if (optional > 1) {
            report(section.line(), "It is not possible to have more than one optional subcommand at the same level.");
            return null;
        }
        if (optional == 1 && trigger != null) {
            report(section.line(), "It is not possible to have a 'trigger' entry if an optional subcommand is present!");
            return null;
        }
        if (trigger == null && subcommands.isEmpty()) {
            report(section.line(), MISSING_TRIGGER);
            return null;
        }
        return new ParsedStructure.CommandNode(section.line(), false, pattern, entries, messages, trigger, subcommands,
                suggestions, suggestionsLine);
    }

    /** The subcommands at one level: groups' subcommands count as their parent's. */
    private static void flatten(List<ParsedStructure.CommandNode> nodes, List<ParsedStructure.CommandNode> into) {
        for (ParsedStructure.CommandNode node : nodes) {
            if (node.group()) {
                flatten(node.subcommands(), into);
            } else {
                into.add(node);
            }
        }
    }

    /** An {@code executable by} entry, checked against what the parent allows. */
    private Set<Executor> executors(Line line, String value, Set<Executor> parent) {
        Set<Executor> executors = EnumSet.noneOf(Executor.class);
        for (String word : LIST_SEPARATOR.split(value.strip().toLowerCase(Locale.ROOT))) {
            Executor executor = switch (word.strip()) {
                case "player", "players" -> Executor.PLAYERS;
                case "console", "the console" -> Executor.CONSOLE;
                case "op", "ops", "operator", "operators" -> Executor.OPERATORS;
                case "block", "blocks", "command block", "command blocks" -> Executor.BLOCKS;
                case "entity", "entities" -> Executor.ENTITIES;
                default -> null;
            };
            if (executor == null) {
                report(line, "'" + word.strip() + "' is not something that can execute commands");
                return null;
            }
            executors.add(executor);
        }
        if (parent != null) {
            for (Executor executor : executors) {
                // Operators are a kind of player (or the console), so they narrow anything down.
                if (executor != Executor.OPERATORS && !parent.contains(executor)) {
                    report(line, "It is not possible to restrict execution to " + executor.description
                            + " as the parent command is only executable by "
                            + String.join(" and ", parent.stream().map(e -> e.description).toList()) + ".");
                    return null;
                }
            }
        }
        return executors;
    }

    // ---------------------------------------------------------------- patterns

    /** Parses a command pattern, or reports why it can't and returns {@code null}. */
    private List<CommandPattern.Part> pattern(Line line, String text, Set<String> names) {
        Reader reader = new Reader(text);
        try {
            List<List<CommandPattern.Part>> alternatives = alternatives(line, reader, names, (char) 0);
            if (alternatives.size() != 1) {
                throw new PatternException("A command can't start with alternatives outside brackets: " + text);
            }
            return alternatives.getFirst();
        } catch (PatternException e) {
            report(line, e.getMessage());
            return null;
        }
    }

    private static final class Reader {
        final String text;
        int position;

        Reader(String text) {
            this.text = text;
        }

        boolean done() {
            return position >= text.length();
        }

        char peek() {
            return text.charAt(position);
        }
    }

    private static final class PatternException extends Exception {
        PatternException(String message) {
            super(message);
        }
    }

    /** Alternatives separated by {@code |}, up to {@code close} (or the end, for {@code 0}). */
    private List<List<CommandPattern.Part>> alternatives(Line line, Reader reader, Set<String> names, char close)
            throws PatternException {
        List<List<CommandPattern.Part>> alternatives = new ArrayList<>();
        List<CommandPattern.Part> parts = new ArrayList<>();
        while (true) {
            if (reader.done()) {
                if (close != 0) {
                    throw new PatternException("Missing '" + close + "' in the command's pattern");
                }
                alternatives.add(parts);
                return alternatives;
            }
            char c = reader.peek();
            if (Character.isWhitespace(c)) {
                reader.position++;
            } else if (c == close) {
                reader.position++;
                alternatives.add(parts);
                return alternatives;
            } else if (c == '|') {
                reader.position++;
                alternatives.add(parts);
                parts = new ArrayList<>();
            } else if (c == '[' || c == '(') {
                reader.position++;
                List<List<CommandPattern.Part>> inner = alternatives(line, reader, names, c == '[' ? ']' : ')');
                if (inner.size() > 1) {
                    parts.add(new CommandPattern.Choice(inner, c == '['));
                } else if (c == '[') {
                    parts.add(new CommandPattern.Optional(inner.getFirst()));
                } else {
                    parts.addAll(inner.getFirst());
                }
            } else if (c == ']' || c == ')') {
                throw new PatternException("Unexpected '" + c + "' in the command's pattern");
            } else if (c == '<') {
                parts.add(argument(line, reader, names));
            } else {
                int start = reader.position;
                while (!reader.done() && !Character.isWhitespace(reader.peek()) && "[]()|<".indexOf(reader.peek()) < 0) {
                    reader.position++;
                }
                parts.add(new CommandPattern.Word(reader.text.substring(start, reader.position)));
            }
        }
    }

    /** {@code <[name:] type [range] [= default]>}. */
    private CommandPattern.Argument argument(Line line, Reader reader, Set<String> names) throws PatternException {
        int start = ++reader.position;
        int equals = -1;
        boolean quoted = false;
        boolean expression = false;
        while (true) {
            if (reader.done()) {
                throw new PatternException("Missing '>' in the command's pattern");
            }
            char c = reader.peek();
            if (c == '"') {
                quoted = !quoted;
            } else if (c == '%' && !quoted) {
                expression = !expression;
            } else if (!quoted && !expression) {
                if (c == '=' && equals < 0) {
                    equals = reader.position;
                } else if (c == '>') {
                    break;
                }
            }
            reader.position++;
        }
        String inner = reader.text.substring(start, equals < 0 ? reader.position : equals);
        String defaultValue = equals < 0 ? null : reader.text.substring(equals + 1, reader.position).strip();
        reader.position++;

        String name = null;
        Matcher named = ARGUMENT_NAME.matcher(inner);
        if (named.matches()) {
            name = named.group(1);
            inner = named.group(2);
            if (!names.add(name.toLowerCase(Locale.ROOT))) {
                throw new PatternException("The argument name '" + name + "' was already used.");
            }
        }
        String typeText = inner.strip();
        TypeRef type = null;
        String rest = "";
        for (String candidate = typeText; !candidate.isEmpty(); ) {
            type = registry.resolveType(candidate);
            if (type != null) {
                rest = typeText.substring(candidate.length()).strip();
                break;
            }
            int space = candidate.lastIndexOf(' ');
            if (space < 0) {
                break;
            }
            candidate = candidate.substring(0, space).strip();
        }
        if (type == null) {
            throw new PatternException("'" + typeText + "' is not a known type.");
        }

        Double min = null;
        Double max = null;
        if (!rest.isEmpty()) {
            String id = type.type().id();
            if (!id.equals("number") && !id.equals("integer") && !id.equals("long")) {
                throw new PatternException(type.type().name().toLowerCase(Locale.ROOT) + " arguments do not support minimum or maximum values.");
            }
            if (type.plural()) {
                throw new PatternException("Only single number arguments support minimum or maximum values.");
            }
            String range = rest.toLowerCase(Locale.ROOT);
            Matcher between = BETWEEN.matcher(range);
            if (between.matches()) {
                min = number(between.group(1), typeText);
                max = number(between.group(2), typeText);
            } else {
                for (String clause : range.split("\\s+and\\s+")) {
                    Matcher m;
                    if ((m = MIN.matcher(clause)).matches()) {
                        min = number(m.group(1), typeText);
                    } else if ((m = MAX.matcher(clause)).matches()) {
                        max = number(m.group(1), typeText);
                    } else {
                        throw new PatternException("'" + typeText + "' is not a known type.");
                    }
                }
            }
            if (min != null && max != null && min > max) {
                throw new PatternException("The minimum value must be less than or equal to the maximum value.");
            }
        }

        Expr defaultExpression = null;
        String defaultText = null;
        if (defaultValue != null) {
            if (defaultValue.length() >= 2 && defaultValue.startsWith("%") && defaultValue.endsWith("%")) {
                defaultText = defaultValue.substring(1, defaultValue.length() - 1).strip();
                defaultExpression = statements.expression(line, defaultText, type.type().id(), type.plural());
                if (defaultExpression == null) {
                    throw new PatternException("Can't understand the default value '" + defaultValue + "'");
                }
            } else if (defaultValue.length() >= 2 && defaultValue.startsWith("\"") && defaultValue.endsWith("\"")) {
                defaultText = defaultValue.substring(1, defaultValue.length() - 1).replace("\"\"", "\"");
            } else {
                defaultText = defaultValue;
            }
        }
        return new CommandPattern.Argument(name, type.type().id(), type.plural(), defaultExpression, defaultText, min, max);
    }

    private static Double number(String text, String argument) throws PatternException {
        try {
            return Double.parseDouble(text);
        } catch (NumberFormatException e) {
            throw new PatternException("'" + text + "' is not a number, in <" + argument + ">");
        }
    }

    private void report(Line line, String message) {
        diagnostics.error(line.number(), line.column(), line.text().length(), message);
    }
}
