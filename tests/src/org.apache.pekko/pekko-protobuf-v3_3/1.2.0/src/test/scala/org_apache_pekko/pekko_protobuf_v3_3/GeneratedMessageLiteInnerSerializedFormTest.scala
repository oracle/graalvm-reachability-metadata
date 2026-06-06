/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_pekko.pekko_protobuf_v3_3

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.DataOutputStream
import java.io.ObjectInputStream
import java.io.ObjectOutputStream
import java.io.ObjectStreamClass
import java.io.ObjectStreamConstants

import org.apache.pekko.protobufv3.internal.GeneratedMessageLite
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class GeneratedMessageLiteInnerSerializedFormTest {
  @Test
  def serializedFormRestoresLiteMessageThroughDefaultInstanceField(): Unit = {
    val message: GeneratedMessageLiteSerializedFormProbe = GeneratedMessageLiteSerializedFormProbe.getDefaultInstance

    val restored: AnyRef = deserialize(serialize(GeneratedMessageLite.SerializedForm.of(message)))

    assertThat(restored).isInstanceOf(classOf[GeneratedMessageLiteSerializedFormProbe])
    assertThat(restored.asInstanceOf[GeneratedMessageLiteSerializedFormProbe].getSerializedSize).isZero()
  }

  @Test
  def serializedFormResolvesLegacyStreamWithoutSerializedMessageClass(): Unit = {
    val message: GeneratedMessageLiteSerializedFormProbe = GeneratedMessageLiteSerializedFormProbe.getDefaultInstance
    val restored: AnyRef = deserialize(serializeWithNullMessageClass(message))

    assertThat(restored).isInstanceOf(classOf[GeneratedMessageLiteSerializedFormProbe])
    assertThat(restored.asInstanceOf[GeneratedMessageLiteSerializedFormProbe].getSerializedSize).isZero()
  }

  private def serialize(value: AnyRef): Array[Byte] = {
    val bytes: ByteArrayOutputStream = new ByteArrayOutputStream()
    val output: ObjectOutputStream = new ObjectOutputStream(bytes)
    try {
      output.writeObject(value)
    } finally {
      output.close()
    }
    bytes.toByteArray
  }

  private def serializeWithNullMessageClass(
      message: GeneratedMessageLiteSerializedFormProbe): Array[Byte] = {
    val bytes: ByteArrayOutputStream = new ByteArrayOutputStream()
    val output: DataOutputStream = new DataOutputStream(bytes)
    try {
      output.writeShort(ObjectStreamConstants.STREAM_MAGIC)
      output.writeShort(ObjectStreamConstants.STREAM_VERSION)
      writeSerializedForm(output)
      writeByteArray(output, message.toByteArray)
      output.writeByte(ObjectStreamConstants.TC_NULL)
      writeString(output, message.getClass.getName)
    } finally {
      output.close()
    }
    bytes.toByteArray
  }

  private def writeSerializedForm(output: DataOutputStream): Unit = {
    output.writeByte(ObjectStreamConstants.TC_OBJECT)
    output.writeByte(ObjectStreamConstants.TC_CLASSDESC)
    output.writeUTF("org.apache.pekko.protobufv3.internal.GeneratedMessageLite$SerializedForm")
    output.writeLong(0L)
    output.writeByte(ObjectStreamConstants.SC_SERIALIZABLE)
    output.writeShort(3)
    writeObjectField(output, '[', "asBytes", "[B")
    writeObjectField(output, 'L', "messageClass", "Ljava/lang/Class;")
    writeObjectField(output, 'L', "messageClassName", "Ljava/lang/String;")
    output.writeByte(ObjectStreamConstants.TC_ENDBLOCKDATA)
    output.writeByte(ObjectStreamConstants.TC_NULL)
  }

  private def writeObjectField(
      output: DataOutputStream,
      typeCode: Char,
      name: String,
      signature: String): Unit = {
    output.writeByte(typeCode)
    output.writeUTF(name)
    writeString(output, signature)
  }

  private def writeByteArray(output: DataOutputStream, value: Array[Byte]): Unit = {
    output.writeByte(ObjectStreamConstants.TC_ARRAY)
    output.writeByte(ObjectStreamConstants.TC_CLASSDESC)
    output.writeUTF("[B")
    output.writeLong(ObjectStreamClass.lookup(classOf[Array[Byte]]).getSerialVersionUID)
    output.writeByte(ObjectStreamConstants.SC_SERIALIZABLE)
    output.writeShort(0)
    output.writeByte(ObjectStreamConstants.TC_ENDBLOCKDATA)
    output.writeByte(ObjectStreamConstants.TC_NULL)
    output.writeInt(value.length)
    output.write(value)
  }

  private def writeString(output: DataOutputStream, value: String): Unit = {
    output.writeByte(ObjectStreamConstants.TC_STRING)
    output.writeUTF(value)
  }

  private def deserialize(bytes: Array[Byte]): AnyRef = {
    val input: ObjectInputStream = new ObjectInputStream(new ByteArrayInputStream(bytes))
    try {
      input.readObject()
    } finally {
      input.close()
    }
  }
}
