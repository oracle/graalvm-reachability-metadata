/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_http4s.http4s_ember_server_3

import cats.effect.IO
import cats.effect.Resource
import cats.effect.unsafe.implicits.global
import com.comcast.ip4s.Host
import com.comcast.ip4s.Port
import fs2.Pipe
import org.http4s.HttpRoutes
import org.http4s.dsl.Http4sDsl
import org.http4s.ember.server.EmberServerBuilder
import org.http4s.implicits.*
import org.http4s.websocket.WebSocketFrame
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Timeout
import org.typelevel.log4cats.LoggerFactory
import org.typelevel.log4cats.slf4j.Slf4jFactory

import java.net.URI
import java.net.http.HttpClient
import java.net.http.WebSocket
import java.time.Duration
import java.util.concurrent.CompletableFuture
import java.util.concurrent.CompletionStage
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.TimeUnit
import scala.concurrent.duration.*

/** Exercises Ember's WebSocket upgrade and frame exchange subsystem for live order tracking. */
class WebSocketOrderTrackingTest extends Http4sDsl[IO] {
  private given LoggerFactory[IO] = Slf4jFactory.create[IO]

  private class TrackingListener extends WebSocket.Listener {
    val messages: LinkedBlockingQueue[String] = new LinkedBlockingQueue[String]()
    val closed: CompletableFuture[(Int, String)] = new CompletableFuture[(Int, String)]()
    private val fragments: StringBuilder = new StringBuilder()

    override def onOpen(socket: WebSocket): Unit = socket.request(1)

    override def onText(socket: WebSocket, data: CharSequence, last: Boolean): CompletionStage[?] = {
      fragments.append(data)
      if (last) {
        messages.add(fragments.toString)
        fragments.clear()
      }
      socket.request(1)
      CompletableFuture.completedFuture[Void](null)
    }

    override def onClose(socket: WebSocket, statusCode: Int, reason: String): CompletionStage[?] = {
      closed.complete((statusCode, reason))
      CompletableFuture.completedFuture[Void](null)
    }

    override def onError(socket: WebSocket, error: Throwable): Unit = {
      closed.completeExceptionally(error)
      ()
    }
  }

  @Test
  @Timeout(60)
  def tracksMultipleOrdersOverOneWebSocketSession(): Unit = {
    val orders: Map[String, String] = Map("order-17" -> "packed", "order-28" -> "dispatched")
    val tracking: Pipe[IO, WebSocketFrame, WebSocketFrame] = _.collect {
      case WebSocketFrame.Text(orderId, _) =>
        WebSocketFrame.Text(s"$orderId:${orders.getOrElse(orderId, "not-found")}")
    }
    val program: IO[Unit] = EmberServerBuilder.default[IO]
      .withHost(Host.fromString("127.0.0.1").get)
      .withPort(Port.fromInt(0).get)
      .withHttpWebSocketApp { builder =>
        HttpRoutes.of[IO] {
          case GET -> Root / "tracking" => builder.build(tracking)
        }.orNotFound
      }
      .withShutdownTimeout(10.seconds)
      .build.use { server =>
        Resource.make(IO.blocking {
          HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(15)).build()
        })(client => IO.blocking(client.close())).use { client =>
          val listener: TrackingListener = new TrackingListener()
          val address: URI = URI.create(s"ws://127.0.0.1:${server.address.port.value}/tracking")
          Resource.make(IO.blocking {
            client.newWebSocketBuilder()
              .connectTimeout(Duration.ofSeconds(15))
              .buildAsync(address, listener)
              .get(15, TimeUnit.SECONDS)
          })(socket => IO(socket.abort())).use { socket =>
            for {
              _ <- IO.blocking {
                socket.sendText("order-17", true).get(15, TimeUnit.SECONDS)
                assertEquals("order-17:packed", listener.messages.poll(15, TimeUnit.SECONDS))
                socket.sendText("order-28", true).get(15, TimeUnit.SECONDS)
                assertEquals("order-28:dispatched", listener.messages.poll(15, TimeUnit.SECONDS))
                socket.sendText("order-missing", true).get(15, TimeUnit.SECONDS)
                assertEquals("order-missing:not-found", listener.messages.poll(15, TimeUnit.SECONDS))
                socket.sendClose(WebSocket.NORMAL_CLOSURE, "finished").get(15, TimeUnit.SECONDS)
                val close: (Int, String) = listener.closed.get(15, TimeUnit.SECONDS)
                assertEquals(WebSocket.NORMAL_CLOSURE, close._1)
              }
            } yield ()
          }
        }
      }

    program.timeout(50.seconds).unsafeRunSync()
  }
}
