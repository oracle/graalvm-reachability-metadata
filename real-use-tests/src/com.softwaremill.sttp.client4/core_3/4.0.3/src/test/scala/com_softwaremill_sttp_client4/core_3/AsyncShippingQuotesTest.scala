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
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Timeout
import scala.concurrent.Await
import scala.concurrent.ExecutionContext
import scala.concurrent.Future
import scala.concurrent.duration.*
import sttp.client4.*
import sttp.client4.httpclient.HttpClientFutureBackend

/** Exercises the asynchronous Future backend by aggregating shipping quotes despite an unavailable delivery service. */
class AsyncShippingQuotesTest {
  private case class ShippingOptions(prices: Map[String, BigDecimal], unavailable: Map[String, String])

  @Test
  @Timeout(60)
  def combinesIndependentQuoteRequestsWithoutDiscardingSuccessfulOptions(): Unit = {
    val executor: ExecutorService = Executors.newFixedThreadPool(4)
    given ExecutionContext = ExecutionContext.fromExecutor(executor)
    val server: HttpServer = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0)
    try {
      server.createContext("/shipping/standard", (exchange: HttpExchange) => respond(exchange, 200, "4.50"))
      server.createContext("/shipping/express", (exchange: HttpExchange) => respond(exchange, 200, "9.75"))
      server.createContext("/shipping/same-day", (exchange: HttpExchange) => {
        respond(exchange, 503, "No couriers available")
      })
      server.start()

      val backend: WebSocketBackend[Future] = HttpClientFutureBackend(BackendOptions.connectionTimeout(10.seconds))
      try {
        val port: Int = server.getAddress.getPort
        val services: List[String] = List("standard", "express", "same-day")
        val pending: List[Future[(String, Response[Either[String, String]])]] = services.map { service =>
          basicRequest
            .get(uri"http://127.0.0.1:$port/shipping/$service")
            .readTimeout(20.seconds)
            .send(backend)
            .map(response => service -> response)
        }
        val checkout: Future[ShippingOptions] = Future.sequence(pending).map { responses =>
          val prices: Map[String, BigDecimal] = responses.flatMap { case (service, response) =>
            response.body.toOption.map(price => service -> BigDecimal(price))
          }.toMap
          val unavailable: Map[String, String] = responses.flatMap { case (service, response) =>
            response.body.swap.toOption.map(message => service -> message)
          }.toMap
          ShippingOptions(prices, unavailable)
        }

        val options: ShippingOptions = Await.result(checkout, 30.seconds)
        assertEquals(Map("standard" -> BigDecimal("4.50"), "express" -> BigDecimal("9.75")), options.prices)
        assertEquals(Map("same-day" -> "No couriers available"), options.unavailable)
        assertEquals("standard", options.prices.minBy(_._2)._1)

      } finally Await.result(backend.close(), 10.seconds)
    } finally {
      server.stop(0)
      executor.shutdownNow()
      assertTrue(executor.awaitTermination(10, TimeUnit.SECONDS), "Quote callbacks must terminate")
    }
  }

  private def respond(exchange: HttpExchange, status: Int, text: String): Unit = {
    try {
      val bytes: Array[Byte] = text.getBytes(UTF_8)
      exchange.getResponseHeaders.set("Content-Type", "text/plain; charset=UTF-8")
      exchange.sendResponseHeaders(status, bytes.length.toLong)
      exchange.getResponseBody.write(bytes)
    } finally exchange.close()
  }
}
