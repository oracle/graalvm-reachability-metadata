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
import sttp.client4.*
import sttp.client4.httpclient.HttpClientSyncBackend
import sttp.model.Method
import sttp.model.StatusCode

/** Exercises HTTP redirect handling for a form submission followed by a relative confirmation-page redirect. */
class OrderRedirectTest {
  @Test
  @Timeout(60)
  def submitsOrderAndFollowsSeeOtherAsGet(): Unit = {
    val received: ConcurrentLinkedQueue[ReceivedRequest] = new ConcurrentLinkedQueue[ReceivedRequest]()
    val server: HttpServer = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0)
    try {
      server.createContext("/orders", (exchange: HttpExchange) => {
        try {
          received.add(capture(exchange))
          exchange.getResponseHeaders.set("Location", "/receipts/A17?view=summary")
          exchange.sendResponseHeaders(303, -1)
        } finally exchange.close()
      })
      server.createContext("/receipts/A17", (exchange: HttpExchange) => {
        try {
          received.add(capture(exchange))
          val confirmation: Array[Byte] = "Order A17 confirmed: 2 boxes of tea".getBytes(UTF_8)
          exchange.getResponseHeaders.set("Content-Type", "text/plain; charset=UTF-8")
          exchange.sendResponseHeaders(200, confirmation.length.toLong)
          exchange.getResponseBody.write(confirmation)
        } finally exchange.close()
      })
      server.start()

      val backend: WebSocketSyncBackend = HttpClientSyncBackend(BackendOptions.connectionTimeout(10.seconds))
      try {
        val port: Int = server.getAddress.getPort
        val response: Response[Either[String, String]] = basicRequest
          .body("item" -> "tea", "quantity" -> "2")
          .post(uri"http://127.0.0.1:$port/orders")
          .followRedirects(true)
          .maxRedirects(3)
          .readTimeout(20.seconds)
          .send(backend)

        assertEquals(StatusCode.Ok, response.code)
        assertEquals(Right("Order A17 confirmed: 2 boxes of tea"), response.body)
        assertEquals(List(StatusCode.SeeOther), response.history.map(_.code))
        assertEquals(Some("/receipts/A17?view=summary"), response.history.head.header("Location"))
        assertEquals(Method.GET, response.request.method)
        assertEquals(uri"http://127.0.0.1:$port/receipts/A17?view=summary", response.request.uri)
        assertEquals(
          List(
            ReceivedRequest("POST", "/orders", "item=tea&quantity=2"),
            ReceivedRequest("GET", "/receipts/A17?view=summary", "")
          ),
          received.iterator().asScala.toList
        )
      } finally backend.close()
    } finally server.stop(0)
  }

  private case class ReceivedRequest(method: String, target: String, body: String)

  private def capture(exchange: HttpExchange): ReceivedRequest = {
    val body: String = new String(exchange.getRequestBody.readAllBytes(), UTF_8)
    ReceivedRequest(exchange.getRequestMethod, exchange.getRequestURI.toString, body)
  }
}
