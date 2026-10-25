/*
 * Copyright (c) 2026 Xena Studios
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package co.xenastudios.neoskript.paper;

import co.xenastudios.neoskript.api.element.Changeable;
import co.xenastudios.neoskript.api.element.ElementInfo;
import co.xenastudios.neoskript.api.element.Expression;
import co.xenastudios.neoskript.api.element.SyntaxError;
import co.xenastudios.neoskript.api.element.TriggerContext;
import java.util.ArrayList;
import java.util.List;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.entity.Player;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.entity.FoodLevelChangeEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerGameModeChangeEvent;
import org.bukkit.event.player.PlayerItemHeldEvent;
import org.bukkit.event.player.PlayerLevelChangeEvent;

/**
 * Player properties whose value in their own change event is the one the event is about, and that have a
 * past and future state there as Skript's: {@code past level of player} in a level change event is the old
 * level, {@code the future gamemode of player} the one changed to. As in Skript, the event's value is also the
 * present one: the player's own changes only once the event is over.
 */
final class TimedProperties {

    private TimedProperties() {}

    /** The players in the owners argument, the event's player where it's left out. */
    private static List<Player> players(TriggerContext context, Object owners) {
        List<Player> players = new ArrayList<>();
        Object[] values = owners == null ? new Object[] {Property.eventValue(context)} : Property.of(owners);
        for (Object value : values) {
            if (value instanceof Player player) {
                players.add(player);
            }
        }
        return players;
    }

    private static Object single(List<Object> values) {
        return values.size() == 1 ? values.getFirst() : values.toArray();
    }

    /**
     * Skript's game mode: in a game mode change event, the event's player's is the new one, now and after the
     * event, and the one they had before it. Set there to another one, the event is cancelled.
     */
    static void registerGameMode(co.xenastudios.neoskript.api.registration.Registrar registrar) {
        registrar.expression("ExprGameMode", "gamemode", info -> new GameModeOf(info.time()),
                co.xenastudios.neoskript.api.registration.Patterns.property("game[ ]mode", "players"));
        registrar.timeStates("ExprGameMode", -1, PlayerGameModeChangeEvent.class);
    }

    private record GameModeOf(int time) implements Expression, Changeable {

        private Player eventPlayer(TriggerContext context) {
            return time >= 0 && context.event() instanceof PlayerGameModeChangeEvent event ? event.getPlayer() : null;
        }

        @Override
        public Object get(TriggerContext context, Object[] args) {
            Player eventPlayer = eventPlayer(context);
            List<Object> modes = new ArrayList<>();
            for (Player player : players(context, args[0])) {
                modes.add(player == eventPlayer
                        ? ((PlayerGameModeChangeEvent) context.event()).getNewGameMode() : player.getGameMode());
            }
            return single(modes);
        }

        @Override
        public Class<?>[] acceptChange(Mode mode) {
            return mode == Mode.SET ? new Class<?>[] {GameMode.class} : mode == Mode.RESET ? new Class<?>[0] : null;
        }

        @Override
        public void change(TriggerContext context, Object[] args, Object[] delta, Mode mode) {
            GameMode gameMode = delta == null || delta.length == 0 ? Bukkit.getDefaultGameMode() : (GameMode) delta[0];
            List<Player> players = players(context, args[0]);
            Player eventPlayer = eventPlayer(context);
            if (eventPlayer != null && players.contains(eventPlayer)
                    && ((PlayerGameModeChangeEvent) context.event()).getNewGameMode() != gameMode) {
                ((PlayerGameModeChangeEvent) context.event()).setCancelled(true);
            }
            for (Player player : players) {
                player.setGameMode(gameMode);
            }
        }
    }

    /**
     * Skript's food level, from 0 to 10 in halves: in a food level change event, the event's player's is the
     * level it changes to, now and after the event, and changing it changes the event's.
     */
    static void registerFoodLevel(co.xenastudios.neoskript.api.registration.Registrar registrar) {
        registrar.expression("ExprFoodLevel", "number", info -> new FoodLevel(info.time()),
                "[the] (food|hunger)[[ ](level|met(er|re)|bar)] [of %players%]",
                "%players%'[s] (food|hunger)[[ ](level|met(er|re)|bar)]");
        registrar.timeStates("ExprFoodLevel", 0, FoodLevelChangeEvent.class);
    }

    private record FoodLevel(int time) implements Expression, Changeable {

        private FoodLevelChangeEvent event(TriggerContext context, Player player) {
            return time >= 0 && context.event() instanceof FoodLevelChangeEvent event && player.equals(event.getEntity())
                    ? event : null;
        }

        @Override
        public Object get(TriggerContext context, Object[] args) {
            List<Object> levels = new ArrayList<>();
            for (Player player : players(context, args[0])) {
                FoodLevelChangeEvent event = event(context, player);
                levels.add(0.5 * (event != null ? event.getFoodLevel() : player.getFoodLevel()));
            }
            return single(levels);
        }

        @Override
        public Class<?>[] acceptChange(Mode mode) {
            return mode == Mode.REMOVE_ALL ? null : mode == Mode.DELETE || mode == Mode.RESET ? new Class<?>[0]
                    : new Class<?>[] {Number.class};
        }

        @Override
        public void change(TriggerContext context, Object[] args, Object[] delta, Mode mode) {
            int by = delta == null || delta.length == 0 ? 0 : Math.round(((Number) delta[0]).floatValue() * 2);
            for (Player player : players(context, args[0])) {
                FoodLevelChangeEvent event = event(context, player);
                int food = event != null ? event.getFoodLevel() : player.getFoodLevel();
                food = switch (mode) {
                    case ADD -> food + by;
                    case REMOVE -> food - by;
                    case RESET -> 20;
                    default -> by;
                };
                food = Math.max(0, Math.min(20, food));
                if (event != null) {
                    event.setFoodLevel(food);
                } else {
                    player.setFoodLevel(food);
                }
            }
        }
    }

    /**
     * Skript's level: in a level change event, the event's player's is the old level before the event, and
     * the new one now and after it. In a death event, the future level is the one they'll respawn with.
     */
    static void registerLevel(co.xenastudios.neoskript.api.registration.Registrar registrar) {
        registrar.expression("ExprLevel", "integer", Level::new,
                PlayerSyntax.defaultProperty("[xp|exp[erience]] level", "players"));
        registrar.timeStates("ExprLevel", 0, PlayerLevelChangeEvent.class, PlayerDeathEvent.class);
    }

    private record Level(ElementInfo info) implements Expression, Changeable {

        @Override
        public Object get(TriggerContext context, Object[] args) {
            List<Object> levels = new ArrayList<>();
            for (Player player : players(context, args[0])) {
                levels.add(context.event() instanceof PlayerLevelChangeEvent event && event.getPlayer() == player
                        ? (long) (info.time() < 0 ? event.getOldLevel() : event.getNewLevel()) : (long) player.getLevel());
            }
            return single(levels);
        }

        @Override
        public Class<?>[] acceptChange(Mode mode) {
            if (mode == Mode.REMOVE_ALL) {
                return null;
            }
            boolean delayed = co.xenastudios.neoskript.api.element.Warnings.surelyDelayed();
            if (info.isEvent(org.bukkit.event.player.PlayerRespawnEvent.class) && !delayed) {
                throw new SyntaxError("Cannot change a player's level in a respawn event. Add a delay of 1 tick or change"
                        + " the 'new level' in a death event.");
            }
            if (info.isEvent(EntityDeathEvent.class) && info.time() == 0 && info.isDefault(0) && !delayed) {
                co.xenastudios.neoskript.api.element.Warnings.warn("Changing the player's level in a death event will"
                        + " change the player's level before they die. Use either 'past level of player' or 'new level of"
                        + " player' to clearly state whether to change the level before or after they die.");
            }
            if (info.time() == -1 && !info.isEvent(EntityDeathEvent.class)) {
                return null;
            }
            if (info.time() != 0 && info.isEvent(PlayerLevelChangeEvent.class)) {
                throw new SyntaxError("Changing the past or future level in a level change event has no effect.");
            }
            return mode == Mode.DELETE || mode == Mode.RESET ? new Class<?>[0] : new Class<?>[] {Number.class};
        }

        @Override
        public void change(TriggerContext context, Object[] args, Object[] delta, Mode mode) {
            int by = delta == null || delta.length == 0 ? 0 : ((Number) delta[0]).intValue();
            for (Player player : players(context, args[0])) {
                PlayerDeathEvent death = info.time() > 0 && context.event() instanceof PlayerDeathEvent event
                        && event.getEntity() == player ? event : null;
                int level = death != null ? death.getNewLevel() : player.getLevel();
                level = switch (mode) {
                    case SET -> by;
                    case ADD -> level + by;
                    case REMOVE -> level - by;
                    default -> 0;
                };
                level = Math.max(0, level);
                if (death != null) {
                    death.setNewLevel(level);
                } else {
                    player.setLevel(level);
                }
            }
        }
    }

    /**
     * Skript's selected hotbar slot: in an item held change event, the slot left before the event and the one
     * chosen after it. The {@code currently selected} one has no such states.
     */
    static void registerHotbarSlot(co.xenastudios.neoskript.api.registration.Registrar registrar) {
        registrar.expression("ExprHotbarSlot", "slot", info -> new HotbarSlot(info.time()),
                PlayerSyntax.defaultProperty("[([current:currently] selected|current:current)] hotbar slot[s]", "players"));
        registrar.timeStates("ExprHotbarSlot", info -> !info.hasTag("current") && info.isDefault(0)
                && info.isEvent(PlayerItemHeldEvent.class));
    }

    private record HotbarSlot(int time) implements Expression, Changeable {

        @Override
        public Object get(TriggerContext context, Object[] args) {
            List<Object> slots = new ArrayList<>();
            for (Player player : players(context, args[0])) {
                org.bukkit.inventory.PlayerInventory inventory = player.getInventory();
                int index = inventory.getHeldItemSlot();
                if (time != 0 && context.event() instanceof PlayerItemHeldEvent held) {
                    index = time > 0 ? held.getNewSlot() : held.getPreviousSlot();
                }
                slots.add(new Items.Slot(inventory, index));
            }
            return single(slots);
        }

        @Override
        public Class<?>[] acceptChange(Mode mode) {
            return mode == Mode.SET ? new Class<?>[] {Items.Slot.class, Number.class} : null;
        }

        @Override
        public void change(TriggerContext context, Object[] args, Object[] delta, Mode mode) {
            if (delta == null || delta.length == 0) {
                return;
            }
            int index = delta[0] instanceof Items.Slot slot ? slot.index() : ((Number) delta[0]).intValue();
            // Only a slot in the hotbar can be the selected one.
            if (index < 0 || index > 8) {
                return;
            }
            for (Player player : players(context, args[0])) {
                player.getInventory().setHeldItemSlot(index);
            }
        }
    }
}
