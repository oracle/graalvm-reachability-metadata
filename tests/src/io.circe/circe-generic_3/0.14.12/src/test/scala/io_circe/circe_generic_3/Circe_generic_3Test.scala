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
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class Circe_generic_3Test {
  @Test
  def semiautomaticDerivationRoundTripsNestedProducts(): Unit = {
    given Encoder[GeoPoint] = deriveEncoder[GeoPoint]
    given Decoder[GeoPoint] = deriveDecoder[GeoPoint]
    given Encoder[PostalAddress] = deriveEncoder[PostalAddress]
    given Decoder[PostalAddress] = deriveDecoder[PostalAddress]
    given Encoder[CustomerProfile] = deriveEncoder[CustomerProfile]
    given Decoder[CustomerProfile] = deriveDecoder[CustomerProfile]

    val profile: CustomerProfile = CustomerProfile(
      id = 42L,
      name = "Ada Lovelace",
      address = PostalAddress(
        street = "1 Algorithm Avenue",
        postcode = "N1 9GU",
        location = GeoPoint(
          latitudeMicrodegrees = 51507400,
          longitudeMicrodegrees = -127800
        )
      ),
      tags = List("founder", "analyst"),
      preferences = Map("newsletter" -> true, "beta" -> false),
      notes = Some("first programmer")
    )

    val json: Json = Encoder[CustomerProfile].apply(profile)

    assertEquals(Some(Json.fromString("Ada Lovelace")), json.hcursor.downField("name").focus)
    assertEquals(
      Some(Json.fromString("N1 9GU")),
      json.hcursor.downField("address").downField("postcode").focus
    )
    assertEquals(Right(profile), Decoder[CustomerProfile].decodeJson(json))
  }

  @Test
  def semiautomaticCodecDerivationHandlesSealedTraitHierarchies(): Unit = {
    given Codec[CardPayment] = deriveCodec[CardPayment]
    given Codec[BankTransfer] = deriveCodec[BankTransfer]
    given Codec[PaymentMethod] = deriveCodec[PaymentMethod]
    given Codec[Invoice] = deriveCodec[Invoice]

    val invoice: Invoice = Invoice(
      reference = "INV-2026-06",
      methods = List(
        CardPayment(last4 = "4242", network = "visa"),
        BankTransfer(iban = "GB82WEST12345698765432")
      ),
      paid = false
    )

    val json: Json = invoice.asJson

    assertTrue(json.noSpaces.contains("CardPayment"))
    assertTrue(json.noSpaces.contains("BankTransfer"))
    assertEquals(Right(invoice), json.as[Invoice])
  }

  @Test
  def automaticDerivationProvidesEncodersAndDecodersFromImports(): Unit = {
    import io.circe.generic.auto.*

    val book: AutoBook = AutoBook(
      title = "Native Image Integration Testing",
      authors = Vector(AutoAuthor("Grace", "Hopper"), AutoAuthor("Katherine", "Johnson")),
      metadata = Some(AutoMetadata(isbn = "978-0-00-000000-2", pages = 256))
    )

    val json: Json = book.asJson

    assertEquals(
      Some(Json.fromString("Native Image Integration Testing")),
      json.hcursor.downField("title").focus
    )
    assertEquals(
      Some(Json.fromInt(256)),
      json.hcursor.downField("metadata").downField("pages").focus
    )
    assertEquals(Right(book), json.as[AutoBook])
  }

  @Test
  def derivedDecodersReturnFailuresForInvalidFieldTypes(): Unit = {
    given Decoder[GeoPoint] = deriveDecoder[GeoPoint]
    given Decoder[PostalAddress] = deriveDecoder[PostalAddress]
    given Decoder[CustomerProfile] = deriveDecoder[CustomerProfile]

    val invalidProfile: Json = Json.obj(
      "id" -> Json.fromString("not a number"),
      "name" -> Json.fromString("Ada Lovelace"),
      "address" -> Json.obj(
        "street" -> Json.fromString("1 Algorithm Avenue"),
        "postcode" -> Json.fromString("N1 9GU"),
        "location" -> Json.obj(
          "latitudeMicrodegrees" -> Json.fromInt(51507400),
          "longitudeMicrodegrees" -> Json.fromInt(-127800)
        )
      ),
      "tags" -> Json.arr(Json.fromString("founder")),
      "preferences" -> Json.obj("newsletter" -> Json.fromBoolean(true)),
      "notes" -> Json.Null
    )

    val result: Decoder.Result[CustomerProfile] =
      Decoder[CustomerProfile].decodeJson(invalidProfile)

    assertTrue(result.isLeft)
    assertTrue(result.swap.exists(_.history.nonEmpty))
  }

  private final case class GeoPoint(
      latitudeMicrodegrees: Int,
      longitudeMicrodegrees: Int
  )

  private final case class PostalAddress(street: String, postcode: String, location: GeoPoint)

  private final case class CustomerProfile(
      id: Long,
      name: String,
      address: PostalAddress,
      tags: List[String],
      preferences: Map[String, Boolean],
      notes: Option[String]
  )

  private sealed trait PaymentMethod

  private final case class CardPayment(last4: String, network: String) extends PaymentMethod

  private final case class BankTransfer(iban: String) extends PaymentMethod

  private final case class Invoice(reference: String, methods: List[PaymentMethod], paid: Boolean)

  private final case class AutoAuthor(firstName: String, lastName: String)

  private final case class AutoMetadata(isbn: String, pages: Int)

  private final case class AutoBook(
      title: String,
      authors: Vector[AutoAuthor],
      metadata: Option[AutoMetadata]
  )
}
