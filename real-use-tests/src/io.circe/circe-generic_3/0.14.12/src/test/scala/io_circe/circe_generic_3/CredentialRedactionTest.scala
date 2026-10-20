/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package io_circe.circe_generic_3

import io.circe.Decoder
import io.circe.Encoder
import io.circe.Json
import io.circe.generic.semiauto.deriveDecoder
import io.circe.generic.semiauto.deriveEncoder
import io.circe.parser.decode
import io.circe.parser.parse
import io.circe.syntax.*
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Timeout

/** Exercises asymmetric derived object encoders for credential-redacted administrative responses. */
@Timeout(60)
class CredentialRedactionTest {
  final case class ClientRegistration(clientId: String, clientSecret: String, scopes: List[String])

  object ClientRegistration {
    given Decoder[ClientRegistration] = deriveDecoder[ClientRegistration]
    given Encoder.AsObject[ClientRegistration] = deriveEncoder[ClientRegistration]
      .mapJsonObject(_.remove("clientSecret"))
  }

  final case class RegistrationReceipt(registrationId: String, client: ClientRegistration)

  object RegistrationReceipt {
    given Encoder.AsObject[RegistrationReceipt] = deriveEncoder[RegistrationReceipt]
  }

  @Test
  def acceptsASecretButOmitsItFromTheAdministrativeReceipt(): Unit = {
    val incoming: String = """
      {
        "clientId": "billing-worker",
        "clientSecret": "private-billing-credential",
        "scopes": ["invoices:read", "receipts:write"]
      }
      """
    val registration: ClientRegistration = decode[ClientRegistration](incoming).fold(
      error => throw new AssertionError(s"Client registration was rejected: $error"),
      identity
    )
    assertThat(registration).isEqualTo(ClientRegistration(
      "billing-worker",
      "private-billing-credential",
      List("invoices:read", "receipts:write")
    ))

    val receipt: RegistrationReceipt = RegistrationReceipt("registration-42", registration)
    val response: Json = receipt.asJson
    val expected: Json = parse("""
      {
        "registrationId": "registration-42",
        "client": {
          "clientId": "billing-worker",
          "scopes": ["invoices:read", "receipts:write"]
        }
      }
      """).fold(error => throw new AssertionError(s"Invalid receipt fixture: $error"), identity)

    assertThat(response).isEqualTo(expected)
    assertThat(response.noSpaces).doesNotContain("clientSecret", registration.clientSecret)
    assertThat(response.hcursor.downField("client").as[ClientRegistration].isLeft).isTrue()

    // Rendering the public view must not discard the credential needed by the service.
    assertThat(registration.clientSecret).isEqualTo("private-billing-credential")
    assertThat(registration.asJson).isEqualTo(
      expected.hcursor.downField("client").focus
        .getOrElse(throw new AssertionError("Receipt has no client view"))
    )
  }
}
