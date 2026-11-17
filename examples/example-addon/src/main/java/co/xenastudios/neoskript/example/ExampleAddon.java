/*
 * Copyright (c) 2026 Xena Studios
 * SPDX-License-Identifier: GPL-3.0-only
 */
package co.xenastudios.neoskript.example;

import co.xenastudios.neoskript.api.NeoSkript;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * An example addon: a lottery of tickets. It registers a type ({@code ticket}), an event ({@code on lottery draw}),
 * an effect ({@code draw the lottery with %tickets%}), an expression ({@code [a] new ticket for %text%}), a
 * condition ({@code %tickets% (is|are) winning}) and a function ({@code lotteryPrize(n: integer)}), and its
 * {@code /lottery} command reads a script's variable.
 */
public final class ExampleAddon extends JavaPlugin {

    @Override
    public void onEnable() {
        // Runs on the first tick, after NeoSkript's own syntax and before scripts load.
        NeoSkript.get().addon(getName(), getPluginMeta().getVersion(), LotterySyntax::register);
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        NeoSkript neoskript = NeoSkript.get();
        // A script keeps the last winning ticket in {lottery::last}. Variables can be read from any thread.
        Object last = neoskript.variables().get("lottery::last");
        sender.sendMessage("[lottery] last winner: " + (last == null ? "none" : last));
        // Reading what a region owns has to happen on its thread on Folia; this only says where it runs.
        neoskript.threads().runGlobal(() -> sender.sendMessage("[lottery] on the global thread: "
                + neoskript.threads().owns(null)));
        sender.sendMessage("[lottery] prize for 3: " + neoskript.scripts().call("lotteryPrize", 3L));
        return true;
    }
}
