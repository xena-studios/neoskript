/*
 * Copyright (c) 2026 Xena Studios
 * SPDX-License-Identifier: GPL-3.0-only
 */
package co.xenastudios.neoskript.plugin;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.List;
import org.bukkit.command.CommandSender;
import org.bukkit.event.server.TabCompleteEvent;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

class LegacyTabCompletionTest {

    private static CommandSender sender(String name) {
        return (CommandSender) Proxy.newProxyInstance(CommandSender.class.getClassLoader(),
                new Class<?>[] {CommandSender.class},
                (proxy, method, args) -> method.getName().equals("getName") ? name : null);
    }

    private static final CommandSender OP = sender("Op");
    private static final CommandSender GUEST = sender("Guest");

    @AfterEach
    void reset() {
        ScriptCommand.legacyTabCompletion(true);
        // Whatever a test left is cleared by the next event on this thread.
        ScriptCommand.keepOwnCompletions(new TabCompleteEvent(OP, "/x", new ArrayList<>()));
    }

    /** What Bukkit's TabCompleteEvent ends with after another plugin replaced the completions. */
    private static List<String> event(CommandSender sender, String buffer) {
        TabCompleteEvent event = new TabCompleteEvent(sender, buffer, new ArrayList<>(List.of("own")));
        event.setCompletions(new ArrayList<>(List.of("other")));
        ScriptCommand.keepOwnCompletions(event);
        return event.getCompletions();
    }

    @Test
    void otherPluginsCanChangeCompletionsByDefault() {
        ScriptCommand.given(OP, "shop", new String[] {"b"}, List.of("buy"));
        assertEquals(List.of("other"), event(OP, "/shop b"));
    }

    @Test
    void offAScriptCommandsCompletionsAreItsOwn() {
        ScriptCommand.legacyTabCompletion(false);
        ScriptCommand.given(OP, "shop", new String[] {"b"}, List.of("buy"));
        assertEquals(List.of("buy"), event(OP, "/shop b"));
        ScriptCommand.given(OP, "shop", new String[] {"b"}, List.of("buy"));
        assertEquals(List.of("buy"), event(OP, "shop b"));
        assertFalse(ScriptCommand.holdsCompletions(), "an event clears what was noted");
    }

    @Test
    void completionsLeftWithoutAnEventReachNoOneElse() {
        ScriptCommand.legacyTabCompletion(false);
        // Another plugin asked the command map for an op's completions, with no event after.
        ScriptCommand.given(OP, "shop", new String[] {"b"}, List.of("secret"));
        // Another player's event, or another command's, or other words: the op's completions aren't used.
        assertEquals(List.of("other"), event(GUEST, "/shop b"));
        ScriptCommand.given(OP, "shop", new String[] {"b"}, List.of("secret"));
        assertEquals(List.of("other"), event(OP, "/home b"));
        ScriptCommand.given(OP, "shop", new String[] {"b"}, List.of("secret"));
        assertEquals(List.of("other"), event(OP, "/shop c"));
        assertFalse(ScriptCommand.holdsCompletions(), "nothing is kept after an event, used or not");
    }
}
