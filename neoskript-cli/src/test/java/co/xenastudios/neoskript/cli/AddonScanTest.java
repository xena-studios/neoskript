/*
 * Copyright (c) 2026 Xena Studios
 * SPDX-License-Identifier: GPL-3.0-only
 */
package co.xenastudios.neoskript.cli;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;

class AddonScanTest {

    @TempDir
    Path dir;

    /** An addon's expression: extends SimpleExpression, overrides init, calls Skript.error, reads Kleenean.TRUE. */
    private static byte[] expression() {
        ClassWriter writer = new ClassWriter(0);
        writer.visit(Opcodes.V21, Opcodes.ACC_PUBLIC, "my/addon/ExprThing", null, "ch/njol/skript/lang/util/SimpleExpression",
                null);
        MethodVisitor init = writer.visitMethod(Opcodes.ACC_PUBLIC, "init",
                "([Lch/njol/skript/lang/Expression;ILch/njol/util/Kleenean;Lch/njol/skript/lang/SkriptParser$ParseResult;)Z",
                null, null);
        init.visitCode();
        init.visitLdcInsn("bad");
        init.visitMethodInsn(Opcodes.INVOKESTATIC, "ch/njol/skript/Skript", "error", "(Ljava/lang/String;)V", false);
        init.visitFieldInsn(Opcodes.GETSTATIC, "ch/njol/util/Kleenean", "TRUE", "Lch/njol/util/Kleenean;");
        init.visitInsn(Opcodes.POP);
        init.visitTypeInsn(Opcodes.NEW, "ch/njol/skript/util/Timespan");
        init.visitInsn(Opcodes.POP);
        init.visitInsn(Opcodes.ICONST_1);
        init.visitInsn(Opcodes.IRETURN);
        init.visitMaxs(2, 5);
        init.visitEnd();
        writer.visitEnd();
        return writer.toByteArray();
    }

    /** A class of the addon's that doesn't touch Skript. */
    private static byte[] plain() {
        ClassWriter writer = new ClassWriter(0);
        writer.visit(Opcodes.V21, Opcodes.ACC_PUBLIC, "my/addon/Util", null, "java/lang/Object", null);
        writer.visitEnd();
        return writer.toByteArray();
    }

    private Path jar() throws IOException {
        Path jar = dir.resolve("addon.jar");
        try (OutputStream out = Files.newOutputStream(jar); ZipOutputStream zip = new ZipOutputStream(out)) {
            zip.putNextEntry(new ZipEntry("my/addon/ExprThing.class"));
            zip.write(expression());
            zip.putNextEntry(new ZipEntry("my/addon/Util.class"));
            zip.write(plain());
            zip.putNextEntry(new ZipEntry("plugin.yml"));
            zip.write("name: Addon\n".getBytes());
            zip.putNextEntry(new ZipEntry("broken/Nope.class"));
            zip.write(new byte[] {1, 2, 3});
        }
        return jar;
    }

    @Test
    void listsWhatAnAddonUsesOfSkript() throws IOException {
        AddonScan.Result result = AddonScan.scan(jar());
        assertEquals(2, result.classes());
        assertEquals(1, result.classesUsingSkript());
        AddonScan.Used base = result.used().get("ch/njol/skript/lang/util/SimpleExpression");
        assertTrue(base.how.contains("extends"));
        assertTrue(base.members.containsKey("overrides init([Lch/njol/skript/lang/Expression;ILch/njol/util/Kleenean;"
                + "Lch/njol/skript/lang/SkriptParser$ParseResult;)Z"), base.members.toString());
        assertEquals(Set.of("my/addon/ExprThing"), result.used().get("ch/njol/skript/Skript").members
                .get("error(Ljava/lang/String;)V"));
        assertTrue(result.used().get("ch/njol/util/Kleenean").members.containsKey("TRUE:Lch/njol/util/Kleenean;"));
        assertTrue(result.used().get("ch/njol/skript/util/Timespan").how.contains("new"));
        assertTrue(result.used().get("ch/njol/skript/lang/SkriptParser$ParseResult").how.contains("signature"));
        assertFalse(result.used().containsKey("java/lang/Object"));
        assertEquals(1, result.unreadable().size());
        String report = AddonScan.report(result, true);
        assertTrue(report.startsWith("addon.jar: 1 of 2 classes use "), report);
        assertTrue(report.contains("ch.njol.skript.Skript  [call]"), report);
    }

    @Test
    void theCommandPrintsTheReportAndWritesJson() throws IOException {
        Path json = dir.resolve("scan.json");
        var out = new java.io.ByteArrayOutputStream();
        var err = new java.io.ByteArrayOutputStream();
        int code = NeoSkriptCli.run(new String[] {"addon", jar().toString(), "--members", "--json", json.toString()},
                new java.io.PrintStream(out), new java.io.PrintStream(err));
        assertEquals(0, code, err.toString());
        assertTrue(out.toString().contains("error(Ljava/lang/String;)V"), out.toString());
        assertTrue(Files.readString(json).contains("\"ch.njol.skript.Skript\""));
        assertEquals(2, NeoSkriptCli.run(new String[] {"addon", dir.resolve("missing.jar").toString()},
                new java.io.PrintStream(out), new java.io.PrintStream(err)));
    }
}
