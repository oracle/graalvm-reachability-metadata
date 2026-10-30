/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package io_getquill.quill_sql_3

import io.getquill.*
import io.getquill.context.ExecutionType
import io.getquill.context.mirror.MirrorSession
import io.getquill.context.mirror.Row
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class StaticQueryTest {
  val ctx: SqlMirrorContext[PostgresDialect, SnakeCase] = new SqlMirrorContext(PostgresDialect, SnakeCase)
  import ctx.*

  @Test
  def translatesFilterAndProjectionAtCompileTime(): Unit = {
    val result = ctx.run(query[Person].filter(p => p.age > 18).map(p => p.firstName))

    assertThat(result.string).isEqualTo("SELECT p.first_name AS firstName FROM person p WHERE p.age > 18")
    assertThat(result.info.executionType).isEqualTo(ExecutionType.Static)
    assertThat(result.prepareRow.data.isEmpty).isTrue()
  }

  @Test
  def bindsLiftedValuesAndDecodesRows(): Unit = {
    val minimumAge: Int = 21
    val result = ctx.run(query[Person].filter(p => p.age >= lift(minimumAge)).sortBy(p => p.firstName))

    assertThat(result.string)
      .isEqualTo("SELECT p.id, p.first_name AS firstName, p.age FROM person p WHERE p.age >= ? ORDER BY p.first_name ASC NULLS FIRST")
    assertThat(result.info.executionType).isEqualTo(ExecutionType.Static)
    assertThat(result.prepareRow.data.map(_._2)).isEqualTo(List(21))

    val decoded: Person = result.extractor(Row(7, "Ada", 36), MirrorSession.default)
    assertThat(decoded).isEqualTo(Person(7, "Ada", 36))
  }

  @Test
  def translatesJoinsBetweenEntities(): Unit = {
    val result = ctx.run(
      query[Person]
        .join(query[Address])
        .on((p, a) => p.id == a.personId)
        .map((p, a) => (p.firstName, a.streetName))
    )

    assertThat(result.string)
      .isEqualTo("SELECT p.first_name AS _1, a.street_name AS _2 FROM person p INNER JOIN address a ON p.id = a.person_id")
    assertThat(result.info.executionType).isEqualTo(ExecutionType.Static)

    val decoded: (String, String) = result.extractor(Row("Ada", "Main Street"), MirrorSession.default)
    assertThat(decoded).isEqualTo(("Ada", "Main Street"))
  }
}
