/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package io_circe.circe_generic_3

import io.circe.Codec
import io.circe.CursorOp
import io.circe.Decoder
import io.circe.DecodingFailure
import io.circe.Encoder
import io.circe.Json
import io.circe.generic.semiauto.deriveCodec
import io.circe.parser.decode
import io.circe.parser.parse
import io.circe.syntax.*
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Timeout

/** Exercises derived subtype codec composition for flat discriminator-based payment commands. */
@Timeout(60)
class PaymentCommandTest {
  sealed trait PaymentCommand

  final case class Authorize(paymentId: String, amount: BigDecimal, cardToken: String) extends PaymentCommand

  object Authorize {
    given Codec.AsObject[Authorize] = deriveCodec[Authorize]
  }

  final case class Refund(paymentId: String, amount: BigDecimal, reason: Option[String]) extends PaymentCommand

  object Refund {
    given Codec.AsObject[Refund] = deriveCodec[Refund]
  }

  object PaymentCommand {
    given Decoder[PaymentCommand] = Decoder.instance { cursor =>
      cursor.get[String]("kind").flatMap {
        case "authorize" => cursor.as[Authorize]
        case "refund" => cursor.as[Refund]
        case other => Left(DecodingFailure(s"Unsupported payment command: $other", cursor.history))
      }
    }

    given Encoder.AsObject[PaymentCommand] = Encoder.AsObject.instance {
      case command: Authorize => Encoder.AsObject[Authorize].encodeObject(command)
        .add("kind", Json.fromString("authorize"))
      case command: Refund => Encoder.AsObject[Refund].encodeObject(command)
        .add("kind", Json.fromString("refund"))
    }
  }

  @Test
  def routesFlatPaymentCommandsAndPublishesTheSameExternalSchema(): Unit = {
    val incoming: String = """
      [
        {
          "kind": "authorize", "paymentId": "payment-41",
          "amount": 25.50, "cardToken": "token-81"
        },
        {
          "kind": "refund", "paymentId": "payment-41",
          "amount": 5.50, "reason": "returned item"
        },
        {
          "kind": "refund", "paymentId": "payment-42",
          "amount": 2.00, "reason": null
        }
      ]
      """
    val commands: List[PaymentCommand] = decode[List[PaymentCommand]](incoming).fold(
      error => throw new AssertionError(s"Payment batch was rejected: $error"),
      identity
    )
    assertThat(commands).isEqualTo(List(
      Authorize("payment-41", BigDecimal("25.50"), "token-81"),
      Refund("payment-41", BigDecimal("5.50"), Some("returned item")),
      Refund("payment-42", BigDecimal("2.00"), None)
    ))

    val routing: List[String] = commands.map {
      case Authorize(paymentId, _, token) => s"authorize $paymentId using $token"
      case Refund(paymentId, _, reason) => s"refund $paymentId: ${reason.getOrElse("unspecified")}"
    }
    assertThat(routing).isEqualTo(List(
      "authorize payment-41 using token-81",
      "refund payment-41: returned item",
      "refund payment-42: unspecified"
    ))

    val published: Json = commands.asJson
    val expected: Json = parse(incoming).fold(
      error => throw new AssertionError(s"Invalid payment fixture: $error"),
      identity
    )
    assertThat(published).isEqualTo(expected)
    assertThat(decode[List[PaymentCommand]](published.noSpaces)).isEqualTo(Right(commands))

    // The discriminator selects a branch; another branch's fields cannot make it valid.
    val missingToken: DecodingFailure = rejection("""
      {"kind": "authorize", "paymentId": "payment-43", "amount": 12, "reason": "test"}
      """)
    assertThat(CursorOp.opsToPath(missingToken.history)).isEqualTo(".cardToken")
    assertThat(rejection("""
      {"kind": "chargeback", "paymentId": "payment-43", "amount": 12}
      """).message).isEqualTo("Unsupported payment command: chargeback")
    assertThat(decode[PaymentCommand]("""
      {"paymentId": "payment-43", "amount": 12, "cardToken": "token-82"}
      """).isLeft).isTrue()
  }

  private def rejection(input: String): DecodingFailure = decode[PaymentCommand](input).fold(
    {
      case error: DecodingFailure => error
      case error => throw new AssertionError(s"Expected a payment decoding failure: $error")
    },
    _ => throw new AssertionError("Invalid payment command was accepted")
  )
}
