/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_typelevel.cats_core

import cats.Align
import cats.data.Ior
import cats.implicits.*
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.{Test, Timeout}

/** Exercises Cats Align to reconcile invoice and payment ledgers by their shared references.
  * The report retains unmatched entries from either ledger as well as payment discrepancies.
  */
@Timeout(60)
class PaymentReconciliationAlignTest {
  private type Ledger[A] = Map[String, A]

  private case class Reconciliation(status: String, outstandingCents: Long)

  private def reconcile(
    invoices: Ledger[Long],
    payments: Ledger[Long]
  ): Ledger[Reconciliation] = {
    Align[Ledger].alignWith(invoices, payments) {
      case Ior.Left(invoiced) => Reconciliation("awaiting-payment", invoiced)
      case Ior.Right(paid) => Reconciliation("unmatched-payment", -paid)
      case Ior.Both(invoiced, paid) =>
        val balance: Long = invoiced - paid
        val status: String = if (balance == 0L) "settled"
          else if (balance > 0L) "underpaid"
          else "overpaid"
        Reconciliation(status, balance)
    }
  }

  @Test
  def producesACompleteReportWithoutDroppingEitherLedgersUnmatchedReferences(): Unit = {
    val invoices: Ledger[Long] = Map(
      "INV-100" -> 12000L,
      "INV-101" -> 8500L,
      "INV-102" -> 3000L,
      "INV-103" -> 4500L
    )
    val payments: Ledger[Long] = Map(
      "INV-100" -> 12000L,
      "INV-101" -> 8000L,
      "INV-102" -> 3500L,
      "UNKNOWN-99" -> 2000L
    )
    val expected: Ledger[Reconciliation] = Map(
      "INV-100" -> Reconciliation("settled", 0L),
      "INV-101" -> Reconciliation("underpaid", 500L),
      "INV-102" -> Reconciliation("overpaid", -500L),
      "INV-103" -> Reconciliation("awaiting-payment", 4500L),
      "UNKNOWN-99" -> Reconciliation("unmatched-payment", -2000L)
    )

    assertEquals(expected, reconcile(invoices, payments))
    assertEquals(
      expected.updated("INV-101", Reconciliation("settled", 0L))
        .updated("INV-103", Reconciliation("settled", 0L)),
      reconcile(invoices, payments.updated("INV-101", 8500L).updated("INV-103", 4500L))
    )
    assertEquals(expected, reconcile(invoices, payments))
  }

  @Test
  def distinguishesMissingRecordsFromPresentZeroAmounts(): Unit = {
    val invoices: Ledger[Long] = Map("FREE-1" -> 0L, "FREE-2" -> 0L)
    val payments: Ledger[Long] = Map("FREE-1" -> 0L, "UNASSIGNED" -> 0L)

    assertEquals(
      Map(
        "FREE-1" -> Reconciliation("settled", 0L),
        "FREE-2" -> Reconciliation("awaiting-payment", 0L),
        "UNASSIGNED" -> Reconciliation("unmatched-payment", 0L)
      ),
      reconcile(invoices, payments)
    )
    assertEquals(
      Map(
        "FREE-1" -> Reconciliation("awaiting-payment", 0L),
        "FREE-2" -> Reconciliation("awaiting-payment", 0L)
      ),
      reconcile(invoices, Map.empty)
    )
    assertEquals(
      Map(
        "FREE-1" -> Reconciliation("unmatched-payment", 0L),
        "UNASSIGNED" -> Reconciliation("unmatched-payment", 0L)
      ),
      reconcile(Map.empty, payments)
    )
    assertEquals(Map.empty[String, Reconciliation], reconcile(Map.empty, Map.empty))
  }
}
