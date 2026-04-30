/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_pekko.pekko_protobuf_v3_3

import org.apache.pekko.protobufv3.internal.GeneratedMessageLite
import org.apache.pekko.protobufv3.internal.MessageLite
import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.ObjectInputStream
import java.io.ObjectOutputStream

class GeneratedMessageLiteInnerSerializedFormTest {
  @Test
  def deserializesSerializedFormUsingDefaultInstanceField(): Unit = {
    val message: DefaultInstanceLiteMessage = DefaultInstanceLiteMessage.getDefaultInstance()

    val restored: MessageLite = deserializeSerializedFormWithClassNameOnly(message)

    assertEquals(classOf[DefaultInstanceLiteMessage], restored.getClass)
    assertArrayEquals(message.toByteArray, restored.toByteArray)
  }

  @Test
  def deserializesSerializedFormUsingFallbackDefaultInstanceField(): Unit = {
    val message: FallbackDefaultInstanceLiteMessage = FallbackDefaultInstanceLiteMessage.getDefaultInstance()

    val restored: MessageLite = deserializeSerializedFormWithClassNameOnly(message)

    assertEquals(classOf[FallbackDefaultInstanceLiteMessage], restored.getClass)
    assertArrayEquals(message.toByteArray, restored.toByteArray)
  }

  private def deserializeSerializedFormWithClassNameOnly(message: MessageLite): MessageLite = {
    val bytes: Array[Byte] = serializeSerializedFormWithClassNameOnly(message)
    val input: ObjectInputStream = new ObjectInputStream(new ByteArrayInputStream(bytes))
    try input.readObject().asInstanceOf[MessageLite]
    finally input.close()
  }

  private def serializeSerializedFormWithClassNameOnly(message: MessageLite): Array[Byte] = {
    val outputBytes: ByteArrayOutputStream = new ByteArrayOutputStream()
    val output: ObjectOutputStream = new MessageClassOmittingObjectOutputStream(outputBytes, message.getClass)
    try output.writeObject(GeneratedMessageLite.SerializedForm.of(message))
    finally output.close()
    outputBytes.toByteArray
  }
}

private class MessageClassOmittingObjectOutputStream(
    outputBytes: ByteArrayOutputStream,
    messageClass: Class[?])
    extends ObjectOutputStream(outputBytes) {
  enableReplaceObject(true)

  override protected def replaceObject(obj: AnyRef): AnyRef =
    if obj == messageClass then null else super.replaceObject(obj)
}
