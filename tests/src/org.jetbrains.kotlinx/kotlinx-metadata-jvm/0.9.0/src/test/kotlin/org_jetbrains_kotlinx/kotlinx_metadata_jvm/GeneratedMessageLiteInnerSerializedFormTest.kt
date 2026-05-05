/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_jetbrains_kotlinx.kotlinx_metadata_jvm

import kotlinx.metadata.internal.metadata.ProtoBuf
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.ObjectInputStream
import java.io.ObjectOutputStream

public class GeneratedMessageLiteInnerSerializedFormTest {
    @Test
    public fun javaSerializationRestoresGeneratedMessageLiteMessages(): Unit {
        val original: ProtoBuf.StringTable = ProtoBuf.StringTable.newBuilder()
            .addString("kotlinx")
            .addString("metadata")
            .build()

        val restored: Any = deserialize(serialize(original))

        assertThat(restored).isInstanceOf(ProtoBuf.StringTable::class.java)
        val restoredStringTable: ProtoBuf.StringTable = restored as ProtoBuf.StringTable
        assertThat(restoredStringTable.stringList).containsExactly("kotlinx", "metadata")
        assertThat(restoredStringTable.toByteArray()).isEqualTo(original.toByteArray())
    }

    private fun serialize(value: Any): ByteArray {
        val byteOutput: ByteArrayOutputStream = ByteArrayOutputStream()
        ObjectOutputStream(byteOutput).use { objectOutput: ObjectOutputStream ->
            objectOutput.writeObject(value)
        }
        return byteOutput.toByteArray()
    }

    private fun deserialize(bytes: ByteArray): Any {
        return ObjectInputStream(ByteArrayInputStream(bytes)).use { objectInput: ObjectInputStream ->
            objectInput.readObject()
        }
    }
}
