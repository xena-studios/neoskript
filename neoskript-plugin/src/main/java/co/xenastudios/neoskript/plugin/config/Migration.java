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
}
