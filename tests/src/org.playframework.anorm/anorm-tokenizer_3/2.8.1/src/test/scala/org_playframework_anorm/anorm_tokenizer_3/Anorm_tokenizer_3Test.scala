/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_playframework_anorm.anorm_tokenizer_3

import anorm.Show
import anorm.StringShow
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

import java.util.Locale

class Anorm_tokenizer_3Test {
  @Test
  def stringShowReturnsTheExactUnderlyingValue(): Unit = {
    val renderedSqlFragment: String = "select 'quoted value'\nwhere name = café and active = true"

    assertThat(new StringShow(renderedSqlFragment).show).isEqualTo(renderedSqlFragment)
    assertThat(new StringShow("").show).isEmpty()
  }

  @Test
  def stringShowCanRepresentNullWithoutSubstitution(): Unit = {
    val show: StringShow = new StringShow(null)

    assertThat(show.show).isNull()
  }

  @Test
  def mkStringUsesTheProvidedMaker(): Unit = {
    val maker: Show.Maker[SqlLiteral] = new Show.Maker[SqlLiteral] {
      override def apply(subject: SqlLiteral): Show = new StringShow(s"'${subject.value.replace("'", "''")}'")
    }

    assertThat(Show.mkString(SqlLiteral("O'Reilly"))(maker)).isEqualTo("'O''Reilly'")
  }

  @Test
  def mkStringUsesImplicitMakerFromCallSite(): Unit = {
    implicit val maker: Show.Maker[ColumnReference] = new Show.Maker[ColumnReference] {
      override def apply(subject: ColumnReference): Show = new StringShow(s"${subject.table}.${subject.column}")
    }

    assertThat(Show.mkString(ColumnReference("users", "created_at"))).isEqualTo("users.created_at")
  }

  @Test
  def identityMakerReturnsTheOriginalShowInstance(): Unit = {
    val original: Show = new StringShow("already rendered")
    val rendered: Show = Show.Maker.Identity(original)

    assertThat(rendered).isSameAs(original)
    assertThat(Show.mkString(original)(Show.Maker.Identity)).isEqualTo("already rendered")
  }

  @Test
  def makerCanBeUsedAsAFunction(): Unit = {
    val maker: Show.Maker[Int] = new Show.Maker[Int] {
      override def apply(subject: Int): Show = new StringShow(s"n = $subject")
    }

    val asFunction: Int => Show = maker
    val upperCaseRendering: Int => String = maker.andThen(_.show.toUpperCase(Locale.ROOT))

    assertThat(asFunction(42).show).isEqualTo("n = 42")
    assertThat(upperCaseRendering(42)).isEqualTo("N = 42")
  }

  @Test
  def makerCanReturnCustomShowImplementation(): Unit = {
    val maker: Show.Maker[DelimitedTokens] = new Show.Maker[DelimitedTokens] {
      override def apply(subject: DelimitedTokens): Show = new DelimitedTokensShow(subject)
    }

    assertThat(Show.mkString(DelimitedTokens(List("select", "*", "from", "users")))(maker))
      .isEqualTo("select * from users")
  }

  private final case class SqlLiteral(value: String)

  private final case class ColumnReference(table: String, column: String)

  private final case class DelimitedTokens(values: List[String])

  private final class DelimitedTokensShow(tokens: DelimitedTokens) extends Show {
    override def show: String = tokens.values.mkString(" ")
  }
}
