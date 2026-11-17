/*
 * Copyright (c) 2026 Xena Studios
 * SPDX-License-Identifier: GPL-3.0-only
 */
package co.xenastudios.neoskript.paper;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;

import co.xenastudios.neoskript.compiler.CompiledScripts;
import co.xenastudios.neoskript.compiler.Library;
import co.xenastudios.neoskript.compiler.ScriptCompiler;
import co.xenastudios.neoskript.frontend.parse.ParsedScript;
import co.xenastudios.neoskript.frontend.parse.ScriptLoader;
import co.xenastudios.neoskript.frontend.source.SourceFile;
import co.xenastudios.neoskript.runtime.SimpleTriggerContext;
import co.xenastudios.neoskript.runtime.variables.VariableStore;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.List;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.junit.jupiter.api.Test;

/** {@code set event-<type>} for Skript's event values with a changer: converted, and never set to nothing. */
class EventValueChangerTest {

    /** A value that converts to an item stack, as an item type does on a server. */
    public static final class Convertible {}

    public static final class ProbeEvent extends org.bukkit.event.Event {
        private static final org.bukkit.event.HandlerList HANDLERS = new org.bukkit.event.HandlerList();

        @Override
        public org.bukkit.event.HandlerList getHandlers() {
            return HANDLERS;
        }

        public static org.bukkit.event.HandlerList getHandlerList() {
            return HANDLERS;
        }
    }

    private final List<Object> set = new ArrayList<>();
    private final VariableStore globals = new VariableStore();
    private final World world = stub(World.class);
    private final Location there = new Location(world, 7, 7, 7);
    private final Player player = (Player) Proxy.newProxyInstance(Player.class.getClassLoader(),
            new Class<?>[] {Player.class}, (proxy, method, args) -> switch (method.getName()) {
                case "getLocation" -> there.clone();
                case "hashCode" -> System.identityHashCode(proxy);
                case "equals" -> proxy == args[0];
                default -> method.getReturnType() == boolean.class ? false : null;
            });

    @SuppressWarnings("unchecked")
    private static <T> T stub(Class<T> type) {
        return (T) Proxy.newProxyInstance(type.getClassLoader(), new Class<?>[] {type}, (proxy, method, args) ->
                switch (method.getName()) {
                    case "hashCode" -> System.identityHashCode(proxy);
                    case "equals" -> proxy == args[0];
                    default -> method.getReturnType() == boolean.class ? false : null;
                });
    }

    private void run(String script, Object event) {
        Library library = new Library();
        StandardLibrary.register(library);
        library.event("ProbeEvt", List.of(ProbeEvent.class), info -> (context, args) -> true, "probe");
        library.eventValue(ProbeEvent.class, "itemstack", event1 -> null);
        library.eventValueChanger(ProbeEvent.class, "itemstack", 0, (event1, item) -> set.add(item));
        library.type("convertible", "convertible", Convertible.class);
        library.converter(Convertible.class, org.bukkit.inventory.ItemStack.class,
                value -> stubItem());
        List<ParsedScript> parsed = new ScriptLoader(library.registry()).load(List.of(new SourceFile("test.sk", script)));
        assertEquals(List.of(), parsed.getFirst().diagnostics(), "parse problems");
        CompiledScripts compiled = new ScriptCompiler(library, globals).compile(parsed);
        assertEquals(List.of(), compiled.diagnostics(), "compile problems");
        compiled.linkFunctions();
        compiled.triggers().forEach(t -> t.trigger().execute(new SimpleTriggerContext(event)));
    }

    private static org.bukkit.inventory.ItemStack stubItem() {
        try {
            var field = sun.misc.Unsafe.class.getDeclaredField("theUnsafe");
            field.setAccessible(true);
            return (org.bukkit.inventory.ItemStack) ((sun.misc.Unsafe) field.get(null))
                    .allocateInstance(org.bukkit.inventory.ItemStack.class);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException(e);
        }
    }

    private PlayerTeleportEvent teleport() {
        return new PlayerTeleportEvent(player, new Location(world, 0, 0, 0), new Location(world, 1, 1, 1));
    }

    @Test
    void aTeleportsDestinationAndStartAreSet() {
        PlayerTeleportEvent to = teleport();
        run("on teleport:\n\tset event-location to location of player\n", to);
        assertEquals(there, to.getTo());
        PlayerTeleportEvent from = teleport();
        run("on teleport:\n\tset past event-location to location of player\n", from);
        assertEquals(there, from.getFrom());
        assertEquals(new Location(world, 1, 1, 1), from.getTo());
    }

    @Test
    void nothingOrTheWrongTypeSetsNothing() {
        // As Skript's change effect: with no value of the type, the event value's changer isn't called.
        PlayerTeleportEvent none = teleport();
        run("on teleport:\n\tset event-location to {_none}\n", none);
        assertEquals(new Location(world, 1, 1, 1), none.getTo());
        PlayerTeleportEvent text = teleport();
        run("on teleport:\n\tset {_x} to \"hello\"\n\tset event-location to {_x}\n", text);
        assertEquals(new Location(world, 1, 1, 1), text.getTo());
        run("on probe:\n\tset event-item to {_none}\n", new ProbeEvent());
        assertEquals(List.of(), set);
    }

    @Test
    void theValueIsConvertedToTheEventValuesClass() {
        globals.set("thing", new Convertible());
        run("on probe:\n\tset event-item to {thing}\n", new ProbeEvent());
        assertEquals(1, set.size());
        assertSame(org.bukkit.inventory.ItemStack.class, set.getFirst().getClass());
    }
}
