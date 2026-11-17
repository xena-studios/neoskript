/*
 * Copyright (c) 2026 Xena Studios
 * SPDX-License-Identifier: GPL-3.0-only
 */
package co.xenastudios.neoskript.plugin;

import java.util.Optional;
import java.util.concurrent.TimeUnit;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.plugin.Plugin;
import org.slf4j.Logger;

/**
 * D15: checks Modrinth for a newer NeoSkript when the server starts and then once a day, tells the console, and
 * tells players with {@code skript.admin} (which Skript's permissions describe as receiving notifications of new
 * versions) when they join. {@code /sk update} checks straight away. {@code update-checker: false} turns it off,
 * also on {@code /sk reload config}.
 */
final class UpdateNotices implements Listener {

    private static final long DAY_HOURS = 24;

    private final Logger logger;
    private final String current;
    private volatile boolean enabled;
    /** The newest version found, once there is one. */
    private volatile UpdateChecker.Update found;
    /** Whether Modrinth has answered at least once. */
    private volatile boolean checked;

    private UpdateNotices(Logger logger, String current, boolean enabled) {
        this.logger = logger;
        this.current = current;
        this.enabled = enabled;
    }

    /**
     * Starts checking, or returns {@code null} if the running version is a development build, which never
     * checks.
     */
    static UpdateNotices start(Plugin plugin, Logger logger, boolean enabled) {
        String current = plugin.getPluginMeta().getVersion();
        if (!UpdateChecker.checks(current)) {
            return null;
        }
        UpdateNotices notices = new UpdateNotices(logger, current, enabled);
        plugin.getServer().getPluginManager().registerEvents(notices, plugin);
        plugin.getServer().getAsyncScheduler().runAtFixedRate(plugin, task -> notices.scheduledCheck(), 0, DAY_HOURS,
                TimeUnit.HOURS);
        return notices;
    }

    /** Turns the checks on or off, for {@code /sk reload config}. */
    void enabled(boolean enabled) {
        this.enabled = enabled;
        if (!enabled) {
            found = null;
        }
    }

    UpdateChecker.Update found() {
        return found;
    }

    boolean checked() {
        return checked;
    }

    private void scheduledCheck() {
        if (!enabled) {
            return;
        }
        try {
            checkNow();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        } catch (Exception e) {
            // A server without internet access, or Modrinth being down, isn't worth more than a line.
            logger.debug("Couldn't check Modrinth for a newer NeoSkript: {}", e.toString());
        }
    }

    /**
     * Asks Modrinth now, and logs a newer version the first time it's found. Blocks, so it's called off the main
     * thread.
     */
    Optional<UpdateChecker.Update> checkNow() throws Exception {
        Optional<UpdateChecker.Update> newest = UpdateChecker.fetch(current);
        checked = true;
        newest.ifPresent(update -> {
            boolean first = found == null || !found.version().equals(update.version());
            found = update;
            if (first) {
                logger.info("NeoSkript {} is out (this server runs {}). Download it at {}", update.version(),
                        current, update.url());
            }
        });
        return newest;
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        UpdateChecker.Update update = found;
        if (!enabled || update == null || !event.getPlayer().hasPermission("skript.admin")) {
            return;
        }
        Messages.send(event.getPlayer(), Messages.UPDATE_OUT, Messages.text("latest", update.version()),
                Messages.text("current", current),
                Messages.component("link", Messages.link(event.getPlayer(), update.url(), update.url())));
    }
}
