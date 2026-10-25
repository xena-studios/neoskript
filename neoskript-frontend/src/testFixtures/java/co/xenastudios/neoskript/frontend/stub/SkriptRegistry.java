/*
 * Copyright (c) 2026 Xena Studios
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package co.xenastudios.neoskript.frontend.stub;

import co.xenastudios.neoskript.frontend.pattern.CompiledPattern;
import co.xenastudios.neoskript.frontend.pattern.PatternCompiler;
import co.xenastudios.neoskript.frontend.registry.FunctionInfo;
import co.xenastudios.neoskript.frontend.registry.RegexRole;
import co.xenastudios.neoskript.frontend.registry.SyntaxInfo;
import co.xenastudios.neoskript.frontend.registry.SyntaxKind;
import co.xenastudios.neoskript.frontend.registry.SyntaxRegistry;
import co.xenastudios.neoskript.frontend.registry.TypeInfo;
import co.xenastudios.neoskript.frontend.registry.TypeRef;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * The stub registry: the reference Skript's syntax from {@link SkriptSyntax}, plus what the docs export
 * leaves out.
 *
 * <ul>
 *   <li>Skript's test-only syntax ({@code test "x":}, {@code assert}, {@code last parse logs},
 *       {@code test-block} and so on), written here from how Skript's tests use it.
 *   <li>What the text matched by each {@code <regex>} is (a condition, an effect, a type name).
 *   <li>Type relations and literals: which types convert to which, closed literal sets from the type docs,
 *       and which types have open literal sets like item names.
 * </ul>
 */
public final class SkriptRegistry {

    /** Element ids whose regexes match code the element parses itself. */
    private static final Map<String, List<RegexRole>> REGEX_ROLES = Map.ofEntries(
            Map.entry("SecConditional", List.of(RegexRole.CONDITION)),
            Map.entry("SecWhile", List.of(RegexRole.CONDITION)),
            Map.entry("EffDoIf", List.of(RegexRole.EFFECT, RegexRole.CONDITION)),
            Map.entry("ExprTernary", List.of(RegexRole.CONDITION)),
            Map.entry("ExprWhether", List.of(RegexRole.CONDITION)),
            Map.entry("ExprFilter", List.of(RegexRole.CONDITION)),
            Map.entry("ExprReduce", List.of(RegexRole.EXPRESSION)),
            Map.entry("ExprTransform", List.of(RegexRole.EXPRESSION)),
            Map.entry("EffSort", List.of(RegexRole.EXPRESSION)),
            Map.entry("EffTransform", List.of(RegexRole.EXPRESSION)),
            Map.entry("ExprEntity", List.of(RegexRole.TYPE)),
            Map.entry("ExprAttacked", List.of(RegexRole.TYPE)),
            Map.entry("ExprValueWithin", List.of(RegexRole.TYPE)),
            Map.entry("TestStructure", List.of(RegexRole.CONDITION)),
            Map.entry("TestAssert", List.of(RegexRole.CODE)));

    /**
     * Patterns the docs export renders without some optional parts, added back so code Skript accepts
     * parses. Each one is backed by a script in Skript's tests that uses it.
     */
    /**
     * Patterns replaced outright. {@code number within {_x}} has the type it names, which needs the type
     * name as a regex with the type role rather than a {@code %*type%} placeholder.
     */
    private static final Map<String, List<String>> PATTERN_REPLACEMENTS = Map.of(
            "ExprValueWithin", List.of("[the] (<.+>|value[s]) (within|in) %~objects%"));

    private static final Map<String, List<String>> PATTERN_FIXES = Map.of(
            "CondIsDivisibleBy", List.of("%numbers% can [evenly] be divided by %number% [with [a] tolerance [of] %number%]",
                    "%numbers% can be [evenly] divided by %number% [with [a] tolerance [of] %number%]"));

    /** Structures the script parser handles itself, by their structure kind. */
    private static final Set<String> BUILT_IN_STRUCTURES = Set.of("StructAliases", "StructOptions", "StructVariables",
            "StructUsing", "StructCommand", "StructEvent", "StructFunction");

    /** Types whose literals are open-ended (item and entity names depend on aliases). */
    private static final Set<String> OPEN_LITERAL_TYPES = Set.of("itemtype", "itemstack", "blockdata", "entitydata",
            "entitytype", "enchantmenttype", "potioneffecttype", "experience", "offlineplayer", "player",
            "minecrafttag", "gamerule", "particle", "sound", "loottable", "damagetype", "bannerpatterntype",
            "potioneffect", "fireworkeffect");

    /** Types that only take themselves and registered conversions. */
    private static final String[] STRICT_TYPES = {"number", "integer", "long", "string", "boolean", "timespan", "date",
        "time", "timeperiod"};

    private static SyntaxRegistry registry;

    private SkriptRegistry() {}

    public static synchronized SyntaxRegistry get() {
        if (registry == null) {
            registry = build(SkriptSyntax.load());
        }
        return registry;
    }

    static SyntaxRegistry build(SkriptSyntax syntax) {
        SyntaxRegistry.Builder builder = SyntaxRegistry.builder();
        types(syntax, builder);
        relations(builder);

        Set<String> entityNames = new LinkedHashSet<>();
        syntax.types().stream()
                .filter(t -> t.id().equals("entity"))
                .flatMap(t -> t.patterns().stream())
                .forEach(p -> entityNames.addAll(splitLiterals(p)));
        builder.entityNames(entityNames);

        testSupport(builder);
        add(builder, SyntaxKind.STRUCTURE, syntax.structures().stream()
                .filter(e -> !BUILT_IN_STRUCTURES.contains(e.id()))
                .toList());
        add(builder, SyntaxKind.EVENT, syntax.events());
        add(builder, SyntaxKind.SECTION, syntax.sections());
        // Effect sections like 'spawn ...:' and 'wait ...' also work as plain effects.
        add(builder, SyntaxKind.EFFECT, syntax.sections().stream()
                .filter(e -> e.id().startsWith("EffSec") || e.id().equals("Delay"))
                .toList());
        add(builder, SyntaxKind.EFFECT, syntax.effects());
        add(builder, SyntaxKind.CONDITION, syntax.conditions());
        add(builder, SyntaxKind.EXPRESSION, syntax.expressions());
        SyntaxRegistry types = builder.build();
        for (SkriptSyntax.Function function : syntax.functions()) {
            builder.function(function(function, types));
        }
        builder.function(new FunctionInfo("case_equals",
                List.of(new FunctionInfo.Parameter("strings", "string", true, false)), "boolean", false));
        builder.function(new FunctionInfo("file_separator", List.of(), "string", false));
        builder.function(new FunctionInfo("vector", List.of(
                new FunctionInfo.Parameter("x", "number", false, false),
                new FunctionInfo.Parameter("y", "number", false, false),
                new FunctionInfo.Parameter("z", "number", false, false)), "vector", false));
        return builder.build();
    }

    private static void types(SkriptSyntax syntax, SyntaxRegistry.Builder builder) {
        Set<String> typeNames = new HashSet<>(UNDOCUMENTED_TYPES.keySet());
        for (SkriptSyntax.Type type : syntax.types()) {
            typeNames.add(type.name().toLowerCase(Locale.ROOT));
            typeNames.add(type.id().toLowerCase(Locale.ROOT));
        }
        for (SkriptSyntax.Type type : syntax.types()) {
            String id = type.id().toLowerCase(Locale.ROOT);
            Set<String> literals = new HashSet<>();
            if (id.equals("classinfo")) {
                for (String name : typeNames) {
                    literals.add(name);
                    literals.add(SyntaxRegistry.plural(name));
                }
            } else if (!id.equals("entity")) {
                for (String pattern : type.patterns()) {
                    literals.addAll(splitLiterals(pattern));
                }
            }
            String[] aliases = switch (id) {
                case "string" -> new String[] {"text/texts", "string/strings"};
                case "itemstack" -> new String[] {"item stack/item stacks"};
                case "potioncause" -> new String[] {"potion effect cause/potion effect causes"};
                case "inventoryclosereason" -> new String[] {"inventory close reason/inventory close reasons"};
                default -> new String[0];
            };
            if (id.equals("direction")) {
                // A bare 'at', as in 'spawn a pig at the player', is a direction.
                literals.add("at");
            }
            builder.type(new TypeInfo(id, type.name(), literals, OPEN_LITERAL_TYPES.contains(id)), aliases);
            if (!literals.isEmpty() && !OPEN_LITERAL_TYPES.contains(id) && !id.equals("classinfo")) {
                // A closed set like game modes: values come from its literals or expressions of the type.
                builder.strict(id);
            }
        }
        UNDOCUMENTED_TYPES.forEach((id, name) -> builder.type(
                new TypeInfo(id, name, Set.of(), OPEN_LITERAL_TYPES.contains(id)),
                id.equals("integer") ? new String[] {"int/ints"} : new String[] {name.replace(" ", "")}));
    }

    /** Types Skript has but doesn't document, by id. */
    private static final Map<String, String> UNDOCUMENTED_TYPES = Map.of(
            "integer", "integer",
            "long", "long",
            "double", "double",
            "sound", "sound",
            "inventoryholder", "inventory holder",
            "itementity", "item entity",
            "visualeffect", "visual effect");

    private static void relations(SyntaxRegistry.Builder builder) {
        builder.strict(STRICT_TYPES);
        builder.assignable("integer", "number", "long", "double");
        builder.assignable("long", "number", "integer", "double");
        builder.assignable("double", "number");
        builder.assignable("number", "integer", "long", "double");
        builder.assignable("string", "textcomponent");
        builder.assignable("textcomponent", "string");
        builder.assignable("timeperiod", "time");
        builder.assignable("vector", "direction");
        // Skript converts UUIDs to text.
        builder.assignable("uuid", "string");
    }

    /**
     * The literals listed in a type's docs, e.g. {@code adventure, survival, spectator, creative}. Returns
     * nothing for descriptions of a syntax rather than a list of values.
     */
    static List<String> splitLiterals(String pattern) {
        if (pattern.matches(".*[<\\[#\"(%].*") || pattern.toLowerCase(Locale.ROOT).startsWith("see ")) {
            return List.of();
        }
        List<String> literals = new ArrayList<>();
        for (String part : pattern.split(",| and |/")) {
            String literal = part.strip().toLowerCase(Locale.ROOT);
            if (literal.startsWith("and ")) {
                literal = literal.substring(4);
            }
            if (!literal.isEmpty() && !literal.contains(".")) {
                literals.add(literal);
            }
        }
        return literals;
    }

    private static void testSupport(SyntaxRegistry.Builder builder) {
        syntax(builder, SyntaxKind.STRUCTURE, "TestStructure", null, "test %string% [when <.+>]");
        syntax(builder, SyntaxKind.EFFECT, "TestAssert", null,
                "assert <.+> [to fail] [with [error] %string%[, expected %objects%][, got %objects%]]");
        syntax(builder, SyntaxKind.EXPRESSION, "TestParseLogs", "string", "[the] last parse logs");
        syntax(builder, SyntaxKind.EXPRESSION, "TestLocation", "location", "[the] test(-| )location");
        syntax(builder, SyntaxKind.EXPRESSION, "TestBlock", "block", "[the] test(-| )block");
        syntax(builder, SyntaxKind.EXPRESSION, "TestWorld", "world", "[the] test(-| )world");
        syntax(builder, SyntaxKind.EVENT, "TestEvent", null, "test %string%");
        syntax(builder, SyntaxKind.STRUCTURE, "TestExampleStructure", null, "example");
        syntax(builder, SyntaxKind.CONDITION, "TestMethodExists", null, "method %string% (exists|doesn't exist)");
        // Test-only syntax for specific Skript features, as its tests use it.
        syntax(builder, SyntaxKind.EXPRESSION, "ExprSecTestRunnable", "object", "[a] new runnable");
        syntax(builder, SyntaxKind.EFFECT, "TestRunRunnable", null, "run runnable %object%");
        syntax(builder, SyntaxKind.EXPRESSION, "TestStringLiteral", "string", "test string literal %string%");
        syntax(builder, SyntaxKind.EFFECT, "TestExpressionList", null, "test expr list %objects%");
        syntax(builder, SyntaxKind.SECTION, "TestDefaultValue", null, "run with custom default value %object% for %*type%");
        syntax(builder, SyntaxKind.EXPRESSION, "TestDefault", "object", "[the] default %*type%");
        syntax(builder, SyntaxKind.SECTION, "TestReturnable", null, "returnable [plural] %*type% section");
        syntax(builder, SyntaxKind.EXPRESSION, "TestReturnValue", "object", "[the] last return value[s]");
        syntax(builder, SyntaxKind.EXPRESSION, "TestOperators", "object", "%object%[ ]bar![ ]%object%",
                "%object%[ ]foo!1[ ]%object%", "%object%[ ]foo!2[ ]%object%", "%object%[ ]blob![ ]%object%");
    }

    private static void syntax(SyntaxRegistry.Builder builder, SyntaxKind kind, String id, String returnType,
            String... patterns) {
        builder.syntax(new SyntaxInfo(kind, id, Arrays.stream(patterns).map(PatternCompiler::compile).toList(),
                returnType, REGEX_ROLES.getOrDefault(id, List.of())));
    }

    private static void add(SyntaxRegistry.Builder builder, SyntaxKind kind, List<SkriptSyntax.Element> elements) {
        for (SkriptSyntax.Element element : elements) {
            List<String> sources = new ArrayList<>(PATTERN_REPLACEMENTS.getOrDefault(element.id(), element.patterns()));
            sources.addAll(PATTERN_FIXES.getOrDefault(element.id(), List.of()));
            List<CompiledPattern> patterns = sources.stream().map(PatternCompiler::compile).toList();
            builder.syntax(new SyntaxInfo(kind, element.id(), patterns, element.returns(),
                    REGEX_ROLES.getOrDefault(element.id(), List.of())));
        }
    }

    private static final Pattern FUNCTION_SIGNATURE = Pattern.compile("(\\w+)\\((.*)\\)");
    private static final Pattern PARAMETER = Pattern.compile("\\s*([\\w ]+?)\\s*:\\s*(optional\\s+)?(.+?)(\\s*=.*)?");

    /** Reads a docs signature like {@code round(n: number, d: number = 0)}. */
    private static FunctionInfo function(SkriptSyntax.Function function, SyntaxRegistry types) {
        List<FunctionInfo.Parameter> parameters = new ArrayList<>();
        Matcher signature = FUNCTION_SIGNATURE.matcher(function.signature().strip());
        if (signature.matches() && !signature.group(2).isBlank()) {
            for (String text : signature.group(2).split(",")) {
                Matcher parameter = PARAMETER.matcher(text);
                if (!parameter.matches()) {
                    continue;
                }
                String typeName = parameter.group(3).replaceAll("\\s+between.*|\\s*\\(.*", "").strip();
                TypeRef type = types.resolveType(typeName);
                parameters.add(new FunctionInfo.Parameter(parameter.group(1),
                        type == null ? SyntaxRegistry.OBJECT : type.type().id(), type == null || type.plural(),
                        parameter.group(2) != null || parameter.group(4) != null));
            }
        }
        String returns = function.returns() == null || function.returns().isBlank() ? null : function.returns();
        return new FunctionInfo(function.name(), parameters, returns, false);
    }
}
