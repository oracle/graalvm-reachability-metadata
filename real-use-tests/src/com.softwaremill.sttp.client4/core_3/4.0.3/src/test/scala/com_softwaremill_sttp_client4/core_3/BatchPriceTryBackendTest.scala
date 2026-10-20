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
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Timeout
import scala.concurrent.duration.*
import scala.jdk.CollectionConverters.*
import scala.util.Failure
import scala.util.Success
import scala.util.Try
import sttp.client4.*
import sttp.client4.httpclient.HttpClientSyncBackend
import sttp.client4.wrappers.TryBackend
import sttp.model.StatusCode

/** Exercises the Try backend adapter to collect batch prices without aborting on an individual HTTP failure. */
class BatchPriceTryBackendTest {
  private case class PriceFailure(status: StatusCode, explanation: String)
  private case class PriceBatch(prices: Map[String, BigDecimal], failures: Map[String, PriceFailure])

  @Test
  @Timeout(60)
  def collectsSuccessfulPricesAndReportsUnavailableItems(): Unit = {
    val requestedItems: ConcurrentLinkedQueue[String] = new ConcurrentLinkedQueue[String]()
    val server: HttpServer = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0)
    try {
      server.createContext("/prices/", (exchange: HttpExchange) => {
        val item: String = exchange.getRequestURI.getPath.stripPrefix("/prices/")
        requestedItems.add(item)
        item match {
          case "coffee" => respond(exchange, 200, "12.50")
          case "tea" => respond(exchange, 503, "Tea supplier is temporarily unavailable")
          case "cocoa" => respond(exchange, 200, "8.75")
          case _ => respond(exchange, 404, "Unknown item")
        }
      })
      server.start()

      val backend: Backend[Try] = TryBackend(HttpClientSyncBackend(BackendOptions.connectionTimeout(10.seconds)))
      try {
        val port: Int = server.getAddress.getPort
        val batch: PriceBatch = List("coffee", "tea", "cocoa").foldLeft(PriceBatch(Map.empty, Map.empty)) {
          (collected: PriceBatch, item: String) =>
            val price: Try[BigDecimal] = basicRequest
              .get(uri"http://127.0.0.1:$port/prices/$item")
              .readTimeout(20.seconds)
              .response(asStringOrFail)
              .send(backend)
              .map(response => BigDecimal(response.body))

            price match {
              case Success(amount) => collected.copy(prices = collected.prices.updated(item, amount))
              case Failure(error) =>
                ResponseException.find(error) match {
                  case Some(ResponseException.UnexpectedStatusCode(body: String, response)) =>
                    val failure: PriceFailure = PriceFailure(response.code, body)
                    collected.copy(failures = collected.failures.updated(item, failure))
                  case _ => throw error
                }
            }
        }

        assertEquals(Map("coffee" -> BigDecimal("12.50"), "cocoa" -> BigDecimal("8.75")), batch.prices)
        assertEquals(
          Map("tea" -> PriceFailure(StatusCode.ServiceUnavailable, "Tea supplier is temporarily unavailable")),
          batch.failures
        )
        assertEquals(List("coffee", "tea", "cocoa"), requestedItems.iterator().asScala.toList)
      } finally backend.close().get
    } finally server.stop(0)
  }

  private def respond(exchange: HttpExchange, status: Int, body: String): Unit = {
    try {
      val bytes: Array[Byte] = body.getBytes(UTF_8)
      exchange.getResponseHeaders.set("Content-Type", "text/plain; charset=UTF-8")
      exchange.sendResponseHeaders(status, bytes.length.toLong)
      exchange.getResponseBody.write(bytes)
    } finally exchange.close()
  }
}
