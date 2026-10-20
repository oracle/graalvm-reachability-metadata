/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_thesamet_scalapb.scalapb_runtime_2_13

import com.google.protobuf.ByteString
import com.google.protobuf.wrappers.BoolValue
import com.google.protobuf.wrappers.BytesValue
import com.google.protobuf.wrappers.DoubleValue
import com.google.protobuf.wrappers.FloatValue
import com.google.protobuf.wrappers.Int32Value
import com.google.protobuf.wrappers.Int64Value
import com.google.protobuf.wrappers.StringValue
import com.google.protobuf.wrappers.UInt32Value
import com.google.protobuf.wrappers.UInt64Value
import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test
import scalapb.GeneratedMessage
import scalapb.GeneratedMessageCompanion
import scalapb.TypeMapper

/**
 * Exercises custom type mapping through `scalapb.TypeMapper`: the built-in mappers for the
 * wrapper well-known types resolved implicitly, domain mappers built with `TypeMapper.apply`
 * and `map2`, and their use to encode and decode wrapper messages on the wire.
 *
 * A pricing service keeps its price book in a key-value store whose cells hold serialized
 * protobuf wrapper messages, exactly as fields annotated with `(scalapb.field).type` would be
 * stored. The service code never touches the wrappers directly: each column has a `TypeMapper`
 * from its wrapper type to a domain value class, and a small codec turns a `PriceEntry` into a
 * row of wrapper bytes and back, validating values as they are mapped in.
 */
class PriceBookTypeMapperTest {
  case class Sku(code: String) {
    require(code.matches("[A-Z0-9-]+"), s"malformed sku $code")
  }

  case class Cents(amount: Long) {
    require(amount >= 0, s"negative price $amount")
  }

  case class BasisPoints(value: Int)

  case class PriceEntry(
      sku: Sku, price: Cents, discount: Option[BasisPoints], active: Boolean)

  /** Column codec: one `TypeMapper` per column, from its wrapper message to a domain type. */
  object PriceCodec {
    implicit val skuMapper: TypeMapper[StringValue, Sku] =
      implicitly[TypeMapper[StringValue, String]].map2(Sku(_))(_.code)

    implicit val centsMapper: TypeMapper[Int64Value, Cents] =
      implicitly[TypeMapper[Int64Value, Long]].map2(Cents(_))(_.amount)

    implicit val discountMapper: TypeMapper[Int32Value, BasisPoints] =
      TypeMapper[Int32Value, BasisPoints](w => BasisPoints(w.value))(b => Int32Value(b.value))

    def encode(entry: PriceEntry): Map[String, Array[Byte]] =
      Map(
        "sku" -> cell(entry.sku),
        "price" -> cell(entry.price),
        "active" -> cell[BoolValue, Boolean](entry.active)
      ) ++ entry.discount.map(d => "discount" -> cell(d))

    def decode(row: Map[String, Array[Byte]]): PriceEntry =
      PriceEntry(
        sku = read[StringValue, Sku](row("sku")),
        price = read[Int64Value, Cents](row("price")),
        discount = row.get("discount").map(read[Int32Value, BasisPoints]),
        active = read[BoolValue, Boolean](row("active"))
      )

    private def cell[B <: GeneratedMessage, C](value: C)(implicit
        mapper: TypeMapper[B, C]): Array[Byte] =
      mapper.toBase(value).toByteArray

    private def read[B <: GeneratedMessage, C](bytes: Array[Byte])(implicit
        companion: GeneratedMessageCompanion[B], mapper: TypeMapper[B, C]): C =
      mapper.toCustom(companion.parseFrom(bytes))
  }

  @Test
  def builtInWrapperMappersAreResolvedImplicitly(): Unit = {
    assertEquals(42, roundTrip[Int32Value, Int](42))
    assertEquals(-7L, roundTrip[Int64Value, Long](-7L))
    assertEquals(4000000000L, roundTrip[UInt64Value, Long](4000000000L))
    assertEquals(-1, roundTrip[UInt32Value, Int](-1))
    assertEquals(true, roundTrip[BoolValue, Boolean](true))
    assertEquals(0.25, roundTrip[DoubleValue, Double](0.25))
    assertEquals(1.5f, roundTrip[FloatValue, Float](1.5f))
    assertEquals("EUR", roundTrip[StringValue, String]("EUR"))
    val payload: ByteString = ByteString.copyFrom(Array[Byte](1, 2, 3))
    assertEquals(payload, roundTrip[BytesValue, ByteString](payload))

    assertEquals(Int32Value(42), implicitly[TypeMapper[Int32Value, Int]].toBase(42))
    assertEquals("EUR", TypeMapper.StringValueTypeMapper.toCustom(StringValue("EUR")))
  }

  @Test
  def priceEntriesAreStoredAsWrapperCells(): Unit = {
    val entry: PriceEntry = PriceEntry(
      sku = Sku("KB-104"),
      price = Cents(1999L),
      discount = Some(BasisPoints(1250)),
      active = true
    )

    val row: Map[String, Array[Byte]] = PriceCodec.encode(entry)

    assertEquals(Set("sku", "price", "discount", "active"), row.keySet)
    assertArrayEquals(StringValue("KB-104").toByteArray, row("sku"))
    assertArrayEquals(Int64Value(1999L).toByteArray, row("price"))
    assertArrayEquals(Int32Value(1250).toByteArray, row("discount"))
    assertEquals(Int64Value(1999L), Int64Value.parseFrom(row("price")))
    assertEquals(entry, PriceCodec.decode(row))
  }

  @Test
  def absentAndDefaultValuesMapToDomainDefaults(): Unit = {
    val entry: PriceEntry = PriceEntry(
      sku = Sku("FREE-SAMPLE"),
      price = Cents(0L),
      discount = None,
      active = false
    )

    val row: Map[String, Array[Byte]] = PriceCodec.encode(entry)

    assertFalse(row.contains("discount"))
    assertEquals(0, row("price").length)
    assertEquals(0, row("active").length)
    assertEquals(entry, PriceCodec.decode(row))
    assertEquals(Cents(0L), PriceCodec.centsMapper.toCustom(Int64Value.defaultInstance))
  }

  @Test
  def mapperChainsComposeAndValidateIncomingValues(): Unit = {
    val asEuros: TypeMapper[Int64Value, BigDecimal] =
      PriceCodec.centsMapper.map2(c => BigDecimal(c.amount) / 100)(
        e => Cents((e * 100).toLongExact))

    assertEquals(BigDecimal("19.99"), asEuros.toCustom(Int64Value(1999L)))
    assertEquals(Int64Value(2550L), asEuros.toBase(BigDecimal("25.50")))

    val corrupted: Map[String, Array[Byte]] =
      PriceCodec.encode(PriceEntry(Sku("KB-104"), Cents(500L), None, active = true)) +
        ("price" -> Int64Value(-500L).toByteArray)
    val rejected: IllegalArgumentException =
      assertThrows(classOf[IllegalArgumentException], () => PriceCodec.decode(corrupted))
    assertEquals("requirement failed: negative price -500", rejected.getMessage)

    val badSku: Map[String, Array[Byte]] =
      corrupted + ("price" -> Int64Value(500L).toByteArray) +
        ("sku" -> StringValue("kb 104").toByteArray)
    assertThrows(classOf[IllegalArgumentException], () => PriceCodec.decode(badSku))

    val upperCased: TypeMapper[StringValue, Sku] =
      TypeMapper[StringValue, String](_.value.toUpperCase)(StringValue(_)).map2(Sku(_))(_.code)
    assertEquals(Sku("KB-104"), upperCased.toCustom(StringValue("kb-104")))
    assertEquals(StringValue("KB-104"), upperCased.toBase(Sku("KB-104")))
  }

  private def roundTrip[B <: GeneratedMessage, C](value: C)(implicit
      companion: GeneratedMessageCompanion[B], mapper: TypeMapper[B, C]): C =
    mapper.toCustom(companion.parseFrom(mapper.toBase(value).toByteArray))
}
