/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_thesamet_scalapb.scalapb_runtime_2_13

import com.google.protobuf.ByteString
import com.google.protobuf.CodedOutputStream
import com.google.protobuf.`type`.Field
import com.google.protobuf.`type`.Type
import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import scalapb.GeneratedEnum
import scalapb.GeneratedEnumCompanion

import java.io.ByteArrayOutputStream

/**
 * Exercises generated enum companions and open-enum evolution: `fromValue`, `fromName`,
 * `values`, the `isX` predicates, `Recognized` versus `Unrecognized` values, and their scala
 * value descriptors.
 *
 * A warehouse loader plans SQL columns from the `google.protobuf.Type` schemas that producers
 * publish. Operators override column types in a config file by enum name, the planner
 * resolves field kinds by number, and schemas from newer producers that use kinds this build
 * does not know are kept intact on the wire while the affected fields are quarantined.
 */
class ColumnPlannerEnumEvolutionTest {

  @Test
  def companionResolvesKindsByNameAndNumberInDeclarationOrder(): Unit = {
    val kinds: Seq[Field.Kind] = Field.Kind.values
    assertEquals(19, kinds.size)
    assertEquals(Field.Kind.TYPE_UNKNOWN, kinds.head)
    assertEquals(Field.Kind.TYPE_SINT64, kinds.last)
    kinds.zipWithIndex.foreach { case (kind: Field.Kind, position: Int) =>
      assertEquals(position, kind.index)
      assertEquals(kind, Field.Kind.fromValue(kind.value))
      assertEquals(Some(kind), Field.Kind.fromName(kind.name))
      assertFalse(kind.isUnrecognized)
      assertEquals(Some(kind), kind.asRecognized)
    }

    assertEquals(Field.Kind.TYPE_STRING, Field.Kind.fromValue(9))
    assertEquals("TYPE_STRING", Field.Kind.TYPE_STRING.toString)
    assertTrue(Field.Kind.fromValue(9).isTypeString)
    assertFalse(Field.Kind.fromValue(9).isTypeBytes)
    assertEquals(Some(Field.Kind.TYPE_INT64), Field.Kind.fromName("TYPE_INT64"))
    assertEquals(None, Field.Kind.fromName("TYPE_DATETIME"))
    assertEquals(None, Field.Kind.fromName("type_int64"))

    val config: Map[String, String] = Map("amount" -> "TYPE_SINT64", "note" -> "TYPE_BLOB")
    val overrides: Map[String, Either[String, Field.Kind]] =
      new ColumnPlanner(Field.Kind).parseOverrides(config)
    assertEquals(Right(Field.Kind.TYPE_SINT64), overrides("amount"))
    assertEquals(Left("unknown kind TYPE_BLOB for column note"), overrides("note"))

    assertEquals(
      Seq("CARDINALITY_UNKNOWN", "CARDINALITY_OPTIONAL", "CARDINALITY_REQUIRED",
        "CARDINALITY_REPEATED"),
      Field.Cardinality.values.map(_.name))
    assertTrue(Field.Cardinality.fromValue(3).isCardinalityRepeated)
  }

  @Test
  def scalaDescriptorsDescribeRecognizedKinds(): Unit = {
    assertEquals("google.protobuf.Field.Kind", Field.Kind.scalaDescriptor.fullName)
    assertEquals(19, Field.Kind.scalaDescriptor.values.size)
    assertEquals(Field.Kind.scalaDescriptor, Field.Kind.TYPE_BOOL.companion.scalaDescriptor)

    val bool: scalapb.descriptors.EnumValueDescriptor = Field.Kind.TYPE_BOOL.scalaValueDescriptor
    assertEquals("TYPE_BOOL", bool.name)
    assertEquals(8, bool.number)
    assertEquals(2, Field.Kind.TYPE_FLOAT.scalaValueDescriptor.index)
    assertEquals(Some(bool), Field.Kind.scalaDescriptor.findValueByNumber(8))
    assertEquals(None, Field.Kind.scalaDescriptor.findValueByNumber(42))
    assertEquals(Some(bool), Field.Kind.scalaDescriptor.values.find(_.name == "TYPE_BOOL"))
  }

  @Test
  def plannerMapsRecognizedKindsAndQuarantinesUnrecognizedOnes(): Unit = {
    val orders: Type = Type(
      name = "shop.Order",
      fields = Seq(
        field(1, "id", Field.Kind.TYPE_INT64),
        field(2, "customer", Field.Kind.TYPE_STRING),
        field(3, "paid", Field.Kind.TYPE_BOOL),
        field(4, "total", Field.Kind.TYPE_DOUBLE),
        field(5, "status", Field.Kind.TYPE_ENUM),
        field(6, "placed_on", Field.Kind.Unrecognized(42))))

    val plan: Plan = new ColumnPlanner(Field.Kind).plan(orders, Map("total" -> Field.Kind.TYPE_SINT64))
    assertEquals(
      Seq("id BIGINT", "customer VARCHAR", "paid BOOLEAN", "total BIGINT", "status VARCHAR"),
      plan.columns)
    assertEquals(Seq("placed_on (kind 42)"), plan.quarantined)

    val unknown: Field.Kind = Field.Kind.fromValue(42)
    assertEquals(Field.Kind.Unrecognized(42), unknown)
    assertTrue(unknown.isUnrecognized)
    assertFalse(unknown.isTypeString)
    assertEquals(None, unknown.asRecognized)
    assertEquals(42, unknown.value)
    assertEquals(-1, unknown.index)
    assertEquals("UNRECOGNIZED", unknown.name)
    assertEquals(42, unknown.scalaValueDescriptor.number)
    assertEquals(Field.Kind.scalaDescriptor, unknown.scalaValueDescriptor.containingEnum)
  }

  @Test
  def schemasFromNewerProducersKeepUnknownKindsAcrossReserialization(): Unit = {
    val published: Array[Byte] = newerProducerSchema()

    val parsed: Type = Type.parseFrom(published)
    assertEquals("shop.Shipment", parsed.name)
    assertEquals(Seq("tracking", "eta"), parsed.fields.map(_.name))
    assertEquals(Field.Kind.TYPE_STRING, parsed.fields.head.kind)
    assertEquals(Field.Kind.Unrecognized(23), parsed.fields(1).kind)
    assertEquals(Field.Cardinality.Unrecognized(7), parsed.fields(1).cardinality)
    assertTrue(parsed.fields(1).unknownFields.asMap.isEmpty)

    val renamed: Type = parsed.withName("shop.ShipmentV2")
    val forwarded: Type = Type.parseFrom(renamed.toByteArray)
    assertEquals(23, forwarded.fields(1).kind.value)
    assertEquals(7, forwarded.fields(1).cardinality.value)
    assertArrayEquals(published.drop(15), renamed.toByteArray.drop(17))

    val repaired: Type = forwarded.update(
      _.fields(1).kind := Field.Kind.TYPE_STRING,
      _.fields(1).cardinality := Field.Cardinality.CARDINALITY_OPTIONAL)
    val plan: Plan = new ColumnPlanner(Field.Kind).plan(repaired, Map.empty)
    assertEquals(Seq("tracking VARCHAR", "eta VARCHAR"), plan.columns)
    assertTrue(plan.quarantined.isEmpty)
  }

  private def field(number: Int, name: String, kind: Field.Kind): Field =
    Field(kind = kind, cardinality = Field.Cardinality.CARDINALITY_OPTIONAL, number = number,
      name = name)

  /** Hand-encodes a `Type` the way a producer built against a newer `type.proto` would. */
  private def newerProducerSchema(): Array[Byte] = {
    val tracking: Array[Byte] = encodeField(kind = 9, cardinality = 1, number = 1, name = "tracking")
    val eta: Array[Byte] = encodeField(kind = 23, cardinality = 7, number = 2, name = "eta")
    val bytes: ByteArrayOutputStream = new ByteArrayOutputStream()
    val output: CodedOutputStream = CodedOutputStream.newInstance(bytes)
    output.writeString(1, "shop.Shipment")
    output.writeBytes(2, ByteString.copyFrom(tracking))
    output.writeBytes(2, ByteString.copyFrom(eta))
    output.flush()
    bytes.toByteArray
  }

  private def encodeField(kind: Int, cardinality: Int, number: Int, name: String): Array[Byte] = {
    val bytes: ByteArrayOutputStream = new ByteArrayOutputStream()
    val output: CodedOutputStream = CodedOutputStream.newInstance(bytes)
    output.writeEnum(1, kind)
    output.writeEnum(2, cardinality)
    output.writeInt32(3, number)
    output.writeString(4, name)
    output.flush()
    bytes.toByteArray
  }

  /** Outcome of planning one schema: column definitions and the fields left out of them. */
  final case class Plan(columns: Seq[String], quarantined: Seq[String])

  /** Plans warehouse columns for any enum companion the loader is configured with. */
  final class ColumnPlanner[E <: GeneratedEnum](kinds: GeneratedEnumCompanion[E]) {

    def parseOverrides(config: Map[String, String]): Map[String, Either[String, E]] =
      config.map { case (column: String, kindName: String) =>
        column -> kinds.fromName(kindName).toRight(s"unknown kind $kindName for column $column")
      }

    def plan(schema: Type, overrides: Map[String, Field.Kind]): Plan = {
      val (known: Seq[Field], unknown: Seq[Field]) =
        schema.fields.partition(f => overrides.getOrElse(f.name, f.kind).asRecognized.isDefined)
      Plan(
        known.map(f => s"${f.name} ${sqlType(overrides.getOrElse(f.name, f.kind))}"),
        unknown.map(f => s"${f.name} (kind ${f.kind.value})"))
    }

    private def sqlType(kind: Field.Kind): String = kind match {
      case Field.Kind.TYPE_INT64 | Field.Kind.TYPE_SINT64 | Field.Kind.TYPE_UINT64 => "BIGINT"
      case Field.Kind.TYPE_DOUBLE | Field.Kind.TYPE_FLOAT => "DOUBLE"
      case Field.Kind.TYPE_BOOL => "BOOLEAN"
      case Field.Kind.TYPE_BYTES => "VARBINARY"
      case k if k.isTypeString || k.isTypeEnum => "VARCHAR"
      case Field.Kind.Unrecognized(value) => throw new IllegalArgumentException(s"kind $value")
      case other: Field.Kind.Recognized => s"JSON /* ${other.name} */"
    }
  }
}
