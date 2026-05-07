/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package io_getquill.quill_jdbc_3

import io.getquill._
import io.getquill.context.ExecutionType
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class Quill_jdbc_3Test {
  private case class PersonRow(id: Int, name: String, age: Int, active: Boolean)

  private val context: SqlMirrorContext[MirrorSqlDialect, Literal] =
    new SqlMirrorContext(MirrorSqlDialect, Literal)

  import context._

  @Test
  def test(): Unit = {
    println("This is just a placeholder, implement your test")
  }

  @Test
  def quotedQueryUsesLiftedValuesAsPreparedParameters(): Unit = {
    val minimumAge: Int = 21
    val result: QueryMirror[(Int, String)] = context.run {
      quote {
        query[PersonRow]
          .filter(person => person.age >= lift(minimumAge))
          .map(person => (person.id, person.name))
      }
    }

    assertThat(result.string)
      .isEqualTo("SELECT person.id AS _1, person.name AS _2 FROM PersonRow person WHERE person.age >= ?")
    assertThat(result.prepareRow.data)
      .isEqualTo(List("_1" -> minimumAge))
    assertThat(result.info.executionType)
      .isEqualTo(ExecutionType.Static)
  }

  @Test
  def insertValueActionPreparesLiftedCaseClassFields(): Unit = {
    val person: PersonRow = PersonRow(1, "Ada", 36, active = true)
    val result: ActionMirror = context.run {
      quote {
        query[PersonRow].insertValue(lift(person))
      }
    }

    assertThat(result.string)
      .isEqualTo("INSERT INTO PersonRow (id,name,age,active) VALUES (?, ?, ?, ?)")
    assertThat(result.prepareRow.data)
      .isEqualTo(List("_1" -> person.id, "_2" -> person.name, "_3" -> person.age, "_4" -> person.active))
    assertThat(result.info.executionType)
      .isEqualTo(ExecutionType.Static)
  }
}
