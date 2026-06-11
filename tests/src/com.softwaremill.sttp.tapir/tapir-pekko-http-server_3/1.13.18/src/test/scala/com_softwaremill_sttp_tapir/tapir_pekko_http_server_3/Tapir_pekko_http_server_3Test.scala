/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_softwaremill_sttp_tapir.tapir_pekko_http_server_3

import org.apache.pekko.actor.ActorSystem
import org.apache.pekko.http.scaladsl.Http
import org.apache.pekko.http.scaladsl.server.Route
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import sttp.model.StatusCode
import sttp.tapir.*
import sttp.tapir.server.pekkohttp.PekkoHttpServerInterpreter

import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.nio.charset.StandardCharsets
import java.time.Duration
import java.util.UUID
import java.util.concurrent.Executors
import scala.concurrent.Await
import scala.concurrent.ExecutionContext
import scala.concurrent.Future
import scala.concurrent.duration.DurationInt

class Tapir_pekko_http_server_3Test {
  @Test
  def shouldServeGetEndpointWithPathQueryAndResponseHeader(): Unit = {
    withServer { executionContext =>
      implicit val ec: ExecutionContext = executionContext
      val greetingEndpoint = endpoint.get
        .in("hello")
        .in(path[String]("name"))
        .in(query[String]("punctuation"))
        .out(header[String]("X-Greeting-Name"))
        .out(stringBody)

      PekkoHttpServerInterpreter().toRoute(
        greetingEndpoint.serverLogicSuccess { case (name, punctuation) =>
          Future.successful((name, s"Hello, $name$punctuation"))
        }
      )
    } { baseUri =>
      val response = send(
        HttpRequest.newBuilder(baseUri.resolve("/hello/tapir?punctuation=%21"))
          .timeout(RequestTimeout)
          .GET()
          .build()
      )

      assertThat(response.statusCode()).isEqualTo(200)
      assertThat(response.body()).isEqualTo("Hello, tapir!")
      assertThat(response.headers().firstValue("X-Greeting-Name")).hasValue("tapir")
      assertThat(response.headers().firstValue("Content-Type")).hasValueSatisfying { contentType =>
        assertThat(contentType).startsWith("text/plain")
      }
    }
  }

  @Test
  def shouldRoutePostRequestBodyThroughTapirServerLogic(): Unit = {
    withServer { executionContext =>
      implicit val ec: ExecutionContext = executionContext
      val echoEndpoint = endpoint.post
        .in("echo")
        .in(stringBody)
        .out(stringBody)

      PekkoHttpServerInterpreter().toRoute(
        echoEndpoint.serverLogicSuccess { body =>
          Future.successful(body.reverse)
        }
      )
    } { baseUri =>
      val response = send(
        HttpRequest.newBuilder(baseUri.resolve("/echo"))
          .timeout(RequestTimeout)
          .header("Content-Type", "text/plain; charset=UTF-8")
          .POST(HttpRequest.BodyPublishers.ofString("pekko tapir", StandardCharsets.UTF_8))
          .build()
      )

      assertThat(response.statusCode()).isEqualTo(200)
      assertThat(response.body()).isEqualTo("ripat okkep")
    }
  }

  @Test
  def shouldReturnConfiguredErrorOutputFromServerLogic(): Unit = {
    withServer { executionContext =>
      implicit val ec: ExecutionContext = executionContext
      val divisionEndpoint = endpoint.get
        .in("divide")
        .in(query[Int]("numerator"))
        .in(query[Int]("denominator"))
        .errorOut(statusCode.and(stringBody))
        .out(stringBody)

      PekkoHttpServerInterpreter().toRoute(
        divisionEndpoint.serverLogic { case (numerator, denominator) =>
          Future.successful {
            if (denominator == 0) {
              Left((StatusCode.BadRequest, "denominator must not be zero"))
            } else {
              Right((numerator / denominator).toString)
            }
          }
        }
      )
    } { baseUri =>
      val response = send(
        HttpRequest.newBuilder(baseUri.resolve("/divide?numerator=10&denominator=0"))
          .timeout(RequestTimeout)
          .GET()
          .build()
      )

      assertThat(response.statusCode()).isEqualTo(400)
      assertThat(response.body()).isEqualTo("denominator must not be zero")
      assertThat(response.headers().firstValue("Content-Type")).hasValueSatisfying { contentType =>
        assertThat(contentType).startsWith("text/plain")
      }
    }
  }

  @Test
  def shouldDispatchAmongSeveralServerEndpoints(): Unit = {
    withServer { executionContext =>
      implicit val ec: ExecutionContext = executionContext
      val uppercaseEndpoint = endpoint.get
        .in("transform")
        .in("uppercase")
        .in(query[String]("value"))
        .out(stringBody)
        .serverLogicSuccess(value => Future.successful(value.toUpperCase))

      val lengthEndpoint = endpoint.get
        .in("transform")
        .in("length")
        .in(query[String]("value"))
        .out(stringBody)
        .serverLogicSuccess(value => Future.successful(value.length.toString))

      PekkoHttpServerInterpreter().toRoute(List(uppercaseEndpoint, lengthEndpoint))
    } { baseUri =>
      val uppercase = send(
        HttpRequest.newBuilder(baseUri.resolve("/transform/uppercase?value=tapir"))
          .timeout(RequestTimeout)
          .GET()
          .build()
      )
      val length = send(
        HttpRequest.newBuilder(baseUri.resolve("/transform/length?value=tapir"))
          .timeout(RequestTimeout)
          .GET()
          .build()
      )

      assertThat(uppercase.statusCode()).isEqualTo(200)
      assertThat(uppercase.body()).isEqualTo("TAPIR")
      assertThat(length.statusCode()).isEqualTo(200)
      assertThat(length.body()).isEqualTo("5")
    }
  }

  private def withServer(testRoute: ExecutionContext => Route)(test: URI => Unit): Unit = {
    implicit val system: ActorSystem = ActorSystem(s"tapir-pekko-http-test-${UUID.randomUUID()}")
    implicit val executionContext: ExecutionContext = system.dispatcher
    var binding: Option[Http.ServerBinding] = None

    try {
      val route = testRoute(executionContext)
      val serverBinding = Await.result(
        Http().newServerAt("127.0.0.1", 0).bind(route),
        OperationTimeout
      )
      binding = Some(serverBinding)

      test(URI.create(s"http://127.0.0.1:${serverBinding.localAddress.getPort}"))
    } finally {
      binding.foreach { serverBinding =>
        Await.result(serverBinding.unbind(), OperationTimeout)
      }
      Await.result(system.terminate(), OperationTimeout)
    }
  }

  private def send(request: HttpRequest): HttpResponse[String] = {
    val executor = Executors.newSingleThreadExecutor()
    val client = HttpClient.newBuilder()
      .connectTimeout(RequestTimeout)
      .executor(executor)
      .build()

    try {
      client.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8))
    } finally {
      client.close()
      executor.shutdownNow()
    }
  }

  private val OperationTimeout = 10.seconds
  private val RequestTimeout = Duration.ofSeconds(10)
}
