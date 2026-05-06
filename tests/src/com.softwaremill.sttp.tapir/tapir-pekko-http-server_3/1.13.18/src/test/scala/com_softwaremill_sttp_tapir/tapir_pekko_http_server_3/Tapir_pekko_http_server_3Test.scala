/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_softwaremill_sttp_tapir.tapir_pekko_http_server_3

import org.apache.pekko.actor.ActorSystem
import org.apache.pekko.http.scaladsl.Http
import org.apache.pekko.http.scaladsl.model.HttpEntity
import org.apache.pekko.http.scaladsl.server.{RequestContext, Route}
import org.apache.pekko.stream.scaladsl.Source
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import sttp.model.StatusCode
import sttp.model.sse.ServerSentEvent
import sttp.tapir._
import sttp.tapir.server.interceptor.RequestInterceptor
import sttp.tapir.server.pekkohttp.{PekkoHttpServerInterpreter, PekkoHttpServerOptions, serverSentEventsBody}

import java.net.URI
import java.net.http.{HttpClient, HttpRequest as JHttpRequest, HttpResponse as JHttpResponse}
import java.nio.charset.StandardCharsets
import java.time.Duration
import scala.concurrent.duration.{DurationInt, FiniteDuration}
import scala.concurrent.{Await, ExecutionContext, Future}

class Tapir_pekko_http_server_3Test {
  private val RequestTimeout: Duration = Duration.ofSeconds(10)
  private val AwaitTimeout: FiniteDuration = 10.seconds
  private val client: HttpClient = HttpClient.newBuilder().connectTimeout(RequestTimeout).build()

  @Test
  def routesTypedPathQueryHeadersAndErrorOutputs(): Unit = {
    withServer {
      val greetingEndpoint = endpoint.get
        .in("greet" / path[String]("name"))
        .in(query[Int]("times"))
        .in(header[String]("X-Trace-Id"))
        .out(statusCode)
        .out(header[String]("X-Reply-Trace"))
        .out(stringBody)
        .errorOut(statusCode)
        .errorOut(stringBody)
        .serverLogic { case (name, times, traceId) =>
          if (times <= 0) {
            Future.successful(Left((StatusCode.BadRequest, s"invalid repetition count: $times")))
          } else {
            val responseBody = List.fill(times)(s"hello $name").mkString("|")
            Future.successful(Right((StatusCode.Ok, traceId, responseBody)))
          }
        }

      PekkoHttpServerInterpreter().toRoute(greetingEndpoint)
    } { baseUri =>
      val okResponse = sendText(
        JHttpRequest
          .newBuilder(baseUri.resolve("/greet/Ada?times=2"))
          .timeout(RequestTimeout)
          .header("X-Trace-Id", "trace-123")
          .GET()
          .build()
      )

      assertThat(okResponse.statusCode()).isEqualTo(200)
      assertThat(okResponse.headers().firstValue("X-Reply-Trace")).hasValue("trace-123")
      assertThat(okResponse.body()).isEqualTo("hello Ada|hello Ada")

      val badRequestResponse = sendText(
        JHttpRequest
          .newBuilder(baseUri.resolve("/greet/Ada?times=0"))
          .timeout(RequestTimeout)
          .header("X-Trace-Id", "trace-456")
          .GET()
          .build()
      )

      assertThat(badRequestResponse.statusCode()).isEqualTo(400)
      assertThat(badRequestResponse.body()).contains("invalid repetition count: 0")
    }
  }

  @Test
  def readsRequestBodyAndExtractsPekkoRequestMetadata(): Unit = {
    withServer {
      val echoEndpoint = endpoint.post
        .in("echo")
        .in(extractFromRequest(request => s"${request.method} ${request.uri}"))
        .in(stringBody)
        .out(stringBody)
        .serverLogicSuccess[Future] { case (requestSummary, body) =>
          Future.successful(s"$requestSummary body=$body")
        }

      PekkoHttpServerInterpreter().toRoute(echoEndpoint)
    } { baseUri =>
      val response = sendText(
        JHttpRequest
          .newBuilder(baseUri.resolve("/echo?debug=true"))
          .timeout(RequestTimeout)
          .POST(JHttpRequest.BodyPublishers.ofString("payload", StandardCharsets.UTF_8))
          .header("Content-Type", "text/plain; charset=utf-8")
          .build()
      )

      assertThat(response.statusCode()).isEqualTo(200)
      assertThat(response.body()).contains("POST")
      assertThat(response.body()).contains("/echo")
      assertThat(response.body()).contains("debug=true")
      assertThat(response.body()).contains("body=payload")
    }
  }

  @Test
  def handlesBinaryRequestAndResponseBodies(): Unit = {
    withServer {
      val bytesEndpoint = endpoint.post
        .in("bytes")
        .in(byteArrayBody)
        .out(byteArrayBody)
        .out(header[String]("X-Byte-Count"))
        .serverLogicSuccess[Future] { bytes =>
          Future.successful((bytes.reverse, bytes.length.toString))
        }

      PekkoHttpServerInterpreter().toRoute(bytesEndpoint)
    } { baseUri =>
      val payload = Array[Byte](1, 2, 3, 5, 8, 13)
      val response = client.send(
        JHttpRequest
          .newBuilder(baseUri.resolve("/bytes"))
          .timeout(RequestTimeout)
          .POST(JHttpRequest.BodyPublishers.ofByteArray(payload))
          .header("Content-Type", "application/octet-stream")
          .build(),
        JHttpResponse.BodyHandlers.ofByteArray()
      )

      assertThat(response.statusCode()).isEqualTo(200)
      assertThat(response.headers().firstValue("X-Byte-Count")).hasValue("6")
      assertThat(response.body()).containsExactly(13.toByte, 8.toByte, 5.toByte, 3.toByte, 2.toByte, 1.toByte)
    }
  }

  @Test
  def serialisesServerSentEventsWithPekkoStreams(): Unit = {
    withServer {
      val eventsEndpoint = endpoint.get
        .in("events")
        .out(serverSentEventsBody)
        .serverLogicSuccess[Future] { _ =>
          val events = List(
            ServerSentEvent(Some("alpha"), Some("message"), Some("event-1"), Some(50)),
            ServerSentEvent(Some("beta"), Some("message"), Some("event-2"), None)
          )
          Future.successful(Source(events))
        }

      PekkoHttpServerInterpreter().toRoute(eventsEndpoint)
    } { baseUri =>
      val response = sendText(
        JHttpRequest
          .newBuilder(baseUri.resolve("/events"))
          .timeout(RequestTimeout)
          .GET()
          .build()
      )

      assertThat(response.statusCode()).isEqualTo(200)
      assertThat(response.headers().firstValue("Content-Type").orElse("")).contains("text/event-stream")
      assertThat(response.body()).contains("data: alpha")
      assertThat(response.body()).contains("event: message")
      assertThat(response.body()).contains("id: event-1")
      assertThat(response.body()).contains("retry: 50")
      assertThat(response.body()).contains("data: beta")
      assertThat(response.body()).contains("id: event-2")
    }
  }

  @Test
  def appliesCustomRequestInterceptorBeforeEndpointLogic(): Unit = {
    withServer {
      val interceptedEndpoint = endpoint.post
        .in("intercept")
        .in(stringBody)
        .out(stringBody)
        .serverLogicSuccess[Future](body => Future.successful(s"endpoint saw: $body"))

      val options = PekkoHttpServerOptions.customiseInterceptors
        .prependInterceptor(RequestInterceptor.transformServerRequest { request =>
          val underlying = request.underlying.asInstanceOf[RequestContext]
          val replacedRequest = underlying.request.withEntity(HttpEntity("body supplied by interceptor"))
          Future.successful(request.withUnderlying(underlying.withRequest(replacedRequest)))
        })
        .options

      PekkoHttpServerInterpreter(options).toRoute(interceptedEndpoint)
    } { baseUri =>
      val response = sendText(
        JHttpRequest
          .newBuilder(baseUri.resolve("/intercept"))
          .timeout(RequestTimeout)
          .POST(JHttpRequest.BodyPublishers.ofString("original", StandardCharsets.UTF_8))
          .header("Content-Type", "text/plain; charset=utf-8")
          .build()
      )

      assertThat(response.statusCode()).isEqualTo(200)
      assertThat(response.body()).isEqualTo("endpoint saw: body supplied by interceptor")
    }
  }

  private def withServer(createRoute: ExecutionContext ?=> Route)(runTest: URI => Unit): Unit = {
    implicit val system: ActorSystem = ActorSystem(s"tapir-pekko-http-server-test-${System.nanoTime()}")
    implicit val executionContext: ExecutionContext = system.dispatcher

    try {
      val route = createRoute
      val binding = Await.result(Http().newServerAt("127.0.0.1", 0).bind(route), AwaitTimeout)
      try {
        runTest(URI.create(s"http://127.0.0.1:${binding.localAddress.getPort}"))
      } finally {
        Await.result(binding.unbind(), AwaitTimeout)
      }
    } finally {
      Await.result(system.terminate(), AwaitTimeout)
    }
  }

  private def sendText(request: JHttpRequest): JHttpResponse[String] = {
    client.send(request, JHttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8))
  }
}
