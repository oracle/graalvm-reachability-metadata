/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package io_scalaland.chimney_3

import io.scalaland.chimney.Codec
import io.scalaland.chimney.Iso
import io.scalaland.chimney.PartialTransformer
import io.scalaland.chimney.Transformer
import io.scalaland.chimney.dsl.*
import io.scalaland.chimney.partial
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * Bidirectional mappings with `Iso` and `Codec`, derived and defined with renames and wrappers.
 *
 * A customer service stores its domain model in a relational table and exposes it over a JSON
 * API. The storage mapping is lossless, so it is an `Iso` whose two transformers are derived
 * together and used implicitly in both directions. The API mapping is a `Codec`: encoding a
 * domain order never fails, while decoding an incoming DTO is partial and reports missing
 * fields with their paths. Nested codecs are picked up from scope by the outer derivation.
 */
class CustomerRecordIsoCodecRoundTripTest {

  final case class CustomerId(value: Long)
  final case class Customer(id: CustomerId, fullName: String, emails: List[String], vip: Boolean)
  final case class CustomerRow(id: Long, name: String, emails: Vector[String], vip: Boolean)

  final case class Point(x: Int, y: Int)
  final case class PointRow(x: Int, y: Int)

  final case class Sku(code: String)
  final case class OrderLine(sku: Sku, quantity: Int)
  final case class Order(id: Long, customer: CustomerId, lines: List[OrderLine], note: Option[String], email: String)

  final case class OrderLineDto(productCode: String, quantity: Int)
  final case class OrderDto(
      id: Long,
      customer: Long,
      lines: Vector[OrderLineDto],
      note: Option[String],
      email: Option[String]
  )

  /**
   * The table column is called `name`, so the rename applies to reading and writing alike, and
   * the `CustomerId` wrapper is unwrapped into the `id` column and wrapped again on load.
   */
  given Iso[Customer, CustomerRow] = Iso
    .define[Customer, CustomerRow]
    .withFieldRenamed(_.fullName, _.name)
    .enableNonAnyValWrappers
    .buildIso

  /** Line items expose the SKU as `productCode` on the wire. */
  given Codec[OrderLine, OrderLineDto] = Codec
    .define[OrderLine, OrderLineDto]
    .withFieldRenamed(_.sku, _.productCode)
    .enableNonAnyValWrappers
    .buildCodec

  /** The order codec reuses the line codec for every element of `lines`. */
  given Codec[Order, OrderDto] = Codec.define[Order, OrderDto].enableNonAnyValWrappers.buildCodec

  @Test
  def storesAndLoadsCustomerThroughIsoInBothDirections(): Unit = {
    val customer: Customer =
      Customer(CustomerId(42L), "Grace Hopper", List("grace@navy.mil", "gh@example.org"), vip = true)

    val row: CustomerRow = customer.transformInto[CustomerRow]
    assertEquals(CustomerRow(42L, "Grace Hopper", Vector("grace@navy.mil", "gh@example.org"), vip = true), row)

    val loaded: Customer = row.transformInto[Customer]
    assertEquals(customer, loaded)

    val iso: Iso[Customer, CustomerRow] = summon[Iso[Customer, CustomerRow]]
    val renamed: CustomerRow = iso.first.transform(customer.copy(fullName = "Rear Admiral Hopper"))
    assertEquals("Rear Admiral Hopper", renamed.name)
    assertEquals(customer.copy(vip = false), iso.second.transform(row.copy(vip = false)))
  }

  @Test
  def derivedIsoMapsMatchingShapesWithoutConfiguration(): Unit = {
    val iso: Iso[Point, PointRow] = Iso.derive[Point, PointRow]

    val stored: List[PointRow] = List(Point(1, 2), Point(-3, 4)).map(iso.first.transform)
    assertEquals(List(PointRow(1, 2), PointRow(-3, 4)), stored)
    assertEquals(List(Point(1, 2), Point(-3, 4)), stored.map(iso.second.transform))
  }

  @Test
  def encodesOrderIntoDtoAndDecodesItBack(): Unit = {
    val order: Order = Order(
      id = 7L,
      customer = CustomerId(42L),
      lines = List(OrderLine(Sku("BOOK-1"), 2), OrderLine(Sku("PEN-9"), 10)),
      note = None,
      email = "grace@navy.mil"
    )

    val dto: OrderDto = order.transformInto[OrderDto]
    assertEquals(
      OrderDto(7L, 42L, Vector(OrderLineDto("BOOK-1", 2), OrderLineDto("PEN-9", 10)), None, Some("grace@navy.mil")),
      dto
    )

    val decoded: partial.Result[Order] = dto.transformIntoPartial[Order]
    assertEquals(Right(order), decoded.asEither)

    val codec: Codec[Order, OrderDto] = summon[Codec[Order, OrderDto]]
    assertEquals(dto, codec.encode.transform(order))
    assertEquals(Some(order), codec.decode.transform(dto).asOption)
  }

  @Test
  def decodingReportsMissingFieldsWithTheirPaths(): Unit = {
    val incoming: OrderDto = OrderDto(8L, 43L, Vector(OrderLineDto("MUG-3", 1)), Some("gift wrap"), None)

    val decoded: partial.Result[Order] = incoming.transformIntoPartial[Order]
    assertTrue(decoded.asOption.isEmpty)
    assertEquals(List("email" -> "empty value"), decoded.asErrorPathMessageStrings.toList)

    val fixed: partial.Result[Order] = incoming.copy(email = Some("buyer@example.org")).transformIntoPartial[Order]
    assertEquals(
      Right(Order(8L, CustomerId(43L), List(OrderLine(Sku("MUG-3"), 1)), Some("gift wrap"), "buyer@example.org")),
      fixed.asEither
    )
  }

  @Test
  def codecHalvesComposeWithOtherTransformersInAServiceLayer(): Unit = {
    val encode: Transformer[OrderLine, OrderLineDto] = summon[Codec[OrderLine, OrderLineDto]].encode
    val decode: PartialTransformer[OrderLineDto, OrderLine] = summon[Codec[OrderLine, OrderLineDto]].decode

    val wire: List[OrderLineDto] = List(OrderLine(Sku("A"), 1), OrderLine(Sku("B"), 3)).map(encode.transform)
    assertEquals(List("A", "B"), wire.map(_.productCode))

    val restocked: partial.Result[List[OrderLine]] =
      partial.Result.traverse[List[OrderLine], OrderLineDto, OrderLine](
        wire.map(line => line.copy(quantity = line.quantity * 2)).iterator,
        line => decode.transform(line),
        failFast = false
      )
    assertEquals(Right(List(OrderLine(Sku("A"), 2), OrderLine(Sku("B"), 6))), restocked.asEither)
  }
}
