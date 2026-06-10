/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package io_circe.circe_generic_3

import io.circe.ACursor
import io.circe.Codec
import io.circe.Decoder
import io.circe.Encoder
import io.circe.Error
import io.circe.HCursor
import io.circe.Json
import io.circe.JsonObject
import io.circe.generic.semiauto.deriveCodec
import io.circe.generic.semiauto.deriveDecoder
import io.circe.generic.semiauto.deriveEncoder
import io.circe.syntax.*
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class Circe_generic_3Test:
  @Test
  def semiautomaticDerivationEncodesAndDecodesNestedProducts(): Unit =
    import SemiautomaticModel.given

    val profile: SemiautomaticModel.Profile = SemiautomaticModel.Profile(
      id = SemiautomaticModel.UserId("user-42"),
      displayName = "Ada Lovelace",
      contact = SemiautomaticModel.Contact("ada@example.test", verified = true),
      roles = List("admin", "analyst"),
      rating = Some(BigDecimal("9.75"))
    )

    val json: Json = profile.asJson
    val cursor: HCursor = json.hcursor

    assertEquals(Right("user-42"), cursor.downField("id").downField("value").as[String])
    assertEquals(Right("Ada Lovelace"), cursor.downField("displayName").as[String])
    assertEquals(Right(true), cursor.downField("contact").downField("verified").as[Boolean])
    assertEquals(Right(List("admin", "analyst")), cursor.downField("roles").as[List[String]])
    assertEquals(Right(Some(BigDecimal("9.75"))), cursor.downField("rating").as[Option[BigDecimal]])
    assertEquals(Right(profile), json.as[SemiautomaticModel.Profile])

  @Test
  def automaticDerivationRoundTripsProductsWithoutExplicitInstances(): Unit =
    import AutomaticModel.*
    import io.circe.generic.auto.*

    val invoice: Invoice = Invoice(
      number = "INV-2026-001",
      customer = Customer("Acme Metadata", Address("Native Way", "10001")),
      items = Vector(
        LineItem("metadata-index", 2, Money(BigDecimal("12.50"), "USD")),
        LineItem("native-test", 1, Money(BigDecimal("25.00"), "USD"))
      )
    )

    val json: Json = invoice.asJson
    val decoded: Either[Error, Invoice] = json.as[Invoice]

    assertEquals(Right(invoice), decoded)
    assertEquals(Right("Acme Metadata"), json.hcursor.downField("customer").downField("name").as[String])
    assertEquals(Right("native-test"), json.hcursor.downField("items").downArray.right.downField("sku").as[String])
    assertTrue(json.noSpaces.contains("INV-2026-001"))

  @Test
  def semiautomaticDerivationSupportsSealedTraitFamilies(): Unit =
    import CommandModel.given

    val commands: List[CommandModel.Command] = List(
      CommandModel.Command.Create("doc-1", CommandModel.Payload(List(1, 1, 2, 3, 5))),
      CommandModel.Command.Delete("doc-2", hard = false)
    )

    val json: Json = commands.asJson
    val firstCommand: ACursor = json.hcursor.downArray
    val secondCommand: ACursor = firstCommand.right

    assertTrue(firstCommand.downField("Create").succeeded)
    assertEquals(Right("doc-1"), firstCommand.downField("Create").downField("id").as[String])
    assertTrue(secondCommand.downField("Delete").succeeded)
    assertEquals(Right(false), secondCommand.downField("Delete").downField("hard").as[Boolean])
    assertEquals(Right(commands), json.as[List[CommandModel.Command]])

  @Test
  def derivedCodecAsObjectExposesObjectFieldsAndRoundTripsValues(): Unit =
    import CodecModel.given

    val envelope: CodecModel.Envelope = CodecModel.Envelope(
      metadata = CodecModel.Metadata("batch", Map("source" -> "generator", "format" -> "json")),
      payload = CodecModel.Payload(Vector("alpha", "beta", "gamma")),
      acknowledged = false
    )

    val codec: Codec.AsObject[CodecModel.Envelope] = summon[Codec.AsObject[CodecModel.Envelope]]
    val jsonObject: JsonObject = codec.encodeObject(envelope)
    val json: Json = Json.fromJsonObject(jsonObject)

    assertEquals(Some(Json.fromBoolean(false)), jsonObject("acknowledged"))
    assertTrue(jsonObject.keys.exists(_ == "metadata"))
    assertTrue(jsonObject.keys.exists(_ == "payload"))
    assertEquals(Right(envelope), codec.decodeJson(json))

  @Test
  def derivedDecodersReturnFailuresForInvalidProductFields(): Unit =
    import SemiautomaticModel.given

    val invalidProfile: Json = Json.obj(
      "id" -> Json.obj("value" -> Json.fromString("user-99")),
      "displayName" -> Json.fromString("Grace Hopper"),
      "contact" -> Json.obj(
        "email" -> Json.fromString("grace@example.test"),
        "verified" -> Json.fromString("yes")
      ),
      "roles" -> Json.arr(Json.fromString("operator")),
      "rating" -> Json.Null
    )

    val decoded: Either[Error, SemiautomaticModel.Profile] = invalidProfile.as[SemiautomaticModel.Profile]

    assertTrue(decoded.isLeft)
    assertTrue(decoded.swap.toOption.exists(_.getMessage.nonEmpty))

  @Test
  def derivedDecodersRejectUnknownSealedTraitConstructors(): Unit =
    import CommandModel.given

    val unknownCommand: Json = Json.obj(
      "Rename" -> Json.obj(
        "id" -> Json.fromString("doc-3"),
        "newName" -> Json.fromString("doc-3-renamed")
      )
    )

    val decoded: Either[Error, CommandModel.Command] = unknownCommand.as[CommandModel.Command]

    assertTrue(decoded.isLeft)
    assertFalse(decoded == Right(CommandModel.Command.Delete("doc-3", hard = true)))

  private object SemiautomaticModel:
    final case class UserId(value: String)
    final case class Contact(email: String, verified: Boolean)
    final case class Profile(
        id: UserId,
        displayName: String,
        contact: Contact,
        roles: List[String],
        rating: Option[BigDecimal])

    given Encoder[UserId] = deriveEncoder[UserId]
    given Decoder[UserId] = deriveDecoder[UserId]
    given Encoder[Contact] = deriveEncoder[Contact]
    given Decoder[Contact] = deriveDecoder[Contact]
    given Encoder[Profile] = deriveEncoder[Profile]
    given Decoder[Profile] = deriveDecoder[Profile]

  private object AutomaticModel:
    final case class Address(street: String, postalCode: String)
    final case class Customer(name: String, address: Address)
    final case class Money(amount: BigDecimal, currency: String)
    final case class LineItem(sku: String, quantity: Int, price: Money)
    final case class Invoice(number: String, customer: Customer, items: Vector[LineItem])

  private object CommandModel:
    final case class Payload(values: List[Int])

    sealed trait Command

    object Command:
      final case class Create(id: String, payload: Payload) extends Command
      final case class Delete(id: String, hard: Boolean) extends Command

    given Encoder[Payload] = deriveEncoder[Payload]
    given Decoder[Payload] = deriveDecoder[Payload]
    given Encoder[Command.Create] = deriveEncoder[Command.Create]
    given Decoder[Command.Create] = deriveDecoder[Command.Create]
    given Encoder[Command.Delete] = deriveEncoder[Command.Delete]
    given Decoder[Command.Delete] = deriveDecoder[Command.Delete]
    given Encoder[Command] = deriveEncoder[Command]
    given Decoder[Command] = deriveDecoder[Command]

  private object CodecModel:
    final case class Metadata(kind: String, attributes: Map[String, String])
    final case class Payload(values: Vector[String])
    final case class Envelope(metadata: Metadata, payload: Payload, acknowledged: Boolean)

    given Codec.AsObject[Metadata] = deriveCodec[Metadata]
    given Codec.AsObject[Payload] = deriveCodec[Payload]
    given Codec.AsObject[Envelope] = deriveCodec[Envelope]
