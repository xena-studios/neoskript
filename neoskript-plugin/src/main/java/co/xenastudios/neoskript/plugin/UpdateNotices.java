/*
 * Copyright (c) 2026 Xena Studios
 * SPDX-License-Identifier: GPL-3.0-only
 */
package co.xenastudios.neoskript.plugin;

import java.util.concurrent.TimeUnit;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.plugin.Plugin;
import org.slf4j.Logger;

/**
 * D15: checks Modrinth for a newer NeoSkript when the server starts and then once a day, tells the console, and
 * tells players with {@code skript.admin} (which Skript's permissions describe as receiving notifications of new
 * versions) when they join.
 */
final class UpdateNotices implements Listener {

    private static final long DAY_HOURS = 24;

    private final Logger logger;
    private final String current;
    /** The newest version found, once there is one. */
    private volatile UpdateChecker.Update found;

    private UpdateNotices(Logger logger, String current) {
        this.logger = logger;
        this.current = current;
    }

    /** Starts checking, unless the running version is a development build. */
    static void start(Plugin plugin, Logger logger) {
        String current = plugin.getPluginMeta().getVersion();
        if (!UpdateChecker.checks(current)) {
            return;
        }
        UpdateNotices notices = new UpdateNotices(logger, current);
        plugin.getServer().getPluginManager().registerEvents(notices, plugin);
        plugin.getServer().getAsyncScheduler().runAtFixedRate(plugin, task -> notices.check(), 0, DAY_HOURS,
                TimeUnit.HOURS);
    }

    private void check() {
        try {
            UpdateChecker.fetch(current).ifPresent(update -> {
                boolean first = found == null || !found.version().equals(update.version());
                found = update;
                if (first) {
                    logger.info("NeoSkript {} is out (this server runs {}). Download it at {}", update.version(),
                            current, update.url());
                }
            });
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        } catch (Exception e) {
            // A server without internet access, or Modrinth being down, isn't worth more than a line.
            logger.debug("Couldn't check Modrinth for a newer NeoSkript: {}", e.toString());
        }
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        UpdateChecker.Update update = found;
        if (update == null || !event.getPlayer().hasPermission("skript.admin")) {
            return;
        }
        event.getPlayer().sendMessage(Component.text("NeoSkript " + update.version() + " is out (this server runs "
                        + current + "). ", NamedTextColor.GOLD)
                .append(Component.text("Download it", NamedTextColor.YELLOW)
                        .clickEvent(ClickEvent.openUrl(update.url()))));
    }
}
