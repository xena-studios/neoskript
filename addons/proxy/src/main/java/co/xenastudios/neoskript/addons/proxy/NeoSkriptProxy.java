/*
 * Copyright (c) 2026 Xena Studios
 * SPDX-License-Identifier: GPL-3.0-only
 */
package co.xenastudios.neoskript.addons.proxy;

import co.xenastudios.neoskript.addon.support.OfficialAddon;
import co.xenastudios.neoskript.api.NeoSkript;
import co.xenastudios.neoskript.api.Threads;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerRegisterChannelEvent;
import org.bukkit.event.server.PluginDisableEvent;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * NeoSkriptProxy: messages and proxy actions across a BungeeCord or Velocity network, over plugin messaging and
 * Redis, with the syntax of SkProxy, Skelocity and SkRedis.
 */
public final class NeoSkriptProxy extends JavaPlugin implements Listener {

    private static final String BUNGEE_MAIN = "bungeecord:main";

    private ScheduledThreadPoolExecutor timer;
    private PluginMessaging messaging;
    private RedisLink redis;
    private Network network;
    private volatile boolean closed;

    @Override
    public void onEnable() {
        if (!OfficialAddon.start(this)) {
            getServer().getPluginManager().disablePlugin(this);
            return;
        }
        saveDefaultConfig();
        List<String> warnings = new ArrayList<>();
        Settings settings = Settings.read(key -> getConfig().get(key), warnings);
        warnings.forEach(warning -> getLogger().warning("config.yml: " + warning));
        Envelope envelope = new Envelope(settings.secret(), settings.maxMessageBytes());
        if (settings.pluginMessaging() && !envelope.signed()) {
            getLogger().warning("secret isn't set in config.yml: network messages over plugin messaging aren't"
                    + " signed, so anyone who can send plugin messages on the BungeeCord channel could forge them."
                    + " Set the same long random text on every server.");
        }
        timer = new ScheduledThreadPoolExecutor(1, runnable -> {
            Thread thread = new Thread(runnable, "NeoSkriptProxy-timer");
            thread.setDaemon(true);
            return thread;
        });
        timer.setRemoveOnCancelPolicy(true);
        Threads threads = NeoSkript.get().threads();
        network = new Network(settings, getLogger(), threads::runGlobal,
                event -> getServer().getPluginManager().callEvent(event), envelope, UUID.randomUUID().toString());
        messaging = new PluginMessaging(settings, this::carry, timer, getLogger(), network::fromForward);
        if (settings.redis()) {
            redis = new RedisLink(settings, getLogger(), network::fromRedis);
            redis.start();
        }
        network.use(settings.pluginMessaging() ? messaging : null, redis);
        if (settings.pluginMessaging()) {
            getServer().getMessenger().registerOutgoingPluginChannel(this, PluginMessaging.CHANNEL);
            getServer().getMessenger().registerIncomingPluginChannel(this, PluginMessaging.CHANNEL,
                    (channel, player, message) -> messaging.received(message));
            getServer().getPluginManager().registerEvents(this, this);
        }
        NeoSkript.get().addon(getName(), getPluginMeta().getVersion(),
                registrar -> ProxySyntax.register(registrar, network, messaging, getLogger(),
                        settings.proxyTimeoutMillis()));
        closeAfterNeoSkript();
    }

    /** Sends a plugin message through an online player whose connection has BungeeCord's channel. */
    private boolean carry(byte[] payload) {
        if (closed) {
            return false;
        }
        Player carrier = null;
        for (Player player : List.copyOf(Bukkit.getOnlinePlayers())) {
            java.util.Set<String> channels = player.getListeningPluginChannels();
            if (channels.contains(PluginMessaging.CHANNEL) || channels.contains(BUNGEE_MAIN)) {
                carrier = player;
                break;
            }
        }
        if (carrier == null) {
            return false;
        }
        Player chosen = carrier;
        Runnable send = () -> chosen.sendPluginMessage(this, PluginMessaging.CHANNEL, payload);
        Threads threads = NeoSkript.get().threads();
        if (threads.owns(chosen)) {
            send.run();
        } else {
            threads.run(chosen, send);
        }
        return true;
    }

    @EventHandler
    public void onChannel(PlayerRegisterChannelEvent event) {
        if (event.getChannel().equals(PluginMessaging.CHANNEL) || event.getChannel().equals(BUNGEE_MAIN)) {
            carrierAvailable();
        }
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        carrierAvailable();
    }

    private void carrierAvailable() {
        // Off the player's thread: sending goes back to it.
        timer.execute(() -> {
            messaging.carrierAvailable();
            if (network.serverName().isEmpty()) {
                messaging.ask(PluginMessaging.Question.serverName()).thenAccept(name ->
                        network.learnName(name instanceof String text ? text : null));
            }
        });
    }

    /**
     * NeoSkript disables after this (it depends on it), and its on unload triggers may still publish to Redis: the
     * connections close once NeoSkript has disabled, or 30 seconds after this one if NeoSkript keeps running.
     */
    private void closeAfterNeoSkript() {
        Plugin neoskript = getServer().getPluginManager().getPlugin("NeoSkript");
        if (neoskript == null) {
            return;
        }
        getServer().getPluginManager().registerEvents(new Listener() {
            @EventHandler
            public void onDisable(PluginDisableEvent event) {
                if (event.getPlugin() == neoskript) {
                    shutDown();
                }
            }
        }, neoskript);
    }

    @Override
    public void onDisable() {
        if (messaging == null) {
            return;
        }
        // Bukkit sends no plugin messages for a disabled plugin, and runs none of its tasks: plugin messaging
        // stops now, and so do messages to scripts. Only Redis can still publish, for on unload triggers.
        messaging.close();
        network.stopDelivering();
        Plugin neoskript = getServer().getPluginManager().getPlugin("NeoSkript");
        if (neoskript == null || !neoskript.isEnabled()) {
            shutDown();
            return;
        }
        Thread closer = new Thread(() -> {
            try {
                Thread.sleep(30_000);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            shutDown();
        }, "NeoSkriptProxy-shutdown");
        closer.setDaemon(true);
        closer.start();
    }

    private synchronized void shutDown() {
        if (closed) {
            return;
        }
        closed = true;
        messaging.close();
        if (redis != null) {
            redis.close(2_000);
        }
        timer.shutdownNow();
    }
}
