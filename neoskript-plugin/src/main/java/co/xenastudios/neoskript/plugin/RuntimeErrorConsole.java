/*
 * Copyright (c) 2026 Xena Studios
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package co.xenastudios.neoskript.plugin;

import co.xenastudios.neoskript.runtime.RuntimeErrors;
import co.xenastudios.neoskript.runtime.Timespan;
import co.xenastudios.neoskript.runtime.errors.ErrorFrame;
import co.xenastudios.neoskript.runtime.errors.ErrorSource;
import co.xenastudios.neoskript.runtime.errors.RuntimeError;
import co.xenastudios.neoskript.runtime.errors.RuntimeErrorMessages;
import co.xenastudios.neoskript.runtime.scheduler.Scheduler;
import java.util.ArrayList;
import java.util.List;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;

/**
 * Skript's {@code BukkitRuntimeErrorConsumer}: runtime errors go to the console with where they came from, a
 * frame's skipped errors and timeouts are summed up at its end, and players with
 * {@code skript.see_runtime_errors} are told a script had some. The limits come from config.yml's
 * {@code runtime-errors} section, Skript's {@code runtime errors} in config.sk.
 */
final class RuntimeErrorConsole implements RuntimeErrors.Consumer {

    static final String PERMISSION = "skript.see_runtime_errors";

    private RuntimeErrorConsole() {}

    /** Installs the console as where runtime errors go, and starts the frames. Returns what stops them. */
    static Runnable start(ConfigurationSection config, Scheduler scheduler) {
        ErrorFrame.Limits errors = limits(config, "error", ErrorFrame.Limits.DEFAULT);
        ErrorFrame.Limits warnings = limits(config, "warning", ErrorFrame.Limits.DEFAULT);
        RuntimeErrors.install(new RuntimeErrorConsole(), errors, warnings);
        return scheduler.runGlobalRepeating(frameTicks(config), RuntimeErrors::nextFrame);
    }

    /** {@code frame duration}, in ticks: Skript's 1 second by default. */
    static long frameTicks(ConfigurationSection config) {
        String text = config == null ? null : config.getString("frame-duration");
        Timespan duration = text == null ? null : Timespan.parse(text);
        long ticks = duration == null ? 20 : duration.millis() / 50;
        return Math.max(ticks, 1);
    }

    /** One level's limits: {@code total errors per frame}, {@code errors from one line per frame}, and so on. */
    static ErrorFrame.Limits limits(ConfigurationSection config, String level, ErrorFrame.Limits defaults) {
        if (config == null) {
            return defaults;
        }
        return new ErrorFrame.Limits(
                config.getInt("total-" + level + "s-per-frame", defaults.totalLimit()),
                config.getInt(level + "s-from-one-line-per-frame", defaults.lineLimit()),
                config.getInt(level + "-spam-timeout-limit", defaults.lineTimeoutLimit()),
                // As Skript, a timeout lasts at least a frame.
                Math.max(config.getInt(level + "-timeout-length", defaults.timeoutDuration()), 1));
    }

    @Override
    public void printError(RuntimeError error) {
        AutoReload.send(List.of(Bukkit.getConsoleSender()), RuntimeErrorMessages.error(error));
    }

    @Override
    public void printFrameOutput(ErrorFrame.Output output, RuntimeError.Level level) {
        List<CommandSender> console = List.of(Bukkit.getConsoleSender());
        String skipped = RuntimeErrorMessages.skipped(output, level);
        if (skipped != null) {
            AutoReload.send(console, skipped);
        }
        for (ErrorSource.Location location : output.newTimeouts()) {
            AutoReload.send(console, RuntimeErrorMessages.timedOut(location, output.limits(), level));
        }
        String notification = RuntimeErrorMessages.notification(output, level);
        if (notification != null) {
            List<CommandSender> players = new ArrayList<>();
            for (Player player : Bukkit.getOnlinePlayers()) {
                if (player.hasPermission(PERMISSION)) {
                    players.add(player);
                }
            }
            AutoReload.send(players, notification);
        }
    }
}
