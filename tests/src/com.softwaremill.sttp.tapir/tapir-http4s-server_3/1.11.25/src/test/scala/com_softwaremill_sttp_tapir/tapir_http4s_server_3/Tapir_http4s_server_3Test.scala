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
import org.http4s.ContextRequest
import org.http4s.ContextRoutes
import org.http4s.Header
import org.http4s.HttpRoutes
import org.http4s.Method
import org.http4s.Request
import org.http4s.Response
import org.http4s.implicits.uri
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.typelevel.ci.CIString
import sttp.model.sse.ServerSentEvent
import sttp.model.{StatusCode => TapirStatusCode}
import sttp.tapir._
import sttp.tapir.server.http4s._

import java.nio.charset.StandardCharsets
import scala.concurrent.duration.DurationInt

class Tapir_http4s_server_3Test {
  @Test
  def routesDecodePathAndQueryInputsAndRenderHeaders(): Unit = {
    val greetingEndpoint: PublicEndpoint[(String, Int), Unit, (String, String), Any] = endpoint.get
      .in("hello" / path[String]("name"))
      .in(query[Int]("times"))
      .out(stringBody.and(header[String]("X-Greeting")))

    val routes: HttpRoutes[IO] = Http4sServerInterpreter[IO]().toRoutes(
      greetingEndpoint.serverLogicSuccess { case (name: String, times: Int) =>
        IO.pure((List.fill(times)(s"Hello, $name").mkString(" | "), s"$name:$times"))
      }
    )

    val response: Response[IO] = responseFor(routes, Request[IO](Method.GET, uri"/hello/Ada?times=3"))

    assertEquals(200, response.status.code)
    assertEquals("Hello, Ada | Hello, Ada | Hello, Ada", bodyAsString(response))
    assertEquals(Some("Ada:3"), headerValue(response, "X-Greeting"))
  }

  @Test
  def routesRejectRequestsThatDoNotMatchMethodOrPath(): Unit = {
    val routes: HttpRoutes[IO] = Http4sServerInterpreter[IO]().toRoutes(
      endpoint.get.in("only-get").out(stringBody).serverLogicSuccess(_ => IO.pure("matched"))
    )

    val wrongMethodResponse: Option[Response[IO]] = optionalResponseFor(routes, Request[IO](Method.POST, uri"/only-get"))
    val wrongPathResponse: Option[Response[IO]] = optionalResponseFor(routes, Request[IO](Method.GET, uri"/missing"))

    assertFalse(wrongMethodResponse.isDefined)
    assertFalse(wrongPathResponse.isDefined)
  }

  @Test
  def routesReturnDecodeFailureResponseForMalformedQueryParameters(): Unit = {
    val routes: HttpRoutes[IO] = Http4sServerInterpreter[IO]().toRoutes(
      endpoint.get
        .in("double")
        .in(query[Int]("value"))
        .out(stringBody)
        .serverLogicSuccess((value: Int) => IO.pure((value * 2).toString))
    )

    val response: Response[IO] = responseFor(routes, Request[IO](Method.GET, uri"/double?value=not-an-int"))

    assertEquals(400, response.status.code)
  }

  @Test
  def routesReadRequestBodiesAndRenderErrorOutputs(): Unit = {
    val validationEndpoint: PublicEndpoint[String, (TapirStatusCode, String), String, Any] = endpoint.post
      .in("validate")
      .in(stringBody)
      .errorOut(statusCode.and(stringBody))
      .out(stringBody)
    val routes: HttpRoutes[IO] = Http4sServerInterpreter[IO]().toRoutes(
      validationEndpoint.serverLogic { (body: String) =>
        if (body.trim.nonEmpty) IO.pure(Right(s"accepted:${body.toUpperCase}"))
        else IO.pure(Left((TapirStatusCode.BadRequest, "empty body")))
      }
    )

    val accepted: Response[IO] = responseFor(routes, textRequest(Method.POST, uri"/validate", "tapir"))
    val rejected: Response[IO] = responseFor(routes, textRequest(Method.POST, uri"/validate", "   "))

    assertEquals(200, accepted.status.code)
    assertEquals("accepted:TAPIR", bodyAsString(accepted))
    assertEquals(400, rejected.status.code)
    assertEquals("empty body", bodyAsString(rejected))
  }

  @Test
  def contextRoutesExposeHttp4sRequestContextAsEndpointInput(): Unit = {
    val contextEndpoint: Endpoint[Unit, RequestContext, Unit, String, Context[RequestContext]] = endpoint.get
      .in("context")
      .contextIn[RequestContext]()
      .out(stringBody)
    val routes: ContextRoutes[RequestContext, IO] = Http4sServerInterpreter[IO]().toContextRoutes(
      contextEndpoint.serverLogicSuccess { (context: RequestContext) =>
        IO.pure(s"${context.user}:${context.traceId}")
      }
    )
    val requestContext: RequestContext = RequestContext("alice", "trace-123")
    val request: ContextRequest[IO, RequestContext] = ContextRequest(requestContext, Request[IO](Method.GET, uri"/context"))

    val response: Response[IO] = await(routes.run(request).value).getOrElse(throw new AssertionError("Expected context route to match"))

    assertEquals(200, response.status.code)
    assertEquals("alice:trace-123", bodyAsString(response))
  }

  @Test
  def contextSecurityInputParticipatesInSecurityAndServerLogic(): Unit = {
    val securedEndpoint: Endpoint[RequestContext, Unit, Unit, String, Context[RequestContext]] = endpoint.get
      .in("secure")
      .contextSecurityIn[RequestContext]()
      .out(stringBody)
    val routes: ContextRoutes[RequestContext, IO] = Http4sServerInterpreter[IO]().toContextRoutes(
      securedEndpoint
        .serverSecurityLogicSuccess((context: RequestContext) => IO.pure(context))
        .serverLogicSuccess { (authenticatedContext: RequestContext) => (_: Unit) =>
          IO.pure(s"authenticated:${authenticatedContext.user}:${authenticatedContext.traceId}")
        }
    )
    val requestContext: RequestContext = RequestContext("bob", "trace-456")
    val request: ContextRequest[IO, RequestContext] = ContextRequest(requestContext, Request[IO](Method.GET, uri"/secure"))

    val response: Response[IO] = await(routes.run(request).value).getOrElse(throw new AssertionError("Expected secured context route to match"))

    assertEquals(200, response.status.code)
    assertEquals("authenticated:bob:trace-456", bodyAsString(response))
  }

  @Test
  def bearerAuthenticationHeaderRunsSecurityLogicBeforeServerLogic(): Unit = {
    val bearerEndpoint: Endpoint[String, Unit, (TapirStatusCode, String), String, Any] = endpoint.get
      .in("bearer")
      .securityIn(auth.bearer[String]())
      .errorOut(statusCode.and(stringBody))
      .out(stringBody)
    val routes: HttpRoutes[IO] = Http4sServerInterpreter[IO]().toRoutes(
      bearerEndpoint
        .serverSecurityLogic { (token: String) =>
          if (token == "secret-token") IO.pure(Right("api-user"))
          else IO.pure(Left((TapirStatusCode.Unauthorized, "invalid bearer token")))
        }
        .serverLogicSuccess { (user: String) => (_: Unit) =>
          IO.pure(s"authenticated:$user")
        }
    )

    val accepted: Response[IO] = responseFor(
      routes,
      Request[IO](Method.GET, uri"/bearer").putHeaders(Header.Raw(CIString("Authorization"), "Bearer secret-token"))
    )
    val rejected: Response[IO] = responseFor(
      routes,
      Request[IO](Method.GET, uri"/bearer").putHeaders(Header.Raw(CIString("Authorization"), "Bearer wrong-token"))
    )

    assertEquals(200, accepted.status.code)
    assertEquals("authenticated:api-user", bodyAsString(accepted))
    assertEquals(401, rejected.status.code)
    assertEquals("invalid bearer token", bodyAsString(rejected))
  }

  @Test
  def routesDecodeCookieInputsFromHttp4sCookieHeader(): Unit = {
    val cookieEndpoint: PublicEndpoint[String, Unit, String, Any] = endpoint.get
      .in("cookie-session")
      .in(cookie[String]("session"))
      .out(stringBody)
    val routes: HttpRoutes[IO] = Http4sServerInterpreter[IO]().toRoutes(
      cookieEndpoint.serverLogicSuccess((session: String) => IO.pure(s"session:$session"))
    )

    val request: Request[IO] = Request[IO](Method.GET, uri"/cookie-session")
      .putHeaders(Header.Raw(CIString("Cookie"), "theme=dark; session=abc-123; locale=en"))
    val response: Response[IO] = responseFor(routes, request)

    assertEquals(200, response.status.code)
    assertEquals("session:abc-123", bodyAsString(response))
  }

  @Test
  def serverSentEventHelpersRoundTripMultipleEvents(): Unit = {
    val events: List[ServerSentEvent] = List(
      ServerSentEvent(Some("first payload"), Some("message"), Some("id-1"), Some(250)),
      ServerSentEvent(Some("second payload"), Some("update"), Some("id-2"), None)
    )

    val bytes: Vector[Byte] = await(
      Http4sServerSentEvents.serialiseSSEToBytes[IO](Stream.emits(events)).compile.toVector
    )
    val parsed: List[ServerSentEvent] = await(
      Http4sServerSentEvents.parseBytesToSSE[IO](Stream.emits(bytes)).compile.toList
    )

    assertEquals(events, parsed)
  }

  @Test
  def serverSentEventsBodyStreamsEventsThroughHttp4sResponse(): Unit = {
    val events: List[ServerSentEvent] = List(
      ServerSentEvent(Some("one"), Some("created"), Some("1"), None),
      ServerSentEvent(Some("two"), Some("updated"), Some("2"), Some(1000))
    )
    val routes: HttpRoutes[IO] = Http4sServerInterpreter[IO]().toRoutes(
      endpoint.get
        .in("events")
        .out(serverSentEventsBody[IO])
        .serverLogicSuccess(_ => IO.pure(Stream.emits(events)))
    )

    val response: Response[IO] = responseFor(routes, Request[IO](Method.GET, uri"/events"))
    val parsed: List[ServerSentEvent] = await(Http4sServerSentEvents.parseBytesToSSE[IO](response.body).compile.toList)

    assertEquals(200, response.status.code)
    assertTrue(headerValue(response, "Content-Type").exists(_.toLowerCase.contains("text/event-stream")))
    assertEquals(events, parsed)
  }

  private def optionalResponseFor(routes: HttpRoutes[IO], request: Request[IO]): Option[Response[IO]] =
    await(routes.run(request).value)

  private def responseFor(routes: HttpRoutes[IO], request: Request[IO]): Response[IO] =
    optionalResponseFor(routes, request).getOrElse(throw new AssertionError(s"Expected route to match $request"))

  private def textRequest(method: Method, requestUri: org.http4s.Uri, body: String): Request[IO] =
    Request[IO](method, requestUri)
      .putHeaders(Header.Raw(CIString("Content-Type"), "text/plain; charset=utf-8"))
      .withBodyStream(Stream.emits(body.getBytes(StandardCharsets.UTF_8)).covary[IO])

  private def bodyAsString(response: Response[IO]): String =
    await(response.body.through(fs2.text.utf8.decode).compile.string)

  private def headerValue(response: Response[IO], name: String): Option[String] =
    response.headers.headers
      .find((header: Header.Raw) => header.name.toString.equalsIgnoreCase(name))
      .map(_.value)

  private def await[A](io: IO[A]): A =
    io.timeout(5.seconds).unsafeRunSync()

  private final case class RequestContext(user: String, traceId: String)
}
