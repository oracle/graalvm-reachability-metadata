/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_typelevel.cats_effect_3

import cats.effect.Deferred
import cats.effect.IO
import cats.effect.Ref
import cats.effect.std.Semaphore
import cats.effect.unsafe.implicits.global
import cats.syntax.all._
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Timeout

import scala.concurrent.duration._

/** Exercises Semaphore concurrency limits for a batch of shipment bookings. */
class ShipmentConcurrencyLimitTest {
  private case class Shipment(orderId: String, parcels: Int)
  private case class Booking(orderId: String, labelCount: Int)

  @Test
  @Timeout(30)
  def shipmentBatchRespectsCarrierConcurrencyLimitAndCompletesEveryBooking(): Unit = {
    val shipments: List[Shipment] = List(
      Shipment("ORDER-101", 2),
      Shipment("ORDER-102", 1),
      Shipment("ORDER-103", 3),
      Shipment("ORDER-104", 1),
      Shipment("ORDER-105", 2),
      Shipment("ORDER-106", 4)
    )

    val program: IO[(List[Booking], Int, Int, Int)] = for {
      carrierSlots <- Semaphore[IO](2)
      active <- Ref.of[IO, Int](0)
      peak <- Ref.of[IO, Int](0)
      initialBookingsStarted <- Deferred[IO, Unit]
      allowCompletion <- Deferred[IO, Unit]
      book = (shipment: Shipment) => carrierSlots.permit.use { _ =>
        val process: IO[Booking] = for {
          running <- active.updateAndGet(_ + 1)
          _ <- peak.update(previous => previous.max(running))
          _ <- if (running == 2) initialBookingsStarted.complete(()).void else IO.unit
          // Hold the first two bookings open until the test observes the full batch limit.
          _ <- allowCompletion.get
        } yield Booking(shipment.orderId, shipment.parcels)
        process.guarantee(active.update(_ - 1))
      }
      result <- IO.both(
        shipments.parTraverse(book),
        initialBookingsStarted.get *> active.get.flatMap { running =>
          allowCompletion.complete(()).as(running)
        }
      )
      maximum <- peak.get
      remaining <- active.get
    } yield (result._1, result._2, maximum, remaining)

    val (bookings, initiallyActive, maximumActive, remainingActive) =
      program.unsafeRunTimed(20.seconds).getOrElse {
        throw new AssertionError("Shipment batch did not finish within 20 seconds")
      }

    val expected: List[Booking] = List(
      Booking("ORDER-101", 2),
      Booking("ORDER-102", 1),
      Booking("ORDER-103", 3),
      Booking("ORDER-104", 1),
      Booking("ORDER-105", 2),
      Booking("ORDER-106", 4)
    )
    assertEquals(expected, bookings)
    assertEquals(2, initiallyActive, "Two bookings must be able to run together")
    assertEquals(2, maximumActive, "The carrier must never receive more than two concurrent bookings")
    assertEquals(0, remainingActive, "All bookings must finish before the batch returns")
  }
}
