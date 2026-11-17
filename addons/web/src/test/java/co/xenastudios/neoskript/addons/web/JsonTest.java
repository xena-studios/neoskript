/*
 * Copyright (c) 2026 Xena Studios
 * SPDX-License-Identifier: GPL-3.0-only
 */
package co.xenastudios.neoskript.addons.web;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

/**
 * skJson's JSON syntax gives what skJson 6.0.0 gives on Skript 2.17.0: each expected value here is skJson's, as the
 * neoskript-web scenario recorded it, except where the comment says NeoSkriptWeb fixes it.
 */
class JsonTest {

    private final Scripts scripts = new Scripts(WebConfig.DEFAULTS);

    @AfterEach
    void close() {
        scripts.close();
    }

    private List<String> run(String... lines) throws InterruptedException {
        StringBuilder script = new StringBuilder("on load:\n\tset {_p} to parse \"{\"\"a\"\": [1, 2.0, \"\"x\"\", {\"\"b\"\": true}], \"\"c\"\": null, \"\"d\"\": {\"\"e\"\": \"\"f\"\"}}\" as json\n");
        for (String line : lines) {
            script.append('\t').append(line).append('\n');
        }
        scripts.run(script.toString(), 5);
        assertEquals(List.of(), scripts.errors);
        return scripts.said;
    }

    @Test
    void readsJson() throws InterruptedException {
        assertEquals(List.of("{\"a\":[1,2.0,\"x\",{\"b\":true}],\"c\":null,\"d\":{\"e\":\"f\"}}", "3", "a, c and d", "e",
                        "1, 2, x and {\"b\":true}", "x", "{\"b\":true}", "true", "f", "<none>", "{\"e\":\"f\"}"),
                run("say {_p}", "say json size of {_p}", "say \"%keys of {_p}%\"", "say all keys \"d\" of {_p}",
                        "set {_v::*} to values at path \"a\" in {_p}", "say \"%{_v::*}%\"", "say value at path \"a.2\" in {_p}",
                        "say value \"a[3]\" of {_p}", "say value at path \"a.3.b\" in {_p}", "say path \"d.e\" of {_p}",
                        "set {_n} to value at path \"nope.x\" in {_p}", "say \"%{_n}%\"", "say value \"d\" of {_p}"));
    }

    @Test
    void jsonGoesIntoListVariablesAndBack() throws InterruptedException {
        // As skJson gives them: a null isn't mapped, and 2.0 stays 2.0.
        assertEquals(List.of("a and d", "1, 2, 3 and 4", "1, 2, x", "true", "<none>",
                        "{\"a\":[1,2.0,\"x\",{\"b\":true}],\"d\":{\"e\":\"f\"}}",
                        "{\"1\":1,\"2\":\"two\",\"3\":true,\"4\":4.5,\"x\":[\"a\",\"b\"]}",
                        "{\"a\":[1,null,{\"q\":2}],\"b\":\"text\",\"c\":{\"k\":\"v\"}}"),
                run("map {_p} to {_m::*}", "say \"%indices of {_m::*}%\"", "say \"%indices of {_m::a::*}%\"",
                        "say \"%{_m::a::1}%, %{_m::a::2}%, %{_m::a::3}%\"", "say {_m::a::4::b}", "say \"%{_m::c}%\"",
                        "say {_m::*}'s formatted json",
                        "set {_l::*} to 1, \"two\", true and 4.5", "set {_l::x::*} to \"a\" and \"b\"",
                        "say {_l::*}'s formatted json",
                        "set {_n::a::1} to \"1\"", "set {_n::a::3} to \"{\"\"q\"\":2}\"", "set {_n::b} to \"text\"",
                        "set {_n::c::k} to \"v\"", "say formatted json of {_n::*}"));
    }

    @Test
    void hostileJsonIsTextAndDeepJsonIsCutShort() throws InterruptedException {
        String deep = "[".repeat(1000) + "]".repeat(1000);
        assertEquals(List.of("true", "true"), run("set {_d} to parse \"" + deep + "\" as json",
                "say whether {_d} is a json primitive",
                "set {_n} to parse \"" + "[".repeat(100) + "1" + "]".repeat(100) + "\" as json",
                "map {_n} to {_x::*}", "say whether {_x::*} is not set"));
    }
}
