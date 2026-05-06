/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_softwaremill_sttp_tapir.tapir_pekko_http_server_3

import org.apache.pekko.actor.ActorSystem
import org.apache.pekko.http.scaladsl.Http
import org.apache.pekko.http.scaladsl.model.headers.RawHeader
import org.apache.pekko.http.scaladsl.model.{ContentTypes, HttpEntity, HttpMethods, HttpRequest, HttpResponse, StatusCodes, Uri}
import org.apache.pekko.http.scaladsl.server.Route
import org.apache.pekko.http.scaladsl.unmarshalling.Unmarshal
import org.apache.pekko.stream.scaladsl.{Sink, Source}
import org.apache.pekko.stream.{Materializer, SystemMaterializer}
import org.apache.pekko.util.ByteString
import org.junit.jupiter.api.Assertions.{assertEquals, assertTrue}
import org.junit.jupiter.api.Test
import sttp.capabilities.pekko.PekkoStreams
import sttp.model.StatusCode
import sttp.model.sse.ServerSentEvent
import sttp.tapir.*
import sttp.tapir.server.pekkohttp.{PekkoHttpServerInterpreter, PekkoServerSentEvents}

import scala.concurrent.duration.*
import scala.concurrent.{Await, ExecutionContext, Future}

class Tapir_pekko_http_server_3Test {
  private val RequestTimeout: FiniteDuration = 10.seconds

  @Test
  def routeDecodesPathAndQueryParametersAndReportsInputErrors(): Unit = {
    val greetingEndpoint = endpoint.get
      .in("hello" / path[String]("name"))
      .in(query[Int]("times"))
      .errorOut(statusCode)
      .out(stringBody)
      .serverLogic { case (name, times) =>
        val response: Either[StatusCode, String] =
          if times > 0 && times <= 3 then Right(List.fill(times)(s"Hello, $name").mkString(" | "))
          else Left(StatusCode.BadRequest)
        Future.successful(response)
      }

    withServer { system =>
      given ExecutionContext = system.dispatcher
      PekkoHttpServerInterpreter().toRoute(greetingEndpoint)
    } { (baseUri, system) =>
      given ActorSystem = system

      val okResponse = singleRequest(HttpRequest(uri = uri(baseUri, "/hello/Ada?times=2")))
      assertEquals(StatusCodes.OK, okResponse.status)
      assertEquals("Hello, Ada | Hello, Ada", bodyAsString(okResponse))

      val rejectedByLogic = singleRequest(HttpRequest(uri = uri(baseUri, "/hello/Ada?times=0")))
      assertEquals(StatusCodes.BadRequest, rejectedByLogic.status)
      bodyAsString(rejectedByLogic)

      val rejectedByDecoder = singleRequest(HttpRequest(uri = uri(baseUri, "/hello/Ada?times=not-a-number")))
      assertEquals(StatusCodes.BadRequest, rejectedByDecoder.status)
      bodyAsString(rejectedByDecoder)
    }
  }

  @Test
  def postRouteReadsHeadersAndBodyAndWritesHeadersAndBody(): Unit = {
    val echoEndpoint = endpoint.post
      .in("echo")
      .in(header[String]("X-Trace-Id"))
      .in(stringBody)
      .out(header[String]("X-Trace-Id"))
      .out(stringBody)
      .serverLogicSuccess { case (traceId, body) =>
        Future.successful((traceId, body.reverse))
      }

    withServer { system =>
      given ExecutionContext = system.dispatcher
      PekkoHttpServerInterpreter().toRoute(echoEndpoint)
    } { (baseUri, system) =>
      given ActorSystem = system

      val request = HttpRequest(
        method = HttpMethods.POST,
        uri = uri(baseUri, "/echo"),
        entity = HttpEntity(ContentTypes.`text/plain(UTF-8)`, "Tapir on Pekko")
      ).withHeaders(RawHeader("X-Trace-Id", "trace-123"))
      val response = singleRequest(request)

      assertEquals(StatusCodes.OK, response.status)
      assertEquals(Some("trace-123"), headerValue(response, "X-Trace-Id"))
      assertEquals("okkeP no ripaT", bodyAsString(response))

      val missingHeaderResponse = singleRequest(
        HttpRequest(
          method = HttpMethods.POST,
          uri = uri(baseUri, "/echo"),
          entity = HttpEntity(ContentTypes.`text/plain(UTF-8)`, "body")
        )
      )
      assertEquals(StatusCodes.BadRequest, missingHeaderResponse.status)
      bodyAsString(missingHeaderResponse)
    }
  }

  @Test
  def pekkoStreamBodyProcessesRequestAndResponseChunks(): Unit = {
    val streamingEndpoint = endpoint.post
      .in("stream-uppercase")
      .in(streamTextBody(PekkoStreams)(CodecFormat.TextPlain()))
      .out(streamTextBody(PekkoStreams)(CodecFormat.TextPlain()))
      .serverLogicSuccess { source =>
        Future.successful(source.map(bytes => ByteString(bytes.utf8String.toUpperCase)))
      }

    withServer { system =>
      given ExecutionContext = system.dispatcher
      PekkoHttpServerInterpreter().toRoute(streamingEndpoint)
    } { (baseUri, system) =>
      given ActorSystem = system

      val requestBody = Source(List(ByteString("tapir\n"), ByteString("pekko\n")))
      val request = HttpRequest(
        method = HttpMethods.POST,
        uri = uri(baseUri, "/stream-uppercase"),
        entity = HttpEntity(ContentTypes.`text/plain(UTF-8)`, requestBody)
      )
      val response = singleRequest(request)

      assertEquals(StatusCodes.OK, response.status)
      assertEquals("TAPIR\nPEKKO\n", bodyAsString(response))
    }
  }

  @Test
  def bearerSecurityLogicControlsAccessBeforeEndpointLogicRuns(): Unit = {
    val securedEndpoint = endpoint.get
      .securityIn(auth.bearer[String]())
      .errorOut(statusCode)
      .in("secure")
      .out(stringBody)
      .serverSecurityLogic { token =>
        val result: Either[StatusCode, String] =
          if token == "secret-token" then Right("ada") else Left(StatusCode.Forbidden)
        Future.successful(result)
      }
      .serverLogic { principal => _ =>
        Future.successful(Right(s"accepted:$principal"))
      }

    withServer { system =>
      given ExecutionContext = system.dispatcher
      PekkoHttpServerInterpreter().toRoute(securedEndpoint)
    } { (baseUri, system) =>
      given ActorSystem = system

      val accepted = singleRequest(
        HttpRequest(uri = uri(baseUri, "/secure")).withHeaders(RawHeader("Authorization", "Bearer secret-token"))
      )
      assertEquals(StatusCodes.OK, accepted.status)
      assertEquals("accepted:ada", bodyAsString(accepted))

      val forbidden = singleRequest(
        HttpRequest(uri = uri(baseUri, "/secure")).withHeaders(RawHeader("Authorization", "Bearer wrong-token"))
      )
      assertEquals(StatusCodes.Forbidden, forbidden.status)
      bodyAsString(forbidden)

      val missingCredentials = singleRequest(HttpRequest(uri = uri(baseUri, "/secure")))
      assertEquals(StatusCodes.Unauthorized, missingCredentials.status)
      bodyAsString(missingCredentials)
    }
  }

  @Test
  def interpreterCombinesMultipleEndpointsInOneRoute(): Unit = {
    val pingEndpoint = endpoint.get
      .in("ping")
      .out(stringBody)
      .serverLogicSuccess(_ => Future.successful("pong"))
    val addEndpoint = endpoint.get
      .in("add")
      .in(query[Int]("a"))
      .in(query[Int]("b"))
      .out(plainBody[Int])
      .serverLogicSuccess { case (a, b) => Future.successful(a + b) }

    withServer { system =>
      given ExecutionContext = system.dispatcher
      PekkoHttpServerInterpreter().toRoute(List(pingEndpoint, addEndpoint))
    } { (baseUri, system) =>
      given ActorSystem = system

      val pingResponse = singleRequest(HttpRequest(uri = uri(baseUri, "/ping")))
      assertEquals(StatusCodes.OK, pingResponse.status)
      assertEquals("pong", bodyAsString(pingResponse))

      val addResponse = singleRequest(HttpRequest(uri = uri(baseUri, "/add?a=20&b=22")))
      assertEquals(StatusCodes.OK, addResponse.status)
      assertEquals("42", bodyAsString(addResponse))

      val missingRoute = singleRequest(HttpRequest(uri = uri(baseUri, "/does-not-exist")))
      assertEquals(StatusCodes.NotFound, missingRoute.status)
      bodyAsString(missingRoute)
    }
  }

  @Test
  def pekkoServerSentEventsRoundTripBetweenEventsAndBytes(): Unit = {
    val events: List[ServerSentEvent] = List(
      ServerSentEvent(
        data = Some("first line\nsecond line"),
        eventType = Some("joined"),
        id = Some("event-1"),
        retry = Some(250)
      ),
      ServerSentEvent(data = Some("tail"), eventType = Some("single"))
    )

    withActorSystem { system =>
      given ActorSystem = system
      given Materializer = SystemMaterializer(system).materializer

      val bytes = Await.result(
        PekkoServerSentEvents
          .serialiseSSEToBytes(Source(events))
          .runWith(Sink.fold(ByteString.empty)(_ ++ _)),
        RequestTimeout
      )
      val text = bytes.utf8String

      assertTrue(text.contains("data: first line\ndata: second line"))
      assertTrue(text.contains("event: joined"))
      assertTrue(text.contains("id: event-1"))
      assertTrue(text.contains("retry: 250"))

      val parsed = Await.result(
        PekkoServerSentEvents
          .parseBytesToSSE(Source.single(bytes))
          .runWith(Sink.seq),
        RequestTimeout
      ).toList

      assertEquals(events, parsed)
    }
  }

  private def withServer(routeFactory: ActorSystem => Route)(test: (Uri, ActorSystem) => Unit): Unit = {
    withActorSystem { system =>
      given ActorSystem = system
      given ExecutionContext = system.dispatcher
      given Materializer = SystemMaterializer(system).materializer
      val route = routeFactory(system)
      val binding = Await.result(Http().newServerAt("127.0.0.1", 0).bind(route), RequestTimeout)
      try {
        val address = binding.localAddress
        val baseUri = Uri(s"http://${address.getHostString}:${address.getPort}")
        test(baseUri, system)
      } finally {
        Await.result(binding.terminate(3.seconds), RequestTimeout)
      }
    }
  }

  private def withActorSystem(test: ActorSystem => Unit): Unit = {
    val system = ActorSystem("tapir-pekko-http-server-test")
    try test(system)
    finally Await.result(system.terminate(), RequestTimeout)
  }

  private def singleRequest(request: HttpRequest)(using ActorSystem): HttpResponse = {
    Await.result(Http().singleRequest(request), RequestTimeout)
  }

  private def bodyAsString(response: HttpResponse)(using system: ActorSystem): String = {
    given ExecutionContext = system.dispatcher
    given Materializer = SystemMaterializer(system).materializer
    Await.result(Unmarshal(response.entity).to[String], RequestTimeout)
  }

  private def headerValue(response: HttpResponse, name: String): Option[String] = {
    response.headers.find(_.name().equalsIgnoreCase(name)).map(_.value())
  }

  private def uri(baseUri: Uri, pathAndQuery: String): Uri = Uri(s"$baseUri$pathAndQuery")
}
