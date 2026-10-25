/*
 * Copyright (c) 2026 Xena Studios
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package co.xenastudios.neoskript.paper;

import co.xenastudios.neoskript.api.registration.Patterns;
import co.xenastudios.neoskript.api.registration.Registrar;
import co.xenastudios.neoskript.api.registration.Parameter;
import co.xenastudios.neoskript.runtime.Timespan;
import co.xenastudios.neoskript.runtime.Values;
import java.util.List;
import java.util.UUID;
import net.kyori.adventure.audience.Audience;
import net.kyori.adventure.title.Title;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.entity.LivingEntity;
import net.kyori.adventure.text.Component;
import org.bukkit.Nameable;
import org.bukkit.OfflinePlayer;
import org.bukkit.World;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;

/** Players and other named things: names, UUIDs, being online, permissions, kicking. */
final class Players {

    private Players() {}

    static void register(Registrar registrar) {
        registrar.property("ExprUUID", "uuid", "uuid[s]", "offlineplayers/worlds/entities", Object.class,
                owner -> switch (owner) {
                    case OfflinePlayer player -> player.getUniqueId();
                    case World world -> world.getUID();
                    case Entity entity -> entity.getUniqueId();
                    default -> null;
                });
        // Settable for entities and items: their custom name.
        java.util.function.BiConsumer<Object, Object> rename = (owner, name) -> {
            Component component = name == null ? null : Messages.component(name);
            switch (owner) {
                case org.bukkit.entity.Player player -> player.displayName(component);
                // A boss bar's name is its title.
                case org.bukkit.boss.BossBar bar -> bar.setTitle(component == null ? null : Messages.LEGACY_SECTION.serialize(component));
                case Entity entity -> {
                    entity.customName(component);
                    // A named mob stays, as Skript has it.
                    if (entity instanceof org.bukkit.entity.LivingEntity living) {
                        living.setRemoveWhenFarAway(component == null);
                    }
                }
                case ItemType item -> item.editMeta(meta -> meta.displayName(component));
                case org.bukkit.inventory.ItemStack stack -> stack.editMeta(meta -> meta.displayName(component));
                case Items.Slot slot -> {
                    org.bukkit.inventory.ItemStack stack = slot.item();
                    if (stack != null && !stack.getType().isAir()) {
                        stack.editMeta(meta -> meta.displayName(component));
                        slot.set(stack);
                    }
                }
                case org.bukkit.inventory.Inventory inventory -> retitle(inventory, component);
                case org.bukkit.block.Block block -> {
                    if (block.getState() instanceof Nameable nameable) {
                        nameable.customName(component);
                        ((org.bukkit.block.BlockState) nameable).update(true, false);
                    }
                }
                default -> {}
            }
        };
        // 'display name', 'nick name', 'chat name', 'custom name': a player's display name, or anything else's
        // custom name. Giving an entity a display name shows it, as in Skript.
        registrar.expression("PropExprName-display", "textcomponent", info -> {
            java.util.function.BiConsumer<Object, Object> setter = (owner, name) -> {
                rename.accept(owner, name);
                if (owner instanceof Entity entity && !(owner instanceof org.bukkit.entity.Player)) {
                    entity.setCustomNameVisible(name != null);
                }
            };
            // An entity's is its custom name, if it has one.
            return new SettableProperty<>(Object.class, Object.class, owner -> switch (owner) {
                case org.bukkit.entity.Player player -> player.displayName();
                case Entity entity -> entity.customName();
                default -> name(owner);
            }, setter)
                    .resettingTo((java.util.function.Consumer<Object>) owner -> setter.accept(owner, null))
                    .deleting(owner -> setter.accept(owner, null));
        }, Patterns.property("(display|nick|chat|custom)[ ]name[s]", "objects"));
        // Resetting or deleting a name takes the custom name away.
        registrar.expression("PropExprName", "textcomponent", info -> new SettableProperty<>(Object.class, Object.class,
                Players::name, rename).resettingTo((java.util.function.Consumer<Object>) owner -> rename.accept(owner, null))
                .deleting(owner -> rename.accept(owner, null)), Patterns.property("name[s]", "objects"));

        // How long a player has played; players who aren't online count as none, as in Skript.
        registrar.expression("ExprTimePlayed", "timespan", info -> new SettableProperty<>(OfflinePlayer.class, Timespan.class,
                player -> new Timespan(player instanceof Player online
                        ? online.getStatistic(org.bukkit.Statistic.PLAY_ONE_MINUTE) * 50L : 0),
                (player, time) -> {
                    if (player instanceof Player online) {
                        online.setStatistic(org.bukkit.Statistic.PLAY_ONE_MINUTE, (int) Math.max(0, Math.min(Integer.MAX_VALUE, time.ticks())));
                    }
                }), "[the] time played of %offlineplayers%", "%offlineplayers%'[s] time played");
        registrar.condition("CondIsOnline", info -> {
            // 'offline' means not online; the negated pattern flips it again.
            boolean online = !info.hasTag("offline") ^ info.pattern() == 1;
            return (context, args) -> {
                Object[] players = (Object[]) args[0];
                for (Object player : players) {
                    if (!(player instanceof OfflinePlayer offline) || offline.isOnline() != online) {
                        return false;
                    }
                }
                return players.length > 0;
            };
        }, "%offlineplayers% (is|are) (online|offline:offline|connected)",
                "%offlineplayers% (isn't|is not|aren't|are not) (online|offline:offline|connected)");

        registrar.condition("CondPermission", info -> {
            boolean negated = info.pattern() == 1;
            return (context, args) -> {
                Object[] senders = (Object[]) args[0];
                for (Object sender : senders) {
                    for (Object permission : (Object[]) args[1]) {
                        boolean has = sender instanceof CommandSender s && s.hasPermission(Values.text(permission));
                        if (has == negated) {
                            return false;
                        }
                    }
                }
                return senders.length > 0;
            };
        }, Patterns.hasProperty("[the] permission[s] %texts%", "commandsenders"));

        registrar.effect("EffKick", info -> (context, args) -> {
            Component reason = args[1] == null ? null : Messages.component(args[1]);
            for (Object player : (Object[]) args[0]) {
                if (player instanceof Player online) {
                    if (reason == null) {
                        online.kick();
                    } else {
                        online.kick(reason);
                    }
                }
            }
        }, "kick %players% [(by reason of|because [of]|on account of|due to) %text%]");

        registerMore(registrar);
    }

    private static void registerMore(Registrar registrar) {
        registrar.function("player", List.of(Parameter.of("nameOrUUID", "string"),
                new Parameter("getExactPlayer", "boolean", false, true)), "player", false, args -> {
                    String text = Values.text(args[0]);
                    try {
                        return Bukkit.getPlayer(UUID.fromString(text));
                    } catch (IllegalArgumentException e) {
                        return Boolean.TRUE.equals(args[1]) ? Bukkit.getPlayerExact(text) : Bukkit.getPlayer(text);
                    }
                });
        registrar.function("offlineplayer", List.of(Parameter.of("nameOrUUID", "string"),
                new Parameter("allowLookups", "boolean", false, true)), "offlineplayer", false, args -> {
                    String text = Values.text(args[0]);
                    try {
                        return Bukkit.getOfflinePlayer(UUID.fromString(text));
                    } catch (IllegalArgumentException e) {
                        OfflinePlayer cached = Bukkit.getOfflinePlayerIfCached(text);
                        return cached != null || Boolean.FALSE.equals(args[1]) ? cached : Bukkit.getOfflinePlayer(text);
                    }
                });

        registrar.condition("CondPlayedBefore", info -> {
            boolean negated = info.pattern() == 1;
            return (context, args) -> all((Object[]) args[0], OfflinePlayer.class,
                    player -> player.hasPlayedBefore() != negated);
        }, "%offlineplayers% [(has|have|did)] [already] play[ed] [on (this|the) server] (before|already)",
                "%offlineplayers% (has not|hasn't|have not|haven't|did not|didn't) [(already|yet)] play[ed] "
                        + "[on (this|the) server] (before|already|yet)");
        registrar.condition("CondCanFly", info -> {
            boolean negated = info.pattern() == 1;
            return (context, args) -> all((Object[]) args[0], Player.class,
                    player -> player.getAllowFlight() != negated);
        }, "%players% can fly", "%players% (can't|cannot|can not) fly");

        SettableProperty.register(registrar, "ExprFlightMode", "boolean", "fl(y[ing]|ight) (mode|state)", "players",
                Player.class, Boolean.class, Player::getAllowFlight, Player::setAllowFlight);
        SettableProperty.register(registrar, "ExprGameMode", "gamemode", "game[ ]mode", "players", Player.class,
                GameMode.class, Player::getGameMode, Player::setGameMode);
        SettableProperty.register(registrar, "ExprFoodLevel", "number", Player.class, Number.class,
                player -> (long) player.getFoodLevel(), (player, level) -> player.setFoodLevel(
                        Math.max(0, Math.min(20, co.xenastudios.neoskript.runtime.Values.toInt(level.doubleValue())))),
                "[the] (food|hunger)[[ ](level|met(er|re)|bar)] [of %players%]",
                "%players%'[s] (food|hunger)[[ ](level|met(er|re)|bar)]");
        SettableProperty.register(registrar, "ExprPlayerListName", "string", "(player|tab)[ ]list name[s]", "players",
                Player.class, Object.class, player -> Messages.LEGACY_SECTION.serialize(player.playerListName()),
                (player, name) -> player.playerListName(Messages.component(name)));

        registrar.effect("EffDamage", info -> (context, args) -> {
            if (!(args[0] instanceof LivingEntity attacker) || !(args[2] instanceof Number amount)) {
                return;
            }
            if (Double.isNaN(amount.doubleValue()) || amount.doubleValue() <= 0) {
                co.xenastudios.neoskript.runtime.RuntimeErrors.report("Cannot damage an entity by 0 or less. Consider healing instead.");
                return;
            }
            if (Double.isInfinite(amount.doubleValue())) {
                co.xenastudios.neoskript.runtime.RuntimeErrors.report("Cannot damage an entity by an infinite amount.");
                return;
            }
            for (Object victim : (Object[]) args[1]) {
                if (victim instanceof LivingEntity living) {
                    living.damage(amount.doubleValue() * 2, attacker);
                }
            }
        }, "make %livingentity% damage %livingentities% by %number% [heart[s]]");
        registrar.effect("EffForceAttack", info -> (context, args) -> {
            if (!(args[1] instanceof org.bukkit.entity.Entity target)) {
                return;
            }
            for (Object attacker : (Object[]) args[0]) {
                if (attacker instanceof LivingEntity living) {
                    living.attack(target);
                }
            }
        }, "make %livingentities% attack %entity%", "force %livingentities% to attack %entity%");
        registrar.expression("ExprLastDamage", "number", info -> new SettableProperty<>(LivingEntity.class, Number.class,
                living -> living.getLastDamage() / 2, (living, damage) -> living.setLastDamage(damage.doubleValue() * 2)),
                "[the] last damage [taken] (of|by) %livingentities%", "%livingentities%'[s] last damage [taken]");
        registrar.effect("EffIgnite", info -> {
            boolean extinguish = info.pattern() == 2;
            return (context, args) -> {
                int ticks = extinguish ? 0 : args[1] instanceof Timespan time ? (int) time.ticks() : 160;
                for (Object entity : (Object[]) args[0]) {
                    if (entity instanceof Entity e) {
                        e.setFireTicks(ticks);
                    }
                }
            };
        }, "(ignite|set fire to) %entities% [for %timespan%]", "(set|light) %entities% on fire [for %timespan%]",
                "extinguish %entities%");

        registrar.effect("EffSendTitle", info -> {
            boolean subtitleOnly = info.pattern() == 1;
            java.util.function.Function<Object, Component> first = TextComponents.componentOf(info, 0);
            java.util.function.Function<Object, Component> second = TextComponents.componentOf(info, 1);
            return (context, args) -> {
                int at = subtitleOnly ? 1 : 2;
                Component title = subtitleOnly || args[0] == null ? Component.empty() : first.apply(args[0]);
                Component subtitle = subtitleOnly ? first.apply(args[0])
                        : args[1] == null ? Component.empty() : second.apply(args[1]);
                Title.Times times = Title.Times.times(duration(args[at + 2], 10), duration(args[at + 1], 70),
                        duration(args[at + 3], 20));
                Title shown = Title.title(title, subtitle, times);
                for (Object audience : audiences(args[at])) {
                    if (audience instanceof Audience a) {
                        a.showTitle(shown);
                    }
                }
            };
        }, "send title %object% [with subtitle %object%] [to %audiences%] [for %timespan%] "
                + "[with fade[(-| )]in %timespan%] [[and] [with] fade[(-| )]out %timespan%]",
                "send subtitle %object% [to %audiences%] [for %timespan%] [with fade[(-| )]in %timespan%] "
                        + "[[and] [with] fade[(-| )]out %timespan%]");
        registrar.effect("EffActionBar", info -> {
            java.util.function.Function<Object, Component> asComponent = TextComponents.componentOf(info, 0);
            return (context, args) -> {
            Component bar = asComponent.apply(args[0]);
            for (Object audience : audiences(args[1])) {
                if (audience instanceof Audience a) {
                    a.sendActionBar(bar);
                }
            }
            };
        }, "send [the] action[ ]bar [with text] %object% [to %audiences%]");
    }

    /** Whether there are values, and {@code test} holds for every one (which has to be a {@code type}). */
    private static <T> boolean all(Object[] values, Class<T> type, java.util.function.Predicate<? super T> test) {
        for (Object value : values) {
            if (!type.isInstance(value) || !test.test(type.cast(value))) {
                return false;
            }
        }
        return values.length > 0;
    }

    /** Who a message goes to: the given audiences, or the console if there are none. */
    private static Object[] audiences(Object audiences) {
        return audiences instanceof Object[] array && array.length > 0 ? array : new Object[] {Bukkit.getConsoleSender()};
    }

    private static java.time.Duration duration(Object timespan, long ticks) {
        return java.time.Duration.ofMillis(timespan instanceof Timespan t ? t.millis() : ticks * 50);
    }

    /** The name of a player, world, entity or anything else with one, as a text component, as Skript gives it. */
    static Component name(Object owner) {
        return switch (owner) {
            case Player player -> player.name();
            case OfflinePlayer player -> player.getName() == null ? null : Component.text(player.getName());
            case co.xenastudios.neoskript.runtime.ScriptRef script -> text(Scripts.name(script));
            case co.xenastudios.neoskript.runtime.FunctionRef function -> text(function.name());
            case World world -> Component.text(world.getName());
            case Configs.ConfigNode node -> text(node.key());
            case CommandSender sender -> sender.name();
            case org.bukkit.boss.BossBar bar -> text(bar.getTitle());
            case Nameable nameable -> nameable.customName();
            // A named block, like a chest: its state has the name.
            case org.bukkit.block.Block block when block.getState() instanceof Nameable nameable -> nameable.customName();
            case Items.Slot slot when slot.item() != null && slot.item().hasItemMeta() -> slot.item().getItemMeta().displayName();
            // An inventory's is the title it's shown with, if anyone has it open.
            case org.bukkit.inventory.Inventory inventory -> inventory.getViewers().isEmpty() ? null
                    : inventory.getViewers().getFirst().getOpenInventory().title();
            case ItemType item when item.stack().getItemMeta() != null -> item.stack().getItemMeta().displayName();
            case org.bukkit.inventory.ItemStack stack when stack.getItemMeta() != null -> stack.getItemMeta().displayName();
            default -> null;
        };
    }

    /**
     * Gives an inventory a new title, as Skript does: its viewers are shown a copy with the same contents.
     * Without a title, its kind's default one.
     */
    private static void retitle(org.bukkit.inventory.Inventory inventory, Component title) {
        if (inventory.getViewers().isEmpty() || !inventory.getType().isCreatable()) {
            return;
        }
        List<org.bukkit.entity.HumanEntity> viewers = new java.util.ArrayList<>(inventory.getViewers());
        org.bukkit.event.inventory.InventoryType type = inventory.getType();
        Component name = title == null ? type.defaultTitle() : title;
        org.bukkit.inventory.Inventory copy = type == org.bukkit.event.inventory.InventoryType.CHEST
                ? org.bukkit.Bukkit.createInventory(inventory.getHolder(), inventory.getSize(), name)
                : org.bukkit.Bukkit.createInventory(inventory.getHolder(), type, name);
        copy.setContents(inventory.getContents());
        viewers.forEach(viewer -> viewer.openInventory(copy));
    }

    private static Component text(String text) {
        return text == null ? null : Component.text(text);
    }
}
