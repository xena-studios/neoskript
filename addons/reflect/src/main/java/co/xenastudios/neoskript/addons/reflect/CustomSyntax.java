/*
 * Copyright (c) 2026 Xena Studios
 * SPDX-License-Identifier: GPL-3.0-only
 */
package co.xenastudios.neoskript.addons.reflect;

import co.xenastudios.neoskript.api.element.ScriptSyntaxCall;
import co.xenastudios.neoskript.api.registration.Registrar;
import co.xenastudios.neoskript.api.registration.ScriptStructure;
import co.xenastudios.neoskript.api.registration.ScriptSyntax;
import co.xenastudios.neoskript.api.registration.ScriptSyntaxHandler;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * skript-reflect's custom syntax: {@code effect}, {@code condition} and {@code expression} structures, and the
 * property forms, declaring syntax every script can use (or only their own, with {@code local}), with their code in
 * {@code trigger:}, {@code check:} and {@code get:}. NeoSkript reads them before any script's code and compiles their
 * code as it compiles functions; each load starts afresh, so a script's syntax goes when it changes or unloads.
 */
final class CustomSyntax {

    private CustomSyntax() {}

    // [local] [plural|non-single] (effect|condition|expression) [<pattern>]
    private static final Pattern PLAIN = Pattern.compile(
            "(?i)(local\\s+)?(?:(plural|non(?:-|\\s)?single)\\s+)?(effect|condition|expression)(?:\\s+(.+))?");
    // [local] <types> property condition <pattern>
    private static final Pattern PROPERTY_CONDITION = Pattern.compile("(?i)(local\\s+)?(.+?)\\s+property\\s+condition\\s+(.+)");
    // [local] <types> property <pattern>
    private static final Pattern PROPERTY = Pattern.compile("(?i)(local\\s+)?(.+?)\\s+property\\s+(.+)");
    private static final Pattern RETURN_TYPE = Pattern.compile("(?i)return type\\s*:\\s*(.+)");

    static void register(Registrar registrar) {
        Handler handler = new Handler();
        registrar.scriptSyntax("NeoSkriptReflectStructCustomEffect", handler, "[local] effect [<.+>]");
        registrar.scriptSyntax("NeoSkriptReflectStructCustomCondition", handler, "[local] condition [<.+>]",
                "[local] <.+> property condition <.+>");
        registrar.scriptSyntax("NeoSkriptReflectStructCustomExpression", handler,
                "[local] [(plural|non(-|[ ])single)] expression [<.+>]", "[local] <.+> property <.+>");

        registrar.expression("NeoSkriptReflectExprExpression", "object", info -> {
            onlyInCustomSyntax("The expression 'expression' may only be used in a custom syntax structure");
            String written = info.text() == null ? "" : info.text();
            Matcher number = Pattern.compile("(\\d+)\\s*$").matcher(written);
            int index = number.find() ? Integer.parseInt(number.group(1)) - 1 : -1;
            return (context, arguments) -> {
                ScriptSyntaxCall use = context.scriptSyntaxCall();
                if (use == null || index < 0 || index >= use.arguments().length) {
                    return null;
                }
                return use.arguments()[index];
            };
        }, "[the] expr[ession][plural:s](-| )<\\d+>");
        registrar.expression("NeoSkriptReflectExprMatchedPattern", "integer", info -> {
            onlyInCustomSyntax("The matched pattern may only be used in custom syntax.");
            return (context, arguments) -> {
            ScriptSyntaxCall use = context.scriptSyntaxCall();
                // Counted from 1, as skript-reflect's.
                return use == null ? null : (Object) Integer.valueOf(use.pattern() + 1);
            };
        }, "[the] [matched] pattern");
        registrar.expression("NeoSkriptReflectExprParseMark", "integer", info -> {
            onlyInCustomSyntax("The parse mark may only be used in custom syntax");
            return (context, arguments) -> {
                ScriptSyntaxCall use = context.scriptSyntaxCall();
                return use == null ? null : (Object) Integer.valueOf(use.mark());
            };
        }, "[the] [parse[r]] mark");
        registrar.expression("NeoSkriptReflectExprParseTags", "string", info -> {
            onlyInCustomSyntax("The parse tags may only be used in custom syntax");
            return (context, arguments) -> {
                ScriptSyntaxCall use = context.scriptSyntaxCall();
                return use == null ? null : use.tags().toArray();
            };
        }, "[the] parse[r] tags");
    }

    /** Refuses the line, with skript-reflect's {@code message}, outside custom syntax's code. */
    private static void onlyInCustomSyntax(String message) {
        if (!co.xenastudios.neoskript.api.NeoSkript.get().inDeclaredSyntaxCode()) {
            throw new co.xenastudios.neoskript.api.element.SyntaxError(message);
        }
    }

    /** Reads skript-reflect's structures, with its messages for what's missing. */
    private static final class Handler implements ScriptSyntaxHandler {

        @Override
        public ScriptSyntax declare(ScriptStructure structure) {
            String header = structure.header().strip();
            Matcher condition = PROPERTY_CONDITION.matcher(header);
            if (condition.matches()) {
                return property(structure, condition.group(1) != null, condition.group(2), condition.group(3), true);
            }
            Matcher plain = PLAIN.matcher(header);
            if (plain.matches()) {
                return plain(structure, plain);
            }
            Matcher property = PROPERTY.matcher(header);
            if (property.matches()) {
                return property(structure, property.group(1) != null, property.group(2), property.group(3), false);
            }
            return null;
        }

        private ScriptSyntax plain(ScriptStructure structure, Matcher header) {
            boolean local = header.group(1) != null;
            boolean plural = header.group(2) != null;
            String what = header.group(3).toLowerCase(Locale.ROOT);
            List<String> written = new ArrayList<>();
            List<ScriptStructure.Entry> patterns = structure.section("patterns");
            // skript-reflect's messages.
            if (header.group(4) != null && patterns != null) {
                structure.error(firstLine(structure), "A custom " + what + " with an inline pattern cannot have a"
                        + " 'patterns' entry too");
                return null;
            }
            if (header.group(4) == null && patterns == null) {
                structure.error(firstLine(structure), "A custom " + what + " without an inline pattern must have a"
                        + " 'patterns' entry");
                return null;
            }
            if (header.group(4) != null) {
                written.add(header.group(4).strip());
            } else {
                for (ScriptStructure.Entry entry : patterns) {
                    written.add(entry.text().strip());
                }
            }
            List<String> patterns2 = new ArrayList<>();
            boolean asArguments = false;
            for (String pattern : written) {
                asArguments |= pattern.contains("%$");
                patterns2.add(pattern.replace("%$", "%"));
            }
            unsupported(structure, what);
            return switch (what) {
                case "effect" -> new ScriptSyntax(ScriptSyntax.Kind.EFFECT, patterns2, null,
                        ScriptSyntax.Plurality.SINGLE, local, "trigger");
                case "condition" -> new ScriptSyntax(ScriptSyntax.Kind.CONDITION, patterns2, null,
                        ScriptSyntax.Plurality.SINGLE, local, "check");
                default -> new ScriptSyntax(ScriptSyntax.Kind.EXPRESSION, patterns2, returnType(structure),
                        plural ? ScriptSyntax.Plurality.PLURAL
                                : asArguments ? ScriptSyntax.Plurality.AS_ARGUMENTS : ScriptSyntax.Plurality.SINGLE,
                        local, "get");
            };
        }

        /** skript-reflect's property forms, with Skript's property patterns. */
        private ScriptSyntax property(ScriptStructure structure, boolean local, String types, String pattern,
                boolean condition) {
            String owners = types.strip().replace("$", "");
            String property = pattern.strip();
            unsupported(structure, condition ? "condition" : "expression");
            if (condition) {
                // As Skript's property conditions: the second pattern is the negated one.
                return new ScriptSyntax(ScriptSyntax.Kind.CONDITION, List.of(
                        "%" + owners + "% (is|are) " + property, "%" + owners + "% (isn't|is not|aren't|are not) " + property),
                        null, ScriptSyntax.Plurality.SINGLE, local, "check", true);
            }
            return new ScriptSyntax(ScriptSyntax.Kind.EXPRESSION, List.of("[the] " + property + " of %" + owners + "%",
                    "%" + owners + "%'[s] " + property), returnType(structure), ScriptSyntax.Plurality.AS_ARGUMENTS, local,
                    "get", true);
        }

        private static String returnType(ScriptStructure structure) {
            for (ScriptStructure.Entry entry : structure.entries()) {
                Matcher type = RETURN_TYPE.matcher(entry.text().strip());
                if (type.matches()) {
                    return type.group(1).strip().toLowerCase(Locale.ROOT);
                }
            }
            return null;
        }

        /** Sections that are skript-reflect's but not run here yet: said once, at the structure. */
        private static void unsupported(ScriptStructure structure, String what) {
            for (String section : List.of("parse", "usable in", "add", "set", "remove", "remove all", "delete",
                    "reset")) {
                if (structure.section(section) != null) {
                    structure.warning(firstLine(structure), "NeoSkript Reflect doesn't support the '" + section
                            + "' section of a custom " + what + " yet, so it's left out");
                }
            }
        }

        private static int firstLine(ScriptStructure structure) {
            return structure.line();
        }
    }
}
