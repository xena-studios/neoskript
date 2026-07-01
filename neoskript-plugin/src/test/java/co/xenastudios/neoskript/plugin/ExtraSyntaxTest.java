package co.xenastudios.neoskript.plugin;

import co.xenastudios.neoskript.core.parser.ScriptParser;
import co.xenastudios.neoskript.core.registry.DefaultSyntaxRegistry;
import co.xenastudios.neoskript.core.runtime.CommandRegistry;
import co.xenastudios.neoskript.core.runtime.EventRegistry;
import co.xenastudios.neoskript.core.runtime.FunctionRegistry;
import co.xenastudios.neoskript.lang.BuiltinFunctions;
import co.xenastudios.neoskript.lang.BuiltinModule;
import co.xenastudios.neoskript.lang.event.BuiltinEvents;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;
import org.mockbukkit.mockbukkit.entity.PlayerMock;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertTrue;

/** Behaviour + parse coverage for the quaternion/axisAngle functions and filtered event triggers. */
class ExtraSyntaxTest {

    private ServerMock server;
    private NeoSkriptPlugin plugin;

    @BeforeEach
    void setUp() {
        server = MockBukkit.mock();
        plugin = MockBukkit.load(NeoSkriptPlugin.class);
    }

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    @Test
    void mathFunctionsRun() throws IOException {
        Path scripts = plugin.getDataFolder().toPath().resolve("scripts");
        Files.createDirectories(scripts);
        Files.writeString(scripts.resolve("x.sk"), """
                command /x:
                    trigger:
                        set {_q} to quaternion(1, 0, 0, 0)
                        if {_q} is set:
                            send "Q" to player
                        set {_a} to axisAngle(90, vector(0, 1, 0))
                        if {_a} is set:
                            send "A" to player
                """, StandardCharsets.UTF_8);
        server.dispatchCommand(server.getConsoleSender(), "neoskript reload");
        PlayerMock player = server.addPlayer();
        server.dispatchCommand(player, "x");
        java.util.Set<String> seen = new java.util.HashSet<>();
        String m;
        while ((m = player.nextMessage()) != null) {
            seen.add(m);
        }
        assertTrue(seen.contains("Q"), "quaternion(1,0,0,0) resolves");
        assertTrue(seen.contains("A"), "axisAngle(90, vector) resolves");
    }

    @Test
    void forEachLoopRuns() throws IOException {
        Path scripts = plugin.getDataFolder().toPath().resolve("scripts");
        Files.createDirectories(scripts);
        Files.writeString(scripts.resolve("fe.sk"), """
                command /fe:
                    trigger:
                        set {_l::1} to "alpha"
                        set {_l::2} to "beta"
                        for each {_x} in {_l::*}:
                            send "ITEM:%{_x}%" to player
                """, StandardCharsets.UTF_8);
        server.dispatchCommand(server.getConsoleSender(), "neoskript reload");
        PlayerMock player = server.addPlayer();
        server.dispatchCommand(player, "fe");
        java.util.Set<String> seen = new java.util.HashSet<>();
        String m;
        while ((m = player.nextMessage()) != null) {
            seen.add(m);
        }
        assertTrue(seen.contains("ITEM:alpha") && seen.contains("ITEM:beta"),
                "for each binds {_x} to every list element; got " + seen);
    }

    @Test
    void timespanValueTypeRenders() throws IOException {
        Path scripts = plugin.getDataFolder().toPath().resolve("scripts");
        Files.createDirectories(scripts);
        Files.writeString(scripts.resolve("ts.sk"), """
                command /ts:
                    trigger:
                        set {_span} to 90 seconds
                        send "SPAN:%{_span}%" to player
                        set {_c} to the item cooldown of stone for player
                        set {_f} to the fire burning time of player
                        set {_p} to time played of player
                        send "DONE" to player
                """, StandardCharsets.UTF_8);
        server.dispatchCommand(server.getConsoleSender(), "neoskript reload");
        PlayerMock player = server.addPlayer();
        server.dispatchCommand(player, "ts");
        java.util.Set<String> seen = new java.util.HashSet<>();
        String m;
        while ((m = player.nextMessage()) != null) {
            seen.add(m);
        }
        assertTrue(seen.contains("SPAN:1 minute 30 seconds"), "timespan literal parses and renders; got " + seen);
        assertTrue(seen.contains("DONE"), "timespan expressions parse and run");
    }

    @Test
    void potionLootDisplayExpressions() throws IOException {
        Path scripts = plugin.getDataFolder().toPath().resolve("scripts");
        Files.createDirectories(scripts);
        Files.writeString(scripts.resolve("pe.sk"), """
                command /pe:
                    trigger:
                        set {_dn} to display name of player
                        if {_dn} is set:
                            send "DN" to player
                        set {_lt} to loot table of player
                        set {_pd} to potion duration of player
                        set {_el} to enchantment level of "x" on item("diamond sword")
                        send "DONE" to player
                """, StandardCharsets.UTF_8);
        server.dispatchCommand(server.getConsoleSender(), "neoskript reload");
        PlayerMock player = server.addPlayer("Alex");
        server.dispatchCommand(player, "pe");
        java.util.Set<String> seen = new java.util.HashSet<>();
        String m;
        while ((m = player.nextMessage()) != null) {
            seen.add(m);
        }
        assertTrue(seen.contains("DN"), "display name of player resolves");
        assertTrue(seen.contains("DONE"), "loot table / potion duration / enchantment level parse and run");
    }

    @Test
    void filteredEventsParse() {
        DefaultSyntaxRegistry registry = new DefaultSyntaxRegistry();
        BuiltinModule.registerAll(registry);
        EventRegistry events = new EventRegistry();
        BuiltinEvents.registerAll(events);
        FunctionRegistry functions = new FunctionRegistry();
        BuiltinFunctions.registerAll(functions);
        String[] aliases = {
                "first join", "break of diamond ore", "mine of stone", "place of stone",
                "death of zombie", "rightclick holding a stick", "script load",
        };
        for (String alias : aliases) {
            ScriptParser parser = new ScriptParser(registry, events, functions, new CommandRegistry());
            parser.parse("on " + alias + ":\n    set {_x} to 1\n");
            assertTrue(parser.errors().isEmpty(), "on " + alias + " should parse: " + parser.errors());
        }
    }
}
