/*
 * Copyright (c) 2026 Xena Studios
 * SPDX-License-Identifier: GPL-3.0-only
 */
package co.xenastudios.neoskript.plugin;

import co.xenastudios.neoskript.plugin.text.Message;
import java.util.List;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.JoinConfiguration;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.event.HoverEvent;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

/**
 * Everything NeoSkript's own commands and notices say, in one place. The messages are written only in the
 * theme's tags ({@code <prefix>}, {@code <highlight>}, {@code <success>}, {@code <error>}), so config.yml's
 * {@code theme} restyles them all. Players get clicks and hovers; the console gets the same as plain text,
 * with what a hover would show written out.
 */
final class Messages {

    private Messages() {}

    static final String DOCS = "https://neoskript.dev/docs/";
    static final String SYNTAX = "https://neoskript.dev/syntax/";
    static final String DOWNLOAD = "https://neoskript.dev/download/";
    static final String ISSUES = "https://github.com/xena-studios/neoskript/issues";
    static final String RELEASES = "https://github.com/xena-studios/neoskript/releases";

    // --- shared ---

    static final Message NO_SCRIPT = Message.of("<prefix><error>There's no script <highlight><name></highlight> in the scripts folder.");
    static final Message NO_FOLDER = Message.of("<prefix><error>There's no folder <highlight><name></highlight> in the scripts folder.");
    static final Message READ_FAILED = Message.of("<prefix><error>Couldn't read the scripts: <reason>");
    static final Message NO_SCRIPTS = Message.of(
            "<prefix>There are no scripts yet. Put <highlight>.sk</highlight> files in <highlight>plugins/NeoSkript/scripts</highlight>.");

    // --- help ---

    static final Message HELP_HEADER = Message.of("<prefix><highlight>NeoSkript</highlight> <version> commands.<hint>");
    static final Message HELP_HINT_CHAT = Message.of(" Click one to type it.");
    static final Message HELP_LINE = Message.of(" <command> <dark_gray>-</dark_gray> <description>");
    static final Message USAGE = Message.of("<prefix><command>");
    static final Message USAGE_LINE = Message.of("   <highlight><arg></highlight> <dark_gray>-</dark_gray> <description>");

    // --- reload, enable and the report they share ---

    static final Message RELOADING = Message.of("<prefix><doing> <what>...");
    static final Message OTHER_RELOADING = Message.of("<prefix><highlight><player></highlight> is reloading <what>...");
    static final Message DONE = Message.of("<prefix><success><done></success> <what> in <highlight><ms> ms</highlight>.");
    static final Message DONE_WITH_WARNINGS = Message.of(
            "<prefix><success><done></success> <what> in <highlight><ms> ms</highlight>, with <problems>.");
    static final Message DONE_WITH_ERRORS = Message.of(
            "<prefix><error><done> <what> with <problems></error> in <highlight><ms> ms</highlight>. Scripts with errors keep their last version.");
    static final Message REPORT_SCRIPT = Message.of(" <highlight><script></highlight> <dark_gray>-</dark_gray> <problems>");
    static final Message REPORT_ERROR = Message.of("   <error>Line <line>:</error> <message>");
    static final Message REPORT_WARNING = Message.of("   <highlight>Line <line>:</highlight> <message> <dark_gray>(warning)");
    static final Message REPORT_CODE = Message.of("     <dark_gray><code>");
    static final Message NO_LAST = Message.of("<prefix><error>No script has been reloaded yet, so there's no last one to reload.");
    static final Message RELOAD_DISABLED = Message.of("<prefix><highlight><name></highlight> is disabled. <command> enables it.");
    static final Message FOLDER_EMPTY = Message.of("<prefix><highlight><name></highlight> has no enabled scripts.");
    static final Message CONFIG_FAILED = Message.of("<prefix><error><reason></error> The running settings are kept.");
    static final Message CONFIG_WARNINGS = Message.of(
            "<prefix><error><settings> fell back to the default.</error> The console says which.");
    static final Message CONFIG_RESTART = Message.of("<prefix><highlight><settings></highlight> <verb> when the server restarts.");

    static final Message ALREADY_ENABLED = Message.of("<prefix><highlight><name></highlight> is already enabled. <command> reloads it.");
    static final Message NOTHING_DISABLED = Message.of("<prefix>There are no disabled scripts.");
    static final Message FOLDER_NOTHING_DISABLED = Message.of("<prefix><highlight><name></highlight> has no disabled scripts.");
    static final Message ENABLE_FAILED = Message.of("<prefix><error>Couldn't enable <highlight><name></highlight>: <reason>");
    static final Message ENABLE_SOME_FAILED = Message.of(
            "<prefix><error>Couldn't enable every script: <reason></error> Those already renamed are enabled when the server restarts.");

    static final Message DISABLED = Message.of("<prefix><success>Disabled</success> <what>.");
    static final Message ALREADY_DISABLED = Message.of("<prefix><highlight><name></highlight> is already disabled.");
    static final Message FOLDER_NOTHING_ENABLED = Message.of("<prefix><highlight><name></highlight> has no enabled scripts.");
    static final Message DISABLE_FAILED = Message.of(
            "<prefix><error>Couldn't disable <highlight><name></highlight>: <reason></error> It's enabled again when the server restarts.");
    static final Message DISABLE_SOME_FAILED = Message.of(
            "<prefix><error>Couldn't disable every script: <reason></error> Those already renamed stay disabled.");

    // --- list and info ---

    static final Message LIST_HEADER = Message.of(
            "<prefix><highlight><enabled></highlight> <scripts> enabled, <highlight><disabled></highlight> disabled.");
    static final Message LIST_GROUP = Message.of(" <label>: <scripts>");
    static final Message LIST_FOLDER = Message.of("   <highlight><folder></highlight>: <scripts>");

    static final Message INFO_HEADER = Message.of("<prefix><highlight>NeoSkript <version></highlight> on <server>");
    static final Message INFO_LINE = Message.of(" <label>: <value>");

    // --- update ---

    static final Message UPDATE_CHECKING = Message.of("<prefix>Checking Modrinth for a newer NeoSkript...");
    static final Message UPDATE_NEWEST = Message.of("<prefix><success>This is the newest NeoSkript</success>, <highlight><current></highlight>.");
    static final Message UPDATE_OUT = Message.of("<prefix>NeoSkript <highlight><latest></highlight> is out (you have <current>): <link>");
    static final Message UPDATE_FAILED = Message.of("<prefix><error>Couldn't ask Modrinth: <reason>");
    static final Message UPDATE_OFF = Message.of(
            "<prefix>The update checker is off: <highlight>update-checker</highlight> is false in config.yml.");
    static final Message UPDATE_DEVELOPMENT = Message.of(
            "<prefix><highlight><current></highlight> is a development build, which doesn't check for updates. Newer builds: <link>");
    static final Message UPDATE_TESTING = Message.of("<prefix>Test servers don't check for updates.");
    static final Message UPDATE_CHANGES = Message.of("<prefix>What changed in each version: <link>");

    // --- restore, gen-docs, test ---

    static final Message RESTORING = Message.of("<prefix>Restoring the loaded scripts...");
    static final Message RESTORED = Message.of("<prefix><success>Restored</success> <highlight><count></highlight> <scripts> to <highlight><folder></highlight>.");
    static final Message RESTORE_FAILED = Message.of("<prefix><error>Couldn't restore the scripts: <reason>");
    static final Message GEN_DOCS = Message.of(
            "<prefix>NeoSkript doesn't generate docs files. Every syntax element is on <link>");
    static final Message TEST = Message.of(
            "<prefix>Tests run when the server starts with <highlight>-Dneoskript.testing.enabled=true</highlight>.");

    // --- helpers ---

    /** Whether {@code sender} sees clicks and hovers, as a player does; the console doesn't. */
    static boolean chat(CommandSender sender) {
        return sender instanceof Player;
    }

    static void send(CommandSender sender, Message message, TagResolver... placeholders) {
        message.send(sender, placeholders);
    }

    static void send(List<CommandSender> senders, Message message, TagResolver... placeholders) {
        Component chat = message.chat(placeholders);
        senders.forEach(sender -> sender.sendMessage(chat));
    }

    static TagResolver text(String name, Object value) {
        return Message.text(name, value);
    }

    static TagResolver component(String name, Component value) {
        return Message.component(name, value);
    }

    private static final Message HIGHLIGHT = Message.of("<highlight><text>");
    private static final Message LINK = Message.of("<highlight><underlined><text>");

    /** Text in the highlight style. */
    static Component highlight(String text) {
        return HIGHLIGHT.render(text("text", text));
    }

    /** A command that's typed into chat when clicked; the console sees just the command. */
    static Component command(String command) {
        return command(command, command);
    }

    /** {@code label}, which types {@code command} into chat when clicked. */
    static Component command(String label, String command) {
        return highlight(label)
                .hoverEvent(HoverEvent.showText(Message.base(Component.text("Click to type " + command))))
                .clickEvent(ClickEvent.suggestCommand(command));
    }

    /** A link: for players, {@code label} that opens {@code url}; for the console, the address itself. */
    static Component link(CommandSender sender, String label, String url) {
        if (!chat(sender)) {
            return Component.text(url);
        }
        return LINK.render(text("text", label))
                .hoverEvent(HoverEvent.showText(Message.base(Component.text(url))))
                .clickEvent(ClickEvent.openUrl(url));
    }

    /** Components separated by commas. */
    static Component joined(List<Component> parts) {
        return Component.join(JoinConfiguration.separator(Component.text(", ")), parts);
    }

    /** {@code count} and the noun, in the singular or the plural: {@code 1 error}, {@code 2 errors}. */
    static String count(long count, String singular) {
        return count + " " + plural(count, singular);
    }

    static String plural(long count, String singular) {
        return count == 1 ? singular : singular + "s";
    }
}
