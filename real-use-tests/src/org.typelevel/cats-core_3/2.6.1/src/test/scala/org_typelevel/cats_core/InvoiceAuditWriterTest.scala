/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_typelevel.cats_core

import cats.catsInstancesForId
import cats.data.Writer
import cats.syntax.all.*
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.{Test, Timeout}

/** Exercises Cats Writer to produce an invoice together with its ordered billing audit trail.
  * Line pricing, loyalty discounts, and delivery charges contribute entries without shared mutable logs.
  */
@Timeout(60)
class InvoiceAuditWriterTest {
  private case class Line(sku: String, quantity: Int, unitCents: Long)

  private case class Invoice(subtotalCents: Long, discountCents: Long, deliveryCents: Long) {
    def payableCents: Long = subtotalCents - discountCents + deliveryCents
  }

  private type Audited[A] = Writer[Vector[String], A]

  private def priceLine(line: Line): Audited[Long] = {
    val amount: Long = line.quantity * line.unitCents
    Writer(Vector(s"line ${line.sku}: ${line.quantity} x ${line.unitCents} = $amount"), amount)
  }

  private def loyaltyDiscount(subtotal: Long, member: Boolean): Audited[Long] = {
    if (member) {
      val discount: Long = subtotal / 10
      Writer(Vector(s"loyalty discount: $discount"), discount)
    } else {
      Writer(Vector("loyalty discount: not eligible"), 0L)
    }
  }

  private def deliveryCharge(afterDiscount: Long): Audited[Long] = {
    val charge: Long = if (afterDiscount >= 5000L) 0L else 500L
    Writer(Vector(s"delivery: $charge"), charge)
  }

  private def invoice(lines: List[Line], member: Boolean): Audited[Invoice] = {
    for {
      subtotal <- lines.traverse(priceLine).map(_.sum)
      discount <- loyaltyDiscount(subtotal, member)
      delivery <- deliveryCharge(subtotal - discount)
      bill = Invoice(subtotal, discount, delivery)
      _ <- Writer.tell[Vector[String]](Vector(s"invoice payable: ${bill.payableCents}"))
    } yield bill
  }

  @Test
  def returnsMemberInvoiceAndAuditInBillingOrder(): Unit = {
    val lines: List[Line] = List(Line("BOOK", 2, 2000L), Line("PEN", 3, 200L))
    val (audit, bill) = invoice(lines, member = true).run

    assertEquals(Invoice(4600L, 460L, 500L), bill)
    assertEquals(4640L, bill.payableCents)
    assertEquals(
      Vector(
        "line BOOK: 2 x 2000 = 4000",
        "line PEN: 3 x 200 = 600",
        "loyalty discount: 460",
        "delivery: 500",
        "invoice payable: 4640"
      ),
      audit
    )
  }

  @Test
  def keepsEachInvoiceAuditIndependentAndUsesDiscountedDeliveryThreshold(): Unit = {
    val lines: List[Line] = List(Line("CHAIR", 1, 5000L))
    val (regularAudit, regularBill) = invoice(lines, member = false).run
    val (memberAudit, memberBill) = invoice(lines, member = true).run

    assertEquals(Invoice(5000L, 0L, 0L), regularBill)
    assertEquals(5000L, regularBill.payableCents)
    assertEquals(
      Vector(
        "line CHAIR: 1 x 5000 = 5000",
        "loyalty discount: not eligible",
        "delivery: 0",
        "invoice payable: 5000"
      ),
      regularAudit
    )
    assertEquals(Invoice(5000L, 500L, 500L), memberBill)
    assertEquals(5000L, memberBill.payableCents)
    assertEquals(
      Vector(
        "line CHAIR: 1 x 5000 = 5000",
        "loyalty discount: 500",
        "delivery: 500",
        "invoice payable: 5000"
      ),
      memberAudit
    )
  }
}
