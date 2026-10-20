/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_softwaremill_sttp_client4.core_3

import com.sun.net.httpserver.HttpExchange
import com.sun.net.httpserver.HttpServer
import java.net.HttpURLConnection
import java.net.InetSocketAddress
import java.net.Proxy
import java.nio.charset.StandardCharsets.UTF_8
import java.util.concurrent.ConcurrentLinkedQueue
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Timeout
import scala.concurrent.duration.*
import scala.jdk.CollectionConverters.*
import sttp.client4.*
import sttp.client4.httpclient.HttpClientSyncBackend
import sttp.model.StatusCode

/** Exercises HTTP proxy routing and host exclusions for an inventory client behind an outbound gateway. */
class InventoryProxyRoutingTest {
  @Test
  @Timeout(60)
  def forwardsStockRequestsAndBypassesTheGatewayForAnExcludedHost(): Unit = {
    val forwarded: ConcurrentLinkedQueue[String] = new ConcurrentLinkedQueue[String]()
    val received: ConcurrentLinkedQueue[String] = new ConcurrentLinkedQueue[String]()
    val origin: HttpServer = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0)
    val gateway: HttpServer = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0)
    try {
      origin.createContext("/", (exchange: HttpExchange) => {
        val target: String = exchange.getRequestURI.toString
        received.add(s"${exchange.getRequestMethod} $target")
        target match {
          case "/stock/tea?depot=west" => respond(exchange, 200, "tea=42;depot=west")
          case "/health" => respond(exchange, 200, "inventory ready")
          case _ => respond(exchange, 404, "Unknown inventory item")
        }
      })
      val originPort: Int = origin.getAddress.getPort
      gateway.createContext("/", (exchange: HttpExchange) => {
        forwarded.add(s"${exchange.getRequestMethod} ${exchange.getRequestURI}")
        forward(exchange, originPort)
      })
      origin.start()
      gateway.start()

      val proxyOptions: BackendOptions = BackendOptions
        .httpProxy("127.0.0.1", gateway.getAddress.getPort)
        .connectionTimeout(10.seconds)
      val backend: WebSocketSyncBackend = HttpClientSyncBackend(proxyOptions)
      try {
        val stock: Response[Either[String, String]] = basicRequest
          .get(uri"http://127.0.0.1:$originPort/stock/tea?depot=west")
          .readTimeout(10.seconds)
          .send(backend)
        val missing: Response[Either[String, String]] = basicRequest
          .get(uri"http://127.0.0.1:$originPort/stock/unknown")
          .readTimeout(10.seconds)
          .send(backend)

        assertEquals(StatusCode.Ok, stock.code)
        assertEquals(Right("tea=42;depot=west"), stock.body)
        assertEquals(Some("1.1 inventory-gateway"), stock.header("Via"))
        assertEquals(StatusCode.NotFound, missing.code)
        assertEquals(Left("Unknown inventory item"), missing.body)
        assertEquals(Some("1.1 inventory-gateway"), missing.header("Via"))
      } finally backend.close()

      val directOptions: BackendOptions = proxyOptions.copy(
        proxy = proxyOptions.proxy.map(_.copy(nonProxyHosts = List("127.*")))
      )
      val directBackend: WebSocketSyncBackend = HttpClientSyncBackend(directOptions)
      try {
        val health: Response[Either[String, String]] = basicRequest
          .get(uri"http://127.0.0.1:$originPort/health")
          .readTimeout(10.seconds)
          .send(directBackend)
        assertEquals(StatusCode.Ok, health.code)
        assertEquals(Right("inventory ready"), health.body)
        assertEquals(None, health.header("Via"))
      } finally directBackend.close()

      assertEquals(
        List(
          s"GET http://127.0.0.1:$originPort/stock/tea?depot=west",
          s"GET http://127.0.0.1:$originPort/stock/unknown"
        ),
        forwarded.iterator().asScala.toList
      )
      assertEquals(
        List("GET /stock/tea?depot=west", "GET /stock/unknown", "GET /health"),
        received.iterator().asScala.toList
      )
    } finally {
      gateway.stop(0)
      origin.stop(0)
    }
  }

  private def forward(exchange: HttpExchange, originPort: Int): Unit = {
    try {
      val target = exchange.getRequestURI
      require(target.getHost == "127.0.0.1" && target.getPort == originPort, "Unexpected proxy destination")
      val connection: HttpURLConnection = target.toURL.openConnection(Proxy.NO_PROXY).asInstanceOf[HttpURLConnection]
      try {
        connection.setConnectTimeout(10000)
        connection.setReadTimeout(10000)
        connection.setInstanceFollowRedirects(false)
        val status: Int = connection.getResponseCode
        val stream = if (status >= 400) connection.getErrorStream else connection.getInputStream
        val body: Array[Byte] = try stream.readAllBytes() finally stream.close()
        exchange.getResponseHeaders.set("Via", "1.1 inventory-gateway")
        respond(exchange, status, new String(body, UTF_8))
      } finally connection.disconnect()
    } finally exchange.close()
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
