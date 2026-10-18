/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_softwaremill_sttp_tapir.tapir_zio_3

import java.util.concurrent.ConcurrentLinkedQueue

import scala.jdk.CollectionConverters.*

import org.junit.jupiter.api.Assertions.{assertEquals, assertTrue}
import org.junit.jupiter.api.Test
import sttp.client4.*
import sttp.client4.httpclient.zio.HttpClientZioBackend
import sttp.model.StatusCode
import sttp.tapir.{Schema, Validator}
import sttp.tapir.json.zio.*
import sttp.tapir.server.interceptor.exception.ExceptionHandler
import sttp.tapir.server.model.ValuedEndpointOutput
import sttp.tapir.server.ziohttp.{ZioHttpInterpreter, ZioHttpServerOptions}
import sttp.tapir.ztapir.*
import zio.http.Server
import zio.json.{DecoderOps, DeriveJsonCodec, JsonCodec}
import zio.{durationInt, Cause, FiberId, FiberRefs, LogLevel, LogSpan, RIO, Ref, Runtime, Task, Trace, UIO, Unsafe}
import zio.{ZIO, ZLayer, ZLogger}

/**
 * Request logging and error handling through the interceptors that `ZioHttpServerOptions` installs.
 *
 * A parcel desk configures `ZioHttpServerOptions.defaultServerLog` to also log received requests
 * and every decode failure, leaves its health probe out of the log, replaces the default
 * exception handler with one that answers a JSON problem, and renders decode failures and
 * rejected requests through `defaultHandlers`. A `ZLogger` installed with `ZIO.withLogger`
 * records what the server writes into ZIO logging. The test drives a registration, a lookup, a
 * lookup with an invalid query, a label print whose printer throws, a method the desk does not
 * offer and the health probe, then checks both the responses a client sees and the log entries
 * an operator would read.
 */
class ParcelDeskZioServerLogTest {
  import ParcelDeskZioServerLogTest.*

  @Test
  def serverLogAndErrorHandlersShapeResponsesAndZioLogEntries(): Unit = {
    val logBook: LogBook = new LogBook

    val program: ZIO[Server & Parcels, Throwable, Observed] = for {
      port <- Server.install(ZioHttpInterpreter(serverOptions).toHttp(serverEndpoints))
      replies <- ZIO.scoped(HttpClientZioBackend.scoped().flatMap(backend => exercise(port, backend)))
      entries <- poll(ZIO.succeed(logBook.serverEntries)) { es =>
        es.count(e => e.message.startsWith("Request: ") && e.message.contains("response: ")) >= 5
      }
    } yield Observed(replies, entries)

    val observed: Observed = Unsafe.unsafe { implicit u =>
      Runtime.default.unsafe.run(
        // The server captures its runtime while its layer is built, so the logger wraps the build.
        ZIO.withLogger(logBook) {
          program.provide(Server.defaultWithPort(0), InMemoryParcels.layer)
        }
      ).getOrThrowFiberFailure()
    }
    val replies: Replies = observed.replies

    assertEquals(StatusCode.Created, replies.registered.code)
    assertEquals(Parcel("P-1", "Lisbon", List("registered")), decode[Parcel](replies.registered.body))
    assertEquals(StatusCode.Ok, replies.tracked.code)
    assertEquals(List("registered"), decode[Parcel](replies.tracked.body).history)

    assertEquals(StatusCode.BadRequest, replies.invalidLimit.code)
    val invalid: Problem = decode[Problem](replies.invalidLimit.body)
    assertEquals("invalid-request", invalid.kind)
    assertTrue(invalid.detail.contains("limit"), invalid.detail)

    assertEquals(StatusCode.ServiceUnavailable, replies.label.code)
    assertEquals(Problem("unavailable", "print-label: label printer offline"), decode[Problem](replies.label.body))

    assertEquals(StatusCode.MethodNotAllowed, replies.wrongMethod.code)
    assertEquals(Problem("invalid-request", "Method Not Allowed"), decode[Problem](replies.wrongMethod.body))
    assertEquals(Reply(StatusCode.Ok, "ok"), replies.health)

    val entries: List[Entry] = observed.entries

    // Requests are logged on arrival, before routing, so the ignored health probe is received too.
    val received: List[Entry] = entries.filter(_.message.startsWith("Request received: "))
    assertTrue(received.forall(_.level == LogLevel.Debug), received.mkString("\n"))
    assertEquals(
      List("POST /parcels", "GET /parcels/P-1?limit=5", "GET /parcels/P-1?limit=0", "GET /parcels/P-1/label",
        "DELETE /parcels/P-1", "GET /health"),
      received.map(_.message.stripPrefix("Request received: "))
    )

    val handled: List[Entry] = entries.filter(_.message.startsWith("Request: "))
    assertTrue(handled.forall(_.level == LogLevel.Debug), handled.mkString("\n"))
    assertTrue(handled.forall(!_.message.contains("/health")), handled.mkString("\n"))
    val registration: Entry = single(handled.filter(_.message.contains("POST /parcels,")))
    assertTrue(registration.message.contains("handled by: [register-parcel]"), registration.message)
    assertTrue(registration.message.contains("response: 201"), registration.message)
    val decodeFailure: Entry = single(handled.filter(_.message.contains("limit=0")))
    assertTrue(decodeFailure.message.contains("decode failure"), decodeFailure.message)
    assertTrue(decodeFailure.message.contains("?limit"), decodeFailure.message)
    assertTrue(decodeFailure.message.contains("response: 400"), decodeFailure.message)
    val label: Entry = single(handled.filter(_.message.contains("/label")))
    assertTrue(label.message.contains("handled by: [print-label]"), label.message)
    assertTrue(label.message.contains("response: 503"), label.message)
    val mismatch: Entry = single(handled.filter(_.message.contains("not handled by: [track-parcel]")))
    assertTrue(mismatch.message.startsWith("Request: DELETE /parcels/P-1"), mismatch.message)
    val rejected: Entry = single(handled.filter(_.message.contains("handled by interceptor")))
    assertTrue(rejected.message.contains("response: 405"), rejected.message)

    val failure: Entry = single(entries.filter(_.message.startsWith("Exception when handling request")))
    assertEquals(LogLevel.Error, failure.level)
    assertTrue(failure.message.contains("[print-label]"), failure.message)
    assertEquals(Some("label printer offline"), failure.failure.map(_.getMessage))

    val printing: Entry = single(entries.filter(_.message == "printing label"))
    assertEquals(LogLevel.Info, printing.level)
    assertEquals(Map("parcel" -> "P-1"), printing.annotations)
  }

  private def exercise(port: Int, backend: Backend[Task]): Task[Replies] = {
    val base: String = s"http://localhost:$port"
    def send(request: Request[Either[String, String]]): Task[Reply] =
      request.send(backend).map(r => Reply(r.code, r.body.fold(identity, identity)))
    for {
      registered <- send(
        basicRequest.post(uri"$base/parcels").contentType("application/json").body("""{"destination":"Lisbon"}""")
      )
      tracked <- send(basicRequest.get(uri"$base/parcels/P-1?limit=5"))
      invalidLimit <- send(basicRequest.get(uri"$base/parcels/P-1?limit=0"))
      label <- send(basicRequest.get(uri"$base/parcels/P-1/label"))
      wrongMethod <- send(basicRequest.delete(uri"$base/parcels/P-1"))
      health <- send(basicRequest.get(uri"$base/health"))
    } yield Replies(registered, tracked, invalidLimit, label, wrongMethod, health)
  }

  /** Re-reads the log until every handled entry has been written. */
  private def poll[A](read: UIO[A])(done: A => Boolean): UIO[A] = {
    def attempt(remaining: Int): UIO[A] =
      read.flatMap(a => if (done(a) || remaining == 0) ZIO.succeed(a) else attempt(remaining - 1).delay(10.millis))
    attempt(1000)
  }

  private def single(entries: List[Entry]): Entry = {
    assertEquals(1, entries.size, entries.mkString("\n"))
    entries.head
  }

  private def decode[A: JsonCodec](body: String): A =
    body.fromJson[A].fold(error => throw new AssertionError(s"$error in $body"), identity)
}

object ParcelDeskZioServerLogTest {
  final case class NewParcel(destination: String) derives Schema
  final case class Parcel(id: String, destination: String, history: List[String]) derives Schema
  final case class Problem(kind: String, detail: String) derives Schema

  given JsonCodec[NewParcel] = DeriveJsonCodec.gen[NewParcel]
  given JsonCodec[Parcel] = DeriveJsonCodec.gen[Parcel]
  given JsonCodec[Problem] = DeriveJsonCodec.gen[Problem]

  final case class Reply(code: StatusCode, body: String)

  final case class Replies(
      registered: Reply,
      tracked: Reply,
      invalidLimit: Reply,
      label: Reply,
      wrongMethod: Reply,
      health: Reply
  )

  final case class Observed(replies: Replies, entries: List[Entry])

  /** One record written to ZIO logging, with the throwable of its cause when it has one. */
  final case class Entry(level: LogLevel, message: String, failure: Option[Throwable], annotations: Map[String, String])

  /** A `ZLogger` that keeps every entry the server and the endpoint logic write. */
  final class LogBook extends ZLogger[String, Unit] {
    private val entries: ConcurrentLinkedQueue[Entry] = new ConcurrentLinkedQueue[Entry]

    override def apply(
        trace: Trace,
        fiberId: FiberId,
        logLevel: LogLevel,
        message: () => String,
        cause: Cause[Any],
        context: FiberRefs,
        spans: List[LogSpan],
        annotations: Map[String, String]
    ): Unit = {
      val failure: Option[Throwable] = cause.failureOption.orElse(cause.dieOption).collect { case t: Throwable => t }
      entries.add(Entry(logLevel, message(), failure, annotations))
      ()
    }

    /** Entries written by the server log and the endpoint logic, leaving out the HTTP server's own. */
    def serverEntries: List[Entry] =
      entries.asScala.toList.filter { e =>
        e.message.startsWith("Request") || e.message.startsWith("Exception") || e.annotations.contains("parcel")
      }
  }

  /** The parcel registry the endpoint logic reads and writes. */
  trait Parcels {
    def register(destination: String): UIO[Parcel]
    def find(id: String): UIO[Option[Parcel]]
  }

  final class InMemoryParcels(store: Ref[Map[String, Parcel]]) extends Parcels {
    def register(destination: String): UIO[Parcel] =
      store.modify { parcels =>
        val parcel: Parcel = Parcel(s"P-${parcels.size + 1}", destination, List("registered"))
        (parcel, parcels.updated(parcel.id, parcel))
      }
    def find(id: String): UIO[Option[Parcel]] = store.get.map(_.get(id))
  }

  object InMemoryParcels {
    val layer: ZLayer[Any, Nothing, Parcels] =
      ZLayer.fromZIO(Ref.make(Map.empty[String, Parcel]).map(new InMemoryParcels(_)))
  }

  type AppTask[A] = RIO[Parcels, A]

  private val registerParcel =
    endpoint.post.in("parcels").in(jsonBody[NewParcel]).out(statusCode(StatusCode.Created)).out(jsonBody[Parcel])
      .name("register-parcel")

  private val trackParcel =
    endpoint.get.in("parcels" / path[String]("id")).in(query[Int]("limit").validate(Validator.min(1)))
      .errorOut(statusCode(StatusCode.NotFound).and(jsonBody[Problem])).out(jsonBody[Parcel]).name("track-parcel")

  private val printLabel =
    endpoint.get.in("parcels" / path[String]("id") / "label")
      .errorOut(statusCode(StatusCode.NotFound).and(jsonBody[Problem])).out(stringBody).name("print-label")

  private val health = endpoint.get.in("health").out(stringBody).name("health")

  private def existing(id: String): ZIO[Parcels, Problem, Parcel] =
    ZIO.serviceWithZIO[Parcels](_.find(id)).someOrFail(Problem("not-found", s"no parcel $id"))

  /** Lets label printing fail with a `Throwable`, which the exception handler turns into a reply. */
  private given RIOMonadError[Parcels] = new RIOMonadError[Parcels]

  /** The label printer is offline, so rendering throws. */
  private def renderLabel(parcel: Parcel): String =
    throw new IllegalStateException("label printer offline")

  val serverEndpoints: List[ZServerEndpoint[Parcels, Any]] = List(
    registerParcel.zServerLogic[Parcels](p => ZIO.serviceWithZIO[Parcels](_.register(p.destination))),
    trackParcel.zServerLogic[Parcels] { (id, limit) => existing(id).map(p => p.copy(history = p.history.take(limit))) },
    printLabel.serverLogic[AppTask] { id =>
      ZIO.logAnnotate("parcel", id) {
        existing(id).either.flatMap {
          case Left(problem) => ZIO.succeed(Left(problem))
          case Right(parcel) => ZIO.logInfo("printing label") *> ZIO.attempt(renderLabel(parcel)).map(Right(_))
        }
      }
    },
    health.zServerLogic[Parcels](_ => ZIO.succeed("ok"))
  )

  private val serverLog =
    ZioHttpServerOptions.defaultServerLog[Parcels].logWhenReceived(true).logAllDecodeFailures(true)
      .ignoreEndpoints(Seq(health))

  private val exceptionHandler: ExceptionHandler[AppTask] =
    ExceptionHandler.pure[AppTask] { ctx =>
      val problem: Problem = Problem("unavailable", s"${ctx.endpoint.info.name.getOrElse("?")}: ${ctx.e.getMessage}")
      Some(ValuedEndpointOutput(statusCode.and(jsonBody[Problem]), (StatusCode.ServiceUnavailable, problem)))
    }

  val serverOptions: ZioHttpServerOptions[Parcels] =
    ZioHttpServerOptions.customiseInterceptors[Parcels]
      .serverLog(serverLog)
      .defaultHandlers(message => ValuedEndpointOutput(jsonBody[Problem], Problem("invalid-request", message)))
      .exceptionHandler(exceptionHandler)
      .options
}
