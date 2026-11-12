/*
 * Copyright (c) 2026 Xena Studios
 * SPDX-License-Identifier: GPL-3.0-only
 */
package co.xenastudios.neoskript.harness.bot;

import io.netty.bootstrap.Bootstrap;
import io.netty.bootstrap.ServerBootstrap;
import io.netty.channel.Channel;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.local.LocalAddress;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.util.Arrays;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;
import java.util.logging.Logger;
import java.util.regex.Pattern;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.EventLoopGroupHolder;
import net.minecraft.server.network.ServerHandshakePacketListenerImpl;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.plugin.Plugin;

/**
 * The bots and their console commands:
 *
 * <ul>
 *   <li>{@code bot join <name>}, {@code bot quit <name>}: connect and disconnect, through the server's own
 *       login path, so the login, join and quit events fire. {@code [bot] <name> joined} and
 *       {@code [bot] <name> left} are logged once the join and quit events have run.
 *   <li>{@code bot chat <name> <message>}, {@code bot command <name> <command>}: send a chat message or
 *       a command, as a client does.
 *   <li>{@code bot respawn|swap|use|punch <name>}, {@code bot drop <name> [all]},
 *       {@code bot slot <name> <0-8>}: respawn, swap hands, right-click and left-click the air, drop
 *       the held item or stack, and select a hotbar slot, also as a client does.
 *   <li>{@code bot op|deop <name>}, {@code bot teleport <name> <x> <y> <z>}, {@code bot damage <name>
 *       <amount>}: server-side changes, through the API on the player's thread.
 *   <li>{@code bot bench <name> <sneak|sprint|flight> <count>}: fires that toggle event for the player
 *       {@code count} times in a row on its thread, as a benchmark of the triggers listening to it, and logs
 *       {@code [bot] bench <event> <count> <nanoseconds>ns}.
 *   <li>{@code bot mspt}: logs {@code [bot] mspt <mean> <p95>}, the server's milliseconds per tick over its
 *       last 100 ticks, as a benchmark of everything the server runs.
 *   <li>{@code bot list}.
 * </ul>
 *
 * <p>Actions sent before a bot is in the world wait until it is, so {@code bot join} can be followed
 * by other commands straight away.
 */
final class Bots implements Listener {

    private static final Logger LOG = Bukkit.getLogger();
    private static final Pattern NAME = Pattern.compile("[A-Za-z0-9_]{3,16}");

    private final Plugin plugin;
    private final Map<String, Bot> bots = new ConcurrentHashMap<>();
    // Every bot that ever joined, as a bot that quit is gone from {@link #bots} before its quit event.
    private final Set<String> names = ConcurrentHashMap.newKeySet();
    private final EventLoopGroupHolder transport = EventLoopGroupHolder.local();
    private final Channel serverChannel;
    // Each bot gets its own loopback port, as separate clients would have.
    private final AtomicInteger nextPort = new AtomicInteger(50_000);

    Bots(Plugin plugin) {
        this.plugin = plugin;
        // An in-memory server channel whose connections are set up like the server's network
        // connections, and handed to the server to tick like them.
        serverChannel = new ServerBootstrap()
                .group(transport.eventLoopGroup())
                .channel(transport.serverChannelCls())
                .childHandler(new ChannelInitializer<>() {
                    @Override
                    protected void initChannel(Channel channel) {
                        MinecraftServer server = MinecraftServer.getServer();
                        Connection connection = new Connection(PacketFlow.SERVERBOUND);
                        InetSocketAddress address =
                                new InetSocketAddress(InetAddress.getLoopbackAddress(), nextPort.getAndIncrement());
                        channel.pipeline().addLast("bot_transport", new ServerTransport(connection, address));
                        connection.configurePacketHandler(channel.pipeline());
                        connection.setListenerForServerboundHandshake(
                                new ServerHandshakePacketListenerImpl(server, connection));
                        ServerConnections.add(server, connection);
                    }
                })
                .localAddress(LocalAddress.ANY)
                .bind()
                .syncUninterruptibly()
                .channel();
    }

    void close() {
        bots.values().forEach(Bot::quit);
        serverChannel.close().awaitUninterruptibly();
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onJoin(PlayerJoinEvent event) {
        if (names.contains(key(event.getPlayer().getName()))) {
            LOG.info("[bot] " + event.getPlayer().getName() + " joined");
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onQuit(PlayerQuitEvent event) {
        if (names.contains(key(event.getPlayer().getName()))) {
            LOG.info("[bot] " + event.getPlayer().getName() + " left");
        }
    }

    boolean command(CommandSender sender, String[] args) {
        if (args.length == 1 && args[0].equalsIgnoreCase("list")) {
            sender.sendMessage("[bot] Bots: " + String.join(", ", bots.values().stream().map(Bot::name).sorted().toList()));
            return true;
        }
        if (args.length == 1 && args[0].equalsIgnoreCase("mspt")) {
            long[] ticks = Bukkit.getServer().getTickTimes();
            long[] sorted = ticks.clone();
            java.util.Arrays.sort(sorted);
            double mean = Arrays.stream(ticks).average().orElse(0) / 1_000_000.0;
            double p95 = sorted.length == 0 ? 0 : sorted[(int) Math.min(sorted.length - 1, sorted.length * 0.95)] / 1_000_000.0;
            LOG.info(String.format(Locale.ROOT, "[bot] mspt %.3f %.3f", mean, p95));
            return true;
        }
        if (args.length < 2) {
            return false;
        }
        String action = args[0].toLowerCase(Locale.ROOT);
        String name = args[1];
        String rest = String.join(" ", Arrays.copyOfRange(args, 2, args.length));
        if (!NAME.matcher(name).matches()) {
            sender.sendMessage("[bot] Not a valid player name: " + name);
            return true;
        }
        if (action.equals("join")) {
            join(sender, name);
            return true;
        }
        Bot bot = bots.get(key(name));
        if (bot == null) {
            sender.sendMessage("[bot] No bot named " + name);
            return true;
        }
        try {
            switch (action) {
                case "quit" -> bot.quit();
                case "chat" -> bot.chat(rest);
                case "command" -> bot.command(rest);
                case "respawn" -> bot.respawn();
                case "swap" -> bot.swapHands();
                case "drop" -> bot.drop(rest.equalsIgnoreCase("all"));
                case "use" -> bot.use();
                case "punch" -> bot.punch();
                case "slot" -> bot.selectSlot(Integer.parseInt(rest));
                case "op" -> Bukkit.getGlobalRegionScheduler().execute(plugin, () -> Bukkit.getOfflinePlayer(Bot.offlineUuid(name)).setOp(true));
                case "deop" -> Bukkit.getGlobalRegionScheduler().execute(plugin, () -> Bukkit.getOfflinePlayer(Bot.offlineUuid(name)).setOp(false));
                case "teleport" -> {
                    double[] xyz = Arrays.stream(rest.split(" ")).mapToDouble(Double::parseDouble).toArray();
                    if (xyz.length != 3) {
                        return false;
                    }
                    onPlayer(sender, name, player -> player.teleportAsync(
                            new Location(player.getWorld(), xyz[0], xyz[1], xyz[2])));
                }
                case "bench" -> {
                    String[] parts = rest.split(" ");
                    if (parts.length != 2) {
                        return false;
                    }
                    String event = parts[0].toLowerCase(Locale.ROOT);
                    int count = Integer.parseInt(parts[1]);
                    onPlayer(sender, name, player -> {
                        org.bukkit.plugin.PluginManager events = Bukkit.getPluginManager();
                        long start = System.nanoTime();
                        for (int i = 0; i < count; i++) {
                            boolean on = (i & 1) == 0;
                            events.callEvent(switch (event) {
                                case "sneak" -> new org.bukkit.event.player.PlayerToggleSneakEvent(player, on);
                                case "sprint" -> new org.bukkit.event.player.PlayerToggleSprintEvent(player, on);
                                default -> new org.bukkit.event.player.PlayerToggleFlightEvent(player, on);
                            });
                        }
                        LOG.info("[bot] bench " + event + " " + count + " " + (System.nanoTime() - start) + "ns");
                    });
                }
                case "damage" -> {
                    double amount = Double.parseDouble(rest);
                    onPlayer(sender, name, player -> player.damage(amount));
                }
                default -> {
                    return false;
                }
            }
        } catch (NumberFormatException e) {
            sender.sendMessage("[bot] Not a number: " + e.getMessage());
        }
        return true;
    }

    private void join(CommandSender sender, String name) {
        Bot bot = new Bot(name, Bukkit.getPort(), closed -> bots.remove(key(name), closed));
        if (bots.putIfAbsent(key(name), bot) != null) {
            sender.sendMessage("[bot] " + name + " is already connected");
            return;
        }
        names.add(key(name));
        ChannelFuture connect = new Bootstrap()
                .group(transport.eventLoopGroup())
                .channel(transport.channelCls())
                .handler(bot)
                .connect(serverChannel.localAddress());
        bot.attach(connect.channel());
    }

    /** Runs {@code action} on the player's thread: its region's on Folia, the main thread on Paper. */
    private void onPlayer(CommandSender sender, String name, Consumer<Player> action) {
        Player player = Bukkit.getPlayerExact(name);
        if (player == null) {
            sender.sendMessage("[bot] " + name + " isn't in the world yet");
            return;
        }
        player.getScheduler().run(plugin, task -> action.accept(player), null);
    }

    private static String key(String name) {
        return name.toLowerCase(Locale.ROOT);
    }
}
