/*
 * Copyright (c) 2026 Xena Studios
 * SPDX-License-Identifier: GPL-3.0-only
 */
package co.xenastudios.neoskript.runtime.variables.storage;

/**
 * A codec that runs another plugin's code, as NeoSkriptCompat's run a Skript addon's serializer: the class loader of
 * that code, which the codec can't run without once that plugin is disabled. Not part of the addon API.
 */
public interface CodecLoader {

    ClassLoader dependsOn();
}
