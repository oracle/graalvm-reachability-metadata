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
import io.getquill.H2JdbcContext
import io.getquill.JdbcContextConfig
import io.getquill.SnakeCase
import org.h2.jdbcx.JdbcDataSource
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

import java.sql.Connection
import java.sql.Statement
import java.time.LocalDate
import java.time.LocalDateTime
import java.util.UUID
import javax.sql.DataSource

case class QuillPerson(
  id: Int,
  name: String,
  age: Int,
  active: Boolean,
  favoriteTopic: Option[String]
)

case class QuillBook(
  id: Int,
  ownerId: Int,
  title: String,
  pages: Int
)

case class QuillGeneratedBook(
  id: Int,
  title: String
)

case class QuillEvent(
  id: UUID,
  createdOn: LocalDate,
  createdAt: LocalDateTime,
  label: String
)

case class QuillLegacyAudit(
  legacyId: Int,
  displayLabel: String
)

class Quill_jdbc_3Test {
  @Test
  def h2JdbcContextRunsCrudQueriesJoinsAndOptionalColumns(): Unit = {
    withH2Context { ctx =>
      import ctx.*

      val people: List[QuillPerson] = List(
        QuillPerson(1, "Ada", 36, active = true, Some("math")),
        QuillPerson(2, "Grace", 29, active = false, None),
        QuillPerson(3, "Linus", 54, active = true, Some("kernels"))
      )
      val books: List[QuillBook] = List(
        QuillBook(10, 1, "Notes on Engines", 120),
        QuillBook(11, 3, "Portable Kernels", 300),
        QuillBook(12, 3, "Distributed Builds", 180)
      )

      assertEquals(List(1L, 1L, 1L), ctx.run(liftQuery(people).foreach(p => query[QuillPerson].insertValue(p))))
      assertEquals(List(1L, 1L, 1L), ctx.run(liftQuery(books).foreach(b => query[QuillBook].insertValue(b))))

      val activeAdults: List[(String, Int)] = ctx.run(
        query[QuillPerson]
          .filter(p => p.active && p.age >= lift(30))
          .sortBy(_.age)
          .map(p => (p.name, p.age))
      )
      assertEquals(List(("Ada", 36), ("Linus", 54)), activeAdults)

      val topics: List[(String, Option[String])] = ctx.run(
        query[QuillPerson]
          .filter(_.favoriteTopic.isDefined)
          .sortBy(_.name)
          .map(p => (p.name, p.favoriteTopic))
      )
      assertEquals(List(("Ada", Some("math")), ("Linus", Some("kernels"))), topics)

      val joined: List[(String, String)] = ctx.run(
        query[QuillPerson]
          .join(query[QuillBook])
          .on((person, book) => person.id == book.ownerId)
          .filter { case (_, book) => book.pages > lift(150) }
          .sortBy { case (_, book) => book.title }
          .map { case (person, book) => (person.name, book.title) }
      )
      assertEquals(List(("Linus", "Distributed Builds"), ("Linus", "Portable Kernels")), joined)
    }
  }

  @Test
  def transactionsCommitRollbackAndReuseTheCurrentConnectionForNestedTransactions(): Unit = {
    withH2Context { ctx =>
      import ctx.*

      ctx.transaction {
        ctx.run(query[QuillPerson].insertValue(lift(QuillPerson(20, "Committed", 41, active = true, None))))
        ctx.transaction {
          ctx.run(query[QuillBook].insertValue(lift(QuillBook(21, 20, "Nested Work", 90))))
        }
      }

      assertEquals(List("Nested Work"), ctx.run(query[QuillBook].map(_.title)))

      val thrown: IllegalStateException = assertThrows(
        classOf[IllegalStateException],
        () =>
          ctx.transaction {
            ctx.run(query[QuillPerson].insertValue(lift(QuillPerson(30, "Rolled Back", 50, active = true, None))))
            ctx.run(query[QuillBook].insertValue(lift(QuillBook(31, 30, "Rolled Back Book", 99))))
            throw new IllegalStateException("force rollback")
          }
      )
      assertEquals("force rollback", thrown.getMessage)

      assertEquals(List("Committed"), ctx.run(query[QuillPerson].map(_.name)))
      assertEquals(List("Nested Work"), ctx.run(query[QuillBook].map(_.title)))
    }
  }

  @Test
  def liftedBatchActionsUpdateAndDeleteRowsWithPreparedJdbcParameters(): Unit = {
    withH2Context { ctx =>
      import ctx.*

      val people: List[QuillPerson] = List(
        QuillPerson(101, "Young", 19, active = true, None),
        QuillPerson(102, "Adult", 45, active = true, Some("databases")),
        QuillPerson(103, "Inactive", 70, active = false, None)
      )

      val inserted: List[Long] = ctx.run(liftQuery(people).foreach(p => query[QuillPerson].insertValue(p)), 2)
      assertEquals(List(2L, 1L), inserted)

      val updated: Long = ctx.run(
        query[QuillPerson]
          .filter(_.age < lift(30))
          .update(_.active -> lift(false), _.favoriteTopic -> lift(Option("too-young")))
      )
      assertEquals(1L, updated)

      val deleted: Long = ctx.run(query[QuillPerson].filter(person => !person.active).delete)
      assertEquals(2L, deleted)

      val remaining: List[QuillPerson] = ctx.run(query[QuillPerson])
      assertEquals(List(QuillPerson(102, "Adult", 45, active = true, Some("databases"))), remaining)
    }
  }

  @Test
  def querySchemaMapsCaseClassFieldsToCustomTableAndColumnNames(): Unit = {
    withH2Context { ctx =>
      import ctx.*

      inline def legacyAudits: EntityQuery[QuillLegacyAudit] = querySchema[QuillLegacyAudit](
        "legacy_audit_records",
        _.legacyId -> "record_key",
        _.displayLabel -> "label_text"
      )

      val firstAudit: QuillLegacyAudit = QuillLegacyAudit(401, "custom schema")
      val secondAudit: QuillLegacyAudit = QuillLegacyAudit(402, "mapped columns")

      assertEquals(1L, ctx.run(legacyAudits.insertValue(lift(firstAudit))))
      assertEquals(1L, ctx.run(legacyAudits.insertValue(lift(secondAudit))))

      val labels: List[String] = ctx.run(
        legacyAudits
          .filter(_.legacyId >= lift(402))
          .map(_.displayLabel)
      )
      assertEquals(List("mapped columns"), labels)
    }
  }

  @Test
  def h2ContextEncodesAndDecodesUuidAndJavaTimeValues(): Unit = {
    withH2Context { ctx =>
      import ctx.*

      val event: QuillEvent = QuillEvent(
        UUID.fromString("11111111-2222-3333-4444-555555555555"),
        LocalDate.of(2026, 6, 26),
        LocalDateTime.of(2026, 6, 26, 12, 34, 56),
        "metadata-test"
      )

      assertEquals(1L, ctx.run(query[QuillEvent].insertValue(lift(event))))
      assertEquals(List(event), ctx.run(query[QuillEvent].filter(_.id == lift(event.id))))
    }
  }

  @Test
  def jdbcContextConfigBuildsAHikariDataSourceForH2AndSupportsGeneratedKeys(): Unit = {
    val databaseName: String = uniqueDatabaseName()
    val config: Config = ConfigFactory.parseString(s"""
      jdbcUrl = "jdbc:h2:mem:$databaseName;DB_CLOSE_DELAY=-1;LOCK_TIMEOUT=10000"
      driverClassName = "org.h2.Driver"
      username = "sa"
      password = ""
      maximumPoolSize = 2
      minimumIdle = 0
      connectionTimeout = 10000
      validationTimeout = 10000
    """)
    val contextConfig: JdbcContextConfig = JdbcContextConfig(config)
    val ctx: H2JdbcContext[SnakeCase.type] = new H2JdbcContext(SnakeCase, contextConfig)

    try {
      createSchema(ctx.dataSource)
      import ctx.*

      val generatedId: Int = ctx.run(
        query[QuillGeneratedBook]
          .insertValue(lift(QuillGeneratedBook(0, "Generated Identity")))
          .returningGenerated(_.id)
      )

      assertTrue(generatedId > 0)
      assertEquals(List(QuillGeneratedBook(generatedId, "Generated Identity")), ctx.run(query[QuillGeneratedBook]))
      assertTrue(ctx.probe("SELECT 1").isSuccess)
      assertFalse(ctx.probe("SELECT * FROM table_that_does_not_exist").isSuccess)
    } finally {
      ctx.close()
    }
  }

  private def withH2Context(test: H2JdbcContext[SnakeCase.type] => Unit): Unit = {
    val dataSource: JdbcDataSource = newH2DataSource()
    createSchema(dataSource)
    val ctx: H2JdbcContext[SnakeCase.type] = new H2JdbcContext(SnakeCase, dataSource)
    try {
      test(ctx)
    } finally {
      dropDatabaseObjects(dataSource)
    }
  }

  private def newH2DataSource(): JdbcDataSource = {
    val dataSource: JdbcDataSource = new JdbcDataSource()
    dataSource.setURL(s"jdbc:h2:mem:${uniqueDatabaseName()};DB_CLOSE_DELAY=-1;LOCK_TIMEOUT=10000")
    dataSource.setUser("sa")
    dataSource.setPassword("")
    dataSource
  }

  private def uniqueDatabaseName(): String =
    s"quill_${UUID.randomUUID().toString.replace("-", "")}"

  private def createSchema(dataSource: DataSource): Unit = {
    val connection: Connection = dataSource.getConnection
    try {
      val statement: Statement = connection.createStatement()
      try {
        statement.execute(
          """
          CREATE TABLE quill_person (
            id INT PRIMARY KEY,
            name VARCHAR(100) NOT NULL,
            age INT NOT NULL,
            active BOOLEAN NOT NULL,
            favorite_topic VARCHAR(100)
          )
          """
        )
        statement.execute(
          """
          CREATE TABLE quill_book (
            id INT PRIMARY KEY,
            owner_id INT NOT NULL,
            title VARCHAR(100) NOT NULL,
            pages INT NOT NULL,
            CONSTRAINT fk_quill_book_person FOREIGN KEY (owner_id) REFERENCES quill_person(id)
          )
          """
        )
        statement.execute(
          """
          CREATE TABLE quill_generated_book (
            id INT GENERATED BY DEFAULT AS IDENTITY PRIMARY KEY,
            title VARCHAR(100) NOT NULL
          )
          """
        )
        statement.execute(
          """
          CREATE TABLE quill_event (
            id UUID PRIMARY KEY,
            created_on DATE NOT NULL,
            created_at TIMESTAMP NOT NULL,
            label VARCHAR(100) NOT NULL
          )
          """
        )
        statement.execute(
          """
          CREATE TABLE legacy_audit_records (
            record_key INT PRIMARY KEY,
            label_text VARCHAR(100) NOT NULL
          )
          """
        )
      } finally {
        statement.close()
      }
    } finally {
      connection.close()
    }
  }

  private def dropDatabaseObjects(dataSource: DataSource): Unit = {
    val connection: Connection = dataSource.getConnection
    try {
      val statement: Statement = connection.createStatement()
      try {
        statement.execute("DROP ALL OBJECTS")
      } finally {
        statement.close()
      }
    } finally {
      connection.close()
    }
  }
}
