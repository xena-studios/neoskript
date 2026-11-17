/*
 * Copyright (c) 2026 Xena Studios
 * SPDX-License-Identifier: GPL-3.0-only
 */
package co.xenastudios.neoskript.harness.bot;

import org.bukkit.plugin.java.JavaPlugin;

/**
 * Does nothing: the plugin the harness wraps an old MySQL driver in, so SQLibrary, which Skript's MySQL storage needs,
 * finds the driver class it was written for ({@code com.mysql.jdbc.jdbc2.optional.MysqlDataSource}). Paper's own
 * driver no longer has it.
 */
public final class DriverShimPlugin extends JavaPlugin {}
