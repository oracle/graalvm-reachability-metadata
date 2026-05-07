/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_tpolecat.doobie_free_3

import cats.Id
import cats.catsInstancesForId
import cats.~>
import cats.effect.IO
import cats.effect.unsafe.implicits.global
import cats.instances.all._
import doobie.free.Embedded
import doobie.free.KleisliInterpreter
import doobie.free.blob
import doobie.free.callablestatement
import doobie.free.callablestatement.CallableStatementOp
import doobie.free.clob
import doobie.free.connection
import doobie.free.connection.ConnectionOp
import doobie.free.databasemetadata
import doobie.free.databasemetadata.DatabaseMetaDataOp
import doobie.free.driver
import doobie.free.preparedstatement
import doobie.free.preparedstatement.PreparedStatementOp
import doobie.free.ref
import doobie.free.resultset
import doobie.free.resultset.ResultSetOp
import doobie.free.sqldata
import doobie.free.sqlinput
import doobie.free.sqloutput
import doobie.free.statement
import doobie.free.statement.StatementOp
import doobie.util.log.LogHandler
import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Assertions.fail
import org.junit.jupiter.api.Test

import java.io.InputStream
import java.io.OutputStream
import java.io.Reader
import java.io.Writer
import java.math.BigDecimal
import java.sql.Clob
import java.sql.Connection
import java.sql.Driver
import java.sql.DriverPropertyInfo
import java.sql.NClob
import java.sql.ResultSet
import java.sql.SQLData
import java.sql.SQLInput
import java.sql.SQLOutput
import java.sql.Statement
import java.sql.Types
import java.util.Properties
import java.util.Vector
import java.util.concurrent.atomic.AtomicReference
import java.util.logging.Logger
import javax.sql.rowset.serial.SerialBlob
import javax.sql.rowset.serial.SerialClob
import javax.sql.rowset.serial.SQLInputImpl
import javax.sql.rowset.serial.SQLOutputImpl
import scala.collection.mutable.ArrayBuffer
import scala.concurrent.duration.DurationInt

class Doobie_free_3Test {
  private val interpreter: KleisliInterpreter[IO] = KleisliInterpreter[IO](LogHandler.noop[IO])

  @Test
  def blobProgramsRunAgainstJdbcBlobImplementations(): Unit = {
    val initialBytes: Array[Byte] = Array[Byte](1, 2, 3, 4, 5)
    val jdbcBlob: SerialBlob = new SerialBlob(initialBytes)

    val program: blob.BlobIO[(Long, Array[Byte], Int, Array[Byte], Long, Long)] =
      for {
        originalLength <- blob.length
        originalSlice <- blob.getBytes(2L, 3)
        changed <- blob.setBytes(3L, Array[Byte](9, 8))
        changedBytes <- blob.getBytes(1L, 5)
        changedPosition <- blob.position(Array[Byte](9, 8), 1L)
        _ <- blob.truncate(4L)
        truncatedLength <- blob.length
      } yield (originalLength, originalSlice, changed, changedBytes, changedPosition, truncatedLength)

    val (originalLength, originalSlice, changed, changedBytes, changedPosition, truncatedLength) =
      runIo(program.foldMap(interpreter.BlobInterpreter).run(jdbcBlob))

    assertEquals(5L, originalLength)
    assertArrayEquals(Array[Byte](2, 3, 4), originalSlice)
    assertEquals(2, changed)
    assertArrayEquals(Array[Byte](1, 2, 9, 8, 5), changedBytes)
    assertEquals(3L, changedPosition)
    assertEquals(4L, truncatedLength)
  }

  @Test
  def clobAndNClobProgramsRunAgainstJdbcCharacterLargeObjects(): Unit = {
    val jdbcClob: SerialClob = new SerialClob("abcdef".toCharArray)
    val clobProgram: clob.ClobIO[(Long, String, Long, Int, String, Long)] =
      for {
        originalLength <- clob.length
        originalSlice <- clob.getSubString(2L, 3)
        position <- clob.position("cd", 1L)
        written <- clob.setString(4L, "XYZ")
        changed <- clob.getSubString(1L, 6)
        _ <- clob.truncate(4L)
        truncatedLength <- clob.length
      } yield (originalLength, originalSlice, position, written, changed, truncatedLength)

    val (originalLength, originalSlice, position, written, changed, truncatedLength) =
      runIo(clobProgram.foldMap(interpreter.ClobInterpreter).run(jdbcClob))

    assertEquals(6L, originalLength)
    assertEquals("bcd", originalSlice)
    assertEquals(3L, position)
    assertEquals(3, written)
    assertEquals("abcXYZ", changed)
    assertEquals(4L, truncatedLength)

    val jdbcNClob: InMemoryNClob = new InMemoryNClob("native-clob")
    val nclobProgram: doobie.free.nclob.NClobIO[(Long, String, Long)] =
      for {
        length <- doobie.free.nclob.length
        value <- doobie.free.nclob.getSubString(1L, 6)
        position <- doobie.free.nclob.position("clob", 1L)
      } yield (length, value, position)

    val (nclobLength, nclobValue, nclobPosition) =
      runIo(nclobProgram.foldMap(interpreter.NClobInterpreter).run(jdbcNClob))

    assertEquals(11L, nclobLength)
    assertEquals("native", nclobValue)
    assertEquals(8L, nclobPosition)
  }

  @Test
  def sqlInputAndSqlOutputProgramsReadAndWriteStructuredValues(): Unit = {
    val writtenAttributes: Vector[AnyRef] = new Vector[AnyRef]()
    val output: SQLOutput = new SQLOutputImpl(writtenAttributes, new java.util.HashMap[String, Class[_]]())
    val outputProgram: sqloutput.SQLOutputIO[Unit] =
      for {
        _ <- sqloutput.writeString("alpha")
        _ <- sqloutput.writeInt(42)
        _ <- sqloutput.writeBoolean(true)
        _ <- sqloutput.writeBigDecimal(new BigDecimal("12.50"))
        _ <- sqloutput.writeBytes(Array[Byte](5, 6, 7))
      } yield ()

    runIo(outputProgram.foldMap(interpreter.SQLOutputInterpreter).run(output))

    assertEquals("alpha", writtenAttributes.get(0))
    assertEquals(Integer.valueOf(42), writtenAttributes.get(1))
    assertEquals(java.lang.Boolean.TRUE, writtenAttributes.get(2))
    assertEquals(new BigDecimal("12.50"), writtenAttributes.get(3))
    assertArrayEquals(Array[Byte](5, 6, 7), writtenAttributes.get(4).asInstanceOf[Array[Byte]])

    val inputAttributes: Array[AnyRef] = Array[AnyRef](
      "beta",
      Integer.valueOf(7),
      java.lang.Boolean.FALSE,
      new BigDecimal("9.25"),
      Array[Byte](1, 2)
    )
    val input: SQLInput = new SQLInputImpl(inputAttributes, new java.util.HashMap[String, Class[_]]())
    val inputProgram: sqlinput.SQLInputIO[(String, Int, Boolean, BigDecimal, Array[Byte], Boolean)] =
      for {
        text <- sqlinput.readString
        number <- sqlinput.readInt
        flag <- sqlinput.readBoolean
        decimal <- sqlinput.readBigDecimal
        bytes <- sqlinput.readBytes
        wasNull <- sqlinput.wasNull
      } yield (text, number, flag, decimal, bytes, wasNull)

    val (text, number, flag, decimal, bytes, wasNull) =
      runIo(inputProgram.foldMap(interpreter.SQLInputInterpreter).run(input))

    assertEquals("beta", text)
    assertEquals(7, number)
    assertFalse(flag)
    assertEquals(new BigDecimal("9.25"), decimal)
    assertArrayEquals(Array[Byte](1, 2), bytes)
    assertFalse(wasNull)
  }

  @Test
  def driverRefAndSQLDataProgramsDelegateToJdbcObjects(): Unit = {
    val driverTarget: RecordingDriver = new RecordingDriver
    val driverProgram: driver.DriverIO[(Boolean, Int, Int, Boolean, String, Int)] =
      for {
        accepts <- driver.acceptsURL("jdbc:recording:test")
        major <- driver.getMajorVersion
        minor <- driver.getMinorVersion
        compliant <- driver.jdbcCompliant
        _ <- driver.connect("jdbc:recording:test", new Properties)
        infos <- driver.getPropertyInfo("jdbc:recording:test", new Properties)
      } yield (accepts, major, minor, compliant, driverTarget.lastUrl.get, infos.length)

    val (accepts, major, minor, compliant, connectedUrl, propertyInfoCount) =
      runIo(driverProgram.foldMap(interpreter.DriverInterpreter).run(driverTarget))

    assertTrue(accepts)
    assertEquals(1, major)
    assertEquals(4, minor)
    assertFalse(compliant)
    assertEquals("jdbc:recording:test", connectedUrl)
    assertEquals(1, propertyInfoCount)

    val refTarget: MutableRef = new MutableRef("demo_type", "initial")
    val refProgram: ref.RefIO[(String, AnyRef, AnyRef)] =
      for {
        base <- ref.getBaseTypeName
        before <- ref.getObject
        _ <- ref.setObject("changed")
        after <- ref.getObject(new java.util.HashMap[String, Class[_]]())
      } yield (base, before, after)

    val (base, before, after) = runIo(refProgram.foldMap(interpreter.RefInterpreter).run(refTarget))

    assertEquals("demo_type", base)
    assertEquals("initial", before)
    assertEquals("changed", after)

    val sqlDataTarget: RecordingSQLData = new RecordingSQLData("custom_type")
    val input: SQLInput = new SQLInputImpl(Array[AnyRef]("payload"), new java.util.HashMap[String, Class[_]]())
    val output: SQLOutput = new SQLOutputImpl(new Vector[AnyRef](), new java.util.HashMap[String, Class[_]]())
    val sqlDataProgram: sqldata.SQLDataIO[String] =
      for {
        typeName <- sqldata.getSQLTypeName
        _ <- sqldata.readSQL(input, "custom_type")
        _ <- sqldata.writeSQL(output)
      } yield typeName

    val typeName = runIo(sqlDataProgram.foldMap(interpreter.SQLDataInterpreter).run(sqlDataTarget))

    assertEquals("custom_type", typeName)
    assertEquals("custom_type", sqlDataTarget.lastReadType.get)
    assertSame(output, sqlDataTarget.lastOutput.get)
  }

  @Test
  def freeProgramsCanBeInterpretedWithCustomNaturalTransformations(): Unit = {
    val events: ArrayBuffer[String] = ArrayBuffer.empty[String]
    val statementTarget: Statement = null.asInstanceOf[Statement]

    val statementInterpreter: StatementOp ~> Id = new (StatementOp ~> Id) {
      override def apply[A](operation: StatementOp[A]): Id[A] = operation match {
        case StatementOp.AddBatch(sql) =>
          events += s"statement.addBatch:$sql"
          ().asInstanceOf[A]
        case StatementOp.GetMaxRows => 50.asInstanceOf[A]
        case StatementOp.ExecuteUpdate(sql) =>
          events += s"statement.executeUpdate:$sql"
          3.asInstanceOf[A]
        case other => fail(s"Unexpected statement operation: $other")
      }
    }

    val connectionInterpreter: ConnectionOp ~> Id = new (ConnectionOp ~> Id) {
      override def apply[A](operation: ConnectionOp[A]): Id[A] = operation match {
        case ConnectionOp.SetAutoCommit(value) =>
          events += s"connection.setAutoCommit:$value"
          ().asInstanceOf[A]
        case ConnectionOp.NativeSQL(sql) => s"native:$sql".asInstanceOf[A]
        case ConnectionOp.IsReadOnly => true.asInstanceOf[A]
        case ConnectionOp.Embed(Embedded.Statement(target, program)) =>
          assertSame(statementTarget, target)
          program.foldMap(statementInterpreter).asInstanceOf[A]
        case other => fail(s"Unexpected connection operation: $other")
      }
    }

    val program: connection.ConnectionIO[(String, Boolean, Int)] =
      for {
        _ <- connection.setAutoCommit(false)
        nativeSql <- connection.nativeSQL("select 1")
        readOnly <- connection.isReadOnly
        _ <- connection.embed(statementTarget, statement.addBatch("insert into audit values (1)"))
        maxRows <- connection.embed(statementTarget, statement.getMaxRows)
      } yield (nativeSql, readOnly, maxRows)

    val (nativeSql, readOnly, maxRows) = program.foldMap(connectionInterpreter)

    assertEquals("native:select 1", nativeSql)
    assertTrue(readOnly)
    assertEquals(50, maxRows)
    assertEquals(
      Seq("connection.setAutoCommit:false", "statement.addBatch:insert into audit values (1)"),
      events.toSeq
    )
  }

  @Test
  def databaseMetaDataAlgebraDescribesDatabaseCapabilitiesAndCatalogRows(): Unit = {
    val tableRows: ResultSet = null.asInstanceOf[ResultSet]
    val events: ArrayBuffer[String] = ArrayBuffer.empty[String]

    val resultSetInterpreter: ResultSetOp ~> Id = new (ResultSetOp ~> Id) {
      override def apply[A](operation: ResultSetOp[A]): Id[A] = operation match {
        case ResultSetOp.Next => true.asInstanceOf[A]
        case ResultSetOp.GetString(index) =>
          events += s"resultSet.getStringByIndex:$index"
          "accounts".asInstanceOf[A]
        case ResultSetOp.GetString1(column) =>
          events += s"resultSet.getStringByName:$column"
          "TABLE".asInstanceOf[A]
        case other => fail(s"Unexpected result set operation: $other")
      }
    }

    val metadataInterpreter: DatabaseMetaDataOp ~> Id = new (DatabaseMetaDataOp ~> Id) {
      override def apply[A](operation: DatabaseMetaDataOp[A]): Id[A] = operation match {
        case DatabaseMetaDataOp.GetDatabaseProductName => "ExampleDB".asInstanceOf[A]
        case DatabaseMetaDataOp.GetDatabaseMajorVersion => 12.asInstanceOf[A]
        case DatabaseMetaDataOp.GetDriverName => "Example JDBC Driver".asInstanceOf[A]
        case DatabaseMetaDataOp.SupportsTransactions => true.asInstanceOf[A]
        case DatabaseMetaDataOp.GetTables(catalog, schemaPattern, tableNamePattern, tableTypes) =>
          events += s"metadata.getTables:$catalog:$schemaPattern:$tableNamePattern:${tableTypes.mkString(",")}"
          tableRows.asInstanceOf[A]
        case DatabaseMetaDataOp.Embed(Embedded.ResultSet(target, program)) =>
          assertSame(tableRows, target)
          program.foldMap(resultSetInterpreter).asInstanceOf[A]
        case other => fail(s"Unexpected database metadata operation: $other")
      }
    }

    val program: databasemetadata.DatabaseMetaDataIO[(String, Int, String, Boolean, Boolean, String, String)] =
      for {
        productName <- databasemetadata.getDatabaseProductName
        majorVersion <- databasemetadata.getDatabaseMajorVersion
        driverName <- databasemetadata.getDriverName
        supportsTransactions <- databasemetadata.supportsTransactions
        catalogResultSet <- databasemetadata.getTables(null, "public", "account%", Array("TABLE"))
        tableInfo <- databasemetadata.embed(
          catalogResultSet,
          for {
            hasRow <- resultset.next
            tableName <- resultset.getString(3)
            tableType <- resultset.getString("TABLE_TYPE")
          } yield (hasRow, tableName, tableType)
        )
      } yield (productName, majorVersion, driverName, supportsTransactions, tableInfo._1, tableInfo._2, tableInfo._3)

    val (productName, majorVersion, driverName, supportsTransactions, hasTable, tableName, tableType) =
      program.foldMap(metadataInterpreter)

    assertEquals("ExampleDB", productName)
    assertEquals(12, majorVersion)
    assertEquals("Example JDBC Driver", driverName)
    assertTrue(supportsTransactions)
    assertTrue(hasTable)
    assertEquals("accounts", tableName)
    assertEquals("TABLE", tableType)
    assertEquals(
      Seq(
        "metadata.getTables:null:public:account%:TABLE",
        "resultSet.getStringByIndex:3",
        "resultSet.getStringByName:TABLE_TYPE"
      ),
      events.toSeq
    )
  }

  @Test
  def callableStatementAlgebraSupportsStoredProcedureParameters(): Unit = {
    val events: ArrayBuffer[String] = ArrayBuffer.empty[String]
    val callableInterpreter: CallableStatementOp ~> Id = new (CallableStatementOp ~> Id) {
      override def apply[A](operation: CallableStatementOp[A]): Id[A] = operation match {
        case CallableStatementOp.SetString1(name, value) =>
          events += s"setString:$name:$value"
          ().asInstanceOf[A]
        case CallableStatementOp.RegisterOutParameter6(name, sqlType) =>
          events += s"registerOutParameter:$name:$sqlType"
          ().asInstanceOf[A]
        case CallableStatementOp.Execute => true.asInstanceOf[A]
        case CallableStatementOp.GetInt1(name) =>
          events += s"getInt:$name"
          123.asInstanceOf[A]
        case CallableStatementOp.GetString1(name) =>
          events += s"getString:$name"
          "approved".asInstanceOf[A]
        case CallableStatementOp.WasNull => false.asInstanceOf[A]
        case other => fail(s"Unexpected callable statement operation: $other")
      }
    }

    val program: callablestatement.CallableStatementIO[(Boolean, Int, String, Boolean)] =
      for {
        _ <- callablestatement.setString("customer_name", "Ada")
        _ <- callablestatement.registerOutParameter("new_id", Types.INTEGER)
        executed <- callablestatement.execute
        generatedId <- callablestatement.getInt("new_id")
        status <- callablestatement.getString("status")
        wasNull <- callablestatement.wasNull
      } yield (executed, generatedId, status, wasNull)

    val (executed, generatedId, status, wasNull) = program.foldMap(callableInterpreter)

    assertTrue(executed)
    assertEquals(123, generatedId)
    assertEquals("approved", status)
    assertFalse(wasNull)
    assertEquals(
      Seq(
        "setString:customer_name:Ada",
        s"registerOutParameter:new_id:${Types.INTEGER}",
        "getInt:new_id",
        "getString:status"
      ),
      events.toSeq
    )
  }

  @Test
  def preparedStatementAndResultSetAlgebrasExposeJdbcSpecificOperations(): Unit = {
    val preparedEvents: ArrayBuffer[String] = ArrayBuffer.empty[String]
    val preparedInterpreter: PreparedStatementOp ~> Id = new (PreparedStatementOp ~> Id) {
      override def apply[A](operation: PreparedStatementOp[A]): Id[A] = operation match {
        case PreparedStatementOp.SetInt(index, value) =>
          preparedEvents += s"setInt:$index:$value"
          ().asInstanceOf[A]
        case PreparedStatementOp.SetString(index, value) =>
          preparedEvents += s"setString:$index:$value"
          ().asInstanceOf[A]
        case PreparedStatementOp.SetNull(index, sqlType) =>
          preparedEvents += s"setNull:$index:$sqlType"
          ().asInstanceOf[A]
        case PreparedStatementOp.ExecuteUpdate => 2.asInstanceOf[A]
        case PreparedStatementOp.GetLargeUpdateCount => 2L.asInstanceOf[A]
        case other => fail(s"Unexpected prepared statement operation: $other")
      }
    }

    val preparedProgram: preparedstatement.PreparedStatementIO[(Int, Long)] =
      for {
        _ <- preparedstatement.setInt(1, 100)
        _ <- preparedstatement.setString(2, "name")
        _ <- preparedstatement.setNull(3, Types.VARCHAR)
        updated <- preparedstatement.executeUpdate
        largeUpdated <- preparedstatement.getLargeUpdateCount
      } yield (updated, largeUpdated)

    val (updated, largeUpdated) = preparedProgram.foldMap(preparedInterpreter)

    assertEquals(2, updated)
    assertEquals(2L, largeUpdated)
    assertEquals(Seq("setInt:1:100", "setString:2:name", s"setNull:3:${Types.VARCHAR}"), preparedEvents.toSeq)

    val resultSetEvents: ArrayBuffer[String] = ArrayBuffer.empty[String]
    val resultSetInterpreter: ResultSetOp ~> Id = new (ResultSetOp ~> Id) {
      override def apply[A](operation: ResultSetOp[A]): Id[A] = operation match {
        case ResultSetOp.Next => true.asInstanceOf[A]
        case ResultSetOp.GetString1(column) =>
          resultSetEvents += s"getString:$column"
          "Alice".asInstanceOf[A]
        case ResultSetOp.GetInt(index) =>
          resultSetEvents += s"getInt:$index"
          42.asInstanceOf[A]
        case ResultSetOp.WasNull => false.asInstanceOf[A]
        case ResultSetOp.UpdateString1(column, value) =>
          resultSetEvents += s"updateString:$column:$value"
          ().asInstanceOf[A]
        case other => fail(s"Unexpected result set operation: $other")
      }
    }

    val resultSetProgram: resultset.ResultSetIO[(Boolean, String, Int, Boolean)] =
      for {
        hasRow <- resultset.next
        name <- resultset.getString("name")
        id <- resultset.getInt(1)
        _ <- resultset.updateString("status", "seen")
        wasNull <- resultset.wasNull
      } yield (hasRow, name, id, wasNull)

    val (hasRow, name, id, wasNull) = resultSetProgram.foldMap(resultSetInterpreter)

    assertTrue(hasRow)
    assertEquals("Alice", name)
    assertEquals(42, id)
    assertFalse(wasNull)
    assertEquals(Seq("getString:name", "getInt:1", "updateString:status:seen"), resultSetEvents.toSeq)
  }

  private def runIo[A](io: IO[A]): A =
    io.timeoutTo(
      5.seconds,
      IO.raiseError(new AssertionError("IO did not complete within the test timeout"))
    ).unsafeRunSync()

  private final class InMemoryNClob(initialValue: String) extends NClob {
    private val delegate: SerialClob = new SerialClob(initialValue.toCharArray)

    override def length(): Long = delegate.length()
    override def getSubString(pos: Long, length: Int): String = delegate.getSubString(pos, length)
    override def getCharacterStream(): Reader = delegate.getCharacterStream()
    override def getAsciiStream(): InputStream = delegate.getAsciiStream()
    override def position(searchstr: String, start: Long): Long = delegate.position(searchstr, start)
    override def position(searchstr: Clob, start: Long): Long = delegate.position(searchstr, start)
    override def setString(pos: Long, str: String): Int = delegate.setString(pos, str)
    override def setString(pos: Long, str: String, offset: Int, len: Int): Int = delegate.setString(pos, str, offset, len)
    override def setAsciiStream(pos: Long): OutputStream = delegate.setAsciiStream(pos)
    override def setCharacterStream(pos: Long): Writer = delegate.setCharacterStream(pos)
    override def truncate(len: Long): Unit = delegate.truncate(len)
    override def free(): Unit = delegate.free()
    override def getCharacterStream(pos: Long, length: Long): Reader = delegate.getCharacterStream(pos, length)
  }

  private final class RecordingDriver extends Driver {
    val lastUrl: AtomicReference[String] = new AtomicReference[String]("")

    override def acceptsURL(url: String): Boolean = url.startsWith("jdbc:recording:")

    override def connect(url: String, info: Properties): Connection = {
      lastUrl.set(url)
      null.asInstanceOf[Connection]
    }

    override def getMajorVersion(): Int = 1
    override def getMinorVersion(): Int = 4
    override def getPropertyInfo(url: String, info: Properties): Array[DriverPropertyInfo] =
      Array(new DriverPropertyInfo("schema", "public"))
    override def jdbcCompliant(): Boolean = false
    override def getParentLogger(): Logger = Logger.getLogger("recording-driver")
  }

  private final class MutableRef(private val baseTypeName: String, initialValue: AnyRef) extends java.sql.Ref {
    private var value: AnyRef = initialValue

    override def getBaseTypeName(): String = baseTypeName
    override def getObject: AnyRef = value
    override def getObject(map: java.util.Map[String, Class[_]]): AnyRef = value
    override def setObject(value: Any): Unit = this.value = value.asInstanceOf[AnyRef]
  }

  private final class RecordingSQLData(private val typeName: String) extends SQLData {
    val lastReadType: AtomicReference[String] = new AtomicReference[String]("")
    val lastOutput: AtomicReference[SQLOutput] = new AtomicReference[SQLOutput]()

    override def getSQLTypeName: String = typeName

    override def readSQL(stream: SQLInput, typeName: String): Unit =
      lastReadType.set(typeName)

    override def writeSQL(stream: SQLOutput): Unit =
      lastOutput.set(stream)
  }
}
