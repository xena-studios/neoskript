/*
 * Copyright (c) 2026 Xena Studios
 * SPDX-License-Identifier: GPL-3.0-only
 */
package co.xenastudios.neoskript.plugin;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import co.xenastudios.neoskript.api.element.TriggerContext;
import co.xenastudios.neoskript.api.registration.ScriptEvent;
import co.xenastudios.neoskript.compiler.CompiledScripts;
import co.xenastudios.neoskript.runtime.Trigger;
import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

/** Which triggers of scripts being loaded listen yet, and the walk that runs their 'on load's (see {@link LoadWalk}). */
class LoadWalkTest {

    /** A trigger of its own: a lambda that captures nothing would be one shared object. */
    private static Trigger trigger() {
        return new Trigger() {
            @Override
            public void execute(TriggerContext context) {}
        };
    }

    private static CompiledScripts.CompiledTrigger compiled(String script, int line, ScriptEvent event) {
        return new CompiledScripts.CompiledTrigger(script, line, event, event == null ? List.of(Object.class) : null,
                null, null, trigger(), null);
    }

    @Test
    void triggersBeforeTheFirstOnLoadListenFromTheStart() {
        Trigger before = trigger();
        Trigger load = trigger();
        Trigger after = trigger();
        LoadWalk walk = new LoadWalk(List.of(before, load, after), 1);
        assertFalse(walk.pending(before));
        assertTrue(walk.pending(after));
    }

    @Test
    void eachTriggerListensOnceTheWalkReachesIt() {
        Trigger load = trigger();
        Trigger first = trigger();
        Trigger second = trigger();
        LoadWalk walk = new LoadWalk(List.of(load, first, second), 0);
        walk.reach(0);
        assertTrue(walk.pending(first));
        walk.reach(1);
        assertFalse(walk.pending(first));
        assertTrue(walk.pending(second));
        // Reaching an earlier place again doesn't take a trigger back.
        walk.reach(0);
        assertFalse(walk.pending(first));
    }

    @Test
    void finishingLetsEveryTriggerListen() {
        Trigger load = trigger();
        Trigger later = trigger();
        LoadWalk walk = new LoadWalk(List.of(load, later), 0);
        walk.finish();
        assertFalse(walk.pending(later));
    }

    @Test
    void triggersOfOtherScriptsNeverWait() {
        LoadWalk walk = new LoadWalk(List.of(trigger(), trigger()), 0);
        assertFalse(walk.pending(trigger()));
    }

    @Test
    void anOnLoadWhoseScriptReloadedSinceDoesntRun() {
        CompiledScripts.CompiledTrigger first = compiled("a.sk", 1, ScriptEvent.LOAD);
        CompiledScripts.CompiledTrigger event = compiled("a.sk", 4, null);
        CompiledScripts.CompiledTrigger stale = compiled("b.sk", 1, ScriptEvent.LOAD);
        List<CompiledScripts.CompiledTrigger> order = List.of(first, event, stale);
        LoadWalk walk = new LoadWalk(order.stream().map(CompiledScripts.CompiledTrigger::trigger).toList(), 0);
        Set<CompiledScripts.CompiledTrigger> loaded = Collections.newSetFromMap(new IdentityHashMap<>());
        loaded.addAll(order);
        List<CompiledScripts.CompiledTrigger> ran = new ArrayList<>();
        ScriptManager.walk(order, walk, loaded::contains, trigger -> {
            ran.add(trigger);
            if (trigger == first) {
                // a.sk's 'on load' reloads b.sk: its old 'on load' has unloaded, and the new one ran in that load.
                loaded.remove(stale);
            }
        });
        assertEquals(List.of(first), ran);
        assertFalse(walk.pending(event.trigger()));
    }
}
