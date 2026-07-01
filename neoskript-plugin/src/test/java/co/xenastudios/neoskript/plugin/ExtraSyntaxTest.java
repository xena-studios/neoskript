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
    void spawnEffectRuns() throws IOException {
        Path scripts = plugin.getDataFolder().toPath().resolve("scripts");
        Files.createDirectories(scripts);
        Files.writeString(scripts.resolve("sp.sk"), """
                command /sp:
                    trigger:
                        spawn zombie at location of player
                        make player look at location of player
                        send "DONE" to player
                """, StandardCharsets.UTF_8);
        server.dispatchCommand(server.getConsoleSender(), "neoskript reload");
        org.mockbukkit.mockbukkit.entity.PlayerMock player = server.addPlayer();
        int before = player.getWorld().getEntities().size();
        server.dispatchCommand(player, "sp");
        boolean done = false;
        String m;
        while ((m = player.nextMessage()) != null) {
            if (m.contains("DONE")) {
                done = true;
            }
        }
        assertTrue(done, "spawn + look at parse and run");
        assertTrue(player.getWorld().getEntities().size() > before, "a zombie was spawned");
    }

    @Test
    void directionalLocations() throws IOException {
        Path scripts = plugin.getDataFolder().toPath().resolve("scripts");
        Files.createDirectories(scripts);
        Files.writeString(scripts.resolve("dir.sk"), """
                command /dir:
                    trigger:
                        set {_base} to location of player
                        set {_up} to 3 blocks above {_base}
                        if y-coordinate of {_up} is (y-coordinate of {_base}) + 3:
                            send "UP3" to player
                        set {_n} to 2 meters north of {_base}
                        if {_n} is set:
                            send "NORTH" to player
                        set {_f} to 1 block in front of player
                        if {_f} is set:
                            send "FRONT" to player
                """, StandardCharsets.UTF_8);
        server.dispatchCommand(server.getConsoleSender(), "neoskript reload");
        org.mockbukkit.mockbukkit.entity.PlayerMock player = server.addPlayer();
        server.dispatchCommand(player, "dir");
        java.util.Set<String> seen = new java.util.HashSet<>();
        String m;
        while ((m = player.nextMessage()) != null) {
            seen.add(m);
        }
        assertTrue(seen.contains("UP3"), "3 blocks above raises y by 3; got " + seen);
        assertTrue(seen.contains("NORTH") && seen.contains("FRONT"), "cardinal + relative directions resolve");
    }

    @Test
    void shootAndSpawnable() throws IOException {
        Path scripts = plugin.getDataFolder().toPath().resolve("scripts");
        Files.createDirectories(scripts);
        Files.writeString(scripts.resolve("sh.sk"), """
                command /sh:
                    trigger:
                        if zombie is spawnable:
                            send "SPAWNABLE" to player
                        shoot arrow from player
                        send "DONE" to player
                """, StandardCharsets.UTF_8);
        server.dispatchCommand(server.getConsoleSender(), "neoskript reload");
        org.mockbukkit.mockbukkit.entity.PlayerMock player = server.addPlayer();
        server.dispatchCommand(player, "sh");
        java.util.Set<String> seen = new java.util.HashSet<>();
        String m;
        while ((m = player.nextMessage()) != null) {
            seen.add(m);
        }
        assertTrue(seen.contains("SPAWNABLE"), "zombie is spawnable");
        assertTrue(seen.contains("DONE"), "shoot parses and runs (MockBukkit can't spawn the projectile)");
    }

    @Test
    void weatherToolTargetItemsColor() throws IOException {
        Path scripts = plugin.getDataFolder().toPath().resolve("scripts");
        Files.createDirectories(scripts);
        Files.writeString(scripts.resolve("wx.sk"), """
                command /wx:
                    trigger:
                        set {_w} to weather in world of player
                        if {_w} is set:
                            send "WEATHER" to player
                        set {_c} to color from hex code "FF0000"
                        if {_c} is set:
                            send "COLOR" to player
                        set {_t} to tool of player
                        set {_i} to all items in inventory of player
                        set {_tg} to target of player
                        send "DONE" to player
                """, StandardCharsets.UTF_8);
        server.dispatchCommand(server.getConsoleSender(), "neoskript reload");
        org.mockbukkit.mockbukkit.entity.PlayerMock player = server.addPlayer();
        server.dispatchCommand(player, "wx");
        java.util.Set<String> seen = new java.util.HashSet<>();
        String m;
        while ((m = player.nextMessage()) != null) {
            seen.add(m);
        }
        assertTrue(seen.contains("WEATHER"), "weather of world resolves");
        assertTrue(seen.contains("COLOR"), "color from hex code resolves");
        assertTrue(seen.contains("DONE"), "tool / items in / target parse and run");
    }

    @Test
    void itemBuilders() throws IOException {
        Path scripts = plugin.getDataFolder().toPath().resolve("scripts");
        Files.createDirectories(scripts);
        Files.writeString(scripts.resolve("iw.sk"), """
                command /iw:
                    trigger:
                        set {_l} to item("diamond sword") with lore "&7Legendary"
                        if {_l} is set:
                            send "LORE" to player
                        set {_g} to item("stone") with glint
                        if {_g} is set:
                            send "GLINT" to player
                        set {_c} to item("stone") with custom model data 5
                        set {_f} to item("stone") with item flag hide_enchants
                        send "DONE" to player
                """, StandardCharsets.UTF_8);
        server.dispatchCommand(server.getConsoleSender(), "neoskript reload");
        org.mockbukkit.mockbukkit.entity.PlayerMock player = server.addPlayer();
        server.dispatchCommand(player, "iw");
        java.util.Set<String> seen = new java.util.HashSet<>();
        String m;
        while ((m = player.nextMessage()) != null) {
            seen.add(m);
        }
        assertTrue(seen.contains("LORE"), "item with lore resolves");
        assertTrue(seen.contains("GLINT"), "item with glint resolves");
        assertTrue(seen.contains("DONE"), "item with custom model data / item flags parse and run");
    }

    @Test
    void dateTimeArithmetic() throws IOException {
        Path scripts = plugin.getDataFolder().toPath().resolve("scripts");
        Files.createDirectories(scripts);
        Files.writeString(scripts.resolve("dt.sk"), """
                command /dt:
                    trigger:
                        set {_s} to time since now
                        if {_s} is set:
                            send "SINCE" to player
                        set {_u} to unix date of 1000000
                        if {_u} is set:
                            send "UNIX" to player
                        set {_a} to 5 minutes ago
                        if {_a} is set:
                            send "AGO" to player
                        set {_ll} to last login of player
                        send "DONE" to player
                """, StandardCharsets.UTF_8);
        server.dispatchCommand(server.getConsoleSender(), "neoskript reload");
        org.mockbukkit.mockbukkit.entity.PlayerMock player = server.addPlayer();
        server.dispatchCommand(player, "dt");
        java.util.Set<String> seen = new java.util.HashSet<>();
        String m;
        while ((m = player.nextMessage()) != null) {
            seen.add(m);
        }
        assertTrue(seen.contains("SINCE"), "time since now resolves");
        assertTrue(seen.contains("UNIX"), "unix date resolves");
        assertTrue(seen.contains("AGO"), "5 minutes ago resolves");
        assertTrue(seen.contains("DONE"), "last login parses and runs");
    }

    @Test
    void colorStringUtils() throws IOException {
        Path scripts = plugin.getDataFolder().toPath().resolve("scripts");
        Files.createDirectories(scripts);
        Files.writeString(scripts.resolve("cx.sk"), """
                command /cx:
                    trigger:
                        set {_c} to colored "&aHello"
                        if {_c} is set:
                            send "COLORED" to player
                        set {_u} to uncolored "&aHello"
                        if {_u} is "Hello":
                            send "UNCOLORED" to player
                        set {_s} to all string colors
                        if {_s} is set:
                            send "STRINGCOLORS" to player
                """, StandardCharsets.UTF_8);
        server.dispatchCommand(server.getConsoleSender(), "neoskript reload");
        org.mockbukkit.mockbukkit.entity.PlayerMock player = server.addPlayer();
        server.dispatchCommand(player, "cx");
        java.util.Set<String> seen = new java.util.HashSet<>();
        String m;
        while ((m = player.nextMessage()) != null) {
            seen.add(m);
        }
        assertTrue(seen.contains("COLORED"), "colored resolves");
        assertTrue(seen.contains("UNCOLORED"), "uncolored strips codes to 'Hello'");
        assertTrue(seen.contains("STRINGCOLORS"), "string colors resolves");
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
                "death of zombie", "rightclick holding a stick", "script load", "walk on stone",
        };
        for (String alias : aliases) {
            ScriptParser parser = new ScriptParser(registry, events, functions, new CommandRegistry());
            parser.parse("on " + alias + ":\n    set {_x} to 1\n");
            assertTrue(parser.errors().isEmpty(), "on " + alias + " should parse: " + parser.errors());
        }
    }
}
