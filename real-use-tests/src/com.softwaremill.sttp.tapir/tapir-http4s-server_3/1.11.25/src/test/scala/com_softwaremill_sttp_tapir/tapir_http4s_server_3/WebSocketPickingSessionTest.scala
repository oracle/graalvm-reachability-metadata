/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_softwaremill_sttp_tapir.tapir_http4s_server_3

import cats.effect.{IO, Resource}
import cats.effect.unsafe.implicits.global
import cats.syntax.all.*
import com.comcast.ip4s.*
import fs2.Pipe
import org.http4s.ember.server.EmberServerBuilder
import org.http4s.implicits.{path as _, *}
import org.junit.jupiter.api.Assertions.{assertEquals, assertNotNull}
import org.junit.jupiter.api.{Test, Timeout}
import sttp.capabilities.WebSockets
import sttp.capabilities.fs2.Fs2Streams
import sttp.tapir.*
import sttp.tapir.server.http4s.Http4sServerInterpreter

import java.net.URI
import java.net.http.{HttpClient, WebSocket}
import java.time.Duration
import java.util.concurrent.{CompletableFuture, CompletionStage, LinkedBlockingQueue, TimeUnit}
import scala.concurrent.duration.*

/** Exercises WebSocket upgrades and stateful bidirectional message streams in Tapir's http4s interpreter. */
class WebSocketPickingSessionTest {
  @Test
  @Timeout(60)
  def acknowledgesScannerCommandsIncludingAFragmentedMessageAndClosesCleanly(): Unit = {
    val session: PublicEndpoint[String, Unit, Pipe[IO, String, String], Fs2Streams[IO] & WebSockets] =
      endpoint.get
        .in("picking" / path[String]("batch"))
        .out(webSocketBody[String, CodecFormat.TextPlain, String, CodecFormat.TextPlain](Fs2Streams[IO]))
    val routes = Http4sServerInterpreter[IO]().toWebSocketRoutes(
      session.serverLogicSuccess[IO] { (batch: String) =>
        val commands: Pipe[IO, String, String] = input => input.mapAccumulate(0) { (picked, command) =>
          val next: Int = command match {
            case "SCAN coffee" => picked + 1
            case "UNDO" => math.max(0, picked - 1)
            case "TOTAL" => picked
            case other => throw new IllegalArgumentException(s"Unknown scanner command: $other")
          }
          (next, s"$batch:picked=$next")
        }.map(_._2)
        IO.pure(commands)
      }
    )
    val server = EmberServerBuilder.default[IO]
      .withHost(ipv4"127.0.0.1")
      .withPort(port"0")
      .withHttpWebSocketApp(builder => routes(builder).orNotFound)
      .build
    val client: Resource[IO, HttpClient] = Resource.fromAutoCloseable(IO {
      HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(15)).build()
    })

    val scenario: IO[Unit] = (server, client).tupled.use { (running, http) =>
      val listener: ScannerListener = new ScannerListener
      val address: URI = URI.create(s"ws://127.0.0.1:${running.address.getPort}/picking/batch-17")
      val socket: Resource[IO, WebSocket] = Resource.make(
        IO.fromCompletableFuture(IO {
          http.newWebSocketBuilder().connectTimeout(Duration.ofSeconds(15)).buildAsync(address, listener)
        })
      )(ws => IO(ws.abort()))

      socket.use { ws =>
        for {
          _ <- IO.fromCompletableFuture(IO(ws.sendText("SCAN ", false)))
          _ <- IO.fromCompletableFuture(IO(ws.sendText("coffee", true)))
          first <- listener.nextMessage
          _ <- IO(assertEquals("batch-17:picked=1", first))
          _ <- IO.fromCompletableFuture(IO(ws.sendText("SCAN coffee", true)))
          second <- listener.nextMessage
          _ <- IO(assertEquals("batch-17:picked=2", second))
          _ <- IO.fromCompletableFuture(IO(ws.sendText("UNDO", true)))
          corrected <- listener.nextMessage
          _ <- IO(assertEquals("batch-17:picked=1", corrected))
          _ <- IO.fromCompletableFuture(IO(ws.sendText("TOTAL", true)))
          total <- listener.nextMessage
          _ <- IO(assertEquals("batch-17:picked=1", total))
          _ <- IO.fromCompletableFuture(IO(ws.sendClose(WebSocket.NORMAL_CLOSURE, "Picking complete")))
          closeCode <- IO.fromCompletableFuture(IO(listener.closed))
          _ <- IO(assertEquals(WebSocket.NORMAL_CLOSURE, closeCode))
        } yield ()
      }
    }

    scenario.timeout(45.seconds).unsafeRunSync()
  }

  private final class ScannerListener extends WebSocket.Listener {
    private val messages: LinkedBlockingQueue[Either[Throwable, String]] = new LinkedBlockingQueue
    private val fragments: StringBuilder = new StringBuilder
    val closed: CompletableFuture[Int] = new CompletableFuture[Int]

    override def onOpen(socket: WebSocket): Unit = socket.request(1)

    override def onText(socket: WebSocket, data: CharSequence, last: Boolean): CompletionStage[?] = {
      fragments.append(data)
      if (last) {
        messages.add(Right(fragments.toString))
        fragments.clear()
      }
      socket.request(1)
      CompletableFuture.completedFuture[Void](null)
    }

    override def onClose(socket: WebSocket, code: Int, reason: String): CompletionStage[?] = {
      closed.complete(code)
      CompletableFuture.completedFuture[Void](null)
    }

    override def onError(socket: WebSocket, error: Throwable): Unit = {
      messages.add(Left(error))
      closed.completeExceptionally(error)
    }

    def nextMessage: IO[String] = IO.blocking {
      val message: Either[Throwable, String] = messages.poll(15, TimeUnit.SECONDS)
      assertNotNull(message, "The scanner must receive an acknowledgement")
      message.fold(error => throw error, identity)
    }
  }
}
