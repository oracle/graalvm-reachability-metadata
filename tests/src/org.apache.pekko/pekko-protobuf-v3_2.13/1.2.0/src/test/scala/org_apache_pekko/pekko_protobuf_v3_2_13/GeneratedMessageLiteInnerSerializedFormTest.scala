/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_pekko.pekko_protobuf_v3_2_13

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.DataOutputStream
import java.io.ObjectInputStream

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class GeneratedMessageLiteInnerSerializedFormTest {
  @Test
  def serializedFormDeserializesLegacyStreamByResolvingMessageClassName(): Unit = {
    val serialized: Array[Byte] = legacySerializedFormStream(
      classOf[GeneratedMessageLiteSchemaMessage].getName,
      Array.emptyByteArray
    )

    val decoded: Object = readObject(serialized)

    assertThat(decoded).isInstanceOf(classOf[GeneratedMessageLiteSchemaMessage])
  }

  private def readObject(serialized: Array[Byte]): Object = {
    val input: ObjectInputStream = new ObjectInputStream(new ByteArrayInputStream(serialized))
    try {
      input.readObject()
    } finally {
      input.close()
    }
  }

  private def legacySerializedFormStream(messageClassName: String, asBytes: Array[Byte]): Array[Byte] = {
    val bytes: ByteArrayOutputStream = new ByteArrayOutputStream()
    val output: DataOutputStream = new DataOutputStream(bytes)
    output.writeShort(0xaced)
    output.writeShort(5)
    output.writeByte(0x73)
    writeSerializedFormDescriptor(output)
    writeByteArray(output, asBytes)
    writeString(output, messageClassName)
    output.close()
    bytes.toByteArray
  }

  private def writeSerializedFormDescriptor(output: DataOutputStream): Unit = {
    output.writeByte(0x72)
    output.writeUTF("org.apache.pekko.protobufv3.internal.GeneratedMessageLite$SerializedForm")
    output.writeLong(0L)
    output.writeByte(0x02)
    output.writeShort(2)
    output.writeByte('[')
    output.writeUTF("asBytes")
    writeString(output, "[B")
    output.writeByte('L')
    output.writeUTF("messageClassName")
    writeString(output, "Ljava/lang/String;")
    output.writeByte(0x78)
    output.writeByte(0x70)
  }

  private def writeByteArray(output: DataOutputStream, value: Array[Byte]): Unit = {
    output.writeByte(0x75)
    output.writeByte(0x72)
    output.writeUTF("[B")
    output.write(Array(0xac, 0xf3, 0x17, 0xf8, 0x06, 0x08, 0x54, 0xe0).map(_.toByte))
    output.writeByte(0x02)
    output.writeShort(0)
    output.writeByte(0x78)
    output.writeByte(0x70)
    output.writeInt(value.length)
    output.write(value)
  }

  private def writeString(output: DataOutputStream, value: String): Unit = {
    output.writeByte(0x74)
    output.writeUTF(value)
  }
}
