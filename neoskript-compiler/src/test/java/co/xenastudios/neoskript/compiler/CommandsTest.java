/*
 * Copyright (c) 2026 Xena Studios
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package co.xenastudios.neoskript.compiler;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

import co.xenastudios.neoskript.frontend.parse.ParsedScript;
import co.xenastudios.neoskript.frontend.parse.ScriptLoader;
import co.xenastudios.neoskript.frontend.source.SourceFile;
import co.xenastudios.neoskript.runtime.SimpleTriggerContext;
import co.xenastudios.neoskript.runtime.Timespan;
import co.xenastudios.neoskript.runtime.Values;
import co.xenastudios.neoskript.runtime.command.CommandEvent;
import co.xenastudios.neoskript.runtime.variables.VariableStore;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** Commands: matching what's typed against routes, arguments, choices, defaults and messages. */
class CommandsTest {

    private final List<String> said = new ArrayList<>();
    private Library library;

    @BeforeEach
    void library() {
        library = new Library();
        library.type("string", "text", String.class);
        library.type("number", "number", Number.class);
        library.parser("number", text -> {
            try {
                return text.contains(".") ? (Object) Double.parseDouble(text) : (Object) Long.parseLong(text);
            } catch (NumberFormatException e) {
                return null;
            }
        });
        library.event("ScriptLoad", co.xenastudios.neoskript.api.registration.ScriptEvent.LOAD, "[script] load");
        library.effect("Say", info -> (context, args) -> said.add(Values.text(args[0])), "say %objects%");
        library.expression("Remaining", "timespan",
                info -> (context, args) -> ((CommandEvent) context.event()).remaining(), "[the] remaining time");
    }

    private CompiledScripts compile(String text) {
        List<ParsedScript> parsed = new ScriptLoader(library.registry()).load(List.of(new SourceFile("test.sk", text)));
        assertEquals(List.of(), parsed.getFirst().diagnostics());
        CompiledScripts compiled = new ScriptCompiler(library, new VariableStore()).compile(parsed);
        assertEquals(List.of(), compiled.diagnostics());
        return compiled;
    }

    /** Runs {@code input} as the first route that matches it would, or says "no match". */
    private void run(CompiledCommand command, String input) {
        for (CompiledCommand.Route route : command.routes()) {
            CompiledCommand.Matcher.Match match = route.match(input);
            if (match != null) {
                route.trigger().execute(new SimpleTriggerContext(new CommandEvent(null, command.name(),
                        command.name() + " " + input, match.arguments(), match.choices())));
                return;
            }
        }
        said.add("no match");
    }

    @Test
    void argumentsByNumberOrdinalAndType() {
        CompiledCommand command = compile("command /add <number> <number> [<text>]:\n\ttrigger:\n"
                + "\t\tsay arg-1 + argument 2\n\t\tsay the 3rd argument\n\t\tsay the text argument\n"
                + "\t\tsay the last argument\n").commands().getFirst();
        run(command, "1 2 hello world");
        assertEquals(List.of("3", "hello world", "hello world", "hello world"), said);
        said.clear();
        run(command, "1 x");
        assertEquals(List.of("no match"), said);
    }

    @Test
    void hyphensInArgumentNumbersArentMinusSigns() {
        run(compile("command /m <number> <number>:\n\ttrigger:\n\t\tsay arg-1 * arg-2\n\t\tsay arg-1 - arg-2\n")
                .commands().getFirst(), "3 4");
        assertEquals(List.of("12", "-1"), said);
    }

    @Test
    void namedArgumentsAreLocalVariables() {
        run(compile("command /n <x: numbers> [<y: number = 7>]:\n\ttrigger:\n\t\tsay {_x::*}\n\t\tsay {_y}\n")
                .commands().getFirst(), "1, 2 and 3");
        assertEquals(List.of("1, 2 and 3", "7"), said);
    }

    @Test
    void textArgumentsTakeAsFewWordsAsTheyCanExceptTheLast() {
        CompiledCommand command = compile("command /mute <text> <text> [<text>]:\n\ttrigger:\n"
                + "\t\tsay \"%arg-1%|%arg-2%|%arg-3%\"\n").commands().getFirst();
        run(command, "bob 10m spamming in chat");
        run(command, "bob 10m");
        assertEquals(List.of("bob|10m|spamming in chat", "bob|10m|<none>"), said);
    }

    @Test
    void optionalArgumentsAreSkippedWhenTheNextOneFits() {
        CompiledCommand command = compile("command /d [<number>] [<text>]:\n\ttrigger:\n\t\tsay \"%arg-1%/%arg-2%\"\n")
                .commands().getFirst();
        run(command, "survival");
        run(command, "5");
        run(command, "");
        assertEquals(List.of("<none>/survival", "5/<none>", "<none>/<none>"), said);
    }

    @Test
    void choicesAndWords() {
        CompiledCommand command = compile("command /c (a|b) [c|d] [go]:\n\ttrigger:\n"
                + "\t\tsay the first choice argument and the 2nd choice argument\n").commands().getFirst();
        run(command, "a");
        run(command, "B c go");
        run(command, "e");
        assertEquals(List.of("a", "B and c", "no match"), said);
    }

    @Test
    void rangesRejectNumbersOutsideThem() {
        CompiledCommand command = compile("command /r <x: number above 5 and below 10>:\n\ttrigger:\n\t\tsay {_x}\n")
                .commands().getFirst();
        run(command, "6");
        run(command, "4");
        run(command, "11");
        assertEquals(List.of("6", "no match", "no match"), said);
    }

    @Test
    void subcommandsComeBeforeTheCommand() {
        CompiledCommand command = compile("command /s <number>:\n\tsubcommand <number>:\n\t\ttrigger:\n"
                + "\t\t\tsay argument 1 - argument 2\n\ttrigger:\n\t\tsay arg-1\n").commands().getFirst();
        run(command, "10");
        run(command, "10 5");
        assertEquals(List.of("10", "5"), said);
    }

    @Test
    void quotedTextAndDefaults() {
        CompiledCommand command = compile("command q [\" <text=\"success 2\"> \"]:\n\ttrigger:\n\t\tsay arg\n")
                .commands().getFirst();
        run(command, "\" \"success 1\" \"");
        run(command, "");
        assertEquals(List.of("success 1", "success 2"), said);
    }

    @Test
    void entriesAndMessages() {
        CompiledCommand command = compile("command /tpaccept:\n\taliases: /tpyes, tpy and tpok\n\tpermission: tp.accept\n"
                + "\tcooldown: 10 seconds\n\tcooldown message: Wait %remaining time%.\n\ttrigger:\n\t\tstop\n")
                .commands().getFirst();
        assertEquals(List.of("tpyes", "tpy", "tpok"), command.aliases());
        assertEquals("tp.accept", command.permission());
        assertEquals(new Timespan(10_000), command.cooldown());
        assertNull(command.permissionMessage());
        CommandEvent event = new CommandEvent(null, "tpaccept", "tpaccept", new Object[0], new String[0]);
        event.cooldown(command.cooldown(), new Timespan(4_000), null);
        assertNotNull(command.cooldownMessage());
        command.cooldownMessage().execute(new SimpleTriggerContext(event));
        assertEquals("Wait 6 seconds.", event.result());
    }

    @Test
    void argumentsOutsideCommandsOrOutOfRangeAreErrors() {
        List<ParsedScript> parsed = new ScriptLoader(library.registry()).load(List.of(new SourceFile("test.sk",
                "command /x <number>:\n\ttrigger:\n\t\tsay arg-2\non load:\n\tsay arg-1\n")));
        CompiledScripts compiled = new ScriptCompiler(library, new VariableStore()).compile(parsed);
        assertEquals(List.of("This command doesn't have a 2nd argument", "'arg-1' can only be used in a command"),
                compiled.diagnostics().stream().map(d -> d.message()).toList());
    }
}
