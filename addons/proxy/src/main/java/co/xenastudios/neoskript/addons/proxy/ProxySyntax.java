/*
 * Copyright (c) 2026 Xena Studios
 * SPDX-License-Identifier: GPL-3.0-only
 */
package co.xenastudios.neoskript.addons.proxy;

import co.xenastudios.neoskript.api.element.PausingExpression;
import co.xenastudios.neoskript.api.element.TriggerContext;
import co.xenastudios.neoskript.api.registration.Registrar;
import co.xenastudios.neoskript.api.registration.ThreadAffinity;
import java.time.Duration;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.function.Function;
import java.util.logging.Logger;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.OfflinePlayer;

/**
 * The syntax: NeoSkriptProxy's own (issue #19's), and the community addons' it takes, word for word:
 * SkProxy 2.4 ({@code player count of}, {@code status of}, {@code proxy player count}), Skelocity 1.1 as released
 * (player counts, players, a player's server, broadcasts and kicks) and SkRedis 2.3.5's
 * pub/sub ({@code send redis message}, {@code on redis message} and its values). Skript's own
 * {@code connect %players% to server %text%} is NeoSkript's already.
 */
final class ProxySyntax {

    private static final String ID = "NeoSkriptProxy";

    private ProxySyntax() {}

    static void register(Registrar registrar, Network network, PluginMessaging messaging, Logger logger,
            long proxyTimeoutMillis) {
        Duration wait = Duration.ofMillis(proxyTimeoutMillis + 2_000);

        // Messages arriving.
        registrar.event(ID + "EvtNetwork", List.of(NetworkMessageEvent.class), info -> (context, args) -> {
            if (!(context.event() instanceof NetworkMessageEvent event)
                    || event.kind() == NetworkMessageEvent.Kind.CUSTOM) {
                return false;
            }
            return args[0] == null || Arrays.stream((Object[]) args[0]).anyMatch(event.channel()::equals);
        }, "network message [(on|in|from) [the] channel[s] %-strings%]");
        registrar.event(ID + "EvtRedis", List.of(NetworkMessageEvent.class), info -> (context, args) ->
                context.event() instanceof NetworkMessageEvent event && event.kind() == NetworkMessageEvent.Kind.REDIS,
                "redis message");
        registrar.event(ID + "EvtCustomRedis", List.of(NetworkMessageEvent.class), info -> (context, args) ->
                context.event() instanceof NetworkMessageEvent event && event.kind() == NetworkMessageEvent.Kind.CUSTOM,
                "custom redis message");
        value(registrar, "ExprNetworkMessage", event -> event.kind() != NetworkMessageEvent.Kind.CUSTOM
                ? event.message() : null, "[the] network message");
        value(registrar, "ExprNetworkChannel", NetworkMessageEvent::channel, "[the] network channel");
        value(registrar, "ExprNetworkSender", NetworkMessageEvent::origin,
                "[the] network (sender|origin) [server]");
        // SkRedis: the message of a redis message or a custom one, its channel, and its date as java.util.Date writes it.
        value(registrar, "ExprRedisMessage", NetworkMessageEvent::message, "redis message");
        value(registrar, "ExprRedisChannel", NetworkMessageEvent::channel, "redis channel");
        value(registrar, "ExprRedisDate", event -> event.kind() == NetworkMessageEvent.Kind.REDIS
                ? new java.util.Date(event.sent()).toString() : null,
                "redis message (date|timestamp|unix|time|unixtime|unix time)");

        // Messages leaving.
        registrar.effect(ID + "EffSend", info -> {
            boolean all = info.pattern() == 0;
            return (context, args) -> {
                if (!(args[0] instanceof String message) || !(args[1] instanceof String channel)) {
                    return;
                }
                List<String> servers = all ? null
                        : Arrays.stream((Object[]) args[2]).filter(String.class::isInstance).map(String.class::cast).toList();
                try {
                    if (!network.send(channel, message, servers)) {
                        logger.warning("A network message on " + channel + " was dropped: nobody is online to carry it"
                                + " and Redis is off, or too many are waiting.");
                    }
                } catch (IllegalArgumentException tooLong) {
                    logger.warning(tooLong.getMessage());
                }
            };
        }, "send [the] [network] message %string% (on|in) [the] channel %string% to all [[of] the] [other] servers",
                "send [the] [network] message %string% (on|in) [the] channel %string% to [the] server[s] %strings%");
        registrar.effect(ID + "EffSendRedis", info -> (context, args) -> {
            if (args[0] instanceof String message && args[1] instanceof String channel) {
                if (!network.sendRedis(channel, message)) {
                    logger.warning("A redis message on " + channel + " was dropped: Redis is off or down.");
                }
            } else {
                logger.severe((args[0] == null ? "Message" : "Channel") + " was empty. Please check your code.");
            }
        }, "(send|publish) (redis|pubsub) (message|msg) %string% to [channel] %string%");
        registrar.effect(ID + "EffSendCustomRedis", info -> (context, args) -> {
            if (args[0] instanceof String message && args[1] instanceof String channel) {
                try {
                    network.sendCustomRedis(channel, message);
                } catch (IllegalArgumentException notJson) {
                    logger.warning(notJson.getMessage());
                }
            } else {
                logger.severe((args[0] == null ? "Message" : "Channel") + " was empty. Please check your code.");
            }
        }, "(send|publish) custom (redis|pubsub) (message|msg) %string% to [channel] %string%");

        // Proxy actions: Skelocity 1.1's broadcast and kick.
        registrar.effect(ID + "EffBroadcast", info -> (context, args) -> {
            if (args[0] instanceof String message) {
                messaging.send(PluginMessaging.action("Message", "ALL", legacy(message)));
            }
        }, "(proxy|velocity|global|network) broadcast %string%");
        registrar.effect(ID + "EffKick", info -> (context, args) -> {
            if (args[0] instanceof OfflinePlayer player && player.getName() != null) {
                String reason = args[1] instanceof String text ? text : "No reason specified";
                messaging.send(PluginMessaging.action("KickPlayer", player.getName(), legacy(reason)));
            }
        }, "(proxy|velocity) kick %player% [(with reason|due to) %-string%]");

        // Questions to the proxy, as expressions that wait for the answer. NeoSkriptProxy's own say "network", and
        // give nothing where the proxy doesn't answer.
        question(registrar, "ExprServers", "string", args -> PluginMessaging.Question.servers(), messaging, wait,
                answer -> answer, "[all [of]] [the] network servers");
        question(registrar, "ExprPlayerCount", "integer", args -> PluginMessaging.Question.playerCount(
                args[0] instanceof String server ? server : "ALL"), messaging, wait, answer -> answer,
                "[the] network player count [(of|on) [[the] server] %-string%]");
        question(registrar, "ExprPlayers", "string", args -> PluginMessaging.Question.playerList(
                args[0] instanceof String server ? server : "ALL"), messaging, wait, answer -> answer,
                "[all [of]] [the] network players [(of|on) [[the] server] %-string%]");
        question(registrar, "ExprPlayerServer", "string", args -> args[0] instanceof OfflinePlayer player
                && player.getName() != null ? PluginMessaging.Question.playerServer(player.getName()) : null,
                messaging, wait, answer -> answer, "[the] network server of %offlineplayer%");
        question(registrar, "ExprServerName", "string", args -> PluginMessaging.Question.serverName(), messaging,
                wait, answer -> {
                    network.learnName(answer instanceof String name ? name : null);
                    return answer;
                }, "[the] [this] server's network name", "[the] network name of this server");
        // SkProxy 2.4's and Skelocity 1.1's: they answer at once, from the last answer the proxy gave, and ask
        // again in the background, so scripts can use them anywhere an expression goes (a server list ping's motd,
        // before cancel event). Before the first answer, what they give: 0, "Offline", nothing, "Unknown".
        cached(registrar, "ExprSkProxyPlayerCount", "integer", args -> args[0] instanceof String server
                ? PluginMessaging.Question.playerCount(server) : null, messaging, 0L,
                "player count of %string%", "[the] [total] player count of [(proxy|velocity)] server %string%");
        registrar.expression(ID + "ExprSkProxyStatus", "string", info -> (context, args) -> {
            if (!(args[0] instanceof String server)) {
                return "Offline";
            }
            PluginMessaging.Question question = PluginMessaging.Question.playerCount(server);
            messaging.cached(question);
            return messaging.answered(question) ? "Online" : "Offline";
        }, "status of %string%");
        cached(registrar, "ExprSkProxyTotal", "integer", args -> PluginMessaging.Question.playerCount("ALL"),
                messaging, 0L, "[the] [total] [(proxy|velocity)] proxy player count");
        cached(registrar, "ExprSkelocityPlayers", "string", args -> PluginMessaging.Question.playerList(
                args[0] instanceof String server ? server : "ALL"), messaging, List.of(),
                "[all] (proxy|velocity) players [on [server] %-string%]");
        cached(registrar, "ExprSkelocityServer", "string", args -> args[0] instanceof OfflinePlayer player
                && player.getName() != null ? PluginMessaging.Question.playerServer(player.getName()) : null,
                messaging, "Unknown", "[the] [(sk|v)elocity] server of %player%");

        for (String id : List.of("EffSend", "EffSendRedis", "EffSendCustomRedis", "EffBroadcast", "EffKick",
                "ExprNetworkMessage", "ExprNetworkChannel", "ExprNetworkSender", "ExprRedisMessage", "ExprRedisChannel",
                "ExprRedisDate", "ExprServers", "ExprPlayerCount", "ExprPlayers", "ExprPlayerServer", "ExprServerName",
                "ExprSkProxyPlayerCount", "ExprSkProxyStatus", "ExprSkProxyTotal", "ExprSkelocityPlayers",
                "ExprSkelocityServer")) {
            // They read names, or nothing of the world.
            registrar.affinity(ID + id, ThreadAffinity.ANY);
        }
    }

    /** A value of the network message being handled. */
    private static void value(Registrar registrar, String id, Function<NetworkMessageEvent, String> get,
            String pattern) {
        registrar.expression(ID + id, "string", info -> (context, args) ->
                context.event() instanceof NetworkMessageEvent event ? get.apply(event) : null, pattern);
    }

    /** A question to the proxy, as an expression that waits for the answer: none if it didn't come. */
    private static void question(Registrar registrar, String id, String type,
            Function<Object[], PluginMessaging.Question> question, PluginMessaging messaging, Duration wait,
            Function<Object, Object> answer, String... patterns) {
        registrar.pausingExpression(ID + id, type, info -> (PausingExpression) (context, args) ->
                ask(messaging, question.apply(args)).thenApply(answer), patterns);
        registrar.timeout(ID + id, wait);
    }

    /** An original's expression: the last answer at once (or what it gives before one), asked again behind. */
    private static void cached(Registrar registrar, String id, String type,
            Function<Object[], PluginMessaging.Question> question, PluginMessaging messaging, Object before,
            String... patterns) {
        registrar.expression(ID + id, type, info -> (context, args) -> {
            PluginMessaging.Question asked = question.apply(args);
            Object answer = asked == null ? null : messaging.cached(asked);
            Object value = answer != null ? answer : before;
            return value instanceof List<?> list ? list.toArray() : value;
        }, patterns);
    }

    private static CompletionStage<Object> ask(PluginMessaging messaging, PluginMessaging.Question question) {
        if (question == null) {
            return CompletableFuture.completedFuture(null);
        }
        return messaging.ask(question).thenApply(answer -> answer == PluginMessaging.NO_ANSWER ? null : answer);
    }

    /** As Skelocity sends text to the proxy: legacy codes as they are, MiniMessage turned into them. */
    static String legacy(String text) {
        if (text == null || text.isEmpty()) {
            return "";
        }
        if (text.contains("§") || text.contains("&")) {
            return text.replace('&', '§');
        }
        try {
            return LegacyComponentSerializer.legacySection().serialize(MiniMessage.miniMessage().deserialize(text));
        } catch (RuntimeException e) {
            return text;
        }
    }

    /** For tests: the trigger as an event. */
    static TriggerContext context(Object event) {
        return () -> event;
    }
}
