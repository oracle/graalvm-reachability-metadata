/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_typelevel.literally_3

import org.typelevel.literally.Literally

import scala.quoted.Expr
import scala.quoted.Quotes

object LiterallyTestFixtures {
  final case class Port(value: Int)

  final case class Label(value: String)

  final case class RgbColor(red: Int, green: Int, blue: Int)

  extension (inline context: StringContext)
    inline def port(inline args: Any*): Port = ${ PortLiteral('context, 'args) }

    inline def label(inline args: Any*): Label = ${ LabelLiteral('context, 'args) }

    inline def rgb(inline args: Any*): RgbColor = ${ RgbColorLiteral('context, 'args) }

  object PortLiteral extends Literally[Port] {
    override def validate(s: String)(using Quotes): Either[String, Expr[Port]] =
      s.toIntOption.filter(value => value >= 0 && value <= 65535) match
        case Some(value) => Right('{ Port(${ Expr(value) }) })
        case None => Left(s"invalid port literal '$s': expected an integer between 0 and 65535")
  }

  object LabelLiteral extends Literally[Label] {
    override def validate(s: String)(using Quotes): Either[String, Expr[Label]] =
      if s.nonEmpty && s.forall(isAllowedLabelCharacter) then Right('{ Label(${ Expr(s) }) })
      else Left(s"invalid label literal '$s': expected letters, digits, hyphen, underscore, newline, or tab")
  }

  object RgbColorLiteral extends Literally[RgbColor] {
    override def validate(s: String)(using Quotes): Either[String, Expr[RgbColor]] =
      parseRgb(s) match
        case Some((red, green, blue)) => Right('{ RgbColor(${ Expr(red) }, ${ Expr(green) }, ${ Expr(blue) }) })
        case None => Left(s"invalid RGB literal '$s': expected #RRGGBB")
  }

  private def isAllowedLabelCharacter(character: Char): Boolean =
    character.isLetterOrDigit || character == '-' || character == '_' || character == '\n' || character == '\t'

  private def parseRgb(s: String): Option[(Int, Int, Int)] =
    Option.when(s.length == 7 && s.head == '#' && s.tail.forall(isHexDigit)) {
      val red = Integer.parseInt(s.substring(1, 3), 16)
      val green = Integer.parseInt(s.substring(3, 5), 16)
      val blue = Integer.parseInt(s.substring(5, 7), 16)
      (red, green, blue)
    }

  private def isHexDigit(character: Char): Boolean =
    character.isDigit || (character >= 'a' && character <= 'f') || (character >= 'A' && character <= 'F')
}
