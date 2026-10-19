/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_github_plokhotnyuk_jsoniter_scala.jsoniter_scala_core_3

import com.github.plokhotnyuk.jsoniter_scala.core.JsonReader
import com.github.plokhotnyuk.jsoniter_scala.core.JsonReaderException
import com.github.plokhotnyuk.jsoniter_scala.core.JsonValueCodec
import com.github.plokhotnyuk.jsoniter_scala.core.JsonWriter
import com.github.plokhotnyuk.jsoniter_scala.core.WriterConfig
import com.github.plokhotnyuk.jsoniter_scala.core.readFromArray
import com.github.plokhotnyuk.jsoniter_scala.core.readFromString
import com.github.plokhotnyuk.jsoniter_scala.core.writeToArray
import com.github.plokhotnyuk.jsoniter_scala.core.writeToString
import com.github.plokhotnyuk.jsoniter_scala.macros.JsonCodecMaker
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

import java.nio.charset.StandardCharsets.UTF_8

import EventRouterRawPayloadTest.*

/**
 * Raw JSON pass-through with `JsonReader.readRawValAsBytes` and `JsonWriter.writeRawVal`:
 * an event router reads macro-derived envelopes whose payload it never interprets, keeps
 * each payload byte for byte, re-routes the envelope with new headers, decodes a payload
 * into a typed model only for the event kinds it handles, and rejects truncated envelopes.
 */
class EventRouterRawPayloadTest {

  private val payloadText: String =
    """{ "orderId" : "o-77", "amount":12345678901234567890.123456789, "note":"caf""" + "\\u00e9" +
      """", "lines":[ 1, 2 ] }"""

  private val inbound: String =
    s"""{"id":"evt-1","kind":"order.created","headers":{"source":"shop"},"payload":  $payloadText  }"""

  @Test
  def payloadIsKeptAndForwardedByteForByte(): Unit = {
    val envelope: Envelope = readFromString[Envelope](inbound)

    assertEquals("evt-1", envelope.id)
    assertEquals("order.created", envelope.kind)
    // The raw value starts right after the colon, so the whitespace before it is kept too.
    assertEquals("  " + payloadText, envelope.payload.text)

    val forwarded: Envelope = envelope.copy(headers = envelope.headers + ("route" -> "billing"))
    val json: String = writeToString(forwarded)

    assertEquals(
      s"""{"id":"evt-1","kind":"order.created","headers":{"source":"shop","route":"billing"},"payload":  $payloadText}""",
      json
    )
    assertEquals(forwarded.payload.text, readFromArray[Envelope](writeToArray(forwarded)).payload.text)
  }

  @Test
  def payloadsOfEveryJsonKindArePassedThrough(): Unit = {
    val batch: String =
      """[{"id":"a","kind":"k","payload":"plain \"text\""},""" +
        """{"id":"b","kind":"k","payload":-0.5e-3},""" +
        """{"id":"c","kind":"k","payload":true},""" +
        """{"id":"d","kind":"k","payload":null},""" +
        """{"id":"e","kind":"k","payload":[[],{},[{"x":[null]}]]}]"""

    val envelopes: List[Envelope] = readFromString[List[Envelope]](batch)

    assertEquals(
      List("\"plain \\\"text\\\"\"", "-0.5e-3", "true", "null", """[[],{},[{"x":[null]}]]"""),
      envelopes.map(_.payload.text)
    )
    assertTrue(envelopes.forall(_.headers.isEmpty))
    assertEquals(batch, writeToString(envelopes))
  }

  @Test
  def onlyHandledKindsAreDecodedIntoTypedModels(): Unit = {
    val batch: String =
      s"""[$inbound,{"id":"evt-2","kind":"user.renamed","headers":{},"payload":{"userId":9,"name":"Bo"}}]"""

    val handled: List[OrderCreated] = readFromString[List[Envelope]](batch).collect {
      case envelope if envelope.kind == "order.created" => readFromArray[OrderCreated](envelope.payload.bytes)
    }

    assertEquals(
      List(OrderCreated("o-77", BigDecimal("12345678901234567890.123456789"), "caf\u00e9", List(1, 2))),
      handled
    )
  }

  @Test
  def prettyPrintedEnvelopeEmbedsPayloadUnchanged(): Unit = {
    val envelope: Envelope = Envelope("evt-3", "ping", Map.empty, RawJson("""{"a":[1,2]}"""))

    assertEquals(
      "{\n" +
        "  \"id\": \"evt-3\",\n" +
        "  \"kind\": \"ping\",\n" +
        "  \"payload\": {\"a\":[1,2]}\n" +
        "}",
      writeToString(envelope, WriterConfig.withIndentionStep(2))
    )
  }

  @Test
  def payloadIsValidatedOnlyWhenDecodedButTruncatedEnvelopesAreRejected(): Unit = {
    val malformed: Envelope = readFromString[Envelope]("""{"id":"x","kind":"order.created","payload":{"orderId":}}""")
    assertEquals("""{"orderId":}""", malformed.payload.text)
    val typed: JsonReaderException = assertThrows(
      classOf[JsonReaderException],
      () => readFromArray[OrderCreated](malformed.payload.bytes)
    )
    assertTrue(typed.getMessage.contains("offset: 0x"), typed.getMessage)

    val truncated: JsonReaderException = assertThrows(
      classOf[JsonReaderException],
      () => readFromString[Envelope]("""{"id":"x","kind":"k","payload":{"a":[1,2]""")
    )
    assertTrue(truncated.getMessage.startsWith("unexpected end of input"), truncated.getMessage)

    val missing: JsonReaderException = assertThrows(
      classOf[JsonReaderException],
      () => readFromString[Envelope]("""{"id":"x","kind":"k","headers":{}}""")
    )
    assertTrue(missing.getMessage.startsWith("missing required field \"payload\""), missing.getMessage)
  }
}

object EventRouterRawPayloadTest {

  /** An opaque JSON value kept as the exact UTF-8 bytes it was read from. */
  final class RawJson(val bytes: Array[Byte]) {
    def text: String = new String(bytes, UTF_8)
  }

  object RawJson {
    def apply(text: String): RawJson = new RawJson(text.getBytes(UTF_8))
  }

  final case class Envelope(id: String, kind: String, headers: Map[String, String], payload: RawJson)

  final case class OrderCreated(orderId: String, amount: BigDecimal, note: String, lines: List[Int])

  given rawJsonCodec: JsonValueCodec[RawJson] = new JsonValueCodec[RawJson] {
    override def decodeValue(in: JsonReader, default: RawJson): RawJson = new RawJson(in.readRawValAsBytes())

    override def encodeValue(x: RawJson, out: JsonWriter): Unit = out.writeRawVal(x.bytes)

    override def nullValue: RawJson = null
  }

  given envelopeCodec: JsonValueCodec[Envelope] = JsonCodecMaker.make

  given envelopesCodec: JsonValueCodec[List[Envelope]] = JsonCodecMaker.make

  given orderCreatedCodec: JsonValueCodec[OrderCreated] = JsonCodecMaker.make
}
