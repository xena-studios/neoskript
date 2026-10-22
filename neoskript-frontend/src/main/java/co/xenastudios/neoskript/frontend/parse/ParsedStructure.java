/*
 * Copyright (c) 2026 Xena Studios
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package co.xenastudios.neoskript.frontend.parse;

import co.xenastudios.neoskript.frontend.registry.FunctionInfo;
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

    record Command(Structure structure, String name, List<CommandArgument> arguments, Map<String, String> entries,
            List<Statement> trigger) implements ParsedStructure {

        public Command {
            arguments = List.copyOf(arguments);
            entries = Map.copyOf(entries);
            trigger = List.copyOf(trigger);
        }
    }

    /** {@code <type>} or {@code [<type=default>]} in a command header. */
    record CommandArgument(String type, boolean plural, boolean optional, String defaultValue) {}

    record Function(Structure structure, FunctionInfo signature, boolean local, List<Statement> body)
            implements ParsedStructure {

        public Function {
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

    /** {@code options:}, {@code variables:}, {@code aliases:} and {@code using}: data, no code. */
    record Data(Structure structure) implements ParsedStructure {}
}
