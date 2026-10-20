/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_softwaremill_sttp_client4.circe_3

import io.circe.Error
import io.circe.generic.auto.*
import io.circe.parser.decode
import io.circe.syntax.*
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import sttp.client4.*
import sttp.client4.circe.*
import sttp.client4.ws.SyncWebSocket
import sttp.client4.ws.sync.*
import sttp.model.ResponseMetadata
import sttp.model.StatusCode
import sttp.ws.WebSocketFrame

import java.io.BufferedInputStream
import java.io.ByteArrayOutputStream
import java.io.DataInputStream
import java.io.EOFException
import java.io.IOException
import java.io.OutputStream
import java.net.InetSocketAddress
import java.net.ServerSocket
import java.net.Socket
import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.util.Base64
import java.util.Locale
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import scala.collection.mutable
import scala.concurrent.duration.*

/**
 * Exercises circe JSON messages over sttp's synchronous WebSocket API: a trading-desk client
 * opens a bearer-authenticated session with `asWebSocketWithMetadata`, sends circe-encoded
 * commands as text frames and decodes every reply with `deserializeJson`, receives a snapshot
 * larger than a short frame, sees a server-initiated close through `eitherClose`, and obtains
 * its session ticket over HTTP with `asJsonEither`, decoding a refused login as a typed body.
 */
class TradingDeskWebSocketTest {
  private sealed trait DeskRequest
  private final case class Subscribe(symbols: List[String]) extends DeskRequest
  private final case class PlaceOrder(orderId: String, symbol: String, quantity: Int, limit: BigDecimal)
      extends DeskRequest
  private final case class RequestSnapshot(account: String) extends DeskRequest
  private final case class Logout(reason: String) extends DeskRequest

  private sealed trait DeskEvent
  private final case class Tick(symbol: String, price: BigDecimal, sequence: Long) extends DeskEvent
  private final case class Subscribed(symbols: List[String], sessionId: String) extends DeskEvent
  private final case class OrderAccepted(orderId: String, notional: BigDecimal) extends DeskEvent
  private final case class OrderRejected(orderId: String, reason: String) extends DeskEvent
  private final case class Snapshot(account: String, positions: List[Position]) extends DeskEvent
  private final case class Problem(detail: String) extends DeskEvent

  private final case class Position(symbol: String, quantity: Int, averagePrice: BigDecimal)
  private final case class DeskError(code: String, message: String)
  private final case class Login(trader: String, password: String)
  private final case class Ticket(trader: String, token: String)

  /** The application's view of an open desk session: typed commands out, typed events in. */
  private final class DeskSession(ws: SyncWebSocket) {
    private val decodeEvent: String => Either[Error, DeskEvent] = deserializeJson[DeskEvent]

    def send(request: DeskRequest): Unit = ws.sendText(request.asJson.noSpaces)

    def sendRaw(text: String): Unit = ws.sendText(text)

    def next(): DeskEvent = decodeEvent(ws.receiveText()) match {
      case Right(event) => event
      case Left(error) => throw error
    }

    def nextOrClose(): Either[WebSocketFrame.Close, DeskEvent] = ws.eitherClose(next())
  }

  private val DeskToken: String = "desk-token-7"
  private val SessionId: String = "desk-session-42"
  private val Prices: Map[String, BigDecimal] = Map("ACME" -> BigDecimal("101.25"), "GLOBEX" -> BigDecimal("47.10"))

  private var serverSocket: ServerSocket = null
  private var serverExecutor: ExecutorService = null
  private var backend: WebSocketSyncBackend = null
  private var deskUri: String = null

  @BeforeEach
  def startServer(): Unit = {
    serverSocket = new ServerSocket()
    serverSocket.bind(new InetSocketAddress("127.0.0.1", 0))
    serverExecutor = Executors.newCachedThreadPool()
    serverExecutor.execute(() => acceptConnections())
    deskUri = s"ws://127.0.0.1:${serverSocket.getLocalPort}/desk"
    backend = DefaultSyncBackend()
  }

  @AfterEach
  def stopServer(): Unit = {
    backend.close()
    serverSocket.close()
    serverExecutor.shutdownNow()
  }

  @Test
  def subscribesToTicksAndTradesOverOneAuthenticatedSession(): Unit = {
    val response = basicRequest
      .get(uri"$deskUri")
      .auth
      .bearer(DeskToken)
      .readTimeout(10.seconds)
      .response(asWebSocketWithMetadata { (ws: SyncWebSocket, metadata: ResponseMetadata) =>
        val session = new DeskSession(ws)
        session.send(Subscribe(List("ACME", "GLOBEX")))
        val subscription = List(session.next(), session.next(), session.next())
        session.send(PlaceOrder("ord-1", "ACME", 10, BigDecimal("101.50")))
        val accepted = session.next()
        session.send(PlaceOrder("ord-2", "GLOBEX", 0, BigDecimal("47.00")))
        val rejected = session.next()
        (metadata.code, subscription, accepted, rejected)
      })
      .send(backend)

    assertThat(response.code).isEqualTo(StatusCode.SwitchingProtocols)
    val (upgradeStatus, subscription, accepted, rejected) =
      response.body.fold(error => throw new AssertionError(error), identity)
    assertThat(upgradeStatus).isEqualTo(StatusCode.SwitchingProtocols)
    assertThat(subscription).isEqualTo(
      List(
        Tick("ACME", BigDecimal("101.25"), 1L),
        Tick("GLOBEX", BigDecimal("47.10"), 2L),
        Subscribed(List("ACME", "GLOBEX"), SessionId)
      )
    )
    assertThat(accepted).isEqualTo(OrderAccepted("ord-1", BigDecimal("1015.00")))
    assertThat(rejected).isEqualTo(OrderRejected("ord-2", "quantity must be positive"))
  }

  @Test
  def receivesLargeSnapshotProblemReportAndServerInitiatedClose(): Unit = {
    val response = basicRequest
      .get(uri"$deskUri")
      .auth
      .bearer(DeskToken)
      .readTimeout(10.seconds)
      .response(asWebSocketOrFail { (ws: SyncWebSocket) =>
        val session = new DeskSession(ws)
        session.send(RequestSnapshot("ACC-9"))
        val snapshot = session.next()
        session.sendRaw("""{"Subscribe":{"symbols":"ACME"}}""")
        val problem = session.next()
        session.send(Logout("end of day"))
        (snapshot, problem, session.nextOrClose())
      })
      .send(backend)

    assertThat(response.code).isEqualTo(StatusCode.SwitchingProtocols)
    val (snapshot, problem, closing) = response.body
    snapshot match {
      case Snapshot(account, positions) =>
        assertThat(account).isEqualTo("ACC-9")
        assertThat(positions.size).isEqualTo(250)
        assertThat(positions.head).isEqualTo(Position("SYM000", 1, BigDecimal("10.05")))
        assertThat(positions.last).isEqualTo(Position("SYM249", 250, BigDecimal("22.50")))
        assertThat(positions.map(_.quantity).sum).isEqualTo(31375)
      case other => throw new AssertionError(s"expected a snapshot but got $other")
    }
    assertThat(problem).isEqualTo(Problem("unreadable request"))
    assertThat(closing).isEqualTo(Left(WebSocketFrame.Close(1000, "end of day")))
  }

  @Test
  def opensSessionWithTicketIssuedOverJsonLogin(): Unit = {
    def login(password: String): Response[Either[ResponseException[DeskError], Ticket]] =
      basicRequest
        .post(uri"http://127.0.0.1:${serverSocket.getLocalPort}/tickets")
        .body(asJson(Login("trader-1", password)))
        .readTimeout(10.seconds)
        .response(asJsonEither[DeskError, Ticket])
        .send(backend)

    val refused = login("guess")
    assertThat(refused.code).isEqualTo(StatusCode.Unauthorized)
    refused.body match {
      case Left(ResponseException.UnexpectedStatusCode(error, metadata)) =>
        assertThat(error).isEqualTo(DeskError("unauthorized", "unknown credentials"))
        assertThat(metadata.code).isEqualTo(StatusCode.Unauthorized)
      case other => throw new AssertionError(s"expected a refused login but got $other")
    }

    val issued = login("s3cret")
    assertThat(issued.code).isEqualTo(StatusCode.Ok)
    val ticket = issued.body.fold(error => throw error, identity)
    assertThat(ticket).isEqualTo(Ticket("trader-1", DeskToken))

    val session = basicRequest
      .get(uri"$deskUri")
      .auth
      .bearer(ticket.token)
      .readTimeout(10.seconds)
      .response(asWebSocketAlways { (ws: SyncWebSocket) =>
        val desk = new DeskSession(ws)
        desk.send(Subscribe(List("GLOBEX")))
        List(desk.next(), desk.next())
      })
      .send(backend)

    assertThat(session.body).isEqualTo(
      List(Tick("GLOBEX", BigDecimal("47.10"), 1L), Subscribed(List("GLOBEX"), SessionId))
    )
  }

  private def acceptConnections(): Unit = {
    try {
      while (true) {
        val socket = serverSocket.accept()
        serverExecutor.execute(() => serveConnection(socket))
      }
    } catch {
      case _: IOException => ()
    }
  }

  private def serveConnection(socket: Socket): Unit = {
    try {
      val in = new DataInputStream(new BufferedInputStream(socket.getInputStream))
      val out = socket.getOutputStream
      val (requestLine, headers) = readRequestHead(in)
      if (requestLine.startsWith("POST /tickets ")) {
        val body = new Array[Byte](headers("content-length").toInt)
        in.readFully(body)
        decode[Login](new String(body, StandardCharsets.UTF_8)) match {
          case Right(Login(trader, "s3cret")) => writeJson(out, "200 OK", Ticket(trader, DeskToken).asJson.noSpaces)
          case _ => writeJson(out, "401 Unauthorized", DeskError("unauthorized", "unknown credentials").asJson.noSpaces)
        }
      } else if (!headers.get("authorization").contains(s"Bearer $DeskToken")) {
        writeJson(out, "401 Unauthorized", DeskError("unauthorized", "bearer token rejected").asJson.noSpaces)
      } else {
        val accept = Base64.getEncoder.encodeToString(
          MessageDigest
            .getInstance("SHA-1")
            .digest((headers("sec-websocket-key") + "258EAFA5-E914-47DA-95CA-C5AB0DC85B11").getBytes(StandardCharsets.US_ASCII))
        )
        writeAscii(
          out,
          "HTTP/1.1 101 Switching Protocols\r\nUpgrade: websocket\r\nConnection: Upgrade\r\n" +
            s"Sec-WebSocket-Accept: $accept\r\n\r\n"
        )
        out.flush()
        serveDesk(in, out)
      }
    } catch {
      case _: IOException => ()
    } finally {
      socket.close()
    }
  }

  /** Answers each client text message until the client logs out or closes the session. */
  private def serveDesk(in: DataInputStream, out: OutputStream): Unit = {
    var sequence: Long = 0L
    var open: Boolean = true
    while (open) {
      readMessage(in, out) match {
        case None => open = false
        case Some(text) =>
          decode[DeskRequest](text) match {
            case Right(Subscribe(symbols)) =>
              symbols.foreach { symbol =>
                sequence += 1
                sendEvent(out, Tick(symbol, Prices(symbol), sequence))
              }
              sendEvent(out, Subscribed(symbols, SessionId))
            case Right(PlaceOrder(orderId, _, quantity, limit)) =>
              if (quantity > 0) sendEvent(out, OrderAccepted(orderId, limit * quantity))
              else sendEvent(out, OrderRejected(orderId, "quantity must be positive"))
            case Right(RequestSnapshot(account)) =>
              val positions = (0 until 250).toList.map { i =>
                Position(f"SYM$i%03d", i + 1, BigDecimal(1005 + 5 * i, 2))
              }
              sendEvent(out, Snapshot(account, positions))
            case Right(Logout(reason)) =>
              val reasonBytes = reason.getBytes(StandardCharsets.UTF_8)
              writeFrame(out, 0x8, Array[Byte](0x03.toByte, 0xe8.toByte) ++ reasonBytes)
              awaitClientClose(in, out)
              open = false
            case Left(_) =>
              sendEvent(out, Problem("unreadable request"))
          }
      }
    }
  }

  private def sendEvent(out: OutputStream, event: DeskEvent): Unit =
    writeFrame(out, 0x1, event.asJson.noSpaces.getBytes(StandardCharsets.UTF_8))

  private def awaitClientClose(in: DataInputStream, out: OutputStream): Unit =
    try {
      while (readMessage(in, out).isDefined) ()
    } catch {
      case _: EOFException => ()
    }

  /** Reads one complete text message, joining continuation frames; `None` once the client closes. */
  private def readMessage(in: DataInputStream, out: OutputStream): Option[String] = {
    val message = new ByteArrayOutputStream()
    var result: Option[Option[String]] = None
    while (result.isEmpty) {
      val first = in.readUnsignedByte()
      val second = in.readUnsignedByte()
      val length: Long = second & 0x7f match {
        case 126 => in.readUnsignedShort().toLong
        case 127 => in.readLong()
        case short => short.toLong
      }
      val mask = new Array[Byte](4)
      if ((second & 0x80) != 0) in.readFully(mask)
      val payload = new Array[Byte](length.toInt)
      in.readFully(payload)
      for (i <- payload.indices) payload(i) = (payload(i) ^ mask(i % 4)).toByte
      first & 0x0f match {
        case 0x8 =>
          writeFrame(out, 0x8, payload.take(2))
          result = Some(None)
        case 0x9 =>
          writeFrame(out, 0xa, payload)
        case 0xa =>
          ()
        case _ =>
          message.write(payload)
          if ((first & 0x80) != 0) result = Some(Some(message.toString(StandardCharsets.UTF_8)))
      }
    }
    result.get
  }

  private def writeFrame(out: OutputStream, opcode: Int, payload: Array[Byte]): Unit = {
    out.write(0x80 | opcode)
    if (payload.length < 126) {
      out.write(payload.length)
    } else if (payload.length < 65536) {
      out.write(126)
      out.write(payload.length >> 8)
      out.write(payload.length & 0xff)
    } else {
      out.write(127)
      for (shift <- 56 to 0 by -8) out.write(((payload.length.toLong >> shift) & 0xff).toInt)
    }
    out.write(payload)
    out.flush()
  }

  private def writeJson(out: OutputStream, status: String, json: String): Unit = {
    val body = json.getBytes(StandardCharsets.UTF_8)
    writeAscii(
      out,
      s"HTTP/1.1 $status\r\nContent-Type: application/json\r\nContent-Length: ${body.length}\r\n" +
        "Connection: close\r\n\r\n"
    )
    out.write(body)
    out.flush()
  }

  private def readRequestHead(in: DataInputStream): (String, Map[String, String]) = {
    val headers = mutable.Map[String, String]()
    val requestLine = readLine(in)
    var line = readLine(in)
    while (line.nonEmpty) {
      val colon = line.indexOf(':')
      headers(line.substring(0, colon).trim.toLowerCase(Locale.ROOT)) = line.substring(colon + 1).trim
      line = readLine(in)
    }
    (requestLine, headers.toMap)
  }

  private def readLine(in: DataInputStream): String = {
    val line = new StringBuilder()
    var current = in.readUnsignedByte()
    while (current != '\n') {
      if (current != '\r') line.append(current.toChar)
      current = in.readUnsignedByte()
    }
    line.toString
  }

  private def writeAscii(out: OutputStream, text: String): Unit = out.write(text.getBytes(StandardCharsets.US_ASCII))
}
