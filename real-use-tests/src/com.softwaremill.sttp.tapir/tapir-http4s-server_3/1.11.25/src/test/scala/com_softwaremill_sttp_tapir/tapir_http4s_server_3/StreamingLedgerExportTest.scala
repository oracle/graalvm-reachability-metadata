/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_softwaremill_sttp_tapir.tapir_http4s_server_3

import cats.effect.IO
import cats.effect.unsafe.implicits.global
import fs2.{Stream, text}
import org.http4s.{Method, Request}
import org.http4s.implicits.*
import org.junit.jupiter.api.Assertions.{assertEquals, assertTrue}
import org.junit.jupiter.api.{Test, Timeout}
import org.typelevel.ci.CIString
import sttp.capabilities.fs2.Fs2Streams
import sttp.tapir.*
import sttp.tapir.server.http4s.Http4sServerInterpreter

import java.nio.charset.StandardCharsets
import scala.concurrent.duration.*

/** Exercises FS2 response-body streaming for ledger downloads through Tapir's http4s interpreter. */
class StreamingLedgerExportTest {
  final case class LedgerEntry(account: String, description: String, cents: Int)

  @Test
  @Timeout(60)
  def downloadsAnAccountLedgerAsAStreamedUtf8Attachment(): Unit = {
    val entries: Vector[LedgerEntry] = Vector(
      LedgerEntry("north", "Coffee", 1250),
      LedgerEntry("south", "Tea", 800),
      LedgerEntry("north", "Caf\u00e9 supplies", 2500)
    )
    val download = endpoint.get
      .in("exports" / "ledger")
      .in(query[String]("account"))
      .out(header[String]("Content-Disposition"))
      .out(streamTextBody(Fs2Streams[IO])(CodecFormat.TextPlain(), Some(StandardCharsets.UTF_8)))
      .serverLogicSuccess { (account: String) =>
        val rows: Stream[IO, String] = Stream.emits(entries).covary[IO]
          .filter(_.account == account)
          .map(entry => s"${entry.description},${entry.cents}\n")
        val csv: Stream[IO, Byte] = (Stream.emit("description,cents\n") ++ rows)
          .through(text.utf8.encode)
        IO.pure((s"attachment; filename=\"$account-ledger.csv\"", csv))
      }
    val app = Http4sServerInterpreter[IO]().toRoutes(download).orNotFound

    val scenario: IO[Unit] = for {
      response <- app.run(Request[IO](Method.GET, uri"/exports/ledger?account=north"))
      body <- response.as[String]
      _ <- IO {
        assertEquals(200, response.status.code)
        assertEquals(
          Some("attachment; filename=\"north-ledger.csv\""),
          response.headers.headers.find(_.name == CIString("Content-Disposition")).map(_.value)
        )
        assertTrue(response.headers.headers.exists { header =>
          header.name == CIString("Content-Type") && header.value.startsWith("text/plain")
        })
        assertEquals("description,cents\nCoffee,1250\nCaf\u00e9 supplies,2500\n", body)
      }
      emptyResponse <- app.run(Request[IO](Method.GET, uri"/exports/ledger?account=west"))
      emptyBody <- emptyResponse.as[String]
      _ <- IO {
        assertEquals(200, emptyResponse.status.code)
        assertEquals("description,cents\n", emptyBody)
      }
    } yield ()

    scenario.timeout(45.seconds).unsafeRunSync()
  }
}
