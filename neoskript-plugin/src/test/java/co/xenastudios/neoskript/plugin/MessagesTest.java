/*
 * Copyright (c) 2026 Xena Studios
 * SPDX-License-Identifier: GPL-3.0-only
 */
package co.xenastudios.neoskript.plugin;

import static co.xenastudios.neoskript.plugin.Messages.component;
import static co.xenastudios.neoskript.plugin.Messages.text;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

import co.xenastudios.neoskript.frontend.source.Diagnostic;
import co.xenastudios.neoskript.frontend.source.Severity;
import co.xenastudios.neoskript.frontend.source.SourceFile;
import co.xenastudios.neoskript.plugin.text.Message;
import co.xenastudios.neoskript.plugin.text.TextStyle;
import co.xenastudios.neoskript.plugin.text.Theme;
import java.lang.reflect.Proxy;
import java.util.List;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.TextComponent;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

class MessagesTest {

    private static final TextColor MAGENTA = TextColor.fromHexString("#FF4D9D");

    @AfterEach
    void defaultTheme() {
        Message.theme(Theme.DEFAULTS);
    }

    private static String plain(Component component) {
        return PlainTextComponentSerializer.plainText().serialize(component);
    }

    /** A sender that's only a {@link CommandSender} (the console), or a {@link Player}. */
    private static CommandSender sender(Class<? extends CommandSender> type) {
        return (CommandSender) Proxy.newProxyInstance(MessagesTest.class.getClassLoader(), new Class<?>[] {type},
                (proxy, method, args) -> method.getName().equals("getName") ? "Steve" : null);
    }

    /** The first text in {@code component} whose content is {@code content}. */
    private static TextComponent find(Component component, String content) {
        if (component instanceof TextComponent text && text.content().equals(content)) {
            return text;
        }
        for (Component child : component.children()) {
            TextComponent found = find(child, content);
            if (found != null) {
                return found;
            }
        }
        return null;
    }

    /** The click of the first part of {@code component} with one, looking from the top. */
    private static ClickEvent click(Component component) {
        if (component.clickEvent() != null) {
            return component.clickEvent();
        }
        for (Component child : component.children()) {
            ClickEvent found = click(child);
            if (found != null) {
                return found;
            }
        }
        return null;
    }

    @Test
    void rendersInTheDefaultTheme() {
        Component done = Messages.DONE.chat(text("done", "Reloaded"), text("what", "the config"), text("ms", 12));
        assertEquals("[NeoSkript] Reloaded the config in 12 ms.", plain(done));
        // The base colour wraps the message; the prefix and highlights are NeoSkript's magenta.
        assertEquals(TextColor.fromHexString("#AAAAAA"), done.color());
        assertEquals(MAGENTA, find(done, "NeoSkript").color());
        assertEquals(MAGENTA, find(done, "12 ms").color());
        assertEquals(TextColor.fromHexString("#5EE08A"), find(done, "Reloaded").color());
    }

    @Test
    void theThemeRestylesEveryMessage() {
        Message.theme(new Theme("", TextStyle.parse("<white>"), TextStyle.parse("gold bold"),
                TextStyle.parse("<green>"), TextStyle.parse("<red>")));
        Component done = Messages.DONE.chat(text("done", "Reloaded"), text("what", "the config"), text("ms", 3));
        assertEquals("Reloaded the config in 3 ms.", plain(done));
        assertEquals(TextColor.fromHexString("#FFAA00"), find(done, "3 ms").color());
        assertEquals(TextColor.fromHexString("#FFFFFF"), done.color());
    }

    @Test
    void textPlaceholdersAreNotRead() {
        Component message = Messages.NO_SCRIPT.chat(text("name", "<red>oops</red>"));
        assertEquals("[NeoSkript] There's no script <red>oops</red> in the scripts folder.", plain(message));
    }

    @Test
    void commandsAreTypedWhenClicked() {
        Component command = Messages.command("/sk reload ");
        assertEquals(ClickEvent.suggestCommand("/sk reload "), command.clickEvent());
        assertNotNull(command.hoverEvent());
    }

    @Test
    void linksOpenForPlayersAndAreWrittenOutForTheConsole() {
        Component chat = Messages.link(sender(Player.class), "Docs", Messages.DOCS);
        assertEquals("Docs", plain(chat));
        assertEquals(ClickEvent.openUrl(Messages.DOCS), chat.clickEvent());
        assertEquals(Messages.DOCS, plain(Messages.link(sender(CommandSender.class), "Docs", Messages.DOCS)));
    }

    @Test
    void countsAreSingularOrPlural() {
        assertEquals("1 error", Messages.count(1, "error"));
        assertEquals("0 scripts", Messages.count(0, "script"));
        assertEquals("2 errors and 1 warning", ScriptReport.problems(2, 1));
        assertEquals("3 warnings", ScriptReport.problems(0, 3));
        assertEquals("", ScriptReport.problems(0, 0));
    }

    private static ScriptReport report() {
        ScriptReport report = new ScriptReport("sk");
        SourceFile a = new SourceFile("a.sk", "on load:\n\tbroadcast \"hi\"\n\tthis is not valid syntax\n");
        report.accept(new Diagnostic(Severity.ERROR, "a.sk", 3, 1, 0,
                "Can't understand this condition/effect: this is not valid syntax"), a);
        report.accept(new Diagnostic(Severity.WARNING, "sub/b.sk", 1, 1, 0, "A warning"), null);
        return report;
    }

    @Test
    void reportsListProblemsPerScriptForTheConsole() {
        ScriptReport report = report();
        List<String> lines = report.lines(sender(CommandSender.class)).stream().map(MessagesTest::plain).toList();
        assertEquals(List.of(
                " a.sk - 1 error",
                "   Line 3: Can't understand this condition/effect: this is not valid syntax",
                "     this is not valid syntax",
                " sub/b.sk - 1 warning",
                "   Line 1: A warning (warning)"), lines);
        assertEquals("[NeoSkript] Reloaded 4 scripts with 1 error and 1 warning in 12 ms. Scripts with errors keep their"
                + " last version.", plain(report.summary("Reloaded", Component.text("4 scripts"), 12)));
    }

    @Test
    void reportsShowTheLineOfCodeOnHoverForPlayers() {
        List<Component> lines = report().lines(sender(Player.class));
        // The code is in the hover, not a line of its own.
        assertEquals(4, lines.size());
        Component error = lines.get(1);
        assertEquals(ClickEvent.copyToClipboard("this is not valid syntax"), error.clickEvent());
        assertEquals("a.sk, line 3:\nthis is not valid syntax\nClick to copy the line.",
                plain((Component) error.hoverEvent().value()));
        assertEquals(ClickEvent.suggestCommand("/sk reload a.sk"), click(lines.get(0)));
        // A problem without its code has no hover.
        assertNull(lines.get(3).hoverEvent());
    }

    @Test
    void aCleanReloadSaysSo() {
        ScriptReport report = new ScriptReport("sk");
        assertEquals(List.of(), report.lines(sender(CommandSender.class)));
        assertEquals("[NeoSkript] Reloaded b.sk in 5 ms.", plain(report.summary("Reloaded", Component.text("b.sk"), 5)));
    }

    @Test
    void namesWithoutTheDisablingDash() {
        assertEquals("c.sk", SkriptCommand.enabledName("-c.sk"));
        assertEquals("sub/c.sk", SkriptCommand.enabledName("sub/-c.sk"));
        assertEquals("-folder/c.sk", SkriptCommand.enabledName("-folder/c.sk"));
        assertEquals("a.sk", SkriptCommand.enabledName("a.sk"));
    }

    @Test
    void updateNoticesLinkToTheVersion() {
        String url = "https://modrinth.com/plugin/neoskript/version/0.2.0";
        Component out = Messages.UPDATE_OUT.chat(text("latest", "0.2.0"), text("current", "0.1.0"),
                component("link", Messages.link(sender(Player.class), url, url)));
        assertEquals("[NeoSkript] NeoSkript 0.2.0 is out (you have 0.1.0): " + url, plain(out));
        assertEquals(ClickEvent.openUrl(url), click(out));
    }
    @Test
    void theEnableCommandListOffersWorks(@org.junit.jupiter.api.io.TempDir java.nio.file.Path scripts) throws Exception {
        java.nio.file.Files.createDirectories(scripts.resolve("sub"));
        java.nio.file.Files.createDirectories(scripts.resolve("-off"));
        java.nio.file.Files.writeString(scripts.resolve("-a.sk"), "");
        java.nio.file.Files.writeString(scripts.resolve("sub/-b.sk"), "");
        java.nio.file.Files.writeString(scripts.resolve("-off/c.sk"), "");
        for (String path : List.of("-a.sk", "sub/-b.sk")) {
            String command = SkriptCommand.enableCommand("sk", path);
            String name = command.substring("/sk enable ".length());
            assertEquals(scripts.resolve(path), ScriptFiles.find(scripts, name), command);
        }
        // /sk enable can't enable a script in a disabled folder, so nothing is offered.
        assertNull(SkriptCommand.enableCommand("sk", "-off/c.sk"));
        assertNull(ScriptFiles.find(scripts, "-off/c.sk"));
    }
}
