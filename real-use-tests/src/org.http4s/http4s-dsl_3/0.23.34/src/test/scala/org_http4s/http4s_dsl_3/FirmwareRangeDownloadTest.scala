/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_http4s.http4s_dsl_3

import cats.data.NonEmptyList
import cats.effect.IO
import cats.effect.Resource
import cats.effect.unsafe.implicits.global
import com.comcast.ip4s.*
import java.nio.charset.StandardCharsets
import org.http4s.EntityTag
import org.http4s.HttpRoutes
import org.http4s.Method
import org.http4s.RangeUnit
import org.http4s.Request
import org.http4s.Response
import org.http4s.Status
import org.http4s.Uri
import org.http4s.client.Client
import org.http4s.dsl.io.*
import org.http4s.ember.client.EmberClientBuilder
import org.http4s.ember.server.EmberServerBuilder
import org.http4s.headers.ETag
import org.http4s.headers.Range
import org.http4s.headers.Range.SubRange
import org.http4s.headers.`Accept-Ranges`
import org.http4s.headers.`Content-Range`
import org.http4s.implicits.*
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.typelevel.ci.*

/**
 * Byte-range requests for resumable downloads with `206 Partial Content` and `416 Range Not Satisfiable`.
 *
 * A firmware mirror serves images through the http4s DSL: a plain `GET` answers `200` with
 * `Accept-Ranges: bytes` and an `ETag`, a single byte range from the `Range` header answers
 * `PartialContent` with a matching `Content-Range` (closed, open-ended and suffix ranges alike), a
 * range starting past the end answers `RangeNotSatisfiable` with a `Content-Range` naming only the
 * image size, and the full image is sent instead when the request asks for several ranges or its `If-Range`
 * tag is stale. A client resumes an interrupted download by fetching the rest in fixed-size
 * chunks. The mirror runs on an Ember server and is called by an Ember client.
 */
class FirmwareRangeDownloadTest {
  import FirmwareRangeDownloadTest.*

  @Test
  def firmwareMirrorServesByteRangesForResumableDownloads(): Unit = {
    val outcome: Observed = serveAndCall { (client: Client[IO], base: Uri) =>
      val image: Uri = base / "firmware" / "router-2.4.bin"
      for {
        whole <- fetch(client, Request[IO](Method.GET, image))
        head <- fetch(client, Request[IO](Method.GET, image).putHeaders(Range(0L, 9L)))
        tail <- fetch(client, Request[IO](Method.GET, image).putHeaders(Range(20L)))
        suffix <- fetch(client, Request[IO](Method.GET, image).putHeaders("Range" -> "bytes=-4"))
        clamped <- fetch(client, Request[IO](Method.GET, image).putHeaders(Range(24L, 99L)))
        beyond <- fetch(client, Request[IO](Method.GET, image).putHeaders(Range(30L, 40L)))
        several <- fetch(
          client,
          Request[IO](Method.GET, image)
            .putHeaders(Range(RangeUnit.Bytes, NonEmptyList.of(SubRange(0L, 1L), SubRange(5L, 6L))))
        )
        freshTag <- fetch(
          client,
          Request[IO](Method.GET, image).putHeaders(Range(10L, 12L), "If-Range" -> CurrentTag)
        )
        staleTag <- fetch(
          client,
          Request[IO](Method.GET, image).putHeaders(Range(10L, 12L), "If-Range" -> "\"router-2.3\"")
        )
        missing <- fetch(client, Request[IO](Method.GET, base / "firmware" / "router-9.9.bin"))
        resumed <- resume(client, image, alreadyHave = "ABCDEF", chunkSize = 8L)
      } yield Observed(whole, head, tail, suffix, clamped, beyond, several, freshTag, staleTag, missing, resumed)
    }

    assertEquals(Seen(Status.Ok, None, Some("bytes"), Some(CurrentTag), Image), outcome.whole)
    assertEquals(Seen(Status.PartialContent, Some("bytes 0-9/26"), None, Some(CurrentTag), "ABCDEFGHIJ"), outcome.head)
    assertEquals(Seen(Status.PartialContent, Some("bytes 20-25/26"), None, Some(CurrentTag), "UVWXYZ"), outcome.tail)
    assertEquals(Seen(Status.PartialContent, Some("bytes 22-25/26"), None, Some(CurrentTag), "WXYZ"), outcome.suffix)
    assertEquals(Seen(Status.PartialContent, Some("bytes 24-25/26"), None, Some(CurrentTag), "YZ"), outcome.clamped)
    assertEquals(
      Seen(Status.RangeNotSatisfiable, Some("bytes */26"), None, None, "range starts past byte 25"),
      outcome.beyond
    )
    assertEquals(Seen(Status.Ok, None, Some("bytes"), Some(CurrentTag), Image), outcome.several)
    assertEquals(Seen(Status.PartialContent, Some("bytes 10-12/26"), None, Some(CurrentTag), "KLM"), outcome.freshTag)
    assertEquals(Seen(Status.Ok, None, Some("bytes"), Some(CurrentTag), Image), outcome.staleTag)
    assertEquals(Seen(Status.NotFound, None, None, None, "no firmware router-9.9.bin"), outcome.missing)
    assertEquals(
      Resumed(List("bytes 6-13/26", "bytes 14-21/26", "bytes 22-25/26"), Image),
      outcome.resumed
    )
  }
}

object FirmwareRangeDownloadTest {

  /** The only image the mirror publishes, kept ASCII so slices are easy to read back. */
  val Image: String = "ABCDEFGHIJKLMNOPQRSTUVWXYZ"

  /** The rendered entity tag of the published image. */
  val CurrentTag: String = "\"router-2.4\""

  /** One response as a download client sees it. */
  final case class Seen(
      status: Status,
      contentRange: Option[String],
      acceptRanges: Option[String],
      etag: Option[String],
      body: String
  )

  /** The `Content-Range` of every chunk a resumed download fetched, and the reassembled file. */
  final case class Resumed(chunks: List[String], file: String)

  /** What the client saw at each step against the mirror. */
  final case class Observed(
      whole: Seen,
      head: Seen,
      tail: Seen,
      suffix: Seen,
      clamped: Seen,
      beyond: Seen,
      several: Seen,
      freshTag: Seen,
      staleTag: Seen,
      missing: Seen,
      resumed: Resumed
  )

  /** How the mirror answers a request for an image of a given size. */
  sealed trait Selection
  case object Whole extends Selection
  final case class Slice(first: Long, last: Long) extends Selection
  case object Unsatisfiable extends Selection

  /** Chooses what to send: only a single byte range with a current or absent `If-Range` is honoured. */
  def select(request: Request[IO], size: Long): Selection = {
    val current: Boolean = request.headers.get(ci"If-Range").forall(_.head.value == CurrentTag)
    request.headers.get[Range] match {
      case Some(Range(RangeUnit.Bytes, NonEmptyList(only, Nil))) if current => slice(only, size)
      case _ => Whole
    }
  }

  /** Resolves one sub-range against the image size; a negative start is a suffix length. */
  def slice(range: SubRange, size: Long): Selection =
    if (range.first < 0) Slice(math.max(0L, size + range.first), size - 1)
    else if (range.first >= size) Unsatisfiable
    else Slice(range.first, math.min(range.second.getOrElse(size - 1), size - 1))

  /** The mirror routes an application would mount. */
  def mirrorRoutes(images: Map[String, Array[Byte]]): HttpRoutes[IO] = HttpRoutes.of[IO] {
    case request @ GET -> Root / "firmware" / name =>
      images.get(name).fold(NotFound(s"no firmware $name")) { bytes =>
        val size: Long = bytes.length.toLong
        val tag: ETag = ETag(EntityTag("router-2.4"))
        select(request, size) match {
          case Whole =>
            Ok(bytes, `Accept-Ranges`.bytes, tag)
          case Slice(first, last) =>
            PartialContent(
              bytes.slice(first.toInt, last.toInt + 1),
              `Content-Range`(SubRange(first, last), Some(size)),
              tag
            )
          case Unsatisfiable =>
            RangeNotSatisfiable(s"range starts past byte ${size - 1}", "Content-Range" -> s"bytes */$size")
        }
      }
  }

  /** Fetches the rest of a partially downloaded file in `chunkSize` pieces until the server's total is reached. */
  def resume(client: Client[IO], image: Uri, alreadyHave: String, chunkSize: Long): IO[Resumed] = {
    def loop(have: String, chunks: List[String]): IO[Resumed] = {
      val first: Long = have.length.toLong
      client.run(Request[IO](Method.GET, image).putHeaders(Range(first, first + chunkSize - 1))).use { response =>
        val range: `Content-Range` = response.headers.get[`Content-Range`].get
        response.body.compile.to(Array).map(bytes => (range, have + new String(bytes, StandardCharsets.US_ASCII)))
      }.flatMap { (range: `Content-Range`, grown: String) =>
        val seen: List[String] = chunks :+ s"bytes ${range.range.first}-${range.range.second.get}/${range.length.get}"
        if (grown.length.toLong < range.length.get) loop(grown, seen) else IO.pure(Resumed(seen, grown))
      }
    }
    loop(alreadyHave, Nil)
  }

  /** Starts the mirror on an ephemeral loopback port and runs `scenario` against it. */
  def serveAndCall[A](scenario: (Client[IO], Uri) => IO[A]): A = {
    val images: Map[String, Array[Byte]] = Map("router-2.4.bin" -> Image.getBytes(StandardCharsets.US_ASCII))
    val running: Resource[IO, (Client[IO], Uri)] = for {
      server <- EmberServerBuilder
        .default[IO]
        .withHost(ipv4"127.0.0.1")
        .withPort(port"0")
        .withHttpApp(mirrorRoutes(images).orNotFound)
        .build
      client <- EmberClientBuilder.default[IO].build
    } yield (client, Uri.unsafeFromString(s"http://127.0.0.1:${server.address.getPort}"))
    running.use((client, base) => scenario(client, base)).unsafeRunSync()
  }

  /** Sends `request` and records the range-related headers and the body as ASCII text. */
  def fetch(client: Client[IO], request: Request[IO]): IO[Seen] =
    client.run(request).use { (response: Response[IO]) =>
      response.body.compile.to(Array).map { bytes =>
        Seen(
          response.status,
          response.headers.get(ci"Content-Range").map(_.head.value),
          response.headers.get(ci"Accept-Ranges").map(_.head.value),
          response.headers.get(ci"ETag").map(_.head.value),
          new String(bytes, StandardCharsets.US_ASCII)
        )
      }
    }
}
