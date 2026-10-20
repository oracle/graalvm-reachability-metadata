/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_thesamet_scalapb.scalapb_runtime_2_13

import com.google.protobuf.any.{Any => ProtoAny}
import com.google.protobuf.duration.Duration
import com.google.protobuf.field_mask.FieldMask
import com.google.protobuf.struct.ListValue
import com.google.protobuf.struct.NullValue
import com.google.protobuf.struct.Struct
import com.google.protobuf.struct.Value
import com.google.protobuf.timestamp.Timestamp
import com.google.protobuf.wrappers.BoolValue
import com.google.protobuf.wrappers.BytesValue
import com.google.protobuf.wrappers.Int64Value
import com.google.protobuf.wrappers.StringValue
import com.google.protobuf.{Any => JAny}
import com.google.protobuf.ByteString
import com.google.protobuf.{BytesValue => JBytesValue}
import com.google.protobuf.{Duration => JDuration}
import com.google.protobuf.{FieldMask => JFieldMask}
import com.google.protobuf.{Int64Value => JInt64Value}
import com.google.protobuf.{ListValue => JListValue}
import com.google.protobuf.{Message => JMessage}
import com.google.protobuf.{Struct => JStruct}
import com.google.protobuf.{Timestamp => JTimestamp}
import com.google.protobuf.{Value => JValue}
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import scalapb.GeneratedMessage
import scalapb.GeneratedMessageCompanion
import scalapb.JavaProtoSupport

import scala.jdk.CollectionConverters._

/**
 * Exercises interop between ScalaPB well-known types and their protobuf-java counterparts
 * through `JavaProtoSupport` (`toJavaProto`, `fromJavaProto`, `javaDescriptor`).
 *
 * A Scala feature-flag service sits next to a legacy Java admin console that speaks the
 * protobuf-java classes. The bridge converts flag documents, audit envelopes, wrapper-typed
 * overrides and read masks between the two worlds, and both sides must agree on the
 * descriptors and the bytes they put on the wire.
 */
class LegacyBridgeJavaProtoInteropTest {

  @Test
  def flagDocumentsSurviveAnEditInTheLegacyConsole(): Unit = {
    val bridge: ConsoleBridge = new ConsoleBridge
    val document: Struct = Struct(
      Map(
        "name" -> Value(Value.Kind.StringValue("checkout-v2")),
        "enabled" -> Value(Value.Kind.BoolValue(false)),
        "rollout" -> Value(Value.Kind.NumberValue(12.5)),
        "owner" -> Value(Value.Kind.NullValue(NullValue.NULL_VALUE)),
        "regions" -> Value(
          Value.Kind.ListValue(
            ListValue(Seq(Value(Value.Kind.StringValue("eu-west")), Value(Value.Kind.StringValue("us-east"))))
          )
        ),
        "limits" -> Value(
          Value.Kind.StructValue(Struct(Map("qps" -> Value(Value.Kind.NumberValue(250.0)))))
        )
      )
    )

    val javaDocument: JStruct = bridge.toConsole(document)
    val fields: java.util.Map[String, JValue] = javaDocument.getFieldsMap
    assertEquals(6, fields.size())
    assertEquals("checkout-v2", fields.get("name").getStringValue)
    assertEquals(JValue.KindCase.NULL_VALUE, fields.get("owner").getKindCase)
    assertEquals(12.5, fields.get("rollout").getNumberValue)
    assertEquals(
      List("eu-west", "us-east"),
      fields.get("regions").getListValue.getValuesList.asScala.map(_.getStringValue).toList
    )
    assertEquals(250.0, fields.get("limits").getStructValue.getFieldsOrThrow("qps").getNumberValue)

    val edited: JStruct = javaDocument.toBuilder
      .putFields("enabled", JValue.newBuilder().setBoolValue(true).build())
      .putFields("rollout", JValue.newBuilder().setNumberValue(50.0).build())
      .putFields(
        "regions",
        JValue
          .newBuilder()
          .setListValue(
            JListValue
              .newBuilder()
              .addValues(JValue.newBuilder().setStringValue("eu-west"))
              .addValues(JValue.newBuilder().setStringValue("us-east"))
              .addValues(JValue.newBuilder().setStringValue("ap-south"))
          )
          .build()
      )
      .removeFields("owner")
      .build()

    val updated: Struct = bridge.fromConsole(edited)
    assertEquals(Some(Value(Value.Kind.BoolValue(true))), updated.fields.get("enabled"))
    assertEquals(Some(50.0), updated.fields.get("rollout").flatMap(_.kind.numberValue))
    assertFalse(updated.fields.contains("owner"))
    assertEquals(
      Seq("eu-west", "us-east", "ap-south"),
      updated.fields("regions").getListValue.values.flatMap(_.kind.stringValue)
    )
    assertEquals(document.fields("limits"), updated.fields("limits"))
    assertEquals(document.fields("name"), updated.fields("name"))

    assertEquals(document, bridge.fromConsole(bridge.toConsole(document)))
  }

  @Test
  def bothRuntimesReadEachOthersBytesForTimeTypes(): Unit = {
    val changedAt: Timestamp = Timestamp(seconds = 1767225600L, nanos = 987654321)
    val cooldown: Duration = Duration(seconds = 900L, nanos = 5000)

    val javaChangedAt: JTimestamp = JTimestamp.parseFrom(changedAt.toByteArray)
    assertEquals(1767225600L, javaChangedAt.getSeconds)
    assertEquals(987654321, javaChangedAt.getNanos)
    assertEquals(javaChangedAt, Timestamp.toJavaProto(changedAt))
    assertEquals(changedAt, Timestamp.fromJavaProto(javaChangedAt))

    val javaCooldown: JDuration = JDuration.newBuilder().setSeconds(900L).setNanos(5000).build()
    assertEquals(cooldown, Duration.parseFrom(javaCooldown.toByteArray))
    assertEquals(cooldown, Duration.fromJavaProto(javaCooldown))
    assertEquals(javaCooldown.toByteString, Duration.toJavaProto(cooldown).toByteString)
  }

  @Test
  def wrapperTypedOverridesKeepPresenceAcrossTheBridge(): Unit = {
    val maxRetries: Int64Value = Int64Value(Long.MaxValue)
    val javaRetries: JInt64Value = Int64Value.toJavaProto(maxRetries)
    assertEquals(Long.MaxValue, javaRetries.getValue)
    assertEquals(maxRetries, Int64Value.fromJavaProto(JInt64Value.parseFrom(javaRetries.toByteArray)))

    val zeroRetries: Int64Value = Int64Value.fromJavaProto(JInt64Value.getDefaultInstance)
    assertEquals(Int64Value.defaultInstance, zeroRetries)
    assertEquals(0L, zeroRetries.value)

    assertEquals("blue", StringValue.toJavaProto(StringValue("blue")).getValue)
    assertTrue(BoolValue.toJavaProto(BoolValue(true)).getValue)
    val salt: Array[Byte] = Array[Byte](0, 1, 127, -128, -1)
    val javaSalt: JBytesValue =
      BytesValue.toJavaProto(BytesValue(ByteString.copyFrom(salt)))
    assertTrue(java.util.Arrays.equals(salt, javaSalt.getValue.toByteArray))
    assertEquals(salt.toSeq, BytesValue.fromJavaProto(javaSalt).value.toByteArray.toSeq)
  }

  @Test
  def auditEnvelopesPackedInJavaUnpackInScala(): Unit = {
    val javaStamp: JTimestamp = JTimestamp.newBuilder().setSeconds(1700000000L).setNanos(42).build()
    val javaEnvelope: JAny = JAny.pack(javaStamp)
    assertEquals("type.googleapis.com/google.protobuf.Timestamp", javaEnvelope.getTypeUrl)

    val envelope: ProtoAny = ProtoAny.fromJavaProto(javaEnvelope)
    assertEquals(javaEnvelope.getTypeUrl, envelope.typeUrl)
    assertTrue(envelope.is[Timestamp])
    assertFalse(envelope.is[Duration])
    assertEquals(Timestamp(seconds = 1700000000L, nanos = 42), envelope.unpack[Timestamp])

    val reply: ProtoAny = ProtoAny.pack(Duration(seconds = 30L))
    val javaReply: JAny = ProtoAny.toJavaProto(reply)
    assertTrue(javaReply.is(classOf[JDuration]))
    assertEquals(30L, javaReply.unpack(classOf[JDuration]).getSeconds)
  }

  @Test
  def readMasksAndDescriptorsAgreeBetweenRuntimes(): Unit = {
    val mask: FieldMask = FieldMask(Seq("name", "limits.qps", "regions"))
    val javaMask: JFieldMask = FieldMask.toJavaProto(mask)
    assertEquals(List("name", "limits.qps", "regions"), javaMask.getPathsList.asScala.toList)
    assertEquals(mask, FieldMask.fromJavaProto(javaMask.toBuilder.build()))

    val bridged: List[DescriptorSummary] = List(
      DescriptorSummary.of(Struct),
      DescriptorSummary.of(Value),
      DescriptorSummary.of(Timestamp),
      DescriptorSummary.of(FieldMask),
      DescriptorSummary.of(ProtoAny)
    )
    bridged.foreach { summary =>
      assertEquals(summary.scalaFullName, summary.javaFullName)
      assertEquals(summary.scalaFields, summary.javaFields)
    }
    assertEquals(
      List("google.protobuf.Struct", "google.protobuf.Value", "google.protobuf.Timestamp",
        "google.protobuf.FieldMask", "google.protobuf.Any"),
      bridged.map(_.javaFullName)
    )
    assertEquals(
      List("null_value", "number_value", "string_value", "bool_value", "struct_value", "list_value"),
      bridged(1).javaFields
    )
  }

  /** Converts flag documents between the Scala service and the Java admin console. */
  private final class ConsoleBridge {
    def toConsole(document: Struct): JStruct = Struct.toJavaProto(document)

    def fromConsole(document: JStruct): Struct = Struct.fromJavaProto(document)
  }

  /** Names and field lists one message type has in each runtime's descriptor. */
  private final class DescriptorSummary(
      val scalaFullName: String,
      val javaFullName: String,
      val scalaFields: List[String],
      val javaFields: List[String]
  )

  private object DescriptorSummary {
    def of[S <: GeneratedMessage, J <: JMessage](
        companion: GeneratedMessageCompanion[S] with JavaProtoSupport[S, J]
    ): DescriptorSummary =
      new DescriptorSummary(
        companion.scalaDescriptor.fullName,
        companion.javaDescriptor.getFullName,
        companion.scalaDescriptor.fields.map(_.name).toList,
        companion.javaDescriptor.getFields.asScala.map(_.getName).toList
      )
  }
}
