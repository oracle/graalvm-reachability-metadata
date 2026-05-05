/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_tpolecat.doobie_postgres_3

import cats.~>
import cats.effect.IO
import doobie.Meta
import doobie.free.connection.{ConnectionIO, ConnectionOp}
import doobie.implicits._
import doobie.postgres.Text
import doobie.postgres.implicits._
import doobie.postgres.pgisimplicits._
import doobie.postgres.sqlstate
import java.net.InetAddress
import java.sql.SQLException
import java.time.{Instant, LocalDate, LocalDateTime, LocalTime, OffsetDateTime, ZonedDateTime}
import java.util.UUID
import org.junit.jupiter.api.Assertions.{assertEquals, assertNotNull, assertThrows, assertTrue}
import org.junit.jupiter.api.Test
import org.postgis.{Geometry, LineString, MultiPoint, Point}
import org.postgresql.geometric.{PGbox, PGcircle, PGlseg, PGpath, PGpoint, PGpolygon}
import org.postgresql.util.PGInterval

class Doobie_postgres_3Test {
  @Test
  def textEncodersRenderPostgresCopyLiteralsForScalarValues(): Unit = {
    assertEquals("plain", Text[String].encode("plain"))
    assertEquals(
      "tab\\tnewline\\nreturn\\rbackslash\\\\",
      Text[String].encode("tab\tnewline\nreturn\rbackslash\\")
    )
    assertEquals("Z", Text[Char].encode('Z'))
    assertEquals("42", Text[Int].encode(42))
    assertEquals("-7", Text[Short].encode((-7).toShort))
    assertEquals("1234567890123", Text[Long].encode(1234567890123L))
    assertEquals("3.5", Text[Float].encode(3.5f))
    assertEquals("-9.25", Text[Double].encode(-9.25d))
    assertEquals("12345.6789", Text[BigDecimal].encode(BigDecimal("12345.6789")))
    assertEquals("true", Text[Boolean].encode(true))
    assertEquals("\\\\x00010aff", Text[Array[Byte]].encode(Array[Byte](0, 1, 10, -1)))
    assertEquals("\\\\x", Text[Array[Byte]].encode(Array.emptyByteArray))
  }

  @Test
  def textEncodersComposeForOptionsProductsTuplesAndArrays(): Unit = {
    assertEquals("value", Text[Option[String]].encode(Some("value")))
    assertEquals(Text.NULL, Text[Option[Int]].encode(None))
    assertEquals(s"7${Text.DELIMETER}seven", Text[Int].product(Text[String]).encode((7, "seven")))
    assertEquals("5\tfive\t\\N", Text[(Int, String, Option[Int])].encode((5, "five", None)))
    assertEquals("99", Text[Int].contramap[Long](_.toInt).encode(99L))
    assertEquals("{1,2,3}", Text[List[Int]].encode(List(1, 2, 3)))
    assertEquals(
      "{\"alpha\",\"b\\\\\"c\",\"d\\\\\\\\e\",\"line\\nbreak\"}",
      Text[List[String]].encode(List("alpha", "b\"c", "d\\e", "line\nbreak"))
    )
    assertEquals("{{1,2},{3,4}}", Text[List[List[Int]]].encode(List(List(1, 2), List(3, 4))))
  }

  @Test
  def postgresMetaInstancesAreAvailableForDriverSpecificTypes(): Unit = {
    assertMetaAvailable[PGbox]
    assertMetaAvailable[PGcircle]
    assertMetaAvailable[PGlseg]
    assertMetaAvailable[PGpath]
    assertMetaAvailable[PGpoint]
    assertMetaAvailable[PGpolygon]
    assertMetaAvailable[PGInterval]
    assertMetaAvailable[UUID]
    assertMetaAvailable[InetAddress]
    assertMetaAvailable[java.util.Map[String, String]]
    assertMetaAvailable[Map[String, String]]
  }

  @Test
  def postgresArrayMetaInstancesCoverBoxedUnboxedOptionalAndDecimalArrays(): Unit = {
    assertMetaAvailable[Array[java.lang.Boolean]]
    assertMetaAvailable[Array[Option[java.lang.Boolean]]]
    assertMetaAvailable[Array[java.lang.Integer]]
    assertMetaAvailable[Array[Option[java.lang.Integer]]]
    assertMetaAvailable[Array[java.lang.Long]]
    assertMetaAvailable[Array[Option[java.lang.Long]]]
    assertMetaAvailable[Array[java.lang.Float]]
    assertMetaAvailable[Array[Option[java.lang.Float]]]
    assertMetaAvailable[Array[java.lang.Double]]
    assertMetaAvailable[Array[Option[java.lang.Double]]]
    assertMetaAvailable[Array[String]]
    assertMetaAvailable[Array[Option[String]]]
    assertMetaAvailable[Array[UUID]]
    assertMetaAvailable[Array[Option[UUID]]]
    assertMetaAvailable[Array[java.math.BigDecimal]]
    assertMetaAvailable[Array[Option[java.math.BigDecimal]]]
    assertMetaAvailable[Array[Boolean]]
    assertMetaAvailable[Array[Option[Boolean]]]
    assertMetaAvailable[Array[Int]]
    assertMetaAvailable[Array[Option[Int]]]
    assertMetaAvailable[Array[Long]]
    assertMetaAvailable[Array[Option[Long]]]
    assertMetaAvailable[Array[Float]]
    assertMetaAvailable[Array[Option[Float]]]
    assertMetaAvailable[Array[Double]]
    assertMetaAvailable[Array[Option[Double]]]
    assertMetaAvailable[Array[BigDecimal]]
    assertMetaAvailable[Array[Option[BigDecimal]]]
  }

  @Test
  def javaTimeAndPostgisMetaInstancesAreAvailable(): Unit = {
    assertMetaAvailable[OffsetDateTime]
    assertMetaAvailable[Instant]
    assertMetaAvailable[ZonedDateTime]
    assertMetaAvailable[LocalDateTime]
    assertMetaAvailable[LocalDate]
    assertMetaAvailable[LocalTime]
    assertMetaAvailable[Geometry]
    assertMetaAvailable[Point]
    assertMetaAvailable[LineString]
    assertMetaAvailable[MultiPoint]
  }

  @Test
  def copyInReturnsZeroForEmptyCollectionsWithoutOpeningCopyApi(): Unit = {
    val copyStatement = sql"COPY target_table (id, name) FROM STDIN"
    val rowsCopied: Long = copyStatement
      .copyIn(List.empty[(Int, String)])
      .foldMap(failOnConnectionAccess)
      .unsafeRunSync()

    assertEquals(0L, rowsCopied)
  }

  @Test
  def postgresEnumHelpersConstructMetaMappingsForScalaAndJavaEnums(): Unit = {
    val scalaEnumMeta: Meta[Weekday.Value] = pgEnum(Weekday, "weekday")
    val stringEnumMeta: Meta[TrafficLight] = pgEnumStringOpt[TrafficLight](
      "traffic_light",
      TrafficLight.fromDatabaseValue,
      _.databaseValue
    )
    val javaEnumMeta: Meta[java.time.Month] = pgJavaEnum[java.time.Month]("month")

    assertNotNull(scalaEnumMeta)
    assertNotNull(stringEnumMeta)
    assertNotNull(javaEnumMeta)
  }

  @Test
  def postgresSqlStateConstantsExposeCanonicalCodes(): Unit = {
    assertEquals("00000", sqlstate.class00.SUCCESSFUL_COMPLETION.value)
    assertEquals("23505", sqlstate.class23.UNIQUE_VIOLATION.value)
    assertEquals("23503", sqlstate.class23.FOREIGN_KEY_VIOLATION.value)
    assertEquals("40001", sqlstate.class40.SERIALIZATION_FAILURE.value)
    assertEquals("40P01", sqlstate.class40.DEADLOCK_DETECTED.value)
    assertEquals("42P01", sqlstate.class42.UNDEFINED_TABLE.value)
    assertEquals("57P01", sqlstate.class57.ADMIN_SHUTDOWN.value)
  }

  @Test
  def postgresExplainSyntaxPrefixesQueriesAndUpdatesWithExplainStatements(): Unit = {
    val explainedQuerySql: String = capturePreparedSql(sql"select 1".query[Int].explain)
    val analyzedUpdateSql: String = capturePreparedSql(
      sql"update target_table set processed = true".update.explainAnalyze
    )

    assertEquals("EXPLAIN select 1", explainedQuerySql)
    assertEquals("EXPLAIN ANALYZE update target_table set processed = true", analyzedUpdateSql)
  }

  @Test
  def postgresSqlStateSyntaxRecoversOnlyMatchingSqlExceptions(): Unit = {
    val recovered: String = IO
      .raiseError[String](new SQLException("duplicate key", sqlstate.class23.UNIQUE_VIOLATION.value))
      .onUniqueViolation(IO.pure("recovered duplicate"))
      .unsafeRunSync()

    val propagated: SQLException = assertThrows(classOf[SQLException], () => {
      IO
        .raiseError[String](new SQLException("duplicate key", sqlstate.class23.UNIQUE_VIOLATION.value))
        .onForeignKeyViolation(IO.pure("wrong handler"))
        .unsafeRunSync()
      ()
    })

    val nonSql: IllegalStateException = assertThrows(classOf[IllegalStateException], () => {
      IO
        .raiseError[String](new IllegalStateException("not a SQL exception"))
        .onUniqueViolation(IO.pure("wrong handler"))
        .unsafeRunSync()
      ()
    })

    assertEquals("recovered duplicate", recovered)
    assertEquals(sqlstate.class23.UNIQUE_VIOLATION.value, propagated.getSQLState)
    assertTrue(nonSql.getMessage.contains("not a SQL exception"))
  }

  private def assertMetaAvailable[A](implicit meta: Meta[A]): Unit = {
    assertNotNull(meta)
  }

  private def capturePreparedSql[A](program: ConnectionIO[A]): String = {
    val capturedSql: CapturedPreparedSql = assertThrows(classOf[CapturedPreparedSql], () => {
      program.foldMap(capturePreparedStatementSql).unsafeRunSync()
      ()
    })

    capturedSql.sql
  }

  private val capturePreparedStatementSql: ConnectionOp ~> IO =
    new (ConnectionOp ~> IO) {
      override def apply[A](operation: ConnectionOp[A]): IO[A] =
        operation match {
          case ConnectionOp.BracketCase(acquire, _, _) =>
            acquire
              .foldMap(this)
              .flatMap(_ => IO.raiseError(new AssertionError(s"unexpected completed bracket: $operation")))
          case ConnectionOp.PrepareStatement(sql) => IO.raiseError(new CapturedPreparedSql(sql))
          case _ => IO.raiseError(new AssertionError(s"unexpected connection operation: $operation"))
        }
    }

  private val failOnConnectionAccess: ConnectionOp ~> IO =
    new (ConnectionOp ~> IO) {
      override def apply[A](operation: ConnectionOp[A]): IO[A] =
        IO.raiseError(new AssertionError(s"unexpected connection operation: $operation"))
    }

  private object Weekday extends Enumeration {
    val Monday: Value = Value("monday")
    val Tuesday: Value = Value("tuesday")
  }

  private final class CapturedPreparedSql(val sql: String)
    extends RuntimeException(s"captured prepared SQL: $sql")

  private sealed trait TrafficLight {
    def databaseValue: String
  }

  private object TrafficLight {
    case object Red extends TrafficLight {
      val databaseValue: String = "red"
    }

    case object Green extends TrafficLight {
      val databaseValue: String = "green"
    }

    def fromDatabaseValue(value: String): Option[TrafficLight] = value match {
      case Red.databaseValue   => Some(Red)
      case Green.databaseValue => Some(Green)
      case _                   => None
    }
  }
}
