/*
 * Copyright (c) 2026 Xena Studios
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package co.xenastudios.neoskript.paper;

import co.xenastudios.neoskript.api.registration.Registrar;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Pattern;
import net.kyori.adventure.audience.Audience;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.OfflinePlayer;
import org.bukkit.World;
import java.util.UUID;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;

/** The basic types: text, numbers, booleans, who messages go to, players, worlds, entities and game modes. */
final class CoreTypes {

    private static final Pattern NUMBER = Pattern.compile("[-+]?(?:\\d+(?:\\.\\d*)?|\\.\\d+)");

    /** An angle: {@code 90 degrees}, or {@code 3.14 radians}, which is in degrees. */
    private static final Pattern ANGLE = Pattern.compile("(.+?) *(deg(?:ree)?s?|rad(?:ian)?s?)", Pattern.CASE_INSENSITIVE);

    /** A number parsed from text: {@code 5}, {@code -2.5}, or an angle in degrees. */
    static Object number(String text) {
        java.util.regex.Matcher angle = ANGLE.matcher(text);
        if (angle.matches()) {
            Object value = number(angle.group(1));
            return value instanceof Number n && Character.toLowerCase(angle.group(2).charAt(0)) == 'r'
                    ? Math.toDegrees(n.doubleValue()) : value;
        }
        if (!NUMBER.matcher(text).matches()) {
            return null;
        }
        if (text.indexOf('.') < 0) {
            try {
                return Long.parseLong(text);
            } catch (NumberFormatException e) {
                // Too big for a whole number.
            }
        }
        return Double.parseDouble(text);
    }
    private static final Pattern INTEGER = Pattern.compile("[-+]?\\d{1,18}");
    private static final Pattern PLAYER_NAME = Pattern.compile("[A-Za-z0-9_]{1,16}");

    private CoreTypes() {}

    /** An online player by name, or by the start of their name if only one player's name starts with it. */
    private static Player player(String text) {
        Player exact = Bukkit.getPlayerExact(text);
        if (exact != null) {
            return exact;
        }
        Player found = null;
        String start = text.toLowerCase(Locale.ROOT);
        for (Player player : Bukkit.getOnlinePlayers()) {
            if (player.getName().toLowerCase(Locale.ROOT).startsWith(start)) {
                if (found != null) {
                    return null;
                }
                found = player;
            }
        }
        return found;
    }

    /** A player by name: online, known to the server, or any valid name. */
    private static OfflinePlayer offlinePlayer(String text) {
        Player online = Bukkit.getPlayerExact(text);
        if (online != null) {
            return online;
        }
        OfflinePlayer cached = Bukkit.getOfflinePlayerIfCached(text);
        if (cached != null) {
            return cached;
        }
        return PLAYER_NAME.matcher(text).matches() ? Bukkit.getOfflinePlayer(text) : null;
    }

    static void register(Registrar registrar) {
        registrar.type("string", "text", String.class);
        registrar.type("number", "number", Number.class);
        registrar.type("integer", "integer", Long.class);
        registrar.type("boolean", "boolean", Boolean.class);
        registrar.subtype("integer", "number");
        // Numbers can be used where whole numbers are expected; they're rounded when they're used.
        registrar.subtype("number", "integer");
        // Left out, a number is 1, as Skript's number types default to.
        registrar.defaultValue("number", 1L);
        registrar.defaultValue("integer", 1L);
        registrar.alias("integer", "int", "ints");

        registrar.type("audience", "audience", Audience.class);
        registrar.type("commandsender", "command sender", CommandSender.class);
        registrar.type("offlineplayer", "offline player", OfflinePlayer.class);
        registrar.type("player", "player", Player.class);
        registrar.type("world", "world", World.class);
        registrar.type("entity", "entity", Entity.class);
        registrar.type("uuid", "uuid", UUID.class);
        registrar.subtype("commandsender", "audience");
        registrar.subtype("player", "commandsender");
        registrar.subtype("player", "offlineplayer");
        // A UUID is the text of it: '{_uuid} is "a0789aeb-..."'.
        registrar.comparison(UUID.class, String.class, (uuid, text) -> uuid.toString().equalsIgnoreCase(text.strip()));
        // Where text is wanted, a UUID is its text.
        registrar.converter(UUID.class, String.class, UUID::toString);
        // A UUID is what it identifies: '{_uuid} is the player'.
        registrar.comparison(UUID.class, OfflinePlayer.class, (uuid, player) -> uuid.equals(player.getUniqueId()));
        registrar.comparison(UUID.class, org.bukkit.entity.Entity.class, (uuid, entity) -> uuid.equals(entity.getUniqueId()));
        registrar.comparison(UUID.class, org.bukkit.World.class, (uuid, world) -> uuid.equals(world.getUID()));
        // 'player is "Notch"': a player is the text of their name.
        registrar.comparison(OfflinePlayer.class, String.class, (player, name) -> player.getName() != null
                && player.getName().equalsIgnoreCase(name));
        registrar.subtype("player", "entity");

        Map<String, GameMode> gameModes = new LinkedHashMap<>();
        for (GameMode mode : GameMode.values()) {
            gameModes.put(mode.name().toLowerCase(Locale.ROOT), mode);
        }
        registrar.namedType("gamemode", "game mode", GameMode.class, gameModes);
        registrar.text(GameMode.class, mode -> mode.name().toLowerCase(Locale.ROOT));

        // How text typed in commands becomes values.
        registrar.parser("string", text -> text);
        registrar.parser("number", CoreTypes::number);
        registrar.parser("integer", text -> INTEGER.matcher(text).matches() ? Long.parseLong(text) : null);
        registrar.parser("boolean", text -> switch (text.toLowerCase(Locale.ROOT)) {
            case "true", "yes", "on" -> Boolean.TRUE;
            case "false", "no", "off" -> Boolean.FALSE;
            default -> null;
        });
        registrar.parser("player", CoreTypes::player);
        registrar.parser("offlineplayer", CoreTypes::offlinePlayer);
        // An entity by its UUID: '{_uuid} parsed as entity'.
        registrar.parser("entity", text -> {
            try {
                return Bukkit.getEntity(UUID.fromString(text.strip()));
            } catch (IllegalArgumentException notUuid) {
                return null;
            }
        });
        registrar.parser("world", Bukkit::getWorld);
        registrar.parser("uuid", text -> {
            try {
                return UUID.fromString(text);
            } catch (IllegalArgumentException e) {
                return null;
            }
        });

        // How they read in text: players and worlds by name (a player the server has no name for, by UUID), the
        // console as Skript writes it.
        registrar.text(OfflinePlayer.class, player -> player.getName() == null ? player.getUniqueId().toString()
                : player.getName());
        registrar.text(World.class, World::getName);
        registrar.text(CommandSender.class, sender -> sender instanceof org.bukkit.command.ConsoleCommandSender
                ? "the console" : sender.getName());
    }
}
