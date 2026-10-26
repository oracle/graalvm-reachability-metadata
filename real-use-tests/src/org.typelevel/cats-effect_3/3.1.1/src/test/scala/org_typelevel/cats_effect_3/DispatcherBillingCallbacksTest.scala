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
import cats.effect.Resource
import cats.effect.std.Dispatcher
import cats.effect.unsafe.implicits.global
import cats.syntax.all._
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Timeout

import java.util.concurrent.CompletableFuture
import java.util.concurrent.CompletionException
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import scala.concurrent.duration._

/** Exercises Dispatcher callback integration to record asynchronous Java billing results in IO. */
class DispatcherBillingCallbacksTest {
  private case class Order(id: String, quantity: Int, unitPriceCents: Int)

  private def calculateTotal(order: Order): Int = {
    require(order.quantity > 0, "quantity must be positive")
    order.quantity * order.unitPriceCents
  }

  private def failureMessage(error: Throwable): String = error match {
    case wrapped: CompletionException => wrapped.getCause.getMessage
    case other => other.getMessage
  }

  @Test
  @Timeout(30)
  def javaCompletionCallbacksRecordBothAcceptedAndRejectedOrders(): Unit = {
    val orders: List[Order] = List(
      Order("ORDER-101", 2, 750),
      Order("ORDER-102", 3, 400),
      Order("ORDER-103", 0, 600),
      Order("ORDER-104", 1, 1250)
    )
    val workers: Resource[IO, ExecutorService] = Resource.make(
      IO.blocking(Executors.newFixedThreadPool(2))
    )(executor => IO.blocking(executor.shutdown()).void)

    val program: IO[Map[String, Either[String, Int]]] = workers.use { executor =>
      Dispatcher[IO].use { dispatcher =>
        for {
          results <- Ref.of[IO, Map[String, Either[String, Int]]](Map.empty)
          allRecorded <- Deferred[IO, Unit]
          callbacks <- IO.delay {
            orders.map { order =>
              CompletableFuture.supplyAsync(() => calculateTotal(order), executor)
                .handle[Unit] { (total, error) =>
                  val outcome: Either[String, Int] =
                    if (error == null) Right(total) else Left(failureMessage(error))
                  val record: IO[Unit] = results.updateAndGet(_ + (order.id -> outcome))
                    .flatMap { current =>
                      if (current.size == orders.size) allRecorded.complete(()).void else IO.unit
                    }
                  dispatcher.unsafeRunAndForget(record)
                }
            }
          }
          // Java callback completion only means the IO was submitted, not that it ran.
          _ <- IO.blocking(callbacks.foreach(_.get(10, TimeUnit.SECONDS)))
          _ <- allRecorded.get
          recorded <- results.get
        } yield recorded
      }
    }

    val recorded: Map[String, Either[String, Int]] =
      program.unsafeRunTimed(20.seconds).getOrElse {
        throw new AssertionError("Billing callbacks did not finish within 20 seconds")
      }

    val expected: Map[String, Either[String, Int]] = Map(
      "ORDER-101" -> Right(1500),
      "ORDER-102" -> Right(1200),
      "ORDER-103" -> Left("requirement failed: quantity must be positive"),
      "ORDER-104" -> Right(1250)
    )
    assertEquals(expected, recorded, "Every completion must update billing state, including failures")
  }
}
