/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_http4s.http4s_core_3

import cats.effect.IO
import cats.effect.unsafe.implicits.global
import fs2.Stream
import fs2.text
import org.http4s.MediaType
import org.http4s.Method
import org.http4s.Request
import org.http4s.Response
import org.http4s.ServerSentEvent
import org.http4s.ServerSentEvent.EventId
import org.http4s.Status
import org.http4s.headers.`Content-Type`
import org.http4s.headers.`Last-Event-Id`
import org.http4s.syntax.literals.*
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

import scala.concurrent.duration.*

/**
 * Streams, renders, and parses Server-Sent Events with `ServerSentEvent`, its `text/event-stream`
 * entity codecs, and the `Last-Event-Id` reconnection header.
 *
 * A deployment service pushes rollout progress to a dashboard as an event stream; the dashboard
 * reads the events, and after a dropped connection it reconnects with the last id it saw so the
 * service resumes the feed where it left off.
 */
class DeploymentProgressFeedTest {

  @Test
  def dashboardReceivesTheFullRolloutFeed(): Unit = {
    val response: Response[IO] = progressFeed(request(None))

    assertEquals(Status.Ok, response.status)
    val contentType: `Content-Type` =
      response.headers.get[`Content-Type`].getOrElse(throw new AssertionError("no Content-Type"))
    assertTrue(contentType.mediaType.satisfies(MediaType.`text/event-stream`), contentType.toString)

    val wire: String = response.body.through(text.utf8.decode).compile.string.unsafeRunSync()
    assertTrue(wire.contains("retry: 3000\n"), wire)
    assertTrue(
      wire.contains("data: canary healthy, shifting 50% of traffic\nevent: progress\nid: 2\n\n"),
      wire
    )
    assertTrue(wire.contains(": heartbeat\n"), wire)

    val received: List[ServerSentEvent] = readEvents(response)
    val progress: List[ServerSentEvent] = received.filter(_.data.isDefined)
    assertEquals(List(Some("1"), Some("2"), Some("3")), progress.map(_.id.map(_.value)))
    assertEquals(
      List(Some("started"), Some("progress"), Some("finished")),
      progress.map(_.eventType)
    )
    assertEquals(Some("canary healthy, shifting 50% of traffic"), progress(1).data)
    assertTrue(received.exists(_.retry.contains(3.seconds)), received.toString)
  }

  @Test
  def dashboardResumesFromTheLastEventIdAfterReconnecting(): Unit = {
    val firstConnection: List[ServerSentEvent] =
      readEvents(progressFeed(request(None))).filter(_.data.isDefined).take(1)
    val lastSeen: EventId = firstConnection.last.id.getOrElse(throw new AssertionError("no id"))
    assertEquals("1", lastSeen.value)

    val reconnect: Request[IO] = request(Some(lastSeen))
    assertEquals(
      Right(`Last-Event-Id`(EventId("1"))),
      `Last-Event-Id`.parse(reconnect.headers.get[`Last-Event-Id`].get.id.value)
    )

    val resumed: List[ServerSentEvent] = readEvents(progressFeed(reconnect)).filter(_.data.isDefined)
    assertEquals(List(Some("2"), Some("3")), resumed.map(_.id.map(_.value)))
    assertEquals(Some("rollout complete"), resumed.last.data)
  }

  @Test
  def dashboardParsesAThirdPartyStreamWithCrLfLinesAndComments(): Unit = {
    val wire: String = List(
      ": connected to build agent",
      "retry:10000",
      "",
      "event: log",
      "id: build-7",
      "data: compiling",
      "data:  module core",
      "x-vendor-field: ignored",
      "",
      "data: tests passed",
      "",
      ""
    ).mkString("\r\n")
    val response: Response[IO] = Response[IO](Status.Ok)
      .withEntity(wire)
      .withContentType(`Content-Type`(MediaType.`text/event-stream`))

    val events: List[ServerSentEvent] = readEvents(response).filter(_.data.isDefined)
    assertEquals(2, events.size, events.toString)
    assertEquals(Some("log"), events.head.eventType)
    assertEquals(Some(EventId("build-7")), events.head.id)
    assertEquals(Some("compiling\n module core"), events.head.data)
    assertEquals(Some("tests passed"), events(1).data)
    assertEquals(None, events(1).eventType)
  }

  @Test
  def rendersASingleEventInWireFormat(): Unit = {
    val event: ServerSentEvent = ServerSentEvent(
      data = Some("paused by operator"),
      eventType = Some("halted"),
      id = Some(EventId("9"))
    )
    val rendered: String = event.renderString
    assertTrue(rendered.contains("data: paused by operator\n"), rendered)
    assertTrue(rendered.contains("event: halted\n"), rendered)
    assertTrue(rendered.contains("id: 9\n"), rendered)
    assertTrue(rendered.endsWith("\n\n"), rendered)
  }

  /** The rollout steps the deployment service publishes, keyed by event id. */
  val rollout: List[ServerSentEvent] = List(
    ServerSentEvent(data = Some("rollout v2.4.1"), eventType = Some("started"), id = Some(EventId("1"))),
    ServerSentEvent(
      data = Some("canary healthy, shifting 50% of traffic"),
      eventType = Some("progress"),
      id = Some(EventId("2"))
    ),
    ServerSentEvent(data = Some("rollout complete"), eventType = Some("finished"), id = Some(EventId("3")))
  )

  /** Builds the dashboard's subscription request, resuming after `lastSeen` when given. */
  def request(lastSeen: Option[EventId]): Request[IO] = {
    val subscribe: Request[IO] = Request[IO](Method.GET, uri"/deployments/42/events")
    lastSeen.fold(subscribe)(id => subscribe.putHeaders(`Last-Event-Id`(id)))
  }

  /** Serves the progress feed the way the deployment service endpoint does. */
  def progressFeed(request: Request[IO]): Response[IO] = {
    val resumeAfter: Option[String] = request.headers.get[`Last-Event-Id`].map(_.id.value)
    val pending: List[ServerSentEvent] = resumeAfter match {
      case Some(id) => rollout.dropWhile(_.id.exists(_.value != id)).drop(1)
      case None => rollout
    }
    val feed: Stream[IO, ServerSentEvent] =
      Stream.emit(ServerSentEvent(retry = Some(3.seconds))) ++
        Stream.emits(pending) ++
        Stream.emit(ServerSentEvent(comment = Some("heartbeat")))
    Response[IO](Status.Ok).withEntity(feed)
  }

  /** Reads every event the dashboard receives from a `text/event-stream` response. */
  def readEvents(response: Response[IO]): List[ServerSentEvent] =
    response.body.through(ServerSentEvent.decoder[IO]).compile.toList.unsafeRunSync()
}
