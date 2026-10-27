/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_lihaoyi.upickle_3

import java.io.StringWriter
import java.nio.charset.StandardCharsets

import scala.collection.mutable

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import upickle.core.ArrVisitor
import upickle.core.NoOpVisitor
import upickle.core.ObjVisitor
import upickle.core.StringVisitor
import upickle.core.Visitor
import upickle.default.*

/**
 * Event-driven processing through custom upickle.core.Visitor pipelines, as a log gateway
 * aggregates access logs without building a JSON tree and redacts secrets while it streams
 * payloads from JSON, MessagePack and typed values into renderers, ASTs and typed readers.
 */
class AccessLogVisitorPipelineTest {

  import AccessLogVisitorPipelineTest.*

  @Test
  def statisticsAreAggregatedFromJsonMessagePackAndTypedSources(): Unit = {
    val entries: Seq[AccessEntry] = Seq(
      AccessEntry("GET", "/api/orders", 200, 5120L, "10.0.0.1"),
      AccessEntry("POST", "/api/orders", 201, 734L, "10.0.0.2"),
      AccessEntry("GET", "/api/orders/77", 404, 120L, "10.0.0.1"),
      AccessEntry("GET", "/health", 200, 2L, "10.0.0.9"),
      AccessEntry("DELETE", "/api/orders/12", 500, 6000000000L, "10.0.0.3")
    )
    val json: String = write(entries)
    val msgPack: Array[Byte] = writeBinary(entries)

    val fromText: AccessStats = new AccessStats()
    ujson.transform(json, fromText)
    val fromBytes: AccessStats = new AccessStats()
    ujson.Readable.fromByteArray(json.getBytes(StandardCharsets.UTF_8)).transform(fromBytes)
    val fromMsgPack: AccessStats = new AccessStats()
    upack.transform(msgPack, fromMsgPack)
    val fromTyped: AccessStats = new AccessStats()
    transform(entries).to(fromTyped)

    for (stats: AccessStats <- Seq(fromText, fromBytes, fromMsgPack, fromTyped)) {
      assertEquals(5, stats.requests)
      assertEquals(6000005976L, stats.totalBytes)
      assertEquals(Map(200 -> 2, 201 -> 1, 404 -> 1, 500 -> 1), stats.statusCounts.toMap)
      assertEquals(2, stats.serverErrorsAndNotFound)
    }
  }

  @Test
  def redactingVisitorMasksSecretsWhileRenderingJson(): Unit = {
    val payload: String =
      """{"user":"ada","password":"hunter2","profile":{"email":"ada@example.org","pin":4321},""" +
        """"cards":[{"brand":"visa","card_number":"4111111111111111"}],"retries":3}"""

    val compact: String = ujson.transform(payload, Redactor(ujson.StringRenderer(), Secrets)).toString
    assertEquals(
      """{"user":"ada","password":"***","profile":{"email":"ada@example.org","pin":"***"},""" +
        """"cards":[{"brand":"visa","card_number":"***"}],"retries":3}""",
      compact
    )

    val pretty: String = ujson
      .transform(payload, Redactor(ujson.StringRenderer(indent = 2), Secrets).map((w: StringWriter) => w.toString))
    assertTrue(pretty.contains("\n  \"password\": \"***\""), pretty)
    assertFalse(pretty.contains("hunter2"), pretty)
    assertFalse(pretty.contains("4111111111111111"), pretty)
  }

  @Test
  def redactingVisitorFeedsTypedReadersAndAsts(): Unit = {
    val request: SignupRequest = SignupRequest(
      user = "grace",
      password = "correct horse battery staple",
      contact = Contact("grace@example.org", pin = "0042"),
      referrer = Some("newsletter")
    )

    val audited: SignupRequest = transform(request).to(Redactor(reader[SignupRequest], Secrets))
    assertEquals(SignupRequest("grace", "***", Contact("grace@example.org", "***"), Some("newsletter")), audited)

    val ast: ujson.Value = transform(request).to(Redactor(ujson.Value, Secrets))
    assertEquals("***", ast("password").str)
    assertEquals("***", ast("contact")("pin").str)
    assertEquals("grace@example.org", ast("contact")("email").str)

    val incoming: String = write(request)
    val sanitized: SignupRequest =
      ujson.Readable.fromString(incoming).transform(Redactor(reader[SignupRequest], Secrets))
    assertEquals(audited, sanitized)
  }

  @Test
  def typedValuesAreProjectedIntoOtherShapesWithoutIntermediateText(): Unit = {
    val entries: Seq[AccessEntry] = Seq(
      AccessEntry("GET", "/api/orders", 200, 5120L, "10.0.0.1"),
      AccessEntry("GET", "/api/orders/77", 404, 120L, "10.0.0.1"),
      AccessEntry("PUT", "/api/orders/77", 409, 64L, "10.0.0.5")
    )

    val audit: Seq[AuditRecord] = transform(entries).to[Seq[AuditRecord]]
    assertEquals(
      Seq(
        AuditRecord("/api/orders", 200, "10.0.0.1"),
        AuditRecord("/api/orders/77", 404, "10.0.0.1"),
        AuditRecord("/api/orders/77", 409, "10.0.0.5")
      ),
      audit
    )

    val failedPaths: Set[String] = ujson.Readable
      .fromString(write(entries))
      .transform(
        reader[Seq[AuditRecord]].map((records: Seq[AuditRecord]) => records.filter(_.status >= 400).map(_.path).toSet)
      )
    assertEquals(Set("/api/orders/77"), failedPaths)
  }
}

object AccessLogVisitorPipelineTest {

  /** Object keys whose values never leave the gateway in clear text. */
  val Secrets: Set[String] = Set("password", "card_number", "pin")

  case class AccessEntry(method: String, path: String, status: Int, bytes: Long, client: String) derives ReadWriter

  case class AuditRecord(path: String, status: Int, client: String) derives ReadWriter

  case class Contact(email: String, pin: String) derives ReadWriter

  case class SignupRequest(user: String, password: String, contact: Contact, referrer: Option[String])
      derives ReadWriter

  /** Counts requests, response sizes and status codes from visitor events alone. */
  final class AccessStats extends Visitor.Delegate[Unit, Unit](NoOpVisitor) {
    var requests: Int = 0
    var totalBytes: Long = 0L
    val statusCounts: mutable.TreeMap[Int, Int] = mutable.TreeMap.empty

    def serverErrorsAndNotFound: Int = statusCounts.collect { case (s, n) if s == 404 || s >= 500 => n }.sum

    override def visitArray(length: Int, index: Int): ArrVisitor[Unit, Unit] = new ArrVisitor[Unit, Unit] {
      def subVisitor: Visitor[?, ?] = EntryVisitor
      def visitValue(v: Unit, index: Int): Unit = requests += 1
      def visitEnd(index: Int): Unit = ()
    }

    private object EntryVisitor extends Visitor.Delegate[Unit, Unit](NoOpVisitor) {
      override def visitObject(length: Int, jsonableKeys: Boolean, index: Int): ObjVisitor[Unit, Unit] =
        new ObjVisitor[Unit, Unit] {
          private var key: String = ""
          def visitKey(index: Int): Visitor[?, ?] = StringVisitor
          def visitKeyValue(v: Any): Unit = key = v.toString
          def subVisitor: Visitor[?, ?] = key match {
            case "bytes" => NumberVisitor((n: Long) => totalBytes += n)
            case "status" => NumberVisitor((n: Long) => statusCounts.updateWith(n.toInt)(c => Some(c.getOrElse(0) + 1)))
            case _ => NoOpVisitor
          }
          def visitValue(v: Unit, index: Int): Unit = ()
          def visitEnd(index: Int): Unit = ()
        }
    }
  }

  /** Feeds integral numbers to a callback whether they arrive as JSON text or binary integers. */
  final case class NumberVisitor(sink: Long => Unit) extends Visitor.Delegate[Unit, Unit](NoOpVisitor) {
    override def visitFloat64StringParts(s: CharSequence, decIndex: Int, expIndex: Int, index: Int): Unit =
      sink(s.toString.toLong)
    override def visitInt32(i: Int, index: Int): Unit = sink(i.toLong)
    override def visitInt64(i: Long, index: Int): Unit = sink(i)
    override def visitUInt64(i: Long, index: Int): Unit = sink(i)
    override def visitFloat64(d: Double, index: Int): Unit = sink(d.toLong)
  }

  /** Forwards every event to the downstream visitor, masking the values of secret keys. */
  final case class Redactor[T, J](downstream: Visitor[T, J], secrets: Set[String])
      extends Visitor.Delegate[T, J](downstream) {

    override def visitArray(length: Int, index: Int): ArrVisitor[T, J] = {
      val arr: ArrVisitor[T, J] = downstream.visitArray(length, index)
      new ArrVisitor[T, J] {
        def subVisitor: Visitor[?, ?] = Redactor(arr.subVisitor, secrets)
        def visitValue(v: T, index: Int): Unit = arr.visitValue(v, index)
        def visitEnd(index: Int): J = arr.visitEnd(index)
      }
    }

    override def visitObject(length: Int, jsonableKeys: Boolean, index: Int): ObjVisitor[T, J] = {
      val obj: ObjVisitor[T, J] = downstream.visitObject(length, jsonableKeys, index)
      new ObjVisitor[T, J] {
        private var secret: Boolean = false
        def visitKey(index: Int): Visitor[?, ?] = captureKey(obj.visitKey(index), (k: String) => secret = secrets(k))
        def visitKeyValue(v: Any): Unit = obj.visitKeyValue(v)
        def subVisitor: Visitor[?, ?] = if (secret) mask(obj.subVisitor) else Redactor(obj.subVisitor, secrets)
        def visitValue(v: T, index: Int): Unit = obj.visitValue(v, index)
        def visitEnd(index: Int): J = obj.visitEnd(index)
      }
    }
  }

  private def captureKey[A, B](keys: Visitor[A, B], onKey: String => Unit): Visitor[A, B] =
    new Visitor.Delegate[A, B](keys) {
      override def visitString(s: CharSequence, index: Int): B = {
        onKey(s.toString)
        super.visitString(s, index)
      }
    }

  private def mask[A, B](values: Visitor[A, B]): Visitor[A, B] = new Visitor.Delegate[A, B](values) {
    override def visitString(s: CharSequence, index: Int): B = values.visitString(Masked, index)
    override def visitFloat64StringParts(s: CharSequence, decIndex: Int, expIndex: Int, index: Int): B =
      values.visitString(Masked, index)
    override def visitInt32(i: Int, index: Int): B = values.visitString(Masked, index)
    override def visitInt64(i: Long, index: Int): B = values.visitString(Masked, index)
    override def visitFloat64(d: Double, index: Int): B = values.visitString(Masked, index)
  }

  private val Masked: String = "***"
}
