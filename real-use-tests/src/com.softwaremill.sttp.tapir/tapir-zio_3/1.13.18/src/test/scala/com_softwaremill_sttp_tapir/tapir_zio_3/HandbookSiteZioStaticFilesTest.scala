/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_softwaremill_sttp_tapir.tapir_zio_3

import java.io.{ByteArrayInputStream, ByteArrayOutputStream}
import java.nio.charset.StandardCharsets
import java.nio.file.{Files, Path}
import java.util.Comparator
import java.util.zip.{GZIPInputStream, GZIPOutputStream}

import org.junit.jupiter.api.Assertions.{assertEquals, assertFalse, assertTrue}
import org.junit.jupiter.api.Test
import sttp.client4.*
import sttp.client4.httpclient.zio.HttpClientZioBackend
import sttp.model.{Header, HeaderNames, Method, StatusCode}
import sttp.tapir.files.{FilesOptions, staticFileGetServerEndpoint, staticFilesGetServerEndpoint, staticFilesServerEndpoints}
import sttp.tapir.server.ziohttp.ZioHttpInterpreter
import sttp.tapir.ztapir.*
import zio.http.Server
import zio.{Runtime, Task, Unsafe, ZIO, ZLayer}

/**
 * Static file serving (tapir-files `staticFilesServerEndpoints` and `staticFilesGetServerEndpoint`
 * in the `Task` effect, widened into `ZServerEndpoint`s next to `zServerLogic` endpoints).
 *
 * A team handbook is published as a static site from a directory on disk. The site is mounted
 * under `/handbook` with GET and HEAD endpoints that hide draft files through a file filter and
 * prefer pre-compressed `.gz` siblings, and the same directory is mounted under `/app` as a
 * single-page application whose unknown routes fall back to `index.html`. A publishing endpoint
 * whose `zServerLogic` reads a `PagePublisher` service from the ZIO environment writes new pages
 * into the directory. Everything is served by the ZIO HTTP interpreter on a real socket and called
 * with an sttp client. The test checks content types, `ETag`/`Last-Modified` headers and the
 * `304 Not Modified` answer to a matching `If-None-Match`, `206` byte ranges, HEAD metadata,
 * gzip negotiation, the 404 for missing and filtered files, the SPA fallback, and that a page
 * published through the API is served by the static endpoint right after.
 */
class HandbookSiteZioStaticFilesTest {
  import HandbookSiteZioStaticFilesTest.*

  @Test
  def handbookSiteIsServedFromDiskNextToThePublishingApi(): Unit = {
    val siteDirectory: Path = Files.createTempDirectory("handbook-site")
    try {
      writeSite(siteDirectory)
      val program: ZIO[Server & PagePublisher, Throwable, Observed] = for {
        port <- Server.install(ZioHttpInterpreter().toHttp(serverEndpoints(siteDirectory)))
        observed <- ZIO.scoped(HttpClientZioBackend.scoped().flatMap(backend => exercise(port, backend)))
      } yield observed

      val observed: Observed = Unsafe.unsafe { implicit u =>
        Runtime.default.unsafe
          .run(program.provide(Server.defaultWithPort(0), DirectoryPagePublisher.layer(siteDirectory)))
          .getOrThrowFiberFailure()
      }

      assertEquals(StatusCode.Ok, observed.index.code)
      assertEquals(IndexHtml, observed.index.text)
      assertTrue(observed.index.header(HeaderNames.ContentType).exists(_.startsWith("text/html")))
      val indexTag: String = observed.index.header(HeaderNames.Etag).getOrElse(throw new AssertionError("no ETag"))
      assertTrue(observed.index.header(HeaderNames.LastModified).nonEmpty)
      assertEquals(Some(IndexHtml.getBytes(StandardCharsets.UTF_8).length.toString), observed.index.header(HeaderNames.ContentLength))

      assertEquals(StatusCode.Ok, observed.guide.code)
      assertEquals(GuideText, observed.guide.text)
      assertTrue(observed.guide.header(HeaderNames.ContentType).exists(_.startsWith("text/plain")))
      val guideTag: String = observed.guide.header(HeaderNames.Etag).getOrElse(throw new AssertionError("no ETag"))
      assertFalse(guideTag == indexTag)

      assertEquals(StatusCode.NotModified, observed.revalidated.code)
      assertEquals("", observed.revalidated.text)

      assertEquals(StatusCode.PartialContent, observed.firstBytes.code)
      assertEquals(GuideText.substring(0, 10), observed.firstBytes.text)
      assertEquals(Some(s"bytes 0-9/${GuideText.length}"), observed.firstBytes.header(HeaderNames.ContentRange))

      assertEquals(StatusCode.Ok, observed.headOnly.code)
      assertEquals("", observed.headOnly.text)
      assertEquals(Some(GuideText.length.toString), observed.headOnly.header(HeaderNames.ContentLength))
      assertEquals(Some("bytes"), observed.headOnly.header(HeaderNames.AcceptRanges))

      assertEquals(StatusCode.Ok, observed.gzipped.code)
      assertEquals(Some("gzip"), observed.gzipped.header(HeaderNames.ContentEncoding))
      assertTrue(observed.gzipped.header(HeaderNames.ContentType).exists(_.contains("javascript")))
      assertEquals(ScriptJs, gunzip(observed.gzipped.body))

      assertEquals(StatusCode.Ok, observed.plainScript.code)
      assertEquals(None, observed.plainScript.header(HeaderNames.ContentEncoding))
      assertEquals(ScriptJs, observed.plainScript.text)

      assertEquals(StatusCode.NotFound, observed.missing.code)
      assertEquals(StatusCode.NotFound, observed.draft.code)

      assertEquals(StatusCode.Ok, observed.spaRoute.code)
      assertEquals(IndexHtml, observed.spaRoute.text)
      assertEquals(StatusCode.Ok, observed.spaAsset.code)
      assertEquals(GuideText, observed.spaAsset.text)

      assertEquals(StatusCode.NotFound, observed.beforePublish.code)
      assertEquals(StatusCode.Created, observed.published.code)
      assertEquals("/handbook/news.html", observed.published.text)
      assertEquals(StatusCode.BadRequest, observed.rejected.code)
      assertEquals("page names may only use letters, digits and dashes", observed.rejected.text)
      assertEquals(StatusCode.Ok, observed.afterPublish.code)
      assertEquals(NewsHtml, observed.afterPublish.text)
      assertTrue(observed.afterPublish.header(HeaderNames.ContentType).exists(_.startsWith("text/html")))
      assertEquals(Some("public, max-age=60"), observed.afterPublish.header(HeaderNames.CacheControl))
      assertEquals(Some("no-store"), observed.robots.header(HeaderNames.CacheControl))
      assertEquals("User-agent: *\nDisallow: /app/\n", observed.robots.text)
    } finally {
      Files.walk(siteDirectory).sorted(Comparator.reverseOrder()).forEach(path => Files.delete(path))
    }
  }

  private def exercise(port: Int, backend: Backend[Task]): Task[Observed] = {
    val base: String = s"http://localhost:$port"
    val raw: PartialRequest[Array[Byte]] = basicRequest.response(asByteArrayAlways).disableAutoDecompression
    def send(request: Request[Array[Byte]]): Task[Reply] =
      request.send(backend).map(r => Reply(r.code, r.headers.map(h => h.name.toLowerCase -> h.value).toMap, r.body))
    for {
      index <- send(raw.get(uri"$base/handbook/index.html"))
      guide <- send(raw.get(uri"$base/handbook/guides/onboarding.txt"))
      guideTag = guide.header(HeaderNames.Etag).getOrElse("")
      revalidated <- send(raw.get(uri"$base/handbook/guides/onboarding.txt").header(HeaderNames.IfNoneMatch, guideTag))
      firstBytes <- send(raw.get(uri"$base/handbook/guides/onboarding.txt").header(HeaderNames.Range, "bytes=0-9"))
      headOnly <- send(raw.method(Method.HEAD, uri"$base/handbook/guides/onboarding.txt"))
      gzipped <- send(raw.get(uri"$base/handbook/assets/app.js").header(HeaderNames.AcceptEncoding, "gzip"))
      plainScript <- send(raw.get(uri"$base/handbook/assets/app.js").header(HeaderNames.AcceptEncoding, "identity"))
      missing <- send(raw.get(uri"$base/handbook/guides/missing.txt"))
      draft <- send(raw.get(uri"$base/handbook/guides/roadmap.draft"))
      spaRoute <- send(raw.get(uri"$base/app/people/ada/profile"))
      spaAsset <- send(raw.get(uri"$base/app/guides/onboarding.txt"))
      beforePublish <- send(raw.get(uri"$base/handbook/news.html"))
      published <- send(raw.post(uri"$base/api/pages/news").body(NewsHtml))
      rejected <- send(raw.post(uri"$base/api/pages/bad_name!").body("<p>nope</p>"))
      afterPublish <- send(raw.get(uri"$base/handbook/news.html"))
      robots <- send(raw.get(uri"$base/robots.txt"))
    } yield Observed(
      index, guide, revalidated, firstBytes, headOnly, gzipped, plainScript, missing, draft,
      spaRoute, spaAsset, beforePublish, published, rejected, afterPublish, robots
    )
  }
}

object HandbookSiteZioStaticFilesTest {
  val IndexHtml: String = "<!doctype html><html><body><h1>Team handbook</h1></body></html>\n"
  val GuideText: String = "Welcome aboard! Start with the setup checklist, then pair with a buddy.\n"
  val ScriptJs: String = "document.querySelector('h1').textContent = 'Team handbook (live)';\n" * 8
  val NewsHtml: String = "<!doctype html><html><body><h2>Release notes</h2></body></html>\n"

  final case class Reply(code: StatusCode, headers: Map[String, String], body: Array[Byte]) {
    def header(name: String): Option[String] = headers.get(name.toLowerCase)
    def text: String = new String(body, StandardCharsets.UTF_8)
  }

  final case class Observed(
      index: Reply,
      guide: Reply,
      revalidated: Reply,
      firstBytes: Reply,
      headOnly: Reply,
      gzipped: Reply,
      plainScript: Reply,
      missing: Reply,
      draft: Reply,
      spaRoute: Reply,
      spaAsset: Reply,
      beforePublish: Reply,
      published: Reply,
      rejected: Reply,
      afterPublish: Reply,
      robots: Reply
  )

  /** The service the publishing logic requires from the ZIO environment. */
  trait PagePublisher {
    def publish(name: String, html: String): Task[String]
  }

  final class DirectoryPagePublisher(directory: Path) extends PagePublisher {
    def publish(name: String, html: String): Task[String] =
      ZIO.attemptBlocking(Files.writeString(directory.resolve(s"$name.html"), html)).as(s"/handbook/$name.html")
  }

  object DirectoryPagePublisher {
    def layer(directory: Path): ZLayer[Any, Nothing, PagePublisher] =
      ZLayer.succeed(new DirectoryPagePublisher(directory))
  }

  def writeSite(directory: Path): Unit = {
    Files.createDirectories(directory.resolve("guides"))
    Files.createDirectories(directory.resolve("assets"))
    Files.writeString(directory.resolve("index.html"), IndexHtml)
    Files.writeString(directory.resolve("robots.txt"), "User-agent: *\nDisallow: /app/\n")
    Files.writeString(directory.resolve("guides/onboarding.txt"), GuideText)
    Files.writeString(directory.resolve("guides/roadmap.draft"), "unannounced plans")
    Files.writeString(directory.resolve("assets/app.js"), ScriptJs)
    Files.write(directory.resolve("assets/app.js.gz"), gzip(ScriptJs))
  }

  def gzip(text: String): Array[Byte] = {
    val bytes: ByteArrayOutputStream = new ByteArrayOutputStream()
    val out: GZIPOutputStream = new GZIPOutputStream(bytes)
    try out.write(text.getBytes(StandardCharsets.UTF_8))
    finally out.close()
    bytes.toByteArray
  }

  def gunzip(bytes: Array[Byte]): String = {
    val in: GZIPInputStream = new GZIPInputStream(new ByteArrayInputStream(bytes))
    try new String(in.readAllBytes(), StandardCharsets.UTF_8)
    finally in.close()
  }

  private val publishPage =
    endpoint.post.in("api" / "pages" / path[String]("name")).in(stringBody)
      .errorOut(statusCode(StatusCode.BadRequest).and(stringBody))
      .out(statusCode(StatusCode.Created)).out(stringBody)

  def serverEndpoints(siteDirectory: Path): List[ZServerEndpoint[PagePublisher, Any]] = {
    val handbookOptions: FilesOptions[Task] = FilesOptions.default[Task]
      .fileFilter(segments => !segments.lastOption.exists(_.endsWith(".draft")))
      .withUseGzippedIfAvailable
    val appOptions: FilesOptions[Task] = FilesOptions.default[Task].defaultFile(List("index.html"))
    val handbook: List[ZServerEndpoint[Any, Any]] = staticFilesServerEndpoints[Task]("handbook")(
      siteDirectory.toString,
      handbookOptions,
      List(Header(HeaderNames.CacheControl, "public, max-age=60"))
    )
    val app: ZServerEndpoint[Any, Any] = staticFilesGetServerEndpoint[Task]("app")(siteDirectory.toString, appOptions)
    val robots: ZServerEndpoint[Any, Any] = staticFileGetServerEndpoint[Task]("robots.txt")(
      siteDirectory.resolve("robots.txt").toString,
      List(Header(HeaderNames.CacheControl, "no-store"))
    )
    val publish: ZServerEndpoint[PagePublisher, Any] = publishPage.zServerLogic[PagePublisher] { (name, html) =>
      if (name.matches("[A-Za-z0-9-]+")) ZIO.serviceWithZIO[PagePublisher](_.publish(name, html)).orDie
      else ZIO.fail("page names may only use letters, digits and dashes")
    }
    publish :: (robots :: app :: handbook).map(_.widen[PagePublisher])
  }
}
