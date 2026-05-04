/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_softwaremill_sttp_shared.ws_3

import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertInstanceOf
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import sttp.monad.TryMonad
import sttp.ws.WebSocket
import sttp.ws.WebSocketBufferFull
import sttp.ws.WebSocketClosed
import sttp.ws.WebSocketFrame
import sttp.ws.WebSocketFrame.Binary
import sttp.ws.WebSocketFrame.Close
import sttp.ws.WebSocketFrame.Data
import sttp.ws.WebSocketFrame.Ping
import sttp.ws.WebSocketFrame.Pong
import sttp.ws.WebSocketFrame.Text
import sttp.ws.testing.WebSocketStub

import scala.util.Failure
import scala.util.Success
import scala.util.Try

class Ws_3Test {
  @Test
  def frameFactoriesCreateExpectedDefaultFramesAndCaseClassFrames(): Unit = {
    val text: Text = WebSocketFrame.text("hello")
    assertEquals("hello", text.payload)
    assertTrue(text.finalFragment)
    assertEquals(None, text.rsv)
    assertEquals(Text("hello", finalFragment = true, None), text)
    assertEquals("Text", text.productPrefix)
    assertEquals("payload", text.productElementName(0))

    val fragmentedText: Text = text.copy(payload = "hel", finalFragment = false, rsv = Some(4))
    assertEquals(Text("hel", finalFragment = false, Some(4)), fragmentedText)
    assertEquals("hel", fragmentedText._1)
    assertFalse(fragmentedText._2)
    assertEquals(Some(4), fragmentedText._3)

    val binaryPayload: Array[Byte] = Array[Byte](1, 2, 3)
    val binary: Binary = WebSocketFrame.binary(binaryPayload)
    assertArrayEquals(binaryPayload, binary.payload)
    assertTrue(binary.finalFragment)
    assertEquals(None, binary.rsv)

    val customBinaryPayload: Array[Byte] = Array[Byte](4, 5)
    val fragmentedBinary: Binary = Binary(customBinaryPayload, finalFragment = false, Some(1))
    assertArrayEquals(customBinaryPayload, fragmentedBinary.payload)
    assertFalse(fragmentedBinary.finalFragment)
    assertEquals(Some(1), fragmentedBinary.rsv)

    val ping: Ping = WebSocketFrame.ping
    val pong: Pong = WebSocketFrame.pong
    assertArrayEquals(Array.emptyByteArray, ping.payload)
    assertArrayEquals(Array.emptyByteArray, pong.payload)
    assertEquals(Ping(Array[Byte](9)).copy(Array[Byte](8)).payload.toSeq, Seq[Byte](8))
    assertEquals(Pong(Array[Byte](7)).copy(Array[Byte](6)).payload.toSeq, Seq[Byte](6))

    val close: Close = WebSocketFrame.close
    assertEquals(1000, close.statusCode)
    assertEquals("normal closure", close.reasonText)
    assertEquals(Close(1001, "going away"), close.copy(statusCode = 1001, reasonText = "going away"))
  }

  @Test
  def framesCanBeHandledThroughPublicSealedTraits(): Unit = {
    val frames: List[WebSocketFrame] = List(
      Text("a", finalFragment = false, None),
      Binary(Array[Byte](1, 2), finalFragment = true, Some(2)),
      Ping(Array[Byte](3)),
      Pong(Array[Byte](4)),
      Close(1000, "done")
    )

    val descriptions: List[String] = frames.map {
      case data: Data[?] if data.finalFragment => s"final:${data.payload}"
      case data: Data[?]                      => s"fragment:${data.payload}"
      case Ping(payload)                       => s"ping:${payload.length}"
      case Pong(payload)                       => s"pong:${payload.length}"
      case Close(code, reason)                 => s"close:$code:$reason"
    }

    assertEquals(
      List("fragment:a", "final:[B@"),
      descriptions.take(2).map(description => if (description.startsWith("final:[B@")) "final:[B@" else description)
    )
    assertEquals(List("ping:1", "pong:1", "close:1000:done"), descriptions.drop(2))
  }

  @Test
  def websocketStubReceivesInitialFramesAndClosesAfterCloseFrame(): Unit = {
    val close: Close = Close(1000, "complete")
    val webSocket: WebSocket[Try] = WebSocketStub
      .initialReceive(List(Text("first", finalFragment = true, None), close))
      .build(TryMonad)

    assertTrue(webSocket.upgradeHeaders.headers.isEmpty)
    assertEquals(Success(true), webSocket.isOpen())
    assertEquals(Success(Text("first", finalFragment = true, None)), webSocket.receive())
    assertEquals(Success(true), webSocket.isOpen())
    assertEquals(Success(close), webSocket.receive())
    assertEquals(Success(false), webSocket.isOpen())

    val failure: Throwable = webSocket.receive().failed.get
    val closed: WebSocketClosed = assertInstanceOf(classOf[WebSocketClosed], failure)
    assertEquals(Some(close), closed.frame)
  }

  @Test
  def websocketStubReportsUnexpectedReceiveAndConfiguredFailures(): Unit = {
    val emptySocket: WebSocket[Try] = WebSocketStub.noInitialReceive.build(TryMonad)
    val unexpected: Throwable = emptySocket.receive().failed.get
    assertInstanceOf(classOf[IllegalStateException], unexpected)
    assertEquals("Unexpected 'receive', no more prepared responses.", unexpected.getMessage)
    assertEquals(Success(true), emptySocket.isOpen())

    val configuredFailure: IllegalArgumentException = new IllegalArgumentException("boom")
    val failingSocket: WebSocket[Try] = WebSocketStub
      .initialReceiveWith(List(Failure(configuredFailure)))
      .build(TryMonad)

    assertEquals(Failure(configuredFailure), failingSocket.receive())
    assertEquals(Success(false), failingSocket.isOpen())
    assertInstanceOf(classOf[WebSocketClosed], failingSocket.receive().failed.get)
  }

  @Test
  def sendHelpersAppendResponsesComputedFromSentFrames(): Unit = {
    val webSocket: WebSocket[Try] = WebSocketStub
      .noInitialReceive
      .thenRespond {
        case Text(payload, _, _)   => List(Text(s"text:$payload", finalFragment = true, None))
        case Binary(payload, _, _) => List(Text(s"binary:${payload.sum}", finalFragment = true, None))
        case Ping(payload)         => List(Pong(payload))
        case Pong(payload)         => List(Text(s"pong:${payload.length}", finalFragment = true, None))
        case Close(_, _)           => List(Close(1000, "ack"))
      }
      .build(TryMonad)

    assertEquals(Success(()), webSocket.sendText("hello"))
    assertEquals(Success(Text("text:hello", finalFragment = true, None)), webSocket.receive())

    assertEquals(Success(()), webSocket.sendBinary(Array[Byte](2, 3, 4)))
    assertEquals(Success(Text("binary:9", finalFragment = true, None)), webSocket.receive())

    assertEquals(Success(()), webSocket.send(Ping(Array[Byte](7, 8))))
    val receivedPong: Try[WebSocketFrame] = webSocket.receive()
    assertTrue(receivedPong.isSuccess)
    assertArrayEquals(Array[Byte](7, 8), receivedPong.get.asInstanceOf[Pong].payload)

    assertEquals(Success(()), webSocket.close())
    assertEquals(Success(Close(1000, "ack")), webSocket.receive())
    assertEquals(Success(false), webSocket.isOpen())
    assertTrue(webSocket.sendText("after-close").isFailure)
  }

  @Test
  def responseBuildersCanEnqueueFailuresAndUseStatefulSuccessLogic(): Unit = {
    val configuredFailure: IllegalArgumentException = new IllegalArgumentException("queued failure")
    val failingSocket: WebSocket[Try] = WebSocketStub
      .noInitialReceive
      .thenRespondWith {
        case Text("fail", _, _) => List(Failure(configuredFailure))
        case frame              => List(Success(frame))
      }
      .build(TryMonad)

    assertEquals(Success(()), failingSocket.sendText("fail"))
    assertEquals(Failure(configuredFailure), failingSocket.receive())
    assertEquals(Success(false), failingSocket.isOpen())

    val countingSocket: WebSocket[Try] = WebSocketStub
      .noInitialReceive
      .thenRespondS(0) { (count: Int, frame: WebSocketFrame) =>
        val next: Int = count + 1
        val response: WebSocketFrame = frame match {
          case Text(payload, _, _) => Text(s"$next:$payload", finalFragment = true, None)
          case _                   => Text(s"$next:other", finalFragment = true, None)
        }
        (next, List(response))
      }
      .build(TryMonad)

    assertEquals(Success(()), countingSocket.sendText("first"))
    assertEquals(Success(()), countingSocket.sendText("second"))
    assertEquals(Success(Text("1:first", finalFragment = true, None)), countingSocket.receive())
    assertEquals(Success(Text("2:second", finalFragment = true, None)), countingSocket.receive())
  }

  @Test
  def statefulStubResponsesObserveAndUpdateApplicationState(): Unit = {
    val webSocket: WebSocket[Try] = WebSocketStub
      .noInitialReceive
      .thenRespondWithS(0) { (count: Int, frame: WebSocketFrame) =>
        val next: Int = count + 1
        val response: Try[WebSocketFrame] = frame match {
          case Text(payload, _, _) => Success(Text(s"$next:$payload", finalFragment = true, None))
          case _                   => Failure(new IllegalArgumentException(s"unexpected frame $next"))
        }
        (next, List(response))
      }
      .build(TryMonad)

    assertEquals(Success(()), webSocket.sendText("first"))
    assertEquals(Success(()), webSocket.sendText("second"))
    assertEquals(Success(()), webSocket.sendBinary(Array[Byte](1)))

    assertEquals(Success(Text("1:first", finalFragment = true, None)), webSocket.receive())
    assertEquals(Success(Text("2:second", finalFragment = true, None)), webSocket.receive())
    val failure: Throwable = webSocket.receive().failed.get
    assertInstanceOf(classOf[IllegalArgumentException], failure)
    assertEquals("unexpected frame 3", failure.getMessage)
    assertEquals(Success(false), webSocket.isOpen())
  }

  @Test
  def websocketStubBuildCreatesIndependentSocketsWithFreshQueuesAndState(): Unit = {
    val stub: WebSocketStub[Int] = WebSocketStub
      .initialReceive(List(Text("initial", finalFragment = true, None)))
      .thenRespondS(0) { (count: Int, frame: WebSocketFrame) =>
        val next: Int = count + 1
        val response: WebSocketFrame = frame match {
          case Text(payload, _, _) => Text(s"$next:$payload", finalFragment = true, None)
          case _                   => Text(s"$next:other", finalFragment = true, None)
        }
        (next, List(response))
      }

    val first: WebSocket[Try] = stub.build(TryMonad)
    val second: WebSocket[Try] = stub.build(TryMonad)

    assertEquals(Success(Text("initial", finalFragment = true, None)), first.receive())
    assertEquals(Success(Text("initial", finalFragment = true, None)), second.receive())

    assertEquals(Success(()), first.sendText("one"))
    assertEquals(Success(()), first.sendText("two"))
    assertEquals(Success(()), second.sendText("one"))

    assertEquals(Success(Text("1:one", finalFragment = true, None)), first.receive())
    assertEquals(Success(Text("2:two", finalFragment = true, None)), first.receive())
    assertEquals(Success(Text("1:one", finalFragment = true, None)), second.receive())
  }

  @Test
  def receiveDataFrameSkipsControlFramesAndPongsReceivedPingByDefault(): Unit = {
    val webSocket: WebSocket[Try] = WebSocketStub
      .initialReceive(List(Pong(Array[Byte](1)), Ping(Array[Byte](2, 3))))
      .thenRespond {
        case Pong(payload) => List(Text(s"ponged:${payload.mkString(",")}", finalFragment = true, None))
        case _             => List.empty
      }
      .build(TryMonad)

    assertEquals(Success(Text("ponged:2,3", finalFragment = true, None)), webSocket.receiveDataFrame())
  }

  @Test
  def receiveDataFrameCanIgnorePingWithoutSendingPong(): Unit = {
    val webSocket: WebSocket[Try] = WebSocketStub
      .initialReceive(List(Ping(Array[Byte](2, 3)), Binary(Array[Byte](4, 5), finalFragment = true, None)))
      .thenRespond(_ => List(Text("should-not-be-enqueued", finalFragment = true, None)))
      .build(TryMonad)

    val received: Try[Data[?]] = webSocket.receiveDataFrame(pongOnPing = false)
    assertTrue(received.isSuccess)
    assertArrayEquals(Array[Byte](4, 5), received.get.asInstanceOf[Binary].payload)
    assertTrue(webSocket.receive().failed.get.isInstanceOf[IllegalStateException])
  }

  @Test
  def receiveDataFrameContinuesWhenAutomaticPongCannotBeSent(): Unit = {
    var observedPongPayload: Option[Array[Byte]] = None
    val webSocket: WebSocket[Try] = WebSocketStub
      .initialReceive(List(Ping(Array[Byte](5, 6)), Text("after-ping", finalFragment = true, None)))
      .thenRespondWith {
        case Pong(payload) =>
          observedPongPayload = Some(payload.clone())
          throw new IllegalStateException("pong unavailable")
        case frame => List(Success(frame))
      }
      .build(TryMonad)

    assertEquals(Success(Text("after-ping", finalFragment = true, None)), webSocket.receiveDataFrame())
    assertTrue(observedPongPayload.isDefined)
    assertArrayEquals(Array[Byte](5, 6), observedPongPayload.get)
    assertEquals(Success(true), webSocket.isOpen())
  }

  @Test
  def receiveTextFrameAndBinaryFrameSkipOtherDataFrameTypes(): Unit = {
    val textSocket: WebSocket[Try] = WebSocketStub
      .initialReceive(List(Binary(Array[Byte](1), finalFragment = true, None), Text("selected", finalFragment = true, None)))
      .build(TryMonad)
    assertEquals(Success(Text("selected", finalFragment = true, None)), textSocket.receiveTextFrame())

    val binarySocket: WebSocket[Try] = WebSocketStub
      .initialReceive(List(Text("ignored", finalFragment = true, None), Binary(Array[Byte](9), finalFragment = true, None)))
      .build(TryMonad)
    val binary: Try[Binary] = binarySocket.receiveBinaryFrame()
    assertTrue(binary.isSuccess)
    assertArrayEquals(Array[Byte](9), binary.get.payload)
  }

  @Test
  def receiveTextConcatenatesFragmentsAndIgnoresNonTextFrames(): Unit = {
    val webSocket: WebSocket[Try] = WebSocketStub
      .initialReceive(
        List(
          Binary(Array[Byte](1), finalFragment = true, None),
          Text("hel", finalFragment = false, Some(1)),
          Pong(Array.emptyByteArray),
          Text("lo", finalFragment = true, None)
        )
      )
      .build(TryMonad)

    assertEquals(Success("hello"), webSocket.receiveText())
  }

  @Test
  def receiveBinaryConcatenatesFragmentsAndIgnoresNonBinaryFrames(): Unit = {
    val webSocket: WebSocket[Try] = WebSocketStub
      .initialReceive(
        List(
          Text("ignored", finalFragment = true, None),
          Binary(Array[Byte](1, 2), finalFragment = false, None),
          Ping(Array.emptyByteArray),
          Binary(Array[Byte](3, 4), finalFragment = true, Some(7))
        )
      )
      .build(TryMonad)

    val received: Try[Array[Byte]] = webSocket.receiveBinary(pongOnPing = true)
    assertTrue(received.isSuccess)
    assertArrayEquals(Array[Byte](1, 2, 3, 4), received.get)
  }

  @Test
  def receiveHelpersFailWhenCloseFrameIsReceived(): Unit = {
    val close: Close = Close(1001, "going away")
    val dataSocket: WebSocket[Try] = WebSocketStub.initialReceive(List(close)).build(TryMonad)
    val dataFailure: Throwable = dataSocket.receiveDataFrame().failed.get
    assertEquals(WebSocketClosed(Some(close)), dataFailure)

    val textSocket: WebSocket[Try] = WebSocketStub.initialReceive(List(close)).build(TryMonad)
    val textFailure: Throwable = textSocket.receiveText().failed.get
    assertEquals(WebSocketClosed(Some(close)), textFailure)

    val binarySocket: WebSocket[Try] = WebSocketStub.initialReceive(List(close)).build(TryMonad)
    val binaryFailure: Throwable = binarySocket.receiveBinary(pongOnPing = true).failed.get
    assertEquals(WebSocketClosed(Some(close)), binaryFailure)
  }

  @Test
  def eitherAndEitherCloseLiftWebSocketClosedToValueLevel(): Unit = {
    val close: Close = Close(1000, "normal")
    val closeSocket: WebSocket[Try] = WebSocketStub.initialReceive(List(close)).build(TryMonad)
    assertEquals(Success(Left(close)), closeSocket.eitherClose(closeSocket.receiveText()))

    val valueSocket: WebSocket[Try] = WebSocketStub.initialReceive(List(Text("ok", finalFragment = true, None))).build(TryMonad)
    assertEquals(Success(Right("ok")), valueSocket.either(valueSocket.receiveText()))

    val noFrameClosedSocket: WebSocket[Try] = WebSocketStub.initialReceiveWith(List(Failure(WebSocketClosed(None)))).build(TryMonad)
    assertEquals(Success(Left(None)), noFrameClosedSocket.either(noFrameClosedSocket.receiveText()))

    val stillFailingSocket: WebSocket[Try] = WebSocketStub.initialReceiveWith(List(Failure(WebSocketClosed(None)))).build(TryMonad)
    assertEquals(Failure(WebSocketClosed(None)), stillFailingSocket.eitherClose(stillFailingSocket.receiveText()))
  }

  @Test
  def websocketExceptionsExposePublicCaseClassStateAndMessages(): Unit = {
    val close: Close = Close(1008, "policy")
    val closed: WebSocketClosed = WebSocketClosed(Some(close))
    assertEquals(Some(close), closed.frame)
    assertEquals(closed, closed.copy(frame = Some(close)))
    assertEquals(None, WebSocketClosed(None).frame)

    val bufferFull: WebSocketBufferFull = WebSocketBufferFull(16)
    assertEquals(16, bufferFull.capacity)
    assertEquals("Buffered 16 messages", bufferFull.getMessage)
    assertEquals(WebSocketBufferFull(32), bufferFull.copy(capacity = 32))
    assertNotEquals(bufferFull, WebSocketBufferFull(17))

    val thrown: WebSocketBufferFull = assertThrows(
      classOf[WebSocketBufferFull],
      () => throw bufferFull
    )
    assertEquals(bufferFull, thrown)
  }
}
