/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_typesafe_akka.akka_protobuf_v3_2_13

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.ObjectInputStream
import java.io.ObjectOutputStream
import java.io.OutputStream

import akka.protobufv3.internal.GeneratedMessageLite
import akka.protobufv3.internal.MessageLite
import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class GeneratedMessageLiteInnerSerializedFormTest {
  @Test
  def serializedFormUsesDefaultInstanceFieldWhenResolvingMessage(): Unit = {
    val payload: Array[Byte] = Array[Byte](1, 3, 5, 7)
    val resolvedMessage: AnyRef = serializeFormAndReadBackWithClassNameLookup(
      new DefaultInstanceLiteMessage(payload)
    )

    assertEquals(classOf[DefaultInstanceLiteMessage], resolvedMessage.getClass)
    assertArrayEquals(payload, resolvedMessage.asInstanceOf[DefaultInstanceLiteMessage].payload())
  }

  @Test
  def serializedFormFallsBackToLegacyDefaultInstanceField(): Unit = {
    val payload: Array[Byte] = Array[Byte](2, 4, 6, 8)
    val resolvedMessage: AnyRef = serializeFormAndReadBackWithClassNameLookup(
      new LegacyDefaultInstanceLiteMessage(payload)
    )

    assertEquals(classOf[LegacyDefaultInstanceLiteMessage], resolvedMessage.getClass)
    assertArrayEquals(payload, resolvedMessage.asInstanceOf[LegacyDefaultInstanceLiteMessage].payload())
  }

  private def serializeFormAndReadBackWithClassNameLookup(message: MessageLite): AnyRef = {
    val out = new ByteArrayOutputStream()
    val objectOut = new ClassNullingObjectOutputStream(out, message.getClass)
    try {
      objectOut.writeObject(GeneratedMessageLite.SerializedForm.of(message))
    } finally {
      objectOut.close()
    }

    val objectIn = new ObjectInputStream(new ByteArrayInputStream(out.toByteArray))
    try {
      objectIn.readObject()
    } finally {
      objectIn.close()
    }
  }
}

private final class ClassNullingObjectOutputStream(output: OutputStream, classToNull: Class[_])
    extends ObjectOutputStream(output) {
  enableReplaceObject(true)

  override protected def replaceObject(obj: AnyRef): AnyRef = {
    if (obj eq classToNull) {
      null
    } else {
      super.replaceObject(obj)
    }
  }
}
