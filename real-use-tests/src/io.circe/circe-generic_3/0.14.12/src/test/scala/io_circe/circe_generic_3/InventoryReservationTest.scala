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
import io.circe.parser.parse
import io.circe.syntax.*
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Timeout

/** Exercises derived codec composition with validated domain leaf codecs for inventory reservations. */
@Timeout(60)
class InventoryReservationTest {
  final case class StockCode(value: String)

  object StockCode {
    given Codec[StockCode] = Codec.from(
      Decoder.decodeString.emap { value =>
        if (value.startsWith("SKU-") && value.length > 4) Right(StockCode(value))
        else Left("Stock code must start with SKU- and have a suffix")
      },
      Encoder.encodeString.contramap[StockCode](_.value)
    )
  }

  final case class Units(value: Int)

  object Units {
    given Codec[Units] = Codec.from(
      Decoder.decodeInt.emap { value =>
        if (value > 0) Right(Units(value))
        else Left("Reserved units must be positive")
      },
      Encoder.encodeInt.contramap[Units](_.value)
    )
  }

  final case class ReservationLine(sku: StockCode, units: Units)

  object ReservationLine {
    given Codec.AsObject[ReservationLine] = deriveCodec[ReservationLine]
  }

  final case class Reservation(orderId: String, lines: List[ReservationLine])

  object Reservation {
    given Codec.AsObject[Reservation] = deriveCodec[Reservation]
  }

  @Test
  def acceptsScalarDomainValuesAndRejectsInvalidReservations(): Unit = {
    val incoming: String = """
      {
        "orderId": "order-82",
        "lines": [
          {"sku": "SKU-47", "units": 3},
          {"sku": "SKU-91", "units": 2}
        ]
      }
      """
    val reservation: Reservation = Decoder[Reservation].decodeJson(fixture(incoming)).fold(
      error => throw new AssertionError(s"Reservation was rejected: $error"),
      identity
    )
    assertThat(reservation).isEqualTo(Reservation("order-82", List(
      ReservationLine(StockCode("SKU-47"), Units(3)),
      ReservationLine(StockCode("SKU-91"), Units(2))
    )))
    assertThat(reservation.lines.map(_.units.value).sum).isEqualTo(5)

    val outbound: Json = reservation.asJson
    assertThat(outbound).isEqualTo(fixture(incoming))
    assertThat(Decoder[Reservation].decodeJson(outbound)).isEqualTo(Right(reservation))

    val invalidCode: DecodingFailure = rejection(incoming.replace("SKU-47", "unknown"))
    assertThat(invalidCode.message).isEqualTo("Stock code must start with SKU- and have a suffix")
    assertThat(CursorOp.opsToPath(invalidCode.history)).isEqualTo(".lines[0].sku")

    val invalidUnits: DecodingFailure = rejection(incoming.replace("\"units\": 3", "\"units\": 0"))
    assertThat(invalidUnits.message).isEqualTo("Reserved units must be positive")
    assertThat(CursorOp.opsToPath(invalidUnits.history)).isEqualTo(".lines[0].units")
  }

  private def rejection(input: String): DecodingFailure =
    Decoder[Reservation].decodeJson(fixture(input)).fold(
      identity,
      _ => throw new AssertionError("Invalid reservation was accepted")
    )

  private def fixture(input: String): Json = parse(input).fold(
    error => throw new AssertionError(s"Invalid inventory fixture: $error"),
    identity
  )
}
