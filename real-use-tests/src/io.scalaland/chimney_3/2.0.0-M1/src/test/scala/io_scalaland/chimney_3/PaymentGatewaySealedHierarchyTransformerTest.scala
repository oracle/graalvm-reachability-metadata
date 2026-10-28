/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package io_scalaland.chimney_3

import io.scalaland.chimney.PartialTransformer
import io.scalaland.chimney.Transformer
import io.scalaland.chimney.dsl.*
import io.scalaland.chimney.partial
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

/**
 * Sealed trait (coproduct) transformations with subtype renaming, handling and name comparison.
 *
 * A payment gateway adapter keeps orders in a domain model whose payment method and status are
 * sealed hierarchies, and exchanges them with a public API that models both as sealed
 * hierarchies of its own, spelled in upper case. Chimney matches subtypes by name, compared
 * case-insensitively here, so most cases map on their own: the outbound mapping renames the bank
 * transfer case with `withSealedSubtypeRenamed` and folds vouchers the API does not know into a
 * generic case with `withSealedSubtypeHandled`. The inbound mapping is partial and rejects the
 * generic API case with `withSealedSubtypeHandledPartial`, since the domain cannot represent it.
 */
class PaymentGatewaySealedHierarchyTransformerTest {

  sealed trait PaymentMethod
  final case class Card(number: String, holder: String) extends PaymentMethod
  final case class BankTransfer(iban: String) extends PaymentMethod
  case object Cash extends PaymentMethod
  final case class Voucher(code: String, amountCents: Long) extends PaymentMethod

  sealed trait OrderStatus
  case object Pending extends OrderStatus
  case object Paid extends OrderStatus
  case object Shipped extends OrderStatus
  case object Cancelled extends OrderStatus

  final case class Order(id: String, status: OrderStatus, payment: PaymentMethod, history: List[OrderStatus])

  /** The public API spells its cases the way they travel on the wire, in upper case. */
  sealed trait PaymentMethodApi
  final case class CARD(number: String, holder: String) extends PaymentMethodApi
  final case class WIRE(iban: String) extends PaymentMethodApi
  case object CASH extends PaymentMethodApi
  final case class OTHER(description: String) extends PaymentMethodApi

  sealed trait StatusApi
  case object PENDING extends StatusApi
  case object PAID extends StatusApi
  case object SHIPPED extends StatusApi
  case object CANCELLED extends StatusApi

  final case class OrderApi(id: String, status: StatusApi, payment: PaymentMethodApi, history: Vector[StatusApi])

  /** Outbound payment methods: names match ignoring case, transfers are renamed, vouchers summarised. */
  given Transformer[PaymentMethod, PaymentMethodApi] = Transformer
    .define[PaymentMethod, PaymentMethodApi]
    .withSealedSubtypeRenamed[BankTransfer, WIRE]
    .withSealedSubtypeHandled[Voucher](voucher => OTHER(s"voucher ${voucher.code} worth ${voucher.amountCents} cents"))
    .enableCustomSubtypeNameComparison(TransformedNamesComparison.CaseInsensitiveEquality)
    .buildTransformer

  /** Inbound payment methods: the generic API case has no domain counterpart and is rejected. */
  given PartialTransformer[PaymentMethodApi, PaymentMethod] = PartialTransformer
    .define[PaymentMethodApi, PaymentMethod]
    .withSealedSubtypeRenamed[WIRE, BankTransfer]
    .withSealedSubtypeHandledPartial[OTHER](other =>
      partial.Result.fromErrorString(s"unsupported payment method: ${other.description}")
    )
    .enableCustomSubtypeNameComparison(TransformedNamesComparison.CaseInsensitiveEquality)
    .buildTransformer

  /** Outbound orders reuse the payment mapping and compare status names case-insensitively. */
  given Transformer[Order, OrderApi] = Transformer
    .define[Order, OrderApi]
    .enableCustomSubtypeNameComparison(TransformedNamesComparison.CaseInsensitiveEquality)
    .buildTransformer

  /** Inbound orders fail when their payment method cannot be represented in the domain. */
  given PartialTransformer[OrderApi, Order] = PartialTransformer
    .define[OrderApi, Order]
    .enableCustomSubtypeNameComparison(TransformedNamesComparison.CaseInsensitiveEquality)
    .buildTransformer

  @Test
  def publishesEveryPaymentMethodThroughTheApiHierarchy(): Unit = {
    val orders: List[Order] = List(
      Order("O-1", Paid, Card("4111-1111", "Ada Lovelace"), List(Pending)),
      Order("O-2", Pending, BankTransfer("DE89370400440532013000"), Nil),
      Order("O-3", Shipped, Cash, List(Pending, Paid)),
      Order("O-4", Cancelled, Voucher("SPRING-24", 1500L), List(Pending))
    )

    val published: List[OrderApi] = orders.transformInto[List[OrderApi]]

    assertEquals(
      List(
        OrderApi("O-1", PAID, CARD("4111-1111", "Ada Lovelace"), Vector(PENDING)),
        OrderApi("O-2", PENDING, WIRE("DE89370400440532013000"), Vector.empty),
        OrderApi("O-3", SHIPPED, CASH, Vector(PENDING, PAID)),
        OrderApi("O-4", CANCELLED, OTHER("voucher SPRING-24 worth 1500 cents"), Vector(PENDING))
      ),
      published
    )
  }

  @Test
  def acceptsInboundOrdersWhoseCasesTheDomainKnows(): Unit = {
    val request: OrderApi =
      OrderApi("O-9", SHIPPED, WIRE("PL61109010140000071219812874"), Vector(PENDING, PAID))

    val result: partial.Result[Order] = request.transformIntoPartial[Order]

    assertEquals(
      Right(Order("O-9", Shipped, BankTransfer("PL61109010140000071219812874"), List(Pending, Paid))),
      result.asEither
    )
    val cash: PaymentMethodApi = CASH
    assertEquals(Right(Cash), cash.transformIntoPartial[PaymentMethod].asEither)
  }

  @Test
  def rejectsInboundOrdersWithAPaymentMethodTheDomainCannotRepresent(): Unit = {
    val request: OrderApi = OrderApi("O-10", PAID, OTHER("crypto"), Vector.empty)

    val result: partial.Result[Order] = request.transformIntoPartial[Order]

    assertEquals(None, result.asOption)
    assertEquals(
      List("payment" -> "unsupported payment method: crypto"),
      result.asErrorPathMessageStrings.toList
    )
  }

  @Test
  def roundTripsStatusesAloneThroughTheCaseInsensitiveMapping(): Unit = {
    val statuses: List[OrderStatus] = List(Pending, Paid, Shipped, Cancelled)
    val order: Order = Order("O-11", Pending, Cash, statuses)

    val published: OrderApi = order.transformInto[OrderApi]
    assertEquals(Vector(PENDING, PAID, SHIPPED, CANCELLED), published.history)

    val back: partial.Result[Order] = published.transformIntoPartial[Order]
    assertEquals(Right(order), back.asEither)
  }
}
