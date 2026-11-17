/*
 * Copyright (c) 2026 Xena Studios
 * SPDX-License-Identifier: GPL-3.0-only
 */
package co.xenastudios.neoskript.frontend.source;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;
import org.junit.jupiter.api.Test;

/** The order Skript 2.17.0 gives a script's problems in, from a probe of a real server (issue #37). */
class MessageOrderTest {

    private static Diagnostic error(int line, String message) {
        return new Diagnostic(Severity.ERROR, "order.sk", line, 0, 0, message);
    }

    private static Diagnostic warning(int line, String message) {
        return new Diagnostic(Severity.WARNING, "order.sk", line, 0, 0, message);
    }

    @Test
    void asSkriptLoadsTheScript() {
        SourceFile file = new SourceFile("order.sk", """
                options:
                \tgood: 1
                \tbad option line

                on load:
                \tfrob one

                on frobnicate the wibble:
                \tbroadcast "x"

                function ordera(n: number) :: number:
                \tfrob two
                \treturn 1

                command /orderc:
                \tpermission message: hi
                \tcooldown: frob seconds
                \tcooldown message: "%frob nonsense%"
                \tcooldown bypass: x.y
                \ttrigger:
                \t\tfrob three
                \tusage: "%frob usage%"

                variables:
                \t{order::a} = frob value

                function orderb(n: numberz):
                \tbroadcast "x"

                using frobfeature

                every frob seconds:
                \tbroadcast "x"

                on chat:
                \tfrob four
                \tif 1 is 1:
                \t\tbroadcast "x"

                function orderc() :: number:
                \tfrob five
                \treturn 1
                """);
        // In the order of their lines, as found.
        List<Diagnostic> found = List.of(error(3, "'bad option line' is not an entry (like 'name : value')"),
                error(6, "Can't understand this condition/effect: frob one"),
                error(8, "Can't understand this structure: on frobnicate the wibble"),
                error(12, "Can't understand this condition/effect: frob two"),
                warning(15, "[Deprecated] The 'permission message' entry has been deprecated"),
                warning(15, "There is a cooldown bypass set, but not a cooldown"),
                error(18, "Can't understand this expression: frob nonsense"),
                error(21, "Can't understand this condition/effect: frob three"),
                error(22, "Can't understand this expression: frob usage"),
                error(25, "Can't understand the value 'frob value'"),
                error(27, "Cannot recognise the type 'numberz'"),
                warning(30, "The experimental feature 'frobfeature' was not found."),
                error(32, "Can't understand this structure: every frob seconds"),
                error(36, "Can't understand this condition/effect: frob four"),
                warning(37, "The condition '1 is equal to 1' will always be true."),
                error(41, "Can't understand this condition/effect: frob five"));
        List<Diagnostic> sorted = MessageOrder.sort(found, file, d -> false);
        // As Skript 2.17.0 gave them: headers, function headers, functions, events, then the command entry by entry.
        assertEquals(List.of(3, 8, 25, 30, 32, 27, 12, 41, 6, 36, 37, 18, 15, 15, 21, 22),
                sorted.stream().map(Diagnostic::line).toList());
        assertEquals("There is a cooldown bypass set, but not a cooldown", sorted.get(12).message());
    }

    @Test
    void layoutProblemsComeFirst() {
        SourceFile file = new SourceFile("order.sk", "on load:\n\tfrob\n\nif x:\n");
        List<Diagnostic> sorted = MessageOrder.sort(List.of(error(2, "frob"), warning(4, "Empty configuration section!")),
                file, d -> d.message().startsWith("Empty"));
        assertEquals(List.of(4, 2), sorted.stream().map(Diagnostic::line).toList());
    }

    @Test
    void aliasesAreReadWithTheHeaders() {
        // Skript 2.17.0 gave the alias problems first, then the events'.
        SourceFile file = new SourceFile("order.sk", "on load:\n\tfrob c1\naliases:\n\tprobe thing = stone\n"
                + "\tthis line is bad\n\tprobe other = notarealitemzzz\non chat:\n\tfrob c2\n");
        List<Diagnostic> sorted = MessageOrder.sort(List.of(error(2, "frob c1"), error(5, "not an entry"),
                error(6, "Minecraft id notarealitemzzz is not valid"), error(8, "frob c2")), file, d -> false);
        assertEquals(List.of(5, 6, 2, 8), sorted.stream().map(Diagnostic::line).toList());
    }
}
