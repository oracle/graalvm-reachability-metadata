/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_softwaremill_sttp_tapir.tapir_zio_3

import java.nio.charset.StandardCharsets

import org.junit.jupiter.api.Assertions.{assertEquals, assertTrue}
import org.junit.jupiter.api.Test
import sttp.capabilities.zio.ZioStreams
import sttp.client4.*
import sttp.client4.httpclient.zio.HttpClientZioBackend
import sttp.model.{HeaderNames, StatusCode}
import sttp.model.sse.ServerSentEvent
import sttp.tapir.CodecFormat
import sttp.tapir.server.ziohttp.ZioHttpInterpreter
import sttp.tapir.ztapir.*
import zio.http.Server
import zio.stream.ZStream
import zio.{Chunk, Runtime, Task, UIO, Unsafe, ZIO, ZLayer}

/**
 * Server-sent events streamed from a `ZStream` with `ZioServerSentEvents`.
 *
 * A CI server publishes the log of each build as a `text/event-stream`: the endpoint output is
 * a ZIO stream body whose events are serialised with `ZioServerSentEvents.serialiseSSEToBytes`,
 * and the logic reads the build log from a `BuildLogs` service in the ZIO environment. The
 * endpoints run on the ZIO HTTP interpreter; a streaming sttp client reads the raw byte stream
 * and turns it back into events with `ZioServerSentEvents.parseBytesToSSE`. The test checks the
 * event-stream content type, that ids, event types, retry hints and multi-line data survive the
 * round trip, that a `Last-Event-ID` header resumes the feed after the given event, and that an
 * unknown build fails with a typed 404 instead of an empty stream.
 */
class BuildFeedZioServerSentEventsTest {
  import BuildFeedZioServerSentEventsTest.*

  @Test
  def buildLogsAreStreamedAsServerSentEventsAndCanBeResumed(): Unit = {
    val program: ZIO[Server & BuildLogs, Throwable, Observed] = for {
      port <- Server.install(ZioHttpInterpreter().toHttp(serverEndpoints))
      observed <- ZIO.scoped(HttpClientZioBackend.scoped().flatMap(backend => exercise(port, backend)))
    } yield observed

    val observed: Observed = Unsafe.unsafe { implicit u =>
      Runtime.default.unsafe.run(program.provide(Server.defaultWithPort(0), BuildLogs.layer)).getOrThrowFiberFailure()
    }

    assertEquals(StatusCode.Ok, observed.full.code)
    assertTrue(
      observed.full.contentType.exists(_.startsWith("text/event-stream")),
      s"expected an event stream, got ${observed.full.contentType}"
    )
    assertEquals(
      List(
        ServerSentEvent(Some("build 7 started"), Some("started"), Some("0"), Some(2000)),
        ServerSentEvent(Some("compiling 12 sources"), Some("log"), Some("1"), None),
        ServerSentEvent(Some("warning: deprecated API\n  at Main.scala:3"), Some("log"), Some("2"), None),
        ServerSentEvent(Some("running tests"), Some("log"), Some("3"), None),
        ServerSentEvent(Some("success"), Some("finished"), Some("4"), None)
      ),
      observed.full.events
    )

    assertEquals(StatusCode.Ok, observed.resumed.code)
    assertEquals(List(Some("3"), Some("4")), observed.resumed.events.map(_.id))
    assertEquals(
      List(Some("running tests"), Some("success")),
      observed.resumed.events.map(_.data)
    )

    assertEquals(StatusCode.Ok, observed.otherBuild.code)
    assertEquals(
      List(Some("started"), Some("log"), Some("finished")),
      observed.otherBuild.events.map(_.eventType)
    )
    assertEquals(Some("failure"), observed.otherBuild.events.last.data)

    assertEquals(StatusCode.NotFound, observed.unknown.code)
    assertEquals(List.empty, observed.unknown.events)
    assertEquals("no build 99", observed.unknown.error)
  }

  private def exercise(port: Int, backend: StreamBackend[Task, ZioStreams]): Task[Observed] = {
    val base: String = s"http://localhost:$port"
    def feed(request: Request[Either[String, String]]): Task[Feed] =
      request.response(asStreamUnsafe(ZioStreams)).send(backend).flatMap { r =>
        val contentType: Option[String] = r.header(HeaderNames.ContentType)
        r.body match {
          case Right(bytes) =>
            ZioServerSentEvents.parseBytesToSSE(bytes).runCollect
              .map(events => Feed(r.code, contentType, events.toList, ""))
          case Left(error) => ZIO.succeed(Feed(r.code, contentType, Nil, error))
        }
      }
    for {
      full <- feed(basicRequest.get(uri"$base/builds/7/events"))
      resumed <- feed(basicRequest.get(uri"$base/builds/7/events").header("Last-Event-ID", "2"))
      otherBuild <- feed(basicRequest.get(uri"$base/builds/8/events"))
      unknown <- feed(basicRequest.get(uri"$base/builds/99/events"))
    } yield Observed(full, resumed, otherBuild, unknown)
  }
}

object BuildFeedZioServerSentEventsTest {
  final case class Feed(code: StatusCode, contentType: Option[String], events: List[ServerSentEvent], error: String)

  final case class Observed(full: Feed, resumed: Feed, otherBuild: Feed, unknown: Feed)

  final case class BuildLog(build: Long, lines: List[String], outcome: String)

  /** Looks up recorded build logs; required by the streaming endpoint logic. */
  trait BuildLogs {
    def find(build: Long): UIO[Option[BuildLog]]
  }

  object BuildLogs {
    private val recorded: Map[Long, BuildLog] = Map(
      7L -> BuildLog(7L, List("compiling 12 sources", "warning: deprecated API\n  at Main.scala:3", "running tests"), "success"),
      8L -> BuildLog(8L, List("compiling 3 sources"), "failure")
    )

    val layer: ZLayer[Any, Nothing, BuildLogs] =
      ZLayer.succeed(new BuildLogs {
        def find(build: Long): UIO[Option[BuildLog]] = ZIO.succeed(recorded.get(build))
      })
  }

  /** A `text/event-stream` body backed by a ZIO byte stream, exposed as a stream of events. */
  private val eventsBody =
    streamTextBody(ZioStreams)(CodecFormat.TextEventStream(), Some(StandardCharsets.UTF_8))
      .map(ZioServerSentEvents.parseBytesToSSE)(ZioServerSentEvents.serialiseSSEToBytes)

  private val buildEvents =
    endpoint.get
      .in("builds" / path[Long]("build") / "events")
      .in(header[Option[Long]]("Last-Event-ID"))
      .errorOut(statusCode(StatusCode.NotFound))
      .errorOut(stringBody)
      .out(eventsBody)

  private def events(log: BuildLog): Chunk[ServerSentEvent] = {
    val started: ServerSentEvent =
      ServerSentEvent(Some(s"build ${log.build} started"), Some("started"), Some("0"), Some(2000))
    val lines: List[ServerSentEvent] = log.lines.zipWithIndex.map { case (line, index) =>
      ServerSentEvent(Some(line), Some("log"), Some((index + 1).toString), None)
    }
    val finished: ServerSentEvent =
      ServerSentEvent(Some(log.outcome), Some("finished"), Some((log.lines.size + 1).toString), None)
    Chunk.fromIterable(started :: lines ::: List(finished))
  }

  val serverEndpoints: List[ZServerEndpoint[BuildLogs, ZioStreams]] = List(
    buildEvents.zServerLogic[BuildLogs] { case (build, lastEventId) =>
      ZIO.serviceWithZIO[BuildLogs](_.find(build)).someOrFail(s"no build $build").map { log =>
        val after: Long = lastEventId.getOrElse(-1L)
        ZStream.fromChunk(events(log)).filter(_.id.exists(_.toLong > after))
      }
    }
  )
}
