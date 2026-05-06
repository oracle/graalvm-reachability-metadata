/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_typesafe_akka.akka_protobuf_v3_2_13

import java.util.Collections

import akka.protobufv3.internal.AbstractParser
import akka.protobufv3.internal.CodedInputStream
import akka.protobufv3.internal.Descriptors
import akka.protobufv3.internal.ExtensionRegistryLite
import akka.protobufv3.internal.Field
import akka.protobufv3.internal.GeneratedMessageV3
import akka.protobufv3.internal.ListValue
import akka.protobufv3.internal.Message
import akka.protobufv3.internal.Parser
import akka.protobufv3.internal.SourceContext
import akka.protobufv3.internal.Struct
import akka.protobufv3.internal.Type
import akka.protobufv3.internal.UnknownFieldSet
import akka.protobufv3.internal.Value
import akka.protobufv3.internal.{Option => ProtoOption}
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Test

class DescriptorMessageInfoFactoryTest {
  @Test
  def schemaCreationForProto3RepeatedMessageFieldsUsesDescriptorReflection(): Unit = {
    val message: TypeSchemaProbeMessage = new TypeSchemaProbeMessage()

    message.mergeEmptyInputThroughRuntimeSchema()

    assertSame(Type.getDescriptor, message.getDescriptorForType)
    assertEquals("google.protobuf.Type", message.getDescriptorForType.getFullName)
  }

  @Test
  def schemaCreationForProto3MessageOneofsUsesDescriptorReflection(): Unit = {
    val message: ValueSchemaProbeMessage = new ValueSchemaProbeMessage()

    message.mergeEmptyInputThroughRuntimeSchema()

    assertSame(Value.getDescriptor, message.getDescriptorForType)
    assertEquals("google.protobuf.Value", message.getDescriptorForType.getFullName)
  }
}

final class TypeSchemaProbeMessage extends GeneratedMessageV3 {
  private var name_ : AnyRef = ""
  private var fields_ : java.util.List[Field] = Collections.emptyList[Field]()
  private var oneofs_ : java.util.List[String] = Collections.emptyList[String]()
  private var options_ : java.util.List[ProtoOption] = Collections.emptyList[ProtoOption]()
  private var sourceContext_ : SourceContext = _
  private var syntax_ : Int = 0

  def mergeEmptyInputThroughRuntimeSchema(): Unit = {
    mergeFromAndMakeImmutableInternal(
      CodedInputStream.newInstance(Array.emptyByteArray),
      ExtensionRegistryLite.getEmptyRegistry
    )
  }

  def getFields(index: Int): Field = fields_.get(index)

  def getOptions(index: Int): ProtoOption = options_.get(index)

  override def getDescriptorForType: Descriptors.Descriptor = Type.getDescriptor

  override def getDefaultInstanceForType: Message = TypeSchemaProbeMessage.getDefaultInstance()

  override def getParserForType: Parser[_ <: GeneratedMessageV3] = TypeSchemaProbeMessage.ParserInstance

  override def getUnknownFields: UnknownFieldSet = UnknownFieldSet.getDefaultInstance

  override def newBuilderForType(): Message.Builder = unsupportedBuilder()

  override def toBuilder(): Message.Builder = unsupportedBuilder()

  override protected def newBuilderForType(parent: GeneratedMessageV3.BuilderParent): Message.Builder = unsupportedBuilder()

  override protected def internalGetFieldAccessorTable(): GeneratedMessageV3.FieldAccessorTable = {
    throw new UnsupportedOperationException("The runtime schema path does not use field accessor tables")
  }

  private def unsupportedBuilder(): Message.Builder = {
    throw new UnsupportedOperationException("The schema probe is not built via protobuf builders")
  }
}

object TypeSchemaProbeMessage {
  private val DefaultInstance: TypeSchemaProbeMessage = new TypeSchemaProbeMessage()

  val ParserInstance: Parser[TypeSchemaProbeMessage] = new AbstractParser[TypeSchemaProbeMessage] {
    override def parsePartialFrom(
      input: CodedInputStream,
      extensionRegistry: ExtensionRegistryLite
    ): TypeSchemaProbeMessage = new TypeSchemaProbeMessage()
  }

  def getDefaultInstance(): TypeSchemaProbeMessage = DefaultInstance
}

final class ValueSchemaProbeMessage extends GeneratedMessageV3 {
  private var kindCase_ : Int = 0
  private var kind_ : AnyRef = _

  def mergeEmptyInputThroughRuntimeSchema(): Unit = {
    mergeFromAndMakeImmutableInternal(
      CodedInputStream.newInstance(Array.emptyByteArray),
      ExtensionRegistryLite.getEmptyRegistry
    )
  }

  def getStructValue(): Struct = {
    if (kindCase_ == Value.STRUCT_VALUE_FIELD_NUMBER) {
      kind_.asInstanceOf[Struct]
    } else {
      Struct.getDefaultInstance
    }
  }

  def getListValue(): ListValue = {
    if (kindCase_ == Value.LIST_VALUE_FIELD_NUMBER) {
      kind_.asInstanceOf[ListValue]
    } else {
      ListValue.getDefaultInstance
    }
  }

  override def getDescriptorForType: Descriptors.Descriptor = Value.getDescriptor

  override def getDefaultInstanceForType: Message = ValueSchemaProbeMessage.getDefaultInstance()

  override def getParserForType: Parser[_ <: GeneratedMessageV3] = ValueSchemaProbeMessage.ParserInstance

  override def getUnknownFields: UnknownFieldSet = UnknownFieldSet.getDefaultInstance

  override def newBuilderForType(): Message.Builder = unsupportedBuilder()

  override def toBuilder(): Message.Builder = unsupportedBuilder()

  override protected def newBuilderForType(parent: GeneratedMessageV3.BuilderParent): Message.Builder = unsupportedBuilder()

  override protected def internalGetFieldAccessorTable(): GeneratedMessageV3.FieldAccessorTable = {
    throw new UnsupportedOperationException("The runtime schema path does not use field accessor tables")
  }

  private def unsupportedBuilder(): Message.Builder = {
    throw new UnsupportedOperationException("The schema probe is not built via protobuf builders")
  }
}

object ValueSchemaProbeMessage {
  private val DefaultInstance: ValueSchemaProbeMessage = new ValueSchemaProbeMessage()

  val ParserInstance: Parser[ValueSchemaProbeMessage] = new AbstractParser[ValueSchemaProbeMessage] {
    override def parsePartialFrom(
      input: CodedInputStream,
      extensionRegistry: ExtensionRegistryLite
    ): ValueSchemaProbeMessage = new ValueSchemaProbeMessage()
  }

  def getDefaultInstance(): ValueSchemaProbeMessage = DefaultInstance
}
