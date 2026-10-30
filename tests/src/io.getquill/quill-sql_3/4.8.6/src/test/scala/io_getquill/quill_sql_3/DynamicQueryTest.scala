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

class DynamicQueryTest {
  val ctx: SqlMirrorContext[PostgresDialect, SnakeCase] = new SqlMirrorContext(PostgresDialect, SnakeCase)
  import ctx.*

  /** A non-inline quotation is kept as an AST and translated when it runs. */
  def adultsOlderThan(minimumAge: Int): Quoted[Query[Person]] =
    quote(query[Person].filter(p => p.age > lift(minimumAge)))

  @Test
  def translatesNonInlineQuotationAtRunTime(): Unit = {
    val result = ctx.run(adultsOlderThan(30))

    assertThat(result.string).isEqualTo("SELECT p.id, p.first_name AS firstName, p.age FROM person p WHERE p.age > ?")
    assertThat(result.info.executionType).isEqualTo(ExecutionType.Dynamic)
    assertThat(result.prepareRow.data.map(_._2)).isEqualTo(List(30))

    val decoded: Person = result.extractor(Row(3, "Linus", 54), MirrorSession.default)
    assertThat(decoded).isEqualTo(Person(3, "Linus", 54))
  }

  @Test
  def composesDynamicQueryFromRuntimeOptions(): Unit = {
    val minimumAge: Option[Int] = Some(18)
    val namePrefix: Option[String] = None
    val sortByName: Boolean = true

    val filtered = dynamicQuery[Person]
      .filterOpt(minimumAge)((p, age) => quote(p.age >= age))
      .filterOpt(namePrefix)((p, prefix) => quote(p.firstName.startsWith(prefix)))
    val sorted = if (sortByName) filtered.sortBy(p => quote(p.firstName)) else filtered
    val result = ctx.run(sorted.map(p => quote(p.firstName)))

    assertThat(result.string).isEqualTo("SELECT v0.first_name AS firstName FROM person v0 WHERE v0.age >= ? ORDER BY v0.first_name ASC NULLS FIRST")
    assertThat(result.info.executionType).isEqualTo(ExecutionType.Dynamic)
    assertThat(result.prepareRow.data.map(_._2)).isEqualTo(List(18))
  }

  @Test
  def appliesRuntimeAssignmentsToDynamicUpdate(): Unit = {
    val changedName: Option[String] = Some("Ada Lovelace")
    val changedAge: Option[Int] = None

    val result = ctx.run(
      dynamicQuery[Person]
        .filter(p => quote(p.id == lift(1)))
        .update(setOpt[Person, String](_.firstName, changedName), setOpt[Person, Int](_.age, changedAge))
    )

    assertThat(result.string).isEqualTo("UPDATE person AS v0 SET first_name = ? WHERE v0.id = ?")
    assertThat(result.info.executionType).isEqualTo(ExecutionType.Dynamic)
    assertThat(result.prepareRow.data.map(_._2)).isEqualTo(List("Ada Lovelace", 1))
  }
}
