/*
 * Copyright (c) 2026 Xena Studios
 * SPDX-License-Identifier: GPL-3.0-only
 */
package co.xenastudios.neoskript.frontend.registry;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Everything a registry knows that a reader of the syntax wants: each element's patterns as they were written at
 * registration, return types, event names and values, types and built-in functions; and what the parser needs
 * besides to check scripts as the registry would ({@code parser} in the JSON), which {@link ExportedRegistry} builds
 * a registry from. The plugin writes it as JSON ({@link #toJson()}) for neoskript.dev's syntax reference and
 * in-browser parser; {@link #fromJson(String)} reads it back. Plain JSON, no library, so the browser parser can
 * load the same file.
 *
 * <p>What a registry can't write down is left out: the checks syntax makes while it's parsed, and the parsers
 * that tell whether words are a literal of an open type such as item types, which need a server.
 *
 * @param format the version of this format; readers should refuse one they don't know
 * @param neoskript the NeoSkript version that wrote it
 * @param server the server it was written on, e.g. {@code Paper 26.3}
 */
public record SyntaxExport(int format, String neoskript, String server, List<Element> elements, List<Type> types,
        List<Function> functions, Parser parser) {

    /** 2 added {@code parser} to the file and to its elements and types. */
    public static final int FORMAT = 2;

    public SyntaxExport {
        elements = List.copyOf(elements);
        types = List.copyOf(types);
        functions = List.copyOf(functions);
    }

    /**
     * A syntax element.
     *
     * @param kind {@code structure}, {@code event}, {@code section}, {@code effect}, {@code condition} or
     *     {@code expression}
     * @param returnType for expressions, the id of the type they return; {@code null} otherwise
     * @param eventName for events, what Skript calls the event ({@code Break / Mine}), or {@code null} if it has
     *     no counterpart in Skript or isn't an event
     * @param eventValues for server events, the values code under the event can use; empty otherwise
     */
    public record Element(String kind, String id, List<String> patterns, String returnType, String eventName,
            List<EventValue> eventValues, ElementParser parser) {

        public Element {
            patterns = List.copyOf(patterns);
            eventValues = List.copyOf(eventValues);
        }
    }

    /**
     * What the parser knows about an element besides its patterns.
     *
     * @param regexRoles what the text each {@code <regex>} matches is, by {@link RegexRole} name
     * @param delays whether it makes the trigger wait
     * @param stops the patterns with which it always ends the trigger
     * @param ends whether code after it in its section never runs
     * @param suppressesWarnings whether it suppresses or allows warnings, by its tags
     * @param sectionOnly whether, as an expression, it can only be used with a section under it
     * @param experiment the experiment it needs, or {@code null}
     * @param experimentSilent whether the line's error leaves out which experiment to enable
     * @param property the type property it is, or {@code null}
     * @param core whether the compiler implements it itself, rather than a library element
     * @param eventValue whether it's the event's value of a type ({@code event-player}, {@code the zombie})
     * @param keepsEventValues for a section, whether code in it has only the event values of the code around it
     * @param scope for an event, the index in {@link Parser#scopes()} of what code under it can use; -1 if unknown
     */
    public record ElementParser(List<String> regexRoles, boolean delays, List<Integer> stops, boolean ends,
            boolean suppressesWarnings, boolean sectionOnly, String experiment, boolean experimentSilent,
            Property property, boolean core, boolean eventValue, boolean keepsEventValues, int scope) {

        /** An element the parser knows nothing more about. */
        public static final ElementParser NONE = new ElementParser(List.of(), false, List.of(), false, false, false,
                null, false, null, false, false, false, -1);

        public ElementParser {
            regexRoles = List.copyOf(regexRoles);
            stops = List.copyOf(stops);
        }
    }

    /**
     * A type property syntax, as {@link SyntaxRegistry.PropertySyntax}: the property it is, by the tags it was
     * written with, is the first of {@code byTag} whose tag it has, else {@code otherwise}.
     */
    public record Property(int argument, boolean counts, Map<String, String> byTag, String otherwise) {

        public Property {
            byTag = Collections.unmodifiableMap(new LinkedHashMap<>(byTag));
        }
    }

    /**
     * A value an event has: {@code event-player}, or with {@code time} -1 {@code past event-block}, 1 for future.
     *
     * @param type the id of the value's type
     */
    public record EventValue(String type, int time) {}

    /**
     * A type.
     *
     * @param literals the values written as words, for a closed set like game modes; empty otherwise
     */
    public record Type(String id, String name, String plural, List<String> literals, TypeParser parser) {

        public Type {
            literals = List.copyOf(literals);
        }
    }

    /**
     * What the parser knows about a type besides its literals.
     *
     * @param names every name it's known by, singular
     * @param pluralNames every name it's known by, plural
     * @param supertypes the types its values can be used as, theirs included
     * @param strict whether it only takes itself, its subtypes and conversions
     * @param enumerable whether {@code all <type>} lists its values
     * @param openLiterals whether any words might be one of its literals, as item names
     * @param eventDefault whether a left-out placeholder of it gets the event's value
     * @param defaultValue whether a left-out placeholder of it gets a value when nothing else gives one
     * @param parsedWords words besides its literals that its parser takes, for a literal that names its type
     *     ({@code ender pearl (teleport cause)}): of the words tried, which are every type's literals and the names
     *     of an enum's constants. The parser may take more.
     * @param parsesAnyWords whether its parser takes made-up words too (text, player names), so takes about any
     * @param literalWords for an open type, the words tried (as for {@code parsedWords}) that its literal parser
     *     takes. It may take more.
     */
    public record TypeParser(List<String> names, List<String> pluralNames, List<String> supertypes, boolean strict,
            boolean enumerable, boolean openLiterals, boolean eventDefault, boolean defaultValue,
            List<String> parsedWords, boolean parsesAnyWords, List<String> literalWords) {

        public TypeParser {
            names = List.copyOf(names);
            pluralNames = List.copyOf(pluralNames);
            supertypes = List.copyOf(supertypes);
            parsedWords = List.copyOf(parsedWords);
            literalWords = List.copyOf(literalWords);
        }
    }

    /** A built-in function. {@code returnType} is {@code null} if it returns nothing. */
    public record Function(String name, List<FunctionInfo.Parameter> parameters, String returnType,
            boolean returnsPlural) {

        public Function {
            parameters = List.copyOf(parameters);
        }
    }

    /**
     * What the parser needs that isn't any one element's or type's.
     *
     * @param conversions what converts to what, which unlike supertypes doesn't chain
     * @param typeProperties Skript's type properties ({@code name}, {@code amount}), each with the types that have it
     * @param experiments each experiment's code name, with a regex for what scripts may write after {@code using}
     * @param scopes the event values code can use somewhere, which events, commands and functions point to
     * @param commandScope the index in {@code scopes} of what a command's code can use; -1 if unknown
     * @param functionScope the index in {@code scopes} of what a function's code can use; -1 if unknown
     */
    public record Parser(List<Conversion> conversions, Map<String, List<String>> typeProperties,
            Map<String, String> experiments, List<Scope> scopes, int commandScope, int functionScope) {

        public static final Parser NONE = new Parser(List.of(), Map.of(), Map.of(), List.of(), -1, -1);

        public Parser {
            conversions = List.copyOf(conversions);
            Map<String, List<String>> properties = new LinkedHashMap<>();
            typeProperties.forEach((property, types) -> properties.put(property, List.copyOf(types)));
            typeProperties = Collections.unmodifiableMap(properties);
            experiments = Collections.unmodifiableMap(new LinkedHashMap<>(experiments));
            scopes = List.copyOf(scopes);
        }
    }

    /** {@code from} converts to {@code to}; with {@code placeholderOnly}, only where a placeholder takes it. */
    public record Conversion(String from, String to, boolean placeholderOnly) {}

    /**
     * The event values code can use somewhere, by the ids of their types.
     *
     * @param values the types whose single value code can get, as {@code event-player}
     * @param plurals the types whose values code can get as a list, as {@code event-players}
     * @param defaults the types whose value fills in a left-out placeholder of a type that takes the event's
     */
    public record Scope(List<String> values, List<String> plurals, List<String> defaults) {

        public Scope {
            values = List.copyOf(values);
            plurals = List.copyOf(plurals);
            defaults = List.copyOf(defaults);
        }
    }

    /** The lower-case name of a kind, as the export writes it. */
    public static String kindName(SyntaxKind kind) {
        return kind.name().toLowerCase(Locale.ROOT);
    }

    public String toJson() {
        StringBuilder out = new StringBuilder();
        Map<String, Object> root = new LinkedHashMap<>();
        root.put("format", format);
        root.put("neoskript", neoskript);
        root.put("server", server);
        List<Object> elementList = new ArrayList<>();
        for (Element element : elements) {
            Map<String, Object> map = new LinkedHashMap<>();
            map.put("kind", element.kind());
            map.put("id", element.id());
            map.put("patterns", element.patterns());
            if (element.returnType() != null) {
                map.put("returnType", element.returnType());
            }
            if (element.eventName() != null) {
                map.put("eventName", element.eventName());
            }
            if (!element.eventValues().isEmpty()) {
                List<Object> values = new ArrayList<>();
                for (EventValue value : element.eventValues()) {
                    Map<String, Object> v = new LinkedHashMap<>();
                    v.put("type", value.type());
                    v.put("time", value.time());
                    values.add(v);
                }
                map.put("eventValues", values);
            }
            Map<String, Object> parser = elementParser(element.parser());
            if (!parser.isEmpty()) {
                map.put("parser", parser);
            }
            elementList.add(map);
        }
        root.put("elements", elementList);
        List<Object> typeList = new ArrayList<>();
        for (Type type : types) {
            Map<String, Object> map = new LinkedHashMap<>();
            map.put("id", type.id());
            map.put("name", type.name());
            map.put("plural", type.plural());
            if (!type.literals().isEmpty()) {
                map.put("literals", type.literals());
            }
            if (type.parser() != null) {
                map.put("parser", typeParser(type.parser()));
            }
            typeList.add(map);
        }
        root.put("types", typeList);
        List<Object> functionList = new ArrayList<>();
        for (Function function : functions) {
            Map<String, Object> map = new LinkedHashMap<>();
            map.put("name", function.name());
            List<Object> parameters = new ArrayList<>();
            for (FunctionInfo.Parameter parameter : function.parameters()) {
                Map<String, Object> p = new LinkedHashMap<>();
                p.put("name", parameter.name());
                p.put("type", parameter.type());
                p.put("plural", parameter.plural());
                p.put("optional", parameter.optional());
                parameters.add(p);
            }
            map.put("parameters", parameters);
            if (function.returnType() != null) {
                map.put("returnType", function.returnType());
                map.put("returnsPlural", function.returnsPlural());
            }
            functionList.add(map);
        }
        root.put("functions", functionList);
        root.put("parser", parser(parser));
        Json.write(root, out, 0);
        return out.append('\n').toString();
    }

    private static Map<String, Object> elementParser(ElementParser parser) {
        Map<String, Object> map = new LinkedHashMap<>();
        if (parser == null) {
            return map;
        }
        if (!parser.regexRoles().isEmpty()) {
            map.put("regexRoles", parser.regexRoles());
        }
        flag(map, "delays", parser.delays());
        if (!parser.stops().isEmpty()) {
            map.put("stops", parser.stops());
        }
        flag(map, "ends", parser.ends());
        flag(map, "suppressesWarnings", parser.suppressesWarnings());
        flag(map, "sectionOnly", parser.sectionOnly());
        if (parser.experiment() != null) {
            map.put("experiment", parser.experiment());
            flag(map, "experimentSilent", parser.experimentSilent());
        }
        if (parser.property() != null) {
            Map<String, Object> property = new LinkedHashMap<>();
            property.put("argument", parser.property().argument());
            property.put("counts", parser.property().counts());
            property.put("byTag", new LinkedHashMap<>(parser.property().byTag()));
            property.put("otherwise", parser.property().otherwise());
            map.put("property", property);
        }
        flag(map, "core", parser.core());
        flag(map, "eventValue", parser.eventValue());
        flag(map, "keepsEventValues", parser.keepsEventValues());
        if (parser.scope() >= 0) {
            map.put("scope", parser.scope());
        }
        return map;
    }

    private static Map<String, Object> typeParser(TypeParser parser) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("names", parser.names());
        map.put("pluralNames", parser.pluralNames());
        if (!parser.supertypes().isEmpty()) {
            map.put("supertypes", parser.supertypes());
        }
        flag(map, "strict", parser.strict());
        flag(map, "enumerable", parser.enumerable());
        flag(map, "openLiterals", parser.openLiterals());
        flag(map, "eventDefault", parser.eventDefault());
        flag(map, "defaultValue", parser.defaultValue());
        if (!parser.parsedWords().isEmpty()) {
            map.put("parsedWords", parser.parsedWords());
        }
        flag(map, "parsesAnyWords", parser.parsesAnyWords());
        if (!parser.literalWords().isEmpty()) {
            map.put("literalWords", parser.literalWords());
        }
        return map;
    }

    private static Map<String, Object> parser(Parser parser) {
        Map<String, Object> map = new LinkedHashMap<>();
        List<Object> conversions = new ArrayList<>();
        for (Conversion conversion : parser.conversions()) {
            Map<String, Object> c = new LinkedHashMap<>();
            c.put("from", conversion.from());
            c.put("to", conversion.to());
            flag(c, "placeholderOnly", conversion.placeholderOnly());
            conversions.add(c);
        }
        map.put("conversions", conversions);
        map.put("typeProperties", new LinkedHashMap<>(parser.typeProperties()));
        map.put("experiments", new LinkedHashMap<>(parser.experiments()));
        List<Object> scopes = new ArrayList<>();
        for (Scope scope : parser.scopes()) {
            Map<String, Object> s = new LinkedHashMap<>();
            s.put("values", scope.values());
            s.put("plurals", scope.plurals());
            s.put("defaults", scope.defaults());
            scopes.add(s);
        }
        map.put("scopes", scopes);
        map.put("commandScope", parser.commandScope());
        map.put("functionScope", parser.functionScope());
        return map;
    }

    private static void flag(Map<String, Object> map, String key, boolean value) {
        if (value) {
            map.put(key, true);
        }
    }

    /** Reads what {@link #toJson()} wrote. */
    @SuppressWarnings("unchecked")
    public static SyntaxExport fromJson(String json) {
        Map<String, Object> root = (Map<String, Object>) Json.read(json);
        int format = ((Number) root.get("format")).intValue();
        if (format != FORMAT) {
            throw new IllegalArgumentException("Unknown syntax export format " + format + ", expected " + FORMAT);
        }
        List<Element> elements = new ArrayList<>();
        for (Object o : (List<Object>) root.get("elements")) {
            Map<String, Object> map = (Map<String, Object>) o;
            List<EventValue> values = new ArrayList<>();
            for (Object v : (List<Object>) map.getOrDefault("eventValues", List.of())) {
                Map<String, Object> value = (Map<String, Object>) v;
                values.add(new EventValue((String) value.get("type"), ((Number) value.get("time")).intValue()));
            }
            elements.add(new Element((String) map.get("kind"), (String) map.get("id"),
                    strings(map.get("patterns")), (String) map.get("returnType"), (String) map.get("eventName"),
                    values, readElementParser((Map<String, Object>) map.get("parser"))));
        }
        List<Type> types = new ArrayList<>();
        for (Object o : (List<Object>) root.get("types")) {
            Map<String, Object> map = (Map<String, Object>) o;
            types.add(new Type((String) map.get("id"), (String) map.get("name"), (String) map.get("plural"),
                    strings(map.getOrDefault("literals", List.of())),
                    readTypeParser((Map<String, Object>) map.get("parser"))));
        }
        List<Function> functions = new ArrayList<>();
        for (Object o : (List<Object>) root.get("functions")) {
            Map<String, Object> map = (Map<String, Object>) o;
            List<FunctionInfo.Parameter> parameters = new ArrayList<>();
            for (Object p : (List<Object>) map.get("parameters")) {
                Map<String, Object> parameter = (Map<String, Object>) p;
                parameters.add(new FunctionInfo.Parameter((String) parameter.get("name"),
                        (String) parameter.get("type"), (Boolean) parameter.get("plural"),
                        (Boolean) parameter.get("optional")));
            }
            functions.add(new Function((String) map.get("name"), parameters, (String) map.get("returnType"),
                    Boolean.TRUE.equals(map.get("returnsPlural"))));
        }
        return new SyntaxExport(format, (String) root.get("neoskript"), (String) root.get("server"), elements,
                types, functions, readParser((Map<String, Object>) root.get("parser")));
    }

    @SuppressWarnings("unchecked")
    private static ElementParser readElementParser(Map<String, Object> map) {
        if (map == null) {
            return ElementParser.NONE;
        }
        Property property = null;
        if (map.get("property") instanceof Map<?, ?> p) {
            Map<String, String> byTag = new LinkedHashMap<>();
            ((Map<String, Object>) p.get("byTag")).forEach((tag, name) -> byTag.put(tag, (String) name));
            property = new Property(((Number) p.get("argument")).intValue(), Boolean.TRUE.equals(p.get("counts")),
                    byTag, (String) p.get("otherwise"));
        }
        List<Integer> stops = new ArrayList<>();
        for (Object stop : (List<Object>) map.getOrDefault("stops", List.of())) {
            stops.add(((Number) stop).intValue());
        }
        return new ElementParser(strings(map.getOrDefault("regexRoles", List.of())), flag(map, "delays"), stops,
                flag(map, "ends"), flag(map, "suppressesWarnings"), flag(map, "sectionOnly"),
                (String) map.get("experiment"), flag(map, "experimentSilent"), property, flag(map, "core"),
                flag(map, "eventValue"), flag(map, "keepsEventValues"),
                map.get("scope") instanceof Number n ? n.intValue() : -1);
    }

    private static TypeParser readTypeParser(Map<String, Object> map) {
        if (map == null) {
            return null;
        }
        return new TypeParser(strings(map.getOrDefault("names", List.of())),
                strings(map.getOrDefault("pluralNames", List.of())), strings(map.getOrDefault("supertypes", List.of())),
                flag(map, "strict"), flag(map, "enumerable"), flag(map, "openLiterals"), flag(map, "eventDefault"),
                flag(map, "defaultValue"), strings(map.getOrDefault("parsedWords", List.of())),
                flag(map, "parsesAnyWords"), strings(map.getOrDefault("literalWords", List.of())));
    }

    @SuppressWarnings("unchecked")
    private static Parser readParser(Map<String, Object> map) {
        if (map == null) {
            return Parser.NONE;
        }
        List<Conversion> conversions = new ArrayList<>();
        for (Object o : (List<Object>) map.getOrDefault("conversions", List.of())) {
            Map<String, Object> c = (Map<String, Object>) o;
            conversions.add(new Conversion((String) c.get("from"), (String) c.get("to"), flag(c, "placeholderOnly")));
        }
        Map<String, List<String>> properties = new LinkedHashMap<>();
        ((Map<String, Object>) map.getOrDefault("typeProperties", Map.of()))
                .forEach((property, types) -> properties.put(property, strings(types)));
        Map<String, String> experiments = new LinkedHashMap<>();
        ((Map<String, Object>) map.getOrDefault("experiments", Map.of()))
                .forEach((name, pattern) -> experiments.put(name, (String) pattern));
        List<Scope> scopes = new ArrayList<>();
        for (Object o : (List<Object>) map.getOrDefault("scopes", List.of())) {
            Map<String, Object> s = (Map<String, Object>) o;
            scopes.add(new Scope(strings(s.get("values")), strings(s.get("plurals")), strings(s.get("defaults"))));
        }
        return new Parser(conversions, properties, experiments, scopes, index(map.get("commandScope")),
                index(map.get("functionScope")));
    }

    private static int index(Object value) {
        return value instanceof Number n ? n.intValue() : -1;
    }

    private static boolean flag(Map<?, ?> map, String key) {
        return Boolean.TRUE.equals(map.get(key));
    }

    @SuppressWarnings("unchecked")
    private static List<String> strings(Object list) {
        return (List<String>) list;
    }
}
