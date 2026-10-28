/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_softwaremill_sttp_tapir.tapir_http4s_server_3

import cats.effect.{IO, Ref}
import cats.effect.unsafe.implicits.global
import fs2.{Stream, text}
import org.http4s.{Header, Method, Request}
import org.http4s.implicits.{path as _, *}
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.{Test, Timeout}
import org.typelevel.ci.CIString
import sttp.capabilities.fs2.Fs2Streams
import sttp.tapir.*
import sttp.tapir.server.http4s.Http4sServerInterpreter

import java.nio.charset.StandardCharsets
import scala.concurrent.duration.*

/** Exercises FS2 request-body streaming for inventory imports in Tapir's http4s interpreter. */
class StreamingInventoryImportTest {
  @Test
  @Timeout(60)
  def importsChunkedUtf8StockRowsAndMakesUpdatedInventoryAvailable(): Unit = {
    val upload: PublicEndpoint[Stream[IO, Byte], Unit, String, Fs2Streams[IO]] = endpoint.post
      .in("inventory" / "imports")
      .in(streamTextBody(Fs2Streams[IO])(CodecFormat.TextPlain(), Some(StandardCharsets.UTF_8)))
      .out(stringBody)
    val lookup: PublicEndpoint[String, Unit, String, Any] = endpoint.get
      .in("inventory")
      .in(query[String]("sku"))
      .out(stringBody)

    val scenario: IO[Unit] = for {
      inventory <- Ref.of[IO, Map[String, Int]](Map.empty)
      importRoute = upload.serverLogicSuccess[IO] { (body: Stream[IO, Byte]) =>
        body.through(text.utf8.decode)
          .through(text.lines)
          .filter(_.nonEmpty)
          .evalMap { row =>
            IO {
              row.split(",", -1).toList match {
                case sku :: quantity :: Nil if sku.nonEmpty => (sku, quantity.toInt)
                case _ => throw new IllegalArgumentException("Expected sku,quantity")
              }
            }.flatMap { (sku: String, quantity: Int) =>
              inventory.update { current =>
                current.updated(sku, current.getOrElse(sku, 0) + quantity)
              }.as(1)
            }
          }
          .compile.fold(0)(_ + _)
          .map(count => s"Imported $count stock rows")
      }
      lookupRoute = lookup.serverLogicSuccess[IO] { (sku: String) =>
        inventory.get.map(current => s"$sku:${current.getOrElse(sku, 0)}")
      }
      app = Http4sServerInterpreter[IO]().toRoutes(List(importRoute, lookupRoute)).orNotFound
      imported <- app.run(importRequest("coffee,12\ncaf\u00e9,4\ncoffee,8"))
      receipt <- imported.as[String]
      coffee <- app.run(Request[IO](Method.GET, uri"/inventory?sku=coffee"))
      coffeeStock <- coffee.as[String]
      cafe <- app.run(Request[IO](Method.GET, uri"/inventory?sku=caf%C3%A9"))
      cafeStock <- cafe.as[String]
      _ <- IO {
        assertEquals(200, imported.status.code)
        assertEquals("Imported 3 stock rows", receipt)
        assertEquals(200, coffee.status.code)
        assertEquals("coffee:20", coffeeStock)
        assertEquals(200, cafe.status.code)
        assertEquals("caf\u00e9:4", cafeStock)
      }
      empty <- app.run(importRequest(""))
      emptyReceipt <- empty.as[String]
      unchanged <- app.run(Request[IO](Method.GET, uri"/inventory?sku=coffee"))
      unchangedStock <- unchanged.as[String]
      _ <- IO {
        assertEquals(200, empty.status.code)
        assertEquals("Imported 0 stock rows", emptyReceipt)
        assertEquals(200, unchanged.status.code)
        assertEquals("coffee:20", unchangedStock)
      }
    } yield ()

    scenario.timeout(45.seconds).unsafeRunSync()
  }

  private def importRequest(contents: String): Request[IO] = {
    // Single-byte chunks split both rows and the two-byte UTF-8 character across boundaries.
    val body: Stream[IO, Byte] = Stream.emits(contents.getBytes(StandardCharsets.UTF_8))
      .covary[IO].chunkN(1).unchunks
    Request[IO](Method.POST, uri"/inventory/imports")
      .withBodyStream(body)
      .putHeaders(Header.Raw(CIString("Content-Type"), "text/plain; charset=UTF-8"))
  }
}
