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
import io.circe.Encoder
import io.circe.Json
import io.circe.generic.semiauto.deriveEncoder
import io.circe.parser.parse
import io.circe.syntax.*
import org.http4s.Charset
import org.http4s.EntityEncoder
import org.http4s.Header
import org.http4s.HttpRoutes
import org.http4s.LanguageTag
import org.http4s.MediaRange
import org.http4s.MediaType
import org.http4s.Method
import org.http4s.QValue
import org.http4s.Request
import org.http4s.Response
import org.http4s.Status
import org.http4s.Uri
import org.http4s.circe.*
import org.http4s.client.Client
import org.http4s.dsl.io.*
import org.http4s.ember.client.EmberClientBuilder
import org.http4s.ember.server.EmberServerBuilder
import org.http4s.headers.Accept
import org.http4s.headers.`Accept-Language`
import org.http4s.headers.`Content-Language`
import org.http4s.headers.`Content-Type`
import org.http4s.implicits.*
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.typelevel.ci.*

/**
 * Proactive content negotiation on the `Accept` and `Accept-Language` request headers.
 *
 * A weather service offers each station report as JSON, CSV or plain text and in English, German
 * or French. Its DSL route reads the typed `Accept` and `Accept-Language` headers, weighs every
 * offered representation by the quality value of the most specific media range that matches it
 * (so subtype wildcards, the full wildcard and `q=0` exclusions behave as RFC 9110 describes), and
 * answers with the winner, a `Content-Language` header and `Vary: Accept, Accept-Language`. A
 * request nothing can satisfy gets `406 Not Acceptable` listing the supported types. The service
 * runs on an Ember server and is called by an Ember client sending both raw browser-style and
 * typed headers.
 */
class WeatherReportContentNegotiationTest {
  import WeatherReportContentNegotiationTest.*

  @Test
  def weatherReportsAreNegotiatedByMediaTypeAndLanguage(): Unit = {
    val outcome: Observed = serveAndCall { (client: Client[IO], base: Uri) =>
      val report: Uri = base / "stations" / "zrh" / "report"
      def raw(accept: String, language: String): Request[IO] =
        Request[IO](Method.GET, report).putHeaders(
          Header.Raw(ci"Accept", accept),
          Header.Raw(ci"Accept-Language", language)
        )

      for {
        defaults <- call(client, Request[IO](Method.GET, report))
        csvInGerman <- call(client, raw("text/csv;q=0.8, application/json;q=0.5", "fr;q=0.4, de;q=0.9"))
        anyTextInFrench <- call(client, raw("text/*", "fr, en;q=0.6"))
        jsonExcluded <- call(client, raw("application/json;q=0, */*;q=0.3", "de;q=0, *;q=0.2"))
        typed <- call(
          client,
          Request[IO](Method.GET, report).putHeaders(
            Accept(
              MediaType.text.plain.withQValue(QValue.unsafeFromString("0.9")),
              MediaType.application.json.withQValue(QValue.unsafeFromString("0.7"))
            ),
            `Accept-Language`(LanguageTag("de"), LanguageTag("en").withQValue(QValue.unsafeFromString("0.5")))
          )
        )
        notAcceptable <- call(client, raw("image/png, application/xml", "en"))
        unknownStation <- call(client, Request[IO](Method.GET, base / "stations" / "xyz" / "report"))
      } yield Observed(defaults, csvInGerman, anyTextInFrench, jsonExcluded, typed, notAcceptable, unknownStation)
    }

    assertEquals(Status.Ok, outcome.defaults.status)
    assertEquals(Some(MediaType.application.json), outcome.defaults.mediaType)
    assertEquals(Some("en"), outcome.defaults.language)
    assertEquals(Some("Accept, Accept-Language"), outcome.defaults.vary)
    assertEquals(
      Right(Json.obj("station" -> "zrh".asJson, "temperature" -> 21.asJson, "summary" -> "Clear sky".asJson)),
      parse(outcome.defaults.body)
    )

    assertEquals(
      Exchange(
        Status.Ok,
        Some(MediaType.text.csv),
        Some("de"),
        Some("Accept, Accept-Language"),
        "station,temperature,summary\nzrh,21,Klarer Himmel\n"
      ),
      outcome.csvInGerman
    )
    assertEquals(
      Exchange(
        Status.Ok,
        Some(MediaType.text.csv),
        Some("fr"),
        Some("Accept, Accept-Language"),
        "station,temperature,summary\nzrh,21,Ciel clair\n"
      ),
      outcome.anyTextInFrench
    )
    assertEquals(Some(MediaType.text.csv), outcome.jsonExcluded.mediaType)
    assertEquals(Some("en"), outcome.jsonExcluded.language)
    assertEquals(
      Exchange(
        Status.Ok,
        Some(MediaType.text.plain),
        Some("de"),
        Some("Accept, Accept-Language"),
        "Station zrh: 21 C, Klarer Himmel"
      ),
      outcome.typed
    )
    assertEquals(Status.NotAcceptable, outcome.notAcceptable.status)
    assertEquals("supported: application/json, text/csv, text/plain", outcome.notAcceptable.body)
    assertEquals(Some("Accept, Accept-Language"), outcome.notAcceptable.vary)
    assertEquals(Status.NotFound, outcome.unknownStation.status)
    assertEquals("no station xyz", outcome.unknownStation.body)
  }
}

object WeatherReportContentNegotiationTest {

  /** What the client read from one response. */
  final case class Exchange(
      status: Status,
      mediaType: Option[MediaType],
      language: Option[String],
      vary: Option[String],
      body: String
  )

  /** What the client saw for each negotiated request. */
  final case class Observed(
      defaults: Exchange,
      csvInGerman: Exchange,
      anyTextInFrench: Exchange,
      jsonExcluded: Exchange,
      typed: Exchange,
      notAcceptable: Exchange,
      unknownStation: Exchange
  )

  /** A station report rendered in one language. */
  final case class Report(station: String, temperature: Int, summary: String)

  given Encoder[Report] = deriveEncoder[Report]

  /** A report rendered as a CSV table, served as `text/csv`. */
  final case class CsvTable(text: String)

  given EntityEncoder[IO, CsvTable] =
    EntityEncoder
      .stringEncoder[IO]
      .contramap[CsvTable](_.text)
      .withContentType(`Content-Type`(MediaType.text.csv, Charset.`UTF-8`))

  /** Representations in the server's order of preference, used to break quality ties. */
  val Offered: List[MediaType] = List(MediaType.application.json, MediaType.text.csv, MediaType.text.plain)

  /** Languages the reports are translated into; the first is the default. */
  val Languages: List[LanguageTag] = List(LanguageTag("en"), LanguageTag("de"), LanguageTag("fr"))

  val Summaries: Map[String, String] = Map("en" -> "Clear sky", "de" -> "Klarer Himmel", "fr" -> "Ciel clair")

  val Temperatures: Map[String, Int] = Map("zrh" -> 21, "gva" -> 18)

  val VaryHeader: Header.Raw = Header.Raw(ci"Vary", "Accept, Accept-Language")

  /** How specific a media range is: an exact type beats a subtype wildcard, then the full one. */
  def specificity(range: MediaRange): Int = range match {
    case _: MediaType => 2
    case wildcard if wildcard == MediaRange.`*/*` => 0
    case _ => 1
  }

  /** The quality the client gives `offered`, taken from its most specific matching range. */
  def quality(accept: Option[Accept], offered: MediaType): QValue =
    accept.fold(QValue.One) { header =>
      header.values.toList
        .filter(entry => offered.satisfies(entry.mediaRange))
        .maxByOption(entry => specificity(entry.mediaRange))
        .fold(QValue.Zero)(_.qValue)
    }

  /** The acceptable representation with the highest quality, preferring earlier offers on ties. */
  def chooseMediaType(accept: Option[Accept]): Option[MediaType] =
    Offered
      .map(offered => (offered, quality(accept, offered)))
      .filter((_, q) => q.isAcceptable)
      .sortBy((_, q) => q)(using Ordering[QValue].reverse)
      .headOption
      .map(_._1)

  /** The acceptable language with the highest quality, falling back to the default. */
  def chooseLanguage(acceptLanguage: Option[`Accept-Language`]): LanguageTag =
    acceptLanguage
      .flatMap { header =>
        Languages
          .map(tag => (tag, header.qValue(tag)))
          .filter((_, q) => q.isAcceptable)
          .sortBy((_, q) => q)(using Ordering[QValue].reverse)
          .headOption
          .map(_._1)
      }
      .getOrElse(Languages.head)

  /** Renders `report` in the negotiated representation. */
  def render(report: Report, mediaType: MediaType): IO[Response[IO]] =
    if (mediaType == MediaType.application.json) Ok(report.asJson)
    else if (mediaType == MediaType.text.csv)
      Ok(CsvTable(s"station,temperature,summary\n${report.station},${report.temperature},${report.summary}\n"))
    else Ok(s"Station ${report.station}: ${report.temperature} C, ${report.summary}")

  /** The weather API an application would serve. */
  val weatherRoutes: HttpRoutes[IO] = HttpRoutes.of[IO] {
    case request @ GET -> Root / "stations" / station / "report" =>
      Temperatures.get(station) match {
        case None => NotFound(s"no station $station")
        case Some(temperature) =>
          chooseMediaType(request.headers.get[Accept]) match {
            case None =>
              val supported: String = Offered.map(offered => s"${offered.mainType}/${offered.subType}").mkString(", ")
              NotAcceptable(s"supported: $supported", VaryHeader)
            case Some(mediaType) =>
              val language: LanguageTag = chooseLanguage(request.headers.get[`Accept-Language`])
              render(Report(station, temperature, Summaries(language.primaryTag)), mediaType)
                .map(_.putHeaders(`Content-Language`(language), VaryHeader))
          }
      }
  }

  /** Starts the service on an ephemeral loopback port and runs `scenario` against it. */
  def serveAndCall[A](scenario: (Client[IO], Uri) => IO[A]): A = {
    val running: Resource[IO, (Client[IO], Uri)] = for {
      server <- EmberServerBuilder
        .default[IO]
        .withHost(ipv4"127.0.0.1")
        .withPort(port"0")
        .withHttpApp(weatherRoutes.orNotFound)
        .build
      client <- EmberClientBuilder.default[IO].build
    } yield (client, Uri.unsafeFromString(s"http://127.0.0.1:${server.address.getPort}"))
    running.use((client, base) => scenario(client, base)).unsafeRunSync()
  }

  /** Sends `request` and reads the status, negotiation headers and whole body. */
  def call(client: Client[IO], request: Request[IO]): IO[Exchange] =
    client.run(request).use { response =>
      response.as[String].map { body =>
        Exchange(
          response.status,
          response.contentType.map(_.mediaType),
          response.headers.get[`Content-Language`].map(_.values.head.renderString),
          response.headers.get(ci"Vary").map(_.head.value),
          body
        )
      }
    }
}
