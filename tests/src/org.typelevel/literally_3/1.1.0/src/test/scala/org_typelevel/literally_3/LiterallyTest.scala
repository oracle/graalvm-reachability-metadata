/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_typelevel.literally_3

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import scala.compiletime.testing.Error

import CountLiteral.*

class LiterallyTest {
  @Test
  def expandsValidatedLiteralsToUsableValues(): Unit = {
    val countValue: Int = count"42"
    assertEquals(42, countValue)
    assertEquals(0, count"0")
    assertEquals(Int.MaxValue, count"2147483647")
    assertEquals(List("item", "item", "item"), List.fill(count"3")("item"))
  }

  @Test
  def reportsValidationErrorsForInvalidLiterals(): Unit = {
    val negativeErrors: List[Error] = LiteralDiagnostics.errors("""
      import org_typelevel.literally_3.CountLiteral.*
      count"-1"
    """)
    val textErrors: List[Error] = LiteralDiagnostics.errors("""
      import org_typelevel.literally_3.CountLiteral.*
      count"many"
    """)
    val emptyErrors: List[Error] = LiteralDiagnostics.errors("""
      import org_typelevel.literally_3.CountLiteral.*
      count""
    """)

    assertTrue(negativeErrors.exists(_.message.contains("count must be a non-negative integer")))
    assertTrue(textErrors.exists(_.message.contains("count must be a non-negative integer")))
    assertTrue(emptyErrors.exists(_.message.contains("count must be a non-negative integer")))
  }

  @Test
  def rejectsInterpolationArguments(): Unit = {
    val errors: List[Error] = LiteralDiagnostics.errors("""
      import org_typelevel.literally_3.CountLiteral.*
      val amount: Int = 3
      count"$amount"
    """)

    assertTrue(errors.exists(_.message.contains("interpolation not supported")))
  }
}
