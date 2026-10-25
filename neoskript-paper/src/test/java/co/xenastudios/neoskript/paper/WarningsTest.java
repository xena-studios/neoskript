/*
 * Copyright (c) 2026 Xena Studios
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package co.xenastudios.neoskript.paper;

import static org.junit.jupiter.api.Assertions.assertEquals;

import co.xenastudios.neoskript.compiler.Library;
import co.xenastudios.neoskript.compiler.ScriptCompiler;
import co.xenastudios.neoskript.frontend.parse.ParsedScript;
import co.xenastudios.neoskript.frontend.parse.ScriptLoader;
import co.xenastudios.neoskript.frontend.source.Diagnostic;
import co.xenastudios.neoskript.frontend.source.SourceFile;
import co.xenastudios.neoskript.runtime.variables.VariableStore;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

/** Skript's parse-time warnings, as Skript 2.17.0 gives them; warnings never stop a script from loading. */
class WarningsTest {

    /** The script's problems, as {@code line: message}, warnings and errors alike. */
    private static List<String> problems(String script) {
        Library library = new Library();
        StandardLibrary.register(library);
        List<ParsedScript> parsed = new ScriptLoader(library.registry()).load(List.of(new SourceFile("test.sk", script)));
        List<Diagnostic> all = new ArrayList<>(parsed.getFirst().diagnostics());
        all.addAll(new ScriptCompiler(library, new VariableStore()).compile(parsed).diagnostics());
        return all.stream().map(d -> d.severity().name().charAt(0) + "" + d.line() + ": " + d.message()).toList();
    }

    @Test
    void lists() {
        assertEquals(List.of(
                "W2: List is missing 'and' or 'or', defaulting to 'and': 1, 2, 3",
                "W3: List has multiple 'and' or 'or', will default to 'and'. Use brackets if you want to define multiple"
                        + " lists. List: 1, 2 or 3 and 4"), problems("""
                on load:
                \tset {_a::*} to 1, 2, 3
                \tset {_b::*} to 1, 2 or 3 and 4
                \tset {_c::*} to 1, 2 and 3
                \tsuppress missing conjunction warnings
                \tset {_d::*} to 1, 2, 3
                """));
    }

    @Test
    void variableNames() {
        assertEquals(List.of(
                "W2: Starting a variable's name with an expression is discouraged ({%now%::x}). You could prefix it"
                        + " with the script's name: {test::%now%::x}",
                "W3: The character '.' is reserved at the start of variable names, and may be restricted in future"
                        + " versions"), problems("""
                on load:
                \tset {%now%::x} to 1
                \tset {.x} to 1
                \tsuppress starting expression warnings
                \tset {%now%::y} to 1
                """));
    }

    @Test
    void elements() {
        assertEquals(List.of(
                "W3: the 1st argument is already a text, so you should not put it in one (e.g. the 1st argument instead"
                        + " of \"%the 1st argument%\")",
                "W4: The condition '1 is equal to 1' will always be true.",
                "W6: The condition '\"a\" is not equal to \"b\"' will always be true.",
                "W8: Delays less than one tick are not possible, defaulting to one tick.",
                "W9: Since you're looping exactly one time, you could simply remove the loop instead",
                "W11: MD5 is not secure and shouldn't be used if a cryptographically secure hashing algorithm is"
                        + " required."), problems("""
                command /t <text>:
                \ttrigger:
                \t\tsend "%arg-1%"
                \t\tif 1 is 1:
                \t\t\tstop
                \t\tif "a" is not "b":
                \t\t\tstop
                \t\twait 0 ticks
                \t\tloop 1 time:
                \t\t\tstop
                \t\tset {_x} to "a" hashed with md5
                \t\tsuppress deprecated syntax warnings
                \t\tset {_x} to "a" hashed with md5
                """));
    }
}
