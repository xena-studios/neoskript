/*
 * Copyright (c) 2026 Xena Studios
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package co.xenastudios.neoskript.paper;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import co.xenastudios.neoskript.compiler.CompiledScripts;
import co.xenastudios.neoskript.compiler.Library;
import co.xenastudios.neoskript.compiler.ScriptCompiler;
import co.xenastudios.neoskript.frontend.parse.ParsedScript;
import co.xenastudios.neoskript.frontend.parse.ScriptLoader;
import co.xenastudios.neoskript.frontend.registry.SyntaxKind;
import co.xenastudios.neoskript.frontend.source.Diagnostic;
import co.xenastudios.neoskript.frontend.source.SourceFile;
import co.xenastudios.neoskript.runtime.SimpleTriggerContext;
import co.xenastudios.neoskript.runtime.Values;
import co.xenastudios.neoskript.runtime.variables.VariableStore;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import net.milkbowl.vault.economy.Economy;
import org.bukkit.Location;
import org.bukkit.OfflinePlayer;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

/** Skript's plugin hooks, with a fake Vault economy and a fake regions plugin, as the test servers have neither. */
class HooksTest {

    private final List<String> said = new ArrayList<>();
    private final Map<Object, Double> balances = new HashMap<>();

    private final OfflinePlayer notch = player("Notch");

    @AfterEach
    void unhook() {
        VaultHook.hook(null, null, null);
        Regions.PLUGINS.clear();
    }

    private static OfflinePlayer player(String name) {
        return (OfflinePlayer) Proxy.newProxyInstance(HooksTest.class.getClassLoader(), new Class<?>[] {OfflinePlayer.class},
                (proxy, method, args) -> switch (method.getName()) {
                    case "getName", "toString" -> name;
                    case "hashCode" -> name.hashCode();
                    case "equals" -> proxy == args[0];
                    default -> null;
                });
    }

    /** An economy of dollars, written '$' for one, which keeps balances in a map. */
    private Economy economy() {
        return (Economy) Proxy.newProxyInstance(HooksTest.class.getClassLoader(), new Class<?>[] {Economy.class},
                (proxy, method, args) -> switch (method.getName()) {
                    case "currencyNamePlural" -> "dollars";
                    case "currencyNameSingular" -> "$";
                    case "format" -> String.format(java.util.Locale.ROOT, "$%.2f", (Double) args[0]);
                    case "getBalance" -> balances.getOrDefault(args[0], 0.0);
                    case "depositPlayer" -> {
                        balances.merge(args[0], (Double) args[1], Double::sum);
                        yield null;
                    }
                    case "withdrawPlayer" -> {
                        balances.merge(args[0], -(Double) args[1], Double::sum);
                        yield null;
                    }
                    case "hashCode" -> 0;
                    case "equals" -> proxy == args[0];
                    default -> null;
                });
    }

    /** Runs the script's triggers with these hooks registered, returning what they said, or its errors. */
    private List<String> run(Consumer<Library> hooks, String script) {
        return run(hooks, script, true);
    }

    private List<String> run(Consumer<Library> hooks, String script, boolean execute) {
        Library library = new Library();
        StandardLibrary.register(library, registrar -> hooks.accept(library));
        library.effect("TestSay", info -> (context, args) -> said.add(Values.text(args[0])), "say %objects%");
        library.expression("TestPlayer", "offlineplayer", info -> (context, args) -> notch, "test player");
        library.expression("TestLocation", "location", info -> (context, args) -> new Location(null, 1, 2, 3),
                "test location");
        List<ParsedScript> parsed = new ScriptLoader(library.registry()).load(List.of(new SourceFile("test.sk", script)));
        List<String> errors = new ArrayList<>(parsed.getFirst().diagnostics().stream().map(Diagnostic::message).toList());
        if (!errors.isEmpty()) {
            return errors;
        }
        CompiledScripts compiled = new ScriptCompiler(library, new VariableStore()).compile(parsed);
        if (!compiled.diagnostics().isEmpty()) {
            return compiled.diagnostics().stream().map(Diagnostic::message).toList();
        }
        compiled.linkFunctions();
        if (execute) {
            compiled.triggers().forEach(t -> t.trigger().execute(new SimpleTriggerContext(null)));
        }
        return said;
    }

    private void vault(Library library) {
        VaultHook.hook(economy(), null, null);
        VaultHook.register(library);
    }

    @Test
    void moneyParsesWithTheCurrencysNames() {
        VaultHook.hook(economy(), null, null);
        assertEquals(5.0, Money.parse("5 dollars").getAmount());
        assertEquals(5.0, Money.parse("5dollars").getAmount());
        assertEquals(2.5, Money.parse("$2.5").getAmount());
        assertEquals(10.0, Money.parse("10 $").getAmount());
        assertEquals(-3.0, Money.parse("DOLLARS -3").getAmount());
        assertNull(Money.parse("5 euros"));
        assertNull(Money.parse("dollars"));
        assertNull(Money.parse("5_000 dollars"));
        assertEquals("$5.00", Money.parse("5 dollars").toString());
        // Money is a number wherever one goes, as Skript converts it to one.
        assertEquals(2.5, co.xenastudios.neoskript.runtime.Arithmetic.divide(new Money(20), 8L));
        assertTrue(co.xenastudios.neoskript.runtime.Comparison.order(new Money(20), 15L) > 0);
        assertTrue(co.xenastudios.neoskript.runtime.Comparison.equal(new Money(20), 20L));
        VaultHook.hook(null, null, null);
        assertNull(Money.parse("5 dollars"));
    }

    @Test
    void balancesChangeAsInSkript() {
        // Words that might be items or entities ('say 5 dollars', '20$') are looked up as those too, which needs a server.
        assertEquals(List.of("$0.00", "$10.00", "$7.50", "$100.00", "$0.00", "true", "true", "$20.00", "$22.00"),
                run(this::vault, """
                on load:
                \tsay balance of test player
                \tadd 10 dollars to test player's balance
                \tsay test player's money
                \tremove 2.5 from the bank account of test player
                \tsay test player's account
                \tset test player's balance to 100
                \tsay test player's balance
                \tclear test player's balance
                \tsay test player's balance
                \tadd 20 to test player's balance
                \tsay whether test player's balance > 15
                \tsay whether test player's balance is 20
                \tset {_m} to test player's balance
                \tsay {_m}
                \tadd 2 to test player's balance
                \tsay test player's balance
                """));
        assertEquals(22.0, balances.get(notch));
    }

    @Test
    void vaultSyntaxOnlyExistsWhenHooked() {
        Library library = new Library();
        StandardLibrary.register(library);
        assertTrue(library.registry().syntaxes(SyntaxKind.EXPRESSION).stream()
                .noneMatch(syntax -> syntax.id().equals("ExprBalance") || syntax.id().equals("ExprRegionsAt")));
    }

    @Test
    void vaultElementsWithoutAProviderDontParse() {
        assertEquals(List.of("The money expression requires Vault and an economy plugin that supports Vault"),
                run(library -> {
                    VaultHook.hook(null, null, null);
                    VaultHook.register(library);
                }, """
                on load:
                \tsay balance of test player
                """));
    }

    /** A regions plugin with one region, 'spawn', the box from 0, 0, 0 to 9, 9, 9, which Notch owns. */
    private static final class FakeRegions implements Regions.RegionsPlugin {
        final Regions.Region spawn = new Regions.Region() {
            @Override
            boolean contains(Location location) {
                return location.getX() >= 0 && location.getX() < 10 && location.getY() >= 0 && location.getY() < 10
                        && location.getZ() >= 0 && location.getZ() < 10;
            }

            @Override
            boolean isMember(OfflinePlayer player) {
                return isOwner(player);
            }

            @Override
            Collection<OfflinePlayer> getMembers() {
                return getOwners();
            }

            @Override
            boolean isOwner(OfflinePlayer player) {
                return "Notch".equals(player.getName());
            }

            @Override
            Collection<OfflinePlayer> getOwners() {
                return List.of();
            }

            @Override
            Iterator<Block> getBlocks() {
                return Collections.emptyIterator();
            }

            @Override
            public String toString() {
                return "spawn in world world";
            }

            @Override
            Regions.RegionsPlugin getPlugin() {
                return FakeRegions.this;
            }

            @Override
            public boolean equals(Object other) {
                return other == this;
            }

            @Override
            public int hashCode() {
                return 1;
            }
        };

        @Override
        public String getName() {
            return "Fake";
        }

        @Override
        public boolean canBuild(Player player, Location location) {
            return !spawn.contains(location);
        }

        @Override
        public Collection<? extends Regions.Region> getRegionsAt(Location location) {
            return spawn.contains(location) ? List.of(spawn) : List.of();
        }

        @Override
        public Regions.Region getRegion(World world, String name) {
            return name.equals("spawn") ? spawn : null;
        }

        @Override
        public boolean hasMultipleOwners() {
            return false;
        }
    }

    private void regions(Library library) {
        FakeRegions fake = new FakeRegions();
        Regions.PLUGINS.add(fake);
        Regions.register(library);
        library.expression("TestRegion", "region", info -> (context, args) -> fake.spawn, "test region");
    }

    @Test
    void regionConditionsAndExpressions() {
        assertEquals(List.of("true", "false", "true", "true", "false", "false", "spawn in world world", "1"),
                run(this::regions, """
                on load:
                \tsay whether test region contains at test location
                \tsay whether test region doesn't contain at test location
                \tsay whether test location is in the region test region
                \tsay whether test player is a member of test region
                \tsay whether test player isn't the owner of test region
                \tsay whether test location isn't contained in test region
                \tsay regions at test location
                \tsay size of regions at test location
                """));
    }

    @Test
    void regionEventsParse() {
        assertEquals(List.of(), run(this::regions, """
                on region enter:
                \tsay event-region
                on exit of a region:
                \tsay region
                on entering of region "spawn":
                \tsay player
                """, false));
    }
}
