/*
 * Copyright (c) 2026 Xena Studios
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package co.xenastudios.neoskript.paper;

import co.xenastudios.neoskript.api.registration.Registrar;
import co.xenastudios.neoskript.paper.PlayerSyntax.InputKey;
import io.papermc.paper.event.player.PrePlayerAttackEntityEvent;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import org.bukkit.Input;
import org.bukkit.event.player.PlayerInputEvent;

/**
 * Skript's attempt attack event ({@code on attack attempt}, {@code on attempt to attack a zombie}) and player input
 * event ({@code on press of jump key}, {@code on any input key release}).
 */
final class InputAndAttackEvents {

    private InputAndAttackEvents() {}

    static void register(Registrar registrar) {
        registrar.event("EvtAttemptAttack", List.of(PrePlayerAttackEntityEvent.class),
                Events.optional(0, (event, kinds) -> Events.any(kinds,
                        kind -> Events.isKind(kind, ((PrePlayerAttackEntityEvent) event).getAttacked()))),
                "attack attempt", "attempt[ing] to attack %entitytypes%");

        // Toggling (0), pressing (1) or releasing (2) keys: the player's current input is still the old one.
        registrar.event("EvtPlayerInput", List.of(PlayerInputEvent.class), info -> {
            int type = Events.mark(info);
            boolean and = info.and(0);
            return (context, args) -> {
                PlayerInputEvent event = (PlayerInputEvent) context.event();
                Set<InputKey> previous = keys(event.getPlayer().getCurrentInput());
                Set<InputKey> current = keys(event.getInput());
                Object[] wanted = Events.values(args[0]);
                if (wanted == null) {
                    return switch (type) {
                        case 1 -> previous.size() <= current.size();
                        case 2 -> previous.size() >= current.size();
                        default -> true;
                    };
                }
                for (Object value : wanted) {
                    if (!(value instanceof InputKey key)) {
                        continue;
                    }
                    boolean before = previous.contains(key);
                    boolean now = current.contains(key);
                    boolean changed = switch (type) {
                        case 1 -> !before && now;
                        case 2 -> before && !now;
                        default -> before != now;
                    };
                    if (and && !changed) {
                        return false;
                    } else if (!and && changed) {
                        return true;
                    }
                }
                return and;
            };
        }, "[player] (toggle|toggling|1:press[ing]|2:release|2:releasing) of (%-inputkeys%|(an|any) input key)",
                "([player] %-inputkeys%|[an|player] input key) (toggle|toggling|1:press[ing]|2:release|2:releasing)");
        registrar.eventValue(PlayerInputEvent.class, "inputkey", InputKey[].class, 0,
                event -> keys(event.getInput()).toArray(new InputKey[0]));
        registrar.eventValue(PlayerInputEvent.class, "inputkey", InputKey[].class, -1,
                event -> keys(event.getPlayer().getCurrentInput()).toArray(new InputKey[0]));
    }

    /** The keys {@code input} has pressed. */
    private static Set<InputKey> keys(Input input) {
        Set<InputKey> keys = EnumSet.noneOf(InputKey.class);
        for (InputKey key : InputKey.values()) {
            if (key.pressed(input)) {
                keys.add(key);
            }
        }
        return keys;
    }
}
