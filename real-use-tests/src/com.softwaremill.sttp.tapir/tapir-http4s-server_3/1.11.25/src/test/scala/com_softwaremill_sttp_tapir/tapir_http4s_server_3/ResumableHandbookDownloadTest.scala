/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_softwaremill_sttp_tapir.tapir_http4s_server_3

import cats.effect.{IO, Resource}
import cats.effect.unsafe.implicits.global
import org.http4s.{Header, HttpApp, Method, Request, Response}
import org.http4s.implicits.*
import org.junit.jupiter.api.Assertions.{assertEquals, assertTrue}
import org.junit.jupiter.api.{Test, Timeout}
import org.typelevel.ci.CIString
import sttp.tapir.*
import sttp.tapir.files.staticFilesServerEndpoints
import sttp.tapir.server.http4s.Http4sServerInterpreter

import java.nio.charset.StandardCharsets
import java.nio.file.{Files, Path}
import scala.concurrent.duration.*

/** Exercises static-file serving, byte ranges, and cache validation in Tapir's http4s interpreter. */
class ResumableHandbookDownloadTest {
  @Test
  @Timeout(60)
  def downloadsResumesAndRevalidatesAPublishedHandbook(): Unit = {
    val handbook: String = "Safety handbook\nChapter one: evacuation\nChapter two: first aid\n"
    val publication: Resource[IO, Path] = Resource.make(
      IO.blocking(Files.createTempDirectory("published-handbook-"))
    ) { directory =>
      IO.blocking {
        Files.deleteIfExists(directory.resolve("handbook.txt"))
        Files.delete(directory)
      }
    }

    val scenario: IO[Unit] = publication.use { directory =>
      val app: HttpApp[IO] = Http4sServerInterpreter[IO]().toRoutes(
        staticFilesServerEndpoints[IO]("downloads")(directory.toString)
      ).orNotFound

      for {
        _ <- IO.blocking(Files.writeString(directory.resolve("handbook.txt"), handbook, StandardCharsets.UTF_8))
        head <- app.run(Request[IO](Method.HEAD, uri"/downloads/handbook.txt"))
        headBytes <- head.body.compile.toVector
        _ <- IO {
          assertEquals(200, head.status.code)
          assertEquals(Some(handbook.length.toString), headerValue(head, "Content-Length"))
          assertEquals(Some("bytes"), headerValue(head, "Accept-Ranges"))
          assertTrue(headBytes.isEmpty)
        }
        full <- app.run(Request[IO](Method.GET, uri"/downloads/handbook.txt"))
        contents <- full.as[String]
        etag <- IO {
          val tag: Option[String] = headerValue(full, "ETag")
          assertEquals(200, full.status.code)
          assertEquals(handbook, contents)
          assertTrue(headerValue(full, "Content-Type").exists(_.startsWith("text/plain")))
          assertTrue(tag.isDefined, "A published file must provide a cache validator")
          tag.get
        }
        partial <- app.run(Request[IO](Method.GET, uri"/downloads/handbook.txt")
          .putHeaders(Header.Raw(CIString("Range"), "bytes=16-25")))
        resumed <- partial.as[String]
        _ <- IO {
          assertEquals(206, partial.status.code)
          assertEquals(handbook.substring(16, 26), resumed)
          assertEquals(Some("10"), headerValue(partial, "Content-Length"))
          assertEquals(Some(s"bytes 16-25/${handbook.length}"), headerValue(partial, "Content-Range"))
        }
        cached <- app.run(Request[IO](Method.GET, uri"/downloads/handbook.txt")
          .putHeaders(Header.Raw(CIString("If-None-Match"), etag)))
        cachedBytes <- cached.body.compile.toVector
        _ <- IO {
          assertEquals(304, cached.status.code)
          assertTrue(cachedBytes.isEmpty, "An unchanged handbook should reuse the browser's cached body")
        }
        unsatisfiable <- app.run(Request[IO](Method.GET, uri"/downloads/handbook.txt")
          .putHeaders(Header.Raw(CIString("Range"), "bytes=1000-1010")))
        _ <- unsatisfiable.body.compile.drain
        missing <- app.run(Request[IO](Method.GET, uri"/downloads/unpublished.txt"))
        _ <- missing.body.compile.drain
        _ <- IO {
          assertEquals(416, unsatisfiable.status.code)
          assertEquals(404, missing.status.code)
        }
      } yield ()
    }

    scenario.timeout(45.seconds).unsafeRunSync()
  }

  private def headerValue(response: Response[IO], name: String): Option[String] =
    response.headers.headers.find(_.name == CIString(name)).map(_.value)
}
