/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_http4s.http4s_ember_server_3

import cats.effect.IO
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
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Timeout
import org.typelevel.ci.CIString
import org.typelevel.log4cats.LoggerFactory
import org.typelevel.log4cats.slf4j.Slf4jFactory

import scala.concurrent.duration.*

/** Exercises Ember's chunked response streaming subsystem for a CSV inventory export. */
class StreamingCsvExportTest extends Http4sDsl[IO] {
  private given LoggerFactory[IO] = Slf4jFactory.create[IO]

  @Test
  @Timeout(60)
  def downloadsAndProcessesAnInventoryExportLineByLine(): Unit = {
    val rowCount: Int = 250
    val rows: Stream[IO, String] = Stream.emits(1 to rowCount).covary[IO].map { id =>
      s"sku-$id,tea,$id\n"
    }
    val csv: Stream[IO, Byte] =
      (Stream.emit("sku,product,quantity\n").covary[IO] ++ rows).through(text.utf8.encode)
    val routes: HttpRoutes[IO] = HttpRoutes.of[IO] {
      case GET -> Root / "exports" / "inventory.csv" =>
        Ok(csv).map(_.putHeaders(
          Header.Raw(CIString("Content-Type"), "text/csv; charset=UTF-8"),
          Header.Raw(CIString("Content-Disposition"), "attachment; filename=inventory.csv")
        ))
    }
    val program: IO[Unit] = EmberServerBuilder.default[IO]
      .withHost(Host.fromString("127.0.0.1").get)
      .withPort(Port.fromInt(0).get)
      .withHttpApp(routes.orNotFound)
      .withShutdownTimeout(10.seconds)
      .build.use { server =>
        EmberClientBuilder.default[IO]
          .withTimeout(15.seconds)
          .withIdleConnectionTime(15.seconds)
          .build.use { client =>
            val uri: Uri = Uri.unsafeFromString(
              s"http://127.0.0.1:${server.address.port.value}/exports/inventory.csv"
            )
            client.run(Request[IO](Method.GET, uri)).use { response =>
              for {
                _ <- IO {
                  assertEquals(Status.Ok, response.status)
                  assertEquals(Some("text/csv; charset=UTF-8"),
                    response.headers.get(CIString("Content-Type")).map(_.head.value))
                  assertEquals(Some("attachment; filename=inventory.csv"),
                    response.headers.get(CIString("Content-Disposition")).map(_.head.value))
                  assertEquals(Some("chunked"),
                    response.headers.get(CIString("Transfer-Encoding")).map(_.head.value))
                  assertTrue(response.contentLength.isEmpty)
                }
                count <- response.body.through(text.utf8.decode).through(text.lines)
                  .filter(_.nonEmpty).zipWithIndex.evalMap { case (line, index) =>
                    IO {
                      val expected: String =
                        if (index == 0) "sku,product,quantity" else s"sku-$index,tea,$index"
                      assertEquals(expected, line)
                    }
                  }.compile.count
                _ <- IO(assertEquals(rowCount.toLong + 1, count))
              } yield ()
            }
          }
      }

    program.timeout(50.seconds).unsafeRunSync()
  }
}
