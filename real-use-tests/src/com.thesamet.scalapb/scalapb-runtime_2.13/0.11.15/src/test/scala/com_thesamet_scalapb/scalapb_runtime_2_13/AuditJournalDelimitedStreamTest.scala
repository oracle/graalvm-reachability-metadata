/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_thesamet_scalapb.scalapb_runtime_2_13

import com.google.protobuf.InvalidProtocolBufferException
import com.google.protobuf.struct.ListValue
import com.google.protobuf.struct.NullValue
import com.google.protobuf.struct.Struct
import com.google.protobuf.struct.Value
import com.google.protobuf.timestamp.Timestamp
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir

import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.InputStream
import java.io.OutputStream
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardOpenOption
import scala.collection.mutable.ListBuffer
import scala.util.Using

/**
 * Exercises length-delimited binary streaming of generated messages: `writeDelimitedTo`,
 * `parseDelimitedFrom` and `streamFromDelimitedInput` move a sequence of `Timestamp` and
 * `Struct` messages through a file on disk.
 *
 * An admin service keeps an append-only audit journal. The file starts with a `Timestamp`
 * header recording when the journal was opened, followed by one `Struct` per audited action.
 * Sessions append to the journal across restarts, a reporting job streams it back lazily, and
 * recovery after a crash keeps every complete entry before a torn final write.
 */
class AuditJournalDelimitedStreamTest {
  @Test
  def appendedSessionsAreReadBackInOrderAfterTheHeader(@TempDir dir: Path): Unit = {
    val journal: Path = dir.resolve("audit.journal")
    val openedAt: Timestamp = Timestamp(seconds = 1760000000L, nanos = 125000000)

    AuditJournal.create(journal, openedAt)
    AuditJournal.append(journal, Seq(
      AuditEntry("alice", "user.create", Map("target" -> "bob", "quota" -> 10)),
      AuditEntry("alice", "role.grant", Map("target" -> "bob", "roles" -> List("viewer", "editor")))
    ))
    AuditJournal.append(journal, Seq(
      AuditEntry("bob", "login", Map("mfa" -> true, "device" -> Map("os" -> "linux", "trusted" -> false))),
      AuditEntry("carol", "user.delete", Map("target" -> "dave", "reason" -> None))
    ))

    val (header: Timestamp, entries: List[AuditEntry]) = AuditJournal.readAll(journal)

    assertEquals(openedAt, header)
    assertEquals(List("user.create", "role.grant", "login", "user.delete"), entries.map(_.action))
    assertEquals(List("alice", "alice", "bob", "carol"), entries.map(_.actor))
    assertEquals(Map("target" -> "bob", "roles" -> List("viewer", "editor")), entries(1).details)
    assertEquals(
      Map("mfa" -> true, "device" -> Map("os" -> "linux", "trusted" -> false)),
      entries(2).details
    )
    assertEquals(Map("target" -> "dave", "reason" -> None), entries(3).details)
    assertEquals(10.0, entries.head.details("quota"))
  }

  @Test
  def reportingStreamsEntriesLazilyAndCountsActionsPerActor(@TempDir dir: Path): Unit = {
    val journal: Path = dir.resolve("report.journal")
    AuditJournal.create(journal, Timestamp(seconds = 1760003600L))
    AuditJournal.append(journal, (1 to 250).map { i =>
      val actor: String = if (i % 5 == 0) "ops-bot" else s"user-${i % 3}"
      AuditEntry(actor, if (i % 2 == 0) "read" else "write", Map("sequence" -> i))
    })

    val perActor: Map[String, Int] = Using.resource(openForRead(journal)) { in =>
      Timestamp.parseDelimitedFrom(in).get
      Struct
        .streamFromDelimitedInput(in)
        .map(AuditEntry.fromStruct)
        .filter(_.action == "write")
        .groupMapReduce(_.actor)(_ => 1)(_ + _)
    }

    assertEquals(125, perActor.values.sum)
    assertEquals(25, perActor("ops-bot"))
    assertEquals(Set("ops-bot", "user-0", "user-1", "user-2"), perActor.keySet)

    val firstFive: List[Int] = Using.resource(openForRead(journal)) { in =>
      Timestamp.parseDelimitedFrom(in).get
      Struct
        .streamFromDelimitedInput(in)
        .take(5)
        .map(s => AuditEntry.fromStruct(s).details("sequence").asInstanceOf[Double].toInt)
        .toList
    }
    assertEquals(List(1, 2, 3, 4, 5), firstFive)
  }

  @Test
  def recoveryKeepsCompleteEntriesBeforeATornFinalWrite(@TempDir dir: Path): Unit = {
    val journal: Path = dir.resolve("crashed.journal")
    AuditJournal.create(journal, Timestamp(seconds = 1760007200L))
    AuditJournal.append(journal, Seq(
      AuditEntry("alice", "export", Map("rows" -> 1200)),
      AuditEntry("alice", "download", Map("file" -> "export.csv"))
    ))
    val intactSize: Long = Files.size(journal)
    AuditJournal.append(journal, Seq(
      AuditEntry("mallory", "bulk.delete", Map("table" -> "invoices", "rows" -> 9000))
    ))

    val bytes: Array[Byte] = Files.readAllBytes(journal)
    val tornLength: Int = intactSize.toInt + (bytes.length - intactSize.toInt) / 2
    Files.write(journal, bytes.take(tornLength))

    val strict: InvalidProtocolBufferException =
      assertThrows(classOf[InvalidProtocolBufferException], () => AuditJournal.readAll(journal))
    assertTrue(strict.getMessage.nonEmpty)

    val recovered: Recovery = AuditJournal.recover(journal)
    assertEquals(List("export", "download"), recovered.entries.map(_.action))
    assertEquals(intactSize, recovered.validBytes)

    AuditJournal.truncate(journal, recovered.validBytes)
    AuditJournal.append(journal, Seq(AuditEntry("system", "journal.repaired", Map("dropped" -> 1))))
    val (_, afterRepair: List[AuditEntry]) = AuditJournal.readAll(journal)
    assertEquals(List("export", "download", "journal.repaired"), afterRepair.map(_.action))
    assertEquals(Map("dropped" -> 1.0), afterRepair.last.details)
  }

  private def openForRead(journal: Path): InputStream =
    new BufferedInputStream(Files.newInputStream(journal))

  /** One audited action; `details` holds JSON-like values that are stored as a `Struct`. */
  case class AuditEntry(actor: String, action: String, details: Map[String, Any]) {
    def toStruct: Struct =
      Struct(fields = Map(
        "actor" -> Value(Value.Kind.StringValue(actor)),
        "action" -> Value(Value.Kind.StringValue(action)),
        "details" -> Value(Value.Kind.StructValue(AuditEntry.toStructValue(details)))
      ))
  }

  object AuditEntry {
    def fromStruct(struct: Struct): AuditEntry =
      AuditEntry(
        actor = struct.fields("actor").getStringValue,
        action = struct.fields("action").getStringValue,
        details = fromStructValue(struct.fields("details").getStructValue)
      )

    private def toStructValue(values: Map[String, Any]): Struct =
      Struct(fields = values.map { case (key, value) => key -> toValue(value) })

    private def toValue(value: Any): Value = value match {
      case None => Value(Value.Kind.NullValue(NullValue.NULL_VALUE))
      case s: String => Value(Value.Kind.StringValue(s))
      case b: Boolean => Value(Value.Kind.BoolValue(b))
      case n: Int => Value(Value.Kind.NumberValue(n.toDouble))
      case d: Double => Value(Value.Kind.NumberValue(d))
      case items: List[_] => Value(Value.Kind.ListValue(ListValue(items.map(toValue))))
      case nested: Map[_, _] =>
        Value(Value.Kind.StructValue(toStructValue(nested.map { case (k, v) => k.toString -> v })))
      case other => throw new IllegalArgumentException(s"Unsupported audit detail: $other")
    }

    private def fromStructValue(struct: Struct): Map[String, Any] =
      struct.fields.map { case (key, value) => key -> fromValue(value) }

    private def fromValue(value: Value): Any = value.kind match {
      case Value.Kind.NullValue(_) => None
      case Value.Kind.StringValue(s) => s
      case Value.Kind.BoolValue(b) => b
      case Value.Kind.NumberValue(n) => n
      case Value.Kind.ListValue(list) => list.values.map(fromValue).toList
      case Value.Kind.StructValue(nested) => fromStructValue(nested)
      case Value.Kind.Empty => throw new IllegalStateException("Audit detail without a value")
    }
  }

  /** The entries a crashed journal still holds and the byte length they occupy. */
  case class Recovery(entries: List[AuditEntry], validBytes: Long)

  /** An append-only file of length-delimited messages: one `Timestamp`, then `Struct` entries. */
  object AuditJournal {
    def create(journal: Path, openedAt: Timestamp): Unit =
      write(journal, StandardOpenOption.CREATE_NEW)(out => openedAt.writeDelimitedTo(out))

    def append(journal: Path, entries: Seq[AuditEntry]): Unit =
      write(journal, StandardOpenOption.APPEND)(out => entries.foreach(_.toStruct.writeDelimitedTo(out)))

    def readAll(journal: Path): (Timestamp, List[AuditEntry]) =
      Using.resource(new BufferedInputStream(Files.newInputStream(journal))) { in =>
        val header: Timestamp = Timestamp
          .parseDelimitedFrom(in)
          .getOrElse(throw new IllegalStateException(s"Journal $journal has no header"))
        val entries: ListBuffer[AuditEntry] = ListBuffer.empty
        var next: Option[Struct] = Struct.parseDelimitedFrom(in)
        while (next.isDefined) {
          entries += AuditEntry.fromStruct(next.get)
          next = Struct.parseDelimitedFrom(in)
        }
        (header, entries.toList)
      }

    def recover(journal: Path): Recovery = {
      val bytes: Array[Byte] = Files.readAllBytes(journal)
      val in: CountingInputStream = new CountingInputStream(bytes)
      Timestamp.parseDelimitedFrom(in).get
      val entries: ListBuffer[AuditEntry] = ListBuffer.empty
      var validBytes: Long = in.position
      var done: Boolean = false
      while (!done) {
        try {
          Struct.parseDelimitedFrom(in) match {
            case Some(struct) =>
              entries += AuditEntry.fromStruct(struct)
              validBytes = in.position
            case None => done = true
          }
        } catch {
          case _: InvalidProtocolBufferException => done = true
        }
      }
      Recovery(entries.toList, validBytes)
    }

    def truncate(journal: Path, length: Long): Unit =
      Using.resource(Files.newByteChannel(journal, StandardOpenOption.WRITE))(_.truncate(length))

    private def write(journal: Path, option: StandardOpenOption)(body: OutputStream => Unit): Unit =
      Using.resource(
        new BufferedOutputStream(Files.newOutputStream(journal, option, StandardOpenOption.WRITE))
      ) { out =>
        body(out)
        out.flush()
      }
  }

  /** Reads an in-memory journal while tracking how many bytes have been consumed. */
  final class CountingInputStream(bytes: Array[Byte]) extends InputStream {
    private var offset: Int = 0

    def position: Long = offset.toLong

    override def read(): Int =
      if (offset >= bytes.length) -1
      else {
        val b: Int = bytes(offset) & 0xff
        offset += 1
        b
      }

    override def read(buffer: Array[Byte], start: Int, length: Int): Int =
      if (offset >= bytes.length) -1
      else {
        val count: Int = math.min(length, bytes.length - offset)
        System.arraycopy(bytes, offset, buffer, start, count)
        offset += count
        count
      }
  }
}
