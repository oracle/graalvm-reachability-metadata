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

import ChatMessageUnicodeTextTest.*

/**
 * Unicode text encoding and JSON string escaping on read and write: a chat service stores
 * messages with accented letters, CJK text, emoji, quotes and control characters, renders
 * them as UTF-8 or as ASCII-only `\uXXXX` escapes for a legacy relay, decodes escaped client
 * input including surrogate pairs, and rejects broken escapes, surrogates and UTF-8 bytes.
 */
class ChatMessageUnicodeTextTest {

  private val message: Message = Message(
    author = "Zo\u00eb",
    initial = '\u00c5',
    text = "Gr\u00fc\u00dfe \"team\"\n\tsee C:\\tmp \u65e5\u672c \ud83d\ude00 bell\u0001",
    reactions = Map("\ud83d\udc4d" -> 3, "\u2764" -> 1)
  )

  @Test
  def defaultWriterKeepsUnicodeAsUtf8AndEscapesOnlyWhatJsonRequires(): Unit = {
    val json: String = writeToString(message)

    assertEquals(
      "{\"author\":\"Zo\u00eb\",\"initial\":\"\u00c5\"," +
        "\"text\":\"Gr\u00fc\u00dfe \\\"team\\\"\\n\\tsee C:\\\\tmp \u65e5\u672c \ud83d\ude00 bell\\u0001\"," +
        "\"reactions\":{\"\ud83d\udc4d\":3,\"\u2764\":1}}",
      json
    )

    val bytes: Array[Byte] = writeToArray(message)
    assertEquals(json, new String(bytes, UTF_8))
    assertEquals(json.getBytes(UTF_8).length, bytes.length)
    assertEquals(message, readFromArray[Message](bytes))
    assertEquals(message, readFromString[Message](json))
  }

  @Test
  def legacyRelayReceivesAsciiOnlyEscapes(): Unit = {
    val asciiOnly: WriterConfig = WriterConfig.withEscapeUnicode(true)
    val bytes: Array[Byte] = writeToArray(message, asciiOnly)

    assertTrue(bytes.forall(b => b >= 0), "relay payload must be 7-bit ASCII")
    val json: String = new String(bytes, UTF_8)
    assertTrue(json.contains("\"author\":\"Zo\\u00eb\""), json)
    assertTrue(json.contains("\"initial\":\"\\u00c5\""), json)
    assertTrue(json.contains("Gr\\u00fc\\u00dfe"), json)
    assertTrue(json.contains("\\u65e5\\u672c \\ud83d\\ude00"), json)
    assertTrue(json.contains("\"\\ud83d\\udc4d\":3"), json)
    assertEquals(message, readFromArray[Message](bytes))
  }

  @Test
  def escapedClientInputIsDecoded(): Unit = {
    val client: String =
      """{
        |  "author": "\u005a\u006f\u00EB",
        |  "initial": "\u00c5",
        |  "text": "caf\u00e9 \/ \ud83c\udf89 \"ok\"\b\f\r",
        |  "reactions": {"\ud83d\udc4d": 2, "plain": 0}
        |}""".stripMargin

    val decoded: Message = readFromString[Message](client)

    assertEquals("Zo\u00eb", decoded.author)
    assertEquals('\u00c5', decoded.initial)
    assertEquals("caf\u00e9 / \ud83c\udf89 \"ok\"\b\f\r", decoded.text)
    assertEquals(0x1f389, decoded.text.codePointAt(7))
    assertEquals(Map("\ud83d\udc4d" -> 2, "plain" -> 0), decoded.reactions)
  }

  @Test
  def brokenTextIsRejectedOnReadAndWrite(): Unit = {
    def rejectedRead(json: Array[Byte]): String =
      assertThrows(classOf[JsonReaderException], () => readFromArray[Message](json, Diagnostics)).getMessage

    def withText(text: String): Array[Byte] =
      ("{\"author\":\"a\",\"initial\":\"a\",\"text\":\"" + text + "\",\"reactions\":{}}").getBytes(UTF_8)

    val badEscape: String = rejectedRead(withText("\\x41"))
    assertTrue(badEscape.contains("illegal escape sequence"), badEscape)

    val loneSurrogate: String = rejectedRead(withText("\\ud83d\\u0041"))
    assertTrue(loneSurrogate.contains("illegal surrogate character pair"), loneSurrogate)

    val rawControl: String = rejectedRead(withText("line\nbreak"))
    assertTrue(rawControl.contains("unescaped control character"), rawControl)

    val Array(head: String, tail: String) = new String(withText("|"), UTF_8).split('|'): @unchecked
    val malformedUtf8: Array[Byte] = head.getBytes(UTF_8) ++ Array(0xc3.toByte, 0x28.toByte) ++ tail.getBytes(UTF_8)
    val badBytes: String = rejectedRead(malformedUtf8)
    assertTrue(badBytes.contains("malformed byte(s)"), badBytes)

    val emojiInitial: String = rejectedRead(
      "{\"author\":\"a\",\"initial\":\"\ud83d\ude00\",\"text\":\"\",\"reactions\":{}}".getBytes(UTF_8)
    )
    assertTrue(emojiInitial.contains("illegal surrogate character"), emojiInitial)

    val truncatedEmoji: Message = message.copy(text = "cut \ud83d")
    val writeError: JsonWriterException =
      assertThrows(classOf[JsonWriterException], () => writeToArray(truncatedEmoji))
    assertTrue(writeError.getMessage.contains("illegal char sequence of surrogate pair"), writeError.getMessage)
  }
}

object ChatMessageUnicodeTextTest {

  final case class Message(author: String, initial: Char, text: String, reactions: Map[String, Int])

  val Diagnostics: ReaderConfig = ReaderConfig.withAppendHexDumpToParseException(false)

  given messageCodec: JsonValueCodec[Message] = JsonCodecMaker.make
}
