/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_typesafe_akka.akka_protobuf_v3_3

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.io.ObjectInputStream
import java.io.ObjectOutputStream
import java.io.OutputStream
import java.nio.charset.StandardCharsets

import akka.protobufv3.internal.AbstractParser
import akka.protobufv3.internal.ByteString
import akka.protobufv3.internal.CodedInputStream
import akka.protobufv3.internal.CodedOutputStream
import akka.protobufv3.internal.ExtensionRegistryLite
import akka.protobufv3.internal.MessageLite
import akka.protobufv3.internal.Parser
import akka.protobufv3.internal.StringValue
import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

import scala.annotation.static

class GeneratedMessageLiteInnerSerializedFormTest {
  @Test
  def readResolveUsesDefaultInstanceFieldFromLegacySerializedForm(): Unit = {
    val message: StringValue = StringValue.newBuilder().setValue("serialized-form").build()
    val serializedForm: Array[Byte] = legacySerializedForm(
      classOf[StringValue].getName,
      message.toByteArray
    )

    val deserialized: Object = deserialize(serializedForm)

    assertEquals(message, deserialized)
  }

  @Test
  def readResolveFallbackUsesLowerCaseDefaultInstanceFieldFromLegacySerializedForm(): Unit = {
    val messageClassName: String = classOf[FallbackNamedLiteMessage].getName
    val serializedForm: Array[Byte] = legacySerializedForm(messageClassName, Array.emptyByteArray)

    val deserialized: Object = deserialize(serializedForm)

    assertTrue(classOf[FallbackNamedLiteMessage].isInstance(deserialized))
    assertArrayEquals(
      Array.emptyByteArray,
      deserialized.asInstanceOf[FallbackNamedLiteMessage].toByteArray
    )
  }

  private def legacySerializedForm(messageClassName: String, asBytes: Array[Byte]): Array[Byte] = {
    val legacyForm = new LegacyGeneratedMessageLiteSerializedForm(asBytes, messageClassName)
    val originalBytes: Array[Byte] = serialize(legacyForm)
    replaceUtfValue(
      originalBytes,
      classOf[LegacyGeneratedMessageLiteSerializedForm].getName,
      "akka.protobufv3.internal.GeneratedMessageLite$SerializedForm"
    )
  }

  private def serialize(value: Object): Array[Byte] = {
    val bytes = new ByteArrayOutputStream()
    val stream = new ObjectOutputStream(bytes)
    try {
      stream.writeObject(value)
    } finally {
      stream.close()
    }
    bytes.toByteArray
  }

  private def deserialize(bytes: Array[Byte]): Object = {
    val stream = new ObjectInputStream(new ByteArrayInputStream(bytes))
    try {
      stream.readObject()
    } finally {
      stream.close()
    }
  }

  private def replaceUtfValue(bytes: Array[Byte], oldValue: String, newValue: String): Array[Byte] = {
    val oldBytes: Array[Byte] = oldValue.getBytes(StandardCharsets.UTF_8)
    val newBytes: Array[Byte] = newValue.getBytes(StandardCharsets.UTF_8)
    val oldToken: Array[Byte] = utfToken(oldBytes)
    val replacement: Array[Byte] = utfToken(newBytes)
    val index: Int = indexOf(bytes, oldToken)

    assertTrue(index >= 0, s"Serialized stream does not contain class descriptor for $oldValue")

    val result = new ByteArrayOutputStream()
    result.write(bytes, 0, index)
    result.write(replacement)
    result.write(bytes, index + oldToken.length, bytes.length - index - oldToken.length)
    result.toByteArray
  }

  private def utfToken(value: Array[Byte]): Array[Byte] = {
    assertTrue(value.length <= 0xffff)
    Array(((value.length >>> 8) & 0xff).toByte, (value.length & 0xff).toByte) ++ value
  }

  private def indexOf(bytes: Array[Byte], token: Array[Byte]): Int = {
    var index = 0
    while (index <= bytes.length - token.length) {
      var matched = true
      var tokenIndex = 0
      while (matched && tokenIndex < token.length) {
        matched = bytes(index + tokenIndex) == token(tokenIndex)
        tokenIndex += 1
      }
      if (matched) {
        return index
      }
      index += 1
    }
    -1
  }
}

@SerialVersionUID(0L)
final class LegacyGeneratedMessageLiteSerializedForm(
  private val asBytes: Array[Byte],
  private val messageClassName: String
) extends Serializable

final class FallbackNamedLiteMessage private (private val bytes: Array[Byte]) extends MessageLite {
  override def writeTo(output: CodedOutputStream): Unit = output.writeRawBytes(bytes)

  override def getSerializedSize(): Int = bytes.length

  override def getParserForType(): Parser[? <: MessageLite] = FallbackNamedLiteMessage.parser

  override def toByteString(): ByteString = ByteString.copyFrom(bytes)

  override def toByteArray(): Array[Byte] = bytes.clone()

  override def writeTo(output: OutputStream): Unit = output.write(bytes)

  override def writeDelimitedTo(output: OutputStream): Unit = {
    val codedOutput = CodedOutputStream.newInstance(output)
    codedOutput.writeUInt32NoTag(bytes.length)
    codedOutput.writeRawBytes(bytes)
    codedOutput.flush()
  }

  override def newBuilderForType(): MessageLite.Builder = new FallbackNamedLiteMessage.Builder()

  override def toBuilder(): MessageLite.Builder = new FallbackNamedLiteMessage.Builder().mergeFrom(bytes)

  override def getDefaultInstanceForType(): MessageLite = FallbackNamedLiteMessage.defaultInstance

  override def isInitialized(): Boolean = true
}

object FallbackNamedLiteMessage {
  @static val defaultInstance: FallbackNamedLiteMessage = new FallbackNamedLiteMessage(
    Array.emptyByteArray
  )

  private def parser: Parser[FallbackNamedLiteMessage] = new FallbackNamedLiteMessageParser(
    defaultInstance
  )

  final class Builder private[akka_protobuf_v3_3] () extends MessageLite.Builder {
    private var bytes: Array[Byte] = Array.emptyByteArray

    override def clear(): MessageLite.Builder = {
      bytes = Array.emptyByteArray
      this
    }

    override def build(): MessageLite = buildPartial()

    override def buildPartial(): MessageLite = new FallbackNamedLiteMessage(bytes.clone())

    override def clone(): MessageLite.Builder = new Builder().mergeFrom(bytes)

    override def mergeFrom(input: CodedInputStream): MessageLite.Builder = this

    override def mergeFrom(
      input: CodedInputStream,
      extensionRegistry: ExtensionRegistryLite
    ): MessageLite.Builder = this

    override def mergeFrom(byteString: ByteString): MessageLite.Builder = mergeFrom(
      byteString.toByteArray
    )

    override def mergeFrom(
      byteString: ByteString,
      extensionRegistry: ExtensionRegistryLite
    ): MessageLite.Builder = mergeFrom(byteString)

    override def mergeFrom(data: Array[Byte]): MessageLite.Builder = {
      bytes = data.clone()
      this
    }

    override def mergeFrom(
      data: Array[Byte],
      offset: Int,
      length: Int
    ): MessageLite.Builder = mergeFrom(data.slice(offset, offset + length))

    override def mergeFrom(
      data: Array[Byte],
      extensionRegistry: ExtensionRegistryLite
    ): MessageLite.Builder = mergeFrom(data)

    override def mergeFrom(
      data: Array[Byte],
      offset: Int,
      length: Int,
      extensionRegistry: ExtensionRegistryLite
    ): MessageLite.Builder = mergeFrom(data, offset, length)

    override def mergeFrom(input: InputStream): MessageLite.Builder = mergeFrom(
      input.readAllBytes()
    )

    override def mergeFrom(
      input: InputStream,
      extensionRegistry: ExtensionRegistryLite
    ): MessageLite.Builder = mergeFrom(input)

    override def mergeFrom(message: MessageLite): MessageLite.Builder = mergeFrom(message.toByteArray)

    override def mergeDelimitedFrom(input: InputStream): Boolean = {
      mergeFrom(input)
      true
    }

    override def mergeDelimitedFrom(
      input: InputStream,
      extensionRegistry: ExtensionRegistryLite
    ): Boolean = mergeDelimitedFrom(input)

    override def getDefaultInstanceForType(): MessageLite = FallbackNamedLiteMessage.defaultInstance

    override def isInitialized(): Boolean = true
  }
}

final class FallbackNamedLiteMessageParser(
  private val defaultInstance: FallbackNamedLiteMessage
) extends AbstractParser[FallbackNamedLiteMessage] {
  override def parsePartialFrom(
    input: CodedInputStream,
    extensionRegistry: ExtensionRegistryLite
  ): FallbackNamedLiteMessage = defaultInstance
}
