/*
 * Copyright (c) 2026 Xena Studios
 * SPDX-License-Identifier: GPL-3.0-only
 */
package co.xenastudios.neoskript.addons.reflect;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;
import org.junit.jupiter.api.Test;

class ProxiesTest {

    private final ScriptHarness harness = new ScriptHarness();

    @Test
    void aProxyRunsTheFunctionsItsListNames() {
        List<String> said = harness.run("""
                import:
                \tjava.lang.Runnable
                \tjava.util.function.Supplier
                \tjava.util.function.Function

                function hello(p: object):
                \tsay "hello"

                function give(p: object) :: object:
                \treturn "given"

                function twice(times: number, p: object, value: object) :: object:
                \treturn {_value} * {_times}

                on load:
                \tset {_f::run} to function reference "hello"
                \tset {_p} to new proxy instance of Runnable using {_f::*}
                \t{_p}.run()
                \tset {_g::get} to function reference "give"
                \tset {_s} to new proxy instance of Supplier using {_g::*}
                \tsay {_s}.get()
                \tset {_h::apply} to function reference "twice" called with arguments 2
                \tset {_t} to new proxy instance of Function using {_h::*}
                \tsay {_t}.apply(21)
                \tsay whether {_p}.equals({_p})
                """);
        assertEquals(List.of("hello", "given", "42", "true"), said);
    }

    @Test
    void skriptReflectsChecks() {
        List<String> problems = harness.load("test.sk", """
                import:
                \tjava.lang.Runnable

                on load:
                \tset {_p} to new proxy instance of Runnable using {_f}
                """);
        assertEquals(List.of("ERROR 5: {_f} is not a list variable."), problems);
    }
}
