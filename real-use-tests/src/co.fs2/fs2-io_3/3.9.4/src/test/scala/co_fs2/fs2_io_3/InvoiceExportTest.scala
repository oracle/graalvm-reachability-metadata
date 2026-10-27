/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package co_fs2.fs2_io_3

import cats.effect.IO
import cats.effect.unsafe.implicits.global
import fs2.Stream
import fs2.io.file.{Files, Flags, Path}
import fs2.text
import org.junit.jupiter.api.Assertions.{assertEquals, assertFalse, assertTrue}
import org.junit.jupiter.api.{Test, Timeout}

/** Exercises file streaming by staging, publishing, and importing a daily invoice export. */
class InvoiceExportTest {
  private case class Invoice(id: String, customer: String, cents: Long)

  @Test
  @Timeout(60)
  def publishAndImportInvoiceExport(): Unit = {
    val invoices: List[Invoice] = List(
      Invoice("INV-101", "Northwind", 12500L),
      Invoice("INV-102", "Contoso", 7999L),
      Invoice("INV-103", "Northwind", 2501L)
    )
    val files: Files[IO] = Files[IO]
    val csv: Stream[IO, String] = Stream.emit("id,customer,cents\n") ++
      Stream.emits(invoices).map(invoice =>
        s"${invoice.id},${invoice.customer},${invoice.cents}\n"
      )

    files.tempDirectory.use { directory =>
      val staged: Path = directory / "invoices.csv.part"
      val published: Path = directory / "invoices.csv"
      for {
        _ <- csv.through(text.utf8.encode).through(files.writeAll(staged)).compile.drain
        _ <- files.move(staged, published)
        stagedExists <- files.exists(staged)
        publishedExists <- files.exists(published)
        rows <- files.readAll(published, 7, Flags.Read)
          .through(text.utf8.decode)
          .through(text.lines)
          .filter(_.nonEmpty)
          .compile.toList
        _ <- IO {
          assertFalse(stagedExists, "Publishing must remove the staging file")
          assertTrue(publishedExists, "The export must be available to consumers")
          assertEquals("id,customer,cents", rows.head)
          val imported: List[Invoice] = rows.tail.map { row =>
            val columns: Array[String] = row.split(",")
            Invoice(columns(0), columns(1), columns(2).toLong)
          }
          assertEquals(invoices, imported)
          assertEquals(23000L, imported.map(_.cents).sum)
        }
      } yield ()
    }.unsafeRunSync()
  }
}
