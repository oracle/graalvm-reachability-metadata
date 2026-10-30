/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_typelevel.literally_3

import org.typelevel.literally.Literally
import scala.compiletime.testing.Error
import scala.compiletime.testing.typeCheckErrors
import scala.quoted.Expr
import scala.quoted.Quotes

object LiteralDiagnostics {
  inline def errors(inline code: String): List[Error] = ${ errorsImpl('code) }

  private def errorsImpl(code: Expr[String])(using Quotes): Expr[List[Error]] =
    '{ typeCheckErrors($code) }
}

object CountLiteral extends Literally[Int] {
  override def validate(value: String)(using Quotes): Either[String, Expr[Int]] = {
    value.toIntOption.filter(_ >= 0) match {
      case Some(count) => Right(Expr(count))
      case None => Left("count must be a non-negative integer")
    }
  }

  extension (inline context: StringContext) {
    inline def count(inline args: Any*): Int = ${ CountLiteral.apply('context, 'args) }
  }
}
