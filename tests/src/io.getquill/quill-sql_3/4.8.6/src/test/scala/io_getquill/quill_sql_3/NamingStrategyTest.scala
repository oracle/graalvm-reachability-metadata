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

class NamingStrategyTest {
  @Test
  def keepsIdentifiersVerbatimWithLiteralNaming(): Unit = {
    val ctx: SqlMirrorContext[MirrorSqlDialect, Literal] = new SqlMirrorContext(MirrorSqlDialect, Literal)
    import ctx.*

    val result = ctx.run(query[Person].filter(p => p.firstName == "Ada").map(p => p.age))

    assertThat(result.string).isEqualTo("SELECT p.age FROM Person p WHERE p.firstName = 'Ada'")
    assertThat(result.info.executionType).isEqualTo(ExecutionType.Static)
  }

  @Test
  def quotesIdentifiersWithEscapeNaming(): Unit = {
    val ctx: SqlMirrorContext[PostgresDialect, Escape] = new SqlMirrorContext(PostgresDialect, Escape)
    import ctx.*

    val result = ctx.run(query[Address].map(a => a.streetName))

    assertThat(result.string).isEqualTo("SELECT a.\"streetName\" AS streetName FROM \"Address\" a")
  }

  @Test
  def appliesCompositeNamingStrategiesInOrder(): Unit = {
    val ctx: SqlMirrorContext[PostgresDialect, CompositeNamingStrategy2[SnakeCase, UpperCase]] =
      new SqlMirrorContext(PostgresDialect, NamingStrategy(SnakeCase, UpperCase))
    import ctx.*

    val result = ctx.run(query[Address].filter(a => a.personId == 1).map(a => a.streetName))

    assertThat(result.string).isEqualTo("SELECT a.STREET_NAME AS streetName FROM ADDRESS a WHERE a.PERSON_ID = 1")
    assertThat(result.info.executionType).isEqualTo(ExecutionType.Static)
  }

  @Test
  def appliesNamingStrategyWhenTranslatingAtRunTime(): Unit = {
    val ctx: SqlMirrorContext[PostgresDialect, CompositeNamingStrategy2[SnakeCase, UpperCase]] =
      new SqlMirrorContext(PostgresDialect, NamingStrategy(SnakeCase, UpperCase))
    import ctx.*

    val streetPrefix: Option[String] = Some("Main")
    val result = ctx.run(
      dynamicQuery[Address]
        .filterOpt(streetPrefix)((a, prefix) => quote(a.streetName.startsWith(prefix)))
        .map(a => quote(a.personId))
    )

    assertThat(result.string).isEqualTo("SELECT v0.PERSON_ID AS personId FROM ADDRESS v0 WHERE v0.STREET_NAME LIKE (? || '%')")
    assertThat(result.info.executionType).isEqualTo(ExecutionType.Dynamic)
    assertThat(result.prepareRow.data.map(_._2)).isEqualTo(List("Main"))
  }
}
