/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_http4s.http4s_ember_client_3

import cats.effect.IO
import cats.effect.unsafe.implicits.global
import cats.syntax.all.*
import com.sun.net.httpserver.HttpExchange
import com.sun.net.httpserver.HttpHandler
import com.sun.net.httpserver.HttpServer
import org.http4s.EntityDecoder
import org.http4s.Header
import org.http4s.Method
import org.http4s.ProductId
import org.http4s.Request
import org.http4s.Response
import org.http4s.Status
import org.http4s.Uri
import org.http4s.client.UnexpectedStatus
import org.http4s.ember.client.EmberClientBuilder
import org.http4s.headers.`User-Agent`
import org.http4s.implicits.*
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Assertions.fail
import org.junit.jupiter.api.Test
import org.typelevel.ci.CIString
import org.typelevel.log4cats.LoggerFactory
import org.typelevel.log4cats.noop.NoOpFactory

import java.net.InetSocketAddress
import java.nio.charset.StandardCharsets
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.ThreadFactory
import java.util.concurrent.atomic.AtomicInteger
import scala.concurrent.duration.*

class Http4s_ember_client_3Test {
  import Http4s_ember_client_3Test.*
  import Http4s_ember_client_3Test.given

  @Test
  def clientExecutesGetRequestsAndHonorsUserAgentConfiguration(): Unit = {
    withHttpServer(clientExerciseHandler) { port =>
      val clientUserAgent: `User-Agent` = `User-Agent`(ProductId("native-image-test", Some("1.0")))
      val program: IO[(Status, String, Option[String], String)] = EmberClientBuilder
        .default[IO]
        .withUserAgent(clientUserAgent)
        .withMaxTotal(2)
        .withMaxPerKey(_ => 2)
        .withTimeout(2.seconds)
        .withIdleConnectionTime(4.seconds)
        .build
        .use { client =>
          val inspectedRequest: Request[IO] = Request[IO](
            method = Method.GET,
            uri = uriFor(port, "/inspect?name=ember"),
          ).putHeaders(Header.Raw(CIString("X-Test-Token"), "native-image"))

          val explicitUserAgentRequest: Request[IO] = Request[IO](
            method = Method.GET,
            uri = uriFor(port, "/inspect?name=override"),
          ).putHeaders(Header.Raw(CIString("User-Agent"), "request-agent/2.0"))

          for {
            inspected <- client.run(inspectedRequest).use { response =>
              response.as[String].map(body => (response.status, body, headerValue(response, "X-Client-Test")))
            }
            explicitUserAgentBody <- client.expect[String](explicitUserAgentRequest)
          } yield (inspected._1, inspected._2, inspected._3, explicitUserAgentBody)
        }

      val (status, body, responseHeader, explicitUserAgentBody) = unsafeRun(program)

      assertEquals(Status.Ok, status)
      assertEquals("GET|/inspect|name=ember|native-image|native-image-test/1.0", body)
      assertEquals(Some("get-ok"), responseHeader)
      assertEquals("GET|/inspect|name=override|missing|request-agent/2.0", explicitUserAgentBody)
    }
  }

  @Test
  def clientPostsEntityBodiesAndReadsChunkedResponses(): Unit = {
    withHttpServer(clientExerciseHandler) { port =>
      val program: IO[(String, String)] = EmberClientBuilder
        .default[IO]
        .withoutUserAgent
        .withChunkSize(5)
        .withTimeout(2.seconds)
        .withIdleConnectionTime(4.seconds)
        .build
        .use { client =>
          val postRequest: Request[IO] = Request[IO](
            method = Method.POST,
            uri = uriFor(port, "/echo"),
          ).withEntity("native-image")

          val chunkedRequest: Request[IO] = Request[IO](
            method = Method.GET,
            uri = uriFor(port, "/chunked"),
          )

          for {
            echoed <- client.expect[String](postRequest)
            chunked <- client.expect[String](chunkedRequest)
          } yield (echoed, chunked)
        }

      val (echoed, chunked) = unsafeRun(program)

      assertEquals("egami-evitan|missing", echoed)
      assertEquals("alpha-omega", chunked)
    }
  }

  @Test
  def clientDefaultMethodsDecodeStatusesOptionsAndUnexpectedStatuses(): Unit = {
    withHttpServer(clientExerciseHandler) { port =>
      val program: IO[(String, Option[String], Status, Boolean, Either[Throwable, String])] = EmberClientBuilder
        .default[IO]
        .withTimeout(2.seconds)
        .withIdleConnectionTime(4.seconds)
        .build
        .use { client =>
          val okRequest: Request[IO] = Request[IO](method = Method.GET, uri = uriFor(port, "/ok"))
          val missingRequest: Request[IO] = Request[IO](method = Method.GET, uri = uriFor(port, "/missing"))
          val teapotRequest: Request[IO] = Request[IO](method = Method.GET, uri = uriFor(port, "/teapot"))
          val failureRequest: Request[IO] = Request[IO](method = Method.GET, uri = uriFor(port, "/failure"))

          for {
            ok <- client.expect[String](okRequest)
            missing <- client.expectOption[String](missingRequest)
            teapotStatus <- client.statusFromString(uriFor(port, "/teapot").renderString)
            teapotSuccessful <- client.successful(teapotRequest)
            failure <- client.expect[String](failureRequest).attempt
          } yield (ok, missing, teapotStatus, teapotSuccessful, failure)
        }

      val (ok, missing, teapotStatus, teapotSuccessful, failure) = unsafeRun(program)

      assertEquals("all good", ok)
      assertEquals(None, missing)
      assertEquals(Status.ImATeapot, teapotStatus)
      assertFalse(teapotSuccessful)
      failure match {
        case Left(status: UnexpectedStatus) =>
          assertEquals(Status.InternalServerError, status.status)
          assertEquals(Method.GET, status.requestMethod)
          assertEquals(uriFor(port, "/failure"), status.requestUri)
        case Left(other) => fail(s"expected UnexpectedStatus, got: $other")
        case Right(value) => fail(s"expected failed response, got successful body: $value")
      }
    }
  }

  @Test
  def builderCopyMethodsExposeImmutableConfiguration(): Unit = {
    val userAgent: `User-Agent` = `User-Agent`(ProductId("native-image-test", Some("1.0")))
    val base: EmberClientBuilder[IO] = EmberClientBuilder
      .default[IO]
      .withMaxTotal(3)
      .withIdleTimeInPool(1500.millis)
      .withIdleConnectionTime(4.seconds)
      .withTimeout(2.seconds)
      .withChunkSize(256)
      .withMaxResponseHeaderSize(2048)
      .withUserAgent(userAgent)
      .withoutCheckEndpointAuthentication
      .withoutServerNameIndication
      .withoutHttp2
      .withoutTLSContext

    val changed: EmberClientBuilder[IO] = base
      .withMaxTotal(8)
      .withoutUserAgent
      .withCheckEndpointAuthentication(true)
      .withServerNameIndication(true)

    assertEquals(3, base.maxTotal)
    assertEquals(1500.millis, base.idleTimeInPool)
    assertEquals(2.seconds, base.timeout)
    assertEquals(256, base.chunkSize)
    assertEquals(2048, base.maxResponseHeaderSize)
    assertEquals(Some(userAgent), base.userAgent)
    assertFalse(base.checkEndpointIdentification)
    assertFalse(base.serverNameIndication)
    assertEquals(Nil, base.additionalSocketOptions)

    assertEquals(3, base.maxTotal)
    assertEquals(Some(userAgent), base.userAgent)
    assertFalse(base.checkEndpointIdentification)
    assertFalse(base.serverNameIndication)
    assertEquals(8, changed.maxTotal)
    assertEquals(None, changed.userAgent)
    assertTrue(changed.checkEndpointIdentification)
    assertTrue(changed.serverNameIndication)
  }
}

object Http4s_ember_client_3Test {
  private given EntityDecoder[IO, String] = EntityDecoder.text[IO]
  private given LoggerFactory[IO] = NoOpFactory[IO]

  private def withHttpServer(handler: HttpExchange => Unit)(test: Int => Unit): Unit = {
    val server: HttpServer = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0)
    val executor: ExecutorService = Executors.newCachedThreadPool(DaemonThreadFactory)
    server.createContext(
      "/",
      new HttpHandler {
        override def handle(exchange: HttpExchange): Unit = handler(exchange)
      },
    )
    server.setExecutor(executor)
    server.start()
    try test(server.getAddress.getPort)
    finally {
      server.stop(0)
      executor.shutdownNow()
    }
  }

  private def clientExerciseHandler(exchange: HttpExchange): Unit = {
    exchange.getRequestURI.getPath match {
      case "/inspect" =>
        val token: String = Option(exchange.getRequestHeaders.getFirst("X-Test-Token")).getOrElse("missing")
        val userAgent: String = Option(exchange.getRequestHeaders.getFirst("User-Agent")).getOrElse("missing")
        val query: String = Option(exchange.getRequestURI.getRawQuery).getOrElse("")
        sendText(
          exchange,
          200,
          s"${exchange.getRequestMethod}|${exchange.getRequestURI.getPath}|$query|$token|$userAgent",
          "X-Client-Test" -> "get-ok",
        )

      case "/echo" =>
        val body: String = readUtf8(exchange)
        val userAgent: String = Option(exchange.getRequestHeaders.getFirst("User-Agent")).getOrElse("missing")
        sendText(exchange, 201, s"${body.reverse}|$userAgent")

      case "/chunked" =>
        sendChunkedText(exchange, 200, List("alpha", "-", "omega"))

      case "/ok" =>
        sendText(exchange, 200, "all good")

      case "/missing" =>
        sendText(exchange, 404, "gone")

      case "/teapot" =>
        sendText(exchange, 418, "short and stout")

      case "/failure" =>
        sendText(exchange, 500, "boom")

      case _ =>
        sendText(exchange, 404, "not found")
    }
  }

  private def uriFor(port: Int, pathAndQuery: String): Uri =
    Uri.unsafeFromString(s"http://127.0.0.1:$port$pathAndQuery")

  private def headerValue(response: Response[IO], name: String): Option[String] =
    response.headers.get(CIString(name)).map(_.head.value)

  private def readUtf8(exchange: HttpExchange): String =
    new String(exchange.getRequestBody.readAllBytes(), StandardCharsets.UTF_8)

  private def sendText(exchange: HttpExchange, status: Int, body: String, headers: (String, String)*): Unit = {
    try {
      headers.foreach { case (name, value) => exchange.getResponseHeaders.add(name, value) }
      exchange.getResponseHeaders.add("Content-Type", "text/plain; charset=utf-8")
      val bytes: Array[Byte] = body.getBytes(StandardCharsets.UTF_8)
      exchange.sendResponseHeaders(status, bytes.length.toLong)
      exchange.getResponseBody.write(bytes)
    } finally exchange.close()
  }

  private def sendChunkedText(exchange: HttpExchange, status: Int, chunks: List[String]): Unit = {
    try {
      exchange.getResponseHeaders.add("Content-Type", "text/plain; charset=utf-8")
      exchange.sendResponseHeaders(status, 0L)
      chunks.foreach { chunk =>
        exchange.getResponseBody.write(chunk.getBytes(StandardCharsets.UTF_8))
        exchange.getResponseBody.flush()
      }
    } finally exchange.close()
  }

  private def unsafeRun[A](io: IO[A]): A = io.timeout(20.seconds).unsafeRunSync()

  private object DaemonThreadFactory extends ThreadFactory {
    private val counter: AtomicInteger = new AtomicInteger(0)

    override def newThread(runnable: Runnable): Thread = {
      val thread: Thread = new Thread(runnable, s"http4s-ember-client-test-${counter.incrementAndGet()}")
      thread.setDaemon(true)
      thread
    }
  }
}
