/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_lihaoyi.requests_3

import com.sun.net.httpserver.HttpExchange
import com.sun.net.httpserver.HttpHandler
import com.sun.net.httpserver.HttpServer
import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.net.InetSocketAddress
import java.net.URI
import java.net.URLDecoder
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import java.util.zip.GZIPInputStream
import java.util.zip.GZIPOutputStream

class Requests_3Test {
  @Test
  def getSendsQueryParamsHeadersCookiesAndExposesTextResponseHelpers(): Unit = {
    withServer { exchange =>
      assertEquals("GET", exchange.getRequestMethod)
      assertEquals("/inspect", exchange.getRequestURI.getPath)

      val params: Map[String, Seq[String]] = queryParams(exchange.getRequestURI)
      assertEquals(Seq("hello world"), params("q"))
      assertEquals(Seq("one", "two"), params("repeat"))
      assertEquals("native-image", exchange.getRequestHeaders.getFirst("X-Client"))
      val cookieHeader: String = exchange.getRequestHeaders.getFirst("Cookie")
      assertTrue(cookieHeader.contains("client-cookie"))
      assertTrue(cookieHeader.contains("scala"))

      sendText(
        exchange,
        200,
        "first line\nsecond line\n",
        Seq(
          "Content-Type" -> "text/plain; charset=UTF-8",
          "Set-Cookie" -> "server-cookie=stored; Path=/"
        )
      )
    } { baseUrl =>
      val response: requests.Response = requests.get(
        s"$baseUrl/inspect",
        params = Seq("q" -> "hello world", "repeat" -> "one", "repeat" -> "two"),
        headers = Seq("X-Client" -> "native-image"),
        cookieValues = Map("client-cookie" -> "scala"),
        readTimeout = 5000,
        connectTimeout = 5000
      )

      assertEquals(200, response.statusCode)
      assertTrue(response.is2xx)
      assertFalse(response.is3xx)
      assertEquals("first line\nsecond line\n", response.text())
      assertEquals("first line\nsecond line", response.trim())
      assertEquals(Vector("first line", "second line"), response.lines())
      assertTrue(response.contentType.exists(_.startsWith("text/plain")))
      assertEquals("stored", response.cookies("server-cookie").getValue)
    }
  }

  @Test
  def postFormDataUsesBasicAuthAndGzipRequestCompression(): Unit = {
    withServer { exchange =>
      assertEquals("POST", exchange.getRequestMethod)
      assertEquals("Basic dXNlcjpwYXNz", exchange.getRequestHeaders.getFirst("Authorization"))
      assertEquals("gzip", exchange.getRequestHeaders.getFirst("Content-Encoding"))
      assertTrue(exchange.getRequestHeaders.getFirst("Content-Type").startsWith("application/x-www-form-urlencoded"))

      val body: String = new String(gunzip(readRequestBody(exchange)), StandardCharsets.UTF_8)
      val fields: Map[String, Seq[String]] = formFields(body)
      assertEquals(Seq("Li Haoyi"), fields("name"))
      assertEquals(Seq("New York"), fields("city"))
      sendText(exchange, 201, "created")
    } { baseUrl =>
      val response: requests.Response = requests.post(
        s"$baseUrl/form",
        data = requests.RequestBlob.FormEncodedRequestBlob(Seq("name" -> "Li Haoyi", "city" -> "New York")),
        auth = new requests.RequestAuth.Basic("user", "pass"),
        compress = requests.Compress.Gzip,
        readTimeout = 5000,
        connectTimeout = 5000
      )

      assertEquals(201, response.statusCode)
      assertEquals("created", response.text())
    }
  }

  @Test
  def multipartUploadWritesStringFileAndNioFileParts(): Unit = {
    val filePart: Path = Files.createTempFile("requests-file-part", ".txt")
    val nioFilePart: Path = Files.createTempFile("requests-nio-file-part", ".txt")
    Files.writeString(filePart, "file body", StandardCharsets.UTF_8)
    Files.writeString(nioFilePart, "nio file body", StandardCharsets.UTF_8)

    try {
      withServer { exchange =>
        assertEquals("POST", exchange.getRequestMethod)
        assertTrue(exchange.getRequestHeaders.getFirst("Content-Type").startsWith("multipart/form-data; boundary="))

        val body: String = new String(readRequestBody(exchange), StandardCharsets.ISO_8859_1)
        assertTrue(body.contains("name=\"field\""))
        assertTrue(body.contains("plain value"))
        assertTrue(body.contains("name=\"file\""))
        assertTrue(body.contains("filename=\"file.txt\""))
        assertTrue(body.contains("file body"))
        assertTrue(body.contains("name=\"nioFile\""))
        assertTrue(body.contains("filename=\"nio-file.txt\""))
        assertTrue(body.contains("nio file body"))
        sendText(exchange, 200, "multipart-ok")
      } { baseUrl =>
        val multipart: requests.MultiPart = requests.MultiPart(
          requests.MultiItem("field", stringBlob("plain value")),
          requests.MultiItem("file", requests.RequestBlob.FileRequestBlob(filePart.toFile), "file.txt"),
          requests.MultiItem("nioFile", requests.RequestBlob.NioFileRequestBlob(nioFilePart), "nio-file.txt")
        )

        val response: requests.Response = requests.post(
          s"$baseUrl/upload",
          data = multipart,
          readTimeout = 5000,
          connectTimeout = 5000
        )
        assertEquals("multipart-ok", response.text())
      }
    } finally {
      Files.deleteIfExists(filePart)
      Files.deleteIfExists(nioFilePart)
    }
  }

  @Test
  def sessionPersistsCookiesAcrossRedirects(): Unit = {
    withServer { exchange =>
      exchange.getRequestURI.getPath match {
        case "/set-cookie" =>
          sendText(exchange, 200, "cookie-set", Seq("Set-Cookie" -> "session-id=abc123; Path=/"))
        case "/redirect" =>
          exchange.getResponseHeaders.add("Location", "/cookie-required")
          exchange.sendResponseHeaders(302, -1)
          exchange.close()
        case "/cookie-required" =>
          val cookie: String = Option(exchange.getRequestHeaders.getFirst("Cookie")).getOrElse("")
          if (cookie.contains("session-id") && cookie.contains("abc123")) sendText(exchange, 200, "redirected with cookie")
          else sendText(exchange, 403, "missing cookie")
        case other =>
          sendText(exchange, 404, s"unknown path: $other")
      }
    } { baseUrl =>
      val session: requests.Session = requests.Session(readTimeout = 5000, connectTimeout = 5000)

      val first: requests.Response = session.get(s"$baseUrl/set-cookie")
      assertEquals("cookie-set", first.text())
      assertEquals("abc123", session.cookies("session-id").getValue)

      val redirected: requests.Response = session.get(s"$baseUrl/redirect", maxRedirects = 3)
      assertEquals(200, redirected.statusCode)
      assertEquals("redirected with cookie", redirected.text())
      assertTrue(redirected.history.nonEmpty)
      assertEquals(302, redirected.history.get.statusCode)
    }
  }

  @Test
  def unsuccessfulStatusThrowsByDefaultButCanBeReturnedWhenCheckIsDisabled(): Unit = {
    withServer { exchange =>
      exchange.getRequestURI.getPath match {
        case "/missing" => sendText(exchange, 404, "not found")
        case "/error" => sendText(exchange, 503, "temporarily unavailable")
        case other => sendText(exchange, 500, s"unexpected path: $other")
      }
    } { baseUrl =>
      val thrown: requests.RequestFailedException = assertThrows(
        classOf[requests.RequestFailedException],
        () => requests.get(s"$baseUrl/missing", readTimeout = 5000, connectTimeout = 5000)
      )
      assertEquals(404, thrown.response.statusCode)
      assertTrue(thrown.response.is4xx)
      assertEquals("not found", thrown.response.text())

      val missing: requests.Response = requests.get(
        s"$baseUrl/missing",
        check = false,
        readTimeout = 5000,
        connectTimeout = 5000
      )
      assertEquals(404, missing.statusCode)
      assertTrue(missing.is4xx)
      assertFalse(missing.is2xx)

      val unavailable: requests.Response = requests.get(
        s"$baseUrl/error",
        check = false,
        readTimeout = 5000,
        connectTimeout = 5000
      )
      assertEquals(503, unavailable.statusCode)
      assertTrue(unavailable.is5xx)
    }
  }

  @Test
  def streamingApiExposesResponseHeadersBeforeReadingBytes(): Unit = {
    val expected: Array[Byte] = (0 until 256).map(_.toByte).toArray

    withServer { exchange =>
      sendBytes(
        exchange,
        200,
        expected,
        Seq("Content-Type" -> "application/octet-stream", "X-Stream" -> "present")
      )
    } { baseUrl =>
      val seenStatus: AtomicInteger = new AtomicInteger(0)
      val readable: geny.Readable = requests.get.stream(
        s"$baseUrl/stream",
        readTimeout = 5000,
        connectTimeout = 5000,
        onHeadersReceived = headers => {
          seenStatus.set(headers.statusCode)
          assertTrue(headers.is2xx)
          assertFalse(headers.is4xx)
          ()
        }
      )

      val actual: Array[Byte] = readable.readBytesThrough(input => input.readAllBytes())
      assertEquals(200, seenStatus.get())
      assertArrayEquals(expected, actual)
    }
  }

  @Test
  def gzipResponsesAreDecompressedOnlyWhenAutoDecompressIsEnabled(): Unit = {
    val plainText: String = "compressed response text"
    val compressed: Array[Byte] = gzip(plainText.getBytes(StandardCharsets.UTF_8))

    withServer { exchange =>
      sendBytes(
        exchange,
        200,
        compressed,
        Seq("Content-Type" -> "text/plain; charset=UTF-8", "Content-Encoding" -> "gzip")
      )
    } { baseUrl =>
      val decompressed: requests.Response = requests.get(
        s"$baseUrl/gzip",
        autoDecompress = true,
        readTimeout = 5000,
        connectTimeout = 5000
      )
      assertEquals(plainText, decompressed.text())
      assertArrayEquals(plainText.getBytes(StandardCharsets.UTF_8), decompressed.bytes)

      val raw: requests.Response = requests.get(
        s"$baseUrl/gzip",
        autoDecompress = false,
        readTimeout = 5000,
        connectTimeout = 5000
      )
      assertArrayEquals(compressed, raw.bytes)
    }
  }

  @Test
  def putRequesterSendsBearerAuthAndRequestBody(): Unit = {
    withServer { exchange =>
      assertEquals("PUT", exchange.getRequestMethod)
      assertEquals("Bearer token-123", exchange.getRequestHeaders.getFirst("Authorization"))
      assertEquals("payload", new String(readRequestBody(exchange), StandardCharsets.UTF_8))
      sendText(exchange, 202, "accepted")
    } { baseUrl =>
      val response: requests.Response = requests.put(
        s"$baseUrl/resource",
        data = stringBlob("payload"),
        auth = requests.RequestAuth.Bearer("token-123"),
        readTimeout = 5000,
        connectTimeout = 5000
      )

      assertEquals(202, response.statusCode)
      assertEquals("accepted", response.text())
    }
  }

  private def withServer(handler: HttpExchange => Unit)(test: String => Unit): Unit = {
    val server: HttpServer = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0)
    val executor: ExecutorService = Executors.newCachedThreadPool { (runnable: Runnable) =>
      val thread: Thread = new Thread(runnable, "requests-test-http-server")
      thread.setDaemon(true)
      thread
    }
    server.createContext(
      "/",
      new HttpHandler {
        override def handle(exchange: HttpExchange): Unit = {
          try handler(exchange)
          catch {
            case throwable: Throwable =>
              if (exchange.getResponseCode == -1) {
                val message: String = s"handler failed: ${throwable.getClass.getName}: ${throwable.getMessage}"
                sendText(exchange, 500, message)
              } else {
                exchange.close()
              }
          }
        }
      }
    )
    server.setExecutor(executor)
    server.start()

    try test(s"http://127.0.0.1:${server.getAddress.getPort}")
    finally {
      server.stop(0)
      executor.shutdownNow()
      executor.awaitTermination(5, TimeUnit.SECONDS)
    }
  }

  private def sendText(
      exchange: HttpExchange,
      statusCode: Int,
      body: String,
      headers: Seq[(String, String)] = Seq.empty
  ): Unit = {
    sendBytes(exchange, statusCode, body.getBytes(StandardCharsets.UTF_8), headers)
  }

  private def sendBytes(
      exchange: HttpExchange,
      statusCode: Int,
      body: Array[Byte],
      headers: Seq[(String, String)] = Seq.empty
  ): Unit = {
    headers.foreach { case (name, value) => exchange.getResponseHeaders.add(name, value) }
    exchange.sendResponseHeaders(statusCode, body.length.toLong)
    val output = exchange.getResponseBody
    try output.write(body)
    finally output.close()
  }

  private def readRequestBody(exchange: HttpExchange): Array[Byte] = {
    val input = exchange.getRequestBody
    try input.readAllBytes()
    finally input.close()
  }

  private def stringBlob(value: String): requests.RequestBlob = {
    requests.RequestBlob.ByteSourceRequestBlob[String](value)((text: String) => geny.Writable.StringWritable(text))
  }

  private def queryParams(uri: URI): Map[String, Seq[String]] = {
    formFields(Option(uri.getRawQuery).getOrElse(""))
  }

  private def formFields(encoded: String): Map[String, Seq[String]] = {
    encoded
      .split("&")
      .toSeq
      .filter(_.nonEmpty)
      .map { pair =>
        val separator: Int = pair.indexOf('=')
        val rawName: String = if (separator >= 0) pair.substring(0, separator) else pair
        val rawValue: String = if (separator >= 0) pair.substring(separator + 1) else ""
        decode(rawName) -> decode(rawValue)
      }
      .groupMap(_._1)(_._2)
  }

  private def decode(value: String): String = {
    URLDecoder.decode(value, StandardCharsets.UTF_8)
  }

  private def gzip(bytes: Array[Byte]): Array[Byte] = {
    val output = new ByteArrayOutputStream()
    val gzipOutput = new GZIPOutputStream(output)
    try gzipOutput.write(bytes)
    finally gzipOutput.close()
    output.toByteArray
  }

  private def gunzip(bytes: Array[Byte]): Array[Byte] = {
    val input = new GZIPInputStream(new ByteArrayInputStream(bytes))
    try input.readAllBytes()
    finally input.close()
  }
}
