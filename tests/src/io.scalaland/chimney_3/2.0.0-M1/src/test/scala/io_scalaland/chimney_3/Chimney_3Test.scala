/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package io_scalaland.chimney_3

import io.scalaland.chimney.*
import io.scalaland.chimney.dsl.*
import io.scalaland.chimney.partial.Result
import org.junit.jupiter.api.Assertions.{assertEquals, assertTrue}
import org.junit.jupiter.api.Test

case class Customer(name: String)
case class TagDto(label: String)
case class Tag(value: String)
case class LineDto(sku: String, qty: Int, cents: Int)
case class Line(sku: String, quantity: Int, priceCents: Int)
case class OrderDto(
    id: String,
    customerName: String,
    items: List[LineDto],
    tags: Map[String, TagDto],
    discount: Option[String]
)
case class Order(
    id: String,
    customer: Customer,
    lines: Vector[Line],
    labels: Map[String, Tag],
    discount: Option[Int],
    status: String,
    totalQuantity: Int
)

case class RawCoordinates(x: String, y: String, label: Option[String])
case class Coordinates(x: Int, y: Int, label: Option[String])

case class Account(id: Int, email: String, phone: Option[String], newsletter: Boolean, audit: Vector[String])
case class AccountPatch(email: Option[String], phone: Option[Option[String]], audit: Vector[String])

case class Person(id: Int, name: String, age: Option[Int])
case class LocalizedPerson(id: Int, imie: String, wiek: Option[Int])
case class Money(amount: BigDecimal, currency: String)
case class MoneyDto(value: BigDecimal, code: String)

enum WireStatus {
  case NewOrder, PaidOrder, CancelledOrder
}

enum DomainStatus {
  case New, Paid, Cancelled
}

class Chimney_3Test {

  @Test
  def transformsNestedProductsCollectionsAndComputedFields(): Unit = {
    given Transformer[LineDto, Line] = Transformer
      .define[LineDto, Line]
      .withFieldRenamed(_.qty, _.quantity)
      .withFieldRenamed(_.cents, _.priceCents)
      .buildTransformer

    given Transformer[TagDto, Tag] = Transformer
      .define[TagDto, Tag]
      .withFieldRenamed(_.label, _.value)
      .buildTransformer

    val source: OrderDto = OrderDto(
      id = "order-1",
      customerName = "Ada Lovelace",
      items = List(LineDto("book", 2, 1299), LineDto("pen", 3, 199)),
      tags = Map("priority" -> TagDto("high"), "channel" -> TagDto("online")),
      discount = Some("15")
    )

    val transformed: Order = source
      .into[Order]
      .withFieldComputed(_.customer, dto => Customer(dto.customerName))
      .withFieldRenamed(_.items, _.lines)
      .withFieldRenamed(_.tags, _.labels)
      .withFieldComputed(_.discount, dto => dto.discount.map(_.toInt))
      .withFieldConst(_.status, "accepted")
      .withFieldComputed(_.totalQuantity, dto => dto.items.map(_.qty).sum)
      .transform

    assertEquals("order-1", transformed.id)
    assertEquals(Customer("Ada Lovelace"), transformed.customer)
    assertEquals(Vector(Line("book", 2, 1299), Line("pen", 3, 199)), transformed.lines)
    assertEquals(Map("priority" -> Tag("high"), "channel" -> Tag("online")), transformed.labels)
    assertEquals(Some(15), transformed.discount)
    assertEquals("accepted", transformed.status)
    assertEquals(5, transformed.totalQuantity)
  }

  @Test
  def derivesPartialTransformersAndReportsFieldPathsForFailures(): Unit = {
    given PartialTransformer[String, Int] = PartialTransformer(value => Result.fromCatchingNonFatal(value.toInt))

    val successful: Result[Coordinates] = RawCoordinates("12", "34", Some("home"))
      .intoPartial[Coordinates]
      .transform

    assertEquals(Right(Coordinates(12, 34, Some("home"))), successful.asEither)
    assertEquals(Some(Coordinates(12, 34, Some("home"))), successful.asOption)

    val failed: Result[Coordinates] = RawCoordinates("west", "north", None)
      .intoPartial[Coordinates]
      .transform

    assertTrue(failed.asEither.isLeft)
    assertEquals(None, failed.asOption)
    failed.asEitherErrorPathMessageStrings match {
      case Left(errors) =>
        val paths: Seq[String] = errors.toSeq.map(_._1)
        assertEquals(2, errors.size)
        assertTrue(paths.exists(_.contains("x")))
        assertTrue(paths.exists(_.contains("y")))
      case Right(value) =>
        throw new AssertionError(s"Expected a partial transformation failure, got $value")
    }
  }

  @Test
  def patchesProductsWithOptionalUpdatesAndCustomPatchDsl(): Unit = {
    val original: Account = Account(
      id = 7,
      email = "old@example.com",
      phone = Some("+1-202-555-0101"),
      newsletter = false,
      audit = Vector("created")
    )
    val patch: AccountPatch = AccountPatch(
      email = Some("vip@example.com"),
      phone = None,
      audit = Vector("email-change")
    )

    val defaultPatched: Account = original.patchUsing(patch)
    assertEquals("vip@example.com", defaultPatched.email)
    assertEquals(Some("+1-202-555-0101"), defaultPatched.phone)
    assertEquals(Vector("email-change"), defaultPatched.audit)

    val customized: Account = original
      .using(patch)
      .withFieldComputed(_.newsletter, update => update.email.exists(_.startsWith("vip")))
      .withFieldComputedFrom(_.audit)(_.audit, entries => original.audit ++ entries :+ "patched")
      .patch

    assertEquals(Account(7, "vip@example.com", Some("+1-202-555-0101"), true, Vector("created", "email-change", "patched")), customized)

    val clearedPhone: Account = original.patchUsing(patch.copy(email = None, phone = Some(None), audit = Vector.empty))
    assertEquals("old@example.com", clearedPhone.email)
    assertEquals(None, clearedPhone.phone)
  }

  @Test
  def exposesIsoCodecAndEnumDerivationThroughTransformerSyntax(): Unit = {
    given Iso[Person, LocalizedPerson] = Iso
      .define[Person, LocalizedPerson]
      .withFieldRenamed(_.name, _.imie)
      .withFieldRenamed(_.age, _.wiek)
      .buildIso

    val person: Person = Person(1, "Grace", Some(37))
    val localized: LocalizedPerson = LocalizedPerson(1, "Grace", Some(37))
    assertEquals(localized, person.transformInto[LocalizedPerson])
    assertEquals(person, localized.transformInto[Person])

    given Codec[Money, MoneyDto] = Codec
      .define[Money, MoneyDto]
      .withFieldRenamed(_.amount, _.value)
      .withFieldRenamed(_.currency, _.code)
      .buildCodec

    val money: Money = Money(BigDecimal("19.95"), "EUR")
    val dto: MoneyDto = MoneyDto(BigDecimal("19.95"), "EUR")
    assertEquals(dto, money.transformInto[MoneyDto])
    assertEquals(Some(money), dto.transformIntoPartial[Money].asOption)

    given Transformer[WireStatus, DomainStatus] = Transformer
      .define[WireStatus, DomainStatus]
      .withEnumCaseRenamed[WireStatus.NewOrder.type, DomainStatus.New.type]
      .withEnumCaseRenamed[WireStatus.PaidOrder.type, DomainStatus.Paid.type]
      .withEnumCaseRenamed[WireStatus.CancelledOrder.type, DomainStatus.Cancelled.type]
      .buildTransformer

    assertEquals(DomainStatus.New, WireStatus.NewOrder.transformInto[DomainStatus])
    assertEquals(DomainStatus.Paid, WireStatus.PaidOrder.transformInto[DomainStatus])
    assertEquals(DomainStatus.Cancelled, WireStatus.CancelledOrder.transformInto[DomainStatus])
  }
}
