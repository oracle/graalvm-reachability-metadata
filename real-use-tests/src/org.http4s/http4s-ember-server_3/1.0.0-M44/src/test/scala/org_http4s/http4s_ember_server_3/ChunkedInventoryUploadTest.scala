/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_http4s.http4s_ember_server_3

import cats.effect.IO
import cats.effect.Ref
import cats.effect.unsafe.implicits.global
import com.comcast.ip4s.Host
import com.comcast.ip4s.Port
import fs2.Stream
import fs2.text
import org.http4s.Header
import org.http4s.HttpRoutes
import org.http4s.Method
import org.http4s.Request
import org.http4s.Status
import org.http4s.Uri
import org.http4s.dsl.Http4sDsl
import org.http4s.ember.client.EmberClientBuilder
import org.http4s.ember.server.EmberServerBuilder
import org.http4s.implicits.*
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Timeout
import org.typelevel.ci.CIString
import org.typelevel.log4cats.LoggerFactory
import org.typelevel.log4cats.slf4j.Slf4jFactory

import java.nio.charset.StandardCharsets
import scala.concurrent.duration.*

/** Exercises Ember's chunked request-body decoding subsystem for streaming inventory imports. */
class ChunkedInventoryUploadTest extends Http4sDsl[IO] {
  private given LoggerFactory[IO] = Slf4jFactory.create[IO]

  private case class InventoryItem(name: String, quantity: Int)

  @Test
  @Timeout(60)
  def importsAnUnknownLengthCsvUploadAndReadsBackTheInventory(): Unit = {
    val rowCount: Int = 250
    val product: String = "caf\u00e9 tea"
    val csv: String = "sku,name,quantity\n" + (1 to rowCount).map { id =>
      s"sku-$id,$product,$id\n"
    }.mkString
    // Small byte chunks also split multibyte UTF-8 characters between stream elements.
    val upload: Stream[IO, Byte] = Stream.emits(csv.getBytes(StandardCharsets.UTF_8))
      .covary[IO].chunkN(7).flatMap(chunk => Stream.chunk(chunk))

    val program: IO[Unit] = for {
      inventory <- Ref.of[IO, Map[String, InventoryItem]](Map.empty)
      routes = HttpRoutes.of[IO] {
        case request @ POST -> Root / "inventory" / "import" =>
          if (!request.isChunked) BadRequest("A streaming upload is required")
          else {
            request.body.through(text.utf8.decode).through(text.lines)
              .drop(1).filter(_.nonEmpty).evalMap { line =>
                val fields: Array[String] = line.split(",", -1)
                val item: InventoryItem = InventoryItem(fields(1), fields(2).toInt)
                inventory.update(_.updated(fields(0), item)).as(item.quantity)
              }.compile.fold((0, 0)) { case ((count, units), quantity) =>
                (count + 1, units + quantity)
              }.flatMap { case (count, units) =>
                Ok(s"Imported $count items with $units units")
              }
          }
        case GET -> Root / "inventory" / sku =>
          inventory.get.flatMap { saved =>
            saved.get(sku) match {
              case Some(item) => Ok(s"$sku,${item.name},${item.quantity}")
              case None => NotFound()
            }
          }
      }
      _ <- EmberServerBuilder.default[IO]
        .withHost(Host.fromString("127.0.0.1").get)
        .withPort(Port.fromInt(0).get)
        .withHttpApp(routes.orNotFound)
        .withShutdownTimeout(10.seconds)
        .build.use { server =>
          EmberClientBuilder.default[IO]
            .withTimeout(15.seconds)
            .withIdleConnectionTime(15.seconds)
            .build.use { client =>
              val base: Uri = Uri.unsafeFromString(s"http://127.0.0.1:${server.address.port.value}")
              val request: Request[IO] = Request[IO](Method.POST, base / "inventory" / "import")
                .withBodyStream(upload)
                .putHeaders(Header.Raw(CIString("Content-Type"), "text/csv; charset=UTF-8"))
              for {
                _ <- client.run(request).use { response =>
                  response.as[String].flatMap { receipt =>
                    IO {
                      assertEquals(Status.Ok, response.status)
                      assertEquals(s"Imported $rowCount items with ${rowCount * (rowCount + 1) / 2} units",
                        receipt)
                    }
                  }
                }
                _ <- client.run(Request[IO](Method.GET, base / "inventory" / s"sku-$rowCount"))
                  .use { response =>
                    response.as[String].flatMap { item =>
                      IO {
                        assertEquals(Status.Ok, response.status)
                        assertEquals(s"sku-$rowCount,$product,$rowCount", item)
                      }
                    }
                  }
              } yield ()
            }
        }
    } yield ()

    program.timeout(50.seconds).unsafeRunSync()
  }
}
