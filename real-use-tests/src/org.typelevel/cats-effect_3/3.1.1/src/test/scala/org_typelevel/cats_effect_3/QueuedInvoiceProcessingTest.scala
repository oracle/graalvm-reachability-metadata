/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_typelevel.cats_effect_3

import cats.effect.IO
import cats.effect.std.Queue
import cats.effect.unsafe.implicits.global
import cats.syntax.all._
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Timeout

import scala.concurrent.duration._

/** Exercises bounded Queue messaging to distribute invoice jobs to billing workers. */
class QueuedInvoiceProcessingTest {
  private case class Invoice(id: String, subtotalCents: Int, shippingCents: Int)

  private def consume(
      queue: Queue[IO, Option[Invoice]],
      completed: Vector[(String, Int)] = Vector.empty
  ): IO[Vector[(String, Int)]] = {
    queue.take.flatMap {
      case Some(invoice) =>
        val totalCents: Int = invoice.subtotalCents + invoice.shippingCents
        consume(queue, completed :+ (invoice.id -> totalCents))
      case None => IO.pure(completed)
    }
  }

  @Test
  @Timeout(30)
  def boundedWorkQueueDeliversEveryInvoiceOnceBeforeShutdown(): Unit = {
    val invoices: List[Invoice] = List(
      Invoice("INV-101", 1200, 350),
      Invoice("INV-102", 2900, 0),
      Invoice("INV-103", 750, 250),
      Invoice("INV-104", 5000, 0),
      Invoice("INV-105", 1800, 350),
      Invoice("INV-106", 950, 250)
    )

    val program: IO[Vector[(String, Int)]] = for {
      queue <- Queue.bounded[IO, Option[Invoice]](2)
      // Shutdown messages follow all jobs, so each worker drains work before exiting.
      publish = invoices.traverse_(invoice => queue.offer(Some(invoice))) *>
        queue.offer(None) *> queue.offer(None)
      results <- IO.both(publish, IO.both(consume(queue), consume(queue)))
    } yield results._2._1 ++ results._2._2

    val completed: Vector[(String, Int)] = program.unsafeRunTimed(20.seconds).getOrElse {
      throw new AssertionError("Invoice workers did not finish within 20 seconds")
    }

    val expected: Vector[(String, Int)] = Vector(
      "INV-101" -> 1550,
      "INV-102" -> 2900,
      "INV-103" -> 1000,
      "INV-104" -> 5000,
      "INV-105" -> 2150,
      "INV-106" -> 1200
    )
    assertEquals(expected, completed.sortBy(_._1))
  }
}
