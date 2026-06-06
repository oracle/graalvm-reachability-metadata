/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_pekko.pekko_protobuf_v3_3

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.ObjectInputStream
import java.io.ObjectOutputStream
import java.io.OutputStream

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
    val restored: AnyRef = deserialize(serializeWithNullMessageClass(
      GeneratedMessageLite.SerializedForm.of(message),
      classOf[GeneratedMessageLiteSerializedFormProbe]))

    assertThat(restored).isInstanceOf(classOf[GeneratedMessageLiteSerializedFormProbe])
    assertThat(restored.asInstanceOf[GeneratedMessageLiteSerializedFormProbe].getSerializedSize).isZero()
  }

  @Test
  def serializedFormFallsBackToLegacyDefaultInstanceFieldName(): Unit = {
    val message: GeneratedMessageLiteSerializedFormFallbackProbe =
      GeneratedMessageLiteSerializedFormFallbackProbe.getDefaultInstance

    val restored: AnyRef = deserialize(serialize(GeneratedMessageLite.SerializedForm.of(message)))

    assertThat(restored).isInstanceOf(classOf[GeneratedMessageLiteSerializedFormFallbackProbe])
    assertThat(restored.asInstanceOf[GeneratedMessageLiteSerializedFormFallbackProbe].getSerializedSize).isZero()
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

  private def serializeWithNullMessageClass(value: AnyRef, messageClass: Class[_]): Array[Byte] = {
    val bytes: ByteArrayOutputStream = new ByteArrayOutputStream()
    val output: ClassNullingObjectOutputStream = new ClassNullingObjectOutputStream(bytes, messageClass)
    try {
      output.writeObject(value)
    } finally {
      output.close()
    }
    bytes.toByteArray
  }

  private def deserialize(bytes: Array[Byte]): AnyRef = {
    val input: ObjectInputStream = new ObjectInputStream(new ByteArrayInputStream(bytes))
    try {
      input.readObject()
    } finally {
      input.close()
    }
  }

  private final class ClassNullingObjectOutputStream(output: OutputStream, messageClass: Class[_])
      extends ObjectOutputStream(output) {
    enableReplaceObject(true)

    override protected def replaceObject(obj: AnyRef): AnyRef = {
      if (obj == messageClass) {
        null
      } else {
        super.replaceObject(obj)
      }
    }
  }
}
