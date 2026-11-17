/*
 * Copyright (c) 2026 Xena Studios
 * SPDX-License-Identifier: GPL-3.0-only
 */
package co.xenastudios.neoskript.plugin;

import static co.xenastudios.neoskript.plugin.Messages.component;
import static co.xenastudios.neoskript.plugin.Messages.text;

import co.xenastudios.neoskript.frontend.source.Diagnostic;
import co.xenastudios.neoskript.frontend.source.Severity;
import co.xenastudios.neoskript.frontend.source.SourceFile;
import co.xenastudios.neoskript.plugin.text.Message;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.BiConsumer;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.event.HoverEvent;
import org.bukkit.command.CommandSender;

/**
 * What loading scripts found, for whoever reloaded or enabled them: each script's errors and warnings with their
 * line numbers, then a line saying what was done, how long it took, and how many problems there were.
 *
 * <p>Players can hover over a problem to see the line of code, and click it to copy that line. The console gets
 * the line of code written under the problem.
 */
final class ScriptReport implements BiConsumer<Diagnostic, SourceFile> {

    /** One problem, with the line of code it's on, if known. */
    record Problem(Diagnostic diagnostic, String code) {
        boolean error() {
            return diagnostic.severity() == Severity.ERROR;
        }
    }

    private final String label;
    private final Map<String, List<Problem>> byScript = new LinkedHashMap<>();
    /** The alias files among {@link #byScript}, whose command reloads the aliases rather than a script. */
    private final java.util.Set<String> aliasFiles = new java.util.HashSet<>();
    private int errors;
    private int warnings;

    /**
     * @param label the command that was typed, such as {@code sk}, for the commands the report offers
     */
    ScriptReport(String label) {
        this.label = label;
    }

    @Override
    public void accept(Diagnostic problem, SourceFile file) {
        String code = "";
        if (file != null && problem.line() >= 1 && problem.line() <= file.lines().size()) {
            code = file.lines().get(problem.line() - 1).strip();
        }
        byScript.computeIfAbsent(problem.file(), script -> new ArrayList<>()).add(new Problem(problem, code));
        if (problem.severity() == Severity.ERROR) {
            errors++;
        } else {
            warnings++;
        }
    }

    /** Takes the problems of alias files, listed as {@code aliases/<file>}, with {@code /sk reload aliases} to click. */
    BiConsumer<Diagnostic, SourceFile> aliases() {
        return (problem, file) -> {
            String name = "aliases/" + problem.file();
            aliasFiles.add(name);
            accept(new Diagnostic(problem.severity(), name, problem.line(), problem.column(), problem.length(),
                    problem.message()), file);
        };
    }

    int errors() {
        return errors;
    }

    int warnings() {
        return warnings;
    }

    /** "1 error", "2 errors and 1 warning", or "" if there were none. */
    static String problems(int errors, int warnings) {
        List<String> parts = new ArrayList<>();
        if (errors > 0) {
            parts.add(Messages.count(errors, "error"));
        }
        if (warnings > 0) {
            parts.add(Messages.count(warnings, "warning"));
        }
        return String.join(" and ", parts);
    }

    /** Sends each script's problems, then what was done. */
    void send(List<CommandSender> recipients, String done, Component what, long millis) {
        for (CommandSender recipient : recipients) {
            for (Component line : lines(recipient)) {
                recipient.sendMessage(line);
            }
            recipient.sendMessage(summary(done, what, millis));
        }
    }

    /** The lines listing each script's problems, for {@code recipient}. */
    List<Component> lines(CommandSender recipient) {
        boolean chat = Messages.chat(recipient);
        List<Component> lines = new ArrayList<>();
        for (Map.Entry<String, List<Problem>> script : byScript.entrySet()) {
            int scriptErrors = (int) script.getValue().stream().filter(Problem::error).count();
            String counts = problems(scriptErrors, script.getValue().size() - scriptErrors);
            Component name = Messages.command(script.getKey(), "/" + label + " reload "
                    + (aliasFiles.contains(script.getKey()) ? "aliases" : script.getKey()));
            Component problems = scriptErrors > 0 ? ERROR.render(text("text", counts)) : Component.text(counts);
            lines.add(Messages.REPORT_SCRIPT.chat(component("script", name), component("problems", problems)));
            for (Problem problem : script.getValue()) {
                Message message = problem.error() ? Messages.REPORT_ERROR : Messages.REPORT_WARNING;
                Component line = message.chat(text("line", problem.diagnostic().line()),
                        text("message", problem.diagnostic().message()));
                if (problem.code().isEmpty()) {
                    lines.add(line);
                } else if (chat) {
                    lines.add(line.hoverEvent(HoverEvent.showText(Message.base(Component.text(
                                    script.getKey() + ", line " + problem.diagnostic().line() + ":\n")
                                    .append(Messages.highlight(problem.code()))
                                    .append(Component.text("\nClick to copy the line.")))))
                            .clickEvent(ClickEvent.copyToClipboard(problem.code())));
                } else {
                    lines.add(line);
                    lines.add(Messages.REPORT_CODE.chat(text("code", problem.code())));
                }
            }
        }
        return lines;
    }

    /** The line saying what was done: "Reloaded 4 scripts in 12 ms.", with the problems if there were any. */
    Component summary(String done, Component what, long millis) {
        String problems = problems(errors, warnings);
        Message message = errors > 0 ? Messages.DONE_WITH_ERRORS
                : warnings > 0 ? Messages.DONE_WITH_WARNINGS : Messages.DONE;
        return message.chat(text("done", done), component("what", what), text("ms", millis), text("problems", problems));
    }

    private static final Message ERROR = Message.of("<error><text>");
}
