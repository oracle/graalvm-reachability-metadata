/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_github_plokhotnyuk_jsoniter_scala.jsoniter_scala_core_3

import com.github.plokhotnyuk.jsoniter_scala.core.JsonReaderException
import com.github.plokhotnyuk.jsoniter_scala.core.JsonValueCodec
import com.github.plokhotnyuk.jsoniter_scala.core.ReaderConfig
import com.github.plokhotnyuk.jsoniter_scala.core.readFromByteBuffer
import com.github.plokhotnyuk.jsoniter_scala.core.readFromSubArray
import com.github.plokhotnyuk.jsoniter_scala.core.writeToByteBuffer
import com.github.plokhotnyuk.jsoniter_scala.core.writeToSubArray
import com.github.plokhotnyuk.jsoniter_scala.macros.JsonCodecMaker
import com.github.plokhotnyuk.jsoniter_scala.macros.named
import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

import java.nio.ByteBuffer
import java.nio.charset.StandardCharsets.UTF_8
import java.util.Arrays

import scala.collection.mutable.ArrayBuffer

import QuoteFrameBufferTest.*

/**
 * `java.nio.ByteBuffer` and byte sub-array reading and writing: a market-data gateway packs
 * quotes as length-prefixed JSON frames into direct and heap buffers, decodes them frame by
 * frame by bounding the buffer limit, and stores quotes in fixed-size slots of a shared ring
 * array, refusing to overflow a slot and tolerating the zero padding after each value.
 */
class QuoteFrameBufferTest {

  private val quotes: Vector[Quote] = Vector(
    Quote("ACME", BigDecimal("101.25"), BigDecimal("101.30"), "XNAS", 1L, List(Level(100, 3), Level(250, 1))),
    Quote("GLOBX", BigDecimal("7.5"), BigDecimal("7.55"), "XLON", 2L, Nil),
    Quote("Z\u00fcrich-AG", BigDecimal("0.0001"), BigDecimal("0.0002"), "XSWX", 3L, List(Level(1000000, 12)))
  )

  /** Appends `quote` as a 4-byte big-endian length followed by its JSON bytes. */
  private def appendFrame(quote: Quote, buf: ByteBuffer): Unit = {
    val lengthAt: Int = buf.position()
    buf.putInt(0)
    val start: Int = buf.position()
    writeToByteBuffer(quote, buf)
    buf.putInt(lengthAt, buf.position() - start)
  }

  /** Reads every frame from `buf` (position to limit) by narrowing the limit to each frame. */
  private def readFrames(buf: ByteBuffer): Vector[Quote] = {
    val decoded: ArrayBuffer[Quote] = ArrayBuffer.empty
    val end: Int = buf.limit()
    while (buf.position() < end) {
      val length: Int = buf.getInt()
      val frameEnd: Int = buf.position() + length
      buf.limit(frameEnd)
      decoded += readFromByteBuffer[Quote](buf)
      assertEquals(frameEnd, buf.position())
      buf.limit(end)
    }
    decoded.toVector
  }

  @Test
  def lengthPrefixedFramesRoundTripThroughDirectBuffer(): Unit = {
    val buf: ByteBuffer = ByteBuffer.allocateDirect(4096)
    quotes.foreach(appendFrame(_, buf))
    buf.flip()

    val firstLength: Int = buf.getInt(0)
    val firstFrame: Array[Byte] = new Array[Byte](firstLength)
    buf.get(4, firstFrame)
    assertEquals(
      """{"symbol":"ACME","bid":101.25,"ask":101.30,"venue":"XNAS","seq":1,"book":[{"size":100,"orders":3},{"size":250,"orders":1}]}""",
      new String(firstFrame, UTF_8)
    )

    assertEquals(quotes, readFrames(buf))
    assertEquals(buf.limit(), buf.position())
  }

  @Test
  def heapBufferViewWritesAtItsArrayOffset(): Unit = {
    val backing: ByteBuffer = ByteBuffer.allocate(512)
    val header: Array[Byte] = "GW01".getBytes(UTF_8)
    backing.put(header)
    val payload: ByteBuffer = backing.slice()
    assertEquals(4, payload.arrayOffset())

    quotes.foreach(appendFrame(_, payload))
    val written: Int = payload.position()

    assertArrayEquals(header, Arrays.copyOfRange(backing.array(), 0, 4))
    val secondJson: String = new String(backing.array(), 4, written, UTF_8)
    assertTrue(secondJson.contains("""{"symbol":"GLOBX","bid":7.5,"ask":7.55,"venue":"XLON","seq":2}"""), secondJson)

    val incoming: ByteBuffer = ByteBuffer.wrap(backing.array(), 4, written).slice()
    assertEquals(quotes, readFrames(incoming))
  }

  @Test
  def malformedFrameReportsOffsetWithinBuffer(): Unit = {
    val buf: ByteBuffer = ByteBuffer.wrap("""{"symbol":"ACME","bid":"oops"}""".getBytes(UTF_8))
    val error: JsonReaderException = assertThrows(classOf[JsonReaderException], () => readFromByteBuffer[Quote](buf))
    assertTrue(error.getMessage.startsWith("illegal number, offset: 0x00000017"), error.getMessage)
  }

  @Test
  def ringSlotsHoldOneQuoteEach(): Unit = {
    val ring: Array[Byte] = new Array[Byte](SlotSize * quotes.size)
    val ends: Vector[Int] = quotes.zipWithIndex.map { (quote, slot) =>
      writeToSubArray(quote, ring, slot * SlotSize, (slot + 1) * SlotSize)
    }
    ends.zipWithIndex.foreach { (end, slot) =>
      assertTrue(end > slot * SlotSize && end <= (slot + 1) * SlotSize, s"slot $slot ends at $end")
      assertEquals('{'.toByte, ring(slot * SlotSize))
      assertEquals(0.toByte, ring(end))
    }

    val exact: Vector[Quote] = ends.zipWithIndex.map((end, slot) => readFromSubArray[Quote](ring, slot * SlotSize, end))
    assertEquals(quotes, exact)

    val padded: Vector[Quote] = quotes.indices.toVector.map { slot =>
      readFromSubArray[Quote](ring, slot * SlotSize, (slot + 1) * SlotSize, ignoreSlotPadding)
    }
    assertEquals(quotes, padded)

    val strict: JsonReaderException =
      assertThrows(classOf[JsonReaderException], () => readFromSubArray[Quote](ring, SlotSize, 2 * SlotSize))
    assertTrue(strict.getMessage.startsWith("expected end of input"), strict.getMessage)
  }

  @Test
  def quoteTooLargeForSlotIsRefused(): Unit = {
    val ring: Array[Byte] = new Array[Byte](2 * SmallSlotSize)
    val big: Quote = quotes.head.copy(book = List.tabulate(20)(i => Level(i * 100, i)))
    assertThrows(classOf[ArrayIndexOutOfBoundsException], () => writeToSubArray(big, ring, 0, SmallSlotSize))

    val next: Int = writeToSubArray(quotes(1), ring, SmallSlotSize, 2 * SmallSlotSize)
    assertEquals(quotes(1), readFromSubArray[Quote](ring, SmallSlotSize, next))
  }
}

object QuoteFrameBufferTest {

  /** Bytes reserved per quote in the ring array. */
  val SlotSize: Int = 256

  /** A slot that fits a quote without a deep order book. */
  val SmallSlotSize: Int = 96

  /** Slots are zero-filled after the value, so the reader stops at the end of the JSON value. */
  val ignoreSlotPadding: ReaderConfig = ReaderConfig.withCheckForEndOfInput(false)

  final case class Level(size: Int, orders: Int)

  final case class Quote(
    symbol: String,
    bid: BigDecimal,
    ask: BigDecimal,
    venue: String,
    @named("seq") sequence: Long,
    book: List[Level]
  )

  given quoteCodec: JsonValueCodec[Quote] = JsonCodecMaker.make
}
