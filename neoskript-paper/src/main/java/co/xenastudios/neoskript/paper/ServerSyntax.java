/*
 * Copyright (c) 2026 Xena Studios
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package co.xenastudios.neoskript.paper;

import static co.xenastudios.neoskript.paper.PlayerSyntax.each;
import static co.xenastudios.neoskript.paper.PlayerSyntax.inEvent;
import static co.xenastudios.neoskript.paper.PlayerSyntax.owners;

import co.xenastudios.neoskript.api.element.Changeable;
import co.xenastudios.neoskript.api.element.Condition;
import co.xenastudios.neoskript.api.element.Expression;
import co.xenastudios.neoskript.api.element.SyntaxError;
import co.xenastudios.neoskript.api.element.TriggerContext;
import co.xenastudios.neoskript.api.registration.Registrar;
import co.xenastudios.neoskript.runtime.Timespan;
import co.xenastudios.neoskript.runtime.Values;
import com.destroystokyo.paper.event.server.PaperServerListPingEvent;
import io.papermc.paper.event.player.AsyncChatEvent;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;
import net.kyori.adventure.audience.Audience;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import org.bukkit.event.player.PlayerKickEvent;
import org.bukkit.event.player.PlayerLoginEvent;
import org.bukkit.event.server.ServerListPingEvent;
import org.bukkit.util.CachedServerIcon;

/**
 * The server: its players (offline, banned, operators, how many), plugins, TPS and memory, logging, stopping it,
 * what the server list shows, and a few conditions that don't belong to anything (chance, dates, text).
 */
final class ServerSyntax {

    private ServerSyntax() {}

    /** The icon loaded last, for {@code the last loaded server icon}. */
    private static volatile CachedServerIcon lastLoadedIcon;

    /** Open log files, by their name in the logs folder. */
    private static final Map<String, PrintWriter> LOGS = new ConcurrentHashMap<>();

    static void register(Registrar registrar) {
        registrar.type("cachedservericon", "server icon", CachedServerIcon.class);
        registerPlayers(registrar);
        registerServer(registrar);
        registerServerList(registrar);
        registerEvents(registrar);
        registerConditions(registrar);
    }

    private static void registerPlayers(Registrar registrar) {
        registrar.expression("ExprAllBannedEntries", "object", info -> {
            boolean ips = info.hasTag("ips");
            return (context, args) -> ips ? Bukkit.getIPBans().toArray() : Bukkit.getBannedPlayers().toArray();
        }, "[all [[of] the]|the] banned (players|ips:(ips|ip addresses))");
        registrar.expression("ExprOfflinePlayers", "offlineplayer", info -> (context, args) -> Bukkit.getOfflinePlayers(),
                "[(all [[of] the]|the)] offline[ ]players");
        registrar.expression("ExprOps", "offlineplayer", info -> new Operators(info.hasTag("non")),
                "[all [[of] the]|the] [server] [:non(-| )]op[erator]s");
        registrar.expression("ExprOnlinePlayersCount", "integer", info -> {
            boolean ping = inEvent(info, PaperServerListPingEvent.class);
            if (info.mark() == 2 && !ping) {
                throw new SyntaxError("The 'fake' online players count expression can't be used outside of a server list ping event");
            }
            return new PlayerCount(info.mark() == 0 && !ping || info.mark() == 1, false);
        }, "[the] [(1¦(real|default)|2¦(fake|shown|displayed))] [online] player (count|amount|number)",
                "[the] [(1¦(real|default)|2¦(fake|shown|displayed))] (count|amount|number|size) of online players");
    }

    private static void registerServer(Registrar registrar) {
        registrar.expression("ExprPlugins", "string", info -> (context, args) -> {
            List<Object> names = new ArrayList<>();
            for (org.bukkit.plugin.Plugin plugin : Bukkit.getPluginManager().getPlugins()) {
                names.add(plugin.getName());
            }
            return names.toArray();
        }, "[(all [[of] the]|the)] [loaded] plugins");
        registrar.expression("ExprTPS", "number", info -> {
            int index = info.pattern();
            return (context, args) -> {
                double[] tps = Bukkit.getServer().getTPS();
                if (index != 3) {
                    return tps[index];
                }
                return new Object[] {tps[0], tps[1], tps[2]};
            };
        }, "tps from [the] last ([1] minute|1[ ]m[inute])", "tps from [the] last 5[ ]m[inutes]",
                "tps from [the] last 15[ ]m[inutes]", "[the] tps");
        registrar.expression("ExprMemory", "number", info -> {
            String kind = info.hasTag("free") ? "free" : info.hasTag("max") ? "max" : "total";
            return (context, args) -> {
                Runtime runtime = Runtime.getRuntime();
                double bytes = switch (kind) {
                    case "free" -> runtime.freeMemory();
                    case "max" -> runtime.maxMemory();
                    default -> runtime.totalMemory();
                };
                return bytes * 1E-6;
            };
        }, "[the] [server] (:free|max:max[imum]|total) (memory|ram)");
        registrar.effect("EffStopServer", info -> {
            boolean restart = info.pattern() == 1;
            return (context, args) -> {
                if (restart) {
                    Bukkit.spigot().restart();
                } else {
                    Bukkit.shutdown();
                }
            };
        }, "(stop|shut[ ]down) [the] server", "restart [the] server");
        registrar.effect("EffLog", info -> {
            Level level = info.mark() == 1 ? Level.WARNING : info.mark() == 2 ? Level.SEVERE : Level.INFO;
            return (context, args) -> {
                for (Object message : owners(args[0])) {
                    String text = Values.text(message);
                    if (args[1] == null) {
                        Bukkit.getLogger().log(level, "[---] " + text);
                        continue;
                    }
                    for (Object file : owners(args[1])) {
                        log(Values.text(file), level, text);
                    }
                }
            };
        }, "log %strings% [(to|in) [file[s]] %-strings%] [with [the|a] severity [of] (1¦warning|2¦severe)]");
        registrar.effect("EffLoadServerIcon", info -> (context, args) -> {
            Object path = Values.single(args[0]);
            if (path == null) {
                return;
            }
            Path file = Path.of(Values.text(path));
            if (Files.isRegularFile(file)) {
                try {
                    lastLoadedIcon = Bukkit.loadServerIcon(file.toFile());
                } catch (Exception ignored) {
                    // Not a 64x64 image: nothing is loaded.
                }
            }
        }, "load [the] server icon (from|of) [the] [image] [file] %string%");
        registrar.expression("ExprLastLoadedServerIcon", "cachedservericon", info -> (context, args) -> lastLoadedIcon,
                "[the] [last[ly]] loaded server icon");
    }

    /** Writes a line to a file in the plugin's logs folder, or to the server log for {@code server.log}. */
    private static void log(String name, Level level, String message) {
        String file = name.toLowerCase(Locale.ENGLISH);
        if (!file.endsWith(".log")) {
            file += ".log";
        }
        if (file.equals("server.log")) {
            Bukkit.getLogger().log(level, message);
            return;
        }
        PrintWriter writer = LOGS.get(file);
        if (writer == null) {
            File target = new File(new File(PlayerSyntax.plugin().getDataFolder(), "logs"), file);
            try {
                target.getParentFile().mkdirs();
                writer = new PrintWriter(new BufferedWriter(new FileWriter(target, true)));
                PrintWriter existing = LOGS.putIfAbsent(file, writer);
                if (existing != null) {
                    writer.close();
                    writer = existing;
                }
            } catch (IOException e) {
                co.xenastudios.neoskript.runtime.RuntimeErrors.report("Cannot write to log file '" + file + "' ("
                        + target.getPath() + "): " + e);
                return;
            }
        }
        String timestamp = java.text.DateFormat.getDateTimeInstance(java.text.DateFormat.SHORT, java.text.DateFormat.MEDIUM)
                .format(new java.util.Date());
        synchronized (writer) {
            writer.println((level == Level.INFO ? "[" + timestamp + "]" : "[" + timestamp + " " + level + "]") + " " + message);
            writer.flush();
        }
    }

    /** What the server list shows when it's pinged: its icon, version, protocol, players. */
    private static void registerServerList(Registrar registrar) {
        registrar.expression("ExprServerIcon", "cachedservericon", info -> {
            boolean ping = inEvent(info, PaperServerListPingEvent.class);
            boolean defaultIcon = info.mark() == 0 && !ping || info.mark() == 1;
            if (!ping && !defaultIcon) {
                throw new SyntaxError("The 'shown' server icon expression can't be used outside of a server list ping event");
            }
            return new ServerIcon(defaultIcon);
        }, "[the] [(1¦(default)|2¦(shown|sent))] [server] icon");
        registrar.effect("EffPlayerInfoVisibility", info -> {
            boolean hide = info.pattern() == 0;
            return (context, args) -> {
                if (context.event() instanceof PaperServerListPingEvent ping) {
                    ping.setHidePlayers(hide);
                }
            };
        }, "hide [all] player [related] info[rmation] [(in|on|from) [the] server list]",
                "(show|reveal) [all] player [related] info[rmation] [(in|to|on|from) [the] server list]");
        registrar.effect("EffHidePlayerFromServerList", info -> (context, args) -> {
            if (!(context.event() instanceof ServerListPingEvent ping)) {
                return;
            }
            List<Object> hidden = List.of(owners(args[0]));
            for (Iterator<Player> players = ping.iterator(); players.hasNext(); ) {
                if (hidden.contains(players.next())) {
                    players.remove();
                }
            }
        }, "hide %players% (in|on|from) [the] server list", "hide %players%'[s] info[rmation] (in|on|from) [the] server list");
        registrar.expression("ExprProtocolVersion", "integer", info -> new PingValue(
                ping -> (long) ping.getProtocolVersion(), (ping, value) -> ping.setProtocolVersion(((Number) value).intValue()),
                Number.class), "[the] [server] [(sent|required|fake)] protocol version [number]");
        registrar.expression("ExprVersionString", "string", info -> new PingValue(PaperServerListPingEvent::getVersion,
                (ping, value) -> ping.setVersion(Values.text(value)), String.class), "[the] [shown|custom] version [string|text]");
    }

    /** Values of other events: the connecting player's host name, a chat message's recipients, a kick's message. */
    private static void registerEvents(Registrar registrar) {
        registrar.expression("ExprHostname", "string", info -> (context, args) ->
                context.event() instanceof PlayerLoginEvent login ? login.getHostname() : null, "[the] (host|domain)[ ][name]");
        registrar.expression("ExprChatRecipients", "audience", info -> new ChatRecipients(), "[the] [chat( | -)]recipients");
        registrar.expression("ExprOnScreenKickMessage", "string", info -> new KickMessage(), "[the] on-screen kick message");
    }

    private static void registerConditions(Registrar registrar) {
        registrar.condition("CondIsPluginEnabled", info -> {
            int pattern = info.pattern();
            boolean and = info.and(0);
            return (context, args) -> Condition.check(owners(args[0]), and, name -> {
                org.bukkit.plugin.Plugin plugin = Bukkit.getPluginManager().getPlugin(Values.text(name));
                return switch (pattern) {
                    case 1 -> plugin == null || !plugin.isEnabled();
                    case 2 -> plugin != null && !plugin.isEnabled();
                    default -> plugin != null && plugin.isEnabled();
                };
            });
        }, "plugin[s] %strings% (is|are) enabled", "plugin[s] %strings% (is|are)(n't| not) enabled",
                "plugin[s] %strings% (is|are) disabled");
        registrar.condition("CondChance", info -> {
            boolean percent = info.mark() == 1;
            boolean fail = info.hasTag("fail");
            return (context, args) -> {
                if (!(Values.single(args[0]) instanceof Number chance)) {
                    return false;
                }
                boolean result = Math.random() < (percent ? chance.doubleValue() / 100 : chance.doubleValue());
                return result != fail;
            };
        }, "chance of %number%(1¦\\%|) [fail:(fails|failed)]");
        registrar.condition("CondDate", info -> {
            boolean negated = info.pattern() == 1;
            boolean and = info.and(0);
            boolean spansAnd = info.and(1);
            return (context, args) -> {
                long now = System.currentTimeMillis();
                Object[] spans = owners(args[1]);
                return negated != Condition.check(owners(args[0]), and, date -> date instanceof co.xenastudios.neoskript.runtime.Date d
                        && Condition.check(spans, spansAnd, span -> span instanceof Timespan t && now - d.millis() >= t.millis()));
            };
        }, "%date% (was|were)( more|(n't| not) less) than %timespan% [ago]",
                "%date% (was|were)((n't| not) more| less) than %timespan% [ago]");
        registrar.condition("CondAlphanumeric", info -> {
            boolean negated = info.pattern() == 1;
            boolean and = info.and(0);
            return (context, args) -> negated != Condition.check(owners(args[0]), and, text -> alphanumeric(Values.text(text)));
        }, "%strings% (is|are) alphanumeric", "%strings% (isn't|is not|aren't|are not) alphanumeric");
    }

    /** Whether text is only letters and digits, as Skript's {@code StringUtils.isAlphanumeric}; empty text is. */
    private static boolean alphanumeric(String text) {
        for (int i = 0; i < text.length(); i++) {
            if (!Character.isLetterOrDigit(text.charAt(i))) {
                return false;
            }
        }
        return true;
    }

    /** The server's operators, which can be changed; or the online players who aren't operators. */
    private record Operators(boolean nonOps) implements Expression, Changeable {
        @Override
        public Object get(TriggerContext context, Object[] arguments) {
            if (nonOps) {
                return Bukkit.getOnlinePlayers().stream().filter(player -> !player.isOp()).toArray();
            }
            return Bukkit.getOperators().toArray();
        }

        @Override
        public Class<?>[] acceptChange(Mode mode) {
            if (nonOps) {
                return null;
            }
            return switch (mode) {
                case ADD, SET, REMOVE, RESET, DELETE -> new Class<?>[] {OfflinePlayer.class};
                default -> null;
            };
        }

        @Override
        public void change(TriggerContext context, Object[] arguments, Object[] delta, Mode mode) {
            if (mode == Mode.SET || mode == Mode.DELETE || mode == Mode.RESET) {
                Bukkit.getOperators().forEach(player -> player.setOp(false));
            }
            if (delta != null && (mode == Mode.SET || mode == Mode.ADD || mode == Mode.REMOVE)) {
                each(delta, OfflinePlayer.class, player -> player.setOp(mode != Mode.REMOVE));
            }
        }
    }

    /**
     * The real or shown count of online or most players. The real max players can be changed; the shown ones in
     * a server list ping.
     */
    private record PlayerCount(boolean real, boolean max) implements Expression, Changeable {
        @Override
        public Object get(TriggerContext context, Object[] arguments) {
            if (real) {
                return (long) (max ? Bukkit.getMaxPlayers() : Bukkit.getOnlinePlayers().size());
            }
            if (max && context.event() instanceof ServerListPingEvent ping) {
                return (long) ping.getMaxPlayers();
            }
            return !max && context.event() instanceof PaperServerListPingEvent ping ? (long) ping.getNumPlayers() : null;
        }

        @Override
        public Class<?>[] acceptChange(Mode mode) {
            return switch (mode) {
                case SET, ADD, REMOVE -> real && !max ? null : new Class<?>[] {Number.class};
                case RESET, DELETE -> real ? null : new Class<?>[] {Number.class};
                default -> null;
            };
        }

        @Override
        public void change(TriggerContext context, Object[] arguments, Object[] delta, Mode mode) {
            int by = delta == null || delta.length == 0 ? 0 : ((Number) delta[0]).intValue();
            if (real) {
                int current = Bukkit.getMaxPlayers();
                Bukkit.setMaxPlayers(mode == Mode.SET ? by : mode == Mode.ADD ? current + by : current - by);
                return;
            }
            if (max && context.event() instanceof ServerListPingEvent ping) {
                int current = ping.getMaxPlayers();
                ping.setMaxPlayers(switch (mode) {
                    case SET -> by;
                    case ADD -> current + by;
                    case REMOVE -> current - by;
                    default -> Bukkit.getMaxPlayers();
                });
            } else if (!max && context.event() instanceof PaperServerListPingEvent ping) {
                int current = ping.getNumPlayers();
                ping.setNumPlayers(switch (mode) {
                    case SET -> by;
                    case ADD -> current + by;
                    case REMOVE -> current - by;
                    default -> Bukkit.getOnlinePlayers().size();
                });
            }
        }
    }

    /** The server's own icon, or the one a server list ping shows, which can be set there. */
    private record ServerIcon(boolean defaultIcon) implements Expression, Changeable {
        @Override
        public Object get(TriggerContext context, Object[] arguments) {
            CachedServerIcon icon = !defaultIcon && context.event() instanceof PaperServerListPingEvent ping
                    ? ping.getServerIcon() : defaultIcon ? Bukkit.getServerIcon() : null;
            return icon == null || icon.getData() == null ? null : icon;
        }

        @Override
        public Class<?>[] acceptChange(Mode mode) {
            return !defaultIcon && (mode == Mode.SET || mode == Mode.RESET) ? new Class<?>[] {CachedServerIcon.class} : null;
        }

        @Override
        public void change(TriggerContext context, Object[] arguments, Object[] delta, Mode mode) {
            if (context.event() instanceof PaperServerListPingEvent ping) {
                ping.setServerIcon(mode == Mode.SET && delta != null && delta.length > 0
                        ? (CachedServerIcon) delta[0] : Bukkit.getServerIcon());
            }
        }
    }

    /** A value of a server list ping, which can be set there. */
    private record PingValue(java.util.function.Function<PaperServerListPingEvent, Object> getter,
            java.util.function.BiConsumer<PaperServerListPingEvent, Object> setter, Class<?> valueClass)
            implements Expression, Changeable {
        @Override
        public Object get(TriggerContext context, Object[] arguments) {
            return context.event() instanceof PaperServerListPingEvent ping ? getter.apply(ping) : null;
        }

        @Override
        public Class<?>[] acceptChange(Mode mode) {
            return mode == Mode.SET ? new Class<?>[] {valueClass} : null;
        }

        @Override
        public void change(TriggerContext context, Object[] arguments, Object[] delta, Mode mode) {
            if (context.event() instanceof PaperServerListPingEvent ping && delta != null && delta.length > 0) {
                setter.accept(ping, delta[0]);
            }
        }
    }

    /** Who sees a chat message. */
    private static final class ChatRecipients implements Expression, Changeable {
        @Override
        public Object get(TriggerContext context, Object[] arguments) {
            return context.event() instanceof AsyncChatEvent chat ? chat.viewers().toArray() : new Object[0];
        }

        @Override
        public Class<?>[] acceptChange(Mode mode) {
            return switch (mode) {
                case ADD, SET, REMOVE, DELETE, RESET -> new Class<?>[] {Audience.class};
                default -> null;
            };
        }

        @Override
        public void change(TriggerContext context, Object[] arguments, Object[] delta, Mode mode) {
            if (!(context.event() instanceof AsyncChatEvent chat)) {
                return;
            }
            if (mode == Mode.SET || mode == Mode.DELETE || mode == Mode.RESET) {
                chat.viewers().clear();
            }
            if (mode == Mode.RESET) {
                chat.viewers().addAll(Bukkit.getOnlinePlayers());
            }
            if (delta != null) {
                for (Object audience : delta) {
                    if (mode == Mode.REMOVE) {
                        chat.viewers().remove((Audience) audience);
                    } else if (mode == Mode.ADD || mode == Mode.SET) {
                        chat.viewers().add((Audience) audience);
                    }
                }
            }
        }
    }

    /** The message a kicked player sees. */
    private static final class KickMessage implements Expression, Changeable {
        @Override
        public Object get(TriggerContext context, Object[] arguments) {
            return context.event() instanceof PlayerKickEvent kick ? Messages.LEGACY_SECTION.serialize(kick.reason()) : null;
        }

        @Override
        public Class<?>[] acceptChange(Mode mode) {
            return mode == Mode.SET || mode == Mode.DELETE ? new Class<?>[] {Object.class} : null;
        }

        @Override
        public void change(TriggerContext context, Object[] arguments, Object[] delta, Mode mode) {
            if (context.event() instanceof PlayerKickEvent kick) {
                kick.reason(delta == null || delta.length == 0 ? Component.empty() : Messages.component(delta[0]));
            }
        }
    }
}
