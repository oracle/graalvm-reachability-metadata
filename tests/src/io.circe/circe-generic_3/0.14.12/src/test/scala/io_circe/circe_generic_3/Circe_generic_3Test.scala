/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package io_circe.circe_generic_3

import io.circe.Codec
import io.circe.Decoder
import io.circe.DecodingFailure
import io.circe.Encoder
import io.circe.Json
import io.circe.generic.semiauto.deriveCodec
import io.circe.generic.semiauto.deriveDecoder
import io.circe.generic.semiauto.deriveEncoder
import io.circe.syntax.*
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Assertions.fail
import org.junit.jupiter.api.Test

import scala.jdk.CollectionConverters.*

final case class CustomerId(value: String)

object CustomerId {
  given Codec.AsObject[CustomerId] = deriveCodec[CustomerId]
}

final case class Address(street: String, city: String, zipCode: String)

object Address {
  given Codec.AsObject[Address] = deriveCodec[Address]
}

final case class Contact(email: String, phone: Option[String])

object Contact {
  given Codec.AsObject[Contact] = deriveCodec[Contact]
}

final case class Customer(
    id: CustomerId,
    name: String,
    address: Address,
    contact: Option[Contact],
    tags: List[String],
    preferences: Map[String, Boolean]
)

object Customer {
  given Codec.AsObject[Customer] = deriveCodec[Customer]
}

final case class InventoryItem(
    sku: String,
    quantity: Int,
    dimensions: Option[Dimensions],
    attributes: Map[String, String]
)

object InventoryItem {
  given Encoder.AsObject[InventoryItem] = deriveEncoder[InventoryItem]
  given Decoder[InventoryItem] = deriveDecoder[InventoryItem]
}

final case class Dimensions(width: BigDecimal, height: BigDecimal, depth: BigDecimal)

object Dimensions {
  given Codec.AsObject[Dimensions] = deriveCodec[Dimensions]
}

sealed trait PaymentMethod

object PaymentMethod {
  final case class Card(last4: String, billingAddress: Address) extends PaymentMethod
  final case class BankAccount(country: String, ibanSuffix: String) extends PaymentMethod
  case object Cash extends PaymentMethod

  given Encoder[PaymentMethod] = deriveEncoder[PaymentMethod]
  given Decoder[PaymentMethod] = deriveDecoder[PaymentMethod]
}

sealed trait ShipmentEvent

object ShipmentEvent {
  final case class Created(orderId: String, method: PaymentMethod) extends ShipmentEvent
  final case class Packed(orderId: String, itemCount: Int) extends ShipmentEvent
  final case class Delivered(orderId: String, signedBy: Option[String]) extends ShipmentEvent

  given Encoder[ShipmentEvent] = deriveEncoder[ShipmentEvent]
  given Decoder[ShipmentEvent] = deriveDecoder[ShipmentEvent]
}

sealed trait SupportTicketEvent

object SupportTicketEvent {
  final case class Opened(ticketId: String, priority: Int) extends SupportTicketEvent
  final case class CommentAdded(ticketId: String, author: String, body: String) extends SupportTicketEvent
  final case class Closed(ticketId: String, resolution: Option[String]) extends SupportTicketEvent
}

enum WorkflowState {
  case Pending(owner: String)
  case Approved(approver: String, comments: Option[String])
  case Archived
}

object WorkflowState {
  given Encoder[WorkflowState] = deriveEncoder[WorkflowState]
  given Decoder[WorkflowState] = deriveDecoder[WorkflowState]
}

final case class Wrapper[A](value: A, history: List[A])

object Wrapper {
  given [A: Encoder: Decoder]: Codec.AsObject[Wrapper[A]] = deriveCodec[Wrapper[A]]
}

class Circe_generic_3Test {
  @Test
  def semiAutomaticCodecDerivesNestedProductRoundTrips(): Unit = {
    val customer: Customer = Customer(
      id = CustomerId("customer-1"),
      name = "Ada Lovelace",
      address = Address("1 Algorithm Lane", "London", "SW1A"),
      contact = Some(Contact("ada@example.test", Some("+44-000"))),
      tags = List("compiler", "math"),
      preferences = Map("newsletter" -> true, "sms" -> false)
    )

    val json: Json = customer.asJson

    assertThat(json.hcursor.downField("id").get[String]("value")).isEqualTo(Right("customer-1"))
    assertThat(json.hcursor.downField("address").get[String]("city")).isEqualTo(Right("London"))
    assertThat(json.hcursor.downField("contact").get[String]("email")).isEqualTo(Right("ada@example.test"))
    assertThat(decodeOrFail(json.hcursor.get[List[String]]("tags")).asJava).containsExactly("compiler", "math")
    assertThat(json.as[Customer]).isEqualTo(Right(customer))
  }

  @Test
  def separateSemiAutomaticEncoderAndDecoderHandleOptionalAndMapFields(): Unit = {
    val item: InventoryItem = InventoryItem(
      sku = "keyboard-ansi",
      quantity = 12,
      dimensions = Some(Dimensions(BigDecimal("44.0"), BigDecimal("3.5"), BigDecimal("13.2"))),
      attributes = Map("layout" -> "ansi", "switch" -> "linear")
    )

    val json: Json = item.asJson

    assertThat(json.hcursor.get[String]("sku")).isEqualTo(Right("keyboard-ansi"))
    assertThat(json.hcursor.downField("dimensions").get[BigDecimal]("width")).isEqualTo(Right(BigDecimal("44.0")))
    assertThat(decodeOrFail(json.hcursor.get[Map[String, String]]("attributes"))).isEqualTo(item.attributes)
    assertThat(json.as[InventoryItem]).isEqualTo(Right(item))

    val withoutDimensions: Json = Json.obj(
      "sku" -> Json.fromString("mouse"),
      "quantity" -> Json.fromInt(3),
      "dimensions" -> Json.Null,
      "attributes" -> Json.obj("wireless" -> Json.fromString("true"))
    )
    assertThat(withoutDimensions.as[InventoryItem])
      .isEqualTo(Right(InventoryItem("mouse", 3, None, Map("wireless" -> "true"))))
  }

  @Test
  def automaticDerivationWorksForProductsInUserCode(): Unit = {
    import io.circe.generic.auto.*

    final case class LineItem(sku: String, quantity: Int, discounts: List[BigDecimal])
    final case class Cart(owner: String, lineItems: Vector[LineItem], coupon: Option[String], expedited: Boolean)

    val cart: Cart = Cart(
      owner = "Grace",
      lineItems = Vector(LineItem("book", 2, List(BigDecimal("1.50"))), LineItem("pen", 5, Nil)),
      coupon = Some("WELCOME"),
      expedited = true
    )

    val json: Json = cart.asJson

    assertThat(json.hcursor.get[String]("owner")).isEqualTo(Right("Grace"))
    assertThat(decodeOrFail(json.hcursor.get[Vector[LineItem]]("lineItems"))).isEqualTo(cart.lineItems)
    assertThat(json.hcursor.get[Option[String]]("coupon")).isEqualTo(Right(Some("WELCOME")))
    assertThat(json.as[Cart]).isEqualTo(Right(cart))
  }

  @Test
  def derivedSealedTraitCodecsEncodeAndDecodeAlternatives(): Unit = {
    import PaymentMethod.*

    val methods: List[PaymentMethod] = List(
      Card("4242", Address("1 Algorithm Lane", "London", "SW1A")),
      BankAccount("DE", "3000"),
      Cash
    )

    val json: Json = methods.asJson

    assertThat(json.hcursor.downArray.downField("Card").get[String]("last4")).isEqualTo(Right("4242"))
    assertThat(json.hcursor.downN(1).downField("BankAccount").get[String]("country")).isEqualTo(Right("DE"))
    assertThat(json.as[List[PaymentMethod]]).isEqualTo(Right(methods))
  }

  @Test
  def automaticDerivationWorksForSealedTraitHierarchiesWithoutCompanionCodecs(): Unit = {
    import SupportTicketEvent.*
    import io.circe.generic.auto.*

    val events: List[SupportTicketEvent] = List(
      Opened("ticket-1", 2),
      CommentAdded("ticket-1", "Ada", "Reproduced with the sample payload"),
      Closed("ticket-1", Some("configuration updated"))
    )

    val json: Json = events.asJson

    assertThat(json.hcursor.downArray.downField("Opened").get[String]("ticketId")).isEqualTo(Right("ticket-1"))
    assertThat(json.hcursor.downN(1).downField("CommentAdded").get[String]("author")).isEqualTo(Right("Ada"))
    assertThat(json.hcursor.downN(2).downField("Closed").get[Option[String]]("resolution"))
      .isEqualTo(Right(Some("configuration updated")))
    assertThat(json.as[List[SupportTicketEvent]]).isEqualTo(Right(events))
  }

  @Test
  def derivedAdtCodecsComposeThroughNestedProducts(): Unit = {
    import PaymentMethod.*
    import ShipmentEvent.*

    val events: Vector[ShipmentEvent] = Vector(
      Created("order-1", Card("1111", Address("Market Street", "San Francisco", "94105"))),
      Packed("order-1", 3),
      Delivered("order-1", Some("Linus"))
    )

    val wrapper: Wrapper[Vector[ShipmentEvent]] = Wrapper(events, List(events.take(1)))
    val json: Json = wrapper.asJson

    assertThat(json.hcursor.downField("value").downArray.downField("Created").get[String]("orderId"))
      .isEqualTo(Right("order-1"))
    assertThat(json.hcursor.downField("value").downN(1).downField("Packed").get[Int]("itemCount"))
      .isEqualTo(Right(3))
    assertThat(json.as[Wrapper[Vector[ShipmentEvent]]]).isEqualTo(Right(wrapper))
  }

  @Test
  def semiAutomaticDerivationSupportsScala3Enums(): Unit = {
    import WorkflowState.*

    val states: List[WorkflowState] = List(
      Pending("Ada"),
      Approved("Grace", Some("looks good")),
      Archived
    )

    val json: Json = states.asJson

    assertThat(json.hcursor.downArray.downField("Pending").get[String]("owner")).isEqualTo(Right("Ada"))
    assertThat(json.hcursor.downN(1).downField("Approved").get[String]("approver")).isEqualTo(Right("Grace"))
    assertThat(json.hcursor.downN(2).downField("Archived").focus.exists(_.isObject)).isTrue
    assertThat(json.as[List[WorkflowState]]).isEqualTo(Right(states))
  }

  @Test
  def derivedDecodersReportUsefulFailuresForProducts(): Unit = {
    val invalid: Json = Json.obj(
      "id" -> Json.obj("value" -> Json.fromInt(42)),
      "name" -> Json.fromBoolean(true),
      "address" -> Json.obj("street" -> Json.fromString("A"), "city" -> Json.fromString("B"), "zipCode" -> Json.fromInt(123)),
      "contact" -> Json.obj("email" -> Json.fromString("ada@example.test"), "phone" -> Json.fromInt(1)),
      "tags" -> Json.fromString("not-a-list"),
      "preferences" -> Json.obj("newsletter" -> Json.fromString("yes"))
    )

    val failures: List[DecodingFailure] = invalid.asAccumulating[Customer].fold(
      _.toList,
      value => fail(s"Expected product decoding to fail, but decoded: $value")
    )

    assertThat(failures.size).isGreaterThanOrEqualTo(5)
    assertThat(failures.map(_.history.nonEmpty).asJava).contains(true)
  }

  @Test
  def derivedDecodersRejectUnknownSealedTraitConstructors(): Unit = {
    val invalid: Json = Json.obj(
      "WireTransfer" -> Json.obj("country" -> Json.fromString("DE"), "ibanSuffix" -> Json.fromString("3000"))
    )

    val failure: DecodingFailure = invalid.as[PaymentMethod] match {
      case Left(error) => error
      case Right(value) => fail(s"Expected unknown ADT constructor to fail, but decoded: $value")
    }

    assertThat(failure.message).isNotBlank
  }

  @Test
  def derivedGenericCaseClassCodecsWorkForDifferentElementTypes(): Unit = {
    val numbers: Wrapper[Int] = Wrapper(3, List(1, 2))
    val addresses: Wrapper[Address] = Wrapper(
      Address("First", "Paris", "75001"),
      List(Address("Second", "Berlin", "10115"))
    )

    val numbersJson: Json = numbers.asJson
    val addressesJson: Json = addresses.asJson

    assertThat(numbersJson.hcursor.get[Int]("value")).isEqualTo(Right(3))
    assertThat(addressesJson.hcursor.downField("history").downArray.get[String]("city")).isEqualTo(Right("Berlin"))
    assertThat(numbersJson.as[Wrapper[Int]]).isEqualTo(Right(numbers))
    assertThat(addressesJson.as[Wrapper[Address]]).isEqualTo(Right(addresses))
  }

  private def decodeOrFail[A](result: Either[DecodingFailure, A]): A = {
    result.fold(error => fail(s"Expected decoding success but got: $error"), identity)
  }
}
