/*
 * Copyright (c) 2026 Xena Studios
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package co.xenastudios.neoskript.paper;

import co.xenastudios.neoskript.api.registration.Registrar;
import co.xenastudios.neoskript.runtime.Values;
import net.kyori.adventure.audience.Audience;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.World;

/** Sending and broadcasting messages, and the console. */
public final class Messages {

    static final LegacyComponentSerializer LEGACY_SECTION = LegacyComponentSerializer.legacySection();

    private Messages() {}

    static void register(Registrar registrar) {
        registrar.effect("EffMessage", info -> {
            java.util.function.Function<Object, Component> asComponent = TextComponents.componentOf(info, 0);
            return (context, args) -> {
            // Without 'to', it goes to the event's player or command sender, or the console if there's none.
            Object[] recipients = args[1] == null || ((Object[]) args[1]).length == 0
                    ? new Object[] {Bukkit.getConsoleSender()} : (Object[]) args[1];
            for (Object message : (Object[]) args[0]) {
                Component component = asComponent.apply(message);
                for (Object recipient : recipients) {
                    if (recipient instanceof Audience audience) {
                        audience.sendMessage(component);
                    }
                }
            }
            };
        }, "(message|send [message[s]]) %objects% [to %audiences%]");

        registrar.effect("EffBroadcast", info -> {
            java.util.function.Function<Object, Component> asComponent = TextComponents.componentOf(info, 0);
            return (context, args) -> {
            for (Object message : (Object[]) args[0]) {
                Component component = asComponent.apply(message);
                if (args[1] == null) {
                    Bukkit.getServer().broadcast(component);
                    continue;
                }
                for (Object world : (Object[]) args[1]) {
                    if (world instanceof World w) {
                        w.getPlayers().forEach(player -> player.sendMessage(component));
                    }
                }
            }
            };
        }, "broadcast %objects% [(to|in) %-worlds%]");

        registrar.expression("LitConsole", "commandsender", info -> (context, args) -> Bukkit.getConsoleSender(),
                "[the] (console|server)");
    }

    /**
     * A message as a text component: a component as it is, or text as Skript formats it, with legacy colour
     * codes ({@code &a}) and every tag ({@code <red>}, {@code <bold>}, {@code <#ff0000>}, {@code <unicode:00A7>}).
     */
    public static Component component(Object message) {
        if (message instanceof Component component) {
            return component;
        }
        return TextComponentParser.instance().parse(Values.text(message));
    }

    /** A component as tags, as Skript gives text that was formatted with them: {@code <red>Hello}. */
    static String tagged(Component component) {
        return TextComponentParser.instance().toString(component);
    }
}
