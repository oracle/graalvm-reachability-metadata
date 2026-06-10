/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package io_getquill.quill_jdbc_3

import com.typesafe.config.Config
import com.typesafe.config.ConfigFactory
import io.getquill.*
import org.assertj.core.api.Assertions.assertThat
import org.h2.jdbcx.JdbcDataSource
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test

import java.sql.Connection
import java.sql.SQLException
import java.time.LocalDate
import java.time.LocalDateTime
import java.util.UUID
import javax.sql.DataSource

case class JdbcPerson(id: Int, firstName: String, age: Int, active: Boolean, nickname: Option[String])

case class JdbcAuditEvent(
  id: Int,
  personId: Int,
  eventDate: LocalDate,
  createdAt: LocalDateTime,
  amount: BigDecimal,
  referenceId: UUID,
  payload: Array[Byte]
)

case class PersonId(value: Int)

case class GeneratedPerson(id: Int, firstName: String)

case class MappedPerson(id: PersonId, name: String)

given MappedEncoding[PersonId, Int] = MappedEncoding[PersonId, Int](_.value)
given MappedEncoding[Int, PersonId] = MappedEncoding[Int, PersonId](PersonId.apply)

class Quill_jdbc_3Test {
  @Test
  def h2JdbcContextRunsTypedCrudQueriesWithOptionsAndLifts(): Unit = {
    withDirectContext { ctx =>
      import ctx.*

      createPersonTable(ctx.dataSource)

      val insertedAda: Long = ctx.run(
        query[JdbcPerson].insertValue(lift(JdbcPerson(1, "Ada", 36, active = true, Some("Countess"))))
      )
      val insertedBob: Long = ctx.run(
        query[JdbcPerson].insertValue(lift(JdbcPerson(2, "Bob", 17, active = true, None)))
      )

      assertThat(insertedAda).isEqualTo(1L)
      assertThat(insertedBob).isEqualTo(1L)

      val minimumAge: Int = 18
      val adults: List[(Int, String)] = ctx.run(
        query[JdbcPerson]
          .filter(person => person.active && person.age >= lift(minimumAge))
          .sortBy(_.firstName)
          .map(person => (person.id, person.firstName))
      )

      assertThat(adults).isEqualTo(List((1, "Ada")))

      val updated: Long = ctx.run(
        query[JdbcPerson]
          .filter(_.id == lift(2))
          .update(
            _.age -> lift(18),
            _.nickname -> lift(Option("Builder"))
          )
      )
      assertThat(updated).isEqualTo(1L)

      val bob: JdbcPerson = ctx.run(query[JdbcPerson].filter(_.id == lift(2))).head
      assertThat(bob).isEqualTo(JdbcPerson(2, "Bob", 18, active = true, Some("Builder")))

      val deleted: Long = ctx.run(query[JdbcPerson].filter(_.id == lift(99)).delete)
      assertThat(deleted).isZero()
    }
  }

  @Test
  def jdbcContextRunsSqlInfixExpressionsInsideTypedQueries(): Unit = {
    withDirectContext { ctx =>
      import ctx.*

      createPersonTable(ctx.dataSource)
      assertThat(ctx.run(query[JdbcPerson].insertValue(lift(JdbcPerson(1, "Ada", 36, active = true, None)))))
        .isEqualTo(1L)
      assertThat(ctx.run(query[JdbcPerson].insertValue(lift(JdbcPerson(2, "Grace", 85, active = true, None)))))
        .isEqualTo(1L)
      assertThat(ctx.run(query[JdbcPerson].insertValue(lift(JdbcPerson(3, "Linus", 54, active = false, None)))))
        .isEqualTo(1L)

      val matchingInitial: String = "A"
      val lowercaseNames: List[String] = ctx.run(
        query[JdbcPerson]
          .filter(person => infix"SUBSTRING(${person.firstName}, 1, 1) = ${lift(matchingInitial)}".as[Boolean])
          .map(person => infix"LOWER(${person.firstName})".as[String])
      )

      assertThat(lowercaseNames).isEqualTo(List("ada"))
    }
  }

  @Test
  def jdbcContextBatchesRowsAndAggregatesResults(): Unit = {
    withDirectContext { ctx =>
      import ctx.*

      createPersonTable(ctx.dataSource)
      val people: List[JdbcPerson] = List(
        JdbcPerson(1, "Ada", 36, active = true, Some("Countess")),
        JdbcPerson(2, "Grace", 85, active = true, None),
        JdbcPerson(3, "Linus", 54, active = false, Some("Kernel"))
      )

      val inserted: List[Long] = ctx.run(
        liftQuery(people).foreach(person => query[JdbcPerson].insertValue(person)),
        2
      )
      assertThat(inserted).isEqualTo(List(2L, 1L))

      val activeCount: Long = ctx.run(query[JdbcPerson].filter(_.active).size)
      val oldestActiveAge: Option[Int] = ctx.run(query[JdbcPerson].filter(_.active).map(_.age).max)

      assertThat(activeCount).isEqualTo(2L)
      assertThat(oldestActiveAge).isEqualTo(Some(85))
    }
  }

  @Test
  def jdbcContextEncodesAndDecodesTemporalUuidDecimalAndBinaryColumns(): Unit = {
    withDirectContext { ctx =>
      import ctx.*

      createAuditEventTable(ctx.dataSource)
      val event: JdbcAuditEvent = JdbcAuditEvent(
        10,
        1,
        LocalDate.of(2026, 6, 10),
        LocalDateTime.of(2026, 6, 10, 14, 30, 15),
        BigDecimal("123.45"),
        UUID.fromString("123e4567-e89b-12d3-a456-426614174000"),
        Array[Byte](1, 2, 3, 4)
      )

      assertThat(ctx.run(query[JdbcAuditEvent].insertValue(lift(event)))).isEqualTo(1L)

      val selected: JdbcAuditEvent = ctx.run(query[JdbcAuditEvent].filter(_.id == lift(event.id))).head
      assertThat(selected.id).isEqualTo(event.id)
      assertThat(selected.personId).isEqualTo(event.personId)
      assertThat(selected.eventDate).isEqualTo(event.eventDate)
      assertThat(selected.createdAt).isEqualTo(event.createdAt)
      assertThat(selected.amount.bigDecimal).isEqualByComparingTo(event.amount.bigDecimal)
      assertThat(selected.referenceId).isEqualTo(event.referenceId)
      assertThat(selected.payload.toSeq).isEqualTo(event.payload.toSeq)
    }
  }

  @Test
  def jdbcContextRollsBackFailedTransactionsAndCommitsSuccessfulTransactions(): Unit = {
    withDirectContext { ctx =>
      import ctx.*

      createPersonTable(ctx.dataSource)

      val thrown: IllegalStateException = assertThrows(
        classOf[IllegalStateException],
        () =>
          ctx.transaction {
            ctx.run(query[JdbcPerson].insertValue(lift(JdbcPerson(1, "Ada", 36, active = true, None))))
            throw new IllegalStateException("rollback requested")
          }
      )
      assertThat(thrown).hasMessage("rollback requested")
      assertThat(ctx.run(query[JdbcPerson])).isEqualTo(Nil)

      ctx.transaction {
        ctx.run(query[JdbcPerson].insertValue(lift(JdbcPerson(2, "Grace", 85, active = true, None))))
        ctx.run(query[JdbcPerson].insertValue(lift(JdbcPerson(3, "Linus", 54, active = false, None))))
      }

      assertThat(ctx.run(query[JdbcPerson].sortBy(_.id).map(_.firstName))).isEqualTo(List("Grace", "Linus"))
    }
  }

  @Test
  def jdbcContextReturnsDatabaseGeneratedKeysForIdentityColumns(): Unit = {
    withDirectContext { ctx =>
      import ctx.*

      createGeneratedPersonTable(ctx.dataSource)

      val firstId: Int = ctx.run(
        query[GeneratedPerson]
          .insertValue(lift(GeneratedPerson(0, "Ida")))
          .returningGenerated(_.id)
      )
      val secondId: Int = ctx.run(
        query[GeneratedPerson]
          .insertValue(lift(GeneratedPerson(0, "Barbara")))
          .returningGenerated(_.id)
      )

      assertThat(firstId).isEqualTo(1)
      assertThat(secondId).isEqualTo(2)
      assertThat(ctx.run(query[GeneratedPerson].sortBy(_.id).map(person => (person.id, person.firstName))))
        .isEqualTo(List((1, "Ida"), (2, "Barbara")))
    }
  }

  @Test
  def jdbcContextUsesMappedEncodingsForDomainValueTypes(): Unit = {
    withDirectContext { ctx =>
      import ctx.*

      executeSql(
        ctx.dataSource,
        """
          |CREATE TABLE mapped_person (
          |  id INTEGER PRIMARY KEY,
          |  name VARCHAR(80) NOT NULL
          |)
          |""".stripMargin
      )

      val id: PersonId = PersonId(42)
      assertThat(ctx.run(query[MappedPerson].insertValue(lift(MappedPerson(id, "Katherine"))))).isEqualTo(1L)

      val selected: MappedPerson = ctx.run(query[MappedPerson].filter(_.id == lift(id))).head
      assertThat(selected).isEqualTo(MappedPerson(PersonId(42), "Katherine"))
    }
  }

  @Test
  def configConstructorCreatesClosableHikariBackedContext(): Unit = {
    val databaseName: String = s"quill_config_${UUID.randomUUID().toString.replace('-', '_')}"
    val config: Config = ConfigFactory.parseString(
      s"""
         |dataSourceClassName = "org.h2.jdbcx.JdbcDataSource"
         |dataSource.url = "jdbc:h2:mem:$databaseName;DB_CLOSE_DELAY=-1"
         |dataSource.user = "sa"
         |dataSource.password = ""
         |maximumPoolSize = 2
         |connectionTimeout = 5000
         |""".stripMargin
    )
    val ctx: H2JdbcContext[SnakeCase.type] = new H2JdbcContext(SnakeCase, config)

    try {
      import ctx.*

      createPersonTable(ctx.dataSource)
      assertThat(ctx.run(query[JdbcPerson].insertValue(lift(JdbcPerson(1, "Ada", 36, active = true, None)))))
        .isEqualTo(1L)
      assertThat(ctx.run(query[JdbcPerson].map(_.firstName))).isEqualTo(List("Ada"))
    } finally {
      ctx.close()
    }
  }

  private def withDirectContext(test: H2JdbcContext[SnakeCase.type] => Unit): Unit = {
    val dataSource: JdbcDataSource = new JdbcDataSource()
    dataSource.setURL(s"jdbc:h2:mem:quill_${UUID.randomUUID().toString.replace('-', '_')};DB_CLOSE_DELAY=-1")
    dataSource.setUser("sa")
    dataSource.setPassword("")
    val ctx: H2JdbcContext[SnakeCase.type] = new H2JdbcContext(SnakeCase, dataSource)

    try {
      test(ctx)
    } finally {
      shutdown(dataSource)
    }
  }

  private def createPersonTable(dataSource: DataSource): Unit = {
    executeSql(
      dataSource,
      """
        |CREATE TABLE jdbc_person (
        |  id INTEGER PRIMARY KEY,
        |  first_name VARCHAR(80) NOT NULL,
        |  age INTEGER NOT NULL,
        |  active BOOLEAN NOT NULL,
        |  nickname VARCHAR(80)
        |)
        |""".stripMargin
    )
  }

  private def createGeneratedPersonTable(dataSource: DataSource): Unit = {
    executeSql(
      dataSource,
      """
        |CREATE TABLE generated_person (
        |  id INTEGER GENERATED BY DEFAULT AS IDENTITY PRIMARY KEY,
        |  first_name VARCHAR(80) NOT NULL
        |)
        |""".stripMargin
    )
  }

  private def createAuditEventTable(dataSource: DataSource): Unit = {
    executeSql(
      dataSource,
      """
        |CREATE TABLE jdbc_audit_event (
        |  id INTEGER PRIMARY KEY,
        |  person_id INTEGER NOT NULL,
        |  event_date DATE NOT NULL,
        |  created_at TIMESTAMP NOT NULL,
        |  amount DECIMAL(12, 2) NOT NULL,
        |  reference_id UUID NOT NULL,
        |  payload BINARY(4) NOT NULL
        |)
        |""".stripMargin
    )
  }

  private def executeSql(dataSource: DataSource, sql: String): Unit = {
    val connection: Connection = dataSource.getConnection
    try {
      val statement = connection.createStatement()
      try {
        statement.execute(sql)
      } finally {
        statement.close()
      }
    } finally {
      connection.close()
    }
  }

  private def shutdown(dataSource: DataSource): Unit = {
    try {
      executeSql(dataSource, "SHUTDOWN")
    } catch {
      case _: SQLException => ()
    }
  }
}
