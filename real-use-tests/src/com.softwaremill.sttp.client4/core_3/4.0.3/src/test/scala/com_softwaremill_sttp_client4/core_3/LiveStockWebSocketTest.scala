/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_softwaremill_sttp_client4.core_3

import java.net.InetSocketAddress
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import org.java_websocket.WebSocket
import org.java_websocket.handshake.ClientHandshake
import org.java_websocket.server.WebSocketServer
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Timeout
import scala.concurrent.duration.*
import sttp.client4.*
import sttp.client4.httpclient.HttpClientSyncBackend
import sttp.client4.ws.SyncWebSocket
import sttp.client4.ws.sync.*
import sttp.model.StatusCode

/** Exercises synchronous WebSocket sessions by subscribing to live stock updates and acknowledging the resulting view. */
class LiveStockWebSocketTest {
  private case class StockView(updates: Vector[String], quantities: Map[String, Int], acknowledgement: String)

  @Test
  @Timeout(60)
  def subscribesToStockFeedAndAcknowledgesLatestQuantities(): Unit = {
    val server: StockFeedServer = new StockFeedServer()
    server.start()
    try {
      assertTrue(server.started.await(10, TimeUnit.SECONDS), "Stock feed must start")
      assertTrue(server.errors.isEmpty, s"Stock feed startup failed: ${server.errors}")
      val backend: WebSocketSyncBackend = HttpClientSyncBackend(BackendOptions.connectionTimeout(10.seconds))
      try {
        val port: Int = server.getPort
        val response: Response[Either[String, StockView]] = basicRequest
          .get(uri"ws://127.0.0.1:$port/stock/live")
          .readTimeout(20.seconds)
          .response(asWebSocket { (socket: SyncWebSocket) =>
            socket.sendText("SUBSCRIBE warehouse-west")
            assertEquals("SUBSCRIBED warehouse-west", socket.receiveText())
            val updates: Vector[String] = Vector.fill(3)(socket.receiveText())
            val quantities: Map[String, Int] = updates.foldLeft(Map.empty[String, Int]) { (stock, update) =>
              val fields: Array[String] = update.split("=", 2)
              stock.updated(fields(0), fields(1).toInt)
            }
            socket.sendText(s"ACK tea=${quantities("tea")};coffee=${quantities("coffee")}")
            StockView(updates, quantities, socket.receiveText())
          })
          .send(backend)

        assertEquals(StatusCode.SwitchingProtocols, response.code)
        assertEquals(
          Right(StockView(
            Vector("tea=12", "coffee=7", "tea=9"),
            Map("tea" -> 9, "coffee" -> 7),
            "ACKNOWLEDGED tea=9;coffee=7"
          )),
          response.body
        )
        assertTrue(server.disconnected.await(10, TimeUnit.SECONDS), "The managed WebSocket session must close")
        assertTrue(server.errors.isEmpty, s"Stock feed failed: ${server.errors}")
      } finally backend.close()
    } finally server.stop(10000)
  }

  private class StockFeedServer extends WebSocketServer(new InetSocketAddress("127.0.0.1", 0), 1) {
    val started: CountDownLatch = new CountDownLatch(1)
    val disconnected: CountDownLatch = new CountDownLatch(1)
    val errors: ConcurrentLinkedQueue[Exception] = new ConcurrentLinkedQueue[Exception]()

    override def onStart(): Unit = started.countDown()

    override def onOpen(connection: WebSocket, handshake: ClientHandshake): Unit = {
      if (handshake.getResourceDescriptor != "/stock/live") connection.close(1008, "Unknown feed")
    }

    override def onMessage(connection: WebSocket, message: String): Unit = {
      message match {
        case "SUBSCRIBE warehouse-west" =>
          connection.send("SUBSCRIBED warehouse-west")
          connection.send("tea=12")
          connection.send("coffee=7")
          connection.send("tea=9")
        case "ACK tea=9;coffee=7" => connection.send("ACKNOWLEDGED tea=9;coffee=7")
        case _ => connection.close(1008, "Invalid stock feed command")
      }
    }

    override def onClose(connection: WebSocket, code: Int, reason: String, remote: Boolean): Unit = {
      disconnected.countDown()
    }

    override def onError(connection: WebSocket, error: Exception): Unit = {
      errors.add(error)
      started.countDown()
    }
  }
}
