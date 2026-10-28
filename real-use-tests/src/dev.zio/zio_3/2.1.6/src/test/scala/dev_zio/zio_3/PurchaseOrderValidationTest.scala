/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package dev_zio.zio_3

import java.util.Locale
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.{Test, Timeout}
import zio.{Duration, IO, Runtime, Unsafe, ZIO}

/** Exercises ZIO parallel error accumulation by validating purchase-order lines before producing a checkout quote. */
class PurchaseOrderValidationTest {
  private case class OrderLine(number: Int, sku: String, quantity: Int)
  private case class LineError(number: Int, message: String)
  private case class PricedLine(number: Int, sku: String, quantity: Int, subtotalCents: Long)
  private case class Quote(lines: List[PricedLine], totalCents: Long)

  private class OrderPricing(catalog: Map[String, Long]) {
    private def price(line: OrderLine): IO[LineError, PricedLine] = {
      val sku: String = line.sku.trim.toUpperCase(Locale.ROOT)
      for {
        unitPrice <- ZIO.fromOption(catalog.get(sku))
          .orElseFail(LineError(line.number, s"Unknown product: $sku"))
        _ <- ZIO.fail(LineError(line.number, "Quantity must be positive")).when(line.quantity <= 0)
      } yield PricedLine(line.number, sku, line.quantity, unitPrice * line.quantity)
    }

    def quote(lines: List[OrderLine]): IO[List[LineError], Quote] =
      ZIO.validatePar(lines)(price).map { priced =>
        Quote(priced, priced.map(_.subtotalCents).sum)
      }
  }

  @Test
  @Timeout(60)
  def reportsAllRejectedLinesAndQuotesTheCorrectedOrderInInputOrder(): Unit = {
    val pricing: OrderPricing = new OrderPricing(Map("PEN" -> 125L, "BOOK" -> 450L, "FOLDER" -> 200L))
    val submitted: List[OrderLine] = List(
      OrderLine(1, " pen ", 4),
      OrderLine(2, "unknown", 2),
      OrderLine(3, "book", 0),
      OrderLine(4, "folder", -1)
    )
    val corrected: List[OrderLine] = List(
      OrderLine(1, " pen ", 4),
      OrderLine(2, "folder", 2),
      OrderLine(3, "book", 3),
      OrderLine(4, "folder", 1)
    )
    val program = for {
      rejected <- pricing.quote(submitted).either
      accepted <- pricing.quote(corrected)
    } yield (rejected, accepted)

    val (rejected, accepted) = Unsafe.unsafe { implicit unsafe =>
      Runtime.default.unsafe.run(
        program.timeoutFail(new IllegalStateException("Order validation timed out"))(Duration.fromSeconds(30))
      ).getOrThrowFiberFailure()
    }

    assertEquals(
      Left(Set(
        LineError(2, "Unknown product: UNKNOWN"),
        LineError(3, "Quantity must be positive"),
        LineError(4, "Quantity must be positive")
      )),
      rejected.left.map(_.toSet)
    )
    assertEquals(
      Quote(
        List(
          PricedLine(1, "PEN", 4, 500L),
          PricedLine(2, "FOLDER", 2, 400L),
          PricedLine(3, "BOOK", 3, 1350L),
          PricedLine(4, "FOLDER", 1, 200L)
        ),
        2450L
      ),
      accepted
    )
  }
}
