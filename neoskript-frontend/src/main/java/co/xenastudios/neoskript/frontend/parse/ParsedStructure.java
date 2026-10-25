/*
 * Copyright (c) 2026 Xena Studios
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package co.xenastudios.neoskript.frontend.parse;

import co.xenastudios.neoskript.frontend.registry.FunctionInfo;
import co.xenastudios.neoskript.frontend.structure.Line;
import co.xenastudios.neoskript.frontend.structure.Structure;
import java.util.List;
import java.util.Map;

/** A parsed top-level structure. */
public sealed interface ParsedStructure {

    Structure structure();

    record Event(Structure structure, ParsedSyntax event, List<Statement> body) implements ParsedStructure {

        public Event {
            body = List.copyOf(body);
        }
    }

    /** A {@code command /name <arguments>:} structure. Its subcommands are in {@code root}. */
    record Command(Structure structure, String name, CommandNode root) implements ParsedStructure {

        /** The code of every trigger: the command's and its subcommands'. */
        public List<Statement> statements() {
            List<Statement> statements = new java.util.ArrayList<>();
            root.collect(statements);
            return statements;
        }
    }

    /**
     * A command, a {@code subcommand <pattern>:} or a {@code subcommands:} group.
     *
     * @param pattern what follows its parent's pattern; empty for a group
     * @param entries entries such as {@code permission}, by lower case name
     * @param messages entries that are text with expressions in them, like {@code cooldown message}
     * @param trigger its code, or {@code null} if it has no {@code trigger:}
     * @param subcommands its subcommands and groups, in order
     * @param suggestions the code of its {@code suggestions:} entry, which works out what tab completion
     *     suggests for its arguments, or {@code null}
     * @param suggestionsLine that entry's line, or {@code null}
     */
    record CommandNode(Line line, boolean group, List<CommandPattern.Part> pattern, Map<String, String> entries,
            Map<String, Expr> messages, List<Statement> trigger, List<CommandNode> subcommands,
            List<Statement> suggestions, Line suggestionsLine) {

        public CommandNode {
            pattern = List.copyOf(pattern);
            entries = java.util.Collections.unmodifiableMap(new java.util.LinkedHashMap<>(entries));
            messages = Map.copyOf(messages);
            trigger = trigger == null ? null : List.copyOf(trigger);
            subcommands = List.copyOf(subcommands);
            suggestions = suggestions == null ? null : List.copyOf(suggestions);
        }

        private void collect(List<Statement> statements) {
            if (trigger != null) {
                statements.addAll(trigger);
            }
            if (suggestions != null) {
                statements.addAll(suggestions);
            }
            subcommands.forEach(s -> s.collect(statements));
        }
    }

    /**
     * A function.
     *
     * @param defaults each parameter's default value, by parameter; {@code null} where there's none. Their
     *     positions are in the default value's own text, {@code defaultTexts}.
     */
    record Function(Structure structure, FunctionInfo signature, boolean local, List<Expr> defaults,
            List<String> defaultTexts, List<Statement> body) implements ParsedStructure {

        public Function {
            defaults = java.util.Collections.unmodifiableList(new java.util.ArrayList<>(defaults));
            defaultTexts = java.util.Collections.unmodifiableList(new java.util.ArrayList<>(defaultTexts));
            body = List.copyOf(body);
        }
    }

    /** A registered structure such as Skript's {@code test "name":}, with its code if it has any. */
    record Custom(Structure structure, ParsedSyntax syntax, List<Statement> body) implements ParsedStructure {

        public Custom {
            body = List.copyOf(body);
        }
    }

    /**
     * Skript's test-only top-level {@code parse:} block: structures parsed with their problems captured,
     * for the test to check.
     */
    record Parse(Structure structure, List<ParsedStructure> code, List<co.xenastudios.neoskript.frontend.source.Diagnostic> captured)
            implements ParsedStructure {

        public Parse {
            code = List.copyOf(code);
            captured = List.copyOf(captured);
        }
    }

    /**
     * Skript's {@code auto reload} structure: the script reloads itself when its file changes.
     *
     * @param recipients the names or UUIDs of the players to tell about reloads, as written; empty to tell
     *     those with {@code permission} instead
     * @param permission what players need to be told about reloads, when there are no recipients
     */
    record AutoReload(Structure structure, List<String> recipients, String permission) implements ParsedStructure {

        public AutoReload {
            recipients = List.copyOf(recipients);
        }
    }

    /** {@code options:}, {@code variables:}, {@code aliases:} and {@code using}: data, no code. */
    record Data(Structure structure) implements ParsedStructure {}
}
