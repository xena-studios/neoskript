/*
 * Copyright (c) 2026 Xena Studios
 * SPDX-License-Identifier: GPL-3.0-only
 */
package co.xenastudios.neoskript.cli;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Enumeration;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.Handle;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.AnnotationNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.FieldInsnNode;
import org.objectweb.asm.tree.FieldNode;
import org.objectweb.asm.tree.InvokeDynamicInsnNode;
import org.objectweb.asm.tree.LdcInsnNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;
import org.objectweb.asm.tree.MultiANewArrayInsnNode;
import org.objectweb.asm.tree.TypeInsnNode;

/**
 * What a Skript addon's jar uses of Skript: every class of Skript's ({@code ch.njol.skript}, {@code ch.njol.util},
 * {@code ch.njol.yggdrasil}, {@code org.skriptlang.skript}) its classes extend, implement, call, read, create, cast
 * to, name in a signature or an annotation, with each member and which of the addon's classes use it. It reads the
 * jar's bytecode only; nothing in it runs.
 */
final class AddonScan {

    /** Skript's packages, as internal names. */
    static final List<String> SKRIPT = List.of("ch/njol/skript/", "ch/njol/util/", "ch/njol/yggdrasil/",
            "org/skriptlang/skript/");

    /** One class of Skript's, as an addon uses it. */
    static final class Used {
        final String name;
        /** The addon's classes that use it at all. */
        final Set<String> users = new TreeSet<>();
        /** How: extends, implements, new, call, field, cast, signature, annotation, method reference. */
        final Set<String> how = new TreeSet<>();
        /** Its members used ({@code name(descriptor)} or a field's name), with the addon's classes that use each. */
        final Map<String, Set<String>> members = new TreeMap<>();

        Used(String name) {
            this.name = name;
        }
    }

    /** What the scan found. */
    record Result(String jar, int classes, int classesUsingSkript, Map<String, Used> used, List<String> unreadable) {}

    private AddonScan() {}

    static boolean skript(String internalName) {
        if (internalName == null) {
            return false;
        }
        for (String prefix : SKRIPT) {
            if (internalName.startsWith(prefix)) {
                return true;
            }
        }
        return false;
    }

    /** Scans every class in a jar. */
    static Result scan(Path jar) throws IOException {
        Map<String, Used> used = new TreeMap<>();
        List<String> unreadable = new ArrayList<>();
        int classes = 0;
        Set<String> users = new TreeSet<>();
        try (ZipFile zip = new ZipFile(jar.toFile())) {
            Enumeration<? extends ZipEntry> entries = zip.entries();
            while (entries.hasMoreElements()) {
                ZipEntry entry = entries.nextElement();
                if (entry.isDirectory() || !entry.getName().endsWith(".class") || entry.getName().startsWith("META-INF/")) {
                    continue;
                }
                ClassNode node = new ClassNode();
                try (InputStream in = zip.getInputStream(entry)) {
                    new ClassReader(in).accept(node, ClassReader.SKIP_FRAMES);
                } catch (IOException | RuntimeException e) {
                    unreadable.add(entry.getName() + ": " + e);
                    continue;
                }
                if (skript(node.name)) {
                    // A copy of Skript's own classes in the jar isn't the addon's use of them.
                    continue;
                }
                classes++;
                int before = total(used);
                scanClass(node, used);
                if (total(used) != before) {
                    users.add(node.name);
                }
            }
        }
        return new Result(jar.getFileName().toString(), classes, users.size(), used, unreadable);
    }

    private static int total(Map<String, Used> used) {
        int total = 0;
        for (Used u : used.values()) {
            total += u.users.size() + u.how.size();
            for (Set<String> users : u.members.values()) {
                total += users.size();
            }
        }
        return total;
    }

    private static Used use(Map<String, Used> used, String type, String user, String how) {
        Used u = used.computeIfAbsent(type, Used::new);
        u.users.add(user);
        u.how.add(how);
        return u;
    }

    private static void member(Map<String, Used> used, String owner, String member, String user, String how) {
        use(used, owner, user, how).members.computeIfAbsent(member, m -> new TreeSet<>()).add(user);
    }

    /** Every Skript class a descriptor or signature names. */
    private static void types(Map<String, Used> used, String descriptor, String user, String how) {
        if (descriptor == null) {
            return;
        }
        int i = 0;
        while ((i = descriptor.indexOf('L', i)) >= 0) {
            int end = descriptor.indexOf(';', i);
            int generic = descriptor.indexOf('<', i);
            if (end < 0) {
                break;
            }
            if (generic >= 0 && generic < end) {
                end = generic;
            }
            String name = descriptor.substring(i + 1, end);
            if (skript(name)) {
                use(used, name, user, how);
            }
            i = end;
        }
    }

    private static void annotations(Map<String, Used> used, List<AnnotationNode> annotations, String user) {
        if (annotations == null) {
            return;
        }
        for (AnnotationNode annotation : annotations) {
            String name = Type.getType(annotation.desc).getInternalName();
            if (skript(name)) {
                use(used, name, user, "annotation");
            }
        }
    }

    private static void scanClass(ClassNode node, Map<String, Used> used) {
        String user = node.name;
        if (skript(node.superName)) {
            use(used, node.superName, user, "extends");
        }
        for (String type : node.interfaces) {
            if (skript(type)) {
                use(used, type, user, "implements");
            }
        }
        types(used, node.signature, user, "signature");
        annotations(used, node.visibleAnnotations, user);
        annotations(used, node.invisibleAnnotations, user);
        for (FieldNode field : node.fields) {
            types(used, field.signature != null ? field.signature : field.desc, user, "signature");
            annotations(used, field.visibleAnnotations, user);
        }
        for (MethodNode method : node.methods) {
            types(used, method.signature != null ? method.signature : method.desc, user, "signature");
            annotations(used, method.visibleAnnotations, user);
            if (skript(node.superName) && (method.access & Opcodes.ACC_STATIC) == 0 && !method.name.startsWith("<")) {
                // An override of one of Skript's methods: what Skript calls on the addon's element.
                member(used, node.superName, "overrides " + method.name + method.desc, user, "extends");
            }
            for (AbstractInsnNode insn : method.instructions) {
                switch (insn) {
                    case MethodInsnNode call when skript(call.owner) ->
                            member(used, call.owner, call.name + call.desc, user, "call");
                    case FieldInsnNode field when skript(field.owner) ->
                            member(used, field.owner, field.name + ":" + field.desc, user, "field");
                    case TypeInsnNode type -> {
                        String name = type.desc.startsWith("[") ? Type.getType(type.desc).getElementType().getInternalName()
                                : type.desc;
                        if (skript(name)) {
                            use(used, name, user, type.getOpcode() == Opcodes.NEW ? "new" : "cast");
                        }
                    }
                    case MultiANewArrayInsnNode array -> types(used, array.desc, user, "new");
                    case LdcInsnNode ldc when ldc.cst instanceof Type type && type.getSort() == Type.OBJECT
                            && skript(type.getInternalName()) -> use(used, type.getInternalName(), user, "class literal");
                    case InvokeDynamicInsnNode indy -> {
                        for (Object argument : indy.bsmArgs) {
                            if (argument instanceof Handle handle && skript(handle.getOwner())) {
                                member(used, handle.getOwner(), handle.getName() + handle.getDesc(), user,
                                        "method reference");
                            }
                        }
                        types(used, indy.desc, user, "signature");
                    }
                    default -> {
                    }
                }
            }
        }
    }

    /** The scan as text: classes most used first, each with how and its members. */
    static String report(Result result, boolean members) {
        StringBuilder out = new StringBuilder();
        out.append(result.jar()).append(": ").append(result.classesUsingSkript()).append(" of ").append(result.classes())
                .append(" classes use ").append(result.used().size()).append(" of Skript's classes\n");
        List<Used> ranked = new ArrayList<>(result.used().values());
        ranked.sort(Comparator.comparingInt((Used u) -> -u.users.size()).thenComparing(u -> u.name));
        for (Used u : ranked) {
            out.append(String.format("%5d  %s  [%s]%n", u.users.size(), u.name.replace('/', '.'), String.join(", ", u.how)));
            if (members) {
                u.members.entrySet().stream()
                        .sorted(Comparator.comparingInt((Map.Entry<String, Set<String>> e) -> -e.getValue().size())
                                .thenComparing(Map.Entry::getKey))
                        .forEach(e -> out.append(String.format("%11d  %s%n", e.getValue().size(), e.getKey())));
            }
        }
        for (String problem : result.unreadable()) {
            out.append("unreadable: ").append(problem).append('\n');
        }
        return out.toString();
    }

    /** The scan as JSON, for tools: each class with how it's used, its users, and its members with theirs. */
    static com.google.gson.JsonObject json(Result result) {
        com.google.gson.JsonObject root = new com.google.gson.JsonObject();
        root.addProperty("jar", result.jar());
        root.addProperty("classes", result.classes());
        root.addProperty("classesUsingSkript", result.classesUsingSkript());
        com.google.gson.JsonObject used = new com.google.gson.JsonObject();
        for (Used u : result.used().values()) {
            com.google.gson.JsonObject entry = new com.google.gson.JsonObject();
            entry.add("how", strings(u.how));
            entry.add("users", strings(u.users));
            com.google.gson.JsonObject membersJson = new com.google.gson.JsonObject();
            u.members.forEach((member, users) -> membersJson.add(member, strings(users)));
            entry.add("members", membersJson);
            used.add(u.name.replace('/', '.'), entry);
        }
        root.add("used", used);
        root.add("unreadable", strings(result.unreadable()));
        return root;
    }

    private static com.google.gson.JsonArray strings(java.util.Collection<String> values) {
        com.google.gson.JsonArray array = new com.google.gson.JsonArray();
        values.forEach(array::add);
        return array;
    }
}
