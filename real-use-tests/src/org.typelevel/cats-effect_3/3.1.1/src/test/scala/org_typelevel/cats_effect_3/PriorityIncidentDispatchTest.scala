/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_typelevel.cats_effect_3

import cats.Order
import cats.effect.IO
import cats.effect.std.PQueue
import cats.effect.unsafe.implicits.global
import cats.syntax.all._
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Timeout

import scala.concurrent.duration._

/** Exercises PQueue priority scheduling for an incident desk with urgent incoming tickets. */
class PriorityIncidentDispatchTest {
  private case class Ticket(id: String, urgency: Int, sequence: Int, summary: String)

  // Lower urgency numbers dispatch first; intake sequence breaks ties independently of enqueue order.
  private implicit val ticketOrder: Order[Ticket] =
    Order.by[Ticket, (Int, Int)](ticket => (ticket.urgency, ticket.sequence))

  private def dispatch(queue: PQueue[IO, Ticket], position: Int): IO[String] = {
    queue.take.map(ticket => s"$position. ${ticket.id}: ${ticket.summary}")
  }

  @Test
  @Timeout(30)
  def urgentArrivalOvertakesBacklogAndEqualUrgencyKeepsIntakeOrder(): Unit = {
    val backlog: List[Ticket] = List(
      Ticket("INC-14", 1, 14, "Warehouse scanner unavailable"),
      Ticket("INC-10", 2, 10, "Update billing address"),
      Ticket("INC-13", 2, 13, "Resend monthly statement"),
      Ticket("INC-11", 0, 11, "Checkout unavailable"),
      Ticket("INC-12", 1, 12, "Shipment labels rejected")
    )
    val incoming: Ticket = Ticket("INC-15", 0, 15, "Payment processing unavailable")

    val program: IO[List[String]] = for {
      queue <- PQueue.bounded[IO, Ticket](backlog.size + 1)
      // Complete intake before dispatch, so the batch result never depends on fiber scheduling.
      _ <- backlog.parTraverse_(queue.offer)
      first <- dispatch(queue, 1)
      // A new outage arrives after the desk has assigned its first incident.
      _ <- queue.offer(incoming)
      remaining <- (2 to (backlog.size + 1)).toList.traverse(position => dispatch(queue, position))
    } yield first :: remaining

    val assignments: List[String] = program.unsafeRunTimed(20.seconds).getOrElse {
      throw new AssertionError("Incident dispatch did not finish within 20 seconds")
    }

    val expected: List[String] = List(
      "1. INC-11: Checkout unavailable",
      "2. INC-15: Payment processing unavailable",
      "3. INC-12: Shipment labels rejected",
      "4. INC-14: Warehouse scanner unavailable",
      "5. INC-10: Update billing address",
      "6. INC-13: Resend monthly statement"
    )
    assertEquals(expected, assignments,
      "Dispatch must prioritize outages over the backlog and preserve intake order within urgency")
  }
}
