/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_tpolecat.doobie_postgres_3

import cats.Id
import cats.catsInstancesForId
import cats.instances.either._
import cats.~>
import doobie.Meta
import doobie.enumerated.SqlState
import doobie.postgres.Text
import doobie.postgres.free.Embedded
import doobie.postgres.free.copyin
import doobie.postgres.free.copyin.CopyInOp
import doobie.postgres.free.copymanager
import doobie.postgres.free.copymanager.CopyManagerOp
import doobie.postgres.free.copyout
import doobie.postgres.free.copyout.CopyOutOp
import doobie.postgres.free.largeobject
import doobie.postgres.free.largeobject.LargeObjectOp
import doobie.postgres.free.largeobjectmanager
import doobie.postgres.free.largeobjectmanager.LargeObjectManagerOp
import doobie.postgres.free.pgconnection
import doobie.postgres.free.pgconnection.PGConnectionOp
import doobie.postgres.hi.{pgconnection => hiPgConnection}
import doobie.postgres.implicits._
import doobie.postgres.sqlstate
import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Assertions.fail
import org.junit.jupiter.api.Test
import org.postgresql.PGNotification
import org.postgresql.copy.CopyManager
import org.postgresql.jdbc.AutoSave
import org.postgresql.jdbc.PreferQueryMode
import org.postgresql.largeobject.LargeObject
import org.postgresql.largeobject.LargeObjectManager
import org.postgresql.geometric.PGpoint
import org.postgresql.util.PGInterval
import org.postgresql.util.PGobject

import java.io.Reader
import java.io.StringReader
import java.net.InetAddress
import java.sql.SQLException
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.OffsetDateTime
import java.time.ZonedDateTime
import java.util.UUID
import scala.collection.mutable.ArrayBuffer

class Doobie_postgres_3Test {
  @Test
  def textEncodersRenderPostgresCopyLiterals(): Unit = {
    assertEquals("plain", Text[String].encode("plain"))
    assertEquals("line\\tbreak\\nslash\\\\", Text[String].encode("line\tbreak\nslash\\"))
    assertEquals("\\N", Text[Option[String]].encode(None))
    assertEquals("value", Text[Option[String]].encode(Some("value")))
    assertEquals("42\talpha\\tbeta", Text[Int].product(Text[String]).encode((42, "alpha\tbeta")))
    assertEquals("\\\\x00012aff", Text[Array[Byte]].encode(Array[Byte](0, 1, 42, -1)))
    assertEquals("{1,2,3}", Text[List[Int]].encode(List(1, 2, 3)))
    assertEquals("{\"alpha\",\"beta\"}", Text[List[String]].encode(List("alpha", "beta")))
  }

  @Test
  def postgresImplicitMetaInstancesCoverNativeJdbcTypes(): Unit = {
    assertNotNull(summon[Meta[UUID]])
    assertNotNull(summon[Meta[InetAddress]])
    assertNotNull(summon[Meta[PGInterval]])
    assertNotNull(summon[Meta[PGpoint]])
    assertNotNull(summon[Meta[java.util.Map[String, String]]])
    assertNotNull(summon[Meta[Map[String, String]]])
    assertNotNull(summon[Meta[Array[Int]]])
    assertNotNull(summon[Meta[Array[Option[Int]]]])
    assertNotNull(summon[Meta[Array[String]]])
    assertNotNull(summon[Meta[Array[Option[String]]]])
    assertNotNull(summon[Meta[Instant]])
    assertNotNull(summon[Meta[OffsetDateTime]])
    assertNotNull(summon[Meta[ZonedDateTime]])
    assertNotNull(summon[Meta[LocalDateTime]])
    assertNotNull(summon[Meta[LocalDate]])
    assertNotNull(summon[Meta[LocalTime]])
  }

  @Test
  def sqlStateConstantsExposePostgresErrorClasses(): Unit = {
    assertEquals(SqlState("00000"), sqlstate.class00.SUCCESSFUL_COMPLETION)
    assertEquals(SqlState("23505"), sqlstate.class23.UNIQUE_VIOLATION)
    assertEquals(SqlState("23503"), sqlstate.class23.FOREIGN_KEY_VIOLATION)
    assertEquals(SqlState("40001"), sqlstate.class40.SERIALIZATION_FAILURE)
    assertEquals(SqlState("42P01"), sqlstate.class42.UNDEFINED_TABLE)
    assertEquals("23505", sqlstate.class23.UNIQUE_VIOLATION.value)
  }

  @Test
  def postgresMonadErrorSyntaxRecoversSpecificSqlStates(): Unit = {
    type Result[A] = Either[Throwable, A]

    val duplicateKey: SQLException = new SQLException("duplicate key", sqlstate.class23.UNIQUE_VIOLATION.value)
    val recoveredUniqueViolation: Result[String] =
      (Left(duplicateKey): Result[String]).onUniqueViolation(Right("insert ignored"))
    assertEquals(Right("insert ignored"), recoveredUniqueViolation)

    val serializationFailure: SQLException =
      new SQLException("serialization failure", sqlstate.class40.SERIALIZATION_FAILURE.value)
    val recoveredSerializationFailure: Result[String] =
      (Left(serializationFailure): Result[String]).onSerializationFailure(Right("retry transaction"))
    assertEquals(Right("retry transaction"), recoveredSerializationFailure)

    val undefinedTable: SQLException = new SQLException("undefined table", sqlstate.class42.UNDEFINED_TABLE.value)
    val notRecoveredByUniqueViolationHandler: Result[String] =
      (Left(undefinedTable): Result[String]).onUniqueViolation(Right("not used"))
    notRecoveredByUniqueViolationHandler match {
      case Left(error) => assertSame(undefinedTable, error)
      case Right(value) => fail(s"Unexpected recovery for a different SQL state: $value")
    }
  }

  @Test
  def pgConnectionAlgebraSupportsDriverOperationsAndEmbeddedCopyManagerPrograms(): Unit = {
    val events: ArrayBuffer[String] = ArrayBuffer.empty[String]
    val copyManagerTarget: CopyManager = null.asInstanceOf[CopyManager]

    val copyManagerInterpreter: CopyManagerOp ~> Id = new (CopyManagerOp ~> Id) {
      override def apply[A](operation: CopyManagerOp[A]): Id[A] = operation match {
        case CopyManagerOp.CopyIn4(sql, reader) =>
          events += s"copyManager.copyIn:$sql:${readAll(reader)}"
          2L.asInstanceOf[A]
        case CopyManagerOp.CopyOut2(sql, writer) =>
          writer.write("id\tname\n1\tAda\n")
          events += s"copyManager.copyOut:$sql"
          1L.asInstanceOf[A]
        case other => fail(s"Unexpected copy manager operation: $other")
      }
    }

    val pgConnectionInterpreter: PGConnectionOp ~> Id = new (PGConnectionOp ~> Id) {
      override def apply[A](operation: PGConnectionOp[A]): Id[A] = operation match {
        case PGConnectionOp.AddDataType(name, clazz) =>
          assertSame(classOf[PGobject], clazz)
          events += s"pg.addDataType:$name"
          ().asInstanceOf[A]
        case PGConnectionOp.SetAutosave(value) =>
          events += s"pg.setAutosave:$value"
          ().asInstanceOf[A]
        case PGConnectionOp.SetDefaultFetchSize(value) =>
          events += s"pg.setDefaultFetchSize:$value"
          ().asInstanceOf[A]
        case PGConnectionOp.SetPrepareThreshold(value) =>
          events += s"pg.setPrepareThreshold:$value"
          ().asInstanceOf[A]
        case PGConnectionOp.GetAutosave => AutoSave.ALWAYS.asInstanceOf[A]
        case PGConnectionOp.GetBackendPID => 12345.asInstanceOf[A]
        case PGConnectionOp.GetDefaultFetchSize => 128.asInstanceOf[A]
        case PGConnectionOp.GetParameterStatus(name) => s"status:$name".asInstanceOf[A]
        case PGConnectionOp.GetPreferQueryMode => PreferQueryMode.SIMPLE.asInstanceOf[A]
        case PGConnectionOp.GetPrepareThreshold => 5.asInstanceOf[A]
        case PGConnectionOp.EscapeIdentifier(identifier) => s"\"${identifier.replace("\"", "\"\"")}\"".asInstanceOf[A]
        case PGConnectionOp.EscapeLiteral(literal) => s"'${literal.replace("'", "''")}'".asInstanceOf[A]
        case PGConnectionOp.Embed(Embedded.CopyManager(target, program)) =>
          assertSame(copyManagerTarget, target)
          program.foldMap(copyManagerInterpreter).asInstanceOf[A]
        case other => fail(s"Unexpected PG connection operation: $other")
      }
    }

    val out = new java.io.StringWriter
    val program: pgconnection.PGConnectionIO[(AutoSave, Int, Int, String, PreferQueryMode, Int, String, String, Long, Long)] =
      for {
        _ <- pgconnection.addDataType("custom_json", classOf[PGobject])
        _ <- pgconnection.setAutosave(AutoSave.ALWAYS)
        _ <- pgconnection.setDefaultFetchSize(128)
        _ <- pgconnection.setPrepareThreshold(5)
        autosave <- pgconnection.getAutosave
        backendPid <- pgconnection.getBackendPID
        fetchSize <- pgconnection.getDefaultFetchSize
        parameterStatus <- pgconnection.getParameterStatus("server_version")
        queryMode <- pgconnection.getPreferQueryMode
        threshold <- pgconnection.getPrepareThreshold
        escapedIdentifier <- pgconnection.escapeIdentifier("account\"name")
        escapedLiteral <- pgconnection.escapeLiteral("Ada's account")
        copiedIn <- pgconnection.embed(copyManagerTarget, copymanager.copyIn("COPY accounts FROM STDIN", new StringReader("1\tAda\n")))
        copiedOut <- pgconnection.embed(copyManagerTarget, copymanager.copyOut("COPY accounts TO STDOUT", out))
      } yield (autosave, backendPid, fetchSize, parameterStatus, queryMode, threshold, escapedIdentifier, escapedLiteral, copiedIn, copiedOut)

    val result = program.foldMap(pgConnectionInterpreter)

    assertEquals(AutoSave.ALWAYS, result._1)
    assertEquals(12345, result._2)
    assertEquals(128, result._3)
    assertEquals("status:server_version", result._4)
    assertEquals(PreferQueryMode.SIMPLE, result._5)
    assertEquals(5, result._6)
    assertEquals("\"account\"\"name\"", result._7)
    assertEquals("'Ada''s account'", result._8)
    assertEquals(2L, result._9)
    assertEquals(1L, result._10)
    assertEquals("id\tname\n1\tAda\n", out.toString)
    assertEquals(
      Seq(
        "pg.addDataType:custom_json",
        "pg.setAutosave:ALWAYS",
        "pg.setDefaultFetchSize:128",
        "pg.setPrepareThreshold:5",
        "copyManager.copyIn:COPY accounts FROM STDIN:1\tAda\n",
        "copyManager.copyOut:COPY accounts TO STDOUT"
      ),
      events.toSeq
    )
  }

  @Test
  def highLevelPgConnectionNotificationsConvertDriverArraysToScalaLists(): Unit = {
    val firstNotification: PGNotification = notification("account_events", "created", 101)
    val secondNotification: PGNotification = notification("account_events", "updated", 102)

    val arrayInterpreter: PGConnectionOp ~> Id = new (PGConnectionOp ~> Id) {
      override def apply[A](operation: PGConnectionOp[A]): Id[A] = operation match {
        case PGConnectionOp.GetNotifications => Array(firstNotification, secondNotification).asInstanceOf[A]
        case other => fail(s"Unexpected PG connection operation: $other")
      }
    }

    val emptyInterpreter: PGConnectionOp ~> Id = new (PGConnectionOp ~> Id) {
      override def apply[A](operation: PGConnectionOp[A]): Id[A] = operation match {
        case PGConnectionOp.GetNotifications => null.asInstanceOf[Array[PGNotification]].asInstanceOf[A]
        case other => fail(s"Unexpected PG connection operation: $other")
      }
    }

    assertEquals(
      List(firstNotification, secondNotification),
      hiPgConnection.getNotifications.foldMap(arrayInterpreter)
    )
    assertEquals(Nil, hiPgConnection.getNotifications.foldMap(emptyInterpreter))
  }

  @Test
  def copyInAndCopyOutAlgebrasDescribeStreamingCopyProtocol(): Unit = {
    val copyInEvents: ArrayBuffer[String] = ArrayBuffer.empty[String]
    val copyInInterpreter: CopyInOp ~> Id = new (CopyInOp ~> Id) {
      override def apply[A](operation: CopyInOp[A]): Id[A] = operation match {
        case CopyInOp.WriteToCopy(bytes, offset, length) =>
          copyInEvents += s"write:${new String(bytes.slice(offset, offset + length), java.nio.charset.StandardCharsets.UTF_8)}"
          ().asInstanceOf[A]
        case CopyInOp.FlushCopy =>
          copyInEvents += "flush"
          ().asInstanceOf[A]
        case CopyInOp.GetFieldCount => 2.asInstanceOf[A]
        case CopyInOp.GetFieldFormat(index) =>
          copyInEvents += s"fieldFormat:$index"
          0.asInstanceOf[A]
        case CopyInOp.GetFormat => 0.asInstanceOf[A]
        case CopyInOp.GetHandledRowCount => 1L.asInstanceOf[A]
        case CopyInOp.IsActive => true.asInstanceOf[A]
        case CopyInOp.EndCopy =>
          copyInEvents += "end"
          1L.asInstanceOf[A]
        case other => fail(s"Unexpected copy-in operation: $other")
      }
    }

    val copyInBytes: Array[Byte] = "1\tAda\n".getBytes(java.nio.charset.StandardCharsets.UTF_8)
    val copyInProgram: copyin.CopyInIO[(Int, Int, Int, Long, Boolean, Long)] =
      for {
        _ <- copyin.writeToCopy(copyInBytes, 0, copyInBytes.length)
        _ <- copyin.flushCopy
        fields <- copyin.getFieldCount
        fieldFormat <- copyin.getFieldFormat(0)
        format <- copyin.getFormat
        handledBeforeEnd <- copyin.getHandledRowCount
        active <- copyin.isActive
        ended <- copyin.endCopy
      } yield (fields, fieldFormat, format, handledBeforeEnd, active, ended)

    val copyInResult = copyInProgram.foldMap(copyInInterpreter)

    assertEquals((2, 0, 0, 1L, true, 1L), copyInResult)
    assertEquals(Seq("write:1\tAda\n", "flush", "fieldFormat:0", "end"), copyInEvents.toSeq)

    val copyOutEvents: ArrayBuffer[String] = ArrayBuffer.empty[String]
    val copyOutInterpreter: CopyOutOp ~> Id = new (CopyOutOp ~> Id) {
      override def apply[A](operation: CopyOutOp[A]): Id[A] = operation match {
        case CopyOutOp.ReadFromCopy1(block) =>
          copyOutEvents += s"read:$block"
          "2\tGrace\n".getBytes(java.nio.charset.StandardCharsets.UTF_8).asInstanceOf[A]
        case CopyOutOp.GetFieldCount => 2.asInstanceOf[A]
        case CopyOutOp.GetFieldFormat(index) =>
          copyOutEvents += s"fieldFormat:$index"
          0.asInstanceOf[A]
        case CopyOutOp.GetFormat => 0.asInstanceOf[A]
        case CopyOutOp.GetHandledRowCount => 1L.asInstanceOf[A]
        case CopyOutOp.IsActive => false.asInstanceOf[A]
        case CopyOutOp.CancelCopy =>
          copyOutEvents += "cancel"
          ().asInstanceOf[A]
        case other => fail(s"Unexpected copy-out operation: $other")
      }
    }

    val copyOutProgram: copyout.CopyOutIO[(Array[Byte], Int, Int, Int, Long, Boolean)] =
      for {
        bytes <- copyout.readFromCopy(false)
        fields <- copyout.getFieldCount
        fieldFormat <- copyout.getFieldFormat(1)
        format <- copyout.getFormat
        handled <- copyout.getHandledRowCount
        active <- copyout.isActive
        _ <- copyout.cancelCopy
      } yield (bytes, fields, fieldFormat, format, handled, active)

    val copyOutResult = copyOutProgram.foldMap(copyOutInterpreter)

    assertArrayEquals("2\tGrace\n".getBytes(java.nio.charset.StandardCharsets.UTF_8), copyOutResult._1)
    assertEquals(2, copyOutResult._2)
    assertEquals(0, copyOutResult._3)
    assertEquals(0, copyOutResult._4)
    assertEquals(1L, copyOutResult._5)
    assertFalse(copyOutResult._6)
    assertEquals(Seq("read:false", "fieldFormat:1", "cancel"), copyOutEvents.toSeq)
  }

  @Test
  def largeObjectAlgebrasDescribeManagerAndObjectOperations(): Unit = {
    val events: ArrayBuffer[String] = ArrayBuffer.empty[String]
    val largeObjectTarget: LargeObject = null.asInstanceOf[LargeObject]

    val largeObjectInterpreter: LargeObjectOp ~> Id = new (LargeObjectOp ~> Id) {
      override def apply[A](operation: LargeObjectOp[A]): Id[A] = operation match {
        case LargeObjectOp.GetLongOID => 9001L.asInstanceOf[A]
        case LargeObjectOp.Write1(bytes, offset, length) =>
          events += s"largeObject.write:${bytes.slice(offset, offset + length).mkString(",")}"
          ().asInstanceOf[A]
        case LargeObjectOp.Seek64(position, reference) =>
          events += s"largeObject.seek64:$position:$reference"
          ().asInstanceOf[A]
        case LargeObjectOp.Read1(length) =>
          events += s"largeObject.read:$length"
          Array[Byte](10, 20, 30).asInstanceOf[A]
        case LargeObjectOp.Size64 => 4096L.asInstanceOf[A]
        case LargeObjectOp.Tell64 => 3L.asInstanceOf[A]
        case LargeObjectOp.Truncate64(length) =>
          events += s"largeObject.truncate64:$length"
          ().asInstanceOf[A]
        case LargeObjectOp.Close =>
          events += "largeObject.close"
          ().asInstanceOf[A]
        case other => fail(s"Unexpected large object operation: $other")
      }
    }

    val managerInterpreter: LargeObjectManagerOp ~> Id = new (LargeObjectManagerOp ~> Id) {
      override def apply[A](operation: LargeObjectManagerOp[A]): Id[A] = operation match {
        case LargeObjectManagerOp.CreateLO1(mode) =>
          events += s"manager.createLO:$mode"
          9001L.asInstanceOf[A]
        case LargeObjectManagerOp.Open5(oid, mode, commitOnClose) =>
          events += s"manager.open:$oid:$mode:$commitOnClose"
          largeObjectTarget.asInstanceOf[A]
        case LargeObjectManagerOp.Embed(Embedded.LargeObject(target, program)) =>
          assertSame(largeObjectTarget, target)
          program.foldMap(largeObjectInterpreter).asInstanceOf[A]
        case LargeObjectManagerOp.Unlink(oid) =>
          events += s"manager.unlink:$oid"
          ().asInstanceOf[A]
        case other => fail(s"Unexpected large object manager operation: $other")
      }
    }

    val bytesToWrite: Array[Byte] = Array[Byte](1, 2, 3, 4)
    val largeObjectProgram: largeobject.LargeObjectIO[(Long, Array[Byte], Long, Long)] =
      for {
        oid <- largeobject.getLongOID
        _ <- largeobject.write(bytesToWrite, 1, 2)
        _ <- largeobject.seek64(0L, 0)
        readBytes <- largeobject.read(3)
        size <- largeobject.size64
        position <- largeobject.tell64
        _ <- largeobject.truncate64(1024L)
        _ <- largeobject.close
      } yield (oid, readBytes, size, position)

    val managerProgram: largeobjectmanager.LargeObjectManagerIO[(Long, Long, Array[Byte], Long, Long)] =
      for {
        oid <- largeobjectmanager.createLO(LargeObjectManager.WRITE)
        opened <- largeobjectmanager.open(oid, LargeObjectManager.READ | LargeObjectManager.WRITE, true)
        largeObjectResult <- largeobjectmanager.embed(opened, largeObjectProgram)
        _ <- largeobjectmanager.unlink(oid)
      } yield (oid, largeObjectResult._1, largeObjectResult._2, largeObjectResult._3, largeObjectResult._4)

    val result = managerProgram.foldMap(managerInterpreter)

    assertEquals(9001L, result._1)
    assertEquals(9001L, result._2)
    assertArrayEquals(Array[Byte](10, 20, 30), result._3)
    assertEquals(4096L, result._4)
    assertEquals(3L, result._5)
    assertEquals(
      Seq(
        s"manager.createLO:${LargeObjectManager.WRITE}",
        s"manager.open:9001:${LargeObjectManager.READ | LargeObjectManager.WRITE}:true",
        "largeObject.write:2,3",
        "largeObject.seek64:0:0",
        "largeObject.read:3",
        "largeObject.truncate64:1024",
        "largeObject.close",
        "manager.unlink:9001"
      ),
      events.toSeq
    )
  }

  private def notification(name: String, parameter: String, processId: Int): PGNotification = new PGNotification {
    override def getName: String = name

    override def getParameter: String = parameter

    override def getPID: Int = processId
  }

  private def readAll(reader: Reader): String = {
    val builder: StringBuilder = new StringBuilder
    val buffer: Array[Char] = new Array[Char](128)
    var read: Int = reader.read(buffer)
    while (read != -1) {
      builder.append(new String(buffer, 0, read))
      read = reader.read(buffer)
    }
    builder.toString
  }
}
