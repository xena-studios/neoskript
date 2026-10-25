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
import org.bukkit.block.Block;
import org.bukkit.block.sign.Side;
import org.bukkit.entity.FishHook;
import org.bukkit.entity.Player;
import org.bukkit.event.block.SignChangeEvent;
import org.bukkit.event.player.PlayerFishEvent;
import org.junit.jupiter.api.Test;

/** Syntax of events, checked where it can be without a server: what it reads and changes, and where it's refused. */
class EventSyntaxTest {

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

    /** The errors compiling {@code script} gives. */
    private List<String> errors(String script) {
        List<String> problems = new ArrayList<>();
        compile(script, problems);
        return problems;
    }

    /** What {@code script}'s triggers say, run for {@code event}. */
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
    void fishingHooksAreReadAndChanged() {
        FishHook hook = fake(FishHook.class, Map.of("MinWaitTime", 100, "MaxWaitTime", 600, "TimeUntilBite", 40,
                "ApplyLure", true, "MinLureAngle", 0f, "MaxLureAngle", 360f));
        PlayerFishEvent event = new PlayerFishEvent(fake(Player.class, Map.of()), null, hook, PlayerFishEvent.State.FISHING);
        assertEquals(List.of("5 seconds", "20 seconds", "30 seconds", "4 seconds", "0.05 seconds", "true", "true",
                "false", "360", "10", "0", "<none>"), run("""
                on fishing line cast:
                \tsay min fishing wait time
                \tset max fishing waiting time to 20 seconds
                \tsay max fish wait time
                \treset maximum fishing wait time
                \tsay maximum fishing wait time
                \tadd 2 seconds to fishing bite time
                \tsay fishing bite time
                \tset fishing bite time to 0 seconds
                \tsay fishing bite time
                \tsay whether lure enchantment bonus is applied
                \tremove lure enchantment bonus
                \tsay whether lure enchantment bonus isn't applied
                \tsay whether lure enchantment bonus is active
                \tset min fishing approach angle to 400
                \tsay min fishing approach angle
                \tremove 350 from min fishing approach angle
                \tsay min fishing approach angle
                \treset min fishing approach angle
                \tsay min fishing approach angle
                \tsay hooked entity
                """, event));
        assertEquals(360f, hook.getMaxLureAngle());
    }

    @Test
    void changedSignSides() {
        SignChangeEvent event = new SignChangeEvent(fake(Block.class, Map.of()), fake(Player.class, Map.of()),
                new String[] {"", "", "", ""}, Side.BACK);
        assertEquals(List.of("true", "true", "false", "true"), run("""
                on sign change:
                \tsay whether the back side was changed
                \tsay whether the front side wasn't changed
                \tsay whether the changed side is the front
                \tsay whether changed side is back
                """, event));
    }

    @Test
    void eventSyntaxIsOnlyForItsEvents() {
        List<String> lines = List.of(
                "whether the brewing stand will consume the fuel", "make the brewing stand consume its fuel",
                "the brewing results", "the displayed enchanting cost", "the enchantment bonus", "the enchantment hint",
                "the enchantment offers", "enchant offer 1", "the applied enchantments", "whether lure enchantment bonus is applied",
                "apply lure enchantment bonus", "min fishing approach angle", "fishing bite time", "the fishing hook",
                "hooked entity", "max fishing wait time", "smelted item", "extracted item", "smelting item", "burned fuel",
                "the thrown egg", "portal blocks", "the hover list", "whether the back side was changed", "the executor");
        StringBuilder script = new StringBuilder();
        for (String line : lines) {
            script.append("on join:\n\t").append(line.startsWith("make ") || line.startsWith("apply ") ? "" : "say ")
                    .append(line).append('\n');
        }
        assertEquals(List.of(
                "'the brewing stand will consume the fuel' can only be used in the brewing fuel event",
                "'make the brewing stand consume the fuel' can only be used in the brewing fuel event",
                "'the brewing results' can only be used in the brewing complete event",
                "'the displayed enchanting cost' can only be used in the enchant event",
                "'the enchantment bonus' can only be used in the enchant prepare event",
                "'the enchantment hint' can only be used in the enchant event",
                "'the enchantment offers' can only be used in the enchant prepare event",
                "'enchantment offer' can only be used in the enchant prepare event",
                "'the applied enchantments' can only be used in the enchant event",
                "The 'lure enchantment' condition can only be used in a fishing event.",
                "The 'fishing hook lure' effect can only be used in a fishing event.",
                "The 'fishing approach angle' expression can only be used in a fishing event.",
                "The 'fishing bite time' expression can only be used in a fishing event.",
                "There's no entity in a join event",
                "The 'hooked entity' expression can only be used in the fishing event.",
                "The 'fishing wait time' expression can only be used in a fishing event.",
                "'smelted item' can only be used in a smelting event.",
                "'extracted item' can only be used in a furnace extract event.",
                "'smelting item' can only be used in a start smelting event",
                "'burned fuel' can only be used in a fuel burning event.",
                "There's no entity in a join event",
                "'the portal blocks' can only be used in the portal create event",
                "'the hover list' can only be used in the server list ping event",
                "'the back side was changed' can only be used in the sign change event",
                "'the command executor' can only be used in the unknown command execution event"), errors(script.toString()));
    }

    @Test
    void anEventValueTheEventHasNotIsReadAnotherWay() {
        // As in Skript: 'slots' isn't the event's slot where there's none, but the inventory's slots.
        assertEquals(List.of(), errors("on load:\n\tset {_a} to amount of slots of {_inv}\n"));
        // Where nothing else parses, it's still the error.
        assertEquals(List.of("There's no slot in an on join event."), errors("on join:\n\tset {_a} to event-slot\n"));
        assertEquals(List.of(), errors("on join:\n\tset {_a} to event-player\n"));
    }

    @Test
    void eventSyntaxParsesInItsEvents() {
        assertEquals(List.of(), errors("""
                on brewing fuel consumption:
                \tsay whether the brewing stand won't consume the fuel
                \tprevent the brewing stand from consuming the fuel
                \tset whether the brewing stand will consume the fuel to true
                on brew:
                \tset the brewing results to {_x::*}
                on brewing start:
                \tsay remaining brewing time
                \tadd 2 seconds to the brewing time
                on enchant:
                \tsay the applied enchantments, the displayed enchanting cost and the enchantment hint
                \tremove 1 from the exp cost
                on enchant prepare:
                \tsay enchantment bonus, enchant offers 1 and 2 and the 3rd enchantment offer
                \tsay the cost of enchantment offer 1
                \tset the cost of the 1st enchantment offer to 10
                \tdelete all of the enchantment offers
                on fishing line cast:
                \tset hooked entity to {_x}
                \tsay the fishing hook
                on smelt:
                \tset smelted item to {_x}
                on fuel burn:
                \tsay burned fuel
                on furnace extract:
                \tsay extracted items
                on smelting start:
                \tsay smelting item
                on server list ping:
                \tclear the hover list
                \tadd "hi" to the hover list
                on portal create:
                \tsay blocks of the portal
                on egg throw:
                \tsay the thrown egg
                on unknown command:
                \tsay the executor
                command /cmd:
                \ttrigger:
                \t\tsay the command's executor
                on load:
                \tsay holders of {_inv}, rows of {_inv} and {_inv}'s amount of slots
                \tsay anvil text input of {_inv}
                \tsay the max repair cost of {_inv}
                \tset {_inv}'s anvil repair cost to 3
                \tsay maximum enchantment level of {_e}
                \tsay {_p}'s experience pickup cooldown
                \tinterrupt the usage of {_p}'s active item
                \tlaunch flickering trailing {_type} firework colored {_c} fading to {_c} at {_l} timed 2
                \tsay the last launched firework
                """));
    }

    @Test
    void comparisonsBeforeAndAfterTheEvent() {
        assertEquals(List.of(), errors("""
                on place:
                \tif event-block was {_b}:
                \t\tsay "x"
                \tif event-block wasn't between 1 and 2:
                \t\tsay "x"
                """));
        assertEquals(List.of("{_x} does not have a past state",
                "Cannot use time states after the event has already passed"), errors("""
                on place:
                \tif {_x} was 1:
                \t\tsay "x"
                on place:
                \twait 1 tick
                \tif event-block was {_b}:
                \t\tsay "x"
                """));
    }

    @Test
    void furnaceSlotsDefaultToTheEventBlock() {
        assertEquals(List.of(), errors("""
                on smelt:
                \tset {_x} to fuel slot
                on break:
                \tset {_x} to the ore slot
                """));
        // As in Skript, a player is a block as an inventory holder (if it were a block state), but a world isn't.
        assertEquals(List.of(), errors("on join:\n\tset {_x} to result slot\n"));
        assertEquals(1, errors("on weather change:\n\tset {_x} to result slot\n").size());
    }
}
