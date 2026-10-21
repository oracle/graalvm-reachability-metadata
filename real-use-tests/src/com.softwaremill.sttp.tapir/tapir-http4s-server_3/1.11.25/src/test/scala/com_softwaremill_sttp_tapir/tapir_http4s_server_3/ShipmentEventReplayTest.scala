/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_softwaremill_sttp_tapir.tapir_http4s_server_3

import cats.effect.IO
import cats.effect.unsafe.implicits.global
import fs2.Stream
import org.http4s.{Header, Method, Request}
import org.http4s.implicits.*
import org.junit.jupiter.api.Assertions.{assertEquals, assertTrue}
import org.junit.jupiter.api.{Test, Timeout}
import org.typelevel.ci.CIString
import sttp.capabilities.fs2.Fs2Streams
import sttp.model.sse.ServerSentEvent
import sttp.tapir.*
import sttp.tapir.server.http4s.{Http4sServerInterpreter, serverSentEventsBody}

import scala.concurrent.duration.*

/** Exercises server-sent event encoding and resumable shipment feeds in Tapir's http4s interpreter. */
class ShipmentEventReplayTest {
  final case class ShipmentUpdate(sequence: Long, description: String)

  @Test
  @Timeout(60)
  def streamsShipmentUpdatesAndReplaysOnlyEventsAfterTheBrowserCursor(): Unit = {
    val updates: Vector[ShipmentUpdate] = Vector(
      ShipmentUpdate(101L, "Packed"),
      ShipmentUpdate(102L, "Arrived in Montr\u00e9al\nAwaiting customs"),
      ShipmentUpdate(103L, "Delivered")
    )
    val feed: PublicEndpoint[Option[Long], Unit, Stream[IO, ServerSentEvent], Fs2Streams[IO]] = endpoint.get
      .in("shipments" / "parcel-42" / "events")
      .in(header[Option[Long]]("Last-Event-ID"))
      .out(serverSentEventsBody[IO])
    val app = Http4sServerInterpreter[IO]().toRoutes(
      feed.serverLogicSuccess[IO] { (cursor: Option[Long]) =>
        val events: Stream[IO, ServerSentEvent] = Stream.emits(updates).covary[IO]
          .filter(update => cursor.forall(_ < update.sequence))
          .map { update =>
            ServerSentEvent(
              data = Some(update.description),
              eventType = Some("shipment-status"),
              id = Some(update.sequence.toString),
              retry = Some(10000)
            )
          }
        IO.pure(events)
      }
    ).orNotFound
    val packed: String = "data: Packed\nevent: shipment-status\nid: 101\nretry: 10000\n\n"
    val customs: String = "data: Arrived in Montr\u00e9al\ndata: Awaiting customs\n" +
      "event: shipment-status\nid: 102\nretry: 10000\n\n"
    val delivered: String = "data: Delivered\nevent: shipment-status\nid: 103\nretry: 10000\n\n"

    val scenario: IO[Unit] = for {
      initial <- app.run(feedRequest(None))
      initialBody <- initial.as[String]
      _ <- IO {
        assertEquals(200, initial.status.code)
        assertTrue(initial.headers.headers.exists { header =>
          header.name == CIString("Content-Type") && header.value.startsWith("text/event-stream")
        })
        assertEquals(packed + customs + delivered, initialBody)
      }
      resumed <- app.run(feedRequest(Some(102L)))
      resumedBody <- resumed.as[String]
      _ <- IO {
        assertEquals(200, resumed.status.code)
        assertEquals(delivered, resumedBody)
      }
      caughtUp <- app.run(feedRequest(Some(103L)))
      caughtUpBody <- caughtUp.as[String]
      _ <- IO {
        assertEquals(200, caughtUp.status.code)
        assertEquals("", caughtUpBody)
      }
    } yield ()

    scenario.timeout(45.seconds).unsafeRunSync()
  }

  private def feedRequest(cursor: Option[Long]): Request[IO] = {
    val request: Request[IO] = Request[IO](Method.GET, uri"/shipments/parcel-42/events")
      .putHeaders(Header.Raw(CIString("Accept"), "text/event-stream"))
    cursor.fold(request) { sequence =>
      request.putHeaders(Header.Raw(CIString("Last-Event-ID"), sequence.toString))
    }
  }
}
