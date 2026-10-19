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
import java.util.UUID
import org.http4s.Header
import org.http4s.HttpRoutes
import org.http4s.MediaType
import org.http4s.Method
import org.http4s.Request
import org.http4s.Response
import org.http4s.Status
import org.http4s.Uri
import org.http4s.client.Client
import org.http4s.dsl.impl.MatrixVar
import org.http4s.dsl.impl.MethodConcat
import org.http4s.dsl.io.*
import org.http4s.ember.client.EmberClientBuilder
import org.http4s.ember.server.EmberServerBuilder
import org.http4s.headers.Location
import org.http4s.headers.`Content-Type`
import org.http4s.implicits.*
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.typelevel.ci.*

/**
 * Advanced path extraction and redirect responses with the DSL prefix, extension and matrix extractors.
 *
 * A document archive serves static assets under a prefix matched with `/:` for both `GET` and
 * `HEAD` through a `MethodConcat`, picks an export format from the file extension with `~`,
 * selects a month of documents from matrix parameters with a `MatrixVar`, addresses documents
 * and their revisions with `UUIDVar` and `LongVar`, and answers legacy and form-style requests
 * with `301 Moved Permanently`, `303 See Other` and `307 Temporary Redirect` carrying a
 * `Location`. The service runs on an Ember server and is called by an Ember client that does not
 * follow redirects, so the client observes each redirect itself.
 */
class DocumentArchivePathExtractorsTest {
  import DocumentArchivePathExtractorsTest.*

  @Test
  def archiveRoutesResolvePrefixExtensionMatrixAndRedirects(): Unit = {
    val outcome: Observed = serveAndCall { (client: Client[IO], base: String) =>
      def at(path: String): Uri = Uri.unsafeFromString(s"$base$path")

      for {
        stylesheet <- call(client, Request[IO](Method.GET, at("/assets/css/site.css")))
        logoHead <- client.run(Request[IO](Method.HEAD, at("/assets/img/logo.svg"))).use { response =>
          IO.pure((response.status, response.headers.get(ci"X-Asset-Path").map(_.head.value)))
        }
        missingAsset <- call(client, Request[IO](Method.GET, at("/assets/js/app.js")))
        csvExport <- typed(client, Request[IO](Method.GET, at("/reports/quarterly.csv")))
        jsonExport <- typed(client, Request[IO](Method.GET, at("/reports/quarterly.json")))
        pdfExport <- call(client, Request[IO](Method.GET, at("/reports/quarterly.pdf")))
        october <- call(client, Request[IO](Method.GET, at("/archive/month;year=2026;number=10")))
        september <- call(client, Request[IO](Method.GET, at("/archive/month;number=9;year=2026")))
        incompleteMonth <- call(client, Request[IO](Method.GET, at("/archive/month;year=2026")))
        document <- call(client, Request[IO](Method.GET, at(s"/docs/$Charter")))
        revision <- call(client, Request[IO](Method.GET, at(s"/docs/$Charter/revisions/2")))
        unknownRevision <- call(client, Request[IO](Method.GET, at(s"/docs/$Charter/revisions/9")))
        malformedId <- call(client, Request[IO](Method.GET, at("/docs/not-a-uuid")))
        legacy <- redirect(client, Request[IO](Method.GET, at("/legacy/doc/7")))
        published <- redirect(client, Request[IO](Method.POST, at(s"/docs/$Minutes/publish")))
        latest <- redirect(client, Request[IO](Method.GET, at("/docs/latest")))
      } yield Observed(
        stylesheet,
        logoHead,
        missingAsset,
        csvExport,
        jsonExport,
        pdfExport,
        october,
        september,
        incompleteMonth,
        document,
        revision,
        unknownRevision,
        malformedId,
        legacy,
        published,
        latest
      )
    }

    assertEquals((Status.Ok, "body { margin: 0 }"), outcome.stylesheet)
    assertEquals((Status.Ok, Some("img/logo.svg")), outcome.logoHead)
    assertEquals((Status.NotFound, "no asset js/app.js"), outcome.missingAsset)
    assertEquals(
      (Status.Ok, Some(MediaType.text.csv), "title,month\nCharter,2026-10\nMinutes,2026-10\nBudget,2026-09"),
      outcome.csvExport
    )
    assertEquals(
      (Status.Ok, Some(MediaType.application.json), """["Charter","Minutes","Budget"]"""),
      outcome.jsonExport
    )
    assertEquals((Status.NotAcceptable, "quarterly cannot be exported as pdf"), outcome.pdfExport)
    assertEquals((Status.Ok, "Charter\nMinutes"), outcome.october)
    assertEquals((Status.Ok, "Budget"), outcome.september)
    assertEquals(Status.NotFound, outcome.incompleteMonth._1)
    assertEquals((Status.Ok, "Charter (3 revisions)"), outcome.document)
    assertEquals((Status.Ok, "Charter r2: adds voting rules"), outcome.revision)
    assertEquals((Status.NotFound, "Charter has no revision 9"), outcome.unknownRevision)
    assertEquals(Status.NotFound, outcome.malformedId._1)
    assertEquals((Status.MovedPermanently, Some(Uri.unsafeFromString(s"/docs/$Charter"))), outcome.legacy)
    assertEquals((Status.SeeOther, Some(Uri.unsafeFromString(s"/docs/$Minutes"))), outcome.published)
    assertEquals((Status.TemporaryRedirect, Some(Uri.unsafeFromString(s"/docs/$Budget"))), outcome.latest)
  }
}

object DocumentArchivePathExtractorsTest {

  /** What the client saw for each request. */
  final case class Observed(
      stylesheet: (Status, String),
      logoHead: (Status, Option[String]),
      missingAsset: (Status, String),
      csvExport: (Status, Option[MediaType], String),
      jsonExport: (Status, Option[MediaType], String),
      pdfExport: (Status, String),
      october: (Status, String),
      september: (Status, String),
      incompleteMonth: (Status, String),
      document: (Status, String),
      revision: (Status, String),
      unknownRevision: (Status, String),
      malformedId: (Status, String),
      legacy: (Status, Option[Uri]),
      published: (Status, Option[Uri]),
      latest: (Status, Option[Uri])
  )

  /** One archived document with its revision notes, oldest first. */
  final case class Document(title: String, year: Int, month: Int, revisions: List[String])

  val Charter: UUID = UUID.fromString("6f1c2a54-0b1e-4c7a-9d3e-2a5b8c9d0e11")
  val Minutes: UUID = UUID.fromString("a3e5d7c9-1f2b-4d6e-8a0c-3b5d7f9a1c22")
  val Budget: UUID = UUID.fromString("c8b6a4f2-3e1d-4b5c-9a7e-6f4d2b0c8e33")

  /** The archive contents, in publication order. */
  val documents: List[(UUID, Document)] = List(
    Charter -> Document("Charter", 2026, 10, List("initial draft", "adds voting rules", "final wording")),
    Minutes -> Document("Minutes", 2026, 10, List("recorded")),
    Budget -> Document("Budget", 2026, 9, List("proposal", "approved"))
  )

  /** Ids the archive kept from the numbered document scheme it replaced. */
  val legacyIds: Map[Int, UUID] = Map(7 -> Charter, 8 -> Minutes)

  val assets: Map[String, String] = Map(
    "css/site.css" -> "body { margin: 0 }",
    "img/logo.svg" -> "<svg/>"
  )

  /** Matches a `month;year=YYYY;number=MM` segment, in any parameter order. */
  object MonthMatrix extends MatrixVar("month", List("year", "number"))

  /** Asset reads accept both methods a cache or browser uses. */
  val Readable: MethodConcat = GET | HEAD

  /** The archive API as an application would mount it at the server root. */
  val archiveRoutes: HttpRoutes[IO] = HttpRoutes.of[IO] {
    case Readable(_) -> "assets" /: rest =>
      val path: String = rest.segments.map(_.decoded()).mkString("/")
      assets.get(path) match {
        case Some(content) => Ok(content, Header.Raw(ci"X-Asset-Path", path))
        case None => NotFound(s"no asset $path")
      }

    case GET -> Root / "reports" / (name ~ "csv") =>
      val rows: List[String] = documents.map((_, doc) => f"${doc.title},${doc.year}-${doc.month}%02d")
      Ok(("title,month" :: rows).mkString("\n")).map(_.withContentType(`Content-Type`(MediaType.text.csv)))

    case GET -> Root / "reports" / (name ~ "json") =>
      val titles: String = documents.map((_, doc) => "\"" + doc.title + "\"").mkString("[", ",", "]")
      Ok(titles).map(_.withContentType(`Content-Type`(MediaType.application.json)))

    case GET -> Root / "reports" / (name ~ extension) =>
      NotAcceptable(s"$name cannot be exported as $extension")

    case GET -> Root / "archive" / MonthMatrix(IntVar(year), IntVar(month)) =>
      Ok(documents.collect { case (_, doc) if doc.year == year && doc.month == month => doc.title }.mkString("\n"))

    case GET -> Root / "docs" / "latest" =>
      TemporaryRedirect(Location(uri"/docs" / documents.last._1.toString))

    case GET -> Root / "docs" / UUIDVar(id) =>
      lookup(id)(doc => Ok(s"${doc.title} (${doc.revisions.size} revisions)"))

    case GET -> Root / "docs" / UUIDVar(id) / "revisions" / LongVar(number) =>
      lookup(id) { doc =>
        doc.revisions.lift(number.toInt - 1) match {
          case Some(note) => Ok(s"${doc.title} r$number: $note")
          case None => NotFound(s"${doc.title} has no revision $number")
        }
      }

    case POST -> Root / "docs" / UUIDVar(id) / "publish" =>
      lookup(id)(_ => SeeOther(Location(uri"/docs" / id.toString)))

    case GET -> Root / "legacy" / "doc" / IntVar(number) =>
      legacyIds.get(number) match {
        case Some(id) => MovedPermanently(Location(uri"/docs" / id.toString))
        case None => NotFound(s"no legacy document $number")
      }
  }

  /** Runs `found` for a known document and answers `404` otherwise. */
  def lookup(id: UUID)(found: Document => IO[Response[IO]]): IO[Response[IO]] =
    documents.find(_._1 == id).fold(NotFound(s"no document $id"))((_, doc) => found(doc))

  /** Starts the archive on an ephemeral loopback port and runs `scenario` against it. */
  def serveAndCall[A](scenario: (Client[IO], String) => IO[A]): A = {
    val running: Resource[IO, (Client[IO], String)] = for {
      server <- EmberServerBuilder
        .default[IO]
        .withHost(ipv4"127.0.0.1")
        .withPort(port"0")
        .withHttpApp(archiveRoutes.orNotFound)
        .build
      client <- EmberClientBuilder.default[IO].build
    } yield (client, s"http://127.0.0.1:${server.address.getPort}")
    running.use((client, base) => scenario(client, base)).unsafeRunSync()
  }

  /** Sends `request` and reads the whole response body as text. */
  def call(client: Client[IO], request: Request[IO]): IO[(Status, String)] =
    client.run(request).use(response => response.as[String].map(body => (response.status, body)))

  /** Sends `request` and reads the status, media type and body. */
  def typed(client: Client[IO], request: Request[IO]): IO[(Status, Option[MediaType], String)] =
    client.run(request).use { response =>
      response.as[String].map(body => (response.status, response.contentType.map(_.mediaType), body))
    }

  /** Sends `request` and reads the status and redirect target without following it. */
  def redirect(client: Client[IO], request: Request[IO]): IO[(Status, Option[Uri])] =
    client.run(request).use(response => IO.pure((response.status, response.headers.get[Location].map(_.uri))))
}
