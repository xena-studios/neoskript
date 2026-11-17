/*
 * Copyright (c) 2026 Xena Studios
 * SPDX-License-Identifier: GPL-3.0-only
 */
import java.io.*;
import java.nio.file.*;
import java.util.*;
import java.util.regex.*;
import java.util.zip.*;
import org.objectweb.asm.*;
import org.objectweb.asm.tree.*;

/**
 * Writes neoskript-compat/src/stubs/skript-<version>.api: Skript's API (class headers, fields and method signatures, no
 * code) for the classes Skript addons use, from Skript's jar and the JSON of neoskript-cli addon for each addon, with
 * their supertypes and outer classes. Run with ASM on the class path:
 * {@code java -cp asm.jar:asm-tree.jar ExtractSkriptApi.java Skript.jar skript-2.17.0.api skbee.json ...}
 */
public class ExtractSkriptApi {
    static Map<String, ClassNode> all = new HashMap<>();

    public static void main(String[] args) throws Exception {
        try (ZipFile zip = new ZipFile(args[0])) {
            for (Enumeration<? extends ZipEntry> e = zip.entries(); e.hasMoreElements();) {
                ZipEntry entry = e.nextElement();
                if (!entry.getName().endsWith(".class")) continue;
                ClassNode node = new ClassNode();
                try (InputStream in = zip.getInputStream(entry)) {
                    new ClassReader(in).accept(node, ClassReader.SKIP_CODE);
                }
                all.put(node.name, node);
            }
        }
        Set<String> wanted = new TreeSet<>();
        Pattern key = Pattern.compile("\"((?:ch\\.njol|org\\.skriptlang)\\.[^\"]+)\"\\s*:\\s*\\{");
        for (int i = 2; i < args.length; i++) {
            Matcher m = key.matcher(Files.readString(Path.of(args[i])));
            while (m.find()) wanted.add(m.group(1).replace('.', '/'));
        }
        // The classes the addons' classes name in their signatures, so the stubs of what they use compile.
        for (String name : new ArrayList<>(wanted)) {
            ClassNode node = all.get(name);
            if (node == null) continue;
            for (InnerClassNode inner : node.innerClasses)
                if (name.equals(inner.outerName) && (inner.access & Opcodes.ACC_PUBLIC) != 0) wanted.add(inner.name);
            for (FieldNode f : node.fields)
                if ((f.access & (Opcodes.ACC_PUBLIC | Opcodes.ACC_PROTECTED)) != 0) referenced(f.desc, wanted);
            for (MethodNode m : node.methods)
                if ((m.access & (Opcodes.ACC_PUBLIC | Opcodes.ACC_PROTECTED)) != 0) referenced(m.desc, wanted);
        }
        Deque<String> todo = new ArrayDeque<>(wanted);
        Set<String> done = new TreeSet<>();
        while (!todo.isEmpty()) {
            String name = todo.pop();
            if (!done.add(name)) continue;
            ClassNode node = all.get(name);
            if (node == null) { System.err.println("missing in jar: " + name); continue; }
            if (node.superName != null && all.containsKey(node.superName)) todo.push(node.superName);
            for (String i : node.interfaces) if (all.containsKey(i)) todo.push(i);
            for (InnerClassNode inner : node.innerClasses)
                if (inner.name.equals(name) && inner.outerName != null && all.containsKey(inner.outerName)) todo.push(inner.outerName);
            if (node.nestHostClass != null && all.containsKey(node.nestHostClass)) todo.push(node.nestHostClass);
        }
        try (PrintWriter out = new PrintWriter(Files.newBufferedWriter(Path.of(args[1])))) {
            out.println("# Skript 2.17.0's API, as NeoSkriptCompat's stubs are generated from it: the classes Skript addons");
            out.println("# use (SkBee 3.26.0, skript-yaml 1.7.2), their supertypes and outer classes, with their public and");
            out.println("# protected members. Names and signatures only: no code. Regenerate with the extractor described in");
            out.println("# docs/SKRIPT-ADDONS.md.");
            for (String name : done) {
                ClassNode n = all.get(name);
                if (n == null) continue;
                out.println(String.join("\t", "class", Integer.toString(n.access), n.name, or(n.signature),
                        or(n.superName), n.interfaces.isEmpty() ? "-" : String.join(",", n.interfaces)));
                for (InnerClassNode inner : n.innerClasses) {
                    if (!done.contains(inner.name) && !inner.name.equals(name)) continue;
                    out.println(String.join("\t", "inner", inner.name, or(inner.outerName), or(inner.innerName),
                            Integer.toString(inner.access)));
                }
                for (FieldNode f : n.fields) {
                    if ((f.access & (Opcodes.ACC_PUBLIC | Opcodes.ACC_PROTECTED)) == 0 && (f.access & Opcodes.ACC_ENUM) == 0) continue;
                    out.println(String.join("\t", "field", Integer.toString(f.access), f.name, f.desc, or(f.signature)));
                }
                for (MethodNode m : n.methods) {
                    boolean visible = (m.access & (Opcodes.ACC_PUBLIC | Opcodes.ACC_PROTECTED)) != 0;
                    boolean abstractOne = (m.access & Opcodes.ACC_ABSTRACT) != 0;
                    if (!visible && !abstractOne) continue;
                    if ((m.access & Opcodes.ACC_SYNTHETIC) != 0 && !m.name.equals("<init>")) continue;
                    out.println(String.join("\t", "method", Integer.toString(m.access), m.name, m.desc, or(m.signature),
                            m.exceptions.isEmpty() ? "-" : String.join(",", m.exceptions)));
                }
            }
        }
        System.err.println(done.size() + " classes");
    }

    static void referenced(String descriptor, Set<String> wanted) {
        Matcher m = Pattern.compile("L([^;]+);").matcher(descriptor);
        while (m.find()) if (all.containsKey(m.group(1))) wanted.add(m.group(1));
    }

    static String or(String s) { return s == null ? "-" : s; }
}
