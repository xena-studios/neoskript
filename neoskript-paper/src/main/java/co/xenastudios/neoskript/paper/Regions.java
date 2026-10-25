/*
 * Copyright (c) 2026 Xena Studios
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package co.xenastudios.neoskript.paper;

import co.xenastudios.neoskript.api.element.Condition;
import co.xenastudios.neoskript.api.element.ElementInfo;
import co.xenastudios.neoskript.api.element.SyntaxError;
import co.xenastudios.neoskript.api.element.Warnings;
import co.xenastudios.neoskript.api.registration.Registrar;
import co.xenastudios.neoskript.runtime.Values;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Predicate;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.OfflinePlayer;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.Cancellable;
import org.bukkit.event.Event;
import org.bukkit.event.EventPriority;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerPortalEvent;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.bukkit.plugin.Plugin;

/**
 * Skript's region hooks ({@code ch.njol.skript.hooks.regions}): the {@code region} type of whichever regions
 * plugins are hooked (WorldGuard, GriefPrevention), and the syntax for them. Nothing here touches a regions
 * plugin's own classes; each hook ({@link WorldGuardHook}, {@link GriefPreventionHook}) does that.
 */
public final class Regions {

    /** What Skript warns of when it hooks into a regions plugin, as its {@code RegionsPlugin.init}. */
    static String hookedWarning(String plugin) {
        return "Loaded regions hook for " + plugin + "."
                + " Please note that Skript's region hooks are deprecated and will be removed in a future release."
                + " For WorldGuard support, we recommend using skript-worldguard: https://github.com/SkriptLang/skript-worldguard";
    }

    private Regions() {}

    /** What Skript warns of wherever its region syntax is used, as its {@code RegionsPlugin.DEPRECATION_MESSAGE}. */
    static final String DEPRECATION_MESSAGE = "Skript's region syntaxes are deprecated and will be removed in a future"
            + " release. For WorldGuard support, we recommend using skript-worldguard:"
            + " https://github.com/SkriptLang/skript-worldguard";

    /**
     * Region syntax being set up: Skript's errors for a region named in quotes at one of {@code regionArguments}
     * that doesn't exist, as its region parser gives them while loading, then its deprecation warning.
     */
    private static void used(ElementInfo info, int... regionArguments) {
        for (int index : regionArguments) {
            String name = info.argumentLiteral(index);
            if (name != null && "string".equals(info.argumentType(index))) {
                check(name);
            }
        }
        Warnings.deprecated(DEPRECATION_MESSAGE);
    }

    /** Skript's {@code Region.parse} with errors: the region must be in exactly one world. */
    private static void check(String name) {
        if (PLUGINS.isEmpty() || Bukkit.getServer() == null) {
            return;
        }
        int found = 0;
        for (World world : Bukkit.getWorlds()) {
            if (getRegion(world, name) != null) {
                found++;
            }
        }
        if (found > 1) {
            throw new SyntaxError("Multiple regions with the name '" + name + "' exist");
        }
        if (found == 0) {
            throw new SyntaxError("Region '" + name + "' could not be found. Ensure that the region name is correct, in"
                    + " the proper world, and that the region data for that world is loaded.");
        }
    }

    /** A regions plugin NeoSkript is hooked into, as Skript's {@code RegionsPlugin}. */
    interface RegionsPlugin {
        /** The plugin's exact name. */
        String getName();

        boolean canBuild(Player player, Location location);

        Collection<? extends Region> getRegionsAt(Location location);

        /** The region of this name in {@code world}, or {@code null}. */
        Region getRegion(World world, String name);

        boolean hasMultipleOwners();
    }

    /** A region of a regions plugin, as Skript's {@code Region}. */
    abstract static class Region {
        abstract boolean contains(Location location);

        abstract boolean isMember(OfflinePlayer player);

        abstract Collection<OfflinePlayer> getMembers();

        abstract boolean isOwner(OfflinePlayer player);

        abstract Collection<OfflinePlayer> getOwners();

        abstract Iterator<Block> getBlocks();

        @Override
        public abstract String toString();

        abstract RegionsPlugin getPlugin();

        @Override
        public abstract boolean equals(Object other);

        @Override
        public abstract int hashCode();
    }

    /** The hooked regions plugins. */
    static final List<RegionsPlugin> PLUGINS = new CopyOnWriteArrayList<>();

    /** Whether the player can build at the location in every hooked plugin. */
    static boolean canBuild(Player player, Location location) {
        for (RegionsPlugin plugin : PLUGINS) {
            if (!plugin.canBuild(player, location)) {
                return false;
            }
        }
        return true;
    }

    /** The regions at a location in every hooked plugin; a plugin that fails is unhooked, as in Skript. */
    static Set<Region> getRegionsAt(Location location) {
        Set<Region> regions = new HashSet<>();
        for (RegionsPlugin plugin : PLUGINS) {
            try {
                regions.addAll(plugin.getRegionsAt(location));
            } catch (Throwable e) { // Unstable WorldGuard API
                Bukkit.getLogger().log(java.util.logging.Level.SEVERE,
                        plugin.getName() + " hook crashed and was removed to prevent future errors.", e);
                PLUGINS.remove(plugin);
            }
        }
        return regions;
    }

    /** The region of this name in a world, by the first hooked plugin only, as Skript's {@code getRegion}. */
    static Region getRegion(World world, String name) {
        return PLUGINS.isEmpty() ? null : PLUGINS.getFirst().getRegion(world, name);
    }

    static boolean hasMultipleOwners() {
        for (RegionsPlugin plugin : PLUGINS) {
            if (plugin.hasMultipleOwners()) {
                return true;
            }
        }
        return false;
    }

    /** The region of this name in any world, or {@code null} if there's none or several, as Skript's {@code Region.parse}. */
    static Region parse(String name) {
        Region found = null;
        for (World world : Bukkit.getWorlds()) {
            Region region = getRegion(world, name);
            if (region == null) {
                continue;
            }
            if (found != null) {
                return null;
            }
            found = region;
        }
        return found;
    }

    /** A player crossing a region's border, which Skript fires itself as its {@code RegionBorderEvent}. */
    public static final class RegionBorderEvent extends Event implements Cancellable {
        private static final HandlerList HANDLERS = new HandlerList();
        private final Region region;
        private final Player player;
        private final boolean enter;
        private boolean cancelled;

        RegionBorderEvent(Region region, Player player, boolean enter) {
            this.region = region;
            this.player = player;
            this.enter = enter;
        }

        public boolean isEntering() {
            return enter;
        }

        Region getRegion() {
            return region;
        }

        public Player getPlayer() {
            return player;
        }

        @Override
        public boolean isCancelled() {
            return cancelled;
        }

        @Override
        public void setCancelled(boolean cancel) {
            cancelled = cancel;
        }

        @Override
        public HandlerList getHandlers() {
            return HANDLERS;
        }

        public static HandlerList getHandlerList() {
            return HANDLERS;
        }
    }

    /** Registers the region type and syntax, once a regions plugin is hooked. */
    static void register(Registrar registrar) {
        registrar.type("region", "region", Region.class);
        registrar.text(Region.class, Region::toString);
        registrar.converter(String.class, Region.class, Regions::parse);
        registrar.parser("region", Regions::parse);

        registrar.condition("CondCanBuild", info -> {
            used(info);
            boolean negated = info.pattern() == 1;
            boolean playersAnd = info.and(0);
            boolean locationsAnd = info.and(2);
            return (context, args) -> {
                List<Location> locations = Direction.combine(args[1], args[2]);
                return Condition.check(Values.array(args[0]), playersAnd, player -> player instanceof Player p
                        && check(locations.toArray(), locationsAnd, negated, location -> canBuild(p, (Location) location)));
            };
        }, "%players% (can|(is|are) allowed to) build %directions% %locations%",
                "%players% (can('t|not)|(is|are)(n't| not) allowed to) build %directions% %locations%");

        registrar.condition("CondIsMember", info -> {
            used(info, 1);
            boolean negated = info.pattern() == 1;
            boolean owner = info.mark() == 1;
            boolean playersAnd = info.and(0);
            boolean regionsAnd = info.and(1);
            return (context, args) -> Condition.check(Values.array(args[0]), playersAnd, player ->
                    player instanceof OfflinePlayer p && check(Values.array(args[1]), regionsAnd, negated, region ->
                            region instanceof Region r && (owner ? r.isOwner(p) : r.isMember(p))));
        }, "%offlineplayers% (is|are) (0¦[a] member|1¦[(the|an)] owner) of [[the] region] %regions%",
                "%offlineplayers% (is|are)(n't| not) (0¦[a] member|1¦[(the|an)] owner) of [[the] region] %regions%");

        registrar.condition("CondRegionContains", info -> {
            boolean negated = info.pattern() >= 2;
            // 'X contains Y' has a direction; 'Y is in X' doesn't.
            boolean directed = info.pattern() % 2 == 0;
            int regionsAt = directed ? 0 : 1;
            used(info, regionsAt);
            boolean regionsAnd = info.and(regionsAt);
            boolean locationsAnd = info.and(directed ? 2 : 0);
            return (context, args) -> {
                Object[] locations = directed ? Direction.combine(args[1], args[2]).toArray() : Values.array(args[0]);
                return Condition.check(Values.array(args[regionsAt]), regionsAnd, region -> region instanceof Region r
                        && check(locations, locationsAnd, negated, location -> location instanceof Location l
                                && r.contains(l)));
            };
        }, "[[the] region] %regions% contain[s] %directions% %locations%",
                "%locations% (is|are) ([contained] in|part of) [[the] region] %regions%",
                "[[the] region] %regions% (do|does)(n't| not) contain %directions% %locations%",
                "%locations% (is|are)(n't| not) (contained in|part of) [[the] region] %regions%");

        registrar.expression("ExprBlocksInRegion", "block", info -> {
            used(info, 0);
            return (context, args) -> {
                List<Object> blocks = new ArrayList<>();
                for (Object region : Values.array(args[0])) {
                    if (region instanceof Region r) {
                        r.getBlocks().forEachRemaining(blocks::add);
                    }
                }
                return blocks.toArray();
            };
        }, "[(all|the)] blocks (in|of) [[the] region[s]] %regions%");

        registrar.expression("ExprMembersOfRegion", "offlineplayer", info -> {
            used(info, 0);
            boolean owners = info.mark() == 1;
            return (context, args) -> {
                List<Object> players = new ArrayList<>();
                for (Object region : Values.array(args[0])) {
                    if (region instanceof Region r) {
                        players.addAll(owners ? r.getOwners() : r.getMembers());
                    }
                }
                return players.size() == 1 ? players.getFirst() : players.toArray();
            };
        }, "(all|the|) (0¦members|1¦owner[s]) of [[the] region[s]] %regions%",
                "[[the] region[s]] %regions%'[s] (0¦members|1¦owner[s])");

        registrar.expression("ExprRegionsAt", "region", info -> {
            used(info);
            return (context, args) -> {
                List<Object> regions = new ArrayList<>();
                for (Location location : Direction.combine(args[0], args[1])) {
                    regions.addAll(getRegionsAt(location));
                }
                return regions.toArray();
            };
        }, "[the] region(1¦s|) %direction% %locations%");

        // Skript's EvtRegionBorder.
        registrar.event("EvtRegionBorder", List.of(RegionBorderEvent.class), info -> {
            used(info, 0);
            boolean enter = info.hasTag("enter");
            return (context, args) -> {
                if (!(context.event() instanceof RegionBorderEvent event) || event.isEntering() != enter) {
                    return false;
                }
                Object[] regions = Events.values(args[0]);
                return regions == null || Events.check(info, 0, regions, region -> event.getRegion().equals(
                        region instanceof String name ? parse(name) : region));
            };
        }, "(:enter[ing]|leav(e|ing)|exit[ing]) [of] ([a] region|[[the] region] %-regions%)",
                "region (:enter[ing]|leav(e|ing)|exit[ing])");
        registrar.eventValue(RegionBorderEvent.class, "region", RegionBorderEvent::getRegion);
        registrar.eventValue(RegionBorderEvent.class, "player", RegionBorderEvent::getPlayer);
        // Skript's ExprRegion, '[event-]region', is the event value above, as NeoSkript gives event values by type.
    }

    /**
     * Skript's {@code SimpleExpression.check} with {@code invert}: whether {@code test} holds for all (or any) of
     * the values, the other way round when inverted.
     */
    private static boolean check(Object[] values, boolean and, boolean invert, Predicate<Object> test) {
        return invert != Condition.check(values, and, test);
    }

    private static final Listener LISTENER = new Listener() {};

    /** The last move event looked at, as a teleport is also a move: each is handled once. */
    private static volatile Event last;

    /**
     * Starts firing region border events from players' moves, teleports and portals, as Skript's
     * EvtRegionBorder does once a trigger listens to it.
     */
    static void start(Plugin plugin, EventPriority priority) {
        for (Class<? extends Event> moves : List.of(PlayerMoveEvent.class, PlayerTeleportEvent.class,
                PlayerPortalEvent.class)) {
            Bukkit.getPluginManager().registerEvent(moves, LISTENER, priority, (listener, event) -> {
                if (event instanceof PlayerMoveEvent move && RegionBorderEvent.getHandlerList()
                        .getRegisteredListeners().length > 0) {
                    moved(move);
                }
            }, plugin, true);
        }
    }

    private static void moved(PlayerMoveEvent event) {
        if (event == last) {
            return;
        }
        last = event;
        Location to = event.getTo();
        Location from = event.getFrom();
        if (to.equals(from)) {
            return;
        }
        Set<Region> oldRegions = getRegionsAt(from);
        Set<Region> newRegions = getRegionsAt(to);
        for (Region oldRegion : oldRegions) {
            if (!newRegions.contains(oldRegion)) {
                cross(oldRegion, event, false);
            }
        }
        for (Region newRegion : newRegions) {
            if (!oldRegions.contains(newRegion)) {
                cross(newRegion, event, true);
            }
        }
    }

    private static void cross(Region region, PlayerMoveEvent event, boolean enter) {
        RegionBorderEvent crossing = new RegionBorderEvent(region, event.getPlayer(), enter);
        crossing.setCancelled(event.isCancelled());
        Bukkit.getPluginManager().callEvent(crossing);
        event.setCancelled(crossing.isCancelled());
    }
}
