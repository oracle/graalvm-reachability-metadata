/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package co_fs2.fs2_core_3

import cats.effect.{IO, Resource}
import cats.effect.unsafe.implicits.global
import cats.syntax.all.*
import fs2.Stream
import fs2.concurrent.Topic
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.{Test, Timeout}

import scala.concurrent.duration.*

/** Exercises FS2 topic publish/subscribe to update inventory and an audit feed from the same events. */
class InventoryTopicBroadcastTest {
  private case class StockChange(reference: String, sku: String, quantity: Int)
  private case class InventoryViews(stock: Map[String, Int], audit: Vector[String])

  @Test
  @Timeout(60)
  def broadcastsEveryStockChangeToBothConsumersBeforeClosing(): Unit = {
    val changes: Vector[StockChange] = Vector(
      StockChange("delivery-100", "mug", 10),
      StockChange("delivery-101", "tea", 8),
      StockChange("order-200", "mug", -3),
      StockChange("order-201", "tea", -2),
      StockChange("order-202", "mug", -1)
    )

    val program: IO[InventoryViews] = Resource
      .make(Topic[IO, StockChange])(_.close.void)
      .use { topic =>
        // Acquire both subscriptions before publishing, without relying on scheduling delays.
        val subscriptions: Resource[IO, (Stream[IO, StockChange], Stream[IO, StockChange])] =
          for {
            stockUpdates <- topic.subscribeAwait(2)
            auditUpdates <- topic.subscribeAwait(2)
          } yield (stockUpdates, auditUpdates)

        subscriptions.use { case (stockUpdates, auditUpdates) =>
          val stock: IO[Map[String, Int]] = stockUpdates
            .fold(Map.empty[String, Int]) { (current, change) =>
              current.updated(change.sku, current.getOrElse(change.sku, 0) + change.quantity)
            }
            .compile
            .lastOrError
          val audit: IO[Vector[String]] = auditUpdates
            .map(change => s"${change.reference}:${change.sku}:${change.quantity}")
            .compile
            .toVector
          val publish: IO[Unit] = Stream
            .emits(changes)
            .covary[IO]
            .evalMap(topic.publish1)
            .compile
            .drain >> topic.close.void

          (stock, audit, publish).parTupled.map { case (balances, entries, _) =>
            InventoryViews(balances, entries)
          }
        }
      }

    val views: InventoryViews = program.timeout(20.seconds).unsafeRunSync()

    assertEquals(Map("mug" -> 6, "tea" -> 6), views.stock)
    assertEquals(Vector(
      "delivery-100:mug:10",
      "delivery-101:tea:8",
      "order-200:mug:-3",
      "order-201:tea:-2",
      "order-202:mug:-1"
    ), views.audit)
  }
}
