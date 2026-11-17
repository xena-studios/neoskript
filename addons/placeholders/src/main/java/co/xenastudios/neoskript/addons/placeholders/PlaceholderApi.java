/*
 * Copyright (c) 2026 Xena Studios
 * SPDX-License-Identifier: GPL-3.0-only
 */
package co.xenastudios.neoskript.addons.placeholders;

import java.util.logging.Logger;
import me.clip.placeholderapi.PlaceholderAPI;
import me.clip.placeholderapi.PlaceholderAPIPlugin;
import me.clip.placeholderapi.expansion.PlaceholderExpansion;
import me.clip.placeholderapi.expansion.Relational;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.jetbrains.annotations.NotNull;

/**
 * Everything that touches PlaceholderAPI's classes, so that nothing else loads them: without PlaceholderAPI, this
 * class is never used.
 */
final class PlaceholderApi implements Placeholders.Expansions {

    private final Plugin plugin;
    private final Logger logger;

    PlaceholderApi(Plugin plugin, Logger logger) {
        this.plugin = plugin;
        this.logger = logger;
    }

    @Override
    public Object register(String prefix, Placeholders placeholders) {
        PlaceholderExpansion other = PlaceholderAPIPlugin.getInstance().getLocalExpansionManager().getExpansion(prefix);
        if (other != null && !(other instanceof Expansion)) {
            // As skript-placeholders, the script's take over, but not silently.
            logger.warning("A script registers placeholders with the prefix \"" + prefix + "\", which replaces "
                    + other.getName() + "'s placeholders with that prefix");
        }
        Expansion expansion = new Expansion(prefix, placeholders, plugin);
        return expansion.register() ? expansion : null;
    }

    @Override
    public void unregister(Object expansion) {
        if (expansion instanceof Expansion ours && ours.isRegistered()) {
            ours.unregister();
        }
    }

    /**
     * The value of a placeholder, as skript-placeholders gets it: {@code %...%} added if there's none, and
     * {@code null} if PlaceholderAPI leaves it as it was or gives nothing.
     */
    static String value(String placeholder, OfflinePlayer player) {
        if (placeholder.indexOf('%') == -1) {
            placeholder = "%" + placeholder + "%";
        }
        String value = PlaceholderAPI.setPlaceholders(player, placeholder);
        return value == null || value.isEmpty() || value.equalsIgnoreCase(placeholder) ? null : value;
    }

    /** The value of a relational placeholder, as {@link #value} for two players. */
    static String relationalValue(String placeholder, Player first, Player second) {
        if (placeholder.indexOf('%') == -1) {
            placeholder = "%" + placeholder + "%";
        }
        String value = PlaceholderAPI.setRelationalPlaceholders(first, second, placeholder);
        return value == null || value.isEmpty() || value.equalsIgnoreCase(placeholder) ? null : value;
    }

    /** The expansion for one prefix: it asks the scripts' triggers for that prefix. */
    static final class Expansion extends PlaceholderExpansion implements Relational {

        private final String prefix;
        private final String key;
        private final Placeholders placeholders;
        private final Plugin plugin;

        Expansion(String prefix, Placeholders placeholders, Plugin plugin) {
            this.prefix = prefix;
            this.key = prefix.toLowerCase(java.util.Locale.ROOT);
            this.placeholders = placeholders;
            this.plugin = plugin;
        }

        @Override
        public @NotNull String getIdentifier() {
            return prefix;
        }

        @Override
        public @NotNull String getAuthor() {
            return String.join(", ", plugin.getPluginMeta().getAuthors());
        }

        @Override
        public @NotNull String getVersion() {
            return plugin.getPluginMeta().getVersion();
        }

        @Override
        public boolean persist() {
            // Kept through /papi reload: scripts, not PlaceholderAPI, decide when it goes.
            return true;
        }

        @Override
        public String onRequest(OfflinePlayer player, @NotNull String params) {
            return placeholders.request(key, params, player);
        }

        @Override
        public String onPlaceholderRequest(Player first, Player second, String params) {
            return placeholders.requestRelational(key, params, first, second);
        }
    }
}
