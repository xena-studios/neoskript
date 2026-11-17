/*
 * Copyright (c) 2026 Xena Studios
 * SPDX-License-Identifier: GPL-3.0-only
 */
package co.xenastudios.neoskript.addons.yaml;

import co.xenastudios.neoskript.api.Values;
import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.StringWriter;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TimeZone;
import org.yaml.snakeyaml.DumperOptions;
import org.yaml.snakeyaml.LoaderOptions;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.error.YAMLException;
import org.yaml.snakeyaml.comments.CommentLine;
import org.yaml.snakeyaml.composer.Composer;
import org.yaml.snakeyaml.nodes.Node;
import org.yaml.snakeyaml.parser.ParserImpl;
import org.yaml.snakeyaml.reader.StreamReader;
import org.yaml.snakeyaml.reader.UnicodeReader;
import org.yaml.snakeyaml.resolver.Resolver;

/**
 * Reads and writes YAML files as skript-yaml 1.7.2 does, so a file written by one reads the same in the other and is
 * written back byte for byte: its header ({@code ## } lines), comments above root keys ({@code # } lines), one root
 * key at a time, and Skript's values with skript-yaml's tags ({@code !location}, {@code !skriptclass}...).
 *
 * <p>What it does differently, on purpose, is in the addon's docs: a file that can't be read isn't loaded (skript-yaml
 * loads it empty, and its next save wipes the file), a value that can't be read (a location in a world that isn't
 * loaded) is kept as it was written rather than lost, and anchors are expanded so changing one key never changes
 * another.
 */
final class YamlText {

    /** skript-yaml's line break: the platform's. */
    static final String LINE_BREAK = DumperOptions.LineBreak.getPlatformLineBreak().getString();
    private static final String COMMENT_PREFIX = "# ";
    private static final String HEADER_PREFIX = "## ";
    /** The most characters a file may have (and the most bytes it may be read from), against a file too big to hold. */
    static final int MAX_CHARACTERS = 32 * 1024 * 1024;
    /**
     * The most values (keys, list items, scalars) a file may hold, counted as it's read, before anything is built from
     * it, and again once aliases are followed. Reading one costs about 350 bytes until the file is built, so a million
     * is about 350 MB at worst.
     */
    static final int MAX_VALUES = 1_000_000;

    private YamlText() {}

    /** What a file holds once read. */
    record Read(Map<String, Object> root, String header, Map<String, YamlFile.Comment> comments) {}

    /** A file that can't be read: it isn't YAML, isn't a map of keys, or is too big. */
    static final class Unreadable extends Exception {
        Unreadable(String message) {
            super(message, null, false, false);
        }
    }

    // ---------------------------------------------------------------- reading

    /**
     * Reads a file's bytes: its values, header and comments.
     * @throws Unreadable if it can't be read as skript-yaml's files are, so nothing is loaded and the file is kept
     */
    static Read read(byte[] bytes, Values values) throws Unreadable {
        StringBuilder yaml = new StringBuilder();
        List<String> pending = new ArrayList<>();
        String[] header = {null};
        Map<String, YamlFile.Comment> comments = new HashMap<>();
        try (BufferedReader input = new BufferedReader(new UnicodeReader(new ByteArrayInputStream(bytes)), 65536)) {
            String line = input.readLine();
            append(yaml, line);
            while (line != null) {
                if (line.startsWith(HEADER_PREFIX)) {
                    while (line != null && line.startsWith(HEADER_PREFIX)) {
                        pending.add(line.replaceFirst(HEADER_PREFIX, ""));
                        line = input.readLine();
                        append(yaml, line);
                    }
                    if (line != null) {
                        header[0] = header(pending.toArray(new String[0]));
                        pending.clear();
                    }
                    // The line after the header is looked at too: skript-yaml skips it, so a comment right under
                    // the header is lost when the file is saved.
                    continue;
                }
                if (line.startsWith(COMMENT_PREFIX)) {
                    while (line != null && line.startsWith(COMMENT_PREFIX)) {
                        pending.add(line.replaceFirst(COMMENT_PREFIX, ""));
                        line = input.readLine();
                        append(yaml, line);
                    }
                    if (line != null) {
                        String key = line.split(":")[0];
                        if (!key.startsWith(" ")) {
                            // Root keys only, as skript-yaml.
                            String[] lines = pending.stream().map(text -> text.matches("^# ?") ? text : COMMENT_PREFIX + text)
                                    .toArray(String[]::new);
                            comments.put(key, new YamlFile.Comment(tabs(String.join(LINE_BREAK, lines)), false));
                        }
                    }
                    pending.clear();
                }
                line = input.readLine();
                append(yaml, line);
            }
        } catch (IOException e) {
            throw new Unreadable("it can't be read: " + e.getMessage());
        }
        Object document;
        try {
            document = compose(yaml.toString(), values);
        } catch (TooManyValues e) {
            throw new Unreadable("it holds more than " + MAX_VALUES + " values");
        } catch (YAMLException | IllegalArgumentException | ClassCastException | StackOverflowError e) {
            throw new Unreadable("it isn't valid YAML: " + describe(e));
        }
        if (document == null) {
            return new Read(new LinkedHashMap<>(), header[0], comments);
        }
        if (!(document instanceof Map<?, ?> map)) {
            throw new Unreadable("its top level is a " + (document instanceof List ? "list" : "value")
                    + ", not keys and values");
        }
        int[] budget = {MAX_VALUES};
        Map<String, Object> root;
        try {
            @SuppressWarnings("unchecked")
            Map<String, Object> copied = (Map<String, Object>) keysAsText(map, budget);
            root = copied;
        } catch (IllegalStateException e) {
            throw new Unreadable("a list or node in it holds itself");
        }
        if (budget[0] < 0) {
            throw new Unreadable("it holds more than " + MAX_VALUES + " values");
        }
        return new Read(root, header[0], comments);
    }

    private static void append(StringBuilder yaml, String line) throws Unreadable {
        // skript-yaml appends the text "null" after a last line that's a comment, which breaks the file.
        if (line != null) {
            yaml.append(line).append(LINE_BREAK);
            if (yaml.length() > MAX_CHARACTERS) {
                throw tooBig();
            }
        }
    }

    static Unreadable tooBig() {
        return new Unreadable("it's larger than " + (MAX_CHARACTERS / 1024 / 1024) + " MB");
    }

    private static String describe(Throwable e) {
        if (e instanceof StackOverflowError) {
            return "it's nested too deeply";
        }
        String message = e.getMessage() == null ? e.toString() : e.getMessage();
        return message.replace('\n', ' ').replaceAll(" {2,}", " ").strip();
    }

    /**
     * Keys as text, in nodes and nodes within nodes (not in lists), as skript-yaml reads them, and every list and
     * node copied, so one an alias shares can't be changed through another key. Counts values against the budget.
     * @throws IllegalStateException for a list or node that holds itself
     */
    private static Object keysAsText(Object value, int[] budget) {
        return copy(value, budget, true, java.util.Collections.newSetFromMap(new java.util.IdentityHashMap<>()));
    }

    private static Object copy(Object value, int[] budget, boolean keysAsText, java.util.Set<Object> within) {
        if (--budget[0] < 0) {
            return null;
        }
        if (!(value instanceof Map) && !(value instanceof List)) {
            return value;
        }
        if (!within.add(value)) {
            throw new IllegalStateException("holds itself");
        }
        try {
            if (value instanceof Map<?, ?> map) {
                Map<Object, Object> copy = new LinkedHashMap<>();
                for (Map.Entry<?, ?> entry : map.entrySet()) {
                    copy.put(keysAsText ? String.valueOf(entry.getKey()) : entry.getKey(),
                            copy(entry.getValue(), budget, keysAsText, within));
                }
                return copy;
            }
            List<?> list = (List<?>) value;
            List<Object> copy = new ArrayList<>(list.size());
            for (Object item : list) {
                // Keys of nodes in lists stay as they are, as skript-yaml leaves them.
                copy.add(copy(item, budget, false, within));
            }
            return copy;
        } finally {
            within.remove(value);
        }
    }

    /** A file with more values than {@link #MAX_VALUES}, found as it's read. */
    private static final class TooManyValues extends RuntimeException {
        TooManyValues() {
            super(null, null, false, false);
        }
    }

    /**
     * Reads a document as SnakeYAML's {@code Yaml.load} does, but counts each value as it's read and stops at
     * {@link #MAX_VALUES}, before the nodes for millions of values are held: a 30 MB list of zeros would otherwise take
     * gigabytes before anything could count it.
     */
    private static Object compose(String text, Values values) {
        LoaderOptions options = new LoaderOptions();
        options.setCodePointLimit(MAX_CHARACTERS);
        // skript-yaml allows any number of aliases; a few thousand covers real files, and the value budget stops the
        // rest once they're followed.
        options.setMaxAliasesForCollections(10_000);
        options.setAllowRecursiveKeys(false);
        options.setTagInspector(tag -> true);
        int[] count = {0};
        Composer composer = new Composer(new ParserImpl(new StreamReader(text), options), new Resolver(), options) {
            private void counted() {
                if (++count[0] > MAX_VALUES) {
                    throw new TooManyValues();
                }
            }

            @Override
            protected Node composeScalarNode(String anchor, List<CommentLine> blockComments) {
                counted();
                return super.composeScalarNode(anchor, blockComments);
            }

            @Override
            protected Node composeSequenceNode(String anchor) {
                counted();
                return super.composeSequenceNode(anchor);
            }

            @Override
            protected Node composeMappingNode(String anchor) {
                counted();
                return super.composeMappingNode(anchor);
            }
        };
        YamlConstructor constructor = new YamlConstructor(options, values);
        constructor.setComposer(composer);
        return constructor.getSingleData(Object.class);
    }

    // ---------------------------------------------------------------- writing

    /**
     * A file's contents, as skript-yaml saves them: the header, then each root key with its comment, and a blank line
     * between root keys unless {@code extraLines} is off.
     */
    static String write(YamlFile.Snapshot file, boolean extraLines, Values values) {
        Yaml yaml = dumper(file.indent());
        StringWriter out = new StringWriter();
        if (file.header() != null) {
            out.append(file.header()).append(LINE_BREAK);
            if (file.extraHeaderLine()) {
                out.append(LINE_BREAK);
            }
        }
        if (extraLines && file.header() != null) {
            out.append(LINE_BREAK);
        }
        boolean first = true;
        for (Map.Entry<String, Object> entry : file.root().entrySet()) {
            if (extraLines && !first) {
                out.append(LINE_BREAK);
            }
            first = false;
            YamlFile.Comment comment = file.comments().get(entry.getKey());
            if (comment != null) {
                if (comment.extraLine()) {
                    out.append(LINE_BREAK);
                }
                out.append(comment.text()).append(LINE_BREAK);
            }
            yaml.dump(Collections.singletonMap(entry.getKey(), YamlValues.toYaml(entry.getValue(), values)), out);
        }
        return out.toString();
    }

    private static Yaml dumper(int indent) {
        DumperOptions options = new DumperOptions();
        options.setIndent(indent);
        options.setDefaultFlowStyle(DumperOptions.FlowStyle.BLOCK);
        options.setTimeZone(TimeZone.getDefault());
        options.setSplitLines(false);
        YamlRepresenter representer = new YamlRepresenter();
        representer.setDefaultFlowStyle(DumperOptions.FlowStyle.BLOCK);
        LoaderOptions loading = new LoaderOptions();
        return new Yaml(new YamlConstructor(loading, null), representer, options, loading);
    }

    // ---------------------------------------------------------------- text

    /** Tabs as four spaces, as skript-yaml writes comments and headers. */
    static String tabs(String text) {
        return text.contains("\t") ? text.replace("\t", "    ") : text;
    }

    /** A header from its lines: each with {@code ## } in front, or {@code #} if it starts with one already. */
    static String header(String... lines) {
        if (lines == null || lines.length == 0) {
            return null;
        }
        StringBuilder header = new StringBuilder();
        for (String line : lines) {
            String text = line == null ? "" : line;
            if (!header.isEmpty()) {
                header.append(LINE_BREAK);
            }
            header.append(text.startsWith("#") ? "#" : HEADER_PREFIX).append(tabs(text));
        }
        return tabs(header.toString());
    }

    /** A date as skript-yaml writes {@code !skriptdate}: ISO 8601 in the server's time zone. */
    static String isoDate(long millis) {
        Calendar calendar = Calendar.getInstance(TimeZone.getDefault());
        calendar.setTime(new java.util.Date(millis));
        int years = calendar.get(Calendar.YEAR);
        int months = calendar.get(Calendar.MONTH) + 1;
        int days = calendar.get(Calendar.DAY_OF_MONTH);
        int hours = calendar.get(Calendar.HOUR_OF_DAY);
        int minutes = calendar.get(Calendar.MINUTE);
        int seconds = calendar.get(Calendar.SECOND);
        int ms = calendar.get(Calendar.MILLISECOND);
        StringBuilder text = new StringBuilder(String.valueOf(years));
        while (text.length() < 4) {
            text.insert(0, "0");
        }
        text.append('-').append(two(months)).append('-').append(two(days)).append('T').append(two(hours)).append(':')
                .append(two(minutes)).append(':').append(two(seconds));
        if (ms > 0) {
            text.append(ms < 10 ? ".00" : ms < 100 ? ".0" : ".").append(ms);
        }
        // As skript-yaml (and SnakeYAML) work out the offset: from the time of day's milliseconds field.
        int offset = calendar.getTimeZone().getOffset(calendar.get(Calendar.ERA), calendar.get(Calendar.YEAR),
                calendar.get(Calendar.MONTH), calendar.get(Calendar.DAY_OF_MONTH), calendar.get(Calendar.DAY_OF_WEEK),
                calendar.get(Calendar.MILLISECOND));
        if (offset == 0) {
            text.append('Z');
        } else {
            text.append(offset < 0 ? '-' : '+');
            int offsetMinutes = Math.abs(offset) / (60 * 1000);
            text.append(two(offsetMinutes / 60)).append(':').append(two(offsetMinutes % 60));
        }
        return text.toString();
    }

    private static String two(int number) {
        return number < 10 ? "0" + number : String.valueOf(number);
    }
}
