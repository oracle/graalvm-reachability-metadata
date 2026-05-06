/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package io_circe.circe_generic_3

import io.circe.Codec
import io.circe.Decoder
import io.circe.Encoder
import io.circe.Json
import io.circe.generic.semiauto.deriveCodec
import io.circe.generic.semiauto.deriveDecoder
import io.circe.generic.semiauto.deriveEncoder
import io.circe.syntax.*
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class Circe_generic_3Test {
  @Test
  def semiautomaticCodecRoundTripsNestedProductsAndCollections(): Unit = {
    val profile = UserProfile(
      name = "Ada Lovelace",
      age = 36,
      aliases = List("analyst", "programmer"),
      address = Some(PostalAddress("St. James's Square", "London")),
      scores = Map("mathematics" -> 10, "poetry" -> 7)
    )

    val expectedJson = Json.obj(
      "name" -> Json.fromString("Ada Lovelace"),
      "age" -> Json.fromInt(36),
      "aliases" -> Json.arr(Json.fromString("analyst"), Json.fromString("programmer")),
      "address" -> Json.obj(
        "street" -> Json.fromString("St. James's Square"),
        "city" -> Json.fromString("London")
      ),
      "scores" -> Json.obj(
        "mathematics" -> Json.fromInt(10),
        "poetry" -> Json.fromInt(7)
      )
    )

    val encoded = profile.asJson

    assertThat(encoded).isEqualTo(expectedJson)
    assertDecodesTo(encoded, profile)
  }

  @Test
  def semiautomaticCodecDecodesMissingOptionalFieldsAndRejectsMissingRequiredFields(): Unit = {
    val jsonWithoutAddress = Json.obj(
      "name" -> Json.fromString("Grace Hopper"),
      "age" -> Json.fromInt(85),
      "aliases" -> Json.arr(Json.fromString("Amazing Grace")),
      "scores" -> Json.obj("compilers" -> Json.fromInt(10))
    )

    assertDecodesTo(
      jsonWithoutAddress,
      UserProfile(
        name = "Grace Hopper",
        age = 85,
        aliases = List("Amazing Grace"),
        address = None,
        scores = Map("compilers" -> 10)
      )
    )

    val missingRequiredName = Json.obj(
      "age" -> Json.fromInt(85),
      "aliases" -> Json.arr(),
      "scores" -> Json.obj()
    )

    assertFailsToDecode[UserProfile](missingRequiredName)
  }

  @Test
  def semiautomaticEncoderAndDecoderCanBeDerivedIndependently(): Unit = {
    val record = AuditRecord(
      id = "evt-42",
      success = true,
      attributes = Map("source" -> "unit-test", "operation" -> "encode-decode")
    )

    val encoded = Encoder[AuditRecord].apply(record)

    assertThat(encoded.hcursor.downField("id").as[String]).isEqualTo(Right("evt-42"))
    assertThat(encoded.hcursor.downField("success").as[Boolean]).isEqualTo(Right(true))
    assertDecodesTo(encoded, record)
  }

  @Test
  def semiautomaticDerivationSupportsParameterizedProductTypes(): Unit = {
    val envelope: ApiEnvelope[ApiEvent] = ApiEnvelope(
      requestId = "req-7",
      payload = ApiEvent(kind = "created", retryCount = 1),
      tags = List("generic", "semiauto")
    )

    val expectedJson = Json.obj(
      "requestId" -> Json.fromString("req-7"),
      "payload" -> Json.obj(
        "kind" -> Json.fromString("created"),
        "retryCount" -> Json.fromInt(1)
      ),
      "tags" -> Json.arr(Json.fromString("generic"), Json.fromString("semiauto"))
    )

    val encoded = envelope.asJson

    assertThat(encoded).isEqualTo(expectedJson)
    assertDecodesTo(encoded, envelope)
    assertFailsToDecode[ApiEnvelope[ApiEvent]](
      Json.obj(
        "requestId" -> Json.fromString("req-8"),
        "payload" -> Json.obj("kind" -> Json.fromString("updated")),
        "tags" -> Json.arr()
      )
    )
  }

  @Test
  def semiautomaticDerivationSupportsSealedTraitCoproducts(): Unit = {
    val card: PaymentMethod = CreditCard(id = "card-1", lastFour = "4242")
    val wire: PaymentMethod = WireTransfer(iban = "DE89370400440532013000", urgent = false)

    val encodedCard = card.asJson
    val encodedWire = wire.asJson

    assertDecodesTo(encodedCard, card)
    assertDecodesTo(encodedWire, wire)
    assertFailsToDecode[PaymentMethod](Json.obj("Cheque" -> Json.obj("number" -> Json.fromString("1001"))))
  }

  @Test
  def automaticDerivationWorksForNestedProducts(): Unit = {
    import io.circe.generic.auto.*

    val inventory = AutoInventory(
      location = "warehouse-a",
      active = true,
      items = Vector(
        AutoInventoryItem(sku = "book-1", quantity = 3),
        AutoInventoryItem(sku = "cable-2", quantity = 8)
      )
    )

    val encoded = inventory.asJson

    assertThat(encoded.hcursor.downField("location").as[String]).isEqualTo(Right("warehouse-a"))
    assertThat(encoded.hcursor.downField("items").downArray.downField("sku").as[String]).isEqualTo(Right("book-1"))
    assertThat(encoded.as[AutoInventory]).isEqualTo(Right(inventory))
  }

  @Test
  def semiautomaticDerivationSupportsScala3EnumsWithSingletonAndParameterizedCases(): Unit = {
    val inTransit: ShipmentStatus = ShipmentStatus.InTransit(location = "Paris", daysInTransit = 2)
    val delivered: ShipmentStatus = ShipmentStatus.Delivered

    assertDecodesTo(inTransit.asJson, inTransit)
    assertDecodesTo(delivered.asJson, delivered)
    assertFailsToDecode[ShipmentStatus](Json.obj("Returned" -> Json.obj()))
  }

  private def assertDecodesTo[A](json: Json, expected: A)(using Decoder[A]): Unit = {
    assertThat(json.as[A]).isEqualTo(Right(expected))
  }

  private def assertFailsToDecode[A](json: Json)(using Decoder[A]): Unit = {
    assertThat(json.as[A].isLeft).isTrue()
  }

  private final case class PostalAddress(street: String, city: String)

  private object PostalAddress {
    given Codec[PostalAddress] = deriveCodec[PostalAddress]
  }

  private final case class UserProfile(
    name: String,
    age: Int,
    aliases: List[String],
    address: Option[PostalAddress],
    scores: Map[String, Int]
  )

  private object UserProfile {
    given Codec[UserProfile] = deriveCodec[UserProfile]
  }

  private final case class AuditRecord(id: String, success: Boolean, attributes: Map[String, String])

  private object AuditRecord {
    given Encoder[AuditRecord] = deriveEncoder[AuditRecord]
    given Decoder[AuditRecord] = deriveDecoder[AuditRecord]
  }

  private final case class ApiEvent(kind: String, retryCount: Int)

  private object ApiEvent {
    given Codec[ApiEvent] = deriveCodec[ApiEvent]
  }

  private final case class ApiEnvelope[A](requestId: String, payload: A, tags: List[String])

  private object ApiEnvelope {
    given [A: Codec]: Codec[ApiEnvelope[A]] = deriveCodec[ApiEnvelope[A]]
  }

  private sealed trait PaymentMethod

  private object PaymentMethod {
    given Encoder[PaymentMethod] = deriveEncoder[PaymentMethod]
    given Decoder[PaymentMethod] = deriveDecoder[PaymentMethod]
  }

  private final case class CreditCard(id: String, lastFour: String) extends PaymentMethod

  private final case class WireTransfer(iban: String, urgent: Boolean) extends PaymentMethod

  private final case class AutoInventoryItem(sku: String, quantity: Int)

  private final case class AutoInventory(location: String, active: Boolean, items: Vector[AutoInventoryItem])

  private enum ShipmentStatus {
    case Pending
    case InTransit(location: String, daysInTransit: Int)
    case Delivered
  }

  private object ShipmentStatus {
    given Codec[ShipmentStatus] = deriveCodec[ShipmentStatus]
  }
}
