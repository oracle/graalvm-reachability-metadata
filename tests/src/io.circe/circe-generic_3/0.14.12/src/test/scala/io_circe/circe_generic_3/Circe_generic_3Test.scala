/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package io_circe.circe_generic_3

import cats.data.Validated
import io.circe.Codec
import io.circe.Decoder
import io.circe.DecodingFailure
import io.circe.Encoder
import io.circe.Json
import io.circe.generic.semiauto.deriveCodec
import io.circe.generic.semiauto.deriveDecoder
import io.circe.generic.semiauto.deriveEncoder
import io.circe.syntax._
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Assertions.fail
import org.junit.jupiter.api.Test

final case class Address(street: String, zipCode: Int)

object Address {
  given Encoder.AsObject[Address] = deriveEncoder[Address]
  given Decoder[Address] = deriveDecoder[Address]
}

final case class Contact(email: Option[String], phones: List[String])

object Contact {
  given Encoder.AsObject[Contact] = deriveEncoder[Contact]
  given Decoder[Contact] = deriveDecoder[Contact]
}

final case class UserProfile(
    id: Long,
    name: String,
    active: Boolean,
    address: Address,
    contacts: List[Contact],
    attributes: Map[String, Json]
)

object UserProfile {
  given Encoder.AsObject[UserProfile] = deriveEncoder[UserProfile]
  given Decoder[UserProfile] = deriveDecoder[UserProfile]
}

final case class InventoryItem(sku: String, count: Int, tags: Vector[String])

object InventoryItem {
  given Codec.AsObject[InventoryItem] = deriveCodec[InventoryItem]
}

sealed trait PaymentMethod

object PaymentMethod {
  final case class Card(last4: String, expiresYear: Int) extends PaymentMethod
  final case class BankAccount(iban: String, verified: Boolean) extends PaymentMethod

  given Encoder[PaymentMethod] = deriveEncoder[PaymentMethod]
  given Decoder[PaymentMethod] = deriveDecoder[PaymentMethod]
}

final case class Purchase(id: String, amount: BigDecimal, method: PaymentMethod)

object Purchase {
  given Encoder.AsObject[Purchase] = deriveEncoder[Purchase]
  given Decoder[Purchase] = deriveDecoder[Purchase]
}

final case class AutoLineItem(name: String, quantity: Int)

final case class AutoOrder(orderId: String, items: List[AutoLineItem], gift: Option[Boolean])

sealed trait DeliveryChannel

object DeliveryChannel {
  case object Email extends DeliveryChannel
  case object Sms extends DeliveryChannel
  final case class Webhook(url: String, headers: Map[String, String]) extends DeliveryChannel

  given Encoder[DeliveryChannel] = deriveEncoder[DeliveryChannel]
  given Decoder[DeliveryChannel] = deriveDecoder[DeliveryChannel]
}

final case class DeliveryRule(name: String, channel: DeliveryChannel)

object DeliveryRule {
  given Codec.AsObject[DeliveryRule] = deriveCodec[DeliveryRule]
}

enum ShipmentStatus {
  case Queued
  case InTransit(carrier: String, trackingNumber: String)
  case Delivered(receivedBy: Option[String])
}

object ShipmentStatus {
  given Encoder[ShipmentStatus] = deriveEncoder[ShipmentStatus]
  given Decoder[ShipmentStatus] = deriveDecoder[ShipmentStatus]
}

final case class Shipment(id: String, status: ShipmentStatus)

object Shipment {
  given Codec.AsObject[Shipment] = deriveCodec[Shipment]
}

class Circe_generic_3Test {
  @Test
  def semiAutomaticallyDerivesEncodersAndDecodersForNestedProducts(): Unit = {
    val profile: UserProfile = UserProfile(
      id = 7L,
      name = "Ada Lovelace",
      active = true,
      address = Address("St James Square", 1010),
      contacts = List(Contact(Some("ada@example.test"), List("+44-1", "+44-2")), Contact(None, Nil)),
      attributes = Map(
        "role" -> Json.fromString("admin"),
        "quota" -> Json.fromInt(12),
        "flags" -> Json.arr(Json.True, Json.False)
      )
    )

    val json: Json = profile.asJson

    assertEquals(Right(7L), json.hcursor.get[Long]("id"))
    assertEquals(Right("Ada Lovelace"), json.hcursor.get[String]("name"))
    assertEquals(Right("St James Square"), json.hcursor.downField("address").get[String]("street"))
    assertEquals(Right(List("+44-1", "+44-2")), json.hcursor.downField("contacts").downArray.get[List[String]]("phones"))
    assertEquals(Right("admin"), json.hcursor.downField("attributes").get[String]("role"))
    assertEquals(Right(profile), json.as[UserProfile])
  }

  @Test
  def derivedProductDecodersIgnoreUnknownFieldsAndReportMissingFields(): Unit = {
    val withExtraField: Json = Json.obj(
      "id" -> Json.fromLong(9L),
      "name" -> Json.fromString("Grace Hopper"),
      "active" -> Json.False,
      "address" -> Json.obj("street" -> Json.fromString("Arlington"), "zipCode" -> Json.fromInt(2220)),
      "contacts" -> Json.arr(),
      "attributes" -> Json.obj(),
      "ignored" -> Json.fromString("not part of the case class")
    )

    assertEquals(
      Right(UserProfile(9L, "Grace Hopper", false, Address("Arlington", 2220), Nil, Map.empty)),
      withExtraField.as[UserProfile]
    )

    val missingAddress: Json = withExtraField.mapObject(_.remove("address"))
    val failure: DecodingFailure = expectLeft(missingAddress.as[UserProfile])

    assertFalse(failure.message.isBlank)
    assertTrue(failure.history.nonEmpty, s"Expected field history for missing address, but got: $failure")
  }

  @Test
  def derivedDecodersAccumulateIndependentProductFieldFailures(): Unit = {
    val invalid: Json = Json.obj(
      "id" -> Json.fromString("not-a-long"),
      "name" -> Json.fromBoolean(true),
      "active" -> Json.fromString("not-a-boolean"),
      "address" -> Json.obj("street" -> Json.fromInt(42), "zipCode" -> Json.fromString("not-an-int")),
      "contacts" -> Json.arr(Json.obj("email" -> Json.fromInt(123), "phones" -> Json.fromString("not-a-list"))),
      "attributes" -> Json.fromString("not-an-object")
    )

    invalid.asAccumulating[UserProfile] match {
      case Validated.Invalid(errors) =>
        val messages: List[String] = errors.toList.map(_.message)
        assertTrue(messages.size >= 6, s"Expected several accumulated errors, but got: $messages")
        assertTrue(errors.toList.exists(_.history.nonEmpty), s"Expected cursor history in errors, but got: $errors")
      case Validated.Valid(value) =>
        fail[Unit](s"Expected invalid profile JSON to fail, but decoded: $value")
    }
  }

  @Test
  def derivesObjectCodecsForRoundTripUpdatesAndCollectionFields(): Unit = {
    val item: InventoryItem = InventoryItem("sku-1", 3, Vector("cold", "fragile"))

    val encoded: Json = item.asJson
    val restocked: Json = encoded.mapObject(_.add("count", Json.fromInt(8)))

    assertEquals(Right("sku-1"), encoded.hcursor.get[String]("sku"))
    assertEquals(Right(Vector("cold", "fragile")), encoded.hcursor.get[Vector[String]]("tags"))
    assertEquals(Right(item), encoded.as[InventoryItem])
    assertEquals(Right(InventoryItem("sku-1", 8, Vector("cold", "fragile"))), restocked.as[InventoryItem])
  }

  @Test
  def derivesSealedTraitCodecsForCaseClassAlternatives(): Unit = {
    val card: PaymentMethod = PaymentMethod.Card("4242", 2030)
    val bankAccount: PaymentMethod = PaymentMethod.BankAccount("DE89370400440532013000", verified = true)

    val encodedCard: Json = card.asJson
    val encodedBankAccount: Json = bankAccount.asJson

    assertTrue(encodedCard.hcursor.downField("Card").succeeded, s"Expected Card discriminator in $encodedCard")
    assertEquals(Right("4242"), encodedCard.hcursor.downField("Card").get[String]("last4"))
    assertEquals(Right(card), encodedCard.as[PaymentMethod])
    assertEquals(Right(bankAccount), encodedBankAccount.as[PaymentMethod])

    val unknownAlternative: Json = Json.obj("Wire" -> Json.obj("reference" -> Json.fromString("x")))
    assertTrue(unknownAlternative.as[PaymentMethod].isLeft)
  }

  @Test
  def derivesNestedSealedTraitMembersInsideProducts(): Unit = {
    val purchase: Purchase = Purchase(
      id = "purchase-1",
      amount = BigDecimal("19.95"),
      method = PaymentMethod.BankAccount("FR1420041010050500013M02606", verified = false)
    )

    val json: Json = purchase.asJson

    assertEquals(Right("purchase-1"), json.hcursor.get[String]("id"))
    assertEquals(Right(BigDecimal("19.95")), json.hcursor.get[BigDecimal]("amount"))
    assertEquals(Right(false), json.hcursor.downField("method").downField("BankAccount").get[Boolean]("verified"))
    assertEquals(Right(purchase), json.as[Purchase])
  }

  @Test
  def derivesSealedTraitCodecsForSingletonAlternatives(): Unit = {
    val rule: DeliveryRule = DeliveryRule("order-created", DeliveryChannel.Email)
    val channels: List[DeliveryChannel] = List(
      DeliveryChannel.Email,
      DeliveryChannel.Webhook("https://hooks.example.test/orders", Map("X-Event" -> "created")),
      DeliveryChannel.Sms
    )

    val ruleJson: Json = rule.asJson
    val channelsJson: Json = channels.asJson

    assertTrue(ruleJson.hcursor.downField("channel").downField("Email").succeeded)
    assertEquals(Right(rule), ruleJson.as[DeliveryRule])
    assertEquals(
      Right("https://hooks.example.test/orders"),
      channelsJson.hcursor.downN(1).downField("Webhook").get[String]("url")
    )
    assertEquals(
      Right(Map("X-Event" -> "created")),
      channelsJson.hcursor.downN(1).downField("Webhook").get[Map[String, String]]("headers")
    )
    assertTrue(channelsJson.hcursor.downN(2).downField("Sms").succeeded)
    assertEquals(Right(channels), channelsJson.as[List[DeliveryChannel]])
  }

  @Test
  def derivesCodecsForScala3EnumsWithSingletonAndProductCases(): Unit = {
    val queued: ShipmentStatus = ShipmentStatus.Queued
    val inTransit: ShipmentStatus = ShipmentStatus.InTransit("DHL", "TRACK-1")
    val delivered: ShipmentStatus = ShipmentStatus.Delivered(Some("Lena"))

    val queuedJson: Json = queued.asJson
    val inTransitJson: Json = inTransit.asJson
    val shipmentJson: Json = Shipment("shipment-1", delivered).asJson

    assertTrue(queuedJson.hcursor.downField("Queued").succeeded, s"Expected Queued enum case in $queuedJson")
    assertEquals(Right(queued), queuedJson.as[ShipmentStatus])
    assertTrue(inTransitJson.hcursor.downField("InTransit").succeeded, s"Expected InTransit enum case in $inTransitJson")
    assertEquals(Right("DHL"), inTransitJson.hcursor.downField("InTransit").get[String]("carrier"))
    assertEquals(Right("TRACK-1"), inTransitJson.hcursor.downField("InTransit").get[String]("trackingNumber"))
    assertEquals(Right(inTransit), inTransitJson.as[ShipmentStatus])
    assertEquals(Right("shipment-1"), shipmentJson.hcursor.get[String]("id"))
    assertEquals(
      Right(Some("Lena")),
      shipmentJson.hcursor.downField("status").downField("Delivered").get[Option[String]]("receivedBy")
    )
    assertEquals(Right(Shipment("shipment-1", delivered)), shipmentJson.as[Shipment])
  }

  @Test
  def automaticallyDerivesCodecsFromImportedPublicApi(): Unit = {
    import io.circe.generic.auto._

    val order: AutoOrder = AutoOrder(
      orderId = "order-1",
      items = List(AutoLineItem("keyboard", 1), AutoLineItem("mouse", 2)),
      gift = Some(false)
    )

    val json: Json = order.asJson

    assertEquals(Right("order-1"), json.hcursor.get[String]("orderId"))
    assertEquals(Right(List("keyboard", "mouse")), json.hcursor.get[List[AutoLineItem]]("items").map(_.map(_.name)))
    assertEquals(Right(Some(false)), json.hcursor.get[Option[Boolean]]("gift"))
    assertEquals(Right(order), json.as[AutoOrder])
  }

  @Test
  def automaticDerivationHandlesOptionalFieldsAndDecodingFailures(): Unit = {
    import io.circe.generic.auto._

    val withoutGift: Json = Json.obj(
      "orderId" -> Json.fromString("order-2"),
      "items" -> Json.arr(Json.obj("name" -> Json.fromString("monitor"), "quantity" -> Json.fromInt(1)))
    )
    val invalidQuantity: Json = Json.obj(
      "orderId" -> Json.fromString("order-3"),
      "items" -> Json.arr(Json.obj("name" -> Json.fromString("monitor"), "quantity" -> Json.fromString("one"))),
      "gift" -> Json.Null
    )

    assertEquals(Right(AutoOrder("order-2", List(AutoLineItem("monitor", 1)), None)), withoutGift.as[AutoOrder])

    val failure: DecodingFailure = expectLeft(invalidQuantity.as[AutoOrder])
    assertFalse(failure.message.isBlank)
    assertTrue(failure.history.nonEmpty, s"Expected cursor history for invalid quantity, but got: $failure")
  }

  private def expectLeft[A](result: Either[DecodingFailure, A]): DecodingFailure = {
    result match {
      case Left(error) => error
      case Right(value) => fail[DecodingFailure](s"Expected decoding failure, but decoded: $value")
    }
  }
}
