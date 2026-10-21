/*
 * Copyright (c) 2026 Xena Studios
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
/**
 * The public addon API: syntax registration, types, converters, events, and codegen hooks.
 *
 * <p>NeoSkript's own standard library is built on this API. It must not depend on Bukkit at
 * runtime, so the CLI and language server can load the syntax registry without a server.
 */
package co.xenastudios.neoskript.api;
