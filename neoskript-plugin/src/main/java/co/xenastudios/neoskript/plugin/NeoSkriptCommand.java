package co.xenastudios.neoskript.plugin;

import co.xenastudios.neoskript.core.runtime.Profiler;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;

/**
 * The {@code /neoskript} command: {@code reload} re-parses all scripts, {@code profile} controls and
 * reports the built-in profiler, and the bare command reports status. Management actions require the
 * {@code neoskript.admin} permission (or operator).
 */
public final class NeoSkriptCommand implements CommandExecutor {

    private final NeoSkriptPlugin plugin;
    private final ScriptLoader loader;
    private final Profiler profiler;

    public NeoSkriptCommand(NeoSkriptPlugin plugin, ScriptLoader loader, Profiler profiler) {
        this.plugin = plugin;
        this.loader = loader;
        this.profiler = profiler;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 0) {
            return status(sender);
        }
        if (!sender.hasPermission("neoskript.admin") && !sender.isOp()) {
            sender.sendMessage(Component.text("You don't have permission to manage NeoSkript.", NamedTextColor.RED));
            return true;
        }
        return switch (args[0].toLowerCase(java.util.Locale.ROOT)) {
            case "reload" -> reload(sender);
            case "profile" -> profile(sender, args);
            default -> status(sender);
        };
    }

    private boolean reload(CommandSender sender) {
        long start = System.nanoTime();
        ScriptLoader.Result result = loader.reload();
        double ms = (System.nanoTime() - start) / 1_000_000.0;
        sender.sendMessage(Component.text(String.format(
                "NeoSkript reloaded in %.1f ms: %d triggers from %d script(s)%s.",
                ms, result.triggers(), result.scripts(),
                result.failed() > 0 ? " (" + result.failed() + " failed)" : ""),
                result.failed() > 0 ? NamedTextColor.YELLOW : NamedTextColor.GREEN));
        return true;
    }

    private boolean profile(CommandSender sender, String[] args) {
        String action = args.length >= 2 ? args[1].toLowerCase(java.util.Locale.ROOT) : "report";
        switch (action) {
            case "on" -> {
                profiler.setEnabled(true);
                sender.sendMessage(Component.text("Profiling enabled.", NamedTextColor.GREEN));
            }
            case "off" -> {
                profiler.setEnabled(false);
                sender.sendMessage(Component.text("Profiling disabled.", NamedTextColor.GRAY));
            }
            case "reset" -> {
                profiler.reset();
                sender.sendMessage(Component.text("Profiler samples cleared.", NamedTextColor.GRAY));
            }
            default -> {
                sender.sendMessage(Component.text("Hottest triggers:", NamedTextColor.AQUA));
                var samples = profiler.top(10);
                if (samples.isEmpty()) {
                    sender.sendMessage(Component.text("  (no samples — run /neoskript profile on)", NamedTextColor.GRAY));
                }
                for (Profiler.Sample sample : samples) {
                    sender.sendMessage(Component.text(String.format(
                            "  %s — %d runs, %.3f ms avg, %.1f ms total",
                            sample.key(), sample.count(),
                            sample.averageNanos() / 1_000_000.0, sample.totalNanos() / 1_000_000.0),
                            NamedTextColor.GRAY));
                }
            }
        }
        return true;
    }

    private boolean status(CommandSender sender) {
        sender.sendMessage(Component.text("NeoSkript " + plugin.getPluginMeta().getVersion()
                + " — " + plugin.registry().size() + " syntaxes registered.", NamedTextColor.AQUA));
        sender.sendMessage(Component.text("Use /neoskript reload or /neoskript profile.", NamedTextColor.GRAY));
        return true;
    }
}
