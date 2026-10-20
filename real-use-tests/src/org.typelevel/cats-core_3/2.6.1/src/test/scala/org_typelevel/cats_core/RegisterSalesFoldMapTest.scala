/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_typelevel.cats_core

import cats.{Foldable, Monoid}
import cats.implicits.*
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.{Test, Timeout}

/** Exercises Cats Foldable and Monoid to merge register batches into a per-product sales report.
  * Sales and refunds contribute net totals while receipt sets count each receipt once per product.
  */
@Timeout(60)
class RegisterSalesFoldMapTest {
  private case class Sale(receipt: String, sku: String, units: Int, unitCents: Long)

  private case class Totals(netUnits: Int, netCents: Long, receipts: Set[String])

  private implicit val totalsMonoid: Monoid[Totals] = new Monoid[Totals] {
    override def empty: Totals = Totals(0, 0L, Set.empty)

    override def combine(left: Totals, right: Totals): Totals = Totals(
      left.netUnits + right.netUnits,
      left.netCents + right.netCents,
      left.receipts union right.receipts
    )
  }

  private def summarize(sales: List[Sale]): Map[String, Totals] = {
    Foldable[List].foldMap(sales) { sale =>
      Map(sale.sku -> Totals(sale.units, sale.units * sale.unitCents, Set(sale.receipt)))
    }
  }

  private def dailyReport(registerBatches: List[List[Sale]]): Map[String, Totals] = {
    registerBatches.map(summarize).combineAll
  }

  @Test
  def mergesOverlappingProductsAndRefundsWithoutOverwritingEarlierRegisters(): Unit = {
    val morning: List[Sale] = List(
      Sale("A-100", "BOOK", 2, 1200L),
      Sale("A-100", "BOOK", 1, 1200L),
      Sale("A-100", "PEN", 3, 150L)
    )
    val afternoon: List[Sale] = List(
      Sale("B-200", "BOOK", 1, 1000L),
      Sale("B-201", "BOOK", -1, 1200L),
      Sale("B-202", "PAD", 2, 400L)
    )
    val expected: Map[String, Totals] = Map(
      "BOOK" -> Totals(3, 3400L, Set("A-100", "B-200", "B-201")),
      "PEN" -> Totals(3, 450L, Set("A-100")),
      "PAD" -> Totals(2, 800L, Set("B-202"))
    )

    assertEquals(expected, dailyReport(List(morning, List.empty, afternoon)))
    assertEquals(expected, summarize(morning ++ afternoon))
    assertEquals(expected, dailyReport(List(afternoon, morning)))
    assertEquals(3, dailyReport(List(morning, afternoon))("BOOK").receipts.size)
  }

  @Test
  def handlesEmptyRegistersAndReceiptsSplitAcrossUploadBatches(): Unit = {
    val firstUpload: List[Sale] = List(Sale("C-300", "PEN", 2, 150L))
    val secondUpload: List[Sale] = List(
      Sale("C-300", "PEN", 1, 150L),
      Sale("C-301", "PEN", -3, 150L)
    )
    val expected: Map[String, Totals] = Map(
      "PEN" -> Totals(0, 0L, Set("C-300", "C-301"))
    )

    assertEquals(Map.empty[String, Totals], dailyReport(List.empty))
    assertEquals(Map.empty[String, Totals], dailyReport(List(List.empty, List.empty)))
    assertEquals(expected, dailyReport(List(firstUpload, secondUpload)))
    assertEquals(expected, summarize(firstUpload ++ secondUpload))
  }
}
