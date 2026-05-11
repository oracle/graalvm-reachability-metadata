/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_thesamet_scalapb.scalapb_runtime_2_13

import com.google.protobuf.ByteString
import com.google.protobuf.CodedOutputStream
import com.google.protobuf.{DescriptorProtos => JavaDescriptorProtos}
import com.google.protobuf.any.{Any => ProtoAny}
import com.google.protobuf.descriptor.FieldDescriptorProto
import com.google.protobuf.descriptor.{FieldOptions => DescriptorFieldOptions}
import com.google.protobuf.descriptor.{FileOptions => DescriptorFileOptions}
import com.google.protobuf.duration.Duration
import com.google.protobuf.field_mask.FieldMask
import com.google.protobuf.struct.ListValue
import com.google.protobuf.struct.Struct
import com.google.protobuf.struct.Value
import com.google.protobuf.timestamp.Timestamp
import com.google.protobuf.wrappers.Int32Value
import com.google.protobuf.wrappers.StringValue
import org.junit.jupiter.api.Assertions._
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.function.Executable
import scalapb.CollectionAdapter
import scalapb.DurationConverters
import scalapb.Encoding
import scalapb.FieldMaskUtil
import scalapb.StructUtils
import scalapb.TextFormat
import scalapb.TextFormatException
import scalapb.TimestampConverters
import scalapb.TypeMapper
import scalapb.UnknownFieldSet
import scalapb.WireType
import scalapb.descriptors.PEmpty
import scalapb.descriptors.PInt
import scalapb.descriptors.PMessage
import scalapb.descriptors.PString
import scalapb.descriptors.ReadsException
import scalapb.options.{FieldOptions => ScalaPbFieldOptions}
import scalapb.options.ScalaPbOptions
import scalapb.options.ScalapbProto

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.time.Instant
import java.time.{Duration => JavaDuration}
import scala.util.Failure
import scala.util.Success

class Scalapb_runtime_2_13Test {
  @Test
  def generatedMessagesRoundTripThroughCompanionsAndStreams(): Unit = {
    val timestamp = Timestamp(seconds = 123456789L, nanos = 987654321)
    val timestampBytes = timestamp.toByteArray

    assertEquals(timestamp.serializedSize, timestampBytes.length)
    assertEquals(timestamp, Timestamp.parseFrom(timestampBytes))
    assertEquals(timestamp, Timestamp.parseFrom(timestamp.toByteString.newCodedInput()))
    assertEquals(timestamp, Timestamp.validate(timestampBytes).get)

    val invalid = Timestamp.validate(Array[Byte](Byte.MinValue))
    assertTrue(invalid.isFailure)

    val output = new ByteArrayOutputStream()
    timestamp.writeDelimitedTo(output)
    Duration(seconds = 5L, nanos = 6).writeDelimitedTo(output)

    val input = new ByteArrayInputStream(output.toByteArray)
    assertEquals(Some(timestamp), Timestamp.parseDelimitedFrom(input))
    assertEquals(Some(Duration(seconds = 5L, nanos = 6)), Duration.parseDelimitedFrom(input))
    assertEquals(None, Timestamp.parseDelimitedFrom(input))
  }

  @Test
  def textFormatPrintsParsesAndReportsErrors(): Unit = {
    val timestamp = Timestamp(seconds = 17L, nanos = 250)
    val ascii = timestamp.toProtoString

    assertTrue(ascii.contains("seconds: 17"))
    assertTrue(ascii.contains("nanos: 250"))
    assertEquals(timestamp, Timestamp.fromAscii(ascii))
    assertEquals(Right(timestamp), TextFormat.fromAscii(Timestamp, ascii))

    val singleLine = TextFormat.printToSingleLineUnicodeString(timestamp)
    assertFalse(singleLine.contains("\n"))
    assertTrue(singleLine.contains("seconds: 17"))

    val unicode = TextFormat.printToUnicodeString(Value().withStringValue("scalaπb"))
    assertTrue(unicode.contains("scalaπb"))

    val parsedStruct = Struct.fromAscii("fields { key: \"answer\" value { number_value: 42.0 } }")
    assertEquals(42.0, parsedStruct.fields("answer").getNumberValue)

    val error = TextFormat.fromAscii(Timestamp, "seconds: \"not-a-number\"")
    assertTrue(error.isLeft)
    assertTrue(error.fold(_.msg.nonEmpty, _ => false))

    val thrown = assertThrows(
      classOf[TextFormatException],
      new Executable {
        override def execute(): Unit = Timestamp.fromAscii("seconds: \"not-a-number\"")
      }
    )
    assertFalse(thrown.getMessage.isEmpty)
  }

  @Test
  def wellKnownTypesExposeConvertersOrderingDescriptorsAndJavaInterop(): Unit = {
    import DurationConverters._
    import TimestampConverters._

    val instant = Instant.parse("2022-03-04T05:06:07.123456789Z")
    val timestamp: Timestamp = instant
    assertEquals(instant, timestamp.asJavaInstant)
    assertEquals(instant, Timestamp(instant).asJavaInstant)
    assertTrue(Timestamp.isValid(timestamp))
    assertEquals(0, Timestamp.ordering.compare(timestamp, Timestamp.checkValid(timestamp)))

    val javaDuration = JavaDuration.ofSeconds(3L).plusNanos(4L)
    val duration: Duration = javaDuration
    assertEquals(javaDuration, duration.asJavaDuration)
    assertEquals(javaDuration, Duration(javaDuration).asJavaDuration)
    assertTrue(Duration.isValid(duration))
    assertTrue(Duration.ordering.compare(Duration(seconds = 1), Duration(seconds = 2)) < 0)

    assertFalse(Timestamp.isValid(Timestamp(seconds = Timestamp.TIMESTAMP_SECONDS_MAX + 1, nanos = 0)))
    assertThrows(
      classOf[IllegalArgumentException],
      new Executable {
        override def execute(): Unit = Timestamp.checkValid(Timestamp(seconds = 0, nanos = -1))
      }
    )
    assertFalse(Duration.isValid(Duration(seconds = 1, nanos = -1)))

    val descriptor = Timestamp.scalaDescriptor
    assertEquals("google.protobuf.Timestamp", descriptor.fullName)
    assertEquals(Some("seconds"), descriptor.findFieldByNumber(1).map(_.name))
    assertEquals(Some(2), descriptor.findFieldByName("nanos").map(_.number))
    assertEquals("google/protobuf/timestamp.proto", Timestamp.scalaDescriptor.file.fullName)
    assertEquals(Timestamp.javaDescriptor.getFullName, descriptor.fullName)

    val javaTimestamp = Timestamp.toJavaProto(timestamp)
    assertEquals(timestamp, Timestamp.fromJavaProto(javaTimestamp))

    val struct = Struct(Map("nested" -> Value().withStructValue(Struct(Map("flag" -> Value().withBoolValue(true))))))
    val javaStruct = Struct.toJavaProto(struct)
    assertEquals(struct, Struct.fromJavaProto(javaStruct))
  }

  @Test
  def anyPackingAndFieldMasksUseGeneratedDescriptors(): Unit = {
    val timestamp = Timestamp(seconds = 99L, nanos = 12)
    val packed = ProtoAny.pack(timestamp)

    assertTrue(packed.is[Timestamp])
    assertFalse(packed.is[Duration])
    assertEquals(timestamp, packed.unpack[Timestamp])
    assertTrue(packed.typeUrl.endsWith("/google.protobuf.Timestamp"))

    val customPacked = ProtoAny.pack(timestamp, "example.com/protos")
    assertEquals("example.com/protos/google.protobuf.Timestamp", customPacked.typeUrl)
    assertThrows(
      classOf[IllegalArgumentException],
      new Executable {
        override def execute(): Unit = packed.unpack[Duration]
      }
    )

    val parsedMask = FieldMaskUtil.fromJsonString("seconds,nanos")
    assertEquals(FieldMask(Seq("seconds", "nanos")), parsedMask)
    assertEquals("seconds,nanos", FieldMaskUtil.toJsonString(parsedMask))
    assertEquals(Some(FieldMask(Seq("seconds", "nanos"))), FieldMaskUtil.fromFieldNumbers[Timestamp](1, 2))
    assertEquals(None, FieldMaskUtil.fromFieldNumbers[Timestamp](1, 99))
    assertTrue(FieldMaskUtil.containsFieldNumber[Timestamp](parsedMask, 1))
    assertFalse(FieldMaskUtil.containsFieldNumber[Timestamp](parsedMask, 99))
    assertTrue(FieldMaskUtil.isValid[Timestamp](parsedMask))
    assertFalse(FieldMaskUtil.isValid[Timestamp](FieldMask(Seq("seconds.value"))))

    val selectedMask = FieldMaskUtil.selectFieldNumbers[Timestamp](_ == 2)
    assertEquals(FieldMask(Seq("nanos")), selectedMask)

    val union = FieldMaskUtil.union(FieldMask(Seq("seconds")), FieldMask(Seq("nanos")))
    assertEquals(FieldMask(Seq("nanos", "seconds")), union)

    val masked = FieldMaskUtil.applyFieldMask(timestamp, FieldMask(Seq("seconds")))
    assertEquals(Timestamp(seconds = 99L), masked)
  }

  @Test
  def structUtilsConvertsGeneratedMessagesAndReportsTypeErrors(): Unit = {
    val timestamp = Timestamp(seconds = 123L, nanos = 456)
    val struct = StructUtils.toStruct(timestamp)

    assertEquals(Value().withStringValue("123"), struct.fields("seconds"))
    assertEquals(Value().withNumberValue(456.0), struct.fields("nanos"))
    assertEquals(Right(timestamp), StructUtils.fromStruct[Timestamp](struct))

    val list = Value().withListValue(ListValue(Seq(Value().withStringValue("first"), Value().withBoolValue(true))))
    val nested = Struct(Map("items" -> list, "child" -> Value().withStructValue(struct)))
    assertEquals(list, nested.fields("items"))
    assertEquals(struct, nested.fields("child").getStructValue)

    val invalidLong = Struct(Map("seconds" -> Value().withStringValue("abc")))
    val invalidResult = StructUtils.fromStruct[Timestamp](invalidLong)
    assertTrue(invalidResult.isLeft)
    assertTrue(invalidResult.fold(_.error.contains("invalid long"), _ => false))

    val invalidInt = Struct(Map("nanos" -> Value().withStringValue("not-an-int")))
    val invalidIntResult = StructUtils.fromStruct[Timestamp](invalidInt)
    assertTrue(invalidIntResult.isLeft)
    assertTrue(invalidIntResult.fold(_.error.contains("nanos"), _ => false))
  }

  @Test
  def unknownFieldsWireTypesEncodingAndGeneratedUnknownFieldAccessWork(): Unit = {
    val payload = ByteString.copyFromUtf8("opaque")
    val field = UnknownFieldSet.Field(
      varint = Vector(150L),
      fixed64 = Vector(0x0102030405060708L),
      fixed32 = Vector(0x11223344),
      lengthDelimited = Vector(payload)
    )
    val unknownFields = UnknownFieldSet.empty.withField(100, field)
    val withUnknown = Timestamp(seconds = 1L).withUnknownFields(unknownFields)
    val parsed = Timestamp.parseFrom(withUnknown.toByteArray)

    assertEquals(Some(field), parsed.unknownFields.getField(100))
    assertEquals(unknownFields.asMap, parsed.unknownFields.asMap)
    assertEquals(Timestamp(seconds = 1L), parsed.discardUnknownFields)
    assertEquals(parsed.toByteArray.length, parsed.serializedSize)

    val output = new ByteArrayOutputStream()
    val codedOutput = CodedOutputStream.newInstance(output)
    unknownFields.writeTo(codedOutput)
    codedOutput.flush()

    val reparsed = Timestamp.parseFrom(output.toByteArray)
    assertEquals(Some(field), reparsed.unknownFields.getField(100))

    val tag = (100 << 3) | WireType.WIRETYPE_LENGTH_DELIMITED
    assertEquals(100, WireType.getTagFieldNumber(tag))
    assertEquals(WireType.WIRETYPE_LENGTH_DELIMITED, WireType.getTagWireType(tag))
  }

  @Test
  def generatedEnumsExposeRecognizedUnrecognizedDescriptorsAndJavaInterop(): Unit = {
    val fieldType = FieldDescriptorProto.Type.TYPE_STRING

    assertEquals(9, fieldType.value)
    assertEquals("TYPE_STRING", fieldType.name)
    assertTrue(fieldType.isTypeString)
    assertFalse(fieldType.isUnrecognized)
    assertEquals(Some(fieldType), FieldDescriptorProto.Type.fromName("TYPE_STRING"))
    assertEquals(fieldType, FieldDescriptorProto.Type.fromValue(fieldType.value))
    assertEquals(Some(fieldType), fieldType.asRecognized)

    val descriptor = FieldDescriptorProto.Type.scalaDescriptor
    val descriptorValue = descriptor.findValueByNumber(fieldType.value)
    assertEquals("google.protobuf.FieldDescriptorProto.Type", descriptor.fullName)
    assertTrue(descriptorValue.isDefined)
    assertEquals("TYPE_STRING", descriptorValue.get.name)
    assertEquals("TYPE_STRING", fieldType.scalaValueDescriptor.name)
    assertEquals(fieldType.value, fieldType.scalaValueDescriptor.number)
    assertFalse(fieldType.scalaValueDescriptor.isUnrecognized)
    assertEquals(FieldDescriptorProto.Type.javaDescriptor.getFullName, descriptor.fullName)

    val javaType = FieldDescriptorProto.Type.toJavaValue(fieldType)
    assertEquals(JavaDescriptorProtos.FieldDescriptorProto.Type.TYPE_STRING, javaType)
    assertEquals(fieldType, FieldDescriptorProto.Type.fromJavaValue(javaType))

    val unrecognized = FieldDescriptorProto.Type.fromValue(123456)
    assertTrue(unrecognized.isUnrecognized)
    assertEquals(None, unrecognized.asRecognized)
    assertEquals(123456, unrecognized.value)

    val unrecognizedDescriptor = descriptor.findValueByNumberCreatingIfUnknown(123456)
    assertTrue(unrecognizedDescriptor.isUnrecognized)
    assertEquals(123456, unrecognizedDescriptor.number)
  }

  @Test
  def scalaPbCustomOptionsStoreAndReadGeneratedExtensions(): Unit = {
    val scalaPbOptions = ScalaPbOptions()
      .withPackageName("example.generated")
      .withFlatPackage(true)
      .withLenses(true)
    val fileOptions = DescriptorFileOptions()
      .withJavaPackage("example.java")
      .withExtension(ScalapbProto.options)(Some(scalaPbOptions))

    assertEquals("example.java", fileOptions.getJavaPackage)
    assertEquals(Some(scalaPbOptions), fileOptions.extension(ScalapbProto.options))

    val updatedOptions = scalaPbOptions.withFlatPackage(false).withObjectName("ExampleProto")
    val updatedFileOptions = fileOptions.withExtension(ScalapbProto.options)(Some(updatedOptions))
    assertEquals(Some(updatedOptions), updatedFileOptions.extension(ScalapbProto.options))
    assertEquals(Some(scalaPbOptions), fileOptions.extension(ScalapbProto.options))

    val clearedFileOptions = updatedFileOptions.withExtension(ScalapbProto.options)(None)
    assertEquals(None, clearedFileOptions.extension(ScalapbProto.options))

    val scalaPbFieldOptions = ScalaPbFieldOptions()
      .withScalaName("renamedField")
      .withNoBox(true)
      .withRequired(true)
    val descriptorFieldOptions = DescriptorFieldOptions()
      .withDeprecated(true)
      .withExtension(ScalapbProto.field)(Some(scalaPbFieldOptions))

    assertTrue(descriptorFieldOptions.getDeprecated)
    assertEquals(Some(scalaPbFieldOptions), descriptorFieldOptions.extension(ScalapbProto.field))
    assertEquals(None, DescriptorFieldOptions().extension(ScalapbProto.field))
  }

  @Test
  def typeMappersCollectionAdaptersBase64AndPValuesWorkTogether(): Unit = {
    val intMapper = implicitly[TypeMapper[Int32Value, Int]]
    assertEquals(42, intMapper.toCustom(Int32Value(42)))
    assertEquals(Int32Value(42), intMapper.toBase(42))

    val lengthMapper = TypeMapper.StringValueTypeMapper.map2[Int](_.length)(count => "x" * count)
    assertEquals(5, lengthMapper.toCustom(StringValue("hello")))
    assertEquals(StringValue("xxx"), lengthMapper.toBase(3))

    val adapter = implicitly[CollectionAdapter[Int, Vector[Int]]]
    assertEquals(Right(Vector(1, 2, 3)), adapter.fromIterator(Iterator(1, 2, 3)))
    assertEquals(Vector(1, 2, 3, 4), adapter.concat(Vector(1, 2), List(3, 4)))
    assertEquals(4, adapter.size(Vector(1, 2, 3, 4)))
    assertEquals(List(1, 2), adapter.toIterator(Vector(1, 2)).toList)
    assertEquals(Vector.empty[Int], adapter.empty)

    val bytes = Array[Byte](0, 1, 2, 3, 4, -1)
    val encoded = Encoding.toBase64(bytes)
    assertArrayEquals(bytes, Encoding.fromBase64(s"\n $encoded \t"))

    assertEquals(7, PInt(7).as[Int])
    assertEquals("scala", PString("scala").as[String])
    assertEquals(None, PEmpty.as[Option[String]])
    assertThrows(
      classOf[ReadsException],
      new Executable {
        override def execute(): Unit = PString("not-an-int").as[Int]
      }
    )

    val timestamp = Timestamp(seconds = 8L, nanos = 9)
    val pMessage = timestamp.toPMessage
    val secondsField = Timestamp.scalaDescriptor.findFieldByName("seconds").get
    assertEquals(8L, pMessage.value(secondsField).as[Long])
    assertEquals(timestamp, Timestamp.messageReads.read(PMessage(pMessage.value)))

    val booleanReads = scalapb.descriptors.Reads.booleanReads
    assertFalse(booleanReads.read(scalapb.descriptors.PBoolean(false)))

    val result = Timestamp.validate(timestamp.toByteArray)
    result match {
      case Success(value) => assertEquals(timestamp, value)
      case Failure(error) => fail(s"valid timestamp failed to parse: ${error.getMessage}")
    }
  }
}
