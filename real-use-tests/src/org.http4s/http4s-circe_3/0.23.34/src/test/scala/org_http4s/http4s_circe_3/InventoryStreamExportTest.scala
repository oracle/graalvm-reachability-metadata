/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_http4s.http4s_circe_3

import cats.effect.IO
import cats.effect.unsafe.implicits.global
import fs2.Stream
import io.circe.Decoder
import io.circe.Encoder
import io.circe.Json
import io.circe.generic.semiauto.deriveDecoder
import io.circe.generic.semiauto.deriveEncoder
import org.http4s.EntityDecoder
import org.http4s.EntityEncoder
import org.http4s.HttpRoutes
import org.http4s.MediaType
import org.http4s.Request
import org.http4s.Status
import org.http4s.Uri
import org.http4s.circe.streamJsonArrayDecoder
import org.http4s.circe.streamJsonArrayEncoderOf
import org.http4s.client.Client
import org.http4s.dsl.io._
import org.http4s.headers.`Content-Type`
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Timeout

import scala.concurrent.duration._

/** Exercises streaming JSON arrays for an inventory export and replenishment report. */
class InventoryStreamExportTest {
  private final case class StockItem(sku: String, description: String, available: Int)
  private final case class Report(products: Int, units: Long, replenish: List[String])

  private given Encoder[StockItem] = deriveEncoder[StockItem]
  private given Decoder[StockItem] = deriveDecoder[StockItem]
  private given EntityEncoder[IO, Stream[IO, StockItem]] = streamJsonArrayEncoderOf[IO, StockItem]
  private given EntityDecoder[IO, Stream[IO, Json]] = streamJsonArrayDecoder[IO]

  @Test
  @Timeout(60)
  def summarizeInventoryWithoutCollectingTheExport(): Unit = {
    val inventory: Stream[IO, StockItem] = Stream.emits(List(
      StockItem("COFFEE", "Coffee beans", 12),
      StockItem("FILTER", "Filter, large", 2),
      StockItem("MUG", "Mug \"blue\"", 0)
    )).covary[IO]

    verifyExport(inventory, Report(3, 14L, List("Filter, large", "Mug \"blue\"")))
  }

  @Test
  @Timeout(60)
  def summarizeAnEmptyWarehouse(): Unit = {
    verifyExport(Stream.empty.covary[IO], Report(0, 0L, Nil))
  }

  private def verifyExport(inventory: Stream[IO, StockItem], expected: Report): Unit = {
    val routes: HttpRoutes[IO] = HttpRoutes.of[IO] {
      case GET -> Root / "inventory" => Ok(inventory)
    }
    val client: Client[IO] = Client.fromHttpApp(routes.orNotFound)
    val request: Request[IO] = Request[IO](uri = Uri.unsafeFromString("/inventory"))

    val result: IO[Unit] = client.run(request).use { response =>
      // The report consumes array elements incrementally, not a complete JSON document.
      val elements: IO[Stream[IO, Json]] = response.as[Stream[IO, Json]]
      for {
        _ <- IO {
          assertEquals(Status.Ok, response.status)
          assertEquals(Some(`Content-Type`(MediaType.application.json)), response.contentType)
        }
        stream <- elements
        report <- stream
          .evalMap(json => IO.fromEither(json.as[StockItem]))
          .compile
          .fold(Report(0, 0L, Nil)) { (summary, item) =>
            Report(
              summary.products + 1,
              summary.units + item.available,
              if (item.available < 5) summary.replenish :+ item.description else summary.replenish
            )
          }
        _ <- IO(assertEquals(expected, report))
      } yield ()
    }

    result.timeout(30.seconds).unsafeRunSync()
  }
}
