/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package io_getquill.quill_sql_2_13

import io.getquill.Literal
import io.getquill.MirrorSqlDialect
import io.getquill.Quoted
import io.getquill.SqlMirrorContext
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class SqlDslInnerLikeAnonymous1Test {
  @Test
  def likeCreatesQuotedConditionWithSqlOperator(): Unit = {
    val context: SqlMirrorContext[MirrorSqlDialect.type, Literal.type] =
      new SqlMirrorContext(MirrorSqlDialect, Literal)
    import context._

    val condition: Quoted[Boolean] = "person.name".like("A%")

    assertThat(condition.ast.toString)
      .contains("like")
  }
}
