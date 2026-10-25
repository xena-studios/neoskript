/*
 * Copyright (c) 2026 Xena Studios
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package co.xenastudios.neoskript.frontend.registry;

import co.xenastudios.neoskript.frontend.pattern.PatternIndex;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Deque;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Everything the parser knows about: types, syntax elements and built-in functions. Bukkit-free; the
 * standard library fills it in on a server, and the stub registry in tests fills it in from Skript's docs.
 *
 * <p>Immutable once built, so one registry can be shared by parsers on several threads.
 */
public final class SyntaxRegistry {

    /** The type every value has. Expressions of unknown type are treated as this. */
    public static final String OBJECT = "object";

    /** A syntax element's pattern, filed in the index for its kind. */
    public record Candidate(SyntaxInfo syntax, int pattern) {}

    private final Map<String, TypeInfo> typesById;
    private final Map<String, Integer> typeOrders;
    private final Map<String, TypeRef> typeNames;
    private final Map<SyntaxKind, List<SyntaxInfo>> syntaxes;
    private final Map<SyntaxKind, PatternIndex<Candidate>> indexes;
    private final Map<String, List<FunctionInfo>> functions;
    private final Map<String, Set<String>> supertypes;
    private final Set<String> strictTypes;
    /** Types whose values can all be listed, though they aren't a closed set of words: {@code all biomes}. */
    private final Set<String> enumerableTypes;
    private final Map<String, java.util.function.Predicate<String>> literalChecks;
    private final Map<String, java.util.function.Predicate<String>> parseChecks;
    /** Direct conversions, which unlike subtypes don't chain: {@code from -> {to...}}. */
    private final Map<String, Set<String>> conversions;
    private final Set<String> entityNames;
    private final Set<String> delaying;
    private final Set<String> stopping;
    private final Set<String> ending;
    private final Set<String> suppressing;
    private final Set<String> sectionOnly;
    private final Map<String, ParseCheck> checks;

    private SyntaxRegistry(Builder builder) {
        this.typesById = Collections.unmodifiableMap(new LinkedHashMap<>(builder.typesById));
        Map<String, Integer> orders = new java.util.HashMap<>();
        for (String id : typesById.keySet()) {
            orders.put(id, orders.size());
        }
        this.typeOrders = Map.copyOf(orders);
        this.typeNames = Map.copyOf(builder.typeNames);
        Map<SyntaxKind, List<SyntaxInfo>> syntaxes = new EnumMap<>(SyntaxKind.class);
        Map<SyntaxKind, PatternIndex<Candidate>> indexes = new EnumMap<>(SyntaxKind.class);
        for (SyntaxKind kind : SyntaxKind.values()) {
            List<SyntaxInfo> list = List.copyOf(builder.syntaxes.getOrDefault(kind, List.of()));
            syntaxes.put(kind, list);
            PatternIndex<Candidate> index = new PatternIndex<>();
            for (SyntaxInfo syntax : list) {
                for (int i = 0; i < syntax.patterns().size(); i++) {
                    index.add(syntax.patterns().get(i), new Candidate(syntax, i));
                }
            }
            indexes.put(kind, index);
        }
        this.syntaxes = Collections.unmodifiableMap(syntaxes);
        this.indexes = Collections.unmodifiableMap(indexes);
        Map<String, List<FunctionInfo>> functions = new HashMap<>();
        builder.functions.forEach((name, overloads) -> functions.put(name, List.copyOf(overloads)));
        this.functions = Map.copyOf(functions);
        this.supertypes = closure(builder.supertypes);
        this.strictTypes = Set.copyOf(builder.strictTypes);
        this.enumerableTypes = Set.copyOf(builder.enumerableTypes);
        this.literalChecks = Map.copyOf(builder.literalChecks);
        this.parseChecks = Map.copyOf(builder.parseChecks);
        Map<String, Set<String>> conversions = new HashMap<>();
        builder.conversions.forEach((from, to) -> conversions.put(from, Set.copyOf(to)));
        this.conversions = Map.copyOf(conversions);
        this.entityNames = Set.copyOf(builder.entityNames);
        this.delaying = Set.copyOf(builder.delaying);
        this.stopping = Set.copyOf(builder.stopping);
        this.ending = Set.copyOf(builder.ending);
        this.suppressing = Set.copyOf(builder.suppressing);
        this.sectionOnly = Set.copyOf(builder.sectionOnly);
        this.checks = Map.copyOf(builder.checks);
    }

    /** Whether the syntax element makes the trigger wait, like {@code wait 1 second}. */
    public boolean delays(SyntaxInfo syntax) {
        return delaying.contains(syntax.kind() + ":" + syntax.id());
    }

    /** Whether the syntax element, matched by {@code pattern}, always ends the trigger, like {@code stop}. */
    public boolean stops(SyntaxInfo syntax, int pattern) {
        return stopping.contains(syntax.kind() + ":" + syntax.id() + ":" + pattern)
                || stopping.contains(syntax.kind() + ":" + syntax.id() + ":*");
    }

    /** Whether the syntax element ends the section it's in ({@code stop}, {@code continue}): code after it never runs. */
    public boolean ends(SyntaxInfo syntax) {
        return ending.contains(syntax.kind() + ":" + syntax.id());
    }

    /** Whether the syntax element suppresses or allows warnings while parsing, by the tags it matched with. */
    public boolean suppressesWarnings(SyntaxInfo syntax) {
        return suppressing.contains(syntax.kind() + ":" + syntax.id());
    }

    /** Whether the expression can only be used with a section under it, as Skript's section-only expressions. */
    public boolean sectionOnly(SyntaxInfo syntax) {
        return sectionOnly.contains(syntax.id());
    }

    /** The check the syntax element makes while it's parsed, or {@code null} if it makes none. */
    public ParseCheck check(SyntaxInfo syntax) {
        return checks.get(syntax.kind() + ":" + syntax.id());
    }

    /**
     * The check the syntax element makes when a section is written under its line, as Skript's effect sections
     * and section expressions get the section in {@code init}; or {@code null}. An empty error means it just
     * doesn't take one.
     */
    public ParseCheck sectionCheck(SyntaxInfo syntax) {
        return checks.get("section:" + syntax.kind() + ":" + syntax.id());
    }

    public static Builder builder() {
        return new Builder();
    }

    public TypeInfo type(String id) {
        return typesById.get(id.toLowerCase(Locale.ROOT));
    }

    /** Where type {@code id} was registered among the types, earliest first, or a large number if it wasn't. */
    public int typeOrder(String id) {
        Integer order = typeOrders.get(id);
        return order == null ? Integer.MAX_VALUE : order;
    }

    public Iterable<TypeInfo> types() {
        return typesById.values();
    }

    /** Resolves a type as written in a pattern or script: id or display name, singular or plural. */
    public TypeRef resolveType(String name) {
        return typeNames.get(normalize(name));
    }

    public List<SyntaxInfo> syntaxes(SyntaxKind kind) {
        return syntaxes.get(kind);
    }

    public PatternIndex<Candidate> index(SyntaxKind kind) {
        return indexes.get(kind);
    }

    /** A built-in function's overloads, or an empty list if there's no such function. */
    public List<FunctionInfo> functions(String name) {
        return functions.getOrDefault(name, List.of());
    }

    /**
     * Whether a value of type {@code from} can be used where {@code to} is expected: the same type, a
     * subtype, an {@code object}, or a registered conversion. Two types with no known relation are allowed
     * unless one of them is strict (numbers, text, booleans and the like), so missing relations between
     * game types don't cause false errors.
     */
    public boolean isAssignable(String from, String to) {
        if (from == null || to == null || from.equals(to) || from.equals(OBJECT) || to.equals(OBJECT)) {
            return true;
        }
        Set<String> supers = supertypes.get(from);
        if (supers != null && supers.contains(to)) {
            return true;
        }
        if (convertsTo(from, to) || (supers != null && supers.stream().anyMatch(s -> convertsTo(s, to)))) {
            return true;
        }
        return !strictTypes.contains(from) && !strictTypes.contains(to);
    }

    /** Whether {@code all <type>} can list the values of type {@code id}: a closed set, or values listed later. */
    public boolean isEnumerable(String id) {
        TypeInfo type = type(id);
        return type != null && (!type.literals().isEmpty() || enumerableTypes.contains(type.id().toLowerCase(Locale.ROOT)));
    }

    /** Whether {@code from} converts directly to {@code to}. */
    public boolean isConvertible(String from, String to) {
        return convertsTo(from, to);
    }

    private boolean convertsTo(String from, String to) {
        Set<String> targets = conversions.get(from);
        return targets != null && targets.contains(to);
    }

    /** Whether {@code from} is {@code to} or one of its registered subtypes, with no leniency. */
    public boolean isSubtype(String from, String to) {
        if (from.equals(to)) {
            return true;
        }
        Set<String> supers = supertypes.get(from);
        return supers != null && supers.contains(to);
    }

    /**
     * Whether {@code words} might be a literal of {@code type}, an open-literal type: its check says so, or
     * it has none (so anything might be).
     */
    /**
     * Whether the type's own parser takes {@code words}, for a literal with its type given ({@code unknown
     * (heal reason)}), where words left out of the type's plain literals still count.
     */
    public boolean parses(TypeInfo type, String words) {
        java.util.function.Predicate<String> check = parseChecks.get(type.id());
        if (check == null) {
            return false;
        }
        try {
            return check.test(words);
        } catch (RuntimeException | LinkageError e) {
            return false;
        }
    }

    public boolean mightBeLiteral(TypeInfo type, String words) {
        java.util.function.Predicate<String> check = literalChecks.get(type.id());
        if (check == null) {
            return true;
        }
        try {
            return check.test(words);
        } catch (RuntimeException | LinkageError e) {
            // The check needs something that isn't there (a server, in tests): don't rule the words out.
            return true;
        }
    }

    /** Whether {@code words} are certainly a literal of {@code type}: its check, which needs a server, says so. */
    public boolean isLiteral(TypeInfo type, String words) {
        java.util.function.Predicate<String> check = literalChecks.get(type.id());
        try {
            return check != null && check.test(words);
        } catch (RuntimeException | LinkageError e) {
            return false;
        }
    }

    /**
     * Whether {@code words} are a literal of some type whose values can be used as {@code expected}, like a
     * potion effect type where a potion effect is expected. Checks are strict here.
     */
    public boolean isConvertibleLiteral(String words, java.util.function.Predicate<String> expected) {
        for (Map.Entry<String, java.util.function.Predicate<String>> check : literalChecks.entrySet()) {
            if (!expected.test(check.getKey())) {
                continue;
            }
            try {
                if (check.getValue().test(words)) {
                    return true;
                }
            } catch (RuntimeException | LinkageError e) {
                // Not without a server.
            }
        }
        return false;
    }

    /** Whether {@code name} names a kind of entity, as in {@code the zombie} or {@code event-creeper}. */
    public boolean isEntityName(String name) {
        return entityNames.contains(normalize(name));
    }

    /** Whether {@code words} name an entity type, by what the entity type's literals accept. */
    public boolean isEntityTypeLiteral(String words) {
        java.util.function.Predicate<String> check = literalChecks.get("entitytype");
        try {
            return check != null && check.test(words);
        } catch (RuntimeException | LinkageError e) {
            return false;
        }
    }

    /** Whether {@code words} name an item, by what the item type's literals accept. */
    public boolean isItemTypeLiteral(String words) {
        java.util.function.Predicate<String> check = literalChecks.get("itemtype");
        try {
            return check != null && check.test(words);
        } catch (RuntimeException | LinkageError e) {
            return false;
        }
    }

    static String normalize(String name) {
        String lower = name.strip().toLowerCase(Locale.ROOT);
        if (lower.indexOf("  ") < 0 && lower.indexOf('\t') < 0) {
            return lower;
        }
        return lower.replaceAll("\\s+", " ");
    }

    /** English plural of a type name, pluralizing the last word: {@code living entity} → {@code living entities}. */
    public static String plural(String name) {
        if (name.endsWith("y") && name.length() > 1 && "aeiou".indexOf(name.charAt(name.length() - 2)) < 0) {
            return name.substring(0, name.length() - 1) + "ies";
        }
        if (name.endsWith("s") || name.endsWith("x") || name.endsWith("z") || name.endsWith("ch")
                || name.endsWith("sh")) {
            return name + "es";
        }
        return name + "s";
    }

    private static Map<String, Set<String>> closure(Map<String, Set<String>> direct) {
        Map<String, Set<String>> result = new HashMap<>();
        for (String type : direct.keySet()) {
            Set<String> seen = new HashSet<>();
            Deque<String> queue = new ArrayDeque<>(direct.get(type));
            while (!queue.isEmpty()) {
                String next = queue.pop();
                if (seen.add(next)) {
                    queue.addAll(direct.getOrDefault(next, Set.of()));
                }
            }
            result.put(type, Set.copyOf(seen));
        }
        return Map.copyOf(result);
    }

    public static final class Builder {

        private final Map<String, TypeInfo> typesById = new LinkedHashMap<>();
        private final Map<String, TypeRef> typeNames = new HashMap<>();
        private final Map<SyntaxKind, List<SyntaxInfo>> syntaxes = new EnumMap<>(SyntaxKind.class);
        private final Map<String, List<FunctionInfo>> functions = new HashMap<>();
        private final Map<String, Set<String>> supertypes = new HashMap<>();
        private final Set<String> strictTypes = new HashSet<>();
        private final Set<String> enumerableTypes = new HashSet<>();
        private final Map<String, java.util.function.Predicate<String>> literalChecks = new HashMap<>();
        private final Map<String, java.util.function.Predicate<String>> parseChecks = new HashMap<>();
        private final Map<String, Set<String>> conversions = new HashMap<>();
        private final Set<String> entityNames = new HashSet<>();
        private final Set<String> delaying = new HashSet<>();
        private final Set<String> stopping = new HashSet<>();
        private final Set<String> ending = new HashSet<>();
        private final Set<String> suppressing = new HashSet<>();
        private final Set<String> sectionOnly = new HashSet<>();
        private final Map<String, ParseCheck> checks = new HashMap<>();

        private Builder() {}

        /** Marks a syntax element as making the trigger wait. */
        public Builder delays(SyntaxKind kind, String id) {
            delaying.add(kind + ":" + id);
            return this;
        }

        /**
         * Marks a syntax element as always ending the trigger when matched by {@code pattern}, or by any
         * pattern if it's -1, as Skript's execution intents do.
         */
        public Builder stops(SyntaxKind kind, String id, int pattern) {
            stopping.add(kind + ":" + id + ":" + (pattern < 0 ? "*" : String.valueOf(pattern)));
            return this;
        }

        /** Marks a syntax element as ending the section it's in. */
        public Builder ends(SyntaxKind kind, String id) {
            ending.add(kind + ":" + id);
            return this;
        }

        /** Marks a syntax element as suppressing or allowing warnings while parsing, by its tags. */
        public Builder suppressesWarnings(SyntaxKind kind, String id) {
            suppressing.add(kind + ":" + id);
            return this;
        }

        /** Marks an expression as only usable with a section under it. */
        public Builder sectionOnly(String id) {
            sectionOnly.add(id);
            return this;
        }

        /** Adds a check the syntax element makes when a section is written under its line. */
        public Builder sectionCheck(SyntaxKind kind, String id, ParseCheck check) {
            checks.put("section:" + kind + ":" + id, check);
            return this;
        }

        /** Adds a check the syntax element makes while it's parsed. */
        public Builder check(SyntaxKind kind, String id, ParseCheck check) {
            checks.put(kind + ":" + id, check);
            return this;
        }

        /** Another name for a type: {@code alias} singular and {@code plural} for the plural. */
        public Builder alias(String typeId, String alias, String plural) {
            TypeInfo type = typesById.get(typeId.toLowerCase(Locale.ROOT));
            if (type == null) {
                throw new IllegalArgumentException("Unknown type: " + typeId);
            }
            typeNames.put(normalize(alias), new TypeRef(type, false));
            typeNames.put(normalize(plural), new TypeRef(type, true));
            return this;
        }

        /**
         * Adds a type, known by its id and display name and their plurals, plus any {@code aliases} (for
         * irregular plurals, write them as {@code name/plural}).
         */
        public Builder type(TypeInfo type, String... aliases) {
            String id = type.id().toLowerCase(Locale.ROOT);
            typesById.put(id, type);
            nameType(id, type);
            nameType(normalize(type.name()), type);
            nameType(normalize(type.name()).replace(" ", ""), type);
            for (String alias : aliases) {
                String[] forms = alias.split("/", 2);
                typeNames.put(normalize(forms[0]), new TypeRef(type, false));
                if (forms.length > 1) {
                    typeNames.put(normalize(forms[1]), new TypeRef(type, true));
                }
            }
            return this;
        }

        /**
         * Marks a type's literals as open: any words might be one (item names), so the parser leaves words
         * where a value of the type is expected for the compiler to resolve.
         */
        public Builder openLiterals(String id, java.util.function.Predicate<String> check) {
            literalChecks.put(id.toLowerCase(Locale.ROOT), check);
            return openLiterals(id);
        }

        /** The type's parser, for literals that say their type. */
        public Builder parses(String id, java.util.function.Predicate<String> check) {
            parseChecks.put(id.toLowerCase(Locale.ROOT), check);
            return this;
        }

        public Builder openLiterals(String id) {
            TypeInfo old = typesById.get(id.toLowerCase(Locale.ROOT));
            if (old == null || old.openLiterals()) {
                return this;
            }
            TypeInfo open = new TypeInfo(old.id(), old.name(), old.literals(), true);
            typesById.put(id.toLowerCase(Locale.ROOT), open);
            typeNames.replaceAll((name, ref) -> ref.type().id().equals(old.id()) ? new TypeRef(open, ref.plural()) : ref);
            return this;
        }

        private void nameType(String name, TypeInfo type) {
            typeNames.putIfAbsent(name, new TypeRef(type, false));
            typeNames.putIfAbsent(plural(name), new TypeRef(type, true));
        }

        public Builder syntax(SyntaxInfo syntax) {
            syntaxes.computeIfAbsent(syntax.kind(), k -> new ArrayList<>()).add(syntax);
            return this;
        }

        /** Adds a built-in function, or another overload of one. */
        public Builder function(FunctionInfo function) {
            functions.computeIfAbsent(function.name(), k -> new ArrayList<>()).add(function);
            return this;
        }

        /** Values of type {@code sub} can be used as any of {@code supers} (subtype or conversion). */
        public Builder assignable(String sub, String... supers) {
            supertypes.computeIfAbsent(sub, k -> new HashSet<>()).addAll(List.of(supers));
            return this;
        }

        /**
         * Values of {@code from} can be converted to {@code to} where it's expected. Unlike
         * {@link #assignable}, conversions don't chain: an entity converts to a location, a location to a
         * block, but an entity isn't a block.
         */
        public Builder convertible(String from, String to) {
            conversions.computeIfAbsent(from, k -> new HashSet<>()).add(to);
            return this;
        }

        /** Lets {@code all <type>} list the values of type {@code id}, as for biomes. */
        public Builder enumerable(String id) {
            enumerableTypes.add(id.toLowerCase(Locale.ROOT));
            return this;
        }

        /** Types that only accept themselves and registered conversions, like numbers and text. */

        public Builder strict(String... types) {
            strictTypes.addAll(List.of(types));
            return this;
        }

        /** Names of kinds of entity, singular and plural, e.g. {@code zombie} and {@code zombies}. */
        public Builder entityNames(Iterable<String> names) {
            for (String name : names) {
                entityNames.add(normalize(name));
                entityNames.add(plural(normalize(name)));
            }
            return this;
        }

        public SyntaxRegistry build() {
            return new SyntaxRegistry(this);
        }
    }
}
