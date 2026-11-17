package co.xenastudios.neoskript.gradle

import org.gradle.api.DefaultTask
import org.gradle.api.GradleException
import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.tasks.CacheableTask
import org.gradle.api.tasks.InputFile
import org.gradle.api.tasks.InputFiles
import org.gradle.api.tasks.OutputFile
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.TaskAction
import org.objectweb.asm.ClassReader
import org.objectweb.asm.Opcodes
import org.objectweb.asm.Type
import org.objectweb.asm.tree.AnnotationNode
import org.objectweb.asm.tree.ClassNode
import java.io.File

/**
 * The addon API's public signatures, one line per class and member, as written to `api/<module>.api`: the erased
 * descriptor in Java's words, then the generic signature where there is one, so a change to either shows. What's
 * marked `@Internal` is left out, and what's marked `@Experimental` is listed with that mark.
 */
object ApiSignatures {

    private const val INTERNAL = "Lco/xenastudios/neoskript/api/annotation/Internal;"
    private const val EXPERIMENTAL = "Lco/xenastudios/neoskript/api/annotation/Experimental;"
    private const val API_PACKAGE = "co/xenastudios/neoskript/api/"
    private const val NEOSKRIPT = "co/xenastudios/neoskript/"
    private val CLASS_NAME = Regex("L([A-Za-z0-9_/$]+)[;<]")

    /** What [read] found: the signatures, and the stable ones that use what isn't in the API. */
    data class Result(val text: String, val problems: List<String>)

    /** The signatures of the classes under [classesDirs]. */
    fun read(classesDirs: Iterable<File>): Result {
        val classes = classesDirs.filter { it.isDirectory }
            .flatMap { dir -> dir.walk().filter { it.isFile && it.name.endsWith(".class") }.toList() }
            .map { file -> ClassNode().also { ClassReader(file.readBytes()).accept(it, ClassReader.SKIP_CODE) } }
            .filter { !it.name.endsWith("/package-info") && !it.name.endsWith("/module-info") }
            .associateBy { it.name }
        val hidden = classes.values.filter { !visible(it, classes) }.map { it.name }.toSet()
        val problems = mutableListOf<String>()
        val out = StringBuilder()
        for (node in classes.values.sortedBy { it.name }) {
            if (node.name in hidden) {
                continue
            }
            val name = javaName(node.name)
            out.append(modifiers(node.access, true)).append(kind(node.access)).append(' ').append(name)
            if (node.superName != null && node.superName != "java/lang/Object" && node.access and Opcodes.ACC_ENUM == 0
                && node.superName != "java/lang/Record") {
                out.append(" extends ").append(javaName(node.superName))
            }
            if (node.interfaces.isNotEmpty() && node.access and Opcodes.ACC_ANNOTATION == 0) {
                out.append(if (node.access and Opcodes.ACC_INTERFACE != 0) " extends " else " implements ")
                    .append(node.interfaces.sorted().joinToString(", ") { javaName(it) })
            }
            if (!node.permittedSubclasses.isNullOrEmpty()) {
                out.append(" permits ").append(node.permittedSubclasses.sorted().joinToString(", ") { javaName(it) })
            }
            node.signature?.let { out.append(" signature ").append(it) }
            out.append(experimental(node.invisibleAnnotations, node.visibleAnnotations)).append('\n')
            check(listOfNotNull(node.superName, node.signature) + node.interfaces, name, hidden, problems)
            for (field in node.fields.sortedBy { it.name }) {
                if (!exposed(field.access) || field.access and Opcodes.ACC_SYNTHETIC != 0
                    || internal(field.invisibleAnnotations, field.visibleAnnotations)) {
                    continue
                }
                check(listOfNotNull(field.desc, field.signature), "$name.${field.name}", hidden, problems)
                out.append("  ").append(modifiers(field.access, false)).append(Type.getType(field.desc).className)
                    .append(' ').append(field.name)
                field.signature?.let { out.append(" signature ").append(it) }
                field.value?.let { out.append(" = ").append(it) }
                out.append(experimental(field.invisibleAnnotations, field.visibleAnnotations)).append('\n')
            }
            for (method in node.methods.sortedWith(compareBy({ it.name }, { it.desc }))) {
                if (!exposed(method.access) || method.access and (Opcodes.ACC_SYNTHETIC or Opcodes.ACC_BRIDGE) != 0
                    || method.name == "<clinit>" || internal(method.invisibleAnnotations, method.visibleAnnotations)) {
                    continue
                }
                val type = Type.getMethodType(method.desc)
                val member = if (method.name == "<init>") simpleName(node.name) else method.name
                check(listOfNotNull(method.desc, method.signature) + method.exceptions.orEmpty(), "$name.$member",
                    hidden, problems)
                out.append("  ").append(modifiers(method.access and Opcodes.ACC_VARARGS.inv(), false))
                if (method.name != "<init>") {
                    out.append(type.returnType.className).append(' ')
                }
                out.append(member).append('(').append(type.argumentTypes.joinToString(", ") { it.className }).append(')')
                if (method.exceptions.isNotEmpty()) {
                    out.append(" throws ").append(method.exceptions.sorted().joinToString(", ") { javaName(it) })
                }
                method.annotationDefault?.let { out.append(" default ").append(value(it)) }
                method.signature?.let { out.append(" signature ").append(it) }
                out.append(experimental(method.invisibleAnnotations, method.visibleAnnotations)).append('\n')
            }
            out.append('\n')
        }
        return Result(out.toString().trimEnd() + "\n", problems)
    }

    /**
     * Why [actual] signatures can't replace the [expected] ones, or `null` if they match.
     *
     * @param file the committed file's name, for the message
     */
    fun compare(expected: String, actual: String, file: String): String? {
        if (actual == expected) {
            return null
        }
        val expectedLines = expected.lines().toSet()
        val actualLines = actual.lines().toSet()
        return buildString {
            append("The addon API changed, and ").append(file).append(" doesn't match it.\n")
            expected.lines().filter { it.isNotBlank() && it !in actualLines }.forEach { append("  - ").append(it).append('\n') }
            actual.lines().filter { it.isNotBlank() && it !in expectedLines }.forEach { append("  + ").append(it).append('\n') }
            append("Removing or changing a stable signature breaks addons: don't, before a major release.\n")
            append("If the change is meant, run ./gradlew :neoskript-api:apiDump and commit the file.")
        }
    }

    /** Whether a class is part of the API: public, not internal, and if nested, in a class that is too. */
    private fun visible(node: ClassNode, classes: Map<String, ClassNode>): Boolean {
        if (internal(node.invisibleAnnotations, node.visibleAnnotations)) {
            return false
        }
        val inner = node.innerClasses.firstOrNull { it.name == node.name }
        if (inner != null) {
            if (inner.access and (Opcodes.ACC_PUBLIC or Opcodes.ACC_PROTECTED) == 0) {
                return false
            }
            val outer = inner.outerName ?: return false
            return classes[outer]?.let { visible(it, classes) } ?: false
        }
        return node.access and Opcodes.ACC_PUBLIC != 0
    }

    private fun exposed(access: Int) = access and (Opcodes.ACC_PUBLIC or Opcodes.ACC_PROTECTED) != 0

    private fun internal(vararg lists: List<AnnotationNode>?) =
        lists.any { list -> list.orEmpty().any { it.desc == INTERNAL } }

    private fun experimental(vararg lists: List<AnnotationNode>?) =
        if (lists.any { list -> list.orEmpty().any { it.desc == EXPERIMENTAL } }) " @Experimental" else ""

    /** Flags classes outside the API, and the API's own internal ones, in descriptors, signatures and names. */
    private fun check(types: List<String>, where: String, hidden: Set<String>, problems: MutableList<String>) {
        for (type in types) {
            val names = if (type.contains(';') || type.startsWith("(")) {
                CLASS_NAME.findAll(type).map { it.groupValues[1] }.toList()
            } else {
                listOf(type)
            }
            for (name in names) {
                if (name.startsWith(NEOSKRIPT) && !name.startsWith(API_PACKAGE)) {
                    problems.add("$where uses ${javaName(name)}, which isn't in the API")
                } else if (name in hidden) {
                    problems.add("$where uses ${javaName(name)}, which isn't public API (internal or not public)")
                }
            }
        }
    }

    private fun value(value: Any?): String = when (value) {
        is List<*> -> value.joinToString(", ", "{", "}") { value(it) }
        is Array<*> -> value.joinToString(".")
        is Type -> value.className + ".class"
        is AnnotationNode -> "@" + Type.getType(value.desc).className
        is String -> "\"" + value + "\""
        else -> value.toString()
    }

    private fun modifiers(access: Int, type: Boolean): String {
        val words = mutableListOf<String>()
        if (access and Opcodes.ACC_PUBLIC != 0) words.add("public")
        if (access and Opcodes.ACC_PROTECTED != 0) words.add("protected")
        if (access and Opcodes.ACC_STATIC != 0 && !type) words.add("static")
        if (access and Opcodes.ACC_ABSTRACT != 0 && !(type && access and Opcodes.ACC_INTERFACE != 0)) words.add("abstract")
        if (access and Opcodes.ACC_FINAL != 0 && !(type && access and Opcodes.ACC_ENUM != 0)) words.add("final")
        return if (words.isEmpty()) "" else words.joinToString(" ", postfix = " ")
    }

    private fun kind(access: Int) = when {
        access and Opcodes.ACC_ANNOTATION != 0 -> "@interface"
        access and Opcodes.ACC_INTERFACE != 0 -> "interface"
        access and Opcodes.ACC_ENUM != 0 -> "enum"
        access and Opcodes.ACC_RECORD != 0 -> "record"
        else -> "class"
    }

    private fun javaName(internalName: String) = internalName.replace('/', '.')

    private fun simpleName(internalName: String) = internalName.substringAfterLast('/').substringAfterLast('$')
}

private fun problemsMessage(problems: List<String>) =
    "The API exposes what isn't in it:\n  " + problems.joinToString("\n  ") +
        "\nMove those types into the API, or mark what uses them @Internal."

/** Writes the API's signatures to the committed file: run it when a change to the API is meant. */
@CacheableTask
abstract class ApiDump : DefaultTask() {

    @get:InputFiles
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val classes: ConfigurableFileCollection

    @get:OutputFile
    abstract val signatures: RegularFileProperty

    @TaskAction
    fun dump() {
        val result = ApiSignatures.read(classes.files)
        if (result.problems.isNotEmpty()) {
            throw GradleException(problemsMessage(result.problems))
        }
        signatures.get().asFile.apply { parentFile.mkdirs() }.writeText(result.text)
    }
}

/** Fails when the API's signatures don't match the committed file, so changes to the API are deliberate. */
@CacheableTask
abstract class ApiCheck : DefaultTask() {

    @get:InputFiles
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val classes: ConfigurableFileCollection

    @get:InputFile
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val signatures: RegularFileProperty

    /** Where the check notes that it passed, so it only runs again when the API or the file changes. */
    @get:OutputFile
    abstract val marker: RegularFileProperty

    @TaskAction
    fun check() {
        val result = ApiSignatures.read(classes.files)
        if (result.problems.isNotEmpty()) {
            throw GradleException(problemsMessage(result.problems))
        }
        val file = signatures.get().asFile
        ApiSignatures.compare(file.readText(), result.text, file.name)?.let { throw GradleException(it) }
        marker.get().asFile.apply { parentFile.mkdirs() }.writeText("ok\n")
    }
}
