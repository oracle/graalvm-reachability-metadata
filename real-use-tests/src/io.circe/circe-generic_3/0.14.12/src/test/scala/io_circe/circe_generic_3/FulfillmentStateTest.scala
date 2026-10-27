/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package io_circe.circe_generic_3

import io.circe.Codec
import io.circe.Json
import io.circe.generic.semiauto.deriveCodec
import io.circe.parser.decode
import io.circe.parser.parse
import io.circe.syntax.*
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Timeout

/** Exercises Scala 3 enum codec derivation for singleton and data-carrying fulfillment states. */
@Timeout(60)
class FulfillmentStateTest {
  enum State {
    case Queued
    case Packing(station: String)
    case Shipped(trackingId: String)
    case Cancelled
  }

  object State {
    given Codec.AsObject[State] = deriveCodec[State]
  }

  final case class Job(orderId: String, state: State)

  object Job {
    given Codec.AsObject[Job] = deriveCodec[Job]
  }

  @Test
  def restoresJobStatesAndPublishesTheNextPackingAssignment(): Unit = {
    val stored: String = """
      [
        {"orderId": "order-11", "state": {"Queued": {}}},
        {"orderId": "order-12", "state": {"Packing": {"station": "bench-2"}}},
        {"orderId": "order-13", "state": {"Shipped": {"trackingId": "parcel-53"}}},
        {"orderId": "order-14", "state": {"Cancelled": {}}}
      ]
      """
    val jobs: List[Job] = decode[List[Job]](stored).fold(
      error => throw new AssertionError(s"Stored fulfillment jobs were rejected: $error"),
      identity
    )
    assertThat(jobs).isEqualTo(List(
      Job("order-11", State.Queued),
      Job("order-12", State.Packing("bench-2")),
      Job("order-13", State.Shipped("parcel-53")),
      Job("order-14", State.Cancelled)
    ))
    assertThat(jobs.asJson).isEqualTo(fixture(stored))

    val dashboard: List[String] = jobs.map { job =>
      val status: String = job.state match {
        case State.Queued => "waiting for assignment"
        case State.Packing(station) => s"packing at $station"
        case State.Shipped(trackingId) => s"track $trackingId"
        case State.Cancelled => "cancelled"
      }
      s"${job.orderId}: $status"
    }
    assertThat(dashboard).isEqualTo(List(
      "order-11: waiting for assignment",
      "order-12: packing at bench-2",
      "order-13: track parcel-53",
      "order-14: cancelled"
    ))

    val assigned: Job = jobs.head.copy(state = State.Packing("bench-1"))
    val outbound: Json = assigned.asJson
    assertThat(outbound).isEqualTo(fixture("""
      {"orderId": "order-11", "state": {"Packing": {"station": "bench-1"}}}
      """))
    assertThat(decode[Job](outbound.noSpaces)).isEqualTo(Right(assigned))
    assertThat(jobs.head.state).isEqualTo(State.Queued)

    // A retired state or a shipped state without tracking must not enter the work queue.
    assertThat(decode[Job]("""
      {"orderId": "order-15", "state": {"OnHold": {}}}
      """).isLeft).isTrue()
    assertThat(decode[Job]("""
      {"orderId": "order-16", "state": {"Shipped": {}}}
      """).isLeft).isTrue()
  }

  private def fixture(input: String): Json = parse(input).fold(
    error => throw new AssertionError(s"Invalid fulfillment fixture: $error"),
    identity
  )
}
