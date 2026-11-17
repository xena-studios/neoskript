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
 * A list of a response's properties is split as Skript splits it with skJson (issue #44): 'status of' and 'status
 * code of' take one response, and 'last response of' is several when its request is, so each list is two values.
 *
 * <p>Not tested: '(status code of last response of {_r} and ...)', where Reqn's bare 'last response' also lets it read
 * as '(status code of last response) of ({_r} and ...)', Skript's 'X of Y'. Skript with both Reqn and skJson reads it
 * that way too; skJson alone has no bare 'last response'.
 */
class ListSplitTest {

    private final Scripts scripts = new Scripts(WebConfig.DEFAULTS);

    @AfterEach
    void close() {
        scripts.close();
    }

    @Test
    void aResponsesPropertiesInAListAreEachTheirOwnValue() throws InterruptedException {
        scripts.run("""
                on load:
                \tset {_r} to prepare MOCK request on "http://example.invalid"
                \texecute {_r}
                \tsay (status of last response of {_r} and body of last response of {_r})
                \tset {_res} to last response of {_r}
                \tsay (status code of {_res} and body of {_res})
                \tsay (status of {_res} and status code of {_res})
                """, 5);
        assertEquals(List.of(), scripts.errors);
        assertEquals(List.of("OK and {\"mock\":true}", "200 and {\"mock\":true}", "OK and 200"), scripts.said);
    }
}
