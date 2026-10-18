/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_softwaremill_sttp_tapir.tapir_json_circe_3

import com.sun.net.httpserver.HttpServer
import io.circe.Codec as CirceCodec
import io.circe.Json
import io.circe.parser.parse
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import sttp.client3.HttpClientSyncBackend
import sttp.client3.Identity
import sttp.client3.Response
import sttp.client3.SttpBackend
import sttp.client3.basicRequest
import sttp.model.Header
import sttp.model.MediaType
import sttp.model.StatusCode
import sttp.model.Uri
import sttp.model.Uri.UriContext
import sttp.tapir.*
import sttp.tapir.client.sttp.SttpClientInterpreter
import sttp.tapir.json.circe.*
import sttp.tapir.server.jdkhttp.JdkHttpServer

import java.util.concurrent.ConcurrentHashMap

/**
 * Content negotiation between a circe JSON representation and a CSV representation of the same
 * resource, with `jsonBody` as one variant of a `oneOfBody` chosen by `Accept` and `Content-Type`.
 *
 * A weather station service publishes its readings as JSON or CSV, picking the representation from
 * the client's `Accept` header and its quality values, and accepts uploads in either format,
 * decoding the body by its `Content-Type`. A JSON-speaking client built from the same endpoint
 * definitions reads the readings back over real HTTP.
 */
class StationReadingsContentNegotiationTest {
  import StationReadingsContentNegotiationTest.*

  private var server: HttpServer = null
  private var backend: SttpBackend[Identity, Any] = null
  private var baseUri: Uri = null
  private val store: ReadingStore = new ReadingStore

  @BeforeEach
  def startServer(): Unit = {
    store.put("oslo", List(Reading("2026-10-18T06:00Z", 4.5, 81), Reading("2026-10-18T12:00Z", 9.25, 64)))
    server = JdkHttpServer()
      .host("localhost")
      .port(0)
      .addEndpoints(store.serverEndpoints)
      .start()
    backend = HttpClientSyncBackend()
    baseUri = uri"http://localhost:${server.getAddress.getPort}"
  }

  @AfterEach
  def stopServer(): Unit = {
    if (backend != null) backend.close()
    if (server != null) server.stop(0)
  }

  @Test
  def answersWithJsonWhenTheClientAcceptsJson(): Unit = {
    val raw: Response[Either[String, String]] = basicRequest
      .get(uri"$baseUri/stations/oslo/readings")
      .header(Header.accept(MediaType.ApplicationJson))
      .send(backend)

    assertEquals(StatusCode.Ok, raw.code)
    assertTrue(raw.header("Content-Type").exists(_.startsWith("application/json")))
    val json: Json = parse(raw.body.fold(identity, identity)).fold(throw _, identity)
    val readings: List[Json] = json.asArray.map(_.toList).getOrElse(Nil)
    assertEquals(2, readings.size)
    assertEquals(Right(9.25), readings(1).hcursor.get[Double]("celsius"))
    assertEquals(Right(64), readings(1).hcursor.get[Int]("humidity"))
  }

  @Test
  def answersWithCsvWhenTheClientAcceptsCsv(): Unit = {
    val raw: Response[Either[String, String]] = basicRequest
      .get(uri"$baseUri/stations/oslo/readings")
      .header(Header.accept(MediaType.TextCsv))
      .send(backend)

    assertEquals(StatusCode.Ok, raw.code)
    assertTrue(raw.header("Content-Type").exists(_.startsWith("text/csv")))
    assertEquals(
      "observedAt,celsius,humidity\n2026-10-18T06:00Z,4.5,81\n2026-10-18T12:00Z,9.25,64",
      raw.body.fold(identity, identity)
    )
  }

  @Test
  def honoursTheQualityValuesOfTheAcceptHeader(): Unit = {
    val preferJson: Response[Either[String, String]] = basicRequest
      .get(uri"$baseUri/stations/oslo/readings")
      .header("Accept", "text/csv;q=0.4, application/json;q=0.9")
      .send(backend)
    val preferCsv: Response[Either[String, String]] = basicRequest
      .get(uri"$baseUri/stations/oslo/readings")
      .header("Accept", "application/json;q=0.2, text/csv")
      .send(backend)

    assertTrue(preferJson.header("Content-Type").exists(_.startsWith("application/json")))
    assertTrue(preferCsv.header("Content-Type").exists(_.startsWith("text/csv")))
  }

  @Test
  def decodesUploadsByTheirContentType(): Unit = {
    val csvUpload: Response[Either[String, String]] = basicRequest
      .post(uri"$baseUri/stations/bergen/readings")
      .contentType(MediaType.TextCsv)
      .body("observedAt,celsius,humidity\n2026-10-18T06:00Z,7.0,90")
      .send(backend)
    val jsonUpload: Response[Either[String, String]] = basicRequest
      .post(uri"$baseUri/stations/bergen/readings")
      .contentType(MediaType.ApplicationJson)
      .body("""[{"observedAt":"2026-10-18T12:00Z","celsius":11.5,"humidity":72}]""")
      .send(backend)

    assertEquals(StatusCode.NoContent, csvUpload.code)
    assertEquals(StatusCode.NoContent, jsonUpload.code)
    assertEquals(
      List(Reading("2026-10-18T06:00Z", 7.0, 90), Reading("2026-10-18T12:00Z", 11.5, 72)),
      store.readingsOf("bergen")
    )
  }

  @Test
  def readsTheJsonRepresentationThroughTheClientInterpreter(): Unit = {
    val client: SttpClientInterpreter = SttpClientInterpreter()

    val readings: Either[Unit, List[Reading]] = client
      .toRequestThrowDecodeFailures(listReadings, Some(baseUri))
      .apply("oslo")
      .send(backend)
      .body

    assertEquals(Right(List(Reading("2026-10-18T06:00Z", 4.5, 81), Reading("2026-10-18T12:00Z", 9.25, 64))), readings)
  }
}

object StationReadingsContentNegotiationTest {
  final case class Reading(observedAt: String, celsius: Double, humidity: Int) derives CirceCodec.AsObject, Schema

  /** The CSV media type as a tapir codec format. */
  final case class CsvFormat() extends CodecFormat {
    override val mediaType: MediaType = MediaType.TextCsv
  }

  private val CsvHeader: String = "observedAt,celsius,humidity"

  private def toCsv(readings: List[Reading]): String =
    (CsvHeader :: readings.map(r => s"${r.observedAt},${r.celsius},${r.humidity}")).mkString("\n")

  private def fromCsv(text: String): DecodeResult[List[Reading]] = {
    val rows: List[String] = text.linesIterator.map(_.trim).filter(_.nonEmpty).toList
    rows match {
      case CsvHeader :: data =>
        val parsed: List[Option[Reading]] = data.map(_.split(',').toList).map {
          case List(at, celsius, humidity) =>
            for {
              c <- celsius.toDoubleOption
              h <- humidity.toIntOption
            } yield Reading(at, c, h)
          case _ => None
        }
        if (parsed.forall(_.isDefined)) DecodeResult.Value(parsed.flatten)
        else DecodeResult.Error(text, new IllegalArgumentException("malformed CSV row"))
      case _ => DecodeResult.Error(text, new IllegalArgumentException("missing CSV header"))
    }
  }

  private val readingsCsvCodec: Codec[String, List[Reading], CsvFormat] = Codec.string
    .format(CsvFormat())
    .mapDecode(fromCsv)(toCsv)
    .schema(summon[Schema[List[Reading]]])

  private val readingsBody = oneOfBody(
    jsonBody[List[Reading]],
    stringBodyUtf8AnyFormat(readingsCsvCodec)
  )

  val listReadings: PublicEndpoint[String, Unit, List[Reading], Any] = endpoint.get
    .in("stations" / path[String]("stationId") / "readings")
    .out(readingsBody)

  val uploadReadings: PublicEndpoint[(String, List[Reading]), Unit, Unit, Any] = endpoint.post
    .in("stations" / path[String]("stationId") / "readings")
    .in(readingsBody)
    .out(statusCode(StatusCode.NoContent))

  /** In-memory reading storage behind the two endpoints. */
  final class ReadingStore {
    private val readings: ConcurrentHashMap[String, List[Reading]] = new ConcurrentHashMap[String, List[Reading]]()

    def put(station: String, values: List[Reading]): Unit = readings.put(station, values)

    def readingsOf(station: String): List[Reading] = readings.getOrDefault(station, Nil)

    val serverEndpoints = List(
      listReadings.handleSuccess(station => readingsOf(station)),
      uploadReadings.handleSuccess { case (station, uploaded) =>
        readings.merge(station, uploaded, (existing, added) => existing ++ added)
        ()
      }
    )
  }
}
