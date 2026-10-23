/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_typelevel.cats_effect_3

import cats.effect.IO
import cats.effect.Ref
import cats.effect.unsafe.implicits.global
import cats.syntax.all._
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Timeout

import scala.concurrent.duration._

/** Exercises Ref atomic state updates for stock reservations shared by concurrent checkouts. */
class AtomicStockReservationTest {
  private case class Order(id: String, quantity: Int)
  private case class Reservation(orderId: String, quantity: Int)
  private case class Inventory(available: Int, reservations: Map[String, Reservation])

  private def reserve(order: Order, inventory: Ref[IO, Inventory]): IO[Option[Reservation]] = {
    inventory.modify { current =>
      current.reservations.get(order.id) match {
        case Some(existing) => (current, Some(existing))
        case None if current.available >= order.quantity =>
          val reservation: Reservation = Reservation(order.id, order.quantity)
          val updated: Inventory = Inventory(
            current.available - order.quantity,
            current.reservations + (order.id -> reservation)
          )
          (updated, Some(reservation))
        case None => (current, None)
      }
    }
  }

  @Test
  @Timeout(30)
  def concurrentCheckoutsCannotOversellAndAcceptedOrderRetriesAreIdempotent(): Unit = {
    val orders: List[Order] = (1 to 8).map(index => Order(s"ORDER-$index", 2)).toList
    val program: IO[(List[Option[Reservation]], Inventory, List[Option[Reservation]], Inventory)] = for {
      inventory <- Ref.of[IO, Inventory](Inventory(10, Map.empty))
      results <- orders.parTraverse(order => reserve(order, inventory))
      afterCheckout <- inventory.get
      acceptedIds = results.flatten.map(_.orderId).toSet
      // Retry only confirmed orders, as a client would after losing an acknowledgement.
      retried <- orders.filter(order => acceptedIds.contains(order.id))
        .parTraverse(order => reserve(order, inventory))
      afterRetry <- inventory.get
    } yield (results, afterCheckout, retried, afterRetry)

    val (results, afterCheckout, retried, afterRetry) =
      program.unsafeRunTimed(20.seconds).getOrElse {
        throw new AssertionError("Stock reservations did not finish within 20 seconds")
      }

    val accepted: List[Reservation] = results.flatten
    assertEquals(5, accepted.size, "Only five two-unit orders fit in the available stock")
    assertEquals(3, results.count(_.isEmpty), "Orders beyond the stock limit must be declined")
    assertEquals(0, afterCheckout.available)
    assertEquals(10, afterCheckout.reservations.values.map(_.quantity).sum)
    assertEquals(accepted.map(reservation => reservation.orderId -> reservation).toMap,
      afterCheckout.reservations, "Every confirmation must have exactly one inventory entry")
    assertTrue(orders.zip(results).forall { case (order, result) =>
      result.forall(reservation => reservation == Reservation(order.id, order.quantity))
    }, "A checkout must receive its own reservation")
    assertEquals(accepted, retried.flatten, "Retries must return the original confirmations")
    assertEquals(afterCheckout, afterRetry, "Retrying confirmed orders must not consume stock again")
  }
}
