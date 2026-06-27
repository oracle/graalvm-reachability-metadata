/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package io_getquill.quill_sql_2_13

import io.getquill.Literal
import io.getquill.MirrorSqlDialect
import io.getquill.Query
import io.getquill.Quoted
import io.getquill.SqlMirrorContext
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

final case class LockableRow(id: Int, name: String)

class SqlDslInnerForUpdateAnonymous2Test {
  @Test
  def forUpdateCreatesQuotedQueryWithLockingClause(): Unit = {
    val context: SqlMirrorContext[MirrorSqlDialect.type, Literal.type] =
      new SqlMirrorContext(MirrorSqlDialect, Literal)
    import context._

    val sourceQuery: Query[LockableRow] = null.asInstanceOf[Query[LockableRow]]
    val lockedRows: Quoted[Query[LockableRow]] = sourceQuery.forUpdate()

    assertThat(lockedRows.ast.toString)
      .contains("FOR UPDATE")
  }
}
