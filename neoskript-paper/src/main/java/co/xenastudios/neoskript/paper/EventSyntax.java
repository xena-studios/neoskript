/*
 * Copyright (c) 2026 Xena Studios
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package co.xenastudios.neoskript.paper;

import static co.xenastudios.neoskript.paper.EventExpressions.isIn;
import static co.xenastudios.neoskript.paper.EventExpressions.restrict;

import co.xenastudios.neoskript.api.element.Changeable.Mode;
import co.xenastudios.neoskript.api.element.SyntaxError;
import co.xenastudios.neoskript.api.registration.Patterns;
import co.xenastudios.neoskript.api.registration.Registrar;
import co.xenastudios.neoskript.runtime.Timespan;
import co.xenastudios.neoskript.runtime.command.CommandEvent;
import com.destroystokyo.paper.event.server.PaperServerListPingEvent;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.bukkit.block.sign.Side;
import org.bukkit.command.CommandSender;
import org.bukkit.command.ProxiedCommandSender;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.block.SignChangeEvent;
import org.bukkit.event.command.UnknownCommandEvent;
import org.bukkit.event.player.PlayerEggThrowEvent;
import org.bukkit.event.world.PortalCreateEvent;
import org.bukkit.inventory.Inventory;

/**
 * Values of a few more events, as Skript has them: the thrown egg, a new portal's blocks, the server list's hover
 * list, the sign side changed, a command's executor; and some things that aren't of events: inventories' rows and
 * slots, players' experience pickup cooldown, and interrupting what an entity is using.
 */
final class EventSyntax {

    private EventSyntax() {}

    static void register(Registrar registrar) {
        registrar.expression("ExprEgg", "entity", info -> {
            if (!isIn(info, PlayerEggThrowEvent.class)) {
                String event = info.eventName();
                throw new SyntaxError("There's no entity in " + (event == null ? "this event"
                        : ("aeiou".indexOf(Character.toLowerCase(event.charAt(0))) >= 0 ? "an " : "a ") + event + " event"));
            }
            return new EventChangeable<>(PlayerEggThrowEvent.class, PlayerEggThrowEvent::getEgg);
        }, "[the] [thrown] egg");
        registrar.expression("ExprPortal", "block", info -> {
            restrict(info, "the portal blocks", "the portal create event", PortalCreateEvent.class);
            return new EventChangeable<>(PortalCreateEvent.class, event -> event.getBlocks().stream()
                    .map(org.bukkit.block.BlockState::getBlock).toArray());
        }, "[the] portal['s] blocks", "[the] blocks of [the] portal");
        registrar.expression("ExprHoverList", "string", info -> {
            restrict(info, "the hover list", "the server list ping event", PaperServerListPingEvent.class);
            return new EventChangeable<>(PaperServerListPingEvent.class, event -> event.getListedPlayers().stream()
                    .map(PaperServerListPingEvent.ListedPlayerInfo::name).toArray(), Object[].class,
                    Property.modes(Mode.SET, Mode.ADD, Mode.REMOVE, Mode.DELETE, Mode.RESET), (event, delta, mode) -> {
                List<PaperServerListPingEvent.ListedPlayerInfo> sample = event.getListedPlayers();
                List<Object> given = new ArrayList<>();
                for (Object value : delta == null ? new Object[0] : delta) {
                    // Components become legacy text, as in Skript.
                    given.add(value instanceof Player ? value : TextComponentParser.instance()
                            .toLegacyString(TextComponents.from(value)));
                }
                switch (mode) {
                    case SET, ADD -> {
                        if (mode == Mode.SET) {
                            sample.clear();
                        }
                        for (Object value : given) {
                            sample.add(value instanceof Player player
                                    ? new PaperServerListPingEvent.ListedPlayerInfo(player.getName(), player.getUniqueId())
                                    : new PaperServerListPingEvent.ListedPlayerInfo((String) value, UUID.randomUUID()));
                        }
                    }
                    case REMOVE -> {
                        for (Object value : given) {
                            sample.removeIf(profile -> profile.name().equals(value));
                        }
                    }
                    default -> sample.clear();
                }
            });
        }, "[the] [custom] [player|server] (hover|sample) ([message] list|message)", "[the] [custom] player [hover|sample] list");
        registrar.condition("CondChangedSide", info -> {
            Side expected = info.hasTag("back") ? Side.BACK : Side.FRONT;
            boolean negated = info.pattern() % 2 == 1;
            restrict(info, "the " + (expected == Side.FRONT ? "front" : "back") + " side was " + (negated ? "not " : "")
                    + "changed", "the sign change event", SignChangeEvent.class);
            return (context, args) -> context.event() instanceof SignChangeEvent event
                    && (event.getSide() == expected) != negated;
        }, "[the] (front|:back) [side] (is|was) changed", "[the] (front|:back) [side] (is not|isn't|was not|wasn't) changed",
                "[the] changed side (is|was) [the] (front|:back)", "[the] changed side (is not|isn't|was not|wasn't) [the] (front|:back)");
        registrar.expression("ExprCommandExecutor", "entity", info -> {
            restrict(info, "the command executor", "the unknown command execution event", CommandEvent.class,
                    UnknownCommandEvent.class);
            return (context, args) -> switch (context.event()) {
                case CommandEvent command -> executor(command.sender());
                case UnknownCommandEvent unknown -> executor(unknown.getSender());
                case null, default -> null;
            };
        }, "[the] [command['s]] executor");

        registrar.expression("ExprExperienceCooldown", "timespan", info -> new Property<>(Player.class,
                player -> Property.ticks(player.getExpCooldown()), Timespan.class,
                Property.modes(Mode.ADD, Mode.SET, Mode.RESET, Mode.DELETE), (player, delta, mode) -> {
                    long ticks = delta == null ? 0 : EventChangeable.ticks(delta[0]);
                    player.setExpCooldown((int) switch (mode) {
                        case ADD -> Property.fit(-1, player.getExpCooldown() + ticks, Integer.MAX_VALUE);
                        case SET -> Property.fit(-1, ticks, Integer.MAX_VALUE);
                        default -> 0;
                    });
                }), Patterns.property("(experience|[e]xp) [pickup|collection] cooldown", "players"));
        registrar.effect("EffCancelItemUse", info -> (context, args) -> {
            for (Object entity : Property.of(args[0])) {
                if (entity instanceof LivingEntity living) {
                    living.clearActiveItem();
                }
            }
        }, "(cancel|interrupt) [the] us[ag]e of %livingentities%'[s] [active|current] item");
        // Skript's holders of inventories are ExprInventoryHolder, in Items; the rest of its ExprInventoryInfo is here.
        registrar.expression("ExprInventoryInfo", "integer", info -> {
            boolean rows = info.mark() == 2;
            return (context, args) -> {
                List<Object> values = new ArrayList<>();
                for (Object value : Property.of(args[0])) {
                    if (value instanceof Inventory inventory) {
                        // Hoppers hold 5: that's a row, not none.
                        values.add(!rows ? (long) inventory.getSize()
                                : inventory.getSize() < 9 ? 1L : (long) (inventory.getSize() / 9));
                    }
                }
                return Property.single(values);
            };
        }, "(2¦[amount of] rows|3¦[amount of] slots) of %inventories%",
                "%inventories%'[s] (2¦[amount of] rows|3¦[amount of] slots)");
    }

    /** The entity a command runs as: who sent it, or with {@code /execute as}, who it runs as. */
    private static Entity executor(Object sender) {
        if (sender instanceof ProxiedCommandSender proxied) {
            CommandSender callee = proxied.getCallee();
            return callee instanceof Entity entity ? entity : null;
        }
        return sender instanceof Entity entity ? entity : null;
    }
}
