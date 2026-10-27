/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_softwaremill_sttp_client4.core_3

import com.sun.net.httpserver.HttpExchange
import com.sun.net.httpserver.HttpServer
import java.io.ByteArrayOutputStream
import java.net.InetSocketAddress
import java.nio.charset.StandardCharsets.UTF_8
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.zip.GZIPOutputStream
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Timeout
import scala.concurrent.duration.*
import scala.jdk.CollectionConverters.*
import sttp.client4.*
import sttp.client4.httpclient.HttpClientSyncBackend
import sttp.model.StatusCode

/** Exercises HTTP response decompression when retrieving a gzip-compressed catalog and a compressed service error. */
class CatalogCompressionTest {
  @Test
  @Timeout(60)
  def decodesCompressedCatalogAndErrorAsUtf8Text(): Unit = {
    val catalog: String = "sku,name,price\nC17,Caf\u00e9 beans,12.50\nT21,Th\u00e9 vert,8.00\n"
    val unavailable: String = "Catalog refresh in progress; r\u00e9essayez plus tard"
    val acceptedEncodings: ConcurrentLinkedQueue[String] = new ConcurrentLinkedQueue[String]()
    val server: HttpServer = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0)
    try {
      server.createContext("/catalog/current", (exchange: HttpExchange) => {
        acceptedEncodings.add(exchange.getRequestHeaders.getFirst("Accept-Encoding"))
        respondCompressed(exchange, 200, catalog)
      })
      server.createContext("/catalog/upcoming", (exchange: HttpExchange) => {
        acceptedEncodings.add(exchange.getRequestHeaders.getFirst("Accept-Encoding"))
        respondCompressed(exchange, 503, unavailable)
      })
      server.start()

      val backend: WebSocketSyncBackend = HttpClientSyncBackend(BackendOptions.connectionTimeout(10.seconds))
      try {
        val port: Int = server.getAddress.getPort
        val catalogRequest = basicRequest
          .header("Accept-Encoding", "gzip")
          .readTimeout(20.seconds)

        val current: Response[Either[String, String]] = catalogRequest
          .get(uri"http://127.0.0.1:$port/catalog/current")
          .send(backend)

        assertEquals(StatusCode.Ok, current.code)
        assertEquals(Right(catalog), current.body)

        val upcoming: Response[Either[String, String]] = catalogRequest
          .get(uri"http://127.0.0.1:$port/catalog/upcoming")
          .send(backend)

        assertEquals(StatusCode.ServiceUnavailable, upcoming.code)
        assertEquals(Left(unavailable), upcoming.body)
        assertEquals(List("gzip", "gzip"), acceptedEncodings.iterator().asScala.toList)
      } finally backend.close()
    } finally server.stop(0)
  }

  private def respondCompressed(exchange: HttpExchange, status: Int, text: String): Unit = {
    try {
      val buffer: ByteArrayOutputStream = new ByteArrayOutputStream()
      val gzip: GZIPOutputStream = new GZIPOutputStream(buffer)
      try gzip.write(text.getBytes(UTF_8))
      finally gzip.close()
      val bytes: Array[Byte] = buffer.toByteArray
      exchange.getResponseHeaders.set("Content-Type", "text/plain; charset=UTF-8")
      exchange.getResponseHeaders.set("Content-Encoding", "gzip")
      exchange.sendResponseHeaders(status, bytes.length.toLong)
      exchange.getResponseBody.write(bytes)
    } finally exchange.close()
  }
}
