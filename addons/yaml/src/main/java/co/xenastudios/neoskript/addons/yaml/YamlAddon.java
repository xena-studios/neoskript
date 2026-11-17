/*
 * Copyright (c) 2026 Xena Studios
 * SPDX-License-Identifier: GPL-3.0-only
 */
package co.xenastudios.neoskript.addons.yaml;

import co.xenastudios.neoskript.addon.support.OfficialAddon;
import co.xenastudios.neoskript.api.NeoSkript;
import java.nio.file.Path;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * NeoSkriptYaml: YAML files from scripts, with skript-yaml's syntax. Files are shared by every script on every
 * thread, saved off the server's threads, and written whole or not at all.
 */
public final class YamlAddon extends JavaPlugin {

    private YamlStore store;

    @Override
    public void onEnable() {
        if (!OfficialAddon.start(this)) {
            getServer().getPluginManager().disablePlugin(this);
            return;
        }
        NeoSkript neoskript = NeoSkript.get();
        // Paths are from the folder the server was started in, as skript-yaml's.
        store = new YamlStore(new ServerPaths(Path.of("")), neoskript.values(), getLogger()::warning);
        YamlStore files = store;
        neoskript.addon(getName(), getPluginMeta().getVersion(),
                registrar -> YamlSyntax.register(registrar, () -> files, neoskript.values()));
    }

    @Override
    public void onDisable() {
        // Pending saves are written now. Scripts that save while the server stops, after this, write at once.
        if (store != null) {
            store.close();
        }
    }
}
