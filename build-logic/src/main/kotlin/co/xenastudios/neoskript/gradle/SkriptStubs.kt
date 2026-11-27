/*
 * Copyright (c) 2026 Xena Studios
 * SPDX-License-Identifier: GPL-3.0-only
 */
package co.xenastudios.neoskript.gradle

import org.gradle.api.DefaultTask
import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.tasks.CacheableTask
import org.gradle.api.tasks.InputFile
import org.gradle.api.tasks.InputFiles
import org.gradle.api.tasks.Optional
import org.gradle.api.tasks.InputDirectory
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.TaskAction
import org.objectweb.asm.ClassReader
import org.objectweb.asm.ClassWriter
import org.objectweb.asm.tree.ClassNode
import org.objectweb.asm.tree.FieldNode
import org.objectweb.asm.tree.MethodNode
import org.objectweb.asm.Opcodes
import org.objectweb.asm.Type
import java.io.File

/**
 * NeoSkriptCompat's stand-ins for Skript's classes that it doesn't implement yet, made from a listing of Skript's API
 * ([listing]: class headers, fields and method signatures, no code). Each class keeps Skript's name, modifiers,
 * generic signature, supertypes and members, so an addon's classes link and reflect as they do on Skript; each method
 * throws, saying that NeoSkriptCompat doesn't have it yet. Classes written by hand in [sources] (and the classes nested
 * in them) aren't made: the hand-written ones are used instead.
 */
@CacheableTask
abstract class GenerateSkriptStubs : DefaultTask() {

    @get:InputFile
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val listing: RegularFileProperty

    /** The hand-written source roots: a class with a source file here isn't made. */
    @get:InputFiles
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val sources: ConfigurableFileCollection

    /**
     * The hand-written classes, compiled: when set, this completes them instead, adding each member of Skript's they
     * don't declare (abstract where it's abstract, else throwing), so they have all of Skript's API.
     */
    @get:InputDirectory
    @get:Optional
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val compiled: DirectoryProperty

    @get:OutputDirectory
    abstract val output: DirectoryProperty

    private class Entry(val access: Int, val name: String, val signature: String?, val superName: String?,
            val interfaces: List<String>) {
        val inners = mutableListOf<List<String>>()
        val fields = mutableListOf<List<String>>()
        val methods = mutableListOf<List<String>>()
    }

    @TaskAction
    fun generate() {
        val out = output.get().asFile
        out.deleteRecursively()
        out.mkdirs()
        val handWritten = mutableSetOf<String>()
        for (root in sources.files) {
            root.walkTopDown().filter { it.isFile && it.name.endsWith(".java") }.forEach {
                handWritten += it.relativeTo(root).path.removeSuffix(".java").replace(File.separatorChar, '/')
            }
        }
        val entries = linkedMapOf<String, Entry>()
        var current: Entry? = null
        for (line in listing.get().asFile.readLines()) {
            if (line.isBlank() || line.startsWith("#")) continue
            val parts = line.split('\t')
            when (parts[0]) {
                "class" -> {
                    current = Entry(parts[1].toInt(), parts[2], parts[3].orNull(), parts[4].orNull(),
                            parts[5].orNull()?.split(',') ?: emptyList())
                    entries[current.name] = current
                }
                "inner" -> current!!.inners += parts.drop(1)
                "field" -> current!!.fields += parts.drop(1)
                "method" -> current!!.methods += parts.drop(1)
                else -> error("Unknown line in ${listing.get().asFile.name}: $line")
            }
        }
        val classes = compiled.orNull?.asFile
        for (entry in entries.values) {
            val outer = entry.name.substringBefore('$')
            val file = File(out, entry.name + ".class")
            if (classes != null) {
                val own = File(classes, entry.name + ".class")
                if (own.isFile) {
                    file.parentFile.mkdirs()
                    file.writeBytes(complete(own.readBytes(), entry, classes))
                }
                continue
            }
            if (outer in handWritten || entry.name in handWritten) continue
            file.parentFile.mkdirs()
            file.writeBytes(stub(entry))
        }
    }

    /** A hand-written class with the members of Skript's it doesn't declare added. */
    private fun complete(bytes: ByteArray, entry: Entry, classes: File): ByteArray {
        val node = ClassNode()
        ClassReader(bytes).accept(node, 0)
        val isInterface = node.access and Opcodes.ACC_INTERFACE != 0
        val unsupported = "NeoSkriptCompat doesn't have ${entry.name.replace('/', '.').replace('$', '.')}."
        for (field in entry.fields) {
            if (node.fields.none { it.name == field[1] }) {
                node.fields.add(FieldNode(field[0].toInt(), field[1], field[2], field[3].orNull(), null))
            }
        }
        for (method in entry.methods) {
            val name = method[1]
            val desc = method[2]
            if (node.methods.any { it.name == name && it.desc == desc }) continue
            // One a hand-written supertype has already is inherited: adding it here would hide that one.
            if (inherits(node, name, desc, classes)) continue
            // A class's constructors are its own: one Skript has that this doesn't isn't added.
            if (name == "<init>" || name == "<clinit>") continue
            var access = method[0].toInt()
            val abstractOne = access and Opcodes.ACC_ABSTRACT != 0
            if (abstractOne && node.access and Opcodes.ACC_ABSTRACT == 0 && !isInterface) {
                // A concrete hand-written class gets a throwing body for a method Skript has abstract.
                access = access and Opcodes.ACC_ABSTRACT.inv()
            }
            val exceptions = method[4].orNull()?.split(',')?.toTypedArray()
            val added = MethodNode(access, name, desc, method[3].orNull(), exceptions)
            if (access and Opcodes.ACC_ABSTRACT == 0) {
                added.visitCode()
                added.visitTypeInsn(Opcodes.NEW, "java/lang/UnsupportedOperationException")
                added.visitInsn(Opcodes.DUP)
                added.visitLdcInsn("$unsupported$name() yet: Skript addons that need it don't work on NeoSkript yet")
                added.visitMethodInsn(Opcodes.INVOKESPECIAL, "java/lang/UnsupportedOperationException", "<init>",
                        "(Ljava/lang/String;)V", false)
                added.visitInsn(Opcodes.ATHROW)
                added.visitMaxs(3, 0)
            }
            added.visitEnd()
            node.methods.add(added)
        }
        // Maxes are worked out again for the bodies added here: a method's locals hold at least its arguments.
        val writer = ClassWriter(ClassWriter.COMPUTE_MAXS)
        node.accept(writer)
        return writer.toByteArray()
    }

    /** Whether a hand-written supertype of [node] has a body for the method [name] [desc]. */
    private fun inherits(node: ClassNode, name: String, desc: String, classes: File): Boolean {
        val supers = ArrayDeque<String>()
        node.superName?.let { supers.add(it) }
        supers.addAll(node.interfaces)
        val seen = mutableSetOf<String>()
        while (supers.isNotEmpty()) {
            val type = supers.removeFirst()
            if (!seen.add(type)) continue
            val file = File(classes, "$type.class")
            if (!file.isFile) continue
            val parent = ClassNode()
            ClassReader(file.readBytes()).accept(parent, ClassReader.SKIP_CODE)
            if (parent.methods.any { it.name == name && it.desc == desc && it.access and Opcodes.ACC_ABSTRACT == 0
                        && it.access and Opcodes.ACC_STATIC == 0 }) {
                return true
            }
            parent.superName?.let { supers.add(it) }
            supers.addAll(parent.interfaces)
        }
        return false
    }

    private fun String.orNull(): String? = if (this == "-") null else this

    private fun stub(entry: Entry): ByteArray {
        val isEnum = entry.access and Opcodes.ACC_ENUM != 0
        val isInterface = entry.access and Opcodes.ACC_INTERFACE != 0
        // An enum's constants are made here, so it can't be abstract, nor can its methods be.
        val classAccess = if (isEnum) entry.access and Opcodes.ACC_ABSTRACT.inv() else entry.access
        val writer = ClassWriter(ClassWriter.COMPUTE_MAXS)
        writer.visit(Opcodes.V21, classAccess, entry.name, entry.signature, entry.superName,
                entry.interfaces.toTypedArray())
        for (inner in entry.inners) {
            writer.visitInnerClass(inner[0], inner[1].orNull(), inner[2].orNull(), inner[3].toInt())
        }
        val constants = mutableListOf<String>()
        for (field in entry.fields) {
            val access = field[0].toInt()
            writer.visitField(access, field[1], field[2], field[3].orNull(), null)?.visitEnd()
            if (access and Opcodes.ACC_ENUM != 0) constants += field[1]
        }
        val unsupported = "NeoSkriptCompat doesn't have ${entry.name.replace('/', '.').replace('$', '.')}."
        var hasEnumConstructor = false
        for (method in entry.methods) {
            var access = method[0].toInt()
            val name = method[1]
            val desc = method[2]
            if (isEnum) access = access and Opcodes.ACC_ABSTRACT.inv()
            if (isEnum && name == "<init>" && desc == "(Ljava/lang/String;I)V") hasEnumConstructor = true
            val exceptions = method[4].orNull()?.split(',')?.toTypedArray()
            val visitor = writer.visitMethod(access, name, desc, method[3].orNull(), exceptions)
            if (access and Opcodes.ACC_ABSTRACT != 0 || access and Opcodes.ACC_NATIVE != 0) {
                visitor.visitEnd()
                continue
            }
            visitor.visitCode()
            when {
                isEnum && name == "values" && desc == "()[L${entry.name};" -> {
                    pushInt(visitor, constants.size)
                    visitor.visitTypeInsn(Opcodes.ANEWARRAY, entry.name)
                    constants.forEachIndexed { index, constant ->
                        visitor.visitInsn(Opcodes.DUP)
                        pushInt(visitor, index)
                        visitor.visitFieldInsn(Opcodes.GETSTATIC, entry.name, constant, "L${entry.name};")
                        visitor.visitInsn(Opcodes.AASTORE)
                    }
                    visitor.visitInsn(Opcodes.ARETURN)
                }
                isEnum && name == "valueOf" && desc == "(Ljava/lang/String;)L${entry.name};" -> {
                    visitor.visitLdcInsn(Type.getObjectType(entry.name))
                    visitor.visitVarInsn(Opcodes.ALOAD, 0)
                    visitor.visitMethodInsn(Opcodes.INVOKESTATIC, "java/lang/Enum", "valueOf",
                            "(Ljava/lang/Class;Ljava/lang/String;)Ljava/lang/Enum;", false)
                    visitor.visitTypeInsn(Opcodes.CHECKCAST, entry.name)
                    visitor.visitInsn(Opcodes.ARETURN)
                }
                else -> {
                    val member = if (name == "<init>") "its constructor" else "$name()"
                    visitor.visitTypeInsn(Opcodes.NEW, "java/lang/UnsupportedOperationException")
                    visitor.visitInsn(Opcodes.DUP)
                    visitor.visitLdcInsn("$unsupported$member yet: Skript addons that need it don't work on NeoSkript yet")
                    visitor.visitMethodInsn(Opcodes.INVOKESPECIAL, "java/lang/UnsupportedOperationException", "<init>",
                            "(Ljava/lang/String;)V", false)
                    visitor.visitInsn(Opcodes.ATHROW)
                }
            }
            visitor.visitMaxs(0, 0)
            visitor.visitEnd()
        }
        if (isEnum) {
            if (!hasEnumConstructor) {
                val constructor = writer.visitMethod(Opcodes.ACC_PRIVATE, "<init>", "(Ljava/lang/String;I)V", null, null)
                constructor.visitCode()
                constructor.visitVarInsn(Opcodes.ALOAD, 0)
                constructor.visitVarInsn(Opcodes.ALOAD, 1)
                constructor.visitVarInsn(Opcodes.ILOAD, 2)
                constructor.visitMethodInsn(Opcodes.INVOKESPECIAL, "java/lang/Enum", "<init>", "(Ljava/lang/String;I)V", false)
                constructor.visitInsn(Opcodes.RETURN)
                constructor.visitMaxs(0, 0)
                constructor.visitEnd()
            }
            val init = writer.visitMethod(Opcodes.ACC_STATIC, "<clinit>", "()V", null, null)
            init.visitCode()
            constants.forEachIndexed { index, constant ->
                init.visitTypeInsn(Opcodes.NEW, entry.name)
                init.visitInsn(Opcodes.DUP)
                init.visitLdcInsn(constant)
                pushInt(init, index)
                init.visitMethodInsn(Opcodes.INVOKESPECIAL, entry.name, "<init>", "(Ljava/lang/String;I)V", false)
                init.visitFieldInsn(Opcodes.PUTSTATIC, entry.name, constant, "L${entry.name};")
            }
            init.visitInsn(Opcodes.RETURN)
            init.visitMaxs(0, 0)
            init.visitEnd()
        }
        if (isInterface) {
            // Nothing more: an interface's abstract methods stay abstract, its default and static ones throw.
        }
        writer.visitEnd()
        return writer.toByteArray()
    }

    private fun pushInt(visitor: org.objectweb.asm.MethodVisitor, value: Int) {
        if (value in -1..5) visitor.visitInsn(Opcodes.ICONST_0 + value)
        else if (value in Byte.MIN_VALUE..Byte.MAX_VALUE) visitor.visitIntInsn(Opcodes.BIPUSH, value)
        else visitor.visitIntInsn(Opcodes.SIPUSH, value)
    }
}
