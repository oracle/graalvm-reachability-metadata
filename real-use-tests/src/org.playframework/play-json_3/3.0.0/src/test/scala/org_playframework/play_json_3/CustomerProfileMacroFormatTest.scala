/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_playframework.play_json_3

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import play.api.libs.json.JsError
import play.api.libs.json.JsObject
import play.api.libs.json.JsPath
import play.api.libs.json.JsResult
import play.api.libs.json.JsSuccess
import play.api.libs.json.JsValue
import play.api.libs.json.Json
import play.api.libs.json.JsonConfiguration
import play.api.libs.json.JsonNaming
import play.api.libs.json.OFormat

/**
 * Compile-time macro derivation of case-class formats tuned through a `JsonConfiguration`.
 *
 * A customer-profile service exchanges profiles with a front end that speaks snake_case JSON.
 * Its formats are derived with `Json.configured`, so the naming strategy renames every field and
 * the `WithDefaultValues` option lets a client omit fields that have a Scala default. The service
 * writes a profile, reads it back from the wire text, fills defaults for a sparse sign-up payload,
 * and rejects a malformed payload with errors that point at the snake_case paths a client sent.
 */
class CustomerProfileMacroFormatTest {
  import CustomerProfileMacroFormatTest.*

  @Test
  def profilesRoundTripThroughSnakeCaseFormatsWithDefaults(): Unit = {
    val profile: CustomerProfile = CustomerProfile(
      customerId = 4711L,
      displayName = "Ada Lovelace",
      emailAddress = Some("ada@example.org"),
      shippingAddresses = List(
        Address("12 Analytical Row", "W1 2AB", "GB"),
        Address("1 Engine Street", "75001", "FR")
      ),
      marketingOptIn = true,
      loyaltyTier = "gold"
    )

    val written: JsValue = Json.toJson(profile)
    val expected: JsValue = Json.parse(
      """{
        |  "customer_id": 4711,
        |  "display_name": "Ada Lovelace",
        |  "email_address": "ada@example.org",
        |  "shipping_addresses": [
        |    {"street_line": "12 Analytical Row", "postal_code": "W1 2AB", "country_code": "GB"},
        |    {"street_line": "1 Engine Street", "postal_code": "75001", "country_code": "FR"}
        |  ],
        |  "marketing_opt_in": true,
        |  "loyalty_tier": "gold"
        |}""".stripMargin
    )
    assertEquals(expected, written)

    val wireText: String = Json.stringify(written)
    assertEquals(JsSuccess(profile), Json.parse(wireText).validate[CustomerProfile])

    val anonymous: JsObject = Json.toJsObject(profile.copy(emailAddress = None))
    assertEquals(false, anonymous.keys.contains("email_address"))

    val signUp: JsValue = Json.parse(
      """{"customer_id": 99, "display_name": "Grace Hopper", "shipping_addresses": []}"""
    )
    assertEquals(
      JsSuccess(CustomerProfile(99L, "Grace Hopper", None, Nil)),
      signUp.validate[CustomerProfile]
    )

    val malformed: JsValue = Json.parse(
      """{
        |  "customer_id": "not-a-number",
        |  "shipping_addresses": [{"street_line": "5 Byte Lane", "country_code": "US"}]
        |}""".stripMargin
    )
    val result: JsResult[CustomerProfile] = malformed.validate[CustomerProfile]
    val failedPaths: Set[JsPath] = result match {
      case JsError(errors) => errors.map(_._1).toSet
      case JsSuccess(value, _) => throw new AssertionError(s"Malformed payload was accepted as $value")
    }
    assertEquals(
      Set(
        JsPath \ "customer_id",
        JsPath \ "display_name",
        (JsPath \ "shipping_addresses")(0) \ "postal_code"
      ),
      failedPaths
    )
  }
}

object CustomerProfileMacroFormatTest {

  /** Field naming and default handling shared by every format the profile service derives. */
  given profileConfiguration: JsonConfiguration.Aux[Json.WithDefaultValues] =
    JsonConfiguration[Json.WithDefaultValues](naming = JsonNaming.SnakeCase)

  final case class Address(streetLine: String, postalCode: String, countryCode: String)

  object Address {
    given OFormat[Address] = Json.configured[Json.WithDefaultValues].format[Address]
  }

  final case class CustomerProfile(
      customerId: Long,
      displayName: String,
      emailAddress: Option[String],
      shippingAddresses: List[Address],
      marketingOptIn: Boolean = false,
      loyaltyTier: String = "bronze"
  )

  object CustomerProfile {
    given OFormat[CustomerProfile] = Json.configured[Json.WithDefaultValues].format[CustomerProfile]
  }
}
