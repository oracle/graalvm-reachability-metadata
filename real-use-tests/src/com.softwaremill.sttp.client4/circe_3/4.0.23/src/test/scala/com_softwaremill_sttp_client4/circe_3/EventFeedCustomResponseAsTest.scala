/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_softwaremill_sttp_client4.circe_3

import com.sun.net.httpserver.HttpExchange
import com.sun.net.httpserver.HttpServer
import io.circe.DecodingFailure
import io.circe.Error
import io.circe.ParsingFailure
import io.circe.generic.auto.*
import io.circe.syntax.*
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import sttp.client4.*
import sttp.client4.circe.*
import sttp.model.MediaType
import sttp.model.ResponseMetadata
import sttp.model.StatusCode

import java.net.InetSocketAddress
import java.nio.charset.StandardCharsets
import scala.concurrent.duration.*

/**
 * Exercises custom response handlers built from circe's `deserializeJson`: an event-feed client
 * composes it with `asStringAlways`, `mapWithMetadata` and `fromMetadata` to decode NDJSON lines
 * one by one, pair JSON pages with a cursor header, and pick a decoder by response content type.
 */
class EventFeedCustomResponseAsTest {
  private final case class Event(id: Long, kind: String, payload: Map[String, String])
  private final case class EventPage(events: List[Event], nextCursor: Option[String])
  private final case class FeedError(code: String, message: String)

  private val ndjsonType: String = "application/x-ndjson"

  private val events: List[Event] = List(
    Event(1L, "user.created", Map("user" -> "ada")),
    Event(2L, "user.renamed", Map("user" -> "ada", "name" -> "Countess Ada")),
    Event(3L, "order.placed", Map("order" -> "o-17", "total" -> "42.50")),
    Event(4L, "user.deleted", Map("user" -> "grace"))
  )

  private var server: HttpServer = null
  private var backend: SyncBackend = null

  @BeforeEach
  def startServer(): Unit = {
    server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0)
    server.createContext(
      "/events/stream",
      (exchange: HttpExchange) => {
        try {
          val lines: List[String] = events.take(2).map(_.asJson.noSpaces) ++
            List("""{"id":"three","kind":"broken"}""", "{not json", events(3).asJson.noSpaces)
          respond(exchange, 200, ndjsonType, lines.mkString("", "\n", "\n"))
        } finally {
          exchange.close()
        }
      }
    )
    server.createContext(
      "/events/page",
      (exchange: HttpExchange) => {
        try {
          val query: String = Option(exchange.getRequestURI.getQuery).getOrElse("")
          val offset: Int = if (query == "cursor=c2") 2 else 0
          val page: List[Event] = events.slice(offset, offset + 2)
          if (offset + 2 < events.size) {
            exchange.getResponseHeaders.set("X-Next-Cursor", s"c${offset + 2}")
          }
          respond(exchange, 200, "application/json", page.asJson.noSpaces)
        } finally {
          exchange.close()
        }
      }
    )
    server.createContext(
      "/events/export",
      (exchange: HttpExchange) => {
        try {
          val accept: String = Option(exchange.getRequestHeaders.getFirst("Accept")).getOrElse("")
          if (accept.contains(ndjsonType)) {
            respond(exchange, 200, ndjsonType, events.map(_.asJson.noSpaces).mkString("\n"))
          } else if (accept.contains("application/json")) {
            respond(exchange, 200, "application/json", events.asJson.noSpaces)
          } else {
            respond(exchange, 406, "application/problem+json", FeedError("not_acceptable", accept).asJson.noSpaces)
          }
        } finally {
          exchange.close()
        }
      }
    )
    server.start()
    backend = DefaultSyncBackend(BackendOptions.connectionTimeout(10.seconds))
  }

  @AfterEach
  def stopServer(): Unit = {
    backend.close()
    server.stop(0)
  }

  @Test
  def decodesEachNdjsonLineIndependently(): Unit = {
    val response: Response[List[Either[Error, Event]]] = basicRequest
      .get(uri"http://127.0.0.1:${port}/events/stream")
      .response(ndjsonEvents)
      .readTimeout(10.seconds)
      .send(backend)

    assertThat(response.code).isEqualTo(StatusCode.Ok)
    val decoded: List[Either[Error, Event]] = response.body
    assertThat(decoded.size).isEqualTo(5)
    assertThat(decoded.collect { case Right(event) => event.id }).isEqualTo(List(1L, 2L, 4L))
    assertThat(decoded(1)).isEqualTo(Right(events(1)))
    decoded(2) match {
      case Left(failure: DecodingFailure) => assertThat(failure.history.toString).contains("id")
      case other => throw new AssertionError(s"Expected a decoding failure for a mistyped id, got $other")
    }
    assertThat(decoded(3).left.toOption.get).isInstanceOf(classOf[ParsingFailure])
  }

  @Test
  def combinesDecodedPagesWithCursorHeader(): Unit = {
    val first: EventPage = fetchPage(None)
    assertThat(first.events.map(_.id)).isEqualTo(List(1L, 2L))
    assertThat(first.nextCursor).isEqualTo(Some("c2"))

    val second: EventPage = fetchPage(first.nextCursor)
    assertThat(second.events.map(_.kind)).isEqualTo(List("order.placed", "user.deleted"))
    assertThat(second.events.head.payload).isEqualTo(Map("order" -> "o-17", "total" -> "42.50"))
    assertThat(second.nextCursor).isEqualTo(None)
  }

  @Test
  def choosesDecoderFromResponseContentType(): Unit = {
    val asNdjson: Either[FeedError, List[Event]] = exportEvents(ndjsonType)
    val asArray: Either[FeedError, List[Event]] = exportEvents("application/json")
    val rejected: Either[FeedError, List[Event]] = exportEvents("text/csv")

    assertThat(asNdjson).isEqualTo(Right(events))
    assertThat(asArray).isEqualTo(Right(events))
    assertThat(rejected).isEqualTo(Left(FeedError("not_acceptable", "text/csv")))
    assertThat(exportHandler.show).isEqualTo("event export")
  }

  /** Decodes every non-empty line of an NDJSON body on its own, keeping per-line failures. */
  private def ndjsonEvents: ResponseAs[List[Either[Error, Event]]] = {
    val decodeLine: String => Either[Error, Event] = deserializeJson[Event]
    asStringAlways.map(body => body.linesIterator.filter(_.nonEmpty).map(decodeLine).toList)
  }

  /** Decodes a JSON array page and reads the continuation cursor from the response headers. */
  private def pageHandler: ResponseAs[EventPage] =
    asStringAlways.mapWithMetadata { (body: String, metadata: ResponseMetadata) =>
      deserializeJson[List[Event]].apply(body) match {
        case Right(page) => EventPage(page, metadata.header("X-Next-Cursor"))
        case Left(error) => throw error
      }
    }

  /** Picks an NDJSON or array decoder by content type and decodes anything else as a feed error. */
  private def exportHandler: ResponseAs[Either[FeedError, List[Event]]] = {
    def orThrow[T](result: Either[Error, T]): T = result.fold(error => throw error, identity)
    val asFeedError: ResponseAs[Either[FeedError, List[Event]]] =
      asStringAlways.map(body => Left(orThrow(deserializeJson[FeedError].apply(body))))
    val asNdjsonList: ResponseAs[Either[FeedError, List[Event]]] =
      ndjsonEvents.map(lines => Right(lines.map(orThrow)))
    val asJsonList: ResponseAs[Either[FeedError, List[Event]]] =
      asStringAlways.map(body => Right(orThrow(deserializeJson[List[Event]].apply(body))))
    fromMetadata(
      asFeedError,
      ConditionalResponseAs(
        (metadata: ResponseMetadata) => metadata.isSuccess && metadata.contentType.contains(ndjsonType),
        asNdjsonList
      ),
      ConditionalResponseAs(
        (metadata: ResponseMetadata) =>
          metadata.isSuccess && metadata.contentType.contains(MediaType.ApplicationJson.toString),
        asJsonList
      )
    ).showAs("event export")
  }

  private def fetchPage(cursor: Option[String]): EventPage =
    basicRequest
      .get(uri"http://127.0.0.1:${port}/events/page?cursor=$cursor")
      .response(pageHandler)
      .readTimeout(10.seconds)
      .send(backend)
      .body

  private def exportEvents(accept: String): Either[FeedError, List[Event]] =
    basicRequest
      .get(uri"http://127.0.0.1:${port}/events/export")
      .header("Accept", accept)
      .response(exportHandler)
      .readTimeout(10.seconds)
      .send(backend)
      .body

  private def port: Int = server.getAddress.getPort

  private def respond(exchange: HttpExchange, status: Int, contentType: String, body: String): Unit = {
    val bytes: Array[Byte] = body.getBytes(StandardCharsets.UTF_8)
    exchange.getResponseHeaders.set("Content-Type", contentType)
    exchange.sendResponseHeaders(status, bytes.length.toLong)
    exchange.getResponseBody.write(bytes)
  }
}
