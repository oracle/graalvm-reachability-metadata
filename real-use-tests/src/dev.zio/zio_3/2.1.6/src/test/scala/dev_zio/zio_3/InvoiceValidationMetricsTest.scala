/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package dev_zio.zio_3

import java.util.UUID
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.{Test, Timeout}
import zio.{Chunk, Duration, IO, Runtime, Unsafe, ZIO}
import zio.metrics.{Metric, MetricKeyType}

/** Exercises ZIO metrics by reporting accepted and rejected invoices and the distribution of accepted amounts. */
class InvoiceValidationMetricsTest {
  private case class Invoice(id: String, amountCents: Long)

  private class InvoiceValidator(batch: String) {
    val accepted = Metric.counter("invoice_validation_total")
      .tagged("batch", batch).tagged("outcome", "accepted")
    val rejected = Metric.counter("invoice_validation_total")
      .tagged("batch", batch).tagged("outcome", "rejected")
    val amounts = Metric.histogram(
      "invoice_accepted_amount_cents",
      MetricKeyType.Histogram.Boundaries.fromChunk(Chunk(500.0, 1500.0, 5000.0))
    ).tagged("batch", batch)

    def validate(invoice: Invoice): IO[String, Invoice] = {
      val validation: IO[String, Invoice] =
        if (invoice.amountCents <= 0) ZIO.fail(s"Nonpositive amount: ${invoice.id}")
        else ZIO.succeed(invoice)

      validation @@ accepted.trackSuccessWith((_: Invoice) => 1L) @@
        rejected.trackErrorWith((_: String) => 1L) @@
        amounts.trackSuccessWith((valid: Invoice) => valid.amountCents.toDouble)
    }
  }

  @Test
  @Timeout(60)
  def batchMetricsDistinguishRejectionsAndSummarizeOnlyAcceptedAmounts(): Unit = {
    // Metrics are process-wide; a batch label isolates this run from other application instances.
    val validator: InvoiceValidator = new InvoiceValidator(UUID.randomUUID().toString)
    val invoices: List[Invoice] = List(
      Invoice("INV-101", 400L),
      Invoice("INV-102", -50L),
      Invoice("INV-103", 1200L),
      Invoice("INV-104", 0L),
      Invoice("INV-105", 2600L)
    )
    val program = for {
      outcomes <- ZIO.foreachPar(invoices)(invoice => validator.validate(invoice).either)
      accepted <- validator.accepted.value
      rejected <- validator.rejected.value
      amounts <- validator.amounts.value
    } yield (outcomes, accepted, rejected, amounts)

    val (outcomes, accepted, rejected, amounts) = Unsafe.unsafe { implicit unsafe =>
      Runtime.default.unsafe.run(
        program.timeoutFail(new IllegalStateException("Invoice validation timed out"))(Duration.fromSeconds(30))
      ).getOrThrowFiberFailure()
    }

    assertEquals(
      List(
        Right(Invoice("INV-101", 400L)),
        Left("Nonpositive amount: INV-102"),
        Right(Invoice("INV-103", 1200L)),
        Left("Nonpositive amount: INV-104"),
        Right(Invoice("INV-105", 2600L))
      ),
      outcomes
    )
    assertEquals(3.0, accepted.count, 0.0)
    assertEquals(2.0, rejected.count, 0.0)
    assertEquals(3L, amounts.count)
    assertEquals(400.0, amounts.min, 0.0)
    assertEquals(2600.0, amounts.max, 0.0)
    assertEquals(4200.0, amounts.sum, 0.0)
  }
}
