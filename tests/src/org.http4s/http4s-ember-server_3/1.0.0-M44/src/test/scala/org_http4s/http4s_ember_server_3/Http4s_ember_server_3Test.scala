/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_http4s.http4s_ember_server_3

import cats.effect.IO
import cats.effect.unsafe.implicits.global
import cats.syntax.all.*
import com.comcast.ip4s.Host
import fs2.Stream
import com.comcast.ip4s.Port
import org.http4s.EntityDecoder
import org.http4s.Header
import org.http4s.HttpApp
import org.http4s.Method
import org.http4s.Response
import org.http4s.Status
import org.http4s.ember.server.EmberServerBuilder
import org.http4s.implicits.*
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Assertions.fail
import org.junit.jupiter.api.Test
import org.typelevel.ci.CIString
import org.typelevel.log4cats.LoggerFactory
import org.typelevel.log4cats.noop.NoOpFactory

import java.net.InetSocketAddress
import java.net.Socket
import java.net.SocketTimeoutException
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.nio.charset.StandardCharsets
import java.time.Duration as JavaDuration
import scala.concurrent.duration.*

class Http4s_ember_server_3Test {
  import Http4s_ember_server_3Test.*
  import Http4s_ember_server_3Test.given

  @Test
  def serverHandlesMethodsQueryParametersHeadersAndRequestBodies(): Unit = {
    withServer(echoApplication) { port =>
      withClient { client =>
        val helloRequest: HttpRequest = HttpRequest
          .newBuilder(serverUri(port, "/hello?name=ember"))
          .timeout(JavaDuration.ofSeconds(5))
          .GET()
          .build()
        val helloResponse: HttpResponse[String] = client.send(helloRequest, HttpResponse.BodyHandlers.ofString())

        assertEquals(200, helloResponse.statusCode())
        assertEquals("hello ember", helloResponse.body())
        assertEquals("http4s-ember", helloResponse.headers().firstValue("X-Server-Test").orElse("missing"))

        val postRequest: HttpRequest = HttpRequest
          .newBuilder(serverUri(port, "/echo"))
          .timeout(JavaDuration.ofSeconds(5))
          .POST(HttpRequest.BodyPublishers.ofString("native-image"))
          .build()
        val postResponse: HttpResponse[String] = client.send(postRequest, HttpResponse.BodyHandlers.ofString())

        assertEquals(201, postResponse.statusCode())
        assertEquals("egami-evitan", postResponse.body())
      }
    }
  }

  @Test
  def serverAcceptsChunkedRequestBodies(): Unit = {
    withServer(echoApplication) { port =>
      val responseText: String = rawHttpExchange(
        port,
        "POST /echo HTTP/1.1\r\n" +
          "Host: 127.0.0.1\r\n" +
          "Transfer-Encoding: chunked\r\n" +
          "Connection: close\r\n" +
          "\r\n" +
          "6\r\nnative\r\n" +
          "5\r\nimage\r\n" +
          "0\r\n\r\n",
      )

      assertTrue(responseText.startsWith("HTTP/1.1 201"), responseText)
      assertTrue(responseText.contains("egamievitan"), responseText)
    }
  }

  @Test
  def serverStreamsResponseBodies(): Unit = {
    val streamingApplication: HttpApp[IO] = HttpApp[IO] { request =>
      (request.method, request.uri.path.renderString) match {
        case (Method.GET, "/stream") =>
          val body: Stream[IO, Byte] = Stream
            .emits(List("alpha", "-", "omega"))
            .covary[IO]
            .flatMap(chunk => Stream.emits(chunk.getBytes(StandardCharsets.UTF_8).toSeq).covary[IO])

          Response[IO](Status.Ok).withBodyStream(body).pure[IO]

        case _ =>
          Response[IO](Status.NotFound).withEntity("not found").pure[IO]
      }
    }

    withServer(streamingApplication) { port =>
      withClient { client =>
        val request: HttpRequest = HttpRequest
          .newBuilder(serverUri(port, "/stream"))
          .timeout(JavaDuration.ofSeconds(5))
          .GET()
          .build()
        val response: HttpResponse[String] = client.send(request, HttpResponse.BodyHandlers.ofString())

        assertEquals(200, response.statusCode())
        assertEquals("alpha-omega", response.body())
        assertTrue(response.headers().firstValue("Content-Length").isEmpty)
      }
    }
  }

  @Test
  def serverAppliesCustomApplicationErrorHandler(): Unit = {
    val failingApplication: HttpApp[IO] = HttpApp[IO] { request =>
      request.uri.path.renderString match {
        case "/boom" => IO.raiseError(new IllegalStateException("route failed"))
        case _ => Response[IO](Status.Ok).withEntity("ok").pure[IO]
      }
    }

    withServer(
      failingApplication,
      _.withErrorHandler { case _: IllegalStateException =>
        Response[IO](Status.ImATeapot).withEntity("handled failure").pure[IO]
      },
    ) { port =>
      withClient { client =>
        val request: HttpRequest = HttpRequest
          .newBuilder(serverUri(port, "/boom"))
          .timeout(JavaDuration.ofSeconds(5))
          .GET()
          .build()
        val response: HttpResponse[String] = client.send(request, HttpResponse.BodyHandlers.ofString())

        assertEquals(418, response.statusCode())
        assertEquals("handled failure", response.body())
      }
    }
  }

  @Test
  def serverUsesCustomRequestLineParseErrorHandlerForMalformedRequests(): Unit = {
    withServer(
      echoApplication,
      _.withRequestLineParseErrorHandler(_ => Response[IO](Status.BadRequest).withEntity("bad request line").pure[IO]),
    ) { port =>
      val responseText: String = rawHttpExchange(port, "BROKEN-REQUEST-LINE\r\nConnection: close\r\n\r\n")

      assertTrue(responseText.startsWith("HTTP/1.1 400"), responseText)
      assertTrue(responseText.contains("bad request line"), responseText)
    }
  }

  @Test
  def serverUsesCustomMaxHeaderSizeErrorHandlerForOversizedHeaders(): Unit = {
    withServer(
      echoApplication,
      _.withMaxHeaderSize(96)
        .withMaxHeaderSizeErrorHandler(_ =>
          Response[IO](Status.RequestHeaderFieldsTooLarge).withEntity("headers too large").pure[IO]
        ),
    ) { port =>
      val oversizedHeader: String = "x" * 256
      val responseText: String = rawHttpExchange(
        port,
        s"GET /hello HTTP/1.1\r\nHost: 127.0.0.1\r\nX-Oversized: $oversizedHeader\r\nConnection: close\r\n\r\n",
      )

      assertTrue(responseText.startsWith("HTTP/1.1 431"), responseText)
      assertTrue(responseText.contains("headers too large"), responseText)
    }
  }

  @Test
  def builderCopyMethodsExposeImmutableConfiguration(): Unit = {
    val host: Host = Host.fromString("127.0.0.1").getOrElse(fail("valid host literal"))
    val firstPort: Port = Port.fromInt(8080).getOrElse(fail("valid port literal"))
    val secondPort: Port = Port.fromInt(9090).getOrElse(fail("valid port literal"))

    val base: EmberServerBuilder[IO] = EmberServerBuilder
      .default[IO]
      .withHost(host)
      .withPort(firstPort)
      .withMaxConnections(7)
      .withReceiveBufferSize(4096)
      .withMaxHeaderSize(8192)
      .withRequestHeaderReceiveTimeout(3.seconds)
      .withIdleTimeout(4.seconds)
      .withShutdownTimeout(5.seconds)
      .withoutTLS
      .withoutHttp2
      .withoutUnixSocketConfig

    val changedPort: EmberServerBuilder[IO] = base.withPort(secondPort)
    val withoutHost: EmberServerBuilder[IO] = base.withoutHost

    assertEquals(Some(host), base.host)
    assertEquals(firstPort, base.port)
    assertEquals(7, base.maxConnections)
    assertEquals(4096, base.receiveBufferSize)
    assertEquals(8192, base.maxHeaderSize)
    assertEquals(3.seconds, base.requestHeaderReceiveTimeout)
    assertEquals(4.seconds, base.idleTimeout)
    assertEquals(5.seconds, base.shutdownTimeout)
    assertEquals(Nil, base.additionalSocketOptions)

    assertEquals(firstPort, base.port)
    assertEquals(secondPort, changedPort.port)
    assertEquals(None, withoutHost.host)
  }
}

object Http4s_ember_server_3Test {
  private given EntityDecoder[IO, String] = EntityDecoder.text[IO]
  private given LoggerFactory[IO] = NoOpFactory[IO]

  private val echoApplication: HttpApp[IO] = HttpApp[IO] { request =>
    (request.method, request.uri.path.renderString) match {
      case (Method.GET, "/hello") =>
        val name: String = request.params.getOrElse("name", "stranger")
        Response[IO](Status.Ok)
          .putHeaders(Header.Raw(CIString("X-Server-Test"), "http4s-ember"))
          .withEntity(s"hello $name")
          .pure[IO]

      case (Method.POST, "/echo") =>
        request.as[String].map(body => Response[IO](Status.Created).withEntity(body.reverse))

      case _ =>
        Response[IO](Status.NotFound).withEntity("not found").pure[IO]
    }
  }

  private def withServer(
      application: HttpApp[IO],
      configure: EmberServerBuilder[IO] => EmberServerBuilder[IO] = identity,
  )(test: Int => Unit): Unit = {
    val host: Host = Host.fromString("127.0.0.1").getOrElse(fail("valid host literal"))
    val port: Port = Port.fromInt(0).getOrElse(fail("valid ephemeral port"))
    val serverProgram: IO[Unit] = configure(
      EmberServerBuilder
        .default[IO]
        .withHost(host)
        .withPort(port)
        .withHttpApp(application)
        .withRequestHeaderReceiveTimeout(2.seconds)
        .withIdleTimeout(2.seconds)
        .withShutdownTimeout(2.seconds)
    ).build.use { server =>
      IO.blocking(test(server.address.port.value))
    }

    serverProgram.unsafeRunTimed(20.seconds).getOrElse(fail("server test timed out"))
  }

  private def withClient(test: HttpClient => Unit): Unit = {
    val client: HttpClient = HttpClient
      .newBuilder()
      .connectTimeout(JavaDuration.ofSeconds(2))
      .build()
    try test(client)
    finally client.close()
  }

  private def serverUri(port: Int, pathAndQuery: String): URI =
    URI.create(s"http://127.0.0.1:$port$pathAndQuery")

  private def rawHttpExchange(port: Int, requestText: String): String = {
    val socket: Socket = new Socket()
    try {
      socket.connect(new InetSocketAddress("127.0.0.1", port), 2000)
      socket.setSoTimeout(2000)
      val bytes: Array[Byte] = requestText.getBytes(StandardCharsets.US_ASCII)
      socket.getOutputStream.write(bytes)
      socket.getOutputStream.flush()

      val buffer: Array[Byte] = new Array[Byte](4096)
      val response = new StringBuilder
      var keepReading: Boolean = true
      while (keepReading) {
        try {
          val read: Int = socket.getInputStream.read(buffer)
          if (read < 0) keepReading = false
          else response.append(new String(buffer, 0, read, StandardCharsets.UTF_8))
        } catch {
          case _: SocketTimeoutException => keepReading = false
        }
      }
      response.toString()
    } finally socket.close()
  }
}
