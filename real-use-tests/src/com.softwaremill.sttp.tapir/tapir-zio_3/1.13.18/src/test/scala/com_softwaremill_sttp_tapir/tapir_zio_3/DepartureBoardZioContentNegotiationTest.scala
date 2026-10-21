/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_softwaremill_sttp_tapir.tapir_zio_3

import org.junit.jupiter.api.Assertions.{assertEquals, assertTrue}
import org.junit.jupiter.api.Test
import sttp.client4.*
import sttp.client4.httpclient.zio.HttpClientZioBackend
import sttp.model.{HeaderNames, MediaType, StatusCode}
import sttp.tapir.{Codec, CodecFormat, DecodeResult, Schema}
import sttp.tapir.json.zio.*
import sttp.tapir.server.ziohttp.ZioHttpInterpreter
import sttp.tapir.ztapir.*
import zio.http.Server
import zio.json.{DecoderOps, DeriveJsonCodec, JsonCodec}
import zio.{Ref, Runtime, Task, UIO, Unsafe, ZIO, ZLayer}

/**
 * Content negotiation of request and response bodies with `oneOfBody`.
 *
 * A railway station departure board is served by `zServerLogic` endpoints that read a
 * `DepartureBoards` service from the ZIO environment. The board output offers JSON, CSV and
 * HTML representations; the server picks one from the client's `Accept` header, honouring
 * quality values and falling back to the first representation when any type is accepted.
 * Departures are announced either as JSON or as a CSV line, chosen by the request
 * `Content-Type`. Station codes travel in the path through a custom codec that rejects
 * malformed codes. The test checks the chosen bodies and their `Content-Type` headers, that an
 * unsupported `Accept` or `Content-Type` is refused, and that departures announced in either
 * format show up on the board.
 */
class DepartureBoardZioContentNegotiationTest {
  import DepartureBoardZioContentNegotiationTest.*

  @Test
  def boardsAreServedAndDeparturesAcceptedInTheNegotiatedFormat(): Unit = {
    val program: ZIO[Server & DepartureBoards, Throwable, Observed] = for {
      port <- Server.install(ZioHttpInterpreter().toHttp(serverEndpoints))
      observed <- ZIO.scoped(HttpClientZioBackend.scoped().flatMap(backend => exercise(port, backend)))
    } yield observed

    val observed: Observed = Unsafe.unsafe { implicit u =>
      Runtime.default.unsafe.run(program.provide(Server.defaultWithPort(0), InMemoryDepartureBoards.layer))
        .getOrThrowFiberFailure()
    }

    assertEquals(StatusCode.Ok, observed.asJson.code)
    assertTrue(observed.asJson.contentType.startsWith("application/json"), observed.asJson.contentType)
    assertEquals(Board("CAM", List(firstTrain)), decode[Board](observed.asJson.body))

    assertEquals(StatusCode.Ok, observed.asCsv.code)
    assertTrue(observed.asCsv.contentType.startsWith("text/csv"), observed.asCsv.contentType)
    assertEquals("train,destination,time,platform\nIC101,London,08:15,2\n", observed.asCsv.body)

    assertEquals(StatusCode.Ok, observed.asHtml.code)
    assertTrue(observed.asHtml.contentType.startsWith("text/html"), observed.asHtml.contentType)
    assertEquals(
      "<table><caption>CAM</caption><tr><td>IC101</td><td>London</td><td>08:15</td><td>2</td></tr></table>",
      observed.asHtml.body
    )

    assertTrue(observed.weighted.contentType.startsWith("text/csv"), observed.weighted.contentType)
    assertTrue(observed.anyType.contentType.startsWith("application/json"), observed.anyType.contentType)
    assertTrue(observed.noAccept.contentType.startsWith("application/json"), observed.noAccept.contentType)

    assertEquals(StatusCode.NotAcceptable, observed.xmlAccept.code)

    assertEquals(StatusCode.Created, observed.announcedJson.code)
    assertEquals(List("IC101", "RE7"), decode[Board](observed.announcedJson.body).departures.map(_.train))
    assertEquals(StatusCode.Created, observed.announcedCsv.code)
    assertEquals(List("IC101", "RE7", "S3"), decode[Board](observed.announcedCsv.body).departures.map(_.train))
    assertEquals(StatusCode.UnsupportedMediaType, observed.announcedXml.code)

    assertEquals(
      "train,destination,time,platform\nIC101,London,08:15,2\nRE7,Ely,08:40,4\nS3,Norwich,09:05,1\n",
      observed.finalCsv.body
    )
    assertEquals(StatusCode.BadRequest, observed.malformedCode.code)
    assertEquals(StatusCode.NotFound, observed.unknownStation.code)
    assertEquals(UnknownStation("ZZZ"), decode[UnknownStation](observed.unknownStation.body))
  }

  private def exercise(port: Int, backend: Backend[Task]): Task[Observed] = {
    val board: String = s"http://localhost:$port/stations/CAM/departures"
    def send(request: Request[Either[String, String]]): Task[Reply] =
      request.send(backend).map(r =>
        Reply(r.code, r.header(HeaderNames.ContentType).getOrElse(""), r.body.fold(identity, identity))
      )
    def get(accept: String): Task[Reply] = send(basicRequest.get(uri"$board").header(HeaderNames.Accept, accept))
    for {
      asJson <- get("application/json")
      asCsv <- get("text/csv")
      asHtml <- get("text/html")
      weighted <- get("text/html;q=0.2, text/csv;q=0.8, application/json;q=0.5")
      anyType <- get("*/*")
      noAccept <- send(basicRequest.get(uri"$board"))
      xmlAccept <- get("application/xml")
      announcedJson <- send(basicRequest.post(uri"$board").contentType("application/json")
        .body("""{"train":"RE7","destination":"Ely","time":"08:40","platform":4}"""))
      announcedCsv <- send(basicRequest.post(uri"$board").contentType("text/csv").body("S3,Norwich,09:05,1"))
      announcedXml <- send(basicRequest.post(uri"$board").contentType("application/xml")
        .body("<departure train=\"X1\"/>"))
      finalCsv <- get("text/csv")
      malformedCode <- send(basicRequest.get(uri"http://localhost:$port/stations/cam1/departures"))
      unknownStation <- send(basicRequest.get(uri"http://localhost:$port/stations/ZZZ/departures"))
    } yield Observed(
      asJson, asCsv, asHtml, weighted, anyType, noAccept, xmlAccept,
      announcedJson, announcedCsv, announcedXml, finalCsv, malformedCode, unknownStation
    )
  }

  private def decode[A: JsonCodec](body: String): A =
    body.fromJson[A].fold(error => throw new AssertionError(s"$error in $body"), identity)
}

object DepartureBoardZioContentNegotiationTest {
  final case class Departure(train: String, destination: String, time: String, platform: Int) derives Schema
  final case class Board(station: String, departures: List[Departure]) derives Schema
  final case class UnknownStation(station: String) derives Schema

  given JsonCodec[Departure] = DeriveJsonCodec.gen[Departure]
  given JsonCodec[Board] = DeriveJsonCodec.gen[Board]
  given JsonCodec[UnknownStation] = DeriveJsonCodec.gen[UnknownStation]

  val firstTrain: Departure = Departure("IC101", "London", "08:15", 2)

  /** A three-letter upper-case station code, carried in the request path. */
  final case class StationCode(value: String)

  given Codec[String, StationCode, CodecFormat.TextPlain] = Codec.string.mapDecode { raw =>
    if (raw.matches("[A-Z]{3}")) DecodeResult.Value(StationCode(raw))
    else DecodeResult.Error(raw, new IllegalArgumentException(s"not a station code: $raw"))
  }(_.value)

  /** The `text/csv` representation used by the board and by departure announcements. */
  final case class Csv() extends CodecFormat {
    override val mediaType: MediaType = MediaType.TextCsv
  }

  private val csvHeader: String = "train,destination,time,platform"

  private def csvRow(d: Departure): String = s"${d.train},${d.destination},${d.time},${d.platform}"

  private def parseCsvRow(row: String): DecodeResult[Departure] = row.trim.split(",").toList match {
    case List(train, destination, time, platform) if platform.toIntOption.isDefined =>
      DecodeResult.Value(Departure(train, destination, time, platform.toInt))
    case _ => DecodeResult.Error(row, new IllegalArgumentException(s"not a departure row: $row"))
  }

  private val boardCsv: Codec[String, Board, Csv] = Codec.string.format(Csv()).mapDecode { raw =>
    DecodeResult.Error(raw, new UnsupportedOperationException("boards are only rendered as CSV"))
  }(board => (csvHeader :: board.departures.map(csvRow)).map(_ + "\n").mkString)

  private val boardHtml: Codec[String, Board, CodecFormat.TextHtml] =
    Codec.string.format(CodecFormat.TextHtml()).mapDecode { raw =>
      DecodeResult.Error(raw, new UnsupportedOperationException("boards are only rendered as HTML"))
    } { board =>
      val rows: String = board.departures.map { d =>
        List(d.train, d.destination, d.time, d.platform.toString).map(cell => s"<td>$cell</td>").mkString("<tr>", "", "</tr>")
      }.mkString
      s"<table><caption>${board.station}</caption>$rows</table>"
    }

  private val departureCsv: Codec[String, Departure, Csv] =
    Codec.string.format(Csv()).mapDecode(parseCsvRow)(csvRow)

  final case class Reply(code: StatusCode, contentType: String, body: String)

  final case class Observed(
      asJson: Reply,
      asCsv: Reply,
      asHtml: Reply,
      weighted: Reply,
      anyType: Reply,
      noAccept: Reply,
      xmlAccept: Reply,
      announcedJson: Reply,
      announcedCsv: Reply,
      announcedXml: Reply,
      finalCsv: Reply,
      malformedCode: Reply,
      unknownStation: Reply
  )

  /** The service the endpoint logic requires from the ZIO environment. */
  trait DepartureBoards {
    def board(station: StationCode): UIO[Option[Board]]
    def announce(station: StationCode, departure: Departure): UIO[Option[Board]]
  }

  final class InMemoryDepartureBoards(boards: Ref[Map[String, List[Departure]]]) extends DepartureBoards {
    def board(station: StationCode): UIO[Option[Board]] =
      boards.get.map(_.get(station.value).map(Board(station.value, _)))

    def announce(station: StationCode, departure: Departure): UIO[Option[Board]] =
      boards.modify { all =>
        all.get(station.value) match {
          case Some(departures) =>
            val updated: List[Departure] = (departures :+ departure).sortBy(_.time)
            (Some(Board(station.value, updated)), all.updated(station.value, updated))
          case None => (None, all)
        }
      }
  }

  object InMemoryDepartureBoards {
    val layer: ZLayer[Any, Nothing, DepartureBoards] =
      ZLayer.fromZIO(Ref.make(Map("CAM" -> List(firstTrain))).map(new InMemoryDepartureBoards(_)))
  }

  private val stationDepartures =
    endpoint.in("stations" / path[StationCode]("station") / "departures")
      .errorOut(statusCode(StatusCode.NotFound).and(jsonBody[UnknownStation]))

  private val getBoard =
    stationDepartures.get.out(
      oneOfBody(jsonBody[Board], stringBodyUtf8AnyFormat(boardCsv), stringBodyUtf8AnyFormat(boardHtml))
    )

  private val announceDeparture =
    stationDepartures.post
      .in(oneOfBody(jsonBody[Departure], stringBodyUtf8AnyFormat(departureCsv)))
      .out(statusCode(StatusCode.Created))
      .out(jsonBody[Board])

  val serverEndpoints: List[ZServerEndpoint[DepartureBoards, Any]] = List(
    getBoard.zServerLogic[DepartureBoards] { station =>
      ZIO.serviceWithZIO[DepartureBoards](_.board(station)).someOrFail(UnknownStation(station.value))
    },
    announceDeparture.zServerLogic[DepartureBoards] { (station, departure) =>
      ZIO.serviceWithZIO[DepartureBoards](_.announce(station, departure)).someOrFail(UnknownStation(station.value))
    }
  )
}
