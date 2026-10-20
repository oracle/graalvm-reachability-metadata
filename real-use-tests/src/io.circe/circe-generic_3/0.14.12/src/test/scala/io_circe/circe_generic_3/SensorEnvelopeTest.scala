/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package io_circe.circe_generic_3

import io.circe.CursorOp
import io.circe.Decoder
import io.circe.DecodingFailure
import io.circe.generic.semiauto.deriveDecoder
import io.circe.parser.decode
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Timeout

/** Exercises derived decoder cursor preparation for importing vendor-wrapped sensor readings. */
@Timeout(60)
class SensorEnvelopeTest {
  final case class Reading(sensorId: String, celsius: BigDecimal, location: Option[String])

  object Reading {
    given Decoder[Reading] = deriveDecoder[Reading]
      .prepare(_.downField("payload").downField("measurement"))
  }

  @Test
  def importsMeasurementsWithoutExposingVendorEnvelopesToTheApplication(): Unit = {
    val incoming: String = """
      [
        {
          "vendorRevision": 4,
          "sensorId": "envelope-not-the-sensor",
          "payload": {
            "measurement": {"sensorId": "cold-room", "celsius": 3.5, "location": "warehouse"},
            "diagnostics": {"batteryPercent": 82}
          }
        },
        {
          "vendorRevision": 4,
          "payload": {
            "measurement": {"sensorId": "loading-bay", "celsius": 9.25}
          }
        }
      ]
      """
    val readings: List[Reading] = decode[List[Reading]](incoming).fold(
      error => throw new AssertionError(s"Sensor batch was rejected: $error"),
      identity
    )
    assertThat(readings).isEqualTo(List(
      Reading("cold-room", BigDecimal("3.5"), Some("warehouse")),
      Reading("loading-bay", BigDecimal("9.25"), None)
    ))

    val alerts: List[String] = readings.filter(_.celsius > BigDecimal(8)).map { reading =>
      s"${reading.sensorId}: ${reading.celsius} C at ${reading.location.getOrElse("unassigned")}"
    }
    assertThat(alerts).isEqualTo(List("loading-bay: 9.25 C at unassigned"))

    // Report the vendor's full field path so an operator can locate the bad measurement.
    val invalid: String = """
      {"payload": {"measurement": {"sensorId": "cold-room", "celsius": "offline"}}}
      """
    val failure: DecodingFailure = decode[Reading](invalid).fold(
      {
        case error: DecodingFailure => error
        case error => throw new AssertionError(s"Expected a measurement decoding failure: $error")
      },
      _ => throw new AssertionError("Invalid temperature was accepted")
    )
    assertThat(CursorOp.opsToPath(failure.history)).isEqualTo(".payload.measurement.celsius")

    // Neither a missing envelope nor fields placed beside it are valid vendor measurements.
    assertThat(decode[Reading]("""
      {"sensorId": "cold-room", "celsius": 3.5}
      """).isLeft).isTrue()
    assertThat(decode[Reading]("""
      {"payload": {"diagnostics": {"sensorId": "cold-room", "celsius": 3.5}}}
      """).isLeft).isTrue()
  }
}
