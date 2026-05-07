/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_github_takayahilton.sql_formatter_2_13

import com.github.takayahilton.sqlformatter.SqlDialect
import com.github.takayahilton.sqlformatter.SqlFormatter
import com.github.takayahilton.sqlformatter.core.FormatConfig
import com.github.takayahilton.sqlformatter.core.Params
import com.github.takayahilton.sqlformatter.languages.AbstractFormatter
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class SqlFormatterTest {

  @Test
  def formatsComplexStandardSqlWithNestedQueryAndKeywordBoundaries(): Unit = {
    val result: String = SqlFormatter.format(
      "SELECT customer_id.from, COUNT(order_id) AS total " +
        "FROM (SELECT * FROM orders LIMIT 30) INNER JOIN customers " +
        "ON customers.customer_id = orders.customer_id " +
        "WHERE total >= 10 ORDER BY customer_id.from;"
    )

    assertThat(result).isEqualTo(
      """|SELECT
         |  customer_id.from,
         |  COUNT(order_id) AS total
         |FROM
         |  (
         |    SELECT
         |      *
         |    FROM
         |      orders
         |    LIMIT
         |      30
         |  )
         |  INNER JOIN customers ON customers.customer_id = orders.customer_id
         |WHERE
         |  total >= 10
         |ORDER BY
         |  customer_id.from;""".stripMargin
    )
  }

  @Test
  def preservesCommentsAndSeparatesStatements(): Unit = {
    val result: String = SqlFormatter.format(
      "SELECT\n" +
        "/*\n" +
        " * This is a block comment\n" +
        " */\n" +
        "* FROM\n" +
        "-- This is another comment\n" +
        "MyTable # One final comment\n" +
        "WHERE 1 = 2;SELECT count(*),Column1 FROM Table2;"
    )

    assertThat(result).isEqualTo(
      """|SELECT
         |  /*
         |   * This is a block comment
         |   */
         |  *
         |FROM
         |  -- This is another comment
         |  MyTable # One final comment
         |WHERE
         |  1 = 2;
         |SELECT
         |  count(*),
         |  Column1
         |FROM
         |  Table2;""".stripMargin
    )
  }

  @Test
  def appliesCustomIndentThroughFormatterAndConfigApis(): Unit = {
    val formatterIndent: String = SqlFormatter
      .of(SqlDialect.StandardSQL)
      .format("SELECT count(*),Column1 FROM Table1;", indent = "    ")

    val configIndent: String = SqlFormatter.format(
      "UPDATE Customers SET ContactName='Alfred Schmidt', City='Hamburg' WHERE CustomerName='Alfreds Futterkiste';",
      FormatConfig(indent = "\t", params = Params.EmptyParams)
    )

    assertThat(formatterIndent).isEqualTo(
      """|SELECT
         |    count(*),
         |    Column1
         |FROM
         |    Table1;""".stripMargin
    )
    assertThat(configIndent).isEqualTo(
      """|UPDATE
         |	Customers
         |SET
         |	ContactName = 'Alfred Schmidt',
         |	City = 'Hamburg'
         |WHERE
         |	CustomerName = 'Alfreds Futterkiste';""".stripMargin
    )
  }

  @Test
  def replacesNamedStringParametersIncludingQuotedPlaceholderKeys(): Unit = {
    val result: String = SqlFormatter.format(
      "SELECT :variable, :a1_2.3$, :'var name', :\"var name\", :`var name`, " +
        ":[var name], :'escaped \\'var\\'', :\"^*& weird \\\" var   \", :missing;",
      Map(
        "variable" -> "\"variable value\"",
        "a1_2.3$" -> "'weird value'",
        "var name" -> "'var value'",
        "escaped 'var'" -> "'weirder value'",
        "^*& weird \" var   " -> "'super weird value'"
      )
    )

    assertThat(result).isEqualTo(
      """|SELECT
         |  "variable value",
         |  'weird value',
         |  'var value',
         |  'var value',
         |  'var value',
         |  'var value',
         |  'weirder value',
         |  'super weird value',
         |  :missing;""".stripMargin
    )
  }

  @Test
  def replacesIndexedParametersWithTypedAndUnsafeParameterApis(): Unit = {
    val typedResult: String = SqlFormatter.format("SELECT ?, ?, ?;", List(10, 20, 30))
    val unsafeResult: String = SqlFormatter.standard.formatUnsafe("SELECT ?, ?, ?;", List("first", 2, true))

    assertThat(typedResult).isEqualTo(
      """|SELECT
         |  10,
         |  20,
         |  30;""".stripMargin
    )
    assertThat(unsafeResult).isEqualTo(
      """|SELECT
         |  first,
         |  2,
         |  true;""".stripMargin
    )
  }

  @Test
  def formatsCaseExpressionAndOracleIdentifiersWithPlSqlDialect(): Unit = {
    val formatter: AbstractFormatter = SqlFormatter.of(SqlDialect.PLSQL)

    val caseExpression: String = formatter.format(
      "SELECT foo, bar, CASE baz WHEN 'one' THEN 1 WHEN 'two' THEN 2 ELSE 3 END FROM table"
    )
    val identifiers: String = formatter.format("SELECT my_col$1#, col.2@ FROM tbl\n")

    assertThat(caseExpression).isEqualTo(
      """|SELECT
         |  foo,
         |  bar,
         |  CASE
         |    baz
         |    WHEN 'one' THEN 1
         |    WHEN 'two' THEN 2
         |    ELSE 3
         |  END
         |FROM
         |  table""".stripMargin
    )
    assertThat(identifiers).isEqualTo(
      """|SELECT
         |  my_col$1#,
         |  col.2@
         |FROM
         |  tbl""".stripMargin
    )
  }

  @Test
  def formatsDb2FetchFirstAndColonParameters(): Unit = {
    val formatter: AbstractFormatter = SqlFormatter.of(SqlDialect.DB2)

    val fetchFirst: String = formatter.format("SELECT col1 FROM tbl ORDER BY col2 DESC FETCH FIRST 20 ROWS ONLY;")
    val parameterized: String = formatter.format("SELECT :variable FROM tbl WHERE id = :missing", Map("variable" -> "\"value\""))

    assertThat(fetchFirst).isEqualTo(
      """|SELECT
         |  col1
         |FROM
         |  tbl
         |ORDER BY
         |  col2 DESC
         |FETCH FIRST
         |  20 ROWS ONLY;""".stripMargin
    )
    assertThat(parameterized).isEqualTo(
      """|SELECT
         |  "value"
         |FROM
         |  tbl
         |WHERE
         |  id = :missing""".stripMargin
    )
  }

  @Test
  def formatsCouchbaseN1qlObjectsArraysAndDollarParameters(): Unit = {
    val formatter: AbstractFormatter = SqlFormatter.of(SqlDialect.CouchbaseN1QL)

    val documentInsert: String = formatter.format(
      "INSERT INTO heroes (KEY, VALUE) VALUES ('123', {'id': 1, 'type': 'Tarzan', " +
        "'array': [123456789, 123456789, 123456789, 123456789, 123456789], 'hello': 'world'});"
    )
    val parameters: String = formatter.format(
      "SELECT $variable, $'var name', $\"var name\", $`var name`;",
      Map("variable" -> "\"variable value\"", "var name" -> "'var value'")
    )

    assertThat(documentInsert).isEqualTo(
      """|INSERT INTO
         |  heroes (KEY, VALUE)
         |VALUES
         |  (
         |    '123',
         |    {
         |      'id': 1,
         |      'type': 'Tarzan',
         |      'array': [
         |        123456789,
         |        123456789,
         |        123456789,
         |        123456789,
         |        123456789
         |      ],
         |      'hello': 'world'
         |    }
         |  );""".stripMargin
    )
    assertThat(parameters).isEqualTo(
      """|SELECT
         |  "variable value",
         |  'var value',
         |  'var value',
         |  'var value';""".stripMargin
    )
  }
}
