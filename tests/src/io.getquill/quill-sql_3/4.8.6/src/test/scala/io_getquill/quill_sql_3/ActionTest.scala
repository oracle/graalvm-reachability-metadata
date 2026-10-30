/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package io_getquill.quill_sql_3

import io.getquill.*
import io.getquill.context.ExecutionType
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class ActionTest {
  val ctx: SqlMirrorContext[PostgresDialect, SnakeCase] = new SqlMirrorContext(PostgresDialect, SnakeCase)
  import ctx.*

  @Test
  def insertsLiftedCaseClass(): Unit = {
    val person: Person = Person(1, "Ada", 36)
    val result = ctx.run(query[Person].insertValue(lift(person)))

    assertThat(result.string).isEqualTo("INSERT INTO person (id,first_name,age) VALUES (?, ?, ?)")
    assertThat(result.info.executionType).isEqualTo(ExecutionType.Static)
    assertThat(result.prepareRow.data.map(_._2)).isEqualTo(List(1, "Ada", 36))
  }

  @Test
  def updatesSelectedColumnsWithLiftedValues(): Unit = {
    val id: Int = 1
    val newAge: Int = 37
    val result = ctx.run(query[Person].filter(p => p.id == lift(id)).update(_.age -> lift(newAge)))

    assertThat(result.string).isEqualTo("UPDATE person AS p SET age = ? WHERE p.id = ?")
    assertThat(result.prepareRow.data.map(_._2)).isEqualTo(List(37, 1))
  }

  @Test
  def returnsGeneratedColumnFromInsert(): Unit = {
    val person: Person = Person(0, "Grace", 45)
    val result = ctx.run(query[Person].insertValue(lift(person)).returningGenerated(_.id))

    assertThat(result.string).isEqualTo("INSERT INTO person (first_name,age) VALUES (?, ?) RETURNING id")
    assertThat(result.prepareRow.data.map(_._2)).isEqualTo(List("Grace", 45))
  }

  @Test
  def batchesInsertsOverLiftedCollection(): Unit = {
    val people: List[Person] = List(Person(1, "Ada", 36), Person(2, "Grace", 45))
    val result = ctx.run(liftQuery(people).foreach(p => query[Person].insertValue(p)))

    assertThat(result.groups.size).isEqualTo(1)
    val (sql, rows) = result.groups.head
    assertThat(sql).isEqualTo("INSERT INTO person (id,first_name,age) VALUES (?, ?, ?)")
    assertThat(rows.map(_.data.map(_._2))).isEqualTo(List(List(1, "Ada", 36), List(2, "Grace", 45)))
  }

  @Test
  def deletesFilteredRows(): Unit = {
    val maximumAge: Int = 17
    val result = ctx.run(query[Person].filter(p => p.age <= lift(maximumAge)).delete)

    assertThat(result.string).isEqualTo("DELETE FROM person AS p WHERE p.age <= ?")
    assertThat(result.prepareRow.data.map(_._2)).isEqualTo(List(17))
  }
}
