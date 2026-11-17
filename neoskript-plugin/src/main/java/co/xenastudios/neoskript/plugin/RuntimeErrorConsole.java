/*
 * Copyright (c) 2026 Xena Studios
 * SPDX-License-Identifier: GPL-3.0-only
 */
package co.xenastudios.neoskript.plugin;

import co.xenastudios.neoskript.paper.TextComponentParser;
import co.xenastudios.neoskript.runtime.RuntimeErrors;
import co.xenastudios.neoskript.runtime.errors.ErrorFrame;
import co.xenastudios.neoskript.runtime.errors.ErrorSource;
import co.xenastudios.neoskript.runtime.errors.RuntimeError;
import co.xenastudios.neoskript.runtime.errors.RuntimeErrorMessages;
import co.xenastudios.neoskript.runtime.scheduler.Scheduler;
import java.util.ArrayList;
import java.util.List;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
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
    static Runnable start(NeoSkriptConfig.RuntimeErrors config, Scheduler scheduler) {
        ErrorFrame.Limits errors = new ErrorFrame.Limits(config.totalErrorsPerFrame(), config.errorsFromOneLinePerFrame(),
                // As Skript, a timeout lasts at least a frame.
                config.errorSpamTimeoutLimit(), Math.max(config.errorTimeoutLength(), 1));
        ErrorFrame.Limits warnings = new ErrorFrame.Limits(config.totalWarningsPerFrame(),
                config.warningsFromOneLinePerFrame(), config.warningSpamTimeoutLimit(),
                Math.max(config.warningTimeoutLength(), 1));
        RuntimeErrors.install(new RuntimeErrorConsole(), errors, warnings);
        return scheduler.runGlobalRepeating(frameTicks(config), RuntimeErrors::nextFrame);
    }

    /** {@code frame-duration}, in ticks: Skript's 1 second by default. */
    static long frameTicks(NeoSkriptConfig.RuntimeErrors config) {
        return Math.max(config.frameDuration().millis() / 50, 1);
    }

    @Override
    public void printError(RuntimeError error) {
        AutoReload.send(List.of(Bukkit.getConsoleSender()), RuntimeErrorMessages.error(error));
    }

    @Override
    public void logError(String message) {
        // Skript's log entry without a line: its prefix, then the message in its error color.
        AutoReload.send(List.of(Bukkit.getConsoleSender()), AutoReload.SKRIPT_PREFIX + "<light red>"
                + TextComponentParser.instance().escape(message) + "<reset>");
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
