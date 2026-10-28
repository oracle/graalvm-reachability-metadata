/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package dev_zio.zio_3

import org.junit.jupiter.api.Assertions.{assertEquals, assertTrue}
import org.junit.jupiter.api.{Test, Timeout}
import zio.{Duration, Runtime, Unsafe, ZIO}
import zio.stm.{STM, TMap, TRef}

/** Exercises ZIO STM by atomically reserving inventory for competing customer orders. */
class InventoryReservationTest {
  private case class Order(id: String, lines: List[(String, Int)])
  private case class OutOfStock(sku: String)

  private def reserve(
      order: Order,
      inventory: TMap[String, Int],
      receipts: TRef[Vector[String]]
  ): STM[OutOfStock, Unit] =
    for {
      _ <- STM.foreach(order.lines) { case (sku, quantity) =>
        inventory.get(sku).flatMap {
          case Some(available) if available >= quantity =>
            inventory.put(sku, available - quantity)
          case _ => STM.fail(OutOfStock(sku))
        }
      }
      _ <- receipts.update(_ :+ order.id)
    } yield ()

  @Test
  @Timeout(60)
  def competingOrdersCannotOversellAndRejectedOrdersRollBack(): Unit = {
    val orders: List[Order] = List(
      Order("alice", List("espresso" -> 4, "oat-milk" -> 1)),
      Order("bob", List("espresso" -> 4, "oat-milk" -> 1))
    )
    val program = for {
      inventory <- TMap.make("espresso" -> 5, "oat-milk" -> 2).commit
      receipts <- TRef.make(Vector.empty[String]).commit
      outcomes <- ZIO.foreachPar(orders)(order => reserve(order, inventory, receipts).commit.either)
      // The first line can be reserved, but failure on the second must undo it.
      rejected <- reserve(
        Order("carol", List("oat-milk" -> 1, "espresso" -> 2)),
        inventory,
        receipts
      ).commit.either
      remaining <- inventory.toMap.commit
      accepted <- receipts.get.commit
    } yield (outcomes, rejected, remaining, accepted)

    val (outcomes, rejected, remaining, accepted) = Unsafe.unsafe { implicit unsafe =>
      Runtime.default.unsafe.run(
        program.timeoutFail(new IllegalStateException("Inventory checkout timed out"))(Duration.fromSeconds(30))
      ).getOrThrowFiberFailure()
    }

    assertEquals(1, outcomes.count(_.isRight))
    assertEquals(List(Left(OutOfStock("espresso"))), outcomes.filter(_.isLeft))
    assertEquals(Left(OutOfStock("espresso")), rejected)
    assertEquals(Map("espresso" -> 1, "oat-milk" -> 1), remaining)
    val winningOrders: List[String] = orders.zip(outcomes).collect {
      case (order, Right(_)) => order.id
    }
    assertEquals(winningOrders.toVector, accepted)
    assertTrue(accepted.nonEmpty)
  }
}
