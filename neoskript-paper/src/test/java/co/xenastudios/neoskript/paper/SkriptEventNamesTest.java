/*
 * Copyright (c) 2026 Xena Studios
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package co.xenastudios.neoskript.paper;

import static org.junit.jupiter.api.Assertions.assertEquals;

import co.xenastudios.neoskript.compiler.Library;
import co.xenastudios.neoskript.frontend.registry.SyntaxInfo;
import co.xenastudios.neoskript.frontend.registry.SyntaxKind;
import java.util.ArrayList;
import java.util.List;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.player.PlayerHarvestBlockEvent;
import org.junit.jupiter.api.Test;

/** Skript's events as its errors name them. */
class SkriptEventNamesTest {

    @Test
    void everyEventHasSkriptsName() {
        Library library = new Library();
        StandardLibrary.register(library);
        List<String> unnamed = new ArrayList<>();
        for (SyntaxInfo syntax : library.registry().syntaxes(SyntaxKind.EVENT)) {
            if (library.eventName(syntax.id()) == null) {
                unnamed.add(syntax.id());
            }
        }
        // Only the triggers that aren't server events, which name their events themselves.
        assertEquals(List.of("EvtPeriodical", "ScriptLoad", "EvtScript"), unnamed);
    }

    @Test
    void namesTheEventsOfClasses() {
        assertEquals(List.of("the on death event or the on harvest block event", "the on death event"),
                List.of(sorted(SkriptEventNames.names(EntityDeathEvent.class, PlayerHarvestBlockEvent.class)),
                        SkriptEventNames.names(EntityDeathEvent.class)));
        assertEquals("", SkriptEventNames.names(String.class));
    }

    /** {@code names} with its events in alphabetical order, as Skript's own order changes from run to run. */
    private static String sorted(String names) {
        List<String> events = new ArrayList<>(List.of(names.split(", | or ")));
        events.sort(null);
        return SkriptEventNames.join(events);
    }
}
