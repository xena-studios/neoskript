/*
 * Copyright (c) 2026 Xena Studios
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package co.xenastudios.neoskript.paper;

import static org.junit.jupiter.api.Assertions.assertEquals;

import co.xenastudios.neoskript.compiler.CompiledScripts;
import co.xenastudios.neoskript.compiler.Library;
import co.xenastudios.neoskript.compiler.ScriptCompiler;
import co.xenastudios.neoskript.frontend.parse.ParsedScript;
import co.xenastudios.neoskript.frontend.parse.ScriptLoader;
import co.xenastudios.neoskript.frontend.source.SourceFile;
import co.xenastudios.neoskript.runtime.SimpleTriggerContext;
import co.xenastudios.neoskript.runtime.Values;
import co.xenastudios.neoskript.runtime.variables.VariableStore;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.bukkit.GameMode;
import org.bukkit.entity.Player;
import org.bukkit.event.player.PlayerGameModeChangeEvent;
import org.bukkit.event.player.PlayerItemHeldEvent;
import org.bukkit.event.player.PlayerLevelChangeEvent;
import org.bukkit.inventory.PlayerInventory;
import org.junit.jupiter.api.Test;

/** Library expressions with a past and future state, as Skript's {@code setTime}: {@code past level of player}. */
class TimeStatesTest {

    private final List<String> said = new ArrayList<>();

    private CompiledScripts compile(String script, List<String> problems) {
        Library library = new Library();
        StandardLibrary.register(library);
        library.effect("TestSay", info -> (context, args) -> said.add(Values.text(args[0])), "say %objects%");
        List<ParsedScript> parsed = new ScriptLoader(library.registry()).load(List.of(new SourceFile("test.sk", script)));
        parsed.getFirst().diagnostics().forEach(d -> problems.add(d.message()));
        CompiledScripts compiled = new ScriptCompiler(library, new VariableStore()).compile(parsed);
        compiled.diagnostics().forEach(d -> problems.add(d.message()));
        return compiled;
    }

    private List<String> errors(String script) {
        List<String> problems = new ArrayList<>();
        compile(script, problems);
        return problems;
    }

    private List<String> run(String script, Object event) {
        List<String> problems = new ArrayList<>();
        CompiledScripts compiled = compile(script, problems);
        assertEquals(List.of(), problems, "problems");
        compiled.linkFunctions();
        compiled.triggers().forEach(t -> t.trigger().execute(new SimpleTriggerContext(event)));
        return said;
    }

    /** A stand-in for an interface: what's set is what's got, else {@code defaults}' value, else nothing. */
    @SuppressWarnings("unchecked")
    private static <T> T fake(Class<T> type, Map<String, Object> defaults) {
        Map<String, Object> values = new HashMap<>(defaults);
        return (T) Proxy.newProxyInstance(type.getClassLoader(), new Class<?>[] {type}, (proxy, method, args) -> {
            String name = method.getName();
            if (name.startsWith("set") && args != null && args.length == 1) {
                values.put(name.substring(3), args[0]);
                return null;
            }
            String key = name.startsWith("get") ? name.substring(3) : name;
            if (values.containsKey(key)) {
                return values.get(key);
            }
            return switch (name) {
                case "hashCode" -> System.identityHashCode(proxy);
                case "equals" -> proxy == args[0];
                case "toString" -> type.getSimpleName();
                default -> method.getReturnType() == boolean.class ? false
                        : method.getReturnType().isPrimitive() && method.getReturnType() != void.class ? 0 : null;
            };
        });
    }

    @Test
    void levelBeforeAndAfterALevelChange() {
        Player player = fake(Player.class, Map.of("Level", 3));
        assertEquals(List.of("3", "5", "5", "was 3", "will be 5"), run("""
                on level change:
                \tsay past level of player
                \tsay level of player
                \tsay the future level of player
                \tif player's level was 3:
                \t\tsay "was 3"
                \tif player's level will be 5:
                \t\tsay "will be 5"
                """, new PlayerLevelChangeEvent(player, 3, 5)));
    }

    @Test
    void gameModeBeforeAndAfterAChange() {
        Player player = fake(Player.class, Map.of("GameMode", GameMode.SURVIVAL));
        PlayerGameModeChangeEvent event = new PlayerGameModeChangeEvent(player, GameMode.CREATIVE,
                PlayerGameModeChangeEvent.Cause.PLUGIN, null);
        assertEquals(List.of("survival", "creative", "creative"), run("""
                on gamemode change:
                \tsay the former gamemode of player
                \tsay gamemode of player
                \tsay player's gamemode after the event
                """, event));
    }

    @Test
    void toolBeforeAndAfterSwitchingSlots() {
        PlayerInventory inventory = fake(PlayerInventory.class, Map.of("HeldItemSlot", 2));
        Player player = fake(Player.class, Map.of("Inventory", inventory));
        assertEquals(List.of("2", "7", "7", "2", "7", "2"), run("""
                on tool change:
                \tsay index of past tool
                \tsay index of tool
                \tsay index of future tool of player
                \tsay index of past hotbar slot
                \tsay index of future hotbar slot
                \tsay index of hotbar slot
                """, new PlayerItemHeldEvent(player, 2, 7)));
    }

    @Test
    void whereTimeStatesAreRefused() {
        assertEquals(List.of(
                "level of {_p} does not have a past state",
                "gamemode of player does not have a future state",
                "target of entity does not have a past state",
                "damage cause does not have a future state",
                "currently selected hotbar slot does not have a past state",
                "Cannot use time states after the event has already passed",
                "past level of player can't be set to anything",
                "Changing the past or future level in a level change event has no effect."), errors("""
                on level change:
                \tsay past level of {_p}
                on join:
                \tsay future gamemode of player
                on target:
                \tsay past target of entity
                on damage:
                \tsay past damage cause
                \tsay future damage cause
                on tool change:
                \tsay past currently selected hotbar slot
                on gamemode change:
                \twait 1 tick
                \tsay past gamemode of player
                on level change:
                \tset past level of player to {_x}
                on level change:
                \tset future level of player to {_x}
                """));
        assertEquals(List.of(), errors("""
                on target:
                \tsay future target of entity
                on quit:
                \tsay past quit reason
                on join:
                \tsay past targeted block of player
                \tsay past biome of player
                on heal:
                \tsay past heal amount
                on teleport:
                \tsay past world of player
                \tsay future world of player
                on world spawn change:
                \tsay past spawn
                """));
        // As in Skript, 'on death' is any entity's death, not a player's, so the level has no future state there.
        assertEquals(List.of("level of player does not have a future state"), errors("""
                on death:
                \tset future level of player to {_x}
                """));
    }
}
