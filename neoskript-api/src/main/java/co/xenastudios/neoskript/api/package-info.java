/*
 * Copyright (c) 2026 Xena Studios
 * SPDX-License-Identifier: GPL-3.0-only
 */
/**
 * NeoSkript's addon API. Start with {@link co.xenastudios.neoskript.api.NeoSkript}, which addons get with
 * {@link co.xenastudios.neoskript.api.NeoSkript#get()} and register their syntax through.
 *
 * <p>NeoSkript's own standard library is built on this API. It has no Bukkit types, and doesn't need Bukkit at run
 * time, so the CLI, the language server and the in-browser parser can load the syntax registry without a server.
 */
package co.xenastudios.neoskript.api;
