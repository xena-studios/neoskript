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

    private static final LegacyComponentSerializer LEGACY = LegacyComponentSerializer.legacyAmpersand();
    static final LegacyComponentSerializer LEGACY_SECTION = LegacyComponentSerializer.legacySection();

    private Messages() {}

    static void register(Registrar registrar) {
        registrar.effect("EffMessage", info -> (context, args) -> {
            // Without 'to', it goes to the event's player or command sender, or the console if there's none.
            Object[] recipients = args[1] == null || ((Object[]) args[1]).length == 0
                    ? new Object[] {Bukkit.getConsoleSender()} : (Object[]) args[1];
            for (Object message : (Object[]) args[0]) {
                Component component = component(message);
                for (Object recipient : recipients) {
                    if (recipient instanceof Audience audience) {
                        audience.sendMessage(component);
                    }
                }
            }
        }, "(message|send [message[s]]) %objects% [to %audiences%]");

        registrar.effect("EffBroadcast", info -> (context, args) -> {
            for (Object message : (Object[]) args[0]) {
                Component component = component(message);
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
        }, "broadcast %objects% [(to|in) %worlds%]");

        registrar.expression("LitConsole", "commandsender", info -> (context, args) -> Bukkit.getConsoleSender(),
                "[the] (console|server)");
    }

    /** A message as a text component. {@code &} color codes are applied to text. */
    /**
     * A message as Skript formats it: {@code &} colour codes, and tags like {@code <red>}, {@code <bold>},
     * {@code <#ff0000>} and {@code <unicode:00A7>}. Text without tags takes the faster colour-code path.
     */
    public static Component component(Object message) {
        if (message instanceof Component component) {
            return component;
        }
        String text = Values.text(message);
        return text.indexOf('<') < 0 ? LEGACY.deserialize(text) : TAGGED.deserialize(tags(text), UNICODE);
    }

    /** A component as tags, as Skript gives text that was formatted with them: {@code <red>Hello}. */
    static String tagged(Component component) {
        return TAGGED.serialize(component);
    }

    /** Text with tags taken out, again and again, as {@code uncolored} gives it: {@code <red<red>>B} is {@code B}. */
    static String plain(String text) {
        String current = text;
        for (int i = 0; i < 16; i++) {
            String next = PLAIN.serialize(component(current));
            if (next.equals(current)) {
                break;
            }
            current = next;
        }
        return current;
    }

    private static final net.kyori.adventure.text.minimessage.MiniMessage TAGGED =
            net.kyori.adventure.text.minimessage.MiniMessage.miniMessage();
    private static final net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer PLAIN =
            net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer.plainText();
    /** {@code <unicode:1F41B>}, or {@code <u:...>}: the character with that code point. */
    private static final net.kyori.adventure.text.minimessage.tag.resolver.TagResolver UNICODE =
            net.kyori.adventure.text.minimessage.tag.resolver.TagResolver.resolver(java.util.Set.of("unicode", "u"), (args, context) -> {
                try {
                    int code = Integer.parseInt(args.popOr("a code point").value(), 16);
                    return net.kyori.adventure.text.minimessage.tag.Tag.inserting(Component.text(new String(Character.toChars(code))));
                } catch (IllegalArgumentException invalid) {
                    return net.kyori.adventure.text.minimessage.tag.Tag.inserting(Component.empty());
                }
            });

    /** Colour codes ({@code &a}, {@code §l}, {@code &#ff0000}) as the tags they mean, so both can be mixed. */
    private static String tags(String text) {
        StringBuilder out = new StringBuilder(text.length() + 16);
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if ((c == '&' || c == '\u00a7') && i + 1 < text.length()) {
                char code = Character.toLowerCase(text.charAt(i + 1));
                if (code == '#' && i + 7 < text.length() && text.substring(i + 2, i + 8).matches("[0-9a-fA-F]{6}")) {
                    out.append("<#").append(text, i + 2, i + 8).append('>');
                    i += 7;
                    continue;
                }
                String tag = switch (code) {
                    case '0' -> "black";
                    case '1' -> "dark_blue";
                    case '2' -> "dark_green";
                    case '3' -> "dark_aqua";
                    case '4' -> "dark_red";
                    case '5' -> "dark_purple";
                    case '6' -> "gold";
                    case '7' -> "gray";
                    case '8' -> "dark_gray";
                    case '9' -> "blue";
                    case 'a' -> "green";
                    case 'b' -> "aqua";
                    case 'c' -> "red";
                    case 'd' -> "light_purple";
                    case 'e' -> "yellow";
                    case 'f' -> "white";
                    case 'k' -> "obfuscated";
                    case 'l' -> "bold";
                    case 'm' -> "strikethrough";
                    case 'n' -> "underlined";
                    case 'o' -> "italic";
                    case 'r' -> "reset";
                    default -> null;
                };
                if (tag != null) {
                    out.append('<').append(tag).append('>');
                    i++;
                    continue;
                }
            }
            out.append(c);
        }
        return out.toString();
    }
}
