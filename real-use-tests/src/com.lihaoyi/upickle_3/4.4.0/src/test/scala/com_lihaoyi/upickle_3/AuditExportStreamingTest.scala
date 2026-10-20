/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_lihaoyi.upickle_3

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.StringWriter
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path

import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertInstanceOf
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import upickle.core.TraceVisitor.TraceException
import upickle.default.*

/**
 * Streaming JSON output and input through upickle's stream, writeTo, writeToOutputStream and
 * writeToByteArray with indent, sortKeys and escapeUnicode options and reads from file, stream
 * and byte sources, as a compliance service exports audit trails to disk and re-imports them.
 */
class AuditExportStreamingTest {

  import AuditExportStreamingTest.*

  @TempDir
  var exportDir: Path = null

  @Test
  def trailIsStreamedToAFileWithOsLibAndReadBackFromThePath(): Unit = {
    val trail: AuditTrail = sampleTrail()
    val exportFile: os.Path = os.Path(exportDir) / "trail-2024-q1.json"

    os.write(exportFile, stream(trail, indent = 2))

    val text: String = os.read(exportFile)
    assertTrue(text.startsWith("{\n  \"tenant\": \"acme\""), text)
    assertTrue(text.contains("\n      \"actor\": \"alice\""), text)
    assertEquals(trail, read[AuditTrail](exportFile.toNIO))
    assertEquals(trail, read[AuditTrail](os.read.stream(exportFile)))
  }

  @Test
  def sortedKeysMakeExportsDiffableRegardlessOfInsertionOrder(): Unit = {
    val first: Map[String, String] = Map("zone" -> "eu-1", "app" -> "billing", "env" -> "prod")
    val second: Map[String, String] = Map("env" -> "prod", "zone" -> "eu-1", "app" -> "billing")

    val firstOut: StringWriter = StringWriter()
    writeTo(first, firstOut, sortKeys = true)
    val secondOut: StringWriter = StringWriter()
    writeTo(second, secondOut, sortKeys = true)

    assertEquals("""{"app":"billing","env":"prod","zone":"eu-1"}""", firstOut.toString)
    assertEquals(firstOut.toString, secondOut.toString)
    val event: AuditEvent = AuditEvent(7L, "bob", "LOGIN", Map("ip" -> "10.0.0.5"))
    assertEquals(
      """{"action":"LOGIN","actor":"bob","details":{"ip":"10.0.0.5"},"sequence":7}""",
      write(event, sortKeys = true)
    )
  }

  @Test
  def escapedUnicodeKeepsLegacyAsciiConsumersWorking(): Unit = {
    val event: AuditEvent = AuditEvent(3L, "J\u00fcrgen", "RENAME", Map("to" -> "\u65e5\u672c"))

    val ascii: Array[Byte] = writeToByteArray(event, escapeUnicode = true)
    val utf8: Array[Byte] = writeToByteArray(event)

    val asciiText: String = String(ascii, StandardCharsets.US_ASCII)
    assertTrue(asciiText.contains("J\\u00fcrgen"), asciiText)
    assertTrue(asciiText.contains("\\u65e5\\u672c"), asciiText)
    assertTrue(ascii.forall(b => b >= 0), "every byte is ASCII")
    assertFalse(String(utf8, StandardCharsets.UTF_8).contains("\\u"), "UTF-8 output keeps characters")
    assertEquals(event, read[AuditEvent](ascii))
    assertEquals(event, read[AuditEvent](utf8))
  }

  @Test
  def newlineDelimitedEventsAreAppendedAndReadLineByLine(): Unit = {
    val events: Seq[AuditEvent] = sampleTrail().events
    val logFile: Path = exportDir.resolve("events.ndjson")

    val out: ByteArrayOutputStream = ByteArrayOutputStream()
    events.foreach { event =>
      writeToOutputStream(event, out)
      out.write('\n')
    }
    Files.write(logFile, out.toByteArray)

    val lines: Seq[String] = os.read.lines(os.Path(logFile)).filter(_.nonEmpty)
    val restored: Seq[AuditEvent] = lines.map(line => read[AuditEvent](line))
    assertEquals(events, restored)
    assertEquals(events.map(_.sequence), lines.map(line => ujson.read(line)("sequence").num.toLong))
  }

  @Test
  def importerReadsAnUploadedStreamAndRejectsTruncatedUploads(): Unit = {
    val trail: AuditTrail = sampleTrail()
    val upload: Array[Byte] = writeToByteArray(trail)

    val imported: AuditTrail = read[AuditTrail](ByteArrayInputStream(upload))
    assertEquals(trail, imported)
    assertArrayEquals(upload, writeToByteArray(imported))

    val truncated: Array[Byte] = upload.take(upload.length - 5)
    val failure: TraceException = assertThrows(
      classOf[TraceException],
      () => read[AuditTrail](ByteArrayInputStream(truncated))
    )
    assertInstanceOf(classOf[ujson.IncompleteParseException], failure.getCause)
    assertEquals("$['events'][2]['details']", failure.jsonPath)
  }
}

object AuditExportStreamingTest {

  final case class AuditEvent(
      sequence: Long,
      actor: String,
      action: String,
      details: Map[String, String]
  ) derives ReadWriter

  final case class AuditTrail(tenant: String, events: Seq[AuditEvent]) derives ReadWriter

  def sampleTrail(): AuditTrail = AuditTrail(
    tenant = "acme",
    events = Seq(
      AuditEvent(1L, "alice", "LOGIN", Map("ip" -> "10.0.0.1")),
      AuditEvent(2L, "alice", "EXPORT", Map("report" -> "q1-revenue", "format" -> "csv")),
      AuditEvent(3L, "carol", "LOGOUT", Map.empty)
    )
  )
}
