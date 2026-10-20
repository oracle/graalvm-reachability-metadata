/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_http4s.http4s_dsl_3

import cats.effect.IO
import cats.effect.Ref
import cats.effect.Resource
import cats.effect.unsafe.implicits.global
import com.comcast.ip4s.*
import fs2.Stream
import fs2.text
import org.http4s.HttpRoutes
import org.http4s.MediaType
import org.http4s.Method
import org.http4s.Request
import org.http4s.ServerSentEvent
import org.http4s.ServerSentEvent.EventId
import org.http4s.Status
import org.http4s.Uri
import org.http4s.client.Client
import org.http4s.dsl.io.*
import org.http4s.ember.client.EmberClientBuilder
import org.http4s.ember.server.EmberServerBuilder
import org.http4s.headers.`Content-Type`
import org.http4s.headers.`Last-Event-Id`
import org.http4s.implicits.*
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * Streamed request and response bodies, including Server-Sent Events, served by http4s DSL routes.
 *
 * A sensor feed accepts readings as a streamed line-oriented upload, answers `202 Accepted` with
 * the number of readings it stored, exports them as a chunked plain-text stream built from an
 * `fs2.Stream`, and publishes them as a `text/event-stream` of `ServerSentEvent`s that a
 * reconnecting subscriber resumes with the `Last-Event-ID` header. Malformed readings are
 * rejected with `400` before anything is stored. The service runs on an Ember server bound to an
 * ephemeral port and is driven by an Ember client that consumes every body as a stream.
 */
class SensorFeedStreamingTest {
  import SensorFeedStreamingTest.*

  @Test
  def sensorFeedStreamsUploadsExportsAndEvents(): Unit = {
    val outcome: Observed = serveAndCall { (client: Client[IO], base: Uri) =>
      val readings: Uri = base / "sensors" / "readings"
      val upload: Stream[IO, Byte] =
        Stream("boiler 71.5\n", "attic 18.25\ncellar 12", ".0\n").covary[IO].through(text.utf8.encode)
      for {
        accepted <- call(client, Request[IO](Method.POST, readings).withEntity(upload))
        rejected <- call(client, Request[IO](Method.POST, readings).withEntity("garage warm\n"))
        exported <- client.run(Request[IO](Method.GET, base / "sensors" / "export")).use { response =>
          response.body
            .through(text.utf8.decode)
            .through(text.lines)
            .filter(_.nonEmpty)
            .compile
            .toList
            .map(lines => (response.status, response.isChunked, response.contentType, lines))
        }
        subscribed <- subscribe(client, Request[IO](Method.GET, base / "sensors" / "events"))
        resumed <- subscribe(
          client,
          Request[IO](Method.GET, base / "sensors" / "events").putHeaders(`Last-Event-Id`(EventId("2")))
        )
      } yield Observed(accepted, rejected, exported, subscribed, resumed)
    }

    assertEquals((Status.Accepted, "stored 3"), outcome.accepted)
    assertEquals((Status.BadRequest, "malformed reading: garage warm"), outcome.rejected)

    val (exportStatus, exportChunked, exportType, exportLines) = outcome.exported
    assertEquals(Status.Ok, exportStatus)
    assertTrue(exportChunked, "a stream of unknown length is sent with chunked transfer encoding")
    assertEquals(Some(MediaType.text.plain), exportType.map(_.mediaType))
    assertEquals(List("1,boiler,71.5", "2,attic,18.25", "3,cellar,12.0"), exportLines)

    val (eventStatus, eventType, events) = outcome.subscribed
    assertEquals(Status.Ok, eventStatus)
    assertEquals(Some(MediaType.`text/event-stream`), eventType.map(_.mediaType))
    assertEquals(
      List(
        ("1", "reading", "boiler=71.5"),
        ("2", "reading", "attic=18.25"),
        ("3", "reading", "cellar=12.0"),
        ("3", "end", "3 readings")
      ),
      events
    )
    assertEquals(
      (Status.Ok, List(("3", "reading", "cellar=12.0"), ("3", "end", "1 readings"))),
      (outcome.resumed._1, outcome.resumed._3)
    )
  }
}

object SensorFeedStreamingTest {

  /** What the client saw at each step of the feed's lifecycle. */
  final case class Observed(
      accepted: (Status, String),
      rejected: (Status, String),
      exported: (Status, Boolean, Option[`Content-Type`], List[String]),
      subscribed: (Status, Option[`Content-Type`], List[(String, String, String)]),
      resumed: (Status, Option[`Content-Type`], List[(String, String, String)])
  )

  /** One stored measurement; ids grow monotonically from 1. */
  final case class Reading(id: Long, sensor: String, celsius: BigDecimal)

  /** Parses one `<sensor> <celsius>` upload line, or explains why it cannot. */
  def parseLine(line: String): Either[String, (String, BigDecimal)] =
    line.trim.split(' ') match {
      case Array(sensor, value) =>
        value.toDoubleOption.map(_ => (sensor, BigDecimal(value))).toRight(s"malformed reading: $line")
      case _ => Left(s"malformed reading: $line")
    }

  /** The feed API an application would mount at its root. */
  def feedRoutes(store: Ref[IO, Vector[Reading]]): HttpRoutes[IO] = HttpRoutes.of[IO] {
    case request @ POST -> Root / "sensors" / "readings" =>
      request.body
        .through(text.utf8.decode)
        .through(text.lines)
        .filter(_.trim.nonEmpty)
        .map(parseLine)
        .compile
        .toList
        .flatMap { parsed =>
          parsed.collectFirst { case Left(problem) => problem } match {
            case Some(problem) => BadRequest(problem)
            case None =>
              val accepted: List[(String, BigDecimal)] = parsed.collect { case Right(reading) => reading }
              store
                .update { existing =>
                  existing ++ accepted.zipWithIndex.map { case ((sensor, celsius), offset) =>
                    Reading(existing.size.toLong + offset + 1, sensor, celsius)
                  }
                }
                .flatMap(_ => Accepted(s"stored ${accepted.size}"))
          }
        }

    case GET -> Root / "sensors" / "export" =>
      Ok(Stream.evalSeq(store.get).map(reading => s"${reading.id},${reading.sensor},${reading.celsius}\n"))

    case request @ GET -> Root / "sensors" / "events" =>
      val after: Long = request.headers.get[`Last-Event-Id`].flatMap(_.id.value.toLongOption).getOrElse(0L)
      val events: Stream[IO, ServerSentEvent] = Stream.eval(store.get).flatMap { all =>
        val pending: Vector[Reading] = all.filter(_.id > after)
        Stream.emits(pending).map { reading =>
          ServerSentEvent(
            data = Some(s"${reading.sensor}=${reading.celsius}"),
            eventType = Some("reading"),
            id = Some(EventId(reading.id.toString))
          )
        } ++ Stream.emit(
          ServerSentEvent(
            data = Some(s"${pending.size} readings"),
            eventType = Some("end"),
            id = Some(EventId(all.lastOption.fold(after)(_.id).toString))
          )
        )
      }
      Ok(events)
  }

  /** Starts the feed on an ephemeral loopback port and runs `scenario` against it. */
  def serveAndCall[A](scenario: (Client[IO], Uri) => IO[A]): A = {
    val running: Resource[IO, (Client[IO], Uri)] = for {
      store <- Resource.eval(Ref.of[IO, Vector[Reading]](Vector.empty))
      server <- EmberServerBuilder
        .default[IO]
        .withHost(ipv4"127.0.0.1")
        .withPort(port"0")
        .withHttpApp(feedRoutes(store).orNotFound)
        .build
      client <- EmberClientBuilder.default[IO].build
    } yield (client, Uri.unsafeFromString(s"http://127.0.0.1:${server.address.getPort}"))
    running.use((client, base) => scenario(client, base)).unsafeRunSync()
  }

  /** Sends `request` and reads the whole response body as text. */
  def call(client: Client[IO], request: Request[IO]): IO[(Status, String)] =
    client.run(request).use(response => response.as[String].map(body => (response.status, body)))

  /**
   * Subscribes to the event stream and decodes every event as `(id, type, data)`; like a browser
   * `EventSource`, it drops events without data, such as the one the decoder flushes at the end.
   */
  def subscribe(
      client: Client[IO],
      request: Request[IO]
  ): IO[(Status, Option[`Content-Type`], List[(String, String, String)])] =
    client.run(request).use { response =>
      response.body
        .through(ServerSentEvent.decoder[IO])
        .filter(_.data.isDefined)
        .map(event =>
          (
            event.id.fold("")(_.value),
            event.eventType.getOrElse("message"),
            event.data.getOrElse("")
          )
        )
        .compile
        .toList
        .map(events => (response.status, response.contentType, events))
    }
}
