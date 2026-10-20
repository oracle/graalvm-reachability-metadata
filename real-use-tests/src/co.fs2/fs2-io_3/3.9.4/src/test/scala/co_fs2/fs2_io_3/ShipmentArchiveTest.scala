/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package co_fs2.fs2_io_3

import cats.effect.{IO, Resource}
import cats.effect.unsafe.implicits.global
import cats.syntax.all.*
import fs2.Stream
import fs2.io.{readInputStream, readOutputStream, toInputStreamResource}
import fs2.text
import org.junit.jupiter.api.Assertions.{assertEquals, assertNull}
import org.junit.jupiter.api.{Test, Timeout}

import java.nio.charset.StandardCharsets.UTF_8
import java.util.zip.{ZipEntry, ZipInputStream, ZipOutputStream}
import scala.concurrent.duration.*

/** Exercises InputStream and OutputStream bridges by importing a streamed ZIP shipment archive. */
class ShipmentArchiveTest {
  private case class Shipment(sku: String, quantity: Int)

  @Test
  @Timeout(60)
  def importShipmentArchiveThroughBlockingZipApi(): Unit = {
    val manifests: List[(String, String)] = List(
      "warehouse-north.csv" -> "sku,quantity\nSKU-42,12\nSKU-7,3\n",
      "warehouse-south.csv" -> "sku,quantity\nSKU-42,5\nSKU-9,8\n"
    )

    // A legacy ZIP exporter writes to an OutputStream; FS2 streams its output.
    val archive: Stream[IO, Byte] = readOutputStream[IO](32) { output =>
      IO.blocking {
        val zip: ZipOutputStream = new ZipOutputStream(output)
        try {
          manifests.foreach { case (name, csv) =>
            val entry: ZipEntry = new ZipEntry(name)
            entry.setTime(0L)
            zip.putNextEntry(entry)
            zip.write(csv.getBytes(UTF_8))
            zip.closeEntry()
          }
        } finally zip.close()
      }
    }

    val imported: IO[List[(String, List[Shipment])]] = toInputStreamResource(archive).use { input =>
      Resource.fromAutoCloseable(IO.blocking(new ZipInputStream(input))).use { zip =>
        for {
          shipments <- manifests.traverse { case (expectedName, _) =>
            for {
              entry <- IO.blocking(zip.getNextEntry)
              name <- IO {
                assertEquals(expectedName, entry.getName)
                entry.getName
              }
              // Reaching an entry's EOF must not close the stream needed by the next entry.
              lines <- readInputStream[IO](IO.pure(zip), 7, closeAfterUse = false)
                .through(text.utf8.decode)
                .through(text.lines)
                .filter(_.nonEmpty)
                .compile.toList
              rows <- IO {
                assertEquals("sku,quantity", lines.head)
                lines.tail.map { line =>
                  val fields: Array[String] = line.split(",")
                  Shipment(fields(0), fields(1).toInt)
                }
              }
              _ <- IO.blocking(zip.closeEntry())
            } yield name -> rows
          }
          next <- IO.blocking(zip.getNextEntry)
          _ <- IO(assertNull(next, "The archive must contain exactly the two warehouse manifests"))
          // ZipInputStream stops before the central-directory trailer; finish consuming the producer.
          _ <- readInputStream[IO](IO.pure(input), 32, closeAfterUse = false).compile.drain
        } yield shipments
      }
    }

    val result: List[(String, List[Shipment])] = imported.timeout(20.seconds).unsafeRunSync()
    assertEquals(List(
      "warehouse-north.csv" -> List(Shipment("SKU-42", 12), Shipment("SKU-7", 3)),
      "warehouse-south.csv" -> List(Shipment("SKU-42", 5), Shipment("SKU-9", 8))
    ), result)
    val totals: Map[String, Int] = result.flatMap(_._2)
      .groupMapReduce(_.sku)(_.quantity)(_ + _)
    assertEquals(Map("SKU-42" -> 17, "SKU-7" -> 3, "SKU-9" -> 8), totals)
  }
}
