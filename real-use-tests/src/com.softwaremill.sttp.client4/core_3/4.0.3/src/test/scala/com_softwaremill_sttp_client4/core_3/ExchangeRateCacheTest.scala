/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_softwaremill_sttp_client4.core_3

import com.sun.net.httpserver.HttpExchange
import com.sun.net.httpserver.HttpServer
import java.net.InetSocketAddress
import java.nio.charset.StandardCharsets.UTF_8
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.atomic.AtomicReference
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Timeout
import scala.concurrent.duration.*
import scala.jdk.CollectionConverters.*
import sttp.client4.*
import sttp.client4.httpclient.HttpClientSyncBackend
import sttp.model.StatusCode
import sttp.model.Uri

/** Exercises metadata-driven conditional response handling for an ETag-validated exchange-rate cache. */
class ExchangeRateCacheTest {
  private case class PublishedRates(etag: String, text: String)
  private case class RateSheet(etag: String, rates: Map[String, BigDecimal])
  private case class Revalidation(validator: Option[String], status: Int)

  @Test
  @Timeout(60)
  def reusesUnchangedRatesAndReplacesThemAfterPublication(): Unit = {
    val published: AtomicReference[PublishedRates] = new AtomicReference(
      PublishedRates("\"rates-17\"", "EUR=0.92\nGBP=0.79\n")
    )
    val requests: ConcurrentLinkedQueue[Revalidation] = new ConcurrentLinkedQueue[Revalidation]()
    val server: HttpServer = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0)
    try {
      server.createContext("/rates/USD", (exchange: HttpExchange) => {
        try {
          val current: PublishedRates = published.get()
          val validator: Option[String] = Option(exchange.getRequestHeaders.getFirst("If-None-Match"))
          val status: Int = if (validator.contains(current.etag)) 304 else 200
          requests.add(Revalidation(validator, status))
          exchange.getResponseHeaders.set("ETag", current.etag)
          exchange.getResponseHeaders.set("Cache-Control", "no-cache")
          if (status == 304) exchange.sendResponseHeaders(status, -1)
          else {
            val bytes: Array[Byte] = current.text.getBytes(UTF_8)
            exchange.getResponseHeaders.set("Content-Type", "text/plain; charset=UTF-8")
            exchange.sendResponseHeaders(status, bytes.length.toLong)
            exchange.getResponseBody.write(bytes)
          }
        } finally exchange.close()
      })
      server.start()

      val backend: WebSocketSyncBackend = HttpClientSyncBackend(BackendOptions.connectionTimeout(10.seconds))
      try {
        val port: Int = server.getAddress.getPort
        val client: RateClient = new RateClient(backend, uri"http://127.0.0.1:$port/rates/USD")
        val initial: Map[String, BigDecimal] = Map("EUR" -> BigDecimal("0.92"), "GBP" -> BigDecimal("0.79"))
        assertEquals(initial, client.refresh())
        assertEquals(initial, client.refresh(), "A bodyless 304 must preserve the cached rate sheet")

        published.set(PublishedRates("\"rates-18\"", "EUR=0.94\nGBP=0.81\nJPY=149.50\n"))
        val updated: Map[String, BigDecimal] = Map(
          "EUR" -> BigDecimal("0.94"), "GBP" -> BigDecimal("0.81"), "JPY" -> BigDecimal("149.50")
        )
        assertEquals(updated, client.refresh())
        assertEquals(updated, client.refresh(), "Revalidation must use the new ETag and keep the new rates")
        assertEquals(
          List(
            Revalidation(None, 200),
            Revalidation(Some("\"rates-17\""), 304),
            Revalidation(Some("\"rates-17\""), 200),
            Revalidation(Some("\"rates-18\""), 304)
          ),
          requests.iterator().asScala.toList
        )
      } finally backend.close()
    } finally server.stop(0)
  }

  private class RateClient(backend: SyncBackend, endpoint: Uri) {
    private var cached: Option[RateSheet] = None

    def refresh(): Map[String, BigDecimal] = {
      val decodeRates: ResponseAs[Option[RateSheet]] = asStringOrFail.mapWithMetadata { (text, metadata) =>
        val etag: String = metadata.header("ETag").getOrElse(
          throw new IllegalStateException("The rate service did not supply an ETag")
        )
        val rates: Map[String, BigDecimal] = text.linesIterator.map { line =>
          val fields: Array[String] = line.split("=", 2)
          fields(0) -> BigDecimal(fields(1))
        }.toMap
        Some(RateSheet(etag, rates))
      }
      val handling: ResponseAs[Option[RateSheet]] = fromMetadata(
        decodeRates,
        ConditionalResponseAs(_.code == StatusCode.NotModified, ignore.map(_ => Option.empty[RateSheet]))
      )
      val request = basicRequest.get(endpoint).readTimeout(10.seconds).response(handling)
      val conditional = cached.fold(request)(sheet => request.header("If-None-Match", sheet.etag))
      val response: Response[Option[RateSheet]] = conditional.send(backend)
      cached = response.body.orElse(cached)
      cached.getOrElse(throw new IllegalStateException("Received 304 without cached exchange rates")).rates
    }
  }
}
