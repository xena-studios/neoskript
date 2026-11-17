/*
 * Copyright (c) 2026 Xena Studios
 * SPDX-License-Identifier: GPL-3.0-only
 */
package co.xenastudios.neoskript.addons.placeholders;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import co.xenastudios.neoskript.api.element.Condition;
import co.xenastudios.neoskript.api.registration.LoadedTrigger;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.function.BiConsumer;
import java.util.logging.Logger;
import org.junit.jupiter.api.Test;

class PlaceholdersTest {

    /** PlaceholderAPI's part, stood in for: what's registered, by prefix as written. */
    private final List<String> registered = new ArrayList<>();
    private final List<Runnable> globalTasks = new ArrayList<>();
    private final Placeholders.Expansions expansions = new Placeholders.Expansions() {
        @Override
        public Object register(String prefix, Placeholders placeholders) {
            registered.add(prefix);
            return prefix;
        }

        @Override
        public void unregister(Object expansion) {
            registered.remove((String) expansion);
        }
    };
    private final Placeholders placeholders = new Placeholders(expansions, Runnable::run, () -> false,
            Logger.getLogger("test"));

    /** A loaded trigger that runs {@code code} with the request. */
    private static LoadedTrigger trigger(String prefix, boolean relational, BiConsumer<PlaceholderRequestEvent, Object> code) {
        RequestFilter filter = new RequestFilter(List.of(prefix), relational);
        return new LoadedTrigger() {
            @Override
            public String script() {
                return "test.sk";
            }

            @Override
            public int line() {
                return 1;
            }

            @Override
            public Condition filter() {
                return filter;
            }

            @Override
            public void run(Object event, Object owner) {
                code.accept((PlaceholderRequestEvent) event, owner);
            }
        };
    }

    private static LoadedTrigger answering(String prefix, String result) {
        return trigger(prefix, false, (event, owner) -> event.result(result));
    }

    @Test
    void registersOneExpansionForEachPrefixInUse() {
        placeholders.update(List.of(answering("MyServer", "a"), answering("myserver", "b"), answering("other", "c")));
        assertEquals(List.of("MyServer", "other"), registered);
        assertEquals(Set.of("myserver", "other"), placeholders.registered());
    }

    @Test
    void reloadsLeaveNothingBehind() {
        for (int i = 0; i < 50; i++) {
            placeholders.update(List.of(answering("p" + i, "x"), answering("kept", "y")));
        }
        assertEquals(Set.of("p49", "kept"), placeholders.registered());
        assertEquals(2, registered.size());
        placeholders.update(List.of());
        assertTrue(registered.isEmpty());
        assertTrue(placeholders.registered().isEmpty());
    }

    @Test
    void closingUnregistersEverythingAndLaterUpdatesChangeNothing() {
        placeholders.update(List.of(answering("a", "x")));
        placeholders.close();
        assertTrue(registered.isEmpty());
        placeholders.update(List.of(answering("b", "x")));
        assertTrue(registered.isEmpty());
        assertNull(placeholders.request("b", "x", null));
    }

    @Test
    void theFirstTriggerInScriptOrderWithAResultAnswers() {
        placeholders.update(List.of(answering("p", null), answering("P", "second"), answering("p", "third")));
        assertEquals("second", placeholders.request("p", "anything", null));
    }

    @Test
    void theEventHasThePlaceholderAsTheTriggerWritesItsPrefix() {
        List<String> seen = new ArrayList<>();
        placeholders.update(List.of(trigger("MyServer", false, (event, owner) -> {
            seen.add(event.placeholder() + "|" + event.prefix() + "|" + event.identifier());
            event.result("ok");
        })));
        assertEquals("ok", placeholders.request("myserver", "coins_total", null));
        assertEquals(List.of("MyServer_coins_total|MyServer|coins_total"), seen);
    }

    @Test
    void relationalAndPlainTriggersAnswerOnlyTheirOwnKind() {
        placeholders.update(List.of(trigger("p", true, (event, owner) -> event.result("relational")),
                answering("p", "plain")));
        assertEquals("plain", placeholders.request("p", "x", null));
    }

    @Test
    void anUnknownPrefixHasNoValue() {
        placeholders.update(List.of(answering("p", "x")));
        assertNull(placeholders.request("q", "x", null));
    }

    @Test
    void aTriggerThatThrowsIsPassedOver() {
        placeholders.update(List.of(trigger("p", false, (event, owner) -> {
            throw new IllegalStateException("broken");
        }), answering("p", "fine")));
        assertEquals("fine", placeholders.request("p", "x", null));
    }

    @Test
    void aPlaceholderThatAsksForItselfStops() {
        int[] calls = {0};
        placeholders.update(List.of(trigger("loop", false, (event, owner) -> {
            calls[0]++;
            String inner = placeholders.request("loop", "x", null);
            event.result(inner == null ? "bottom" : inner);
        })));
        assertEquals("bottom", placeholders.request("loop", "x", null));
        assertEquals(Placeholders.MAX_DEPTH, calls[0]);
    }

    @Test
    void registeringOffTheGlobalThreadWaitsForIt() {
        Placeholders later = new Placeholders(expansions, globalTasks::add, () -> true, Logger.getLogger("test"));
        later.update(List.of(answering("p", "x")));
        // The lookup switches at once; the expansion follows on the global thread.
        assertEquals("x", later.request("p", "y", null));
        assertTrue(registered.isEmpty());
        globalTasks.forEach(Runnable::run);
        assertEquals(List.of("p"), registered);
    }

    @Test
    void anExpansionThatFailsToRegisterDoesNotStopTheOthers() {
        Placeholders failing = new Placeholders(new Placeholders.Expansions() {
            @Override
            public Object register(String prefix, Placeholders placeholders) {
                if (prefix.equals("bad")) {
                    throw new IllegalStateException("refused");
                }
                registered.add(prefix);
                return prefix;
            }

            @Override
            public void unregister(Object expansion) {
                registered.remove((String) expansion);
            }
        }, Runnable::run, () -> false, Logger.getLogger("test"));
        failing.update(List.of(answering("bad", "x"), answering("good", "y")));
        assertEquals(List.of("good"), registered);
    }

    @Test
    void prefixesAreCheckedAsSkriptPlaceholdersChecksThem() {
        assertNull(Syntax.invalid("myserver"));
        assertEquals("A prefix cannot be blank", Syntax.invalid(" "));
        assertEquals("PlaceholderAPI prefixes cannot contain the character '_'", Syntax.invalid("my_server"));
        assertEquals("PlaceholderAPI prefixes cannot contain the character '%'", Syntax.invalid("50%"));
    }
}
