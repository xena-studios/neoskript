/*
 * Copyright (c) 2026 Xena Studios
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package co.xenastudios.neoskript.frontend.parse;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

import co.xenastudios.neoskript.frontend.source.SourceFile;
import co.xenastudios.neoskript.frontend.stub.SkriptRegistry;
import java.util.List;
import org.junit.jupiter.api.Test;

/** Command structures: patterns, subcommands, entries, and Skript's errors for them. */
class CommandParserTest {

    private static final ScriptLoader LOADER = new ScriptLoader(SkriptRegistry.get());
    private static final String MISSING_TRIGGER = "You must have a 'trigger' entry if there are no subcommands!";

    private static ParsedScript load(String text) {
        return LOADER.load(List.of(new SourceFile("test.sk", text))).getFirst();
    }

    private static List<String> errors(String text) {
        return load(text).diagnostics().stream().map(d -> d.message()).toList();
    }

    private static ParsedStructure.Command command(String text) {
        ParsedScript script = load(text);
        assertEquals(List.of(), script.diagnostics(), text);
        return assertInstanceOf(ParsedStructure.Command.class, script.structures().getFirst());
    }

    @Test
    void patternsHaveWordsArgumentsOptionalPartsAndChoices() {
        ParsedStructure.Command command = command("command /eco (give|take) <x: offlineplayer> [<number = 5>] [c|d]:\n"
                + "\ttrigger:\n\t\tstop\n");
        assertEquals("eco", command.name());
        List<CommandPattern.Part> pattern = command.root().pattern();
        assertEquals(2, assertInstanceOf(CommandPattern.Choice.class, pattern.get(0)).alternatives().size());
        CommandPattern.Argument player = assertInstanceOf(CommandPattern.Argument.class, pattern.get(1));
        assertEquals("x", player.name());
        assertEquals("offlineplayer", player.type());
        CommandPattern.Optional optional = assertInstanceOf(CommandPattern.Optional.class, pattern.get(2));
        assertEquals("5", assertInstanceOf(CommandPattern.Argument.class, optional.parts().getFirst()).defaultText());
        assertEquals(true, assertInstanceOf(CommandPattern.Choice.class, pattern.get(3)).optional());
        assertEquals("(give|take) <x> [<number>] [c|d]", CommandPattern.usage(pattern));
    }

    @Test
    void defaultsCanBeExpressionsOrQuotedText() {
        CommandPattern.Optional optional = assertInstanceOf(CommandPattern.Optional.class,
                command("command /a [<player = %player%>]:\n\ttrigger:\n\t\tstop\n").root().pattern().getFirst());
        assertNotNull(assertInstanceOf(CommandPattern.Argument.class, optional.parts().getFirst()).defaultValue());
        List<CommandPattern.Part> quoted =
                command("command b [\" <text=\"success 2\"> \"]:\n\ttrigger:\n\t\tstop\n").root().pattern();
        CommandPattern.Optional inner = assertInstanceOf(CommandPattern.Optional.class, quoted.getFirst());
        assertEquals("success 2", assertInstanceOf(CommandPattern.Argument.class, inner.parts().get(1)).defaultText());
    }

    @Test
    void rangesOnlyForSingleNumbers() {
        CommandPattern.Argument ranged = assertInstanceOf(CommandPattern.Argument.class,
                command("command /r <x: number above 5 and below 10>:\n\ttrigger:\n\t\tstop\n").root().pattern().getFirst());
        assertEquals(5.0, ranged.min());
        assertEquals(10.0, ranged.max());
        assertEquals(List.of("text arguments do not support minimum or maximum values."),
                errors("command /r <x: text between \"hello\" and \"world\">:\n\ttrigger:\n\t\tstop\n"));
        assertEquals(List.of("Only single number arguments support minimum or maximum values."),
                errors("command /r <x: numbers above 5>:\n\ttrigger:\n\t\tstop\n"));
        assertEquals(List.of("The minimum value must be less than or equal to the maximum value."),
                errors("command /r <x: number greater than 10 and less than 5>:\n\ttrigger:\n\t\tstop\n"));
        assertEquals(List.of("The argument name 'x' was already used."),
                errors("command /r <x:number> <x:number>:\n\ttrigger:\n\t\tstop\n"));
    }

    @Test
    void subcommandsAndGroups() {
        ParsedStructure.Command command = command("command /s <number>:\n\tsubcommand <number>:\n\t\ttrigger:\n\t\t\tstop\n"
                + "\tsubcommands:\n\t\texecutable by: players\n\t\tsubcommand help:\n\t\t\ttrigger:\n\t\t\t\tstop\n"
                + "\ttrigger:\n\t\tstop\n");
        assertEquals(2, command.root().subcommands().size());
        assertEquals(true, command.root().subcommands().get(1).group());
        assertEquals("players", command.root().subcommands().get(1).entries().get("executable by"));
        assertEquals(3, command.statements().size());
    }

    @Test
    void skriptsSubcommandErrors() {
        assertEquals(List.of(MISSING_TRIGGER), errors("command /e:\n"));
        assertEquals(List.of(MISSING_TRIGGER, MISSING_TRIGGER), errors("command /e:\n\tsubcommand test:\n"));
        assertEquals(List.of("It is not possible to restrict execution to the console as the parent command is only "
                + "executable by players."), errors("command /e:\n\texecutable by: players\n\ttrigger:\n\t\tstop\n"
                + "\tsubcommand test:\n\t\texecutable by: console\n\t\ttrigger:\n\t\t\tstop\n"));
        assertEquals(List.of(), errors("command /e:\n\texecutable by: players\n\ttrigger:\n\t\tstop\n"
                + "\tsubcommand test:\n\t\texecutable by: operators\n\t\ttrigger:\n\t\t\tstop\n"));
        assertEquals(List.of("It is not possible to have a 'trigger' entry if an optional subcommand is present!"),
                errors("command /e:\n\tsubcommand [help]:\n\t\ttrigger:\n\t\t\tstop\n\ttrigger:\n\t\tstop\n"));
        assertEquals(List.of("It is not possible to have more than one optional subcommand at the same level."),
                errors("command /e:\n\tsubcommand [help]:\n\t\ttrigger:\n\t\t\tstop\n\tsubcommands:\n"
                        + "\t\tsubcommand help2:\n\t\t\ttrigger:\n\t\t\t\tstop\n\t\tsubcommands:\n"
                        + "\t\t\tsubcommand [help3]:\n\t\t\t\ttrigger:\n\t\t\t\t\tstop\n"));
    }

    @Test
    void messagesAreTextWithExpressions() {
        ParsedStructure.Command command = command("command /c:\n\tcooldown: 2 seconds\n"
                + "\tcooldown message: Wait %remaining time%, \"please\"\n\ttrigger:\n\t\tstop\n");
        assertEquals("2 seconds", command.root().entries().get("cooldown"));
        assertInstanceOf(Expr.Text.class, command.root().messages().get("cooldown message"));
        assertNull(command.root().messages().get("permission message"));
    }
}
