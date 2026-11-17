/*
 * Copyright (c) 2026 Xena Studios
 * SPDX-License-Identifier: GPL-3.0-only
 */
package co.xenastudios.neoskript.plugin.check;

import static org.junit.jupiter.api.Assertions.assertEquals;

import co.xenastudios.neoskript.compiler.Library;
import co.xenastudios.neoskript.frontend.source.SourceFile;
import co.xenastudios.neoskript.paper.StandardLibrary;
import java.util.List;
import org.junit.jupiter.api.Test;

/** The script checker, as {@code neoskript-cli check} runs it. */
class ScriptCheckTest {

    @Test
    void aFunctionCallWithAnArgumentLeftOutIsChecked() {
        // Its left-out argument is a default, with no expression: the Folia check went through it and crashed.
        Library library = new Library();
        StandardLibrary.register(library);
        List<ScriptCheck.ScriptReport> reports = new ScriptCheck(library).check(List.of(new SourceFile("test.sk", """
                function twice(a: number = 1) :: number:
                \treturn {_a} * 2

                every 5 seconds:
                \tset {checked} to twice()
                """)));
        assertEquals(1, reports.size());
        assertEquals(List.of(), reports.getFirst().findings().stream()
                .filter(f -> f.kind() == ScriptCheck.Kind.ERROR).toList());
    }
}
