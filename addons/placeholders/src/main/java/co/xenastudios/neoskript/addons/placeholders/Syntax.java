/*
 * Copyright (c) 2026 Xena Studios
 * SPDX-License-Identifier: GPL-3.0-only
 */
package co.xenastudios.neoskript.addons.placeholders;

import co.xenastudios.neoskript.api.NeoSkript;
import co.xenastudios.neoskript.api.element.Changeable;
import co.xenastudios.neoskript.api.element.ElementInfo;
import co.xenastudios.neoskript.api.element.Expression;
import co.xenastudios.neoskript.api.element.SyntaxError;
import co.xenastudios.neoskript.api.element.TriggerContext;
import co.xenastudios.neoskript.api.registration.Registrar;
import co.xenastudios.neoskript.api.registration.ThreadAffinity;
import java.util.ArrayList;
import java.util.List;
import java.util.function.BooleanSupplier;
import java.util.function.Function;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;

/**
 * skript-placeholders' syntax (1.7.1), as NeoSkript elements: the placeholder structure scripts register
 * placeholders with, what its triggers read and set, and the value of any placeholder for a player.
 */
final class Syntax {

    static final String EVENT = "NeoSkriptPlaceholdersEvtPlaceholder";
    static final String NOT_INSTALLED = " placeholders can not be created because the plugin is not installed.";

    private Syntax() {}

    /**
     * Registers the syntax.
     *
     * @param registrar NeoSkript's registrar
     * @param placeholders the scripts' placeholders, or {@code null} without PlaceholderAPI
     * @param values the value of a placeholder for a player, or {@code null} without PlaceholderAPI
     * @param relationalValues the value of a relational placeholder for two players, or {@code null}
     */
    static void register(Registrar registrar, Placeholders placeholders, Values values, RelationalValues relationalValues) {
        BooleanSupplier installed = () -> placeholders != null;
        registrar.event(EVENT, List.of(PlaceholderRequestEvent.class, RelationalPlaceholderRequestEvent.class),
                info -> filter(info, installed.getAsBoolean()),
                // skript-placeholders 1.7's structure.
                "(placeholder[ ]api|papi) [:relational] placeholder (with|for) [the] prefix %*string%",
                "(mvdw[ ]placeholder[ ]api|mvdw) placeholder [with [the] name|named] %*string%",
                // 1.6's, which it no longer reads.
                "(placeholder[ ]api|papi) [placeholder] request (for|with) [the] prefix[es] %*strings%",
                "(mvdw[ ]placeholder[ ]api|mvdw) [placeholder] request (for|with) [the] placeholder[s] %*strings%");
        if (placeholders != null) {
            registrar.eventTriggers(EVENT, placeholders::update);
        }
        registrar.eventValue(PlaceholderRequestEvent.class, "player", PlaceholderRequestEvent::player);
        registrar.eventValue(PlaceholderRequestEvent.class, "offlineplayer", PlaceholderRequestEvent::offlinePlayer);

        registrar.expression("NeoSkriptPlaceholdersExprPlaceholder", "string", Syntax::placeholder,
                "[the] placeholder", "[the] [placeholder] (1:prefix|2:identifier)");
        registrar.affinity("NeoSkriptPlaceholdersExprPlaceholder", ThreadAffinity.ANY);
        registrar.expression("NeoSkriptPlaceholdersExprResult", "string", info -> {
            if (!info.isEvent(PlaceholderRequestEvent.class)) {
                throw new SyntaxError("'the placeholder result' can only be used in custom placeholders");
            }
            return new Result();
        }, "[the] [placeholder] result");
        registrar.affinity("NeoSkriptPlaceholdersExprResult", ThreadAffinity.ANY);
        registrar.expression("NeoSkriptPlaceholdersExprRelationalPlayers", "player", info -> {
            boolean first = info.pattern() == 0;
            if (!info.isEvent(RelationalPlaceholderRequestEvent.class)) {
                throw new SyntaxError("'the " + (first ? "first" : "second")
                        + " player' can only be used in custom relational placeholders.");
            }
            return (context, arguments) -> context.event() instanceof RelationalPlaceholderRequestEvent event
                    ? (first ? event.first() : event.second()) : null;
        }, "[the] first player", "[the] second player");
        registrar.affinity("NeoSkriptPlaceholdersExprRelationalPlayers", ThreadAffinity.ANY);
        // As in Skript, where they fail to set up outside placeholders and it tries other syntax: 'the result' in a
        // smelting event is still the smelted item.
        registrar.onlyInEvents("NeoSkriptPlaceholdersExprPlaceholder", -1, PlaceholderRequestEvent.class);
        registrar.onlyInEvents("NeoSkriptPlaceholdersExprResult", -1, PlaceholderRequestEvent.class);
        registrar.onlyInEvents("NeoSkriptPlaceholdersExprRelationalPlayers", -1, RelationalPlaceholderRequestEvent.class);
        // Given online players, it runs on their thread, as NeoSkript's region rules want: PlaceholderAPI's
        // expansions read the player.
        registrar.expression("NeoSkriptPlaceholdersExprValue", "string", info -> value(info, values, relationalValues),
                "[the] [value[s] of [the]] [:relational] placeholder[s] [in] %strings% [(for|from|of) %-players/offlineplayers%]",
                "[:relational] placeholder[s] %strings%'[s] value[s] [(for|from|of) %-players/offlineplayers%]",
                // 1.6's, which 1.7 no longer reads.
                "parse placeholder[s] %strings% [(for|as) %-players/offlineplayers%]");
    }

    /** The value of a placeholder for a player, or {@code null}. */
    @FunctionalInterface
    interface Values {
        String value(String placeholder, OfflinePlayer player);
    }

    /** The value of a relational placeholder for two players, or {@code null}. */
    @FunctionalInterface
    interface RelationalValues {
        String value(String placeholder, Player first, Player second);
    }

    /** The structure's line: which prefixes, and whether it's relational. Errors as skript-placeholders words them. */
    static RequestFilter filter(ElementInfo info, boolean installed) {
        boolean mvdw = info.pattern() == 1 || info.pattern() == 3;
        if (mvdw) {
            // MVdWPlaceholderAPI is gone: no MVdW plugin runs on today's servers, and it needs one.
            throw new SyntaxError("MVdWPlaceholderAPI" + NOT_INSTALLED);
        }
        if (!installed) {
            throw new SyntaxError("PlaceholderAPI" + NOT_INSTALLED);
        }
        String prefix = info.argumentLiteral(0);
        if (prefix == null) {
            throw new SyntaxError("Write one prefix for each placeholder structure, as a plain text: "
                    + info.textOr("this one") + " has " + (info.isList(0) ? "several" : "none"));
        }
        String error = invalid(prefix);
        if (error != null) {
            throw new SyntaxError(error);
        }
        return new RequestFilter(List.of(prefix), info.hasTag("relational"));
    }

    /** Why a prefix can't be PlaceholderAPI's, as skript-placeholders says, or {@code null} if it can. */
    static String invalid(String prefix) {
        if (prefix.isBlank()) {
            return "A prefix cannot be blank";
        }
        for (char c : prefix.toCharArray()) {
            if (c == '%' || c == '{' || c == '}' || c == '_') {
                return "PlaceholderAPI prefixes cannot contain the character '" + c + "'";
            }
        }
        return null;
    }

    private static Expression placeholder(ElementInfo info) {
        // '2:identifier' is a parse mark in Skript, and a tag here.
        boolean identifier = (info.mark() & 2) != 0 || info.hasTag("2") || info.hasTag("identifier");
        String part = info.pattern() == 0 ? "placeholder" : identifier ? "identifier" : "prefix";
        if (!info.isEvent(PlaceholderRequestEvent.class)) {
            throw new SyntaxError("'the " + part + "' can only be used in custom placeholders");
        }
        Function<PlaceholderRequestEvent, String> getter = switch (part) {
            case "identifier" -> PlaceholderRequestEvent::identifier;
            case "prefix" -> PlaceholderRequestEvent::prefix;
            default -> PlaceholderRequestEvent::placeholder;
        };
        return (context, arguments) -> context.event() instanceof PlaceholderRequestEvent event ? getter.apply(event)
                : null;
    }

    /** {@code the result}: set, deleted or reset; any value becomes its text. */
    static final class Result implements Expression, Changeable {

        @Override
        public Object get(TriggerContext context, Object[] arguments) {
            return context.event() instanceof PlaceholderRequestEvent event ? event.result() : null;
        }

        @Override
        public Class<?>[] acceptChange(Mode mode) {
            return switch (mode) {
                case SET -> new Class<?>[] {Object.class};
                case DELETE, RESET -> new Class<?>[0];
                default -> null;
            };
        }

        @Override
        public boolean takesOne(Mode mode) {
            return true;
        }

        @Override
        public void change(TriggerContext context, Object[] arguments, Object[] delta, Mode mode) {
            if (!(context.event() instanceof PlaceholderRequestEvent event)) {
                return;
            }
            if (mode == Mode.SET && delta != null && delta.length > 0 && delta[0] != null) {
                event.result(delta[0] instanceof String text ? text : text(delta[0]));
            } else if (mode == Mode.DELETE || mode == Mode.RESET) {
                event.result(null);
            }
        }

        private static String text(Object value) {
            try {
                return NeoSkript.get().values().text(value);
            } catch (RuntimeException | LinkageError none) {
                return String.valueOf(value);
            }
        }
    }

    private static Expression value(ElementInfo info, Values values, RelationalValues relationalValues) {
        boolean relational = info.hasTag("relational");
        if (relational && (info.isSingle(1) || info.isDefault(1))) {
            throw new SyntaxError("There must be two players provided to use relational placeholders.");
        }
        return (context, arguments) -> {
            Object[] placeholders = all(arguments[0]);
            if (values == null) {
                return new Object[0];
            }
            List<Object> found = new ArrayList<>();
            if (relational) {
                Object[] players = all(arguments.length > 1 ? arguments[1] : null);
                if (players.length == 2 && online(players[0]) instanceof Player one
                        && online(players[1]) instanceof Player two) {
                    for (Object placeholder : placeholders) {
                        if (placeholder instanceof String text) {
                            add(found, () -> relationalValues.value(text, one, two));
                        }
                    }
                }
                return found.toArray();
            }
            Object[] players = arguments.length > 1 && arguments[1] != null ? all(arguments[1]) : new Object[] {null};
            for (Object player : players) {
                if (player != null && !(player instanceof OfflinePlayer)) {
                    continue;
                }
                for (Object placeholder : placeholders) {
                    if (placeholder instanceof String text) {
                        add(found, () -> values.value(text, (OfflinePlayer) player));
                    }
                }
            }
            return found.toArray();
        };
    }

    /** Adds a placeholder's value, if it has one. One that throws has none, rather than failing the line. */
    private static void add(List<Object> found, java.util.function.Supplier<String> value) {
        String text;
        try {
            text = value.get();
        } catch (RuntimeException | LinkageError e) {
            text = null;
        }
        if (text != null) {
            found.add(text);
        }
    }

    private static Object online(Object player) {
        return player instanceof OfflinePlayer offline ? offline.getPlayer() : null;
    }

    private static Object[] all(Object value) {
        if (value == null) {
            return new Object[0];
        }
        return value instanceof Object[] array ? array : new Object[] {value};
    }
}
