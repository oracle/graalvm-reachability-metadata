/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_playframework.play_server_3

import java.net.URI
import java.net.http.HttpClient
import java.net.http.WebSocket
import java.time.Duration
import java.util.concurrent.CompletableFuture
import java.util.concurrent.CompletionStage
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.TimeUnit
import org.apache.pekko.stream.scaladsl.Flow
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Timeout
import play.api.Mode
import play.api.mvc.WebSocket as PlayWebSocket
import play.core.server.Server
import play.core.server.ServerConfig

/** Exercises WebSocket upgrades and bidirectional text streaming for a stock subscription. */
class StockWebSocketServerTest {
  @Test
  @Timeout(60)
  def exchangesSubscriptionMessagesAndClosesNormally(): Unit = {
    val config: ServerConfig = ServerConfig(
      port = Some(0),
      address = "127.0.0.1",
      mode = Mode.Test
    )

    Server.withRouterFromComponents(config) { _ =>
      {
        case request if request.method == "GET" && request.path == "/stock-updates" =>
          PlayWebSocket.accept[String, String] { _ =>
            Flow[String].map {
              case command if command.startsWith("WATCH ") =>
                s"stock:${command.stripPrefix("WATCH ")}:12"
              case "PING" => "PONG"
              case _ => "error:unknown-command"
            }
          }
      }
    } { port =>
      val client: HttpClient = HttpClient.newBuilder()
        .connectTimeout(Duration.ofSeconds(10))
        .build()
      try {
        val listener: StockListener = new StockListener()
        val socket: WebSocket = client.newWebSocketBuilder()
          .connectTimeout(Duration.ofSeconds(10))
          .buildAsync(URI.create(s"ws://127.0.0.1:${port.value}/stock-updates"), listener)
          .get(10, TimeUnit.SECONDS)
        try {
          // A single application command may arrive in several WebSocket frames.
          socket.sendText("WATCH ", false).get(10, TimeUnit.SECONDS)
          socket.sendText("notebook", true).get(10, TimeUnit.SECONDS)
          assertEquals("stock:notebook:12", listener.messages.poll(10, TimeUnit.SECONDS))

          socket.sendText("PING", true).get(10, TimeUnit.SECONDS)
          assertEquals("PONG", listener.messages.poll(10, TimeUnit.SECONDS))

          socket.sendClose(WebSocket.NORMAL_CLOSURE, "subscription complete")
            .get(10, TimeUnit.SECONDS)
          assertEquals(WebSocket.NORMAL_CLOSURE, listener.closed.get(10, TimeUnit.SECONDS).intValue())
        } finally {
          socket.abort()
        }
      } finally {
        client.close()
      }
    }
  }

  private class StockListener extends WebSocket.Listener {
    val messages: LinkedBlockingQueue[String] = new LinkedBlockingQueue[String]()
    val closed: CompletableFuture[Integer] = new CompletableFuture[Integer]()
    private val pending: StringBuilder = new StringBuilder()

    override def onOpen(socket: WebSocket): Unit = {
      socket.request(1)
    }

    override def onText(socket: WebSocket, data: CharSequence, last: Boolean): CompletionStage[?] = {
      pending.append(data)
      if (last) {
        messages.add(pending.toString())
        pending.clear()
      }
      socket.request(1)
      CompletableFuture.completedFuture[Void](null)
    }

    override def onClose(socket: WebSocket, statusCode: Int, reason: String): CompletionStage[?] = {
      closed.complete(Integer.valueOf(statusCode))
      CompletableFuture.completedFuture[Void](null)
    }

    override def onError(socket: WebSocket, error: Throwable): Unit = {
      closed.completeExceptionally(error)
    }
  }
}
