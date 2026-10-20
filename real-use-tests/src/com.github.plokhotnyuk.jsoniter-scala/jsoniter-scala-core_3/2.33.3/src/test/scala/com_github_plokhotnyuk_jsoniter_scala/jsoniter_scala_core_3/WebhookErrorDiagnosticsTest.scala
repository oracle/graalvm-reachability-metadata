/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_github_plokhotnyuk_jsoniter_scala.jsoniter_scala_core_3

import com.github.plokhotnyuk.jsoniter_scala.core.JsonReaderException
import com.github.plokhotnyuk.jsoniter_scala.core.JsonValueCodec
import com.github.plokhotnyuk.jsoniter_scala.core.JsonWriterException
import com.github.plokhotnyuk.jsoniter_scala.core.ReaderConfig
import com.github.plokhotnyuk.jsoniter_scala.core.WriterConfig
import com.github.plokhotnyuk.jsoniter_scala.core.readFromStream
import com.github.plokhotnyuk.jsoniter_scala.core.readFromString
import com.github.plokhotnyuk.jsoniter_scala.core.writeToString
import com.github.plokhotnyuk.jsoniter_scala.macros.JsonCodecMaker
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

import java.io.ByteArrayInputStream
import java.nio.charset.StandardCharsets.UTF_8

import WebhookErrorDiagnosticsTest.*

/**
 * Parse and serialization error diagnostics and input-size limits configured through
 * `ReaderConfig` and `WriterConfig`: a webhook receiver reports rejected partner payloads
 * with compact or hex-dumped messages, with or without stack traces, caps the length of
 * strings it buffers from untrusted streams, and refuses to emit unrepresentable metrics.
 */
class WebhookErrorDiagnosticsTest {

  private val malformed: String = """{"partner":"acme","event":"order.paid","amount":12.5x}"""

  @Test
  def productionModeReportsCompactMessagesWithoutStackTraces(): Unit = {
    val error: JsonReaderException =
      assertThrows(classOf[JsonReaderException], () => readFromString[Delivery](malformed, ProductionReader))

    assertEquals("expected '}' or ',', offset: 0x00000034", error.getMessage)
    assertEquals(0, error.getStackTrace.length)
  }

  @Test
  def debugModeAppendsHexDumpAndStackTrace(): Unit = {
    val error: JsonReaderException =
      assertThrows(classOf[JsonReaderException], () => readFromString[Delivery](malformed, DebugReader))

    assertEquals(
      """expected '}' or ',', offset: 0x00000034, buf:
        |+----------+-------------------------------------------------+------------------+
        ||          |  0  1  2  3  4  5  6  7  8  9  a  b  c  d  e  f | 0123456789abcdef |
        |+----------+-------------------------------------------------+------------------+
        || 00000020 | 2e 70 61 69 64 22 2c 22 61 6d 6f 75 6e 74 22 3a | .paid","amount": |
        || 00000030 | 31 32 2e 35 78 7d                               | 12.5x}           |
        |+----------+-------------------------------------------------+------------------+""".stripMargin,
      error.getMessage
    )
    assertTrue(error.getStackTrace.length > 0)
  }

  @Test
  def oversizedStringsFromUntrustedStreamsAreRejected(): Unit = {
    val accepted: Delivery = Delivery("acme", "order.paid", 12.5, "x" * 64)
    val acceptedBytes: Array[Byte] = writeToString(accepted).getBytes(UTF_8)
    assertEquals(accepted, readFromStream[Delivery](new ByteArrayInputStream(acceptedBytes), LimitedReader))

    val flooded: Array[Byte] = writeToString(accepted.copy(note = "x" * 4096)).getBytes(UTF_8)
    val error: JsonReaderException = assertThrows(
      classOf[JsonReaderException],
      () => readFromStream[Delivery](new ByteArrayInputStream(flooded), LimitedReader)
    )
    assertTrue(error.getMessage.startsWith("too long string exceeded 'maxCharBufSize', offset: 0x"), error.getMessage)
    assertFalse(error.getMessage.contains("buf:"), error.getMessage)
  }

  @Test
  def invalidLimitsAreRejectedWhenConfiguring(): Unit = {
    val error: IllegalArgumentException =
      assertThrows(
        classOf[IllegalArgumentException],
        () => ReaderConfig.withPreferredCharBufSize(256).withMaxCharBufSize(64)
      )
    assertEquals("'maxCharBufSize' should be not less than 'preferredCharBufSize'", error.getMessage)
  }

  @Test
  def unrepresentableMetricsFailSerialization(): Unit = {
    val error: JsonWriterException =
      assertThrows(classOf[JsonWriterException], () => writeToString(Delivery("acme", "order.paid", Double.NaN, "")))
    assertEquals("illegal number: NaN", error.getMessage)
    assertEquals(0, error.getStackTrace.length)

    val traced: JsonWriterException = assertThrows(
      classOf[JsonWriterException],
      () => writeToString(Delivery("acme", "order.paid", Double.PositiveInfinity, ""), TracingWriter)
    )
    assertEquals("illegal number: Infinity", traced.getMessage)
    assertTrue(traced.getStackTrace.length > 0)
  }
}

object WebhookErrorDiagnosticsTest {

  final case class Delivery(partner: String, event: String, amount: Double, note: String = "")

  given deliveryCodec: JsonValueCodec[Delivery] = JsonCodecMaker.make

  val ProductionReader: ReaderConfig = ReaderConfig.withAppendHexDumpToParseException(false)

  val DebugReader: ReaderConfig = ReaderConfig.withThrowReaderExceptionWithStackTrace(true).withHexDumpSize(1)

  // The hex dump shares the capped char buffer, so a limited reader reports without one.
  val LimitedReader: ReaderConfig =
    ProductionReader.withPreferredBufSize(64).withPreferredCharBufSize(32).withMaxCharBufSize(256)

  val TracingWriter: WriterConfig = WriterConfig.withThrowWriterExceptionWithStackTrace(true)
}
