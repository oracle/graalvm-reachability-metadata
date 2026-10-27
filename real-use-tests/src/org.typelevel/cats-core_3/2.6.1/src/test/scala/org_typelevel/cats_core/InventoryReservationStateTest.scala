/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_typelevel.cats_core

import cats.data.State
import cats.syntax.all.*
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.{Test, Timeout}

/** Exercises Cats State to thread inventory through a warehouse reservation workflow.
  * Successful reservations consume stock and issue receipts; rejected requests leave stock unchanged.
  */
@Timeout(60)
class InventoryReservationStateTest {
  private case class Reservation(number: Int, sku: String, units: Int)

  private case class Inventory(
    stock: Map[String, Int],
    nextNumber: Int,
    receipts: Vector[Reservation]
  )

  private type Result = Either[String, Reservation]

  private def reserve(sku: String, units: Int): State[Inventory, Result] = {
    State { inventory =>
      val available: Int = inventory.stock.getOrElse(sku, 0)
      if (units <= 0) {
        (inventory, Left("quantity must be positive"))
      } else if (available < units) {
        (inventory, Left(s"insufficient stock for $sku"))
      } else {
        val receipt: Reservation = Reservation(inventory.nextNumber, sku, units)
        val updated: Inventory = inventory.copy(
          stock = inventory.stock.updated(sku, available - units),
          nextNumber = inventory.nextNumber + 1,
          receipts = inventory.receipts :+ receipt
        )
        (updated, Right(receipt))
      }
    }
  }

  private def restock(sku: String, units: Int): State[Inventory, Unit] = {
    State.modify { inventory =>
      inventory.copy(stock = inventory.stock.updated(
        sku, inventory.stock.getOrElse(sku, 0) + units
      ))
    }
  }

  @Test
  def sequencesRestockingAndReservationsAgainstTheLatestInventory(): Unit = {
    val initial: Inventory = Inventory(Map("BOOK" -> 2), 100, Vector.empty)
    val workflow: State[Inventory, (Result, Result)] = for {
      first <- reserve("BOOK", 2)
      _ <- restock("BOOK", 5)
      second <- reserve("BOOK", 3)
    } yield (first, second)

    val (remaining, results) = workflow.run(initial).value
    val firstReceipt: Reservation = Reservation(100, "BOOK", 2)
    val secondReceipt: Reservation = Reservation(101, "BOOK", 3)

    assertEquals((Right(firstReceipt), Right(secondReceipt)), results)
    assertEquals(Map("BOOK" -> 2), remaining.stock)
    assertEquals(Vector(firstReceipt, secondReceipt), remaining.receipts)
    assertEquals(102, remaining.nextNumber)
    assertEquals(Map("BOOK" -> 2), initial.stock)
    assertEquals(Vector.empty[Reservation], initial.receipts)
  }

  @Test
  def processesABatchInOrderWithoutConsumingStockOrNumbersForRejectedRequests(): Unit = {
    val initial: Inventory = Inventory(Map("BOOK" -> 5, "PEN" -> 3), 200, Vector.empty)
    val requests: List[(String, Int)] = List(
      ("BOOK", 4),
      ("BOOK", 2),
      ("MISSING", 1),
      ("PEN", 0),
      ("BOOK", 1),
      ("PEN", 3)
    )
    val batch: State[Inventory, List[Result]] = requests.traverse { case (sku, units) =>
      reserve(sku, units)
    }

    val (remaining, results) = batch.run(initial).value
    val firstReceipt: Reservation = Reservation(200, "BOOK", 4)
    val secondReceipt: Reservation = Reservation(201, "BOOK", 1)
    val thirdReceipt: Reservation = Reservation(202, "PEN", 3)
    val expected: List[Result] = List(
      Right(firstReceipt),
      Left("insufficient stock for BOOK"),
      Left("insufficient stock for MISSING"),
      Left("quantity must be positive"),
      Right(secondReceipt),
      Right(thirdReceipt)
    )

    assertEquals(expected, results)
    assertEquals(Map("BOOK" -> 0, "PEN" -> 0), remaining.stock)
    assertEquals(Vector(firstReceipt, secondReceipt, thirdReceipt), remaining.receipts)
    assertEquals(203, remaining.nextNumber)
  }
}
