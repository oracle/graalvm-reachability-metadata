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
import com.github.plokhotnyuk.jsoniter_scala.core.readFromArray
import com.github.plokhotnyuk.jsoniter_scala.core.readFromString
import com.github.plokhotnyuk.jsoniter_scala.core.writeToString
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

import java.nio.charset.StandardCharsets

import scala.collection.mutable.ListBuffer

import LogIngestCharBufDispatchTest.*

/**
 * Allocation-free field-name and enum-value dispatch over the reader's internal char buffer
 * with `readKeyAsCharBuf`, `readStringAsCharBuf`, `charBufToHashCode`, `isCharBufEqualsTo`
 * and `JsonReader.toHashCode`: a log ingestion pipeline decodes shipped log entries through
 * precomputed key hash tables, matches severity levels and the entry-kind discriminator
 * without building strings, and rejects unknown, duplicated and illegal keys and values with
 * the reader's own key, enum and discriminator errors.
 */
class LogIngestCharBufDispatchTest {

  private val record: LogRecord = LogRecord(
    ts = 1777622400123L,
    level = Level.Warn,
    logger = "billing.invoice",
    message = "retrying \"charge\" for invoice 42",
    tags = Map("region" -> "eu-west-1", "tenant" -> "acme")
  )

  private val gauge: MetricSample = MetricSample(ts = 1777622401000L, name = "queue.depth", value = 17.5)

  @Test
  def entriesRoundTripThroughHashDispatchedCodecs(): Unit = {
    val json: String = writeToString[Entry](record)
    assertEquals(
      """{"kind":"log","ts":1777622400123,"level":"warn","logger":"billing.invoice",""" +
        """"msg":"retrying \"charge\" for invoice 42","tags":{"region":"eu-west-1","tenant":"acme"}}""",
      json
    )
    assertEquals(record, readFromString[Entry](json))

    val metricJson: String = writeToString[Entry](gauge)
    assertEquals("""{"kind":"metric","ts":1777622401000,"name":"queue.depth","value":17.5}""", metricJson)
    assertEquals(gauge, readFromString[Entry](metricJson))
  }

  @Test
  def shippedBatchIsDecodedRegardlessOfFieldOrderAndCounted(): Unit = {
    val shipped: String =
      """[
        |  {"kind": "log", "msg": "boot", "level": "INFO", "logger": "app", "ts": 1},
        |  {"kind": "metric", "value": 0.25, "name": "cpu", "ts": 2},
        |  {"kind": "log", "tags": {}, "ts": 3, "logger": "db.pool", "level": "Error", "msg": "timeout"},
        |  {"kind": "log", "ts": 4, "level": "debug", "logger": "app", "msg": "caf\u00e9 \ud83d\ude80"}
        |]""".stripMargin
    val entries: List[Entry] = readFromArray[List[Entry]](shipped.getBytes(StandardCharsets.UTF_8))

    assertEquals(
      List(
        LogRecord(1L, Level.Info, "app", "boot", Map.empty),
        MetricSample(2L, "cpu", 0.25),
        LogRecord(3L, Level.Error, "db.pool", "timeout", Map.empty),
        LogRecord(4L, Level.Debug, "app", "caf\u00e9 \ud83d\ude80", Map.empty)
      ),
      entries
    )
    val perLevel: Map[Level, Int] =
      entries.collect { case log: LogRecord => log.level }.groupMapReduce(identity)(_ => 1)(_ + _)
    assertEquals(Map(Level.Info -> 1, Level.Error -> 1, Level.Debug -> 1), perLevel)
  }

  @Test
  def keysWhoseHashesCollideAreStillToldApartByContent(): Unit = {
    assertEquals(
      JsonReader.toHashCode("ts".toCharArray, 2),
      JsonReader.toHashCode("uT".toCharArray, 2),
      "the probe key must share the hash code of a known field to exercise the content check"
    )
    val json: String = """{"kind":"metric","uT":9,"name":"x","value":1.0}"""
    val error: String = assertThrows(classOf[JsonReaderException], () => readFromString[Entry](json)).getMessage
    assertTrue(error.contains("unexpected field \"uT\""), error)
    assertEquals(MetricSample(9L, "x", 1.0), readFromString[Entry](json.replace("uT", "ts")))
  }

  @Test
  def illegalEntriesAreRejectedWithReaderDiagnostics(): Unit = {
    def rejection(json: String): String =
      assertThrows(classOf[JsonReaderException], () => readFromString[Entry](json)).getMessage

    val unknownKey: String = rejection("""{"kind":"log","ts":1,"level":"info","logger":"a","msg":"b","host":"h1"}""")
    assertTrue(unknownKey.contains("unexpected field \"host\""), unknownKey)

    val duplicated: String = rejection("""{"kind":"log","ts":1,"level":"info","ts":2,"logger":"a","msg":"b"}""")
    assertTrue(duplicated.contains("duplicated field \"ts\""), duplicated)

    val badLevel: String = rejection("""{"kind":"log","ts":1,"level":"fatal","logger":"a","msg":"b"}""")
    assertTrue(badLevel.contains("illegal enum value \"fatal\""), badLevel)

    val badKind: String = rejection("""{"kind":"trace","ts":1}""")
    assertTrue(badKind.contains("illegal value of discriminator field \"kind\""), badKind)

    val kindNotFirst: String = rejection("""{"ts":1,"kind":"log"}""")
    assertTrue(kindNotFirst.contains("illegal discriminator"), kindNotFirst)

    val missingMessage: String = rejection("""{"kind":"log","ts":1,"level":"info","logger":"a"}""")
    assertTrue(missingMessage.contains("missing required field \"msg\""), missingMessage)
  }
}

object LogIngestCharBufDispatchTest {

  enum Level(val wire: String) {
    case Trace extends Level("trace")
    case Debug extends Level("debug")
    case Info extends Level("info")
    case Warn extends Level("warn")
    case Error extends Level("error")
  }

  sealed trait Entry {
    def ts: Long
  }

  final case class LogRecord(ts: Long, level: Level, logger: String, message: String, tags: Map[String, String])
    extends Entry

  final case class MetricSample(ts: Long, name: String, value: Double) extends Entry

  /**
   * Maps wire names to dense indexes by the hash code the reader computes over its char
   * buffer, so a decoder identifies a key or a value without allocating a `String`.
   */
  final class HashDispatch(names: IndexedSeq[String]) {
    private val byHash: Map[Int, IndexedSeq[Int]] =
      names.indices.groupBy(i => JsonReader.toHashCode(names(i).toCharArray, names(i).length))

    /** Returns the index of the name held in the first `len` chars of the buffer, or -1. */
    def lookup(in: JsonReader, len: Int): Int =
      byHash.get(in.charBufToHashCode(len)) match {
        case Some(candidates) => candidates.find(i => in.isCharBufEqualsTo(len, names(i))).getOrElse(-1)
        case None => -1
      }
  }

  private val kinds: HashDispatch = HashDispatch(IndexedSeq("log", "metric"))

  /** Accepts each level in lower, upper and capitalised spelling: index / 3 is the ordinal. */
  private val levels: HashDispatch =
    HashDispatch(Level.values.toIndexedSeq.flatMap(l => IndexedSeq(l.wire, l.wire.toUpperCase, l.wire.capitalize)))

  private val logFields: HashDispatch = HashDispatch(IndexedSeq("ts", "level", "logger", "msg", "tags"))
  private val metricFields: HashDispatch = HashDispatch(IndexedSeq("ts", "name", "value"))

  /** Visits keys as char-buffer lengths up to the closing `}`; a key must come next. */
  private def fieldLoop(in: JsonReader)(field: Int => Unit): Unit = {
    var more: Boolean = true
    while (more) {
      field(in.readKeyAsCharBuf())
      more = in.isNextToken(',')
    }
    if (!in.isCurrentToken('}')) in.objectEndOrCommaError()
  }

  /** Marks the field at `index` as read, reporting a repeat through the reader's key error. */
  private def claim(in: JsonReader, seen: Array[Boolean], index: Int, len: Int): Unit =
    if (seen(index)) in.duplicatedKeyError(len) else seen(index) = true

  given entryCodec: JsonValueCodec[Entry] = new JsonValueCodec[Entry] {
    override def nullValue: Entry = null

    override def encodeValue(x: Entry, out: JsonWriter): Unit = {
      out.writeObjectStart()
      out.writeNonEscapedAsciiKey("kind")
      x match {
        case log: LogRecord =>
          out.writeNonEscapedAsciiVal("log")
          out.writeNonEscapedAsciiKey("ts")
          out.writeVal(log.ts)
          out.writeNonEscapedAsciiKey("level")
          out.writeNonEscapedAsciiVal(log.level.wire)
          out.writeNonEscapedAsciiKey("logger")
          out.writeVal(log.logger)
          out.writeNonEscapedAsciiKey("msg")
          out.writeVal(log.message)
          out.writeNonEscapedAsciiKey("tags")
          out.writeObjectStart()
          log.tags.foreach { (key, value) =>
            out.writeKey(key)
            out.writeVal(value)
          }
          out.writeObjectEnd()
        case metric: MetricSample =>
          out.writeNonEscapedAsciiVal("metric")
          out.writeNonEscapedAsciiKey("ts")
          out.writeVal(metric.ts)
          out.writeNonEscapedAsciiKey("name")
          out.writeVal(metric.name)
          out.writeNonEscapedAsciiKey("value")
          out.writeVal(metric.value)
      }
      out.writeObjectEnd()
    }

    /** Requires the `kind` discriminator as the first key, then dispatches to the subtype. */
    override def decodeValue(in: JsonReader, default: Entry): Entry =
      if (!in.isNextToken('{')) in.readNullOrTokenError(default, '{')
      else if (in.isNextToken('}')) in.discriminatorError()
      else {
        in.rollbackToken()
        if (!in.isCharBufEqualsTo(in.readKeyAsCharBuf(), "kind")) in.discriminatorError()
        val kind: Int = kinds.lookup(in, in.readStringAsCharBuf())
        if (kind < 0) in.discriminatorValueError("kind")
        if (in.isNextToken(',')) {
          if (kind == 0) decodeLog(in) else decodeMetric(in)
        } else if (in.isCurrentToken('}')) in.requiredFieldError("ts")
        else in.objectEndOrCommaError()
      }

    private def decodeLog(in: JsonReader): LogRecord = {
      val seen: Array[Boolean] = new Array[Boolean](5)
      var ts: Long = 0L
      var level: Level = null
      var logger: String = null
      var message: String = null
      var tags: Map[String, String] = Map.empty
      fieldLoop(in) { len =>
        val index: Int = logFields.lookup(in, len)
        if (index < 0) in.unexpectedKeyError(len)
        claim(in, seen, index, len)
        index match {
          case 0 => ts = in.readLong()
          case 1 =>
            val valueLen: Int = in.readStringAsCharBuf()
            val spelling: Int = levels.lookup(in, valueLen)
            if (spelling < 0) in.enumValueError(valueLen)
            level = Level.fromOrdinal(spelling / 3)
          case 2 => logger = in.readString(null)
          case 3 => message = in.readString(null)
          case 4 => tags = decodeTags(in)
        }
      }
      if (!seen(0)) in.requiredFieldError("ts")
      if (level == null) in.requiredFieldError("level")
      if (logger == null) in.requiredFieldError("logger")
      if (message == null) in.requiredFieldError("msg")
      LogRecord(ts, level, logger, message, tags)
    }

    /** Tag keys are free-form, so unlike field names they are materialised as strings. */
    private def decodeTags(in: JsonReader): Map[String, String] = {
      if (!in.isNextToken('{')) in.objectStartOrNullError()
      val collected: ListBuffer[(String, String)] = ListBuffer.empty
      if (!in.isNextToken('}')) {
        in.rollbackToken()
        var more: Boolean = true
        while (more) {
          collected += in.readKeyAsString() -> in.readString(null)
          more = in.isNextToken(',')
        }
        if (!in.isCurrentToken('}')) in.objectEndOrCommaError()
      }
      collected.toMap
    }

    private def decodeMetric(in: JsonReader): MetricSample = {
      val seen: Array[Boolean] = new Array[Boolean](3)
      var ts: Long = 0L
      var name: String = null
      var value: Double = 0.0
      fieldLoop(in) { len =>
        val index: Int = metricFields.lookup(in, len)
        if (index < 0) in.unexpectedKeyError(len)
        claim(in, seen, index, len)
        index match {
          case 0 => ts = in.readLong()
          case 1 => name = in.readString(null)
          case 2 => value = in.readDouble()
        }
      }
      if (!seen(0)) in.requiredFieldError("ts")
      if (!seen(1)) in.requiredFieldError("name")
      if (!seen(2)) in.requiredFieldError("value")
      MetricSample(ts, name, value)
    }
  }

  /** Delegates each element to the entry codec, so one batch mixes log and metric entries. */
  given batchCodec: JsonValueCodec[List[Entry]] = new JsonValueCodec[List[Entry]] {
    override def nullValue: List[Entry] = Nil

    override def encodeValue(x: List[Entry], out: JsonWriter): Unit = {
      out.writeArrayStart()
      x.foreach(entry => entryCodec.encodeValue(entry, out))
      out.writeArrayEnd()
    }

    override def decodeValue(in: JsonReader, default: List[Entry]): List[Entry] =
      if (in.isNextToken('[')) {
        val buffer: ListBuffer[Entry] = ListBuffer.empty
        if (!in.isNextToken(']')) {
          in.rollbackToken()
          var more: Boolean = true
          while (more) {
            buffer += entryCodec.decodeValue(in, entryCodec.nullValue)
            more = in.isNextToken(',')
          }
          if (!in.isCurrentToken(']')) in.arrayEndOrCommaError()
        }
        buffer.toList
      } else in.readNullOrTokenError(default, '[')
  }
}
