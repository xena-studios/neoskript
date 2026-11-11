/*
 * Copyright (c) 2026 Xena Studios
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package co.xenastudios.neoskript.harness.bot;

import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * The harness bot plugin. The harness installs it next to NeoSkript or the reference Skript so that
 * scenarios can drive player events on a server without players. See {@link Bots} for the commands.
 */
public final class HarnessBotPlugin extends JavaPlugin {

    private Bots bots;
    private String unsupported;

    @Override
    public void onEnable() {
        // The bot speaks the protocol with the server's own classes, so it only links against the
        // versions it was compiled for. Nothing that touches those classes loads before this check.
        String version = Bukkit.getMinecraftVersion();
        if (version.startsWith("1.")) {
            unsupported = "[bot] The harness bot doesn't support Minecraft " + version + ", only 26.x";
            Bukkit.getLogger().warning(unsupported);
            return;
        }
        bots = new Bots(this);
        getServer().getPluginManager().registerEvents(bots, this);
        Bukkit.getLogger().info("[bot] Ready on Minecraft " + version);
    }

    @Override
    public void onDisable() {
        if (bots != null) {
            bots.close();
        }
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (bots == null) {
            sender.sendMessage(unsupported);
            return true;
        }
        return bots.command(sender, args);
    }
}
