/*
 * Copyright (c) 2026 Xena Studios
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package co.xenastudios.neoskript.frontend.parse;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import co.xenastudios.neoskript.frontend.source.Diagnostic;
import co.xenastudios.neoskript.frontend.source.SourceFile;
import co.xenastudios.neoskript.frontend.stub.SkriptRegistry;
import java.util.List;
import org.junit.jupiter.api.Test;

/** Parsing against the stub registry built from the reference Skript's syntax. */
class ScriptLoaderTest {

    private static final ScriptLoader LOADER = new ScriptLoader(SkriptRegistry.get());

    private static ParsedScript load(String text) {
        return LOADER.load(List.of(new SourceFile("test.sk", text))).getFirst();
    }

    private static List<String> errors(ParsedScript script) {
        return script.diagnostics().stream().map(d -> d.line() + ": " + d.message()).toList();
    }

    private static void assertParses(String text) {
        ParsedScript script = load(text);
        assertEquals(List.of(), errors(script), text);
    }

    @Test
    void parsesATypicalScript() {
        ParsedScript script = load("""
                options:
                \tprefix: &6[Shop]&7
                on join:
                \tset {_id} to "%uuid of player%"
                \tif {balance::%{_id}%} is not set:
                \t\tset {balance::%{_id}%} to 100
                \tsend "{@prefix} Welcome, %player%!" to player
                command /balance [<offlineplayer>]:
                \taliases: bal
                \ttrigger:
                \t\tsend "%{balance::%uuid of player%} ? 0%" to sender
                function double(n: number) :: number:
                \treturn {_n} * 2
                """);
        assertEquals(List.of(), errors(script));
        assertEquals(4, script.structures().size());
        ParsedStructure.Event join = assertInstanceOf(ParsedStructure.Event.class, script.structures().get(1));
        assertInstanceOf(Statement.Section.class, join.body().get(1));
        ParsedStructure.Command command = assertInstanceOf(ParsedStructure.Command.class, script.structures().get(2));
        assertEquals("balance", command.name());
        assertEquals("offlineplayer", command.arguments().getFirst().type());
        assertEquals("bal", command.entries().get("aliases"));
        ParsedStructure.Function function = assertInstanceOf(ParsedStructure.Function.class, script.structures().get(3));
        assertEquals("number", function.signature().returnType());
    }

    @Test
    void reportsLinesItCantUnderstand() {
        ParsedScript script = load("on join:\n\tfrobnicate the player\n\tsend \"ok\" to player\non flying pigs:\n\tstop\n");
        assertEquals(List.of("2: Can't understand this condition/effect: frobnicate the player",
                "4: Can't understand this event: 'on flying pigs'"), errors(script));
    }

    @Test
    void functionsCanBeCalledFromOtherScripts() {
        List<ParsedScript> scripts = LOADER.load(List.of(
                new SourceFile("a.sk", "on load:\n\tsend \"%greet(\"Steve\")%\" to console\n\tgreet_all()\n"),
                new SourceFile("b.sk", "function greet(name: text) :: text:\n\treturn \"Hi %{_name}%\"\n"
                        + "function greet_all():\n\tbroadcast \"hi\"\n")));
        assertEquals(List.of(), scripts.get(0).diagnostics());
        assertEquals(List.of(), scripts.get(1).diagnostics());
        assertNull(LOADER.parseExpression("greet(\"Steve\")", "text"), "no script defines greet here");
    }

    @Test
    void functionOverloadsAndNamedArguments() {
        assertParses("""
                function f(x: number) :: number:
                \treturn 1
                function f(x: text, y: number = 2) :: number:
                \treturn 2
                on load:
                \tset {_a} to f(1)
                \tset {_b} to f("a")
                \tset {_c} to f("a", y: 5)
                \tset {_d} to f(y: 5, x: "a")
                """);
        ParsedScript script =
                load("function f(x: number, y: number) :: number:\n\treturn 1\non load:\n\tset {_a} to f(1, 2, 3)\n");
        assertEquals(1, script.diagnostics().size(), "too many arguments");
    }

    @Test
    void literals() {
        assertNotNull(LOADER.parseExpression("5 minutes and 30 seconds", "timespan"));
        assertNotNull(LOADER.parseExpression("50%", "number"));
        assertNotNull(LOADER.parseExpression("-1_000.5", "number"));
        assertNotNull(LOADER.parseExpression("creative", "gamemode"));
        assertNull(LOADER.parseExpression("flying", "gamemode"));
        assertNotNull(LOADER.parseExpression("black (wolf variant)", "objects"));
        assertNotNull(LOADER.parseExpression("campfire[lit=false;waterlogged=true]", "block data"));
        assertNull(LOADER.parseExpression("\"5\"", "number"), "text isn't a number");
    }

    @Test
    void listsWhoseItemsContainAnd() {
        Expr list = LOADER.parseExpression("characters between \"a\" and \"f\", integers from 1 to 5", "objects");
        Expr.ListOf items = assertInstanceOf(Expr.ListOf.class, list);
        assertEquals(2, items.items().size());
    }

    @Test
    void resolvedParsesWinOverUnresolvedWords() {
        Expr expr = LOADER.parseExpression("a loot context at test-location", "objects");
        Expr.Syntax syntax = assertInstanceOf(Expr.Syntax.class, expr);
        assertEquals("ExprSecCreateLootContext", syntax.syntax().syntax().id());
    }

    @Test
    void sectionsAndConditionals() {
        assertParses("""
                on load:
                \tif 1 is 2:
                \t\tstop
                \telse if 2 is 3:
                \t\tstop
                \telse:
                \t\tbroadcast "x"
                \tif any:
                \t\t1 is 2
                \t\t2 is 2
                \tthen:
                \t\tbroadcast "y"
                \tloop 3 times:
                \t\tbroadcast "%loop-number%"
                \tset {_bar} to a new boss bar:
                \t\tset title of the boss bar to "hi"
                """);
        assertEquals(List.of("2: 'else' has to be placed just after another 'if' or 'else if' section"),
                errors(load("on load:\n\telse:\n\t\tstop\n")));
    }

    @Test
    void parseBlocksCaptureTheirProblems() {
        ParsedScript script = load("test \"x\":\n\tparse:\n\t\tnot real syntax\n\tassert last parse logs is set\n");
        assertEquals(List.of(), errors(script));
        ParsedStructure.Custom test = assertInstanceOf(ParsedStructure.Custom.class, script.structures().getFirst());
        Statement.Parse parse = assertInstanceOf(Statement.Parse.class, test.body().getFirst());
        List<Diagnostic> captured = parse.captured();
        assertEquals(1, captured.size());
        assertTrue(captured.getFirst().message().contains("not real syntax"));
    }

    @Test
    void commandSubcommandsAndNamedArguments() {
        assertParses("""
                command /shop <x: number above 0 and below 10>:
                \tsubcommand buy <text>:
                \t\ttrigger:
                \t\t\tsend "buying" to sender
                \ttrigger:
                \t\tsend "%{_x}%" to sender
                """);
    }
}
