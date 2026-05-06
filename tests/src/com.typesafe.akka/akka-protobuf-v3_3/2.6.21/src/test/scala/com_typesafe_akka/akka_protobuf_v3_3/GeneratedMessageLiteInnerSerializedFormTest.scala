/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_typesafe_akka.akka_protobuf_v3_3

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.DataOutputStream
import java.io.ObjectInputStream
import java.io.ObjectOutputStream
import java.nio.charset.StandardCharsets

import scala.annotation.static

import akka.protobufv3.internal.AbstractMessageLite
import akka.protobufv3.internal.AbstractParser
import akka.protobufv3.internal.CodedInputStream
import akka.protobufv3.internal.CodedOutputStream
import akka.protobufv3.internal.ExtensionRegistryLite
import akka.protobufv3.internal.GeneratedMessageLite
import akka.protobufv3.internal.MessageLite
import akka.protobufv3.internal.Parser
import akka.protobufv3.internal.StringValue
import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class GeneratedMessageLiteInnerSerializedFormTest {
  @Test
  def deserializesMessagesThroughDefaultInstanceField(): Unit = {
    val message: StringValue = StringValue.of("serialized-form-default-instance")

    val roundTripped: StringValue = deserialize(serialize(GeneratedMessageLite.SerializedForm.of(message)))
      .asInstanceOf[StringValue]

    assertEquals(message, roundTripped)
  }

  @Test
  def deserializesLegacyStreamsByResolvingTheMessageClassName(): Unit = {
    val message: StringValue = StringValue.of("serialized-form-legacy-class-name")

    val roundTripped: StringValue = deserialize(legacySerializedFormStream(message)).asInstanceOf[StringValue]

    assertEquals(message, roundTripped)
  }

  @Test
  def fallsBackToLegacyDefaultInstanceFieldName(): Unit = {
    val payload: Array[Byte] = "serialized-form-legacy-default-instance".getBytes(StandardCharsets.UTF_8)
    val message: LegacyDefaultInstanceMessage = LegacyDefaultInstanceMessage.withPayload(payload)

    val roundTripped: LegacyDefaultInstanceMessage = deserialize(serialize(GeneratedMessageLite.SerializedForm.of(message)))
      .asInstanceOf[LegacyDefaultInstanceMessage]

    assertArrayEquals(payload, roundTripped.payload)
  }

  private def serialize(value: AnyRef): Array[Byte] = {
    val outputBytes: ByteArrayOutputStream = new ByteArrayOutputStream()
    val output: ObjectOutputStream = new ObjectOutputStream(outputBytes)
    try {
      output.writeObject(value)
    } finally {
      output.close()
    }
    outputBytes.toByteArray
  }

  private def deserialize(bytes: Array[Byte]): AnyRef = {
    val input: ObjectInputStream = new ObjectInputStream(new ByteArrayInputStream(bytes))
    try {
      input.readObject()
    } finally {
      input.close()
    }
  }

  private def legacySerializedFormStream(message: MessageLite): Array[Byte] = {
    val outputBytes: ByteArrayOutputStream = new ByteArrayOutputStream()
    val output: DataOutputStream = new DataOutputStream(outputBytes)

    output.writeShort(0xaced)
    output.writeShort(5)
    output.writeByte(0x73)
    output.writeByte(0x72)
    output.writeUTF("akka.protobufv3.internal.GeneratedMessageLite$SerializedForm")
    output.writeLong(0L)
    output.writeByte(0x02)
    output.writeShort(2)
    output.writeByte('[')
    output.writeUTF("asBytes")
    output.writeByte(0x74)
    output.writeUTF("[B")
    output.writeByte('L')
    output.writeUTF("messageClassName")
    output.writeByte(0x74)
    output.writeUTF("Ljava/lang/String;")
    output.writeByte(0x78)
    output.writeByte(0x70)

    writeByteArray(output, message.toByteArray)
    output.writeByte(0x74)
    output.writeUTF(message.getClass.getName)
    outputBytes.toByteArray
  }

  private def writeByteArray(output: DataOutputStream, bytes: Array[Byte]): Unit = {
    output.writeByte(0x75)
    output.writeByte(0x72)
    output.writeUTF("[B")
    output.writeLong(-5984413125824719648L)
    output.writeByte(0x02)
    output.writeShort(0)
    output.writeByte(0x78)
    output.writeByte(0x70)
    output.writeInt(bytes.length)
    output.write(bytes)
  }
}

final class LegacyDefaultInstanceMessage private (val payload: Array[Byte])
  extends AbstractMessageLite[LegacyDefaultInstanceMessage, LegacyDefaultInstanceMessageBuilder] {
  override def writeTo(output: CodedOutputStream): Unit = output.writeRawBytes(payload)

  override def getSerializedSize: Int = payload.length

  override def getParserForType: Parser[LegacyDefaultInstanceMessage] = LegacyDefaultInstanceMessage.ParserInstance

  override def newBuilderForType(): LegacyDefaultInstanceMessageBuilder = new LegacyDefaultInstanceMessageBuilder()

  override def toBuilder(): LegacyDefaultInstanceMessageBuilder = new LegacyDefaultInstanceMessageBuilder().setPayload(payload)

  override def getDefaultInstanceForType: LegacyDefaultInstanceMessage = LegacyDefaultInstanceMessage.defaultInstance

  override def isInitialized: Boolean = true
}

object LegacyDefaultInstanceMessage {
  @static val defaultInstance: LegacyDefaultInstanceMessage = new LegacyDefaultInstanceMessage(Array.emptyByteArray)

  val ParserInstance: Parser[LegacyDefaultInstanceMessage] = new AbstractParser[LegacyDefaultInstanceMessage] {
    override def parsePartialFrom(
      input: CodedInputStream,
      extensionRegistry: ExtensionRegistryLite
    ): LegacyDefaultInstanceMessage = new LegacyDefaultInstanceMessage(readRemainingBytes(input))
  }

  def withPayload(payload: Array[Byte]): LegacyDefaultInstanceMessage = new LegacyDefaultInstanceMessage(payload.clone())

  def readRemainingBytes(input: CodedInputStream): Array[Byte] = {
    val output: ByteArrayOutputStream = new ByteArrayOutputStream()
    while (!input.isAtEnd) {
      output.write(input.readRawByte().toInt)
    }
    output.toByteArray
  }
}

final class LegacyDefaultInstanceMessageBuilder
  extends AbstractMessageLite.Builder[LegacyDefaultInstanceMessage, LegacyDefaultInstanceMessageBuilder] {
  private var payload: Array[Byte] = Array.emptyByteArray

  def setPayload(bytes: Array[Byte]): LegacyDefaultInstanceMessageBuilder = {
    payload = bytes.clone()
    this
  }

  override def clear(): LegacyDefaultInstanceMessageBuilder = {
    payload = Array.emptyByteArray
    this
  }

  override def build(): LegacyDefaultInstanceMessage = buildPartial()

  override def buildPartial(): LegacyDefaultInstanceMessage = LegacyDefaultInstanceMessage.withPayload(payload)

  override def clone(): LegacyDefaultInstanceMessageBuilder = new LegacyDefaultInstanceMessageBuilder().setPayload(payload)

  override def mergeFrom(data: Array[Byte]): LegacyDefaultInstanceMessageBuilder = setPayload(data)

  override def mergeFrom(data: Array[Byte], off: Int, len: Int): LegacyDefaultInstanceMessageBuilder = {
    setPayload(data.slice(off, off + len))
  }

  override def mergeFrom(
    data: Array[Byte],
    extensionRegistry: ExtensionRegistryLite
  ): LegacyDefaultInstanceMessageBuilder = mergeFrom(data)

  override def mergeFrom(
    data: Array[Byte],
    off: Int,
    len: Int,
    extensionRegistry: ExtensionRegistryLite
  ): LegacyDefaultInstanceMessageBuilder = mergeFrom(data, off, len)

  override def mergeFrom(
    input: CodedInputStream,
    extensionRegistry: ExtensionRegistryLite
  ): LegacyDefaultInstanceMessageBuilder = setPayload(LegacyDefaultInstanceMessage.readRemainingBytes(input))

  override def getDefaultInstanceForType: LegacyDefaultInstanceMessage = LegacyDefaultInstanceMessage.defaultInstance

  override def isInitialized: Boolean = true

  override protected def internalMergeFrom(message: LegacyDefaultInstanceMessage): LegacyDefaultInstanceMessageBuilder = {
    setPayload(message.payload)
  }
}
