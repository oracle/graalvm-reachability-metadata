/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_typelevel.literally_3

import java.util.Locale
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.typelevel.literally.Literally
import scala.quoted.{Expr, Quotes}

class Literally_3Test {
  @Test
  def literalInstancesAreOrdinaryRuntimeValues(): Unit = {
    import Literally_3TestSyntax.*

    assertThat(PositiveInt).isNotNull
    assertThat(PositiveInt.Expr).isNotNull
    assertThat(NormalizedToken).isNotNull
    assertThat(NormalizedToken.Expr).isNotNull
  }
}

object Literally_3TestSyntax {
  object PositiveInt extends Literally[Int] {
    override def validate(s: String)(using Quotes): Either[String, Expr[Int]] =
      s.toIntOption match {
        case Some(value) if value > 0 => Right(Expr(value))
        case _ => Left(s"Expected a positive base-10 integer literal, got: $s")
      }
  }

  object NormalizedToken extends Literally[String] {
    override def validate(s: String)(using Quotes): Either[String, Expr[String]] =
      if s.matches("[A-Za-z][A-Za-z0-9_-]*") then Right(Expr(s.toLowerCase(Locale.ROOT)))
      else Left(s"Expected an ASCII token literal, got: $s")
  }
}
