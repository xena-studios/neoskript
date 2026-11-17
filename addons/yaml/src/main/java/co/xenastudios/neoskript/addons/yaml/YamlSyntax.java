/*
 * Copyright (c) 2026 Xena Studios
 * SPDX-License-Identifier: GPL-3.0-only
 */
package co.xenastudios.neoskript.addons.yaml;

import co.xenastudios.neoskript.api.Values;
import co.xenastudios.neoskript.api.element.Changeable;
import co.xenastudios.neoskript.api.element.Condition;
import co.xenastudios.neoskript.api.element.ElementInfo;
import co.xenastudios.neoskript.api.element.Expression;
import co.xenastudios.neoskript.api.element.LoopValue;
import co.xenastudios.neoskript.api.element.SyntaxError;
import co.xenastudios.neoskript.api.element.TriggerContext;
import co.xenastudios.neoskript.api.registration.Parameter;
import co.xenastudios.neoskript.api.registration.Registrar;
import co.xenastudios.neoskript.api.registration.ThreadAffinity;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Supplier;

/**
 * skript-yaml 1.7.2's syntax, pattern for pattern, so its scripts load unchanged. Ids start with
 * {@code NeoSkriptYaml}. Every element only touches the addon's own files, which are safe to use from any thread, so
 * on Folia they run on whichever thread the trigger is on.
 */
final class YamlSyntax {

    private static final String PREFIX = "NeoSkriptYaml";
    /** What the addon's elements are, by id, for {@link #register}'s affinity. */
    private static final List<String> IDS = new ArrayList<>();

    private YamlSyntax() {}

    /** The four things {@code yaml ... %string% of %string%} can be. */
    enum State {
        VALUE, NODES, NODE_KEYS, LIST;

        static State of(int mark) {
            return switch (mark) {
                case 2 -> NODES;
                case 3 -> NODE_KEYS;
                case 4 -> LIST;
                default -> VALUE;
            };
        }
    }

    static void register(Registrar registrar, Supplier<YamlStore> stores, Values values) {
        Syntax syntax = new Syntax(registrar, stores, values);
        syntax.effects();
        syntax.expressions();
        syntax.conditions();
        syntax.function();
        for (String id : IDS) {
            registrar.affinity(id, ThreadAffinity.ANY);
        }
    }

    // ---------------------------------------------------------------- argument helpers

    private static Object[] all(Object argument) {
        if (argument == null) {
            return new Object[0];
        }
        return argument instanceof Object[] array ? array : new Object[] {argument};
    }

    private static String single(Object argument) {
        Object value = argument instanceof Object[] array ? (array.length == 0 ? null : array[0]) : argument;
        return value == null ? null : value.toString();
    }

    private static boolean has(int mark, int bit) {
        return (mark & bit) != 0;
    }

    /** Where a warning is from: the line's element, as written. */
    private static String where(ElementInfo info) {
        String text = info.text();
        return text == null || text.isBlank() ? "" : " ('" + text.strip() + "')";
    }

    private static final class Syntax {

        private final Registrar registrar;
        private final Supplier<YamlStore> stores;
        private final Values values;

        Syntax(Registrar registrar, Supplier<YamlStore> stores, Values values) {
            this.registrar = registrar;
            this.stores = stores;
            this.values = values;
        }

        private YamlStore store() {
            return stores.get();
        }

        private String id(String name) {
            String id = PREFIX + name;
            IDS.add(id);
            return id;
        }

        private Path path(String path, boolean nonRelative, String where) {
            try {
                return store().paths().resolve(path, nonRelative);
            } catch (ServerPaths.Refused refused) {
                store().warn(refused.getMessage() + where);
                return null;
            }
        }

        /** A folder named in a script, as skript-yaml's {@code filterDirectories}: from the server's folder, or absolute. */
        private Path folder(String name, String where) {
            try {
                Path given = Path.of(name);
                if (given.isAbsolute() && given.normalize().startsWith(store().paths().server())) {
                    return given.normalize();
                }
            } catch (RuntimeException notAPath) {
                // Read as relative below, which says what's wrong.
            }
            return path(name, false, where);
        }

        // ------------------------------------------------------------ effects

        void effects() {
            registrar.effect(id("EffLoad"), info -> {
                String where = where(info);
                boolean nonRelative = has(info.mark(), 1);
                return (context, arguments) -> {
                    if (info.pattern() == 1) {
                        String name = single(arguments[0]);
                        Path file = path(name, nonRelative, where);
                        if (file != null) {
                            store().load(single(arguments[1]), file, where);
                        }
                        return;
                    }
                    for (Object name : all(arguments[0])) {
                        Path file = path(name.toString(), nonRelative, where);
                        if (file != null) {
                            store().load(info.pattern() == 0 ? ServerPaths.id(file) : name.toString(), file, where);
                        }
                    }
                };
            }, "[re]load [(1¦non[(-| )]relative)] y[a]ml %strings%",
                    "[re]load [(1¦non[(-| )]relative)] y[a]ml %string% as %string%",
                    "[re]load [(1¦non[(-| )]relative)] y[a]ml %strings% using [the] [file] path[s] as [the] id[s]");

            registrar.effect(id("EffLoadDirectory"), info -> {
                String where = where(info);
                boolean nonRelative = has(info.mark(), 1);
                return (context, arguments) -> {
                    for (Object name : all(arguments[0])) {
                        Path folder = path(name.toString(), nonRelative, where);
                        List<Path> files = folder == null ? null : store().filesIn(folder, name.toString(), "Load", where);
                        if (files == null) {
                            // skript-yaml stops at the first folder that isn't one; the others still load here.
                            continue;
                        }
                        for (Path file : files) {
                            String id = info.pattern() == 1 ? ServerPaths.id(file)
                                    : ServerPaths.withSeparator(name.toString()) + file.getFileName();
                            store().load(id, file, where);
                        }
                    }
                };
            }, "[re]load all y[a]ml from [(1¦non[(-| )]relative)] director(y|ies) %strings%",
                    "[re]load all y[a]ml from [(1¦non[(-| )]relative)] director(y|ies) %strings% using [the] filename as [the] id");

            registrar.effect(id("EffUnload"), info -> {
                String where = where(info);
                return (context, arguments) -> {
                    for (Object name : all(arguments[0])) {
                        if (has(info.mark(), 1)) {
                            Path folder = path(name.toString(), false, where);
                            if (folder != null) {
                                store().unloadFolder(folder);
                            }
                        } else {
                            store().unload(name.toString());
                        }
                    }
                };
            }, "unload y[a]ml [(1¦director(y|ies))] %strings%");

            registrar.effect(id("EffSave"), info -> {
                String where = where(info);
                boolean elsewhere = info.pattern() == 1;
                boolean nonRelative = elsewhere && has(info.mark(), 1);
                // skript-yaml reads both marks of the second pattern as one number, so with both, neither works.
                boolean extraLines = !has(info.mark(), elsewhere ? 2 : 1);
                return (context, arguments) -> {
                    Object indent = arguments[elsewhere ? 2 : 1];
                    for (Object name : all(arguments[0])) {
                        YamlFile file = store().get(name.toString());
                        if (file == null) {
                            continue;
                        }
                        if (indent instanceof Number number) {
                            try {
                                file.indent(number.intValue());
                            } catch (IllegalArgumentException e) {
                                store().warn(e.getMessage() + where);
                            }
                        }
                        if (!elsewhere) {
                            if (file.fileDeleted()) {
                                store().warn("Cannot save yaml '" + name + "' because the file has been deleted but kept"
                                        + " in memory. Use 'save yaml \"" + name + "\" to \"new/path.yml\"' to save to a"
                                        + " new location" + where);
                                continue;
                            }
                            store().save(file, null, extraLines, name.toString(), where);
                            continue;
                        }
                        String target = single(arguments[1]);
                        if (target == null) {
                            store().warn("Target file path cannot be null" + where);
                            continue;
                        }
                        Path to = path(target, nonRelative, where);
                        if (to == null) {
                            continue;
                        }
                        boolean deleted = file.fileDeleted();
                        if (store().save(file, to, extraLines, name.toString(), where) && deleted) {
                            // A file deleted but kept loaded is saved where it now is from here on, as skript-yaml.
                            file.file(to);
                            store().warn("Successfully saved deleted yaml '" + name + "' to new location '" + target
                                    + "'" + where);
                        }
                    }
                };
            }, "save y[a]ml %strings% [with an indentation of %-number%] [(1¦[and] with(out| no) extra lines between nodes)]",
                    "save y[a]ml %strings% (to|as) [(1¦non[(-| )]relative)] [file] %string% [with an indentation of %-number%]"
                            + " [(2¦[and] with(out| no) extra lines between nodes)]");

            registrar.effect(id("EffDelete"), info -> {
                boolean keep = info.hasTag("keep");
                return (context, arguments) -> {
                    for (Object name : all(arguments[0])) {
                        YamlFile file = store().get(name.toString());
                        if (file == null) {
                            continue;
                        }
                        store().delete(file.file());
                        if (keep) {
                            file.markFileDeleted();
                        } else {
                            store().unload(name.toString());
                        }
                    }
                };
            }, "delete y[a]ml %strings% [keep:and keep loaded in memory]");

            // As skript-yaml's, the trigger waits while the files are deleted, here off the server's threads.
            registrar.pausingEffect(id("EffDeleteDirectory"), info -> {
                String where = where(info);
                boolean nonRelative = has(info.mark(), 1);
                return (context, arguments) -> java.util.concurrent.CompletableFuture.runAsync(() -> {
                    for (Object name : all(arguments[0])) {
                        Path folder = path(name.toString(), nonRelative, where);
                        List<Path> files = folder == null ? null : store().filesIn(folder, name.toString(), "Delete", where);
                        if (files == null) {
                            continue;
                        }
                        for (Path path : files) {
                            String id = info.pattern() == 1 ? ServerPaths.id(path)
                                    : ServerPaths.withSeparator(name.toString()) + path.getFileName();
                            YamlFile file = store().get(id);
                            if (file != null) {
                                store().delete(file.file());
                                store().unload(id);
                            }
                        }
                    }
                });
            }, "delete (all|any) [loaded] y[a]ml from [(1¦non[(-| )]relative)] director(y|ies) %strings%",
                    "delete (all|any) [loaded] y[a]ml from [(1¦non[(-| )]relative)] director(y|ies) %strings% using [the]"
                            + " filename as [the] id");

            registrar.effect(id("EffChangeId"), info -> {
                String where = where(info);
                boolean nonRelative = has(info.mark(), 1);
                return (context, arguments) -> {
                    String from = single(arguments[0]);
                    String to = single(arguments[1]);
                    if (info.pattern() == 0) {
                        if (from == null || to == null) {
                            store().warn("Changing the ID of a YAML file requires both the old and new ID to be a valid"
                                    + " string" + where);
                            return;
                        }
                        store().rename(from, to, where);
                        return;
                    }
                    if (from == null || to == null) {
                        store().warn("Reassigning the file path of a YAML file requires both the ID and new file path to"
                                + " be valid strings" + where);
                        return;
                    }
                    YamlFile file = store().require(from, where);
                    Path path = file == null ? null : path(to, nonRelative, where);
                    if (path == null) {
                        return;
                    }
                    try {
                        if (path.getParent() != null) {
                            Files.createDirectories(path.getParent());
                        }
                    } catch (java.io.IOException | RuntimeException e) {
                        store().warn("Failed to reassign file path for yaml '" + from + "': " + e.getMessage() + where);
                        return;
                    }
                    file.file(path);
                };
            }, "(change|rename|update) y[a]ml id [from] %string% to %string%",
                    "(change|rename|update) y[a]ml %string% file path to [(1¦non[(-| )]relative)] %string%");
        }

        // ------------------------------------------------------------ expressions

        void expressions() {
            String yaml = id("ExprYaml");
            registrar.expression(yaml, "object", info -> new YamlExpression(State.of(info.mark()),
                    info.hasTag("nochecks"), where(info)),
                    "[skript-]y[a]ml (1¦value|2¦(node|path) list|3¦(node|path)[s with] key[s]|4¦list) %string% (of|in|from)"
                            + " %string% [nochecks:without string checks]");
            loopValues(yaml);

            registrar.expression(id("ExprListValue"), "object", info -> new ListValue(info.hasTag("nochecks"), where(info)),
                    "(index|value) %number% (of|in|from) [skript-]y[a]ml list %string% (of|in|from) %string%"
                            + " [nochecks:without string checks]");

            registrar.expression(id("ExprComments"), "object", info -> new Comments(info.pattern() == 0,
                    has(info.mark(), 1), where(info)),
                    "[the] comment[s] (of|from) y[a]ml node[s] %strings% (of|in|from) %string% [(1¦with [an] extra line)]",
                    "[the] (comment[s] (at|on) [the] top of|header (of|from)) %string% [(1¦with [an] extra line)]");

            registrar.expression(id("ExprAllNodes"), "string", info -> (context, arguments) -> {
                String name = single(arguments[0]);
                YamlFile file = store().get(name);
                if (file == null) {
                    store().warn("No yaml file by the name '" + name + "' has been loaded");
                    return null;
                }
                return file.paths().toArray();
            }, "[all] [skript-]y[a]ml (node|path)[s] (of|in|from) %string%");

            registrar.expression(id("ExprAllLoaded"), "string", info -> {
                String where = where(info);
                return (context, arguments) -> switch (info.pattern()) {
                    case 0 -> arguments[0] == null ? nonEmpty(store().ids()) : loadedIn(all(arguments[0]), where);
                    case 1 -> {
                        Set<String> unloaded = new LinkedHashSet<>();
                        for (Object name : all(arguments[0])) {
                            Path folder = folder(name.toString(), where);
                            if (folder != null) {
                                unloaded.addAll(store().unloadedIn(folder));
                            }
                        }
                        yield nonEmpty(new ArrayList<>(unloaded));
                    }
                    default -> {
                        Set<String> folders = new LinkedHashSet<>();
                        for (YamlFile file : store().all().values()) {
                            Path parent = file.file().getParent();
                            folders.add(parent == null ? "" : parent.toString());
                        }
                        yield folders.toArray();
                    }
                };
            }, "[(the|all [(of the|the)])] [currently] loaded y[a]ml [files] [from (director(y|ies) %-strings%|all directories)]",
                    "[(the|all [(of the|the)])] [currently] unloaded y[a]ml [files] from director(y|ies) %strings%",
                    "[(the|all [(of the|the)])] [currently] loaded y[a]ml directories");
        }

        private static Object[] nonEmpty(List<String> ids) {
            return ids.isEmpty() ? null : ids.toArray();
        }

        private Object[] loadedIn(Object[] names, String where) {
            Set<Path> folders = new LinkedHashSet<>();
            for (Object name : names) {
                Path folder = folder(name.toString(), where);
                if (folder != null) {
                    folders.add(folder);
                }
            }
            List<String> ids = new ArrayList<>();
            store().all().forEach((id, file) -> {
                if (folders.contains(file.file().getParent()) && !ids.contains(id)) {
                    ids.add(id);
                }
            });
            // In the store's order, as skript-yaml.
            List<String> ordered = new ArrayList<>();
            for (String id : store().ids()) {
                if (ids.contains(id)) {
                    ordered.add(id);
                }
            }
            return nonEmpty(ordered);
        }

        /** skript-yaml's {@code loop-id}, {@code loop-val}, {@code loop-list}, {@code loop-node}, {@code loop-key}... */
        private void loopValues(String yaml) {
            for (String name : List.of("id", "val", "list", "node", "key", "subnodekey", "subnodekeys")) {
                registrar.loopValue(yaml, name, info -> {
                    State state = State.of(info.mark());
                    boolean checks = info.hasTag("nochecks");
                    if (state == State.VALUE) {
                        throw new SyntaxError("A 'loop-" + name + "' can only be used in a yaml expression loop ie."
                                + " 'loop yaml node keys \"node\" from \"config\"'");
                    }
                    if (state == State.LIST && (name.equals("node") || name.equals("key") || name.startsWith("subnodekey"))) {
                        throw new SyntaxError("There's no 'loop-" + name + "' in a yaml list");
                    }
                    return loopValue(name, state, checks);
                });
            }
        }

        private LoopValue loopValue(String name, State state, boolean checks) {
            return (context, arguments, current) -> {
                if (current == null) {
                    return null;
                }
                String path = single(arguments[0]);
                String id = single(arguments[1]);
                if (path == null) {
                    return null;
                }
                String node = state == State.NODE_KEYS ? (path.isEmpty() ? "" : path + ".") + current : current.toString();
                return switch (name) {
                    case "id" -> id;
                    case "val" -> state == State.LIST ? current : get(id, node, State.VALUE, checks, "");
                    case "list" -> get(id, node, State.LIST, checks, "");
                    case "node" -> node;
                    case "key" -> {
                        if (state == State.NODE_KEYS) {
                            yield current.toString();
                        }
                        // The key itself: skript-yaml gives it with the dot before it.
                        String text = current.toString();
                        yield text.substring(text.lastIndexOf('.') + 1);
                    }
                    default -> get(id, node, state, checks, "");
                };
            };
        }

        /** What {@code yaml <state> path of id} gives. */
        Object get(String id, String path, State state, boolean checks, String where) {
            YamlFile file = id == null || path == null ? null : store().require(id, where);
            if (file == null) {
                return null;
            }
            return switch (state) {
                case VALUE -> {
                    Object value = file.get(path);
                    if (!checks && value instanceof String text) {
                        value = YamlValues.colourCodes(text);
                    }
                    yield YamlValues.toScript(value);
                }
                case NODES -> {
                    List<String> nodes = file.nodes(path);
                    yield nodes == null ? null : nodes.toArray();
                }
                case NODE_KEYS -> {
                    List<String> keys = file.keys(path);
                    yield keys == null ? null : keys.toArray();
                }
                case LIST -> {
                    List<Object> items = file.list(path);
                    if (items == null) {
                        yield null;
                    }
                    List<Object> out = new ArrayList<>(items.size());
                    for (Object item : items) {
                        Object value = YamlValues.toScript(item);
                        if (value != null) {
                            out.add(value);
                        }
                    }
                    yield out.toArray();
                }
            };
        }

        /** {@code yaml value|node list|node keys|list "path" of "id"}, and changing it. */
        private final class YamlExpression implements Expression, Changeable {

            private final State state;
            private final boolean checks;
            private final String where;

            YamlExpression(State state, boolean checks, String where) {
                this.state = state;
                this.checks = checks;
                this.where = where;
            }

            @Override
            public Object get(TriggerContext context, Object[] arguments) {
                return Syntax.this.get(single(arguments[1]), single(arguments[0]), state, checks, where);
            }

            @Override
            public Class<?>[] acceptChange(Mode mode) {
                return switch (mode) {
                    case DELETE, RESET -> new Class<?>[0];
                    case SET -> state == State.VALUE || state == State.LIST ? new Class<?>[] {Object.class} : null;
                    case ADD, REMOVE -> state == State.NODE_KEYS || state == State.LIST ? new Class<?>[] {Object.class} : null;
                    default -> null;
                };
            }

            @Override
            public boolean takesOne(Mode mode) {
                return state == State.VALUE && mode == Mode.SET;
            }

            @Override
            public void change(TriggerContext context, Object[] arguments, Object[] delta, Mode mode) {
                String path = single(arguments[0]);
                String id = single(arguments[1]);
                YamlFile file = id == null || path == null ? null : store().require(id, where);
                if (file == null) {
                    return;
                }
                if (mode == Mode.DELETE || mode == Mode.RESET) {
                    String warning = file.remove(path);
                    if (warning != null) {
                        store().warn(warning + where);
                    }
                    return;
                }
                Object[] given = delta == null ? new Object[0] : delta;
                switch (state) {
                    case VALUE -> {
                        if (mode == Mode.SET && given.length > 0) {
                            file.set(path, YamlValues.fromScript(given[0], checks));
                        }
                    }
                    case NODE_KEYS -> {
                        // Every key given (skript-yaml only takes the first), and at the root without a leading dot.
                        for (Object key : given) {
                            String node = path.isEmpty() ? String.valueOf(key) : path + "." + key;
                            if (mode == Mode.ADD) {
                                file.set(node, null);
                            } else if (mode == Mode.REMOVE) {
                                String warning = file.remove(node);
                                if (warning != null) {
                                    store().warn(warning + where);
                                }
                            }
                        }
                    }
                    case LIST -> {
                        List<Object> items = file.list(path);
                        if (mode == Mode.REMOVE) {
                            if (items == null) {
                                store().warn("There is no list at path '" + path + "' in yaml '" + id + "'" + where);
                                return;
                            }
                            for (Object value : given) {
                                Object stored = YamlValues.fromScript(value, checks);
                                for (int i = 0; i < items.size(); i++) {
                                    if (YamlValues.same(items.get(i), stored)) {
                                        items.remove(i);
                                        break;
                                    }
                                }
                            }
                            file.set(path, items);
                            return;
                        }
                        List<Object> list = mode == Mode.ADD && items != null ? items : new ArrayList<>();
                        for (Object value : given) {
                            list.add(YamlValues.fromScript(value, checks));
                        }
                        file.set(path, list);
                    }
                    default -> {
                        // Nothing else changes.
                    }
                }
            }
        }

        /** {@code index 1 of yaml list "path" of "id"}, and changing it. */
        private final class ListValue implements Expression, Changeable {

            private final boolean checks;
            private final String where;

            ListValue(boolean checks, String where) {
                this.checks = checks;
                this.where = where;
            }

            /** The file and its list, or {@code null} with skript-yaml's warning. */
            private List<Object> items(YamlFile file, String id, String path, int index, Object[] delta) {
                List<Object> items = file.list(path);
                if (items == null && delta != null) {
                    // No list there yet: one is made from what's set, as skript-yaml.
                    List<Object> made = new ArrayList<>();
                    for (Object value : delta) {
                        made.add(YamlValues.fromScript(value, checks));
                    }
                    file.set(path, made);
                    items = file.list(path);
                }
                if (items == null) {
                    store().warn("The node '" + path + "' in yaml '" + id + "' is not a list" + where);
                    return null;
                }
                if (index < 1 || index > items.size()) {
                    store().warn("The index of node '" + path + "' in yaml '" + id + "' needs to be between 1 and "
                            + items.size() + where);
                    return null;
                }
                return items;
            }

            @Override
            public Object get(TriggerContext context, Object[] arguments) {
                if (!(arguments[0] instanceof Number number)) {
                    return null;
                }
                String path = single(arguments[1]);
                String id = single(arguments[2]);
                YamlFile file = id == null || path == null ? null : store().require(id, where);
                List<Object> items = file == null ? null : items(file, id, path, number.intValue(), null);
                if (items == null) {
                    return null;
                }
                Object value = items.get(number.intValue() - 1);
                if (!checks && value instanceof String text) {
                    value = YamlValues.colourCodes(text);
                }
                return YamlValues.toScript(value);
            }

            @Override
            public Class<?>[] acceptChange(Mode mode) {
                return switch (mode) {
                    case SET -> new Class<?>[] {Object.class};
                    case DELETE, RESET -> new Class<?>[0];
                    default -> null;
                };
            }

            @Override
            public boolean takesOne(Mode mode) {
                return mode == Mode.SET;
            }

            @Override
            public void change(TriggerContext context, Object[] arguments, Object[] delta, Mode mode) {
                if (!(arguments[0] instanceof Number number)) {
                    return;
                }
                int index = number.intValue();
                String path = single(arguments[1]);
                String id = single(arguments[2]);
                YamlFile file = id == null || path == null ? null : store().require(id, where);
                List<Object> items = file == null ? null : items(file, id, path, index, delta);
                if (items == null) {
                    return;
                }
                if (mode == Mode.SET && delta != null && delta.length > 0) {
                    items.set(index - 1, YamlValues.fromScript(delta[0], checks));
                } else if (mode == Mode.DELETE || mode == Mode.RESET) {
                    items.remove(index - 1);
                }
                file.set(path, items);
            }
        }

        /** The comments above root keys, or the header, and changing them. */
        private final class Comments implements Expression, Changeable {

            private final boolean comments;
            private final boolean extraLine;
            private final String where;

            Comments(boolean comments, boolean extraLine, String where) {
                this.comments = comments;
                this.extraLine = extraLine;
                this.where = where;
            }

            @Override
            public Object get(TriggerContext context, Object[] arguments) {
                String id = single(arguments[comments ? 1 : 0]);
                YamlFile file = store().require(id, where);
                if (file == null) {
                    return null;
                }
                String text;
                if (comments) {
                    String key = single(arguments[0]);
                    YamlFile.Comment comment = key == null ? null : file.comment(key);
                    if (comment == null) {
                        store().warn("No comment found at '" + key + "' in yaml " + file.file() + where);
                        return null;
                    }
                    text = comment.text();
                } else {
                    text = file.header();
                    if (text == null) {
                        store().warn("No header found in yaml '" + file.file() + "'" + where);
                        return null;
                    }
                }
                return text.split("\\r?\\n");
            }

            @Override
            public Class<?>[] acceptChange(Mode mode) {
                return switch (mode) {
                    case SET -> new Class<?>[] {Object.class};
                    case DELETE, RESET -> new Class<?>[0];
                    default -> null;
                };
            }

            @Override
            public void change(TriggerContext context, Object[] arguments, Object[] delta, Mode mode) {
                String id = single(arguments[comments ? 1 : 0]);
                YamlFile file = store().require(id, where);
                if (file == null) {
                    return;
                }
                String[] lines = delta == null ? new String[0] : new String[delta.length];
                for (int i = 0; i < lines.length; i++) {
                    lines[i] = delta[i] instanceof String text ? text : values.text(delta[i]);
                }
                if (!comments) {
                    if (mode == Mode.SET) {
                        file.header(extraLine, lines);
                    } else {
                        file.removeHeader();
                    }
                    return;
                }
                for (Object key : all(arguments[0])) {
                    String path = key.toString();
                    if (mode != Mode.SET) {
                        if (file.hasRootKey(path)) {
                            file.removeComment(path);
                        }
                    } else if (path.contains(".")) {
                        store().warn("Comments can only be added to root paths not '" + path + "' in '" + id + "'" + where);
                    } else if (!file.hasRootKey(path)) {
                        store().warn("'" + path + "' is not a valid path in '" + id + "'" + where);
                    } else {
                        file.comment(path, extraLine, lines);
                    }
                }
            }
        }

        // ------------------------------------------------------------ conditions

        void conditions() {
            registrar.condition(id("CondHasList"), info -> (context, arguments) -> {
                boolean negated = info.pattern() == 1;
                YamlFile file = store().get(single(arguments[1]));
                String path = single(arguments[0]);
                Object value = file == null || path == null ? null : file.raw(path);
                return value == null ? negated : value instanceof List<?> != negated;
            }, "[skript-]y[a]ml [(node|path)[s]] %string% (of|in|from) %string% has [a] list",
                    "[skript-]y[a]ml [(node|path)[s]] %string% (of|in|from) %string% does(n't| not) have [a] list");

            registrar.condition(id("CondHasValue"), info -> (context, arguments) -> {
                boolean negated = info.pattern() == 1;
                YamlFile file = store().get(single(arguments[1]));
                if (file == null) {
                    return negated;
                }
                // Every path given has to have one (skript-yaml fails with an error on more than one).
                for (Object path : all(arguments[0])) {
                    if (file.raw(path.toString()) == null) {
                        return negated;
                    }
                }
                return !negated;
            }, "[skript-]y[a]ml [(node|path)[s]] %strings% (of|in|from) %string% has [a] value[s]",
                    "[skript-]y[a]ml [(node|path)[s]] %strings% (of|in|from) %string% does(n't| not) have [a] value[s]");

            registrar.condition(id("CondFileExists"), info -> {
                String where = where(info);
                boolean nonRelative = has(info.mark(), 1);
                return (context, arguments) -> {
                    boolean negated = info.pattern() == 1;
                    String name = single(arguments[0]);
                    Path file = name == null ? null : path(name, nonRelative, where);
                    if (file == null) {
                        return negated;
                    }
                    // A save that's still being written counts: scripts see their saves at once.
                    store().await(file);
                    return Files.exists(file) != negated;
                };
            }, "[(1¦non[(-| )]relative)] y[a]ml file %string% exists",
                    "[(1¦non[(-| )]relative)] y[a]ml file %string% does(n't| not) exist");

            registrar.condition(id("CondIsEmpty"), info -> (context, arguments) -> {
                boolean negated = info.pattern() == 1;
                YamlFile file = store().get(single(arguments[0]));
                return file == null ? negated : file.isEmpty() != negated;
            }, "[skript-]y[a]ml %string% is empty", "[skript-]y[a]ml %string% is(n't| not) empty");

            registrar.condition(id("CondIsLoaded"), info -> (context, arguments) -> {
                boolean negated = info.pattern() == 1;
                return Condition.check(all(arguments[0]), info.and(0), id -> store().isLoaded(id.toString())) != negated;
            }, "y[a]ml[s] %strings% (is|are) loaded", "y[a]ml[s] %strings% ((are|is) not|(is|are)n[']t) loaded");

            registrar.condition(id("CondIsModified"), info -> (context, arguments) -> {
                boolean negated = info.pattern() == 1;
                YamlFile file = store().get(single(arguments[0]));
                return file == null ? negated : file.modified() != negated;
            }, "y[a]ml %string% is (modified|unsaved)", "y[a]ml %string% is not (modified|unsaved)");

            registrar.condition(id("CondNodeIsType"), info -> {
                String where = where(info);
                return (context, arguments) -> {
                    boolean negated = info.pattern() == 1;
                    String path = single(arguments[0]);
                    String id = single(arguments[1]);
                    if (path == null || id == null) {
                        return false;
                    }
                    YamlFile file = store().require(id, where);
                    if (file == null) {
                        return false;
                    }
                    Object value = file.raw(path);
                    boolean is = value != null && switch (info.mark()) {
                        case 1 -> value instanceof List;
                        case 2 -> value instanceof java.util.Map;
                        default -> !(value instanceof List) && !(value instanceof java.util.Map);
                    };
                    return is != negated;
                };
            }, "y[a]ml (node|path) %string% (of|in|from) %string% is [a] (1¦list|2¦node|3¦value)",
                    "y[a]ml (node|path) %string% (of|in|from) %string% is(n't| not) [a] (1¦list|2¦node|3¦value)");

            registrar.condition(id("CondPathExists"), info -> (context, arguments) -> {
                boolean negated = info.pattern() == 1;
                YamlFile file = store().get(single(arguments[1]));
                if (file == null) {
                    return false;
                }
                for (Object path : all(arguments[0])) {
                    if (!file.hasPath(path.toString())) {
                        return negated;
                    }
                }
                return !negated;
            }, "[skript-]y[a]ml [(node|path)[s]] %strings% (of|in|from) %string% exists",
                    "[skript-]y[a]ml [(node|path)[s]] %strings% (of|in|from) %string% does(n't| not) exist");
        }

        // ------------------------------------------------------------ function

        void function() {
            registrar.function("loadYaml", List.of(Parameter.of("name", "string"), Parameter.of("file", "string"),
                    Parameter.defaulting("isNonRelative", "boolean", () -> false)), "boolean", false, arguments -> {
                        String id = single(arguments[0]);
                        String name = single(arguments[1]);
                        boolean nonRelative = Boolean.TRUE.equals(arguments[2] instanceof Object[] array
                                ? (array.length == 0 ? null : array[0]) : arguments[2]);
                        Path file = name == null ? null : path(name, nonRelative, "");
                        return file != null && store().load(id, file, "");
                    });
        }
    }
}
