/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package io_getquill.quill_jdbc_2_13

import io.getquill.H2JdbcContext
import io.getquill.SnakeCase
import org.assertj.core.api.Assertions.assertThat
import org.h2.jdbcx.JdbcDataSource
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test

import java.io.Closeable
import java.io.PrintWriter
import java.sql.Connection
import java.sql.SQLFeatureNotSupportedException
import java.sql.Types
import java.time.LocalDate
import java.time.LocalDateTime
import java.util.UUID
import java.util.concurrent.atomic.AtomicInteger
import java.util.logging.Logger
import javax.sql.DataSource
import scala.jdk.CollectionConverters._

class Quill_jdbc_2_13Test {
  import Quill_jdbc_2_13Test._

  private val context: H2JdbcContext[SnakeCase.type] = new H2JdbcContext(SnakeCase, newDataSource())
  import context._

  @AfterEach
  def closeContext(): Unit = {
    context.close()
  }

  @Test
  def quotedCrudQueriesRunAgainstJdbcAndDecodeOptionalValues(): Unit = {
    val people = quote(querySchema[Person]("people"))
    createPeopleTable()

    val inserted: List[Long] = context.run(
      liftQuery(List(
        Person(1, "Ada", 36, Some("countess")),
        Person(2, "Grace", 37, None),
        Person(3, "Katherine", 25, Some("orbital"))
      )).foreach(person => people.insertValue(person))
    )
    val adultNames: List[String] = context.run(
      people.filter(person => person.age >= lift(36)).sortBy(_.id).map(_.name)
    )

    assertThat(inserted.asJava).containsExactly(1L, 1L, 1L)
    assertThat(adultNames.asJava).containsExactly("Ada", "Grace")
    assertThat(context.run(people.filter(_.nickname.isEmpty).map(_.name)).asJava).containsExactly("Grace")
  }

  @Test
  def updateDeleteAndTransactionsPreserveJdbcSemantics(): Unit = {
    val people = quote(querySchema[Person]("people"))
    createPeopleTable()
    context.run(people.insertValue(lift(Person(1, "Ada", 36, Some("countess")))))
    context.run(people.insertValue(lift(Person(2, "Grace", 37, None))))
    context.run(people.insertValue(lift(Person(3, "Katherine", 25, Some("orbital")))))

    val updatedNickname: Option[String] = Some("compiler")
    val updated: Long = context.run(
      people.filter(_.id == lift(2)).update(_.age -> lift(38), _.nickname -> lift(updatedNickname))
    )
    val deleted: Long = context.run(people.filter(_.age < lift(30)).delete)

    assertThat(updated).isEqualTo(1L)
    assertThat(deleted).isEqualTo(1L)
    assertThat(context.run(people.sortBy(_.id)).asJava).containsExactly(
      Person(1, "Ada", 36, Some("countess")),
      Person(2, "Grace", 38, Some("compiler"))
    )

    val rollback: IllegalStateException = assertThrows(
      classOf[IllegalStateException],
      () => context.transaction {
        context.run(people.insertValue(lift(Person(4, "Rollback", 99, None))))
        throw new IllegalStateException("rollback requested")
      }
    )

    assertThat(rollback).hasMessage("rollback requested")
    assertThat(context.run(people.filter(_.id == lift(4))).asJava).isEmpty()
  }

  @Test
  def jdbcEncodersAndDecodersRoundTripCommonScalarTypes(): Unit = {
    val records = quote(querySchema[AuditRecord]("audit_records"))
    createAuditTable()
    val id: UUID = UUID.fromString("123e4567-e89b-12d3-a456-426614174000")
    val record: AuditRecord = AuditRecord(
      id,
      active = true,
      BigDecimal("42.50"),
      LocalDate.of(2024, 2, 29),
      LocalDateTime.of(2024, 2, 29, 10, 15, 30),
      Array[Byte](1, 2, 3, 5, 8),
      None
    )

    val inserted: Long = context.run(records.insertValue(lift(record)))
    val loaded: AuditRecord = context.run(records.filter(_.id == lift(id))).head

    assertThat(inserted).isEqualTo(1L)
    assertThat(loaded.id).isEqualTo(record.id)
    assertThat(loaded.active).isTrue()
    assertThat(loaded.score.bigDecimal).isEqualByComparingTo(record.score.bigDecimal)
    assertThat(loaded.createdOn).isEqualTo(record.createdOn)
    assertThat(loaded.createdAt).isEqualTo(record.createdAt)
    assertArrayEquals(record.payload, loaded.payload)
    assertThat(loaded.note).isEqualTo(None)
  }

  @Test
  def customEncodersAndDecodersAreAppliedForUserDefinedValueTypes(): Unit = {
    implicit val encodeEmail: Encoder[Email] = encoder[Email](
      Types.VARCHAR,
      (index: Index, value: Email, row: PrepareRow) => row.setString(index, value.value)
    )
    implicit val decodeEmail: Decoder[Email] = decoder((index, row, _) => Email(row.getString(index)))
    val contacts = quote(querySchema[Contact]("contacts"))
    requireSuccessfulProbe(
      "create table contacts (id int primary key, email varchar(128) not null)"
    )

    context.run(contacts.insertValue(lift(Contact(1, Email("ada@example.com")))))
    context.run(contacts.insertValue(lift(Contact(2, Email("grace@example.com")))))

    val domainContacts: List[Contact] = context.run(
      contacts.filter(_.email == lift(Email("grace@example.com")))
    )

    assertThat(domainContacts.asJava).containsExactly(Contact(2, Email("grace@example.com")))
  }

  @Test
  def translateAndProbeExposeGeneratedSqlAndDatabaseValidation(): Unit = {
    val people = quote(querySchema[Person]("people"))
    createPeopleTable()

    val translated: String = context.translate(
      people.filter(person => person.age > 30).map(person => person.name)
    )

    assertThat(translated.toLowerCase).contains("select")
    assertThat(translated).contains("people")
    assertThat(translated).contains("age")
    assertThat(context.probe("select 1").isSuccess).isTrue()
    assertThat(context.probe("select * from table_that_does_not_exist").isFailure).isTrue()
  }

  private def createPeopleTable(): Unit = {
    requireSuccessfulProbe(
      """
        |create table people (
        |  id int primary key,
        |  name varchar(64) not null,
        |  age int not null,
        |  nickname varchar(64)
        |)
        |""".stripMargin
    )
  }

  private def createAuditTable(): Unit = {
    requireSuccessfulProbe(
      """
        |create table audit_records (
        |  id uuid primary key,
        |  active boolean not null,
        |  score decimal(10, 2) not null,
        |  created_on date not null,
        |  created_at timestamp not null,
        |  payload varbinary not null,
        |  note varchar(128)
        |)
        |""".stripMargin
    )
  }

  private def requireSuccessfulProbe(sql: String): Unit = {
    val result = context.probe(sql)
    assertThat(result.isSuccess).describedAs(result.failed.toOption.map(_.getMessage).getOrElse(sql)).isTrue()
  }
}

object Quill_jdbc_2_13Test {
  private val databaseId: AtomicInteger = new AtomicInteger(0)

  final case class Person(id: Int, name: String, age: Int, nickname: Option[String])

  final case class AuditRecord(
      id: UUID,
      active: Boolean,
      score: BigDecimal,
      createdOn: LocalDate,
      createdAt: LocalDateTime,
      payload: Array[Byte],
      note: Option[String]
  )

  final case class Email(value: String)

  final case class Contact(id: Int, email: Email)

  private def newDataSource(): CloseableH2DataSource = {
    val dataSource: JdbcDataSource = new JdbcDataSource()
    dataSource.setURL(s"jdbc:h2:mem:quill_jdbc_${databaseId.incrementAndGet()};DATABASE_TO_UPPER=false;DB_CLOSE_DELAY=-1")
    dataSource.setUser("sa")
    dataSource.setPassword("")
    new CloseableH2DataSource(dataSource)
  }
}

final class CloseableH2DataSource(private val delegate: JdbcDataSource) extends DataSource with Closeable {
  override def getConnection: Connection = delegate.getConnection

  override def getConnection(username: String, password: String): Connection = delegate.getConnection(username, password)

  override def getLogWriter: PrintWriter = delegate.getLogWriter

  override def setLogWriter(out: PrintWriter): Unit = delegate.setLogWriter(out)

  override def setLoginTimeout(seconds: Int): Unit = delegate.setLoginTimeout(seconds)

  override def getLoginTimeout: Int = delegate.getLoginTimeout

  override def getParentLogger: Logger = throw new SQLFeatureNotSupportedException("Parent logger is not available")

  override def unwrap[T](iface: Class[T]): T = delegate.unwrap(iface)

  override def isWrapperFor(iface: Class[_]): Boolean = delegate.isWrapperFor(iface)

  override def close(): Unit = ()
}
