/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_http4s.http4s_dsl_3

import cats.effect.IO
import cats.effect.Resource
import cats.effect.unsafe.implicits.global
import com.comcast.ip4s.*
import fs2.Stream
import java.io.ByteArrayInputStream
import java.nio.charset.StandardCharsets
import java.util.zip.GZIPInputStream
import org.http4s.HttpRoutes
import org.http4s.MediaType
import org.http4s.Method
import org.http4s.Request
import org.http4s.Response
import org.http4s.Status
import org.http4s.Uri
import org.http4s.client.Client
import org.http4s.client.middleware.GZip as ClientGZip
import org.http4s.dsl.io.*
import org.http4s.ember.client.EmberClientBuilder
import org.http4s.ember.server.EmberServerBuilder
import org.http4s.headers.`Content-Type`
import org.http4s.implicits.*
import org.http4s.server.middleware.GZip
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.typelevel.ci.*

/**
 * Response compression with the server `GZip` middleware and transparent decompression with the client `GZip` middleware.
 *
 * A CI service publishes build logs through http4s DSL routes wrapped in `GZip` with an
 * application-chosen `isZippable` rule: text logs, including a streamed live tail, are sent with
 * `Content-Encoding: gzip` when the request's `Accept-Encoding` admits gzip, while already
 * compressed artifacts and tiny status replies are passed through untouched. A raw client checks
 * the wire format by inflating the bytes itself, and a client wrapped in the client `GZip`
 * middleware reads the same logs as plain text. The service runs on an Ember server and is called
 * by an Ember client.
 */
class BuildLogGzipCompressionTest {
  import BuildLogGzipCompressionTest.*

  @Test
  def buildLogsAreGzippedOnlyWhenTheClientAcceptsItAndTheBodyIsWorthIt(): Unit = {
    val outcome: Observed = serveAndCall { (raw: Client[IO], base: Uri) =>
      val log: Uri = base / "builds" / "1042" / "log"
      val decoding: Client[IO] = ClientGZip()(raw)
      for {
        gzipped <- fetchRaw(raw, Request[IO](Method.GET, log).putHeaders("Accept-Encoding" -> "gzip"))
        identity <- fetchRaw(raw, Request[IO](Method.GET, log))
        refused <- fetchRaw(raw, Request[IO](Method.GET, log).putHeaders("Accept-Encoding" -> "gzip;q=0, br"))
        anyCoding <- fetchRaw(raw, Request[IO](Method.GET, log).putHeaders("Accept-Encoding" -> "*"))
        artifact <- fetchRaw(
          raw,
          Request[IO](Method.GET, base / "builds" / "1042" / "artifact.zip").putHeaders("Accept-Encoding" -> "gzip")
        )
        status <- fetchRaw(
          raw,
          Request[IO](Method.GET, base / "builds" / "1042" / "status").putHeaders("Accept-Encoding" -> "gzip")
        )
        tail <- fetchRaw(
          raw,
          Request[IO](Method.GET, base / "builds" / "1042" / "tail").putHeaders("Accept-Encoding" -> "gzip")
        )
        viaMiddleware <- fetchText(decoding, Request[IO](Method.GET, log))
        tailViaMiddleware <- fetchText(decoding, Request[IO](Method.GET, base / "builds" / "1042" / "tail"))
        missing <- fetchText(decoding, Request[IO](Method.GET, base / "builds" / "7" / "log"))
      } yield Observed(gzipped, identity, refused, anyCoding, artifact, status, tail, viaMiddleware, tailViaMiddleware, missing)
    }

    assertEquals(Status.Ok, outcome.gzipped.status)
    assertEquals(Some("gzip"), outcome.gzipped.contentEncoding)
    assertEquals(BuildLog, gunzip(outcome.gzipped.bytes))
    assertTrue(
      outcome.gzipped.bytes.length < BuildLog.length / 4,
      s"a repetitive log should shrink well, got ${outcome.gzipped.bytes.length} of ${BuildLog.length} bytes"
    )

    assertEquals(Seen(Status.Ok, None, ascii(BuildLog)), outcome.identity.normalized)
    assertEquals(Seen(Status.Ok, None, ascii(BuildLog)), outcome.refused.normalized)
    assertEquals(Some("gzip"), outcome.anyCoding.contentEncoding)
    assertEquals(BuildLog, gunzip(outcome.anyCoding.bytes))

    assertEquals(Seen(Status.Ok, None, Artifact.toList), outcome.artifact.normalized)
    assertEquals(Seen(Status.Ok, None, ascii("passed")), outcome.status.normalized)

    assertEquals(Some("gzip"), outcome.tail.contentEncoding)
    assertEquals(TailLines.mkString, gunzip(outcome.tail.bytes))

    assertEquals(Text(Status.Ok, None, BuildLog), outcome.viaMiddleware)
    assertEquals(Text(Status.Ok, None, TailLines.mkString), outcome.tailViaMiddleware)
    assertEquals(Text(Status.NotFound, None, "no build 7"), outcome.missing)
  }
}

object BuildLogGzipCompressionTest {

  /** A long, repetitive build log of the kind that compresses well. */
  val BuildLog: String =
    (1 to 120).map(step => f"[build 1042] step $step%03d: compiling module core ... ok%n").mkString

  /** Lines the live tail emits one at a time. */
  val TailLines: List[String] =
    (1 to 40).toList.map(line => f"[build 1042] test $line%02d of 40 passed in the integration suite%n")

  /** An already compressed artifact: the zip magic number followed by padding. */
  val Artifact: Array[Byte] = Array[Byte](0x50, 0x4b, 0x03, 0x04) ++ Array.fill[Byte](252)(0x2a)

  /** Smallest body, in bytes, the service bothers to compress. */
  val MinimumZippableLength: Long = 64L

  /** One response as a raw client sees it on the wire. */
  final case class Wire(status: Status, contentEncoding: Option[String], bytes: Array[Byte]) {
    def normalized: Seen = Seen(status, contentEncoding, bytes.toList)
  }

  /** A comparable view of a [[Wire]] response. */
  final case class Seen(status: Status, contentEncoding: Option[String], bytes: List[Byte])

  /** One response as a decoding client sees it. */
  final case class Text(status: Status, contentEncoding: Option[String], body: String)

  /** What the clients saw at each step against the log service. */
  final case class Observed(
      gzipped: Wire,
      identity: Wire,
      refused: Wire,
      anyCoding: Wire,
      artifact: Wire,
      status: Wire,
      tail: Wire,
      viaMiddleware: Text,
      tailViaMiddleware: Text,
      missing: Text
  )

  /** The log routes an application would mount. */
  val logRoutes: HttpRoutes[IO] = HttpRoutes.of[IO] {
    case GET -> Root / "builds" / LongVar(1042L) / "log" =>
      Ok(BuildLog)
    case GET -> Root / "builds" / LongVar(1042L) / "tail" =>
      Ok(Stream.emits(TailLines).covary[IO])
    case GET -> Root / "builds" / LongVar(1042L) / "artifact.zip" =>
      Ok(Artifact, `Content-Type`(MediaType.application.zip))
    case GET -> Root / "builds" / LongVar(1042L) / "status" =>
      Ok("passed")
    case GET -> Root / "builds" / LongVar(build) / _ =>
      NotFound(s"no build $build")
  }

  /** Compresses only bodies that are not already compressed and are not known to be tiny. */
  def worthZipping(response: Response[IO]): Boolean =
    GZip.defaultIsZippable(response) &&
      !response.contentType.exists(_.mediaType == MediaType.application.zip) &&
      response.contentLength.forall(_ >= MinimumZippableLength)

  /** Starts the compressed service on an ephemeral loopback port and runs `scenario` against it. */
  def serveAndCall[A](scenario: (Client[IO], Uri) => IO[A]): A = {
    val running: Resource[IO, (Client[IO], Uri)] = for {
      server <- EmberServerBuilder
        .default[IO]
        .withHost(ipv4"127.0.0.1")
        .withPort(port"0")
        .withHttpApp(GZip(logRoutes.orNotFound, isZippable = worthZipping))
        .build
      client <- EmberClientBuilder.default[IO].build
    } yield (client, Uri.unsafeFromString(s"http://127.0.0.1:${server.address.getPort}"))
    running.use((client, base) => scenario(client, base)).unsafeRunSync()
  }

  /** Sends `request` and keeps the body exactly as it arrived. */
  def fetchRaw(client: Client[IO], request: Request[IO]): IO[Wire] =
    client.run(request).use { (response: Response[IO]) =>
      response.body.compile.to(Array).map(bytes => Wire(response.status, contentEncoding(response), bytes))
    }

  /** Sends `request` and reads the body as text. */
  def fetchText(client: Client[IO], request: Request[IO]): IO[Text] =
    client.run(request).use { (response: Response[IO]) =>
      response.as[String].map(body => Text(response.status, contentEncoding(response), body))
    }

  def contentEncoding(response: Response[IO]): Option[String] =
    response.headers.get(ci"Content-Encoding").map(_.head.value)

  def ascii(text: String): List[Byte] = text.getBytes(StandardCharsets.US_ASCII).toList

  /** Inflates a gzip body with the JDK, independently of http4s. */
  def gunzip(bytes: Array[Byte]): String = {
    val in: GZIPInputStream = new GZIPInputStream(new ByteArrayInputStream(bytes))
    try new String(in.readAllBytes(), StandardCharsets.US_ASCII)
    finally in.close()
  }
}
