/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_typesafe_play.play_ws_standalone_2_13

import akka.actor.ActorSystem
import akka.stream.Materializer
import akka.stream.scaladsl.Sink
import akka.util.ByteString
import com.sun.net.httpserver.HttpExchange
import com.sun.net.httpserver.HttpHandler
import com.sun.net.httpserver.HttpServer
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import play.api.libs.ws.BodyWritable
import play.api.libs.ws.DefaultBodyReadables._
import play.api.libs.ws.DefaultBodyWritables._
import play.api.libs.ws.DefaultWSCookie
import play.api.libs.ws.DefaultWSProxyServer
import play.api.libs.ws.EmptyBody
import play.api.libs.ws.InMemoryBody
import play.api.libs.ws.StandaloneWSClient
import play.api.libs.ws.WSAuthScheme
import play.api.libs.ws.WSClientConfig
import play.api.libs.ws.WSRequestExecutor
import play.api.libs.ws.WSRequestFilter
import play.api.libs.ws.ahc.StandaloneAhcWSClient

import java.io.IOException
import java.net.InetAddress
import java.net.InetSocketAddress
import java.nio.ByteBuffer
import java.nio.charset.StandardCharsets.UTF_8
import java.util.Base64
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference
import scala.concurrent.Await
import scala.concurrent.duration.DurationInt
import scala.jdk.CollectionConverters._
import scala.util.Using

class Play_ws_standalone_2_13Test {
  @Test
  def standaloneClientExecutesLocalGetAndReadsResponseMetadata(): Unit = {
    Using.resource(TestHttpServer { exchange =>
      assertThat(exchange.getRequestMethod).isEqualTo("GET")
      assertThat(exchange.getRequestURI.getPath).isEqualTo("/echo")
      assertThat(exchange.getRequestURI.getRawQuery).contains("name=play", "mode=native")
      assertThat(exchange.getRequestHeaders.getFirst("X-Test")).isEqualTo("header-value")
      assertThat(exchange.getRequestHeaders.getFirst("Cookie")).contains("clientCookie=cookie-value")

      respond(
        exchange,
        200,
        "method=GET;query=" + exchange.getRequestURI.getRawQuery,
        Seq(
          "Content-Type" -> "text/plain; charset=UTF-8",
          "X-Reply" -> "ok",
          "Set-Cookie" -> "serverCookie=server-value; Path=/; HttpOnly"
        )
      )
    }) { server =>
      withWs { fixture =>
        implicit val materializer: Materializer = fixture.materializer

        val response = Await.result(
          fixture.client
            .url(server.url("/echo"))
            .addQueryStringParameters("name" -> "play", "mode" -> "native")
            .addHttpHeaders("X-Test" -> "header-value")
            .addCookies(DefaultWSCookie("clientCookie", "cookie-value"))
            .get(),
          30.seconds
        )

        server.assertNoFailure()
        assertThat(response.status).isEqualTo(200)
        assertThat(response.statusText).isEqualTo("OK")
        assertThat(response.uri.getPath).isEqualTo("/echo")
        assertThat(response.contentType).startsWith("text/plain")
        assertThat(response.header("X-Reply").get).isEqualTo("ok")
        assertThat(response.headerValues("X-Reply").asJava).containsExactly("ok")
        assertThat(response.cookie("serverCookie").get.value).isEqualTo("server-value")
        assertThat(response.body).contains("method=GET")
        assertThat(response.body[String]).contains("query=")
        assertThat(new String(response.body[Array[Byte]], UTF_8)).contains("name=play")
        assertThat(response.body[ByteString].utf8String).contains("mode=native")
        assertThat(UTF_8.decode(response.body[ByteBuffer]).toString).contains("method=GET")

        val streamedBody = Await.result(
          response.bodyAsSource.runWith(Sink.fold[ByteString, ByteString](ByteString.empty)(_ ++ _)),
          30.seconds
        )
        assertThat(streamedBody.utf8String).contains("method=GET")
      }
    }
  }

  @Test
  def basicAuthenticationRespondsToServerChallenge(): Unit = {
    val expectedAuthorization = "Basic " + Base64.getEncoder.encodeToString(
      "play:secret".getBytes(UTF_8)
    )
    val authorizedRequests = new AtomicInteger()

    Using.resource(TestHttpServer { exchange =>
      assertThat(exchange.getRequestMethod).isEqualTo("GET")
      assertThat(exchange.getRequestURI.getPath).isEqualTo("/secure")

      if (expectedAuthorization == exchange.getRequestHeaders.getFirst("Authorization")) {
        authorizedRequests.incrementAndGet()
        respond(exchange, 200, "authorized", Seq("Content-Type" -> "text/plain"))
      } else {
        respond(
          exchange,
          401,
          "authentication required",
          Seq(
            "WWW-Authenticate" -> "Basic realm=\"play-ws-test\"",
            "Content-Type" -> "text/plain"
          )
        )
      }
    }) { server =>
      withWs { fixture =>
        val response = Await.result(
          fixture.client
            .url(server.url("/secure"))
            .withAuth("play", "secret", WSAuthScheme.BASIC)
            .withRequestTimeout(10.seconds)
            .get(),
          30.seconds
        )

        server.assertNoFailure()
        assertThat(response.status).isEqualTo(200)
        assertThat(response.body[String]).isEqualTo("authorized")
        assertThat(authorizedRequests.get()).isEqualTo(1)
      }
    }
  }

  @Test
  def followsRedirectResponsesWhenConfigured(): Unit = {
    val redirectRequests = new AtomicInteger()
    val finalRequests = new AtomicInteger()

    Using.resource(TestHttpServer { exchange =>
      exchange.getRequestURI.getPath match {
        case "/start" =>
          redirectRequests.incrementAndGet()
          assertThat(exchange.getRequestMethod).isEqualTo("GET")
          respond(
            exchange,
            302,
            "redirecting",
            Seq("Location" -> "/final?source=redirect", "Content-Type" -> "text/plain")
          )
        case "/final" =>
          finalRequests.incrementAndGet()
          assertThat(exchange.getRequestMethod).isEqualTo("GET")
          assertThat(exchange.getRequestURI.getRawQuery).isEqualTo("source=redirect")
          respond(exchange, 200, "reached final", Seq("Content-Type" -> "text/plain"))
        case other =>
          throw new AssertionError(s"unexpected request path: $other")
      }
    }) { server =>
      withWs { fixture =>
        val response = Await.result(
          fixture.client
            .url(server.url("/start"))
            .withFollowRedirects(true)
            .withRequestTimeout(10.seconds)
            .get(),
          30.seconds
        )

        server.assertNoFailure()
        assertThat(response.status).isEqualTo(200)
        assertThat(response.body[String]).isEqualTo("reached final")
        assertThat(redirectRequests.get()).isEqualTo(1)
        assertThat(finalRequests.get()).isEqualTo(1)
      }
    }
  }

  @Test
  def requestFilterAndImmutableBuilderSettingsApplyToExecutedPost(): Unit = {
    Using.resource(TestHttpServer { exchange =>
      assertThat(exchange.getRequestMethod).isEqualTo("POST")
      assertThat(exchange.getRequestURI.getPath).isEqualTo("/submit")
      assertThat(exchange.getRequestHeaders.getFirst("X-Filtered")).isEqualTo("true")
      assertThat(exchange.getRequestHeaders.getFirst("Content-Type")).startsWith("text/plain")
      assertThat(readRequestBody(exchange)).isEqualTo("payload")
      respond(exchange, 202, "accepted", Seq("Content-Type" -> "text/plain"))
    }) { server =>
      withWs { fixture =>
        val filter = WSRequestFilter { executor =>
          WSRequestExecutor { request =>
            executor(request.addHttpHeaders("X-Filtered" -> "true"))
          }
        }

        val request = fixture.client
          .url(server.url("/submit"))
          .withRequestFilter(filter)
          .withRequestTimeout(10.seconds)
          .withFollowRedirects(false)
          .withMethod("POST")
          .withBody("payload")

        assertThat(request.method).isEqualTo("POST")
        assertThat(request.requestTimeout.get).isEqualTo(10.seconds)
        assertThat(request.followRedirects.get).isEqualTo(false)
        assertThat(request.contentType.get).startsWith("text/plain")
        assertThat(request.body.asInstanceOf[InMemoryBody].bytes.utf8String).isEqualTo("payload")

        val response = Await.result(request.execute(), 30.seconds)

        server.assertNoFailure()
        assertThat(response.status).isEqualTo(202)
        assertThat(response.body[String]).isEqualTo("accepted")
      }
    }
  }

  @Test
  def defaultBodyWritableEncodesFormDataForPutRequests(): Unit = {
    Using.resource(TestHttpServer { exchange =>
      assertThat(exchange.getRequestMethod).isEqualTo("PUT")
      assertThat(exchange.getRequestURI.getPath).isEqualTo("/form")
      assertThat(exchange.getRequestHeaders.getFirst("Content-Type"))
        .startsWith("application/x-www-form-urlencoded")

      val requestBody = readRequestBody(exchange)
      assertThat(requestBody).contains("first=one", "second=two")
      respond(exchange, 201, "stored", Seq("Content-Type" -> "text/plain"))
    }) { server =>
      withWs { fixture =>
        val response = Await.result(
          fixture.client
            .url(server.url("/form"))
            .put(Map("first" -> "one", "second" -> "two")),
          30.seconds
        )

        server.assertNoFailure()
        assertThat(response.status).isEqualTo(201)
        assertThat(response.body[String]).isEqualTo("stored")
      }
    }
  }

  @Test
  def valueTypesAndBodyHelpersExposeExpectedStandaloneApiState(): Unit = {
    val config = WSClientConfig(
      connectionTimeout = 10.seconds,
      idleTimeout = 11.seconds,
      requestTimeout = 12.seconds,
      followRedirects = false,
      useProxyProperties = true,
      userAgent = Some("play-ws-standalone-test"),
      compressionEnabled = false
    )
    assertThat(config.connectionTimeout).isEqualTo(10.seconds)
    assertThat(config.idleTimeout).isEqualTo(11.seconds)
    assertThat(config.requestTimeout).isEqualTo(12.seconds)
    assertThat(config.followRedirects).isFalse()
    assertThat(config.useProxyProperties).isTrue()
    assertThat(config.userAgent.get).isEqualTo("play-ws-standalone-test")
    assertThat(config.copy(followRedirects = true).followRedirects).isTrue()

    val cookie = DefaultWSCookie(
      name = "token",
      value = "abc",
      domain = Some("example.test"),
      path = Some("/api"),
      maxAge = Some(60L),
      secure = true,
      httpOnly = true
    )
    assertThat(cookie.name).isEqualTo("token")
    assertThat(cookie.value).isEqualTo("abc")
    assertThat(cookie.domain.get).isEqualTo("example.test")
    assertThat(cookie.path.get).isEqualTo("/api")
    assertThat(cookie.maxAge.get).isEqualTo(60L)
    assertThat(cookie.secure).isTrue()
    assertThat(cookie.httpOnly).isTrue()
    assertThat(cookie.copy(value = "updated").value).isEqualTo("updated")

    val proxy = DefaultWSProxyServer(
      host = "proxy.example.test",
      port = 8080,
      protocol = Some("http"),
      principal = Some("user"),
      password = Some("secret"),
      ntlmDomain = Some("DOMAIN"),
      encoding = Some("UTF-8"),
      nonProxyHosts = Some(Seq("localhost", "127.0.0.1"))
    )
    assertThat(proxy.host).isEqualTo("proxy.example.test")
    assertThat(proxy.port).isEqualTo(8080)
    assertThat(proxy.protocol.get).isEqualTo("http")
    assertThat(proxy.principal.get).isEqualTo("user")
    assertThat(proxy.password.get).isEqualTo("secret")
    assertThat(proxy.ntlmDomain.get).isEqualTo("DOMAIN")
    assertThat(proxy.encoding.get).isEqualTo("UTF-8")
    assertThat(proxy.nonProxyHosts.get.asJava).containsExactly("localhost", "127.0.0.1")

    val mappedWriter: BodyWritable[Int] = writeableOf_String.map[Int](_.toString)
    val mappedBody = mappedWriter.transform(123).asInstanceOf[InMemoryBody]
    assertThat(mappedWriter.contentType).startsWith("text/plain")
    assertThat(mappedBody.bytes.utf8String).isEqualTo("123")

    val byteBufferBody = writeableOf_ByteBuffer
      .transform(ByteBuffer.wrap("buffer-body".getBytes(UTF_8)))
      .asInstanceOf[InMemoryBody]
    assertThat(byteBufferBody.bytes.utf8String).isEqualTo("buffer-body")

    val formBody = writeableOf_urlEncodedSimpleForm
      .transform(Map("hello" -> "world value"))
      .asInstanceOf[InMemoryBody]
    assertThat(formBody.bytes.utf8String).isEqualTo("hello=world+value")

    assertThat(writeableOf_WsBody.transform(EmptyBody)).isSameAs(EmptyBody)
  }

  private def withWs(block: WsFixture => Unit): Unit = {
    val fixture = new WsFixture
    try {
      block(fixture)
    } finally {
      fixture.close()
    }
  }

  private def readRequestBody(exchange: HttpExchange): String = {
    new String(exchange.getRequestBody.readAllBytes(), UTF_8)
  }

  private def respond(
      exchange: HttpExchange,
      status: Int,
      body: String,
      headers: Seq[(String, String)] = Seq.empty
  ): Unit = {
    val bytes = body.getBytes(UTF_8)
    headers.foreach { case (name, value) => exchange.getResponseHeaders.add(name, value) }
    exchange.sendResponseHeaders(status, bytes.length.toLong)
    val output = exchange.getResponseBody
    try {
      output.write(bytes)
    } finally {
      output.close()
      exchange.close()
    }
  }

  private final class WsFixture extends AutoCloseable {
    val system: ActorSystem = ActorSystem(s"play-ws-standalone-test-${WsFixture.nextId()}")
    implicit val materializer: Materializer = Materializer(system)
    val client: StandaloneWSClient = StandaloneAhcWSClient()

    override def close(): Unit = {
      var closeFailure: Throwable = null
      try {
        client.close()
      } catch {
        case error: Throwable => closeFailure = error
      } finally {
        Await.result(system.terminate(), 30.seconds)
      }
      if (closeFailure != null) {
        throw closeFailure
      }
    }
  }

  private object WsFixture {
    private val ids = new AtomicInteger()

    def nextId(): Int = ids.incrementAndGet()
  }

  private final class TestHttpServer private (handler: HttpExchange => Unit) extends AutoCloseable {
    private val serverFailure = new AtomicReference[Throwable]()
    private val executor = Executors.newCachedThreadPool()
    private val server = HttpServer.create(new InetSocketAddress(InetAddress.getLoopbackAddress, 0), 0)

    server.createContext(
      "/",
      new HttpHandler {
        override def handle(exchange: HttpExchange): Unit = {
          try {
            handler(exchange)
          } catch {
            case error: Throwable =>
              serverFailure.compareAndSet(null, error)
              if (exchange.getResponseCode == -1) {
                respond(exchange, 500, error.toString, Seq("Content-Type" -> "text/plain"))
              } else {
                exchange.close()
              }
          }
        }
      }
    )
    server.setExecutor(executor)
    server.start()

    def url(path: String): String = {
      val address = server.getAddress
      val hostAddress = address.getAddress.getHostAddress
      val host = if (hostAddress.contains(":")) s"[$hostAddress]" else hostAddress
      s"http://$host:${address.getPort}$path"
    }

    def assertNoFailure(): Unit = {
      val failure = serverFailure.get()
      if (failure != null) {
        throw new AssertionError("HTTP test server handler failed", failure)
      }
    }

    override def close(): Unit = {
      server.stop(0)
      executor.shutdownNow()
      executor.awaitTermination(10, TimeUnit.SECONDS)
    }
  }

  private object TestHttpServer {
    @throws[IOException]
    def apply(handler: HttpExchange => Unit): TestHttpServer = new TestHttpServer(handler)
  }
}
