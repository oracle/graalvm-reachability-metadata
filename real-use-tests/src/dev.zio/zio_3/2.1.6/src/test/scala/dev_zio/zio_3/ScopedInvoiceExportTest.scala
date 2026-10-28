/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package dev_zio.zio_3

import java.nio.charset.StandardCharsets.UTF_8
import java.nio.file.{Files, Path}
import org.junit.jupiter.api.Assertions.{assertEquals, assertFalse, assertTrue}
import org.junit.jupiter.api.{Test, Timeout}
import org.junit.jupiter.api.io.TempDir
import scala.jdk.CollectionConverters.*
import zio.{Duration, Runtime, Unsafe, ZIO}

/** Exercises ZIO scoped resource management by publishing CSV invoices and cleaning up rejected exports. */
class ScopedInvoiceExportTest {
  private case class Invoice(id: String, amountCents: Long)

  private def exportInvoices(
      directory: Path,
      filename: String,
      invoices: List[Invoice]
  ): ZIO[Any, Throwable, Path] = ZIO.scoped {
    for {
      staging <- ZIO.acquireRelease(
        ZIO.attemptBlocking(Files.createTempFile(directory, ".invoice-", ".csv"))
      )(path => ZIO.attemptBlocking(Files.deleteIfExists(path)).orDie)
      // Close the writer before publishing; the outer scope owns staging-file cleanup.
      _ <- ZIO.scoped {
        for {
          writer <- ZIO.acquireRelease(
            ZIO.attemptBlocking(Files.newBufferedWriter(staging, UTF_8))
          )(writer => ZIO.attemptBlocking(writer.close()).orDie)
          _ <- ZIO.attemptBlocking(writer.write("invoice_id,amount_cents\n"))
          _ <- ZIO.foreachDiscard(invoices) { invoice =>
            if (invoice.amountCents < 0)
              ZIO.fail(new IllegalArgumentException(s"Negative amount for ${invoice.id}"))
            else
              ZIO.attemptBlocking(writer.write(s"${invoice.id},${invoice.amountCents}\n"))
          }
        } yield ()
      }
      published <- ZIO.attemptBlocking(Files.move(staging, directory.resolve(filename)))
    } yield published
  }

  @Test
  @Timeout(60)
  def publishesCompleteExportsAndRemovesPartialFiles(@TempDir directory: Path): Unit = {
    val program = for {
      published <- exportInvoices(
        directory,
        "accepted.csv",
        List(Invoice("INV-100", 2500), Invoice("INV-101", 1750))
      )
      rejected <- exportInvoices(
        directory,
        "rejected.csv",
        List(Invoice("INV-102", 900), Invoice("INV-103", -1))
      ).either
    } yield (published, rejected)

    val (published, rejected) = Unsafe.unsafe { implicit unsafe =>
      Runtime.default.unsafe.run(
        program.timeoutFail(new IllegalStateException("Invoice export timed out"))(Duration.fromSeconds(30))
      ).getOrThrowFiberFailure()
    }

    assertEquals(directory.resolve("accepted.csv"), published)
    assertEquals("invoice_id,amount_cents\nINV-100,2500\nINV-101,1750\n", Files.readString(published, UTF_8))
    assertTrue(rejected.isLeft)
    assertEquals(Left("Negative amount for INV-103"), rejected.left.map(_.getMessage))
    assertFalse(Files.exists(directory.resolve("rejected.csv")))
    val files = Files.list(directory)
    try {
      assertEquals(Set("accepted.csv"), files.iterator().asScala.map(_.getFileName.toString).toSet)
    } finally {
      files.close()
    }
  }
}
