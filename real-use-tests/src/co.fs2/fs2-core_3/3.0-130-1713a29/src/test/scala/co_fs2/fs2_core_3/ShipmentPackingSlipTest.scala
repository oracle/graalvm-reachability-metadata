/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package co_fs2.fs2_core_3

import cats.effect.IO
import cats.effect.unsafe.implicits.global
import cats.syntax.all.*
import fs2.{Chunk, Stream}
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.{Test, Timeout}

import scala.concurrent.duration.*

/** Exercises FS2 adjacent-key grouping to build packing slips from a paginated shipment feed. */
class ShipmentPackingSlipTest {
  private case class ShipmentLine(trackingId: String, sku: String, units: Int)
  private case class PackingSlip(trackingId: String, items: Map[String, Int])

  @Test
  @Timeout(60)
  def consolidatesShipmentsAcrossPagesAndFlushesTheLastPackingSlip(): Unit = {
    // The export is ordered by tracking ID, but page boundaries can split a shipment.
    val pages: Vector[Vector[ShipmentLine]] = Vector(
      Vector(
        ShipmentLine("parcel-100", "mug", 2),
        ShipmentLine("parcel-100", "tea", 1)
      ),
      Vector(
        ShipmentLine("parcel-100", "mug", 3),
        ShipmentLine("parcel-100", "card", 2),
        ShipmentLine("parcel-101", "tea", 4),
        ShipmentLine("parcel-102", "cup", 1)
      ),
      Vector(
        ShipmentLine("parcel-102", "cup", 2),
        ShipmentLine("parcel-102", "card", 5)
      )
    )

    val slips: Vector[PackingSlip] = Stream
      .emits(pages)
      .covary[IO]
      .flatMap(page => Stream.chunk(Chunk.vector(page)))
      .groupAdjacentBy(_.trackingId)
      .map { case (trackingId, lines) =>
        val items: Map[String, Int] = lines.toVector.groupMapReduce(_.sku)(_.units)(_ + _)
        PackingSlip(trackingId, items)
      }
      .compile
      .toVector
      .timeout(20.seconds)
      .unsafeRunSync()

    val expected: Vector[PackingSlip] = Vector(
      PackingSlip("parcel-100", Map("mug" -> 5, "tea" -> 1, "card" -> 2)),
      PackingSlip("parcel-101", Map("tea" -> 4)),
      PackingSlip("parcel-102", Map("cup" -> 3, "card" -> 5))
    )
    assertEquals(expected, slips)
    assertEquals(20, slips.map(_.items.values.sum).sum)
  }
}
