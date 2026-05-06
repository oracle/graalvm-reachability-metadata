/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_http4s.http4s_ember_client_3

import cats.effect.IO
import cats.effect.unsafe.implicits.global
import org.http4s.Header
import org.http4s.Method
import org.http4s.Request
import org.http4s.Response
import org.http4s.Status
import org.http4s.Uri
import org.http4s.ember.client.EmberClientBuilder
import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.typelevel.ci._
import org.typelevel.log4cats.LoggerFactory
import org.typelevel.log4cats.noop.NoOpFactory

import java.io.BufferedInputStream
import java.io.ByteArrayOutputStream
import java.net.InetAddress
import java.net.ServerSocket
import java.net.Socket
import java.nio.charset.StandardCharsets
import java.util.Locale
import java.util.concurrent.Callable
import java.util.concurrent.Executors
import java.util.concurrent.Future
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import scala.concurrent.duration._

class Http4s_ember_client_3Test {
  private given LoggerFactory[IO] = NoOpFactory[IO]

  @Test
  def getRequestSendsMethodTargetAndHeadersAndReadsResponseBody(): Unit = {
    val server: LocalHttpServer = LocalHttpServer { request =>
      assertEquals("GET", request.method)
      assertEquals("/api/items?name=ember%20client&enabled=true", request.target)
      assertEquals(List("abc123"), request.header("x-test-token"))

      TestResponse(
        status = 200,
        reason = "OK",
        headers = List("Content-Type" -> "text/plain; charset=utf-8"),
        body = utf8("received " + request.target)
      )
    }

    try {
      val response: ReceivedResponse = run {
        EmberClientBuilder.default[IO].build.use { client =>
          val request: Request[IO] = Request[IO](Method.GET, uri(server, "/api/items?name=ember%20client&enabled=true"))
            .putHeaders(Header.Raw(ci"X-Test-Token", "abc123"))
          client.run(request).use(readTextResponse)
        }
      }

      assertEquals(Status.Ok, response.status)
      assertEquals("received /api/items?name=ember%20client&enabled=true", response.body)
      server.awaitHandledRequests()
    } finally {
      server.close()
    }
  }

  @Test
  def postRequestSendsEntityBodyAndReceivesCreatedResponse(): Unit = {
    val server: LocalHttpServer = LocalHttpServer { request =>
      assertEquals("POST", request.method)
      assertEquals("/submit", request.target)
      assertEquals("payload from ember", request.bodyText)

      TestResponse(
        status = 201,
        reason = "Created",
        headers = List("Content-Type" -> "text/plain"),
        body = utf8("created:" + request.bodyText.reverse)
      )
    }

    try {
      val response: ReceivedResponse = run {
        EmberClientBuilder.default[IO].build.use { client =>
          val request: Request[IO] = Request[IO](Method.POST, uri(server, "/submit"))
            .withEntity("payload from ember")
          client.run(request).use(readTextResponse)
        }
      }

      assertEquals(Status.Created, response.status)
      assertEquals("created:rebme morf daolyap", response.body)
      server.awaitHandledRequests()
    } finally {
      server.close()
    }
  }

  @Test
  def chunkedTransferResponseIsDecodedIntoOneBodyStream(): Unit = {
    val server: LocalHttpServer = LocalHttpServer { request =>
      assertEquals("GET", request.method)
      TestResponse(
        status = 200,
        reason = "OK",
        headers = List("Content-Type" -> "text/plain"),
        body = utf8("alpha-beta-gamma"),
        chunked = true
      )
    }

    try {
      val response: ReceivedResponse = run {
        EmberClientBuilder.default[IO].build.use { client =>
          client.run(Request[IO](Method.GET, uri(server, "/chunked"))).use(readTextResponse)
        }
      }

      assertEquals(Status.Ok, response.status)
      assertEquals("alpha-beta-gamma", response.body)
      server.awaitHandledRequests()
    } finally {
      server.close()
    }
  }

  @Test
  def responseBodyCanBeConsumedAsBinaryBytes(): Unit = {
    val binaryPayload: Array[Byte] = Array[Byte](0, 1, 2, 3, 8, 13, 21, 34, 55, 89, -1)
    val server: LocalHttpServer = LocalHttpServer { _ =>
      TestResponse(
        status = 200,
        reason = "OK",
        headers = List("Content-Type" -> "application/octet-stream"),
        body = binaryPayload
      )
    }

    try {
      val response: ReceivedBinaryResponse = run {
        EmberClientBuilder.default[IO].build.use { client =>
          client.run(Request[IO](Method.GET, uri(server, "/bytes"))).use { response =>
            response.body.compile.toVector.map(bytes => ReceivedBinaryResponse(response.status, bytes.toArray))
          }
        }
      }

      assertEquals(Status.Ok, response.status)
      assertArrayEquals(binaryPayload, response.body)
      server.awaitHandledRequests()
    } finally {
      server.close()
    }
  }

  @Test
  def nonSuccessfulResponseStatusAndBodyRemainAvailable(): Unit = {
    val server: LocalHttpServer = LocalHttpServer { request =>
      assertEquals("/missing", request.target)
      TestResponse(
        status = 404,
        reason = "Not Found",
        headers = List("Content-Type" -> "text/plain"),
        body = utf8("short and stout")
      )
    }

    try {
      val response: ReceivedResponse = run {
        EmberClientBuilder.default[IO].build.use { client =>
          client.run(Request[IO](Method.GET, uri(server, "/missing"))).use(readTextResponse)
        }
      }

      assertEquals(Status.NotFound, response.status)
      assertEquals("short and stout", response.body)
      server.awaitHandledRequests()
    } finally {
      server.close()
    }
  }

  @Test
  def oneClientResourceCanExecuteMultipleSequentialRequests(): Unit = {
    val counter: AtomicInteger = new AtomicInteger()
    val server: LocalHttpServer = LocalHttpServer(expectedRequests = 2) { request =>
      val requestNumber: Int = counter.incrementAndGet()
      assertEquals(s"/request-$requestNumber", request.target)
      TestResponse(
        status = 200,
        reason = "OK",
        headers = List("Content-Type" -> "text/plain"),
        body = utf8(s"response-$requestNumber")
      )
    }

    try {
      val bodies: List[String] = run {
        EmberClientBuilder.default[IO].build.use { client =>
          val first: IO[String] = client
            .run(Request[IO](Method.GET, uri(server, "/request-1")))
            .use(response => response.bodyText.compile.string)
          val second: IO[String] = client
            .run(Request[IO](Method.GET, uri(server, "/request-2")))
            .use(response => response.bodyText.compile.string)
          first.flatMap(firstBody => second.map(secondBody => List(firstBody, secondBody)))
        }
      }

      assertEquals(List("response-1", "response-2"), bodies)
      server.awaitHandledRequests()
    } finally {
      server.close()
    }
  }

  private final case class CapturedRequest(
      method: String,
      target: String,
      headers: Map[String, List[String]],
      body: Array[Byte]) {
    def header(name: String): List[String] =
      headers.getOrElse(name.toLowerCase(Locale.ROOT), Nil)

    def bodyText: String =
      new String(body, StandardCharsets.UTF_8)
  }

  private final case class TestResponse(
      status: Int,
      reason: String,
      headers: List[(String, String)] = Nil,
      body: Array[Byte] = Array.empty[Byte],
      chunked: Boolean = false)

  private final case class ReceivedResponse(status: Status, body: String)

  private final case class ReceivedBinaryResponse(status: Status, body: Array[Byte])

  private final class LocalHttpServer private (
      expectedRequests: Int,
      handler: CapturedRequest => TestResponse)
      extends AutoCloseable {
    private val socket: ServerSocket = new ServerSocket(0, 16, InetAddress.getLoopbackAddress)
    private val handledRequests: AtomicInteger = new AtomicInteger()
    private val executor = Executors.newSingleThreadExecutor { runnable =>
      val thread: Thread = new Thread(runnable, "http4s-ember-client-test-server")
      thread.setDaemon(true)
      thread
    }
    private val serving: Future[Unit] = executor.submit(new Callable[Unit] {
      override def call(): Unit = serve()
    })

    val port: Int = socket.getLocalPort

    def awaitHandledRequests(): Unit = {
      serving.get(5, TimeUnit.SECONDS)
      assertEquals(expectedRequests, handledRequests.get())
    }

    override def close(): Unit = {
      socket.close()
      executor.shutdownNow()
      executor.awaitTermination(5, TimeUnit.SECONDS)
    }

    private def serve(): Unit = {
      while (handledRequests.get() < expectedRequests) {
        val client: Socket = socket.accept()
        try {
          val request: CapturedRequest = readRequest(client)
          val response: TestResponse = handler(request)
          writeResponse(client, response)
          handledRequests.incrementAndGet()
        } finally {
          client.close()
        }
      }
    }
  }

  private object LocalHttpServer {
    def apply(handler: CapturedRequest => TestResponse): LocalHttpServer =
      new LocalHttpServer(1, handler)

    def apply(expectedRequests: Int)(handler: CapturedRequest => TestResponse): LocalHttpServer =
      new LocalHttpServer(expectedRequests, handler)
  }

  private def readTextResponse(response: Response[IO]): IO[ReceivedResponse] =
    response.bodyText.compile.string.map(body => ReceivedResponse(response.status, body))

  private def readRequest(socket: Socket): CapturedRequest = {
    socket.setSoTimeout(5000)
    val input: BufferedInputStream = new BufferedInputStream(socket.getInputStream)
    val requestLine: String = readLine(input)
    val requestParts: Array[String] = requestLine.split(" ", 3)
    assertTrue(requestParts.length >= 2, "HTTP request line should include method and target")

    val headerPairs: List[(String, String)] = Iterator
      .continually(readLine(input))
      .takeWhile(_.nonEmpty)
      .map { line =>
        val separator: Int = line.indexOf(':')
        assertTrue(separator > 0, "HTTP header should contain a name and value")
        line.substring(0, separator).toLowerCase(Locale.ROOT) -> line.substring(separator + 1).trim
      }
      .toList
    val headers: Map[String, List[String]] = headerPairs.groupMap(_._1)(_._2)
    val body: Array[Byte] = readBody(input, headers)

    CapturedRequest(requestParts(0), requestParts(1), headers, body)
  }

  private def readBody(input: BufferedInputStream, headers: Map[String, List[String]]): Array[Byte] = {
    if (headers.getOrElse("transfer-encoding", Nil).exists(_.toLowerCase(Locale.ROOT).contains("chunked"))) {
      readChunkedBody(input)
    } else {
      val contentLength: Int = headers.get("content-length").flatMap(_.headOption).map(_.toInt).getOrElse(0)
      readExactly(input, contentLength)
    }
  }

  private def readChunkedBody(input: BufferedInputStream): Array[Byte] = {
    val output: ByteArrayOutputStream = new ByteArrayOutputStream()
    var done: Boolean = false
    while (!done) {
      val chunkSize: Int = Integer.parseInt(readLine(input), 16)
      if (chunkSize == 0) {
        readLine(input)
        done = true
      } else {
        output.write(readExactly(input, chunkSize))
        readLine(input)
      }
    }
    output.toByteArray
  }

  private def writeResponse(socket: Socket, response: TestResponse): Unit = {
    val output = socket.getOutputStream
    output.write(ascii(s"HTTP/1.1 ${response.status} ${response.reason}\r\n"))
    output.write(ascii("Connection: close\r\n"))
    response.headers.foreach { case (name, value) =>
      output.write(ascii(s"$name: $value\r\n"))
    }
    if (response.chunked) {
      output.write(ascii("Transfer-Encoding: chunked\r\n\r\n"))
      val splitAt: Int = response.body.length / 2
      writeChunk(output, response.body.take(splitAt))
      writeChunk(output, response.body.drop(splitAt))
      output.write(ascii("0\r\n\r\n"))
    } else {
      output.write(ascii(s"Content-Length: ${response.body.length}\r\n\r\n"))
      output.write(response.body)
    }
    output.flush()
  }

  private def writeChunk(output: java.io.OutputStream, bytes: Array[Byte]): Unit = {
    if (bytes.nonEmpty) {
      output.write(ascii(Integer.toHexString(bytes.length) + "\r\n"))
      output.write(bytes)
      output.write(ascii("\r\n"))
    }
  }

  private def readLine(input: BufferedInputStream): String = {
    val buffer: ByteArrayOutputStream = new ByteArrayOutputStream()
    var previous: Int = -1
    var current: Int = input.read()
    while (current != -1 && !(previous == '\r' && current == '\n')) {
      buffer.write(current)
      previous = current
      current = input.read()
    }
    val bytes: Array[Byte] = buffer.toByteArray
    val lineBytes: Array[Byte] =
      if (bytes.nonEmpty && bytes.last == '\r'.toByte) bytes.dropRight(1) else bytes
    new String(lineBytes, StandardCharsets.ISO_8859_1)
  }

  private def readExactly(input: BufferedInputStream, length: Int): Array[Byte] = {
    val bytes: Array[Byte] = new Array[Byte](length)
    var offset: Int = 0
    while (offset < length) {
      val read: Int = input.read(bytes, offset, length - offset)
      if (read == -1) {
        throw new AssertionError(s"expected $length request bytes, got $offset")
      }
      offset += read
    }
    bytes
  }

  private def uri(server: LocalHttpServer, pathAndQuery: String): Uri =
    Uri.fromString(s"http://127.0.0.1:${server.port}$pathAndQuery").fold(
      failure => throw new IllegalArgumentException(failure.message),
      identity
    )

  private def utf8(value: String): Array[Byte] =
    value.getBytes(StandardCharsets.UTF_8)

  private def ascii(value: String): Array[Byte] =
    value.getBytes(StandardCharsets.US_ASCII)

  private def run[A](io: IO[A]): A =
    io.timeout(30.seconds).unsafeRunSync()
}
