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

    @Test
    void scriptsLoadedTogetherAreInterleavedByStage() {
        // Three scripts loaded together on Skript 2.17.0: the alias problems (headers) first, then every script's
        // functions, then every script's events, then every script's commands (issue #69).
        SourceFile a = new SourceFile("order-a.sk", "on load:\n\tfrob a1\nfunction order_a():\n\tfrob a2\n"
                + "command /ordera:\n\ttrigger:\n\t\tfrob a3\n");
        SourceFile b = new SourceFile("order-b.sk", "command /orderb:\n\ttrigger:\n\t\tfrob b3\non load:\n\tfrob b1\n"
                + "function order_b():\n\tfrob b2\n");
        SourceFile c = new SourceFile("order-c.sk", "on load:\n\tfrob c1\naliases:\n\tprobe thing = stone\n"
                + "\tthis line is bad\n\tprobe other = notarealitemzzz\non chat:\n\tfrob c2\n");
        List<Diagnostic> found = List.of(
                new Diagnostic(Severity.ERROR, "order-a.sk", 2, 0, 0, "frob a1"),
                new Diagnostic(Severity.ERROR, "order-a.sk", 4, 0, 0, "frob a2"),
                new Diagnostic(Severity.ERROR, "order-a.sk", 7, 0, 0, "frob a3"),
                new Diagnostic(Severity.ERROR, "order-b.sk", 3, 0, 0, "frob b3"),
                new Diagnostic(Severity.ERROR, "order-b.sk", 5, 0, 0, "frob b1"),
                new Diagnostic(Severity.ERROR, "order-b.sk", 7, 0, 0, "frob b2"),
                new Diagnostic(Severity.ERROR, "order-c.sk", 2, 0, 0, "frob c1"),
                new Diagnostic(Severity.ERROR, "order-c.sk", 5, 0, 0, "'this line is bad' is not an entry (like 'name = value')"),
                new Diagnostic(Severity.ERROR, "order-c.sk", 6, 0, 0, "Minecraft id notarealitemzzz is not valid"),
                new Diagnostic(Severity.ERROR, "order-c.sk", 8, 0, 0, "frob c2"));
        assertEquals(List.of("order-c.sk:5", "order-c.sk:6", "order-a.sk:4", "order-b.sk:7", "order-a.sk:2",
                "order-b.sk:5", "order-c.sk:2", "order-c.sk:8", "order-a.sk:7", "order-b.sk:3"),
                MessageOrder.sortAll(found, List.of(a, b, c), d -> false).stream()
                        .map(d -> d.file() + ":" + d.line()).toList());
    }

    @Test
    void layoutProblemsOfEveryScriptComeFirst() {
        SourceFile a = new SourceFile("a.sk", "on load:\n\tfrob\n");
        SourceFile b = new SourceFile("b.sk", "on load:\n\tfrob\nif x:\n");
        List<Diagnostic> found = List.of(new Diagnostic(Severity.ERROR, "a.sk", 2, 0, 0, "frob"),
                new Diagnostic(Severity.WARNING, "b.sk", 3, 0, 0, "Empty configuration section!"));
        assertEquals(List.of("b.sk:3", "a.sk:2"), MessageOrder.sortAll(found, List.of(a, b),
                d -> d.message().startsWith("Empty")).stream().map(d -> d.file() + ":" + d.line()).toList());
    }

    @Test
    void aReportGroupsTheScriptsInTheirLoadOrder() {
        // '/sk reload' lists each script's problems together: by load order, not by which has the first problem.
        SourceFile a = new SourceFile("a.sk", "on load:\n\tfrob\n");
        SourceFile b = new SourceFile("b.sk", "on load:\n\tfrob\n");
        List<Diagnostic> found = List.of(new Diagnostic(Severity.ERROR, "b.sk", 2, 0, 0, "b1"),
                new Diagnostic(Severity.ERROR, "a.sk", 2, 0, 0, "a1"),
                new Diagnostic(Severity.ERROR, "b.sk", 3, 0, 0, "b2"),
                new Diagnostic(Severity.ERROR, "c.sk", 1, 0, 0, "c1"),
                new Diagnostic(Severity.ERROR, "a.sk", 1, 0, 0, "a2"));
        assertEquals(List.of("a1", "a2", "b1", "b2", "c1"), MessageOrder.byScript(found, List.of(a, b)).stream()
                .map(Diagnostic::message).toList());
    }
}
