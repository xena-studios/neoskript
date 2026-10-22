/*
 * Copyright (c) 2026 Xena Studios
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package co.xenastudios.neoskript.frontend.structure;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

import co.xenastudios.neoskript.frontend.source.Diagnostic;
import co.xenastudios.neoskript.frontend.source.SourceFile;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class ScriptParserTest {

    private static ScriptParser.Result parse(String text) {
        return ScriptParser.parse(new SourceFile("test.sk", text));
    }

    private static List<String> errors(ScriptParser.Result result) {
        return result.diagnostics().stream().map(d -> d.line() + ": " + d.message()).toList();
    }

    @Test
    void buildsNestedSections() {
        ScriptParser.Result result = parse("""
                command /home [<text>]:
                \taliases: h
                \ttrigger:
                \t\tif arg-1 is set:
                \t\t\tsend "a"
                \t\telse:
                \t\t\tsend "b"
                \t\tsend "done"
                """);
        assertEquals(List.of(), result.diagnostics());
        Structure command = result.structures().getFirst();
        assertEquals(Structure.Kind.COMMAND, command.kind());
        List<Node> entries = command.section().children();
        assertEquals("aliases: h", entries.get(0).line().text());
        Node.Section trigger = assertInstanceOf(Node.Section.class, entries.get(1));
        assertEquals("trigger", trigger.line().key());
        assertEquals(3, trigger.children().size());
        Node.Section ifSection = assertInstanceOf(Node.Section.class, trigger.children().get(0));
        assertEquals("if arg-1 is set", ifSection.line().key());
        assertEquals("send \"a\"", ifSection.children().getFirst().line().text());
        assertEquals(8, trigger.children().get(2).line().number());
    }

    @Test
    void acceptsSpacesAndSkipsCommentsAndBlankLines() {
        ScriptParser.Result result = parse("""
                # a comment

                on join:   # trailing
                    # indented comment
                    send "hi"

                    send "there"
                ###
                on broken
                  stuff:
                ###
                """);
        assertEquals(List.of(), result.diagnostics());
        assertEquals(1, result.structures().size());
        Node.Section join = result.structures().getFirst().section();
        assertEquals("on join", join.line().key());
        assertEquals(List.of(5, 7), join.children().stream().map(n -> n.line().number()).toList());
    }

    @Test
    void sectionsCanBeEmpty() {
        ScriptParser.Result result = parse("on join:\non quit:\n\tsend \"bye\"\n");
        assertEquals(List.of(), result.diagnostics());
        assertEquals(List.of(), result.structures().get(0).section().children());
        assertEquals(1, result.structures().get(1).section().children().size());
    }

    @Test
    void classifiesStructures() {
        ScriptParser.Result result = parse("""
                options:
                \tx: 1
                variables:
                \t{a} = 1
                aliases:
                \tthing = stone
                function f(n: number) :: number:
                \treturn 1
                local function g():
                \tstop
                command /x:
                \ttrigger:
                \t\tstop
                every 5 minutes:
                \tstop
                on join:
                \tstop
                using local variable type hints
                """);
        assertEquals(List.of(Structure.Kind.OPTIONS, Structure.Kind.VARIABLES, Structure.Kind.ALIASES,
                Structure.Kind.FUNCTION, Structure.Kind.FUNCTION, Structure.Kind.COMMAND, Structure.Kind.EVENT,
                Structure.Kind.EVENT, Structure.Kind.USING), result.structures().stream().map(Structure::kind).toList());
    }

    @Test
    void reportsCodeOutsideStructures() {
        ScriptParser.Result result = parse("send \"hi\"\non join:\n\tstop\n");
        assertEquals(List.of("1: Can't understand this line: code has to go inside an event, command, function"
                + " or other structure"), errors(result));
        assertEquals(1, result.structures().size());
    }

    @Test
    void reportsUnexpectedIndentationOnce() {
        ScriptParser.Result result = parse("""
                on join:
                \tsend "a"
                \t\tsend "b"
                \t\tsend "c"
                \tsend "d"
                """);
        assertEquals(List.of("3: Unexpected indentation: only the lines under a section (a line ending in ':')"
                + " can be indented further"), errors(result));
        assertEquals(2, result.structures().getFirst().section().children().size());
    }

    @Test
    void reportsIndentationThatMatchesNoLevel() {
        ScriptParser.Result result = parse("""
                on join:
                \t\tif true:
                \t\t\tsend "a"
                \tsend "b"
                """);
        assertEquals(List.of("4: Indentation error: expected 2 tabs, but found 1 tab"), errors(result));
    }

    @Test
    void reportsMixedTabsAndSpaces() {
        ScriptParser.Result result = parse("on join:\n    if true:\n\t\tsend \"a\"\n    send \"b\"\n");
        assertEquals(1, result.diagnostics().size());
        Diagnostic error = result.diagnostics().getFirst();
        assertEquals(3, error.line());
        assertTrue(error.message().startsWith("Indentation error"), error.message());
        assertEquals(2, result.structures().getFirst().section().children().size());
    }

    @Test
    void substitutesOptionsIncludingNestedOnes() {
        ScriptParser.Result result = parse("""
                on join:
                \tsend "{@prefix}hi {@colors.main}x{@colors.main}" # options can be used before they're defined
                options:
                \tprefix: &6[Server]&7 #
                \tcolors:
                \t\tmain: &b
                """);
        assertEquals(List.of(), result.diagnostics());
        assertEquals(Map.of("prefix", "&6[Server]&7", "colors.main", "&b"), result.options());
        Node send = result.structures().getFirst().section().children().getFirst();
        assertEquals("send \"&6[Server]&7hi &bx&b\"", send.line().text());
    }

    @Test
    void reportsUndefinedOptions() {
        ScriptParser.Result result = parse("on join:\n\tsend \"{@missing}\"\n");
        assertEquals(List.of("2: Undefined option {@missing}"), errors(result));
        assertEquals(7, result.diagnostics().getFirst().column());
    }

    @Test
    void handlesWindowsLineEndingsAndByteOrderMark() {
        ScriptParser.Result result = parse("﻿on join:\r\n\tsend \"a\"\r\n");
        assertEquals(List.of(), result.diagnostics());
        assertEquals("on join", result.structures().getFirst().section().line().key());
    }
}
