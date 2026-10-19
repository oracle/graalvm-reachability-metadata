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
import com.github.plokhotnyuk.jsoniter_scala.core.WriterConfig
import com.github.plokhotnyuk.jsoniter_scala.core.readFromStream
import com.github.plokhotnyuk.jsoniter_scala.core.scanJsonArrayFromStream
import com.github.plokhotnyuk.jsoniter_scala.core.scanJsonValuesFromStream
import com.github.plokhotnyuk.jsoniter_scala.core.writeToStream
import com.github.plokhotnyuk.jsoniter_scala.macros.JsonCodecMaker
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.nio.charset.StandardCharsets.UTF_8

import scala.collection.mutable.ArrayBuffer

import AuditLogStreamingTest.*

/**
 * Stream-based reading and scanning of JSON values from `InputStream` and writing to
 * `OutputStream`: an audit service appends events as newline-delimited JSON, then replays
 * the log value by value and a large exported array element by element through small read
 * buffers, stopping early when it has found what it needs and rejecting a truncated log.
 */
class AuditLogStreamingTest {

  /** A read buffer far smaller than one event, so every value spans several refills. */
  private val smallBuffers: ReaderConfig = ReaderConfig.withPreferredBufSize(32).withPreferredCharBufSize(32)

  private val events: Vector[AuditEvent] = Vector.tabulate(200) { i =>
    AuditEvent(
      seq = i.toLong,
      actor = if (i % 3 == 0) "admin" else s"user-$i",
      action = if (i % 10 == 7) "delete" else "update",
      resource = s"/documents/${i * 17}",
      details = Map("ip" -> s"10.0.${i % 256}.1", "note" -> "caf\u00e9 \u2192 \"quoted\"")
    )
  }

  /** Appends every event as one line of NDJSON, the way the service writes its log file. */
  private def writeLog(target: Seq[AuditEvent]): Array[Byte] = {
    val out: ByteArrayOutputStream = new ByteArrayOutputStream()
    target.foreach { event =>
      writeToStream(event, out)
      out.write('\n')
    }
    out.toByteArray
  }

  @Test
  def ndjsonLogReplaysEveryEventInOrder(): Unit = {
    val log: Array[Byte] = writeLog(events)
    val lines: Array[String] = new String(log, UTF_8).split("\n")
    assertEquals(200, lines.length)
    assertTrue(lines(0).startsWith("""{"seq":0,"actor":"admin","action":"update""""), lines(0))

    val replayed: ArrayBuffer[AuditEvent] = ArrayBuffer.empty
    val in: InputStream = new ByteArrayInputStream(log)
    scanJsonValuesFromStream[AuditEvent](in, smallBuffers) { event =>
      replayed += event
      true
    }

    assertEquals(events, replayed.toVector)
  }

  @Test
  def scanStopsAtFirstDeletionWithoutVisitingTheRest(): Unit = {
    val in: InputStream = new ByteArrayInputStream(writeLog(events))
    var visited: Int = 0
    var firstDeletion: Option[AuditEvent] = None

    scanJsonValuesFromStream[AuditEvent](in, smallBuffers) { event =>
      visited += 1
      if (event.action == "delete") {
        firstDeletion = Some(event)
        false
      } else true
    }

    assertEquals(Some(events(7)), firstDeletion)
    assertEquals(8, visited)
  }

  @Test
  def exportedArrayIsAggregatedElementByElement(): Unit = {
    val out: ByteArrayOutputStream = new ByteArrayOutputStream()
    writeToStream(events.toList, out, WriterConfig.withIndentionStep(2))
    val exported: Array[Byte] = out.toByteArray
    assertTrue(new String(exported, UTF_8).startsWith("[\n  {\n    \"seq\": 0,"))

    val perActor: scala.collection.mutable.Map[String, Int] = scala.collection.mutable.Map.empty
    var lastSeq: Long = -1L
    scanJsonArrayFromStream[AuditEvent](new ByteArrayInputStream(exported), smallBuffers) { event =>
      assertEquals(lastSeq + 1, event.seq)
      lastSeq = event.seq
      perActor.updateWith(event.actor)(count => Some(count.getOrElse(0) + 1))
      true
    }

    assertEquals(199L, lastSeq)
    assertEquals(67, perActor("admin"))
    assertEquals(201 - 67, perActor.size)
    assertEquals(events.toList, readFromStream[List[AuditEvent]](new ByteArrayInputStream(exported), smallBuffers))
  }

  @Test
  def truncatedLogIsRejectedAfterTheCompleteEvents(): Unit = {
    val log: Array[Byte] = writeLog(events.take(3))
    val truncated: Array[Byte] = log.take(log.length - 20)
    val delivered: ArrayBuffer[Long] = ArrayBuffer.empty

    val error: JsonReaderException = assertThrows(
      classOf[JsonReaderException],
      () => scanJsonValuesFromStream[AuditEvent](new ByteArrayInputStream(truncated), smallBuffers) { event =>
        delivered += event.seq
        true
      }
    )

    assertEquals(Seq(0L, 1L), delivered.toSeq)
    assertTrue(error.getMessage.contains("unexpected end of input"), error.getMessage)
  }
}

object AuditLogStreamingTest {

  final case class AuditEvent(seq: Long, actor: String, action: String, resource: String, details: Map[String, String])

  given auditEventCodec: JsonValueCodec[AuditEvent] = JsonCodecMaker.make

  given auditEventListCodec: JsonValueCodec[List[AuditEvent]] = JsonCodecMaker.make
}
