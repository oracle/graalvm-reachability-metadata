/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_pekko.pekko_protobuf_v3_2_13

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.ObjectInputStream
import java.io.ObjectOutputStream

import org.apache.pekko.protobufv3.internal.GeneratedMessageLite
import org.apache.pekko.protobufv3.internal.MessageLite
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class GeneratedMessageLiteInnerSerializedFormTest {
  @Test
  def deserializesSerializedFormUsingClassNameAndDefaultInstanceField(): Unit = {
    val message: SerializedFormDefaultInstanceProbe = SerializedFormDefaultInstanceProbe.getDefaultInstance
    val serialized: Array[Byte] = serializeSerializedForm(message, Some(classOf[SerializedFormDefaultInstanceProbe]))

    val restored: AnyRef = deserialize(serialized)

    assertThat(restored).isInstanceOf(classOf[SerializedFormDefaultInstanceProbe])
  }

  @Test
  def deserializesSerializedFormUsingLegacyDefaultInstanceField(): Unit = {
    val message: SerializedFormLegacyDefaultInstanceProbe = SerializedFormLegacyDefaultInstanceProbe.getDefaultInstance()
    val serialized: Array[Byte] = serializeSerializedForm(
      message,
      Some(classOf[SerializedFormLegacyDefaultInstanceProbe]))

    val restored: AnyRef = deserialize(serialized)

    assertThat(restored).isInstanceOf(classOf[SerializedFormLegacyDefaultInstanceProbe])
  }

  private def serializeSerializedForm(message: MessageLite, classObjectToNull: Option[Class[_]]): Array[Byte] = {
    val bytes: ByteArrayOutputStream = new ByteArrayOutputStream()
    val form: GeneratedMessageLite.SerializedForm = GeneratedMessageLite.SerializedForm.of(message)
    val output: ObjectOutputStream = classObjectToNull match {
      case Some(messageClass) => new NullingClassObjectOutputStream(bytes, messageClass)
      case None => new ObjectOutputStream(bytes)
    }
    try {
      output.writeObject(form)
    } finally {
      output.close()
    }
    bytes.toByteArray
  }

  private def deserialize(serialized: Array[Byte]): AnyRef = {
    val input: ObjectInputStream = new ObjectInputStream(new ByteArrayInputStream(serialized))
    try {
      input.readObject()
    } finally {
      input.close()
    }
  }

  private final class NullingClassObjectOutputStream(
      output: ByteArrayOutputStream,
      classObjectToNull: Class[_])
      extends ObjectOutputStream(output) {
    enableReplaceObject(true)

    override protected def replaceObject(obj: AnyRef): AnyRef = {
      if (obj eq classObjectToNull) {
        null
      } else {
        obj
      }
    }
  }
}
