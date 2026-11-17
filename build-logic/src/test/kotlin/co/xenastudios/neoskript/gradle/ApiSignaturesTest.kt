package co.xenastudios.neoskript.gradle

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import org.objectweb.asm.ClassWriter
import org.objectweb.asm.Opcodes
import java.io.File

class ApiSignaturesTest {

    @TempDir
    lateinit var dir: File

    /** An API interface `co.xenastudios.neoskript.api.Thing` with one method, whose signature is [signature]. */
    private fun api(name: String, signature: String?, descriptor: String = "(Ljava/util/List;)V"): File {
        val classes = File(dir, name).apply { mkdirs() }
        val writer = ClassWriter(0)
        writer.visit(Opcodes.V21, Opcodes.ACC_PUBLIC or Opcodes.ACC_INTERFACE or Opcodes.ACC_ABSTRACT,
            "co/xenastudios/neoskript/api/Thing", null, "java/lang/Object", null)
        writer.visitMethod(Opcodes.ACC_PUBLIC or Opcodes.ACC_ABSTRACT, "take", descriptor, signature, null).visitEnd()
        writer.visitEnd()
        File(classes, "co/xenastudios/neoskript/api").apply { mkdirs() }.resolve("Thing.class")
            .writeBytes(writer.toByteArray())
        return classes
    }

    @Test
    fun aChangeOnlyToGenericsFailsTheCheck() {
        val before = ApiSignatures.read(listOf(api("before", "(Ljava/util/List<Ljava/lang/String;>;)V")))
        val after = ApiSignatures.read(listOf(api("after", "(Ljava/util/List<Ljava/lang/Integer;>;)V")))
        val failure = ApiSignatures.compare(before.text, after.text, "test.api")
        assertNotNull(failure)
        assertTrue(failure!!.contains("Ljava/lang/Integer;"), failure)
        assertNull(ApiSignatures.compare(before.text, before.text, "test.api"))
    }

    @Test
    fun internalsInGenericSignaturesAreFlagged() {
        val result = ApiSignatures.read(listOf(api("leak",
            "(Ljava/util/List<Lco/xenastudios/neoskript/runtime/KeyedValues;>;)V")))
        assertEquals(1, result.problems.size, result.problems.toString())
        assertTrue(result.problems[0].contains("co.xenastudios.neoskript.runtime.KeyedValues"))
    }
}
