/*
 * Copyright (c) 2026 Xena Studios
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package co.xenastudios.neoskript.paper;

import co.xenastudios.neoskript.api.registration.Registrar;
import co.xenastudios.neoskript.frontend.source.Diagnostics;
import co.xenastudios.neoskript.frontend.source.SourceFile;
import co.xenastudios.neoskript.frontend.structure.Node;
import co.xenastudios.neoskript.frontend.structure.StructureParser;
import co.xenastudios.neoskript.runtime.Parsers;
import co.xenastudios.neoskript.runtime.ScriptCatalog;
import co.xenastudios.neoskript.runtime.ScriptRef;
import co.xenastudios.neoskript.runtime.Values;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/**
 * Skript's config nodes: {@code the skript config} (Skript's own defaults, as NeoSkript behaves) and scripts
 * read as nodes, with {@code node "x" of}, {@code nodes of} and {@code text value at "x" in}.
 */
final class Configs {

    private Configs() {}

    /**
     * A node: a section ({@code trigger:}) with nodes under it, an entry ({@code language: english}) with a
     * value, or a plain line of code.
     */
    record ConfigNode(String key, String value, List<ConfigNode> children, boolean section, String path) {
        /** The node at {@code path} under this one: {@code disable hooks.vault}. */
        ConfigNode at(String path) {
            ConfigNode node = this;
            for (String part : path.split("\\.")) {
                ConfigNode found = null;
                for (ConfigNode child : node.children()) {
                    if (child.key().equalsIgnoreCase(part.strip())) {
                        found = child;
                        break;
                    }
                }
                if (found == null) {
                    return null;
                }
                node = found;
            }
            return node;
        }

        /** As Skript writes a node: its path from the top, {@code disable hooks.vault}. */
        @Override
        public String toString() {
            return path;
        }
    }

    private static volatile ConfigNode skriptConfig;

    static void register(Registrar registrar) {
        registrar.type("node", "node", ConfigNode.class);
        registrar.text(ConfigNode.class, ConfigNode::path);
        // A script is its file's nodes.
        registrar.converter(ScriptRef.class, ConfigNode.class, Configs::ofScript);
        registrar.expression("ExprConfig", "node", info -> (context, args) -> skriptConfig(), "[the] [skript] config");
        registrar.expression("ExprNode", "node", info -> {
            int pattern = info.pattern();
            return (context, args) -> {
                if (pattern <= 1) {
                    Object path = Values.single(args[pattern == 0 ? 0 : 1]);
                    Object owner = node(Values.single(args[pattern == 0 ? 1 : 0]));
                    return owner instanceof ConfigNode node && path != null ? node.at(Values.text(path)) : null;
                }
                List<Object> nodes = new ArrayList<>();
                for (Object value : Values.array(args[0])) {
                    if (node(value) instanceof ConfigNode node) {
                        nodes.addAll(node.children());
                    }
                }
                return nodes.toArray();
            };
        }, "[the] node %string% (of|in) %node/script%", "%node/script%'[s] node %string%",
                "[the] nodes (of|in) %nodes/scripts%", "%nodes/scripts%'[s] nodes");
        // 'the text value at "language" in the skript config': an entry's value, read as the type.
        registrar.typedExpression("ExprSubnodeValue", "object", info -> {
            co.xenastudios.neoskript.api.element.ElementInfo.TypeArgument type = info.types().getFirst();
            return (context, args) -> {
                Object owner = node(Values.single(args[1]));
                Object path = Values.single(args[0]);
                ConfigNode node = owner instanceof ConfigNode n && path != null ? n.at(Values.text(path)) : null;
                return node == null ? null : valueAs(node, type);
            };
        }, "[the] <.+> value [at] %string% (from|in) %node/script%");
        registrar.typedExpression("PropExprValueOf", "object", info -> {
            co.xenastudios.neoskript.api.element.ElementInfo.TypeArgument type = info.types().isEmpty() ? null
                    : info.types().getFirst();
            return (context, args) -> {
                List<Object> values = new ArrayList<>();
                for (Object value : Values.array(args[0])) {
                    if (value instanceof ConfigNode node) {
                        Object converted = valueAs(node, type);
                        if (converted != null) {
                            values.add(converted);
                        }
                    }
                }
                return values.size() == 1 ? values.getFirst() : values.toArray();
            };
        }, "[the] [<.+>] value of %nodes%", "%nodes%'[s] [<.+>] value");
    }

    /** An entry's value as {@code type}: text as written, else read as that type. */
    private static Object valueAs(ConfigNode node, co.xenastudios.neoskript.api.element.ElementInfo.TypeArgument type) {
        if (node.value() == null) {
            return null;
        }
        if (type == null || type.id().equals("string") || type.id().equals("object")) {
            return node.value();
        }
        return Parsers.parse(type.id(), node.value());
    }

    private static Object node(Object value) {
        return value instanceof ScriptRef script ? ofScript(script) : value;
    }

    /** The skript config: Skript's {@code config.sk} as it ships, which NeoSkript's behaviour matches. */
    static ConfigNode skriptConfig() {
        ConfigNode config = skriptConfig;
        if (config == null) {
            try (InputStream in = Configs.class.getResourceAsStream("/skript-config.sk")) {
                String text = in == null ? "" : new String(in.readAllBytes(), StandardCharsets.UTF_8);
                config = parse("config.sk", text, true);
            } catch (IOException e) {
                config = new ConfigNode("config", null, List.of(), true, "");
            }
            skriptConfig = config;
        }
        return config;
    }

    /** A script's nodes, read from its file now. */
    static ConfigNode ofScript(ScriptRef script) {
        String source = ScriptCatalog.get().source(script);
        return source == null ? null : parse(script.name(), source, false);
    }

    /** Nodes from {@code text}; with {@code entries}, {@code key: value} lines are entries, as in config files. */
    private static ConfigNode parse(String name, String text, boolean entries) {
        SourceFile file = new SourceFile(name, text);
        List<Node> nodes = StructureParser.parse(file, new Diagnostics(file));
        return new ConfigNode(name, null, nodes(nodes, entries, ""), true, "");
    }

    private static List<ConfigNode> nodes(List<Node> nodes, boolean entries, String parent) {
        List<ConfigNode> converted = new ArrayList<>();
        for (Node node : nodes) {
            switch (node) {
                case Node.Section section -> {
                    String key = section.line().key().strip();
                    String path = parent.isEmpty() ? key : parent + "." + key;
                    converted.add(new ConfigNode(key, null, nodes(section.children(), entries, path), true, path));
                }
                case Node.Simple simple -> {
                    String line = simple.line().text().strip();
                    int separator = entries ? line.indexOf(": ") : -1;
                    String key = separator > 0 ? line.substring(0, separator).strip() : line;
                    String path = parent.isEmpty() ? key : parent + "." + key;
                    converted.add(new ConfigNode(key, separator > 0 ? line.substring(separator + 2).strip() : null,
                            List.of(), false, path));
                }
            }
        }
        return converted;
    }
}
