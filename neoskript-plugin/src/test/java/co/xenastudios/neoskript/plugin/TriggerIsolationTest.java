/*
 * Copyright (c) 2026 Xena Studios
 * SPDX-License-Identifier: GPL-3.0-only
 */
package co.xenastudios.neoskript.plugin;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import co.xenastudios.neoskript.runtime.ScriptRuntimeException;
import co.xenastudios.neoskript.runtime.Trigger;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.List;
import org.bukkit.event.Event;
import org.bukkit.event.EventPriority;
import org.bukkit.event.HandlerList;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;

/** What one trigger throws stays with it: the event's other triggers run, and nothing reaches Bukkit. */
class TriggerIsolationTest {

    private static final class Fired extends Event {
        private static final HandlerList HANDLERS = new HandlerList();

        @Override
        public HandlerList getHandlers() {
            return HANDLERS;
        }

        public static HandlerList getHandlerList() {
            return HANDLERS;
        }
    }

    /** A logger that keeps the messages of its errors. */
    private static Logger logger(List<String> errors) {
        return (Logger) Proxy.newProxyInstance(Logger.class.getClassLoader(), new Class<?>[] {Logger.class},
                (proxy, method, args) -> {
                    if (method.getName().equals("error") && args != null) {
                        errors.add(String.valueOf(args[0]));
                    }
                    return method.getReturnType() == boolean.class ? false : null;
                });
    }

    @Test
    void theEventsOtherTriggersStillRun() {
        List<String> errors = new ArrayList<>();
        List<String> ran = new ArrayList<>();
        EventDispatcher dispatcher = new EventDispatcher(null, logger(errors), EventPriority.HIGH, false);
        Trigger classCast = context -> {
            throw new ClassCastException("not compiled code");
        };
        Trigger recursed = context -> {
            throw new ScriptRuntimeException("deep.sk", 3, new StackOverflowError());
        };
        Trigger failed = context -> {
            throw new ScriptRuntimeException("npe.sk", 7, new NullPointerException());
        };
        Trigger linkage = context -> {
            throw new NoClassDefFoundError("an addon's class");
        };
        Trigger last = context -> ran.add("last");
        dispatcher.fire(new Trigger[] {classCast, recursed, failed, linkage, last}, new Fired());
        assertEquals(List.of("last"), ran);
        // Recursion is told to admins, as Skript does, not logged with its stack.
        assertEquals(3, errors.size(), errors.toString());
        assertTrue(errors.contains("Error in {}, line {}: {}"), errors.toString());
    }

    @Test
    void recursionIsToldToAdminsInSkriptsWords() {
        assertEquals("[Skript] The script 'deep.sk' infinitely (or excessively) repeated itself!",
                net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer.plainText()
                        .serialize(TriggerErrors.repeated("deep.sk")));
    }

    private static final class Other extends Event {
        private static final HandlerList HANDLERS = new HandlerList();

        @Override
        public HandlerList getHandlers() {
            return HANDLERS;
        }

        public static HandlerList getHandlerList() {
            return HANDLERS;
        }
    }

    @Test
    void anEventTheServerWontListenToDoesntStopTheOthers() {
        // One registerEvent that threw left every event class after it without a listener.
        List<String> errors = new ArrayList<>();
        List<Class<?>> registered = new ArrayList<>();
        EventDispatcher dispatcher = new EventDispatcher(null, logger(errors), EventPriority.HIGH, false) {
            @Override
            void register(Class<? extends Event> eventClass, Trigger[] listeners) {
                if (eventClass == Fired.class) {
                    throw new IllegalArgumentException("no handler list");
                }
                registered.add(eventClass);
            }
        };
        Trigger nothing = context -> {};
        dispatcher.update(List.of(
                new co.xenastudios.neoskript.compiler.CompiledScripts.CompiledTrigger("a.sk", 1, null,
                        List.of(Fired.class), null, null, nothing),
                new co.xenastudios.neoskript.compiler.CompiledScripts.CompiledTrigger("b.sk", 1, null,
                        List.of(Fired.class, Other.class), null, null, nothing)));
        assertEquals(List.of(Other.class), registered);
        assertEquals(List.of("Couldn't listen for {}, so its triggers in {} won't run: {}"), errors);
    }
}
