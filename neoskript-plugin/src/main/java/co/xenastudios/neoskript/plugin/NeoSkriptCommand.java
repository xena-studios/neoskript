package co.xenastudios.neoskript.plugin;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;

/**
 * The {@code /neoskript} command: {@code reload} re-parses all scripts, and the bare command reports
 * status. Reloading requires the {@code neoskript.admin} permission (or operator).
 */
public final class NeoSkriptCommand implements CommandExecutor {

    private final NeoSkriptPlugin plugin;
    private final ScriptLoader loader;

    public NeoSkriptCommand(NeoSkriptPlugin plugin, ScriptLoader loader) {
        this.plugin = plugin;
        this.loader = loader;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length >= 1 && args[0].equalsIgnoreCase("reload")) {
            if (!sender.hasPermission("neoskript.admin") && !sender.isOp()) {
                sender.sendMessage(Component.text("You don't have permission to reload NeoSkript.", NamedTextColor.RED));
                return true;
            }
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

        sender.sendMessage(Component.text("NeoSkript " + plugin.getPluginMeta().getVersion()
                + " — " + plugin.registry().size() + " syntaxes registered.", NamedTextColor.AQUA));
        sender.sendMessage(Component.text("Use /neoskript reload to reload scripts.", NamedTextColor.GRAY));
        return true;
    }
}
