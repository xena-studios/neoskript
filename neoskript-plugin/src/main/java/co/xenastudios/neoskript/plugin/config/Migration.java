/*
 * Copyright (c) 2026 Xena Studios
 * SPDX-License-Identifier: GPL-3.0-only
 */
package co.xenastudios.neoskript.plugin.config;

import org.bukkit.configuration.ConfigurationSection;

/** Carries a config forward one version, editing the raw YAML in place. */
@FunctionalInterface
public interface Migration {
    void apply(ConfigurationSection root);

    /** As {@link #apply(ConfigurationSection)}, saying in {@code warnings} what the owner should hear about. */
    default void apply(ConfigurationSection root, java.util.List<String> warnings) {
        apply(root);
    }
}
