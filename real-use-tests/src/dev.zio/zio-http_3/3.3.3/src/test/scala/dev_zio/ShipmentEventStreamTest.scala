/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package dev_zio

import java.time.Duration

import org.junit.jupiter.api.Assertions.{assertEquals, assertTrue}
import org.junit.jupiter.api.{Test, Timeout}
import zio.{Chunk, Runtime, Unsafe, ZIO}
import zio.http.*
import zio.stream.ZStream

/** Exercises ZIO HTTP server-sent events for a resumable shipment-tracking feed. */
class ShipmentEventStreamTest {
  private val updates: Chunk[ServerSentEvent[String]] = Chunk(
    ServerSentEvent(
      "Parcel PKG-1042 accepted",
      eventType = Some("shipment-status"),
      id = Some("shipment-1"),
      retry = Some(Duration.ofSeconds(10))
    ),
    ServerSentEvent(
      "Parcel PKG-1042 in transit\nNext stop: regional depot",
      eventType = Some("shipment-status"),
      id = Some("shipment-2")
    ),
    ServerSentEvent(
      "Parcel PKG-1042 delivered",
      eventType = Some("shipment-status"),
      id = Some("shipment-3")
    )
  )

  @Test
  @Timeout(60)
  def resumesTrackingWithoutReplayingAlreadyDeliveredUpdates(): Unit = {
    val program: ZIO[Any, Throwable, Unit] = for {
      response <- ZIO.succeed(trackingFeed(Request.get("/shipments/PKG-1042/events")))
      _ <- ZIO.succeed {
        assertEquals(Status.Ok, response.status)
        assertEquals(Some("text/event-stream"), response.rawHeader("Content-Type"))
      }
      // The dashboard disconnects after displaying the first two updates.
      displayed <- readEvents(response).take(2).runCollect
      _ <- ZIO.succeed {
        assertEquals(updates.take(2), displayed)
        assertEquals(
          "Parcel PKG-1042 in transit\nNext stop: regional depot",
          displayed.last.data
        )
      }
      lastId <- ZIO.fromOption(displayed.last.id)
        .orElseFail(new IllegalStateException("Tracking updates must have event IDs"))
      reconnect = Request.get("/shipments/PKG-1042/events").addHeader("Last-Event-ID", lastId)
      remaining <- readEvents(trackingFeed(reconnect)).runCollect
      _ <- ZIO.succeed {
        assertEquals(Chunk(updates.last), remaining)
        assertEquals(updates, displayed ++ remaining)
        assertEquals("Parcel PKG-1042 delivered", remaining.last.data)
      }
      caughtUp <- readEvents(
        trackingFeed(reconnect.removeHeader("Last-Event-ID").addHeader("Last-Event-ID", "shipment-3"))
      ).runCollect
      _ <- ZIO.succeed(assertTrue(caughtUp.isEmpty))
    } yield ()

    Unsafe.unsafe { implicit unsafe =>
      Runtime.default.unsafe.run(program).getOrThrowFiberFailure()
    }
  }

  private def trackingFeed(request: Request): Response = {
    val pending: Chunk[ServerSentEvent[String]] = request.rawHeader("Last-Event-ID") match {
      case Some(lastId) => updates.dropWhile(_.id != Some(lastId)).drop(1)
      case None => updates
    }
    Response.fromServerSentEvents(ZStream.fromChunk(pending))
  }

  private def readEvents(response: Response): ZStream[Any, Throwable, ServerSentEvent[String]] =
    // Event boundaries need not align with the byte chunks a consumer receives.
    Body.fromStreamChunked(response.body.asStream.rechunk(3)).asServerSentEvents[String]
}
