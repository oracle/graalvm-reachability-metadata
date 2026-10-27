/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_http4s.http4s_dsl_3

import cats.effect.Deferred
import cats.effect.IO
import cats.effect.Resource
import cats.effect.unsafe.implicits.global
import com.comcast.ip4s.*
import fs2.Pipe
import fs2.Stream
import java.net.URI
import java.net.http.HttpClient
import java.net.http.WebSocket
import java.net.http.WebSocketHandshakeException
import java.nio.ByteBuffer
import java.util.concurrent.CompletableFuture
import java.util.concurrent.CompletionException
import java.util.concurrent.CompletionStage
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.TimeUnit
import org.http4s.HttpRoutes
import org.http4s.Method
import org.http4s.Request
import org.http4s.Status
import org.http4s.Uri
import org.http4s.client.Client
import org.http4s.dsl.io.*
import org.http4s.ember.client.EmberClientBuilder
import org.http4s.ember.server.EmberServerBuilder
import org.http4s.implicits.*
import org.http4s.server.websocket.WebSocketBuilder2
import org.http4s.websocket.WebSocketFrame
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import scala.concurrent.duration.*

/**
 * WebSocket endpoints served from DSL routes through `WebSocketBuilder2` on an Ember server.
 *
 * A shoutbox exposes `/shout/<room>?nick=<name>` as a WebSocket route: the DSL matches the room
 * and the nickname before the upgrade, so a missing nickname is refused with `400` during the
 * handshake. Once upgraded, each text frame is shouted back numbered and upper-cased, a binary
 * frame is acknowledged with its size, and `/quit` makes the server send a close frame with code
 * `1000` and a farewell reason, after which the builder's `onClose` hook records the room. A
 * plain `GET` without the upgrade headers is answered `426 Upgrade Required` by the server. The
 * WebSocket client is the JDK `java.net.http.WebSocket`; the plain request uses an Ember client.
 */
class ShoutboxWebSocketTest {
  import ShoutboxWebSocketTest.*

  @Test
  def shoutboxEchoesFramesAndClosesOnQuit(): Unit = {
    val outcome: Observed = serveAndCall { (client: Client[IO], port: Int, closed: Deferred[IO, String]) =>
      val wsBase: String = s"ws://127.0.0.1:$port"
      for {
        session <- IO.blocking(
          converse(
            URI.create(s"$wsBase/shout/lobby?nick=ann"),
            List(Left("hello"), Right(Array[Byte](1, 2, 3, 4, 5)), Left("is anyone here?"), Left("/quit"))
          )
        )
        closedRoom <- closed.get.timeout(10.seconds)
        refused <- IO.blocking(handshakeStatus(URI.create(s"$wsBase/shout/lobby")))
        plain <- client.run(Request[IO](Method.GET, Uri.unsafeFromString(s"http://127.0.0.1:$port/shout/lobby?nick=ann")))
          .use(response => response.as[String].map(body => (response.status, body)))
      } yield Observed(session, closedRoom, refused, plain)
    }

    assertEquals(
      Session(
        List("welcome to lobby, ann", "#1 ann: HELLO", "ann sent 5 bytes", "#2 ann: IS ANYONE HERE?"),
        1000,
        "bye ann"
      ),
      outcome.session
    )
    assertEquals("lobby", outcome.closedRoom)
    assertEquals(400, outcome.refusedStatus)
    assertEquals((Status.UpgradeRequired, "Upgrade required for WebSocket communication."), outcome.plain)
  }
}

object ShoutboxWebSocketTest {

  /** The text messages a WebSocket client received and how the server closed the session. */
  final case class Session(messages: List[String], closeCode: Int, closeReason: String)

  /** Everything the clients observed against the shoutbox. */
  final case class Observed(session: Session, closedRoom: String, refusedStatus: Int, plain: (Status, String))

  /** Matches the `nick` query parameter, which the route requires before upgrading. */
  object NickParam extends OptionalQueryParamDecoderMatcher[String]("nick")

  /** Turns incoming frames into replies, ending the conversation with a close frame on `/quit`. */
  def shout(room: String, nick: String): Pipe[IO, WebSocketFrame, WebSocketFrame] = frames => {
    val replies: Stream[IO, WebSocketFrame] = frames
      .collect {
        case WebSocketFrame.Text(text, _) => Left(text)
        case WebSocketFrame.Binary(data, _) => Right(data)
      }
      .takeWhile(_ != Left("/quit"))
      .mapAccumulate(0) {
        case (count, Left(text)) => (count + 1, s"#${count + 1} $nick: ${text.toUpperCase}")
        case (count, Right(data)) => (count, s"$nick sent ${data.size} bytes")
      }
      .map((_, reply) => WebSocketFrame.Text(reply))
    val farewell: Stream[IO, WebSocketFrame] = Stream.eval(IO.fromEither(WebSocketFrame.Close(1000, s"bye $nick")))
    Stream.emit(WebSocketFrame.Text(s"welcome to $room, $nick")) ++ replies ++ farewell
  }

  /** The shoutbox routes an application would mount on a WebSocket-capable server. */
  def shoutboxRoutes(ws: WebSocketBuilder2[IO], closed: Deferred[IO, String]): HttpRoutes[IO] = HttpRoutes.of[IO] {
    case GET -> Root / "shout" / room :? NickParam(nick) =>
      nick.filter(_.nonEmpty) match {
        case None => BadRequest("a nickname is required")
        case Some(name) =>
          ws.withOnClose(closed.complete(room).void)
            .build(shout(room, name))
      }
  }

  /** Starts the shoutbox on an ephemeral loopback port and runs `scenario` against it. */
  def serveAndCall[A](scenario: (Client[IO], Int, Deferred[IO, String]) => IO[A]): A = {
    val running: Resource[IO, (Client[IO], Int, Deferred[IO, String])] = for {
      closed <- Resource.eval(Deferred[IO, String])
      server <- EmberServerBuilder
        .default[IO]
        .withHost(ipv4"127.0.0.1")
        .withPort(port"0")
        .withHttpWebSocketApp(ws => shoutboxRoutes(ws, closed).orNotFound)
        .build
      client <- EmberClientBuilder.default[IO].build
    } yield (client, server.address.getPort, closed)
    running.use((client, port, closed) => scenario(client, port, closed)).unsafeRunSync()
  }

  /** Collects whole text messages and the close frame delivered to a JDK WebSocket client. */
  final class Recorder extends WebSocket.Listener {
    val messages: LinkedBlockingQueue[String] = new LinkedBlockingQueue[String]()
    val closing: CompletableFuture[(Int, String)] = new CompletableFuture[(Int, String)]()
    private val partial: StringBuilder = new StringBuilder()

    override def onOpen(webSocket: WebSocket): Unit = webSocket.request(1)

    override def onText(webSocket: WebSocket, data: CharSequence, last: Boolean): CompletionStage[?] = {
      partial.append(data)
      if (last) {
        messages.put(partial.toString)
        partial.clear()
      }
      webSocket.request(1)
      null
    }

    override def onClose(webSocket: WebSocket, statusCode: Int, reason: String): CompletionStage[?] = {
      closing.complete((statusCode, reason))
      null
    }

    override def onError(webSocket: WebSocket, error: Throwable): Unit = closing.completeExceptionally(error)

    /** Waits for the next whole text message. */
    def next(): String = {
      val message: String = messages.poll(10, TimeUnit.SECONDS)
      assert(message != null, "no message arrived within 10 seconds")
      message
    }
  }

  /** Connects, sends `outgoing` in order waiting for each reply, and returns what the session saw. */
  def converse(uri: URI, outgoing: List[Either[String, Array[Byte]]]): Session = {
    val recorder: Recorder = new Recorder()
    val socket: WebSocket = HttpClient.newHttpClient().newWebSocketBuilder().buildAsync(uri, recorder).get(10, TimeUnit.SECONDS)
    val welcome: String = recorder.next()
    val replies: List[String] = outgoing.flatMap {
      case Left("/quit") =>
        socket.sendText("/quit", true).get(10, TimeUnit.SECONDS)
        Nil
      case Left(text) =>
        socket.sendText(text, true).get(10, TimeUnit.SECONDS)
        List(recorder.next())
      case Right(bytes) =>
        socket.sendBinary(ByteBuffer.wrap(bytes), true).get(10, TimeUnit.SECONDS)
        List(recorder.next())
    }
    val (code, reason) = recorder.closing.get(10, TimeUnit.SECONDS)
    socket.sendClose(WebSocket.NORMAL_CLOSURE, "").exceptionally(_ => socket)
    Session(welcome :: replies, code, reason)
  }

  /** Attempts a WebSocket handshake that the server refuses and returns the HTTP status it answered with. */
  def handshakeStatus(uri: URI): Int =
    try {
      HttpClient.newHttpClient().newWebSocketBuilder().buildAsync(uri, new Recorder()).join()
      throw new AssertionError(s"handshake to $uri unexpectedly succeeded")
    } catch {
      case failure: CompletionException =>
        failure.getCause match {
          case refused: WebSocketHandshakeException => refused.getResponse.statusCode()
          case other => throw other
        }
    }
}
