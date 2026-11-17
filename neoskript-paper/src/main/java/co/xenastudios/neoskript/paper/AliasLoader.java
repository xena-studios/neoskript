/*
 * Copyright (c) 2026 Xena Studios
 * SPDX-License-Identifier: GPL-3.0-only
 */
package co.xenastudios.neoskript.paper;

import co.xenastudios.neoskript.frontend.source.Diagnostic;
import co.xenastudios.neoskript.frontend.source.Diagnostics;
import co.xenastudios.neoskript.frontend.source.SourceFile;
import co.xenastudios.neoskript.frontend.structure.Line;
import co.xenastudios.neoskript.frontend.structure.Node;
import co.xenastudios.neoskript.frontend.structure.StructureParser;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Reads alias files in Skript's format, as Skript 2.17.0's {@code AliasesParser} and {@code AliasesProvider} do:
 * sections of {@code name = items} lines, with optional parts ({@code [big] rock}), choices ({@code (rock|stone)}),
 * plurals ({@code rock¦s}), variations ({@code {woods}:} sections and {@code {woods} plank¦s = -planks}), block
 * states ({@code minecraft:anvil[facing=north]}), components ({@code minecraft:diamond {custom_model_data=...}}),
 * {@code minecraft version} conditions and other aliases ({@code gems = ruby, sapphire}).
 *
 * <p>Files are read in turn into one {@link AliasTable}, as Skript loads its default aliases and then
 * {@code plugins/Skript/aliases}: an alias whose name is already taken adds its items to that alias, variations
 * stay defined for the files after, and a Minecraft item named by any alias file doesn't get the name Skript
 * otherwise makes from its id ({@code red dye}).
 *
 * <p>Problems are reported as Skript reports them, with the same messages. Where Skript itself fails (an alias
 * starting with {@code {}, a block state without {@code =}, components the server can't read, an unknown version
 * or a lone {@code }}, which stop Skript loading every alias), this reports an error for the line and goes on.
 */
final class AliasLoader {

    /** What the loader needs from the server. */
    interface Server {

        /**
         * The material an id stands for, as Skript's {@code BukkitUnsafe.getMaterialFromNamespacedId}
         * ({@code minecraft:stone} and {@code stone} are both stone), by its key, or {@code null}.
         */
        String material(String id);

        boolean isItem(String material);

        boolean isBlock(String material);

        /**
         * An item of {@code material} with {@code components} applied, as {@code [minecraft:x=y, ...]}.
         *
         * @throws IllegalArgumentException with the reason if the components can't be applied
         */
        Made item(String material, String components);

        /**
         * The block data of {@code material} with {@code states}, as {@code [facing=north]} ({@code []} for the
         * default).
         *
         * @throws IllegalArgumentException if they aren't the block's states
         */
        Made blockData(String material, String states);

        /** The server's Minecraft version: major, minor, patch. */
        int[] version();

        /** Every material that isn't legacy, by key, in the order of Bukkit's {@code Material.values()}. */
        List<String> materials();
    }

    /**
     * Something the server made: {@code value} for NeoSkript to use (an item stack or block data), and {@code key},
     * equal for two that are the same.
     */
    record Made(Object value, String key) {}

    /**
     * A problem with a line of an alias file: the file's name, the line (0 for the file as a whole), and that line's
     * code, so a problem never keeps the file's text.
     */
    record Problem(String file, int line, boolean error, String message, String code) {}

    /** A file to read: its name as problems give it (Skript gives the file name alone), and its text. */
    record Input(String name, String text) {}

    private final Server server;
    private final int[] version;
    private final List<Problem> problems = new ArrayList<>();
    /** Problems past {@link #MAX_PROBLEMS}, which are counted but not kept, and how many of them are errors. */
    private int moreProblems;
    private int moreErrors;

    /** The most problems kept; the rest are counted, so a file of bad lines can't fill the heap or the chat. */
    static final int MAX_PROBLEMS = 1000;

    /** The current file and line, for problems. */
    private SourceFile file;
    private int line;
    /**
     * How often each problem of the current line came up. Skript reports a problem once for each name a line stands
     * for; past {@link #MAX_REPEATS} the same problem isn't repeated, so a line with thousands of names can't flood
     * the console.
     */
    private final Map<String, Integer> lineProblems = new HashMap<>();

    static final int MAX_REPEATS = 64;

    // What's been loaded: Skript's provider.
    private final Map<String, AliasTable.Type> aliases = new HashMap<>();
    private final Map<String, VariationGroup> variations = new HashMap<>();
    private final Set<String> covered = new HashSet<>();
    private final Map<String, AliasTable.Names> names = new HashMap<>();
    private int nameCount;

    /** The most names one line may stand for, so a line like {@code [a][b][c]...} can't run out of memory. */
    static final int MAX_NAMES_PER_LINE = 1 << 14;
    /** The most names all the files may define, for the same reason. */
    static final int MAX_NAMES = 1 << 21;

    AliasLoader(Server server) {
        this.server = server;
        this.version = server.version();
    }

    /** Reads {@code inputs} in order, then gives the result, with Skript's names for the items no file named. */
    static AliasTable load(Server server, List<Input> inputs, List<Problem> problems) {
        AliasLoader loader = new AliasLoader(server);
        for (Input input : inputs) {
            loader.read(input);
        }
        AliasTable table = loader.finish();
        problems.addAll(loader.problems);
        return table;
    }

    /** The problems kept so far, in order. */
    List<Problem> problems() {
        return problems;
    }

    /** How many problems there were past {@link #MAX_PROBLEMS}. */
    int moreProblems() {
        return moreProblems;
    }

    /** How many of {@link #moreProblems} are errors; the rest are warnings. */
    int moreErrors() {
        return moreErrors;
    }

    /** A problem with a whole file, such as one that can't be read. */
    void fileProblem(String name, boolean error, String message) {
        report(new Problem(name, 0, error, message, ""));
    }

    /** Whether another problem would be listed rather than only counted. */
    private boolean listing() {
        return problems.size() < MAX_PROBLEMS;
    }

    private void report(Problem problem) {
        if (listing()) {
            problems.add(problem);
        } else {
            moreProblems++;
            if (problem.error()) {
                moreErrors++;
            }
        }
    }

    // --- files ---

    /** Reads one file into what's loaded. Its text isn't kept. */
    void read(Input input) {
        file = new SourceFile(input.name(), input.text());
        fileLines = null;
        line = 0;
        lineProblems.clear();
        if (input.text().isEmpty()) {
            problem(false, "'" + input.name() + "' is empty");
            return;
        }
        Diagnostics diagnostics = new Diagnostics(file);
        List<Node> nodes;
        try {
            nodes = StructureParser.parse(file, diagnostics);
        } catch (RuntimeException | StackOverflowError e) {
            problem(true, "Couldn't read this file: " + e);
            return;
        }
        // The file's layout first, as Skript reads the whole file before loading its aliases.
        for (Diagnostic diagnostic : diagnostics.all()) {
            if (diagnostic.message().startsWith("Empty configuration section!") && sectionWithEquals(nodes, diagnostic.line())) {
                continue;
            }
            report(new Problem(file.name(), diagnostic.line(),
                    diagnostic.severity() == co.xenastudios.neoskript.frontend.source.Severity.ERROR, diagnostic.message(),
                    listing() ? code(diagnostic.line()) : ""));
        }
        notEntries(nodes);
        for (Node node : nodes) {
            at(node.line());
            if (node instanceof Node.Section section && !isEntry(section)) {
                section(section);
            } else if (isEntry(node)) {
                problem(true, "Aliases must be put in sections");
            }
        }
    }

    /** Skript's config: a line ending in {@code :} is a section unless it has the separator {@code =} in it. */
    private static boolean isEntry(Node node) {
        return node.line().text().indexOf('=') >= 0;
    }

    private static boolean sectionWithEquals(List<Node> nodes, int line) {
        for (Node node : nodes) {
            if (node.line().number() == line) {
                return node.line().text().indexOf('=') >= 0;
            }
            if (node instanceof Node.Section section && sectionWithEquals(section.children(), line)) {
                return true;
            }
        }
        return false;
    }

    /** Skript's error for each line that's neither a section nor {@code name = value}, which it then leaves out. */
    private void notEntries(List<Node> nodes) {
        for (Node node : nodes) {
            if (node instanceof Node.Section section && !isEntry(section)) {
                notEntries(section.children());
            } else if (!isEntry(node)) {
                at(node.line());
                problem(true, "'" + node.line().text().strip() + "' is not an entry (like 'name = value')");
            }
        }
    }

    private void at(Line at) {
        line = at.number();
        lineProblems.clear();
    }

    private record Entry(String key, String value) {}

    private static Entry entry(Node node) {
        String text = node.line().text().strip();
        int equals = text.indexOf('=');
        return equals < 0 ? null : new Entry(text.substring(0, equals).strip(), text.substring(equals + 1).strip());
    }

    /** Skript's {@code AliasesParser.load}: one section of aliases. */
    private void section(Node.Section section) {
        for (Node node : section.children()) {
            at(node.line());
            if (node instanceof Node.Section variation && !isEntry(variation)) {
                String name = variation.line().key();
                VariationGroup group;
                try {
                    group = variations(variation);
                } catch (RuntimeException | StackOverflowError e) {
                    problem(true, "Couldn't load this variation: " + e);
                    continue;
                }
                if (group != null) {
                    variations.put(name, group);
                } else {
                    at(node.line());
                    problem(true, "A section should be variation section, but " + name + " is not valid variation name");
                }
                continue;
            }
            Entry entry = entry(node);
            if (entry == null) {
                // Not an entry, which reading the file reported.
                continue;
            }
            if (entry.key().equals("minecraft version")) {
                if (!versionMatches(entry.value())) {
                    return;
                }
                continue;
            }
            try {
                alias(entry.key(), entry.value());
            } catch (RuntimeException | StackOverflowError e) {
                // Never a reason to stop: the line is left out, and the rest load.
                problem(true, "Couldn't load this alias: " + e);
            }
            if (nameCount > MAX_NAMES) {
                problem(true, "There are more than " + MAX_NAMES + " aliases, so the rest of the aliases aren't loaded");
                return;
            }
        }
    }

    /** Skript's {@code minecraft version} condition: {@code 1.20.5 or newer}, {@code 1.20.4 or older}, {@code 1.19 to 1.20.4}. */
    private boolean versionMatches(String condition) {
        try {
            int orNewer = condition.indexOf("or newer");
            if (orNewer != -1) {
                return compare(version, parseVersion(condition.substring(0, Math.max(orNewer - 1, 0)))) >= 0;
            }
            int orOlder = condition.indexOf("or older");
            if (orOlder != -1) {
                return compare(version, parseVersion(condition.substring(0, Math.max(orOlder - 1, 0)))) <= 0;
            }
            int to = condition.indexOf("to");
            if (to != -1) {
                int[] first = parseVersion(condition.substring(0, Math.max(to - 1, 0)));
                int[] second = parseVersion(condition.substring(Math.min(to + 3, condition.length())));
                return compare(version, first) >= 0 && compare(version, second) <= 0;
            }
            return compare(version, parseVersion(condition)) == 0;
        } catch (IllegalArgumentException e) {
            // Skript stops loading aliases here; the section is left out instead.
            problem(true, e.getMessage());
            return false;
        }
    }

    private static final Pattern VERSION = Pattern.compile("(\\d+)\\.(\\d+)(?:\\.(\\d+))?(?:-(.*))?");

    /** Skript's {@code Version}: major.minor[.patch][-postfix]. */
    static int[] parseVersion(String text) {
        Matcher matcher = VERSION.matcher(text.strip());
        if (!matcher.matches()) {
            throw new IllegalArgumentException("'" + text + "' is not a valid version string");
        }
        int[] parsed = new int[3];
        for (int i = 0; i < 3; i++) {
            String part = matcher.group(i + 1);
            if (part != null) {
                try {
                    parsed[i] = Integer.parseInt(part);
                } catch (NumberFormatException tooBig) {
                    throw new IllegalArgumentException("'" + text + "' is not a valid version string");
                }
            }
        }
        return parsed;
    }

    private static int compare(int[] a, int[] b) {
        for (int i = 0; i < 3; i++) {
            if (a[i] != b[i]) {
                return Integer.compare(a[i], b[i]);
            }
        }
        return 0;
    }

    // --- variations ---

    /** What a variation or an alias's item adds: Skript's {@code AliasesProvider.Variation}. */
    record Variation(String id, int insertPoint, String components, Map<String, String> states) {

        static final Variation NONE = new Variation(null, -1, null, Map.of());

        /** Skript's {@code Variation.insertId}. */
        String insertId(String inserted) {
            if (inserted == null) {
                return id;
            }
            inserted = inserted.substring(0, inserted.length() - 1);
            if (id == null || insertPoint == -1) {
                return inserted;
            }
            return id.substring(0, insertPoint) + inserted + id.substring(insertPoint + 1);
        }

        /** Skript's {@code Variation.merge}: this one's components and states win over {@code other}'s. */
        Variation merge(Variation other) {
            String mergedComponents = components != null ? components : other.components;
            Map<String, String> mergedStates = new HashMap<>(other.states);
            mergedStates.putAll(states);
            return new Variation(insertId(other.id), -1, mergedComponents, mergedStates);
        }
    }

    /** A variation section's entries, by name, in order. */
    static final class VariationGroup {
        final List<String> keys = new ArrayList<>();
        final List<Variation> values = new ArrayList<>();
    }

    /** Skript's {@code loadVariations}, or {@code null} if the section's name isn't {@code {name}}. */
    private VariationGroup variations(Node.Section section) {
        String name = section.line().key();
        if (!name.startsWith("{") || !name.endsWith("}")) {
            return null;
        }
        VariationGroup group = new VariationGroup();
        for (Node node : section.children()) {
            at(node.line());
            Entry entry = entry(node);
            if (node instanceof Node.Section && !isEntry(node) || entry == null) {
                // Skript fails on a section here (it expects only entries); it's left out instead.
                if (node instanceof Node.Section) {
                    problem(true, "Sections are not allowed here");
                }
                continue;
            }
            List<String> keys;
            try {
                keys = keyPattern(entry.key());
            } catch (TooManyNames e) {
                problem(true, "This variation stands for more than " + MAX_NAMES_PER_LINE + " names, so it isn't loaded");
                continue;
            }
            Variation variation = variation(entry.value());
            if (variation == null) {
                continue;
            }
            boolean useful = false;
            for (String key : keys) {
                if (key.equals("{default}")) {
                    key = "";
                    useful = true;
                }
                group.keys.add(key);
                group.values.add(variation);
            }
            if (!useful && variation.id() == null && variation.components() == null && variation.states().isEmpty()) {
                problem(false, "Variation has no Minecraft id or tags specified, so it is useless");
            }
        }
        at(section.line());
        return group;
    }

    /**
     * Skript's {@code parseVariation}: an id or alias, with components after it in braces and block states in
     * brackets, or {@code -} for none. {@code null}, with an error, where Skript fails.
     */
    private Variation variation(String item) {
        item = item.strip();
        int firstBracket = item.indexOf('{');
        String id;
        String components = null;
        if (firstBracket == -1) {
            id = item;
        } else {
            if (firstBracket == 0) {
                problem(true, "Missing space between the id and the components in " + item);
                return null;
            }
            id = item.substring(0, firstBracket - 1);
            String json;
            int jsonEnd = item.indexOf("} [");
            if (jsonEnd == -1) {
                json = item.substring(firstBracket);
            } else {
                json = item.substring(firstBracket, jsonEnd + 1);
                id = id + item.substring(jsonEnd + 2);
            }
            components = "[" + json.substring(1, Math.max(json.length() - 1, 1)) + "]";
        }
        String typeName;
        Map<String, String> states = new HashMap<>();
        int stateIndex = id.indexOf('[');
        if (stateIndex != -1) {
            if (stateIndex == 0) {
                problem(true, "Missing id or - in " + id);
                return null;
            }
            typeName = id.substring(0, stateIndex);
            String input = id.substring(stateIndex + 1, Math.max(id.length() - 1, stateIndex + 1));
            if (!blockStates(input, states)) {
                return null;
            }
        } else {
            typeName = id;
        }
        if (typeName.equals("-")) {
            typeName = null;
        }
        return new Variation(typeName, typeName == null ? -1 : typeName.indexOf('-'), components, states);
    }

    /** Skript's {@code parseBlockStates}: {@code facing=north,half=top}. */
    private boolean blockStates(String input, Map<String, String> into) {
        int pos = 0;
        while (pos != -1) {
            int comma = input.indexOf(',', pos);
            String pair;
            if (comma == -1) {
                pair = input.substring(pos);
                pos = -1;
            } else {
                pair = input.substring(pos, comma);
                pos = comma + 1;
            }
            String[] parts = pair.split("=");
            if (parts.length < 2) {
                // Skript fails on a state without a value.
                problem(true, "Parsing block state [" + input + "] failed!");
                return false;
            }
            into.put(parts[0], parts[1]);
        }
        return true;
    }

    // --- names ---

    /**
     * Skript's {@code parseKeyPattern}: every name a pattern stands for, in Skript's order (which decides an item's
     * name). Skript tries each group as it closes, again and again; the same names come out here in the same order,
     * without the repeats, so a pattern with many groups can't take forever.
     */
    List<String> keyPattern(String name) {
        LinkedHashMap<String, Boolean> versions = new LinkedHashMap<>();
        keyPattern(name, versions, new HashMap<>(), new int[] {0});
        return new ArrayList<>(versions.keySet());
    }

    private static final Object TOO_MANY = new Object();

    /** Adds what {@code name} stands for to {@code into}, remembering patterns already done. */
    private void keyPattern(String name, LinkedHashMap<String, Boolean> into, Map<String, List<String>> done, int[] budget) {
        List<String> known = done.get(name);
        if (known != null) {
            for (String version : known) {
                into.putIfAbsent(version, Boolean.TRUE);
            }
            return;
        }
        if (++budget[0] > MAX_NAMES_PER_LINE * 4 || into.size() > MAX_NAMES_PER_LINE) {
            throw new TooManyNames();
        }
        LinkedHashMap<String, Boolean> versions = new LinkedHashMap<>();
        boolean simple = true;
        IntStack optionals = new IntStack();
        IntStack choices = new IntStack();
        boolean failed = false;
        for (int i = 0; i < name.length() && !failed; ) {
            int c = name.codePointAt(i);
            if (c == '[') {
                optionals.push(i);
                simple = false;
            } else if (c == '(') {
                choices.push(i);
                simple = false;
            } else if (c == ']') {
                if (optionals.isEmpty()) {
                    problem(true, "Character " + i + " (']') closes a nonexistent section");
                    failed = true;
                    break;
                }
                int start = optionals.pop();
                keyPattern(name.substring(0, start) + name.substring(start + 1, i) + name.substring(i + 1), versions, done, budget);
                keyPattern(name.substring(0, start) + name.substring(i + 1), versions, done, budget);
            } else if (c == ')') {
                if (choices.isEmpty()) {
                    problem(true, "Character " + i + " (')') closes a nonexistent section");
                    failed = true;
                    break;
                }
                int start = choices.pop();
                int optionStart = start;
                int nested = 0;
                for (int j = start + 1; j < i; ) {
                    int d = name.codePointAt(j);
                    if (d == '(' || d == '[') {
                        nested++;
                    } else if (d == ')' || d == ']') {
                        nested--;
                    } else if (d == '|' && nested == 0) {
                        keyPattern(name.substring(0, start) + name.substring(optionStart + 1, j) + name.substring(i + 1),
                                versions, done, budget);
                        optionStart = j;
                    }
                    j += Character.charCount(d);
                }
                keyPattern(name.substring(0, start) + name.substring(optionStart + 1, i) + name.substring(i + 1),
                        versions, done, budget);
            }
            i += Character.charCount(c);
        }
        if (!failed && (!optionals.isEmpty() || !choices.isEmpty())) {
            int errorStart = !optionals.isEmpty() ? optionals.pop() : choices.pop();
            problem(true, "Section starting at character " + errorStart + " ('" + (char) name.codePointAt(errorStart)
                    + "') must be closed");
            failed = true;
        }
        if (!failed && simple) {
            versions.put(name, Boolean.TRUE);
        }
        List<String> result = List.copyOf(versions.keySet());
        done.put(name, result);
        for (String version : result) {
            into.putIfAbsent(version, Boolean.TRUE);
        }
    }

    private static final class TooManyNames extends RuntimeException {
        TooManyNames() {
            super(null, null, false, false);
        }
    }

    private static final class IntStack {
        private int[] ints = new int[4];
        private int pos;

        void push(int value) {
            if (pos == ints.length) {
                ints = java.util.Arrays.copyOf(ints, ints.length * 2);
            }
            ints[pos++] = value;
        }

        int pop() {
            return ints[--pos];
        }

        boolean isEmpty() {
            return pos == 0;
        }
    }

    /** A part of a name: text, or a variation that fills it in. */
    private record Slot(String text, VariationGroup group) {}

    /** Skript's {@code parseKeyVariations}: each name with its variations filled in, and what they add. */
    private LinkedHashMap<String, Variation> keyVariations(String name) {
        int varStart = -1;
        int varEnd = 0;
        List<Slot> slots = new ArrayList<>();
        for (int i = 0; i < name.length(); i += Character.charCount(name.codePointAt(i))) {
            int c = name.codePointAt(i);
            if (c == '{') {
                varStart = i;
                slots.add(new Slot(name.substring(varEnd, i), null));
            } else if (c == '}') {
                if (varStart == -1) {
                    // Skript fails here with an internal error.
                    problem(true, "Character " + i + " ('}') closes a nonexistent section");
                    continue;
                }
                String varName = name.substring(varStart, i + 1);
                VariationGroup group = variations.get(varName);
                if (group == null) {
                    problem(true, "The variation " + varName + " has not been defined before");
                    varStart = -1;
                    varEnd = i + 1;
                    continue;
                }
                slots.add(new Slot(null, group));
                varStart = -1;
                varEnd = i + 1;
            }
        }
        slots.add(new Slot(name.substring(varEnd), null));
        if (varStart != -1) {
            problem(true, "Section starting at character " + varStart + " ('{') must be closed");
        }
        LinkedHashMap<String, Variation> result = new LinkedHashMap<>();
        if (slots.size() == 1) {
            result.put(fixName(name), Variation.NONE);
            return result;
        }
        int[] counters = new int[slots.size()];
        long combinations = 1;
        for (Slot slot : slots) {
            if (slot.group() != null) {
                if (slot.group().keys.isEmpty()) {
                    return result;
                }
                combinations *= slot.group().keys.size();
                if (combinations > MAX_NAMES_PER_LINE) {
                    throw new TooManyNames();
                }
            }
        }
        while (true) {
            int count = slots.size();
            int incremented = 0;
            StringBuilder pattern = new StringBuilder();
            String id = null;
            int insertPoint = -1;
            String components = null;
            Map<String, String> states = new HashMap<>();
            for (int i = 0; i < count; i++) {
                Slot slot = slots.get(i);
                if (slot.group() != null) {
                    pattern.append(slot.group().keys.get(counters[i]));
                    Variation variation = slot.group().values.get(counters[i]);
                    if (variation.id() != null) {
                        id = variation.id();
                    }
                    if (variation.insertPoint() != -1) {
                        insertPoint = variation.insertPoint();
                    }
                    if (variation.components() != null) {
                        components = variation.components();
                    }
                    states.putAll(variation.states());
                    if (i == incremented) {
                        counters[i]++;
                        if (counters[i] == slot.group().keys.size()) {
                            counters[i] = 0;
                            incremented++;
                        }
                    }
                } else {
                    if (i == incremented) {
                        incremented++;
                    }
                    pattern.append(slot.text());
                }
            }
            result.put(fixName(pattern.toString()), new Variation(id, insertPoint, components, states));
            if (incremented == count) {
                break;
            }
        }
        return result;
    }

    /** Skript's {@code fixName}: trimmed, with runs of spaces made one, and no space before a {@code ¦}. */
    static String fixName(String name) {
        if (name.isEmpty()) {
            return "";
        }
        StringBuilder sb = new StringBuilder(name.length());
        int firstNonWhitespace = -1;
        int lastNonWhitespace = -1;
        int lastWhitespace = -1;
        int stripped = 0;
        for (int i = 0; i < name.length(); ++i) {
            char c = name.charAt(i);
            if (c > ' ') {
                int adjusted = i - stripped;
                if (firstNonWhitespace == -1) {
                    firstNonWhitespace = adjusted;
                }
                lastNonWhitespace = adjusted;
            } else {
                int oldLastWhitespace = lastWhitespace;
                lastWhitespace = i;
                if (oldLastWhitespace != -1 && oldLastWhitespace == i - 1
                        || i < name.length() - 1 && name.charAt(i + 1) == '¦') {
                    stripped++;
                    continue;
                }
            }
            sb.append(c);
        }
        return firstNonWhitespace == -1 ? "" : sb.substring(firstNonWhitespace, lastNonWhitespace + 1);
    }

    /** Skript's {@code getAliasPlural}: {@code rock¦s} is rock and rocks, {@code pane¦s of glass} pane and panes of glass. */
    static String[] plural(String name) {
        int marker = name.indexOf('¦');
        if (marker == -1) {
            String trimmed = name.strip();
            return new String[] {trimmed, trimmed};
        }
        int pluralEnd = -1;
        for (int i = marker; i < name.length(); i++) {
            int c = name.codePointAt(i);
            if (Character.isWhitespace(c)) {
                pluralEnd = i;
                break;
            }
            i += Character.charCount(c);
        }
        if (pluralEnd == -1) {
            String singular = name.substring(0, marker);
            String plural = singular + name.substring(marker + 1);
            return new String[] {singular.strip(), plural.strip()};
        }
        String base = name.substring(0, marker);
        return new String[] {(base + name.substring(pluralEnd)).strip(), (base + name.substring(marker + 1)).strip()};
    }

    /** Skript's {@code Noun.stripGender}: a name without its {@code @a}, {@code @an} gender. */
    private String stripGender(String name) {
        int at = name.lastIndexOf('@');
        if (at == -1) {
            return name;
        }
        String gender = name.substring(at + 1).strip();
        if (!Set.of("a", "an", "x", "-").contains(gender)) {
            problem(false, "Undefined gender '" + gender + "' at " + name);
        }
        return name.substring(0, at).strip();
    }

    // --- aliases ---

    /** Skript's {@code loadAlias}: a {@code name = items} line. */
    private void alias(String name, String data) {
        LinkedHashMap<String, Variation> named = new LinkedHashMap<>();
        try {
            for (String pattern : keyPattern(name)) {
                named.putAll(keyVariations(pattern));
                if (named.size() > MAX_NAMES_PER_LINE) {
                    throw new TooManyNames();
                }
            }
        } catch (TooManyNames e) {
            problem(true, "This alias stands for more than " + MAX_NAMES_PER_LINE + " names, so it isn't loaded");
            return;
        }
        // Items are split at commas outside of components.
        int start = 0;
        int indexStart = 0;
        while (start - 1 != data.length()) {
            int comma = indexOfOutsideGroup(data, indexStart);
            if (comma == -1) {
                if (indexStart == 0) {
                    singleAlias(named, data.strip());
                    break;
                }
                comma = data.length();
            }
            singleAlias(named, data.substring(start, comma).strip());
            start = comma + 1;
            indexStart = start;
        }
    }

    /** Skript's {@code StringUtils.indexOfOutsideGroup} for a comma outside braces. */
    private static int indexOfOutsideGroup(String string, int i) {
        int group = 0;
        for (; i < string.length(); i++) {
            char c = string.charAt(i);
            if (c == '\\') {
                i++;
            } else if (c == '{') {
                group++;
            } else if (c == '}') {
                group--;
            } else if (c == ',' && group == 0) {
                return i;
            }
        }
        return -1;
    }

    /** Skript's {@code loadSingleAlias}: one item, under every name. */
    private void singleAlias(LinkedHashMap<String, Variation> named, String item) {
        Variation base = variation(item);
        if (base == null) {
            return;
        }
        for (Map.Entry<String, Variation> entry : named.entrySet()) {
            Variation merged = base.merge(entry.getValue());
            String id = merged.id();
            if (id == null) {
                problem(false, "Alias has no Minecraft id or tags defined");
                continue;
            }
            id = id.toLowerCase(Locale.ENGLISH);
            String[] forms = plural(stripGender(entry.getKey()));
            add(forms[0], forms[1], id, merged.components(), new HashMap<>(merged.states()));
        }
    }

    /** Skript's {@code AliasesProvider.addAlias}. */
    private void add(String singular, String plural, String id, String components, Map<String, String> states) {
        AliasTable.Type ofId = aliases.get(id);
        boolean deduplicate = true;
        String deduplicateFlag = states.remove("deduplicate");
        if (deduplicateFlag != null) {
            deduplicate = !deduplicateFlag.equals("false");
        }
        List<AliasTable.Item> datas;
        if (ofId != null) {
            datas = List.copyOf(ofId.items);
        } else {
            String material = server.material(id);
            if (material == null) {
                problem(true, "Minecraft id " + id + " is not valid");
                return;
            }
            covered.add(material);
            String related = states.remove("relatedEntity");
            Made item = null;
            boolean changed = false;
            if (server.isItem(material)) {
                try {
                    item = server.item(material, components);
                    changed = components != null;
                } catch (IllegalArgumentException e) {
                    // Skript fails here and stops loading aliases.
                    problem(true, "Failed to apply component data to ItemStack: " + e.getMessage());
                    return;
                }
            }
            Made block = null;
            if (!states.isEmpty() || server.isBlock(material)) {
                StringBuilder combined = new StringBuilder("[");
                boolean first = true;
                for (Map.Entry<String, String> state : states.entrySet()) {
                    if (!first) {
                        combined.append(',');
                    }
                    first = false;
                    combined.append(state.getKey()).append('=').append(state.getValue());
                }
                combined.append(']');
                try {
                    block = server.blockData(material, combined.toString());
                } catch (IllegalArgumentException e) {
                    problem(true, "Parsing block state " + combined + " failed!");
                }
            }
            AliasTable.Item data = new AliasTable.Item(id, material, changed ? components : null,
                    item == null ? null : item.value(), item == null || !changed ? "" : item.key(),
                    block == null ? null : block.value(), block == null ? null : block.key(),
                    states.isEmpty() ? Map.of() : Map.copyOf(states), related);
            AliasTable.Names entry = names.computeIfAbsent(material, key -> new AliasTable.Names());
            if (deduplicate) {
                for (AliasTable.Named existing : entry.items) {
                    if (existing.item().sameAs(data)) {
                        data = existing.item();
                        break;
                    }
                }
            }
            datas = List.of(data);
        }
        if (ofId == null) {
            AliasTable.Item data = datas.getFirst();
            AliasTable.Names entry = names.computeIfAbsent(data.material(), key -> new AliasTable.Names());
            AliasTable.Named namedItem = new AliasTable.Named(data, singular, plural);
            if (data.isDefault()) {
                // As Skript, the last plain item's name wins.
                entry.defaultItem = namedItem;
            } else {
                entry.items.add(namedItem);
            }
        }
        String singularKey = singular.toLowerCase(Locale.ENGLISH);
        String pluralKey = plural.toLowerCase(Locale.ENGLISH);
        AliasTable.Type type = aliases.get(singularKey);
        if (type == null) {
            type = aliases.get(pluralKey);
        }
        if (type == null) {
            type = new AliasTable.Type(singular);
            aliases.put(singularKey, type);
            if (aliases.put(pluralKey, type) == null && !pluralKey.equals(singularKey)) {
                nameCount++;
            }
            nameCount++;
            type.items.addAll(datas);
        } else {
            next:
            for (AliasTable.Item data : datas) {
                for (AliasTable.Item existing : type.items) {
                    if (data == existing || data.sameAs(existing)) {
                        continue next;
                    }
                }
                type.items.add(data);
            }
        }
    }

    /**
     * Skript's {@code loadMissingAliases}, as far as the files' aliases go: an item no file named takes its id as a
     * name ({@code red dye}), and where a file already has that name, Skript adds the item to that alias. Those
     * items are added here; the names of the rest are NeoSkript's own (see {@link ItemType}).
     */
    AliasTable finish() {
        file = null;
        fileLines = null;
        Set<String> generated = new HashSet<>();
        Set<String> merged = new HashSet<>();
        for (String material : server.materials()) {
            if (covered.contains(material)) {
                continue;
            }
            int colon = material.indexOf(':');
            if (colon < 0 || !material.substring(0, colon).equals("minecraft")) {
                continue;
            }
            String singular = material.substring(colon + 1).replace('_', ' ');
            String plural = singular + "s";
            AliasTable.Type target = null;
            if (aliases.containsKey(singular)) {
                target = aliases.get(singular);
            } else if (!generated.contains(singular) && aliases.containsKey(plural)) {
                target = aliases.get(plural);
            } else if (!generated.contains(singular) && !generated.contains(plural)) {
                generated.add(singular);
                generated.add(plural);
                continue;
            }
            if (target == null) {
                continue;
            }
            Made block = null;
            if (server.isBlock(material)) {
                try {
                    block = server.blockData(material, "[]");
                } catch (IllegalArgumentException e) {
                    block = null;
                }
            }
            Made item = server.isItem(material) ? server.item(material, null) : null;
            AliasTable.Item data = new AliasTable.Item(material, material, null, item == null ? null : item.value(), "",
                    block == null ? null : block.value(), block == null ? null : block.key(), Map.of(), null);
            AliasTable.Names entry = names.computeIfAbsent(material, key -> new AliasTable.Names());
            AliasTable.Named namedItem = new AliasTable.Named(data, singular, plural);
            if (data.isDefault()) {
                entry.defaultItem = namedItem;
            } else {
                entry.items.add(namedItem);
            }
            boolean present = false;
            for (AliasTable.Item existing : target.items) {
                present |= existing.sameAs(data);
            }
            if (!present) {
                target.items.add(data);
            }
            merged.add(material);
        }
        return new AliasTable(aliases, names, covered, merged, nameCount);
    }

    // --- problems ---

    private void problem(boolean error, String message) {
        if (lineProblems.merge((error ? "E" : "W") + message, 1, Integer::sum) <= MAX_REPEATS) {
            report(new Problem(file == null ? "" : file.name(), line, error, message, listing() ? code(line) : ""));
        }
    }

    /** The current file's lines, split once per file: splitting for every problem made bad files quadratic. */
    private List<String> fileLines;

    /** The code on line {@code number} of the current file, or "" for none. */
    private String code(int number) {
        if (file == null) {
            return "";
        }
        if (fileLines == null) {
            fileLines = file.lines();
        }
        if (number < 1 || number > fileLines.size()) {
            return "";
        }
        return fileLines.get(number - 1).strip();
    }
}
