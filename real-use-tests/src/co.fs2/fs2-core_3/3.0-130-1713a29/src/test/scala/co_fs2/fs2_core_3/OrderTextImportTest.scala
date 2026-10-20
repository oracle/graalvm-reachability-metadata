/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package co_fs2.fs2_core_3

import cats.effect.IO
import cats.effect.unsafe.implicits.global
import fs2.{Chunk, Stream, text}
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.{Test, Timeout}

import java.nio.charset.StandardCharsets
import scala.concurrent.duration.*

/** Exercises FS2 text decoding and line framing to import a fragmented UTF-8 order feed. */
class OrderTextImportTest {
  private case class OrderLine(id: String, product: String, quantity: Int, unitPriceCents: Int)

  @Test
  @Timeout(60)
  def importsOrdersAcrossByteAndLineBoundaries(): Unit = {
    val feed: String =
      "order,product,quantity,unitPriceCents\r\n" +
        "A-100,caf\u00e9,2,1250\r\n" +
        "\r\n" +
        "A-101,tea,3,400\n" +
        "A-102,caf\u00e9,1,1250"
    val fragments: Vector[Array[Byte]] =
      feed.getBytes(StandardCharsets.UTF_8).grouped(2).toVector

    val orders: Vector[OrderLine] = Stream
      .emits(fragments)
      .covary[IO]
      .flatMap(bytes => Stream.chunk(Chunk.array(bytes)))
      .through(text.utf8.decode)
      .through(text.lines)
      .drop(1)
      .filter(_.nonEmpty)
      .map { line =>
        val fields: Array[String] = line.split(",", -1)
        require(fields.length == 4, s"Invalid order row: $line")
        OrderLine(fields(0), fields(1), fields(2).toInt, fields(3).toInt)
      }
      .compile
      .toVector
      .timeout(20.seconds)
      .unsafeRunSync()

    val expected: Vector[OrderLine] = Vector(
      OrderLine("A-100", "caf\u00e9", 2, 1250),
      OrderLine("A-101", "tea", 3, 400),
      OrderLine("A-102", "caf\u00e9", 1, 1250)
    )
    assertEquals(expected, orders)
    assertEquals(6, orders.map(_.quantity).sum)
    assertEquals(4950, orders.map(order => order.quantity * order.unitPriceCents).sum)
  }
}
