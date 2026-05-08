/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_pekko.pekko_actor_3

import java.io.Serializable
import java.util.concurrent.Callable

import org.apache.pekko.util.LineNumbers
import org.apache.pekko.util.LineNumbers.NoSourceInfo
import org.apache.pekko.util.LineNumbers.SourceFile
import org.apache.pekko.util.LineNumbers.SourceFileLines
import org.apache.pekko.util.LineNumbers.UnknownSourceFormat
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Assertions.fail
import org.junit.jupiter.api.Test

class LineNumbersTest {
  @Test
  def readsSourceInformationFromAnOrdinaryClassResource(): Unit = {
    val result: LineNumbers.Result = LineNumbers(new LineNumbersPlainTarget("ordinary-class"))

    assertSourceInfo(result)
  }

  @Test
  def readsSourceInformationFromASerializableLambdaImplementationResource(): Unit = {
    val target: LineNumbersSerializableCallable = () => "serializable-lambda"

    assertEquals("serializable-lambda", target.call())
    val result: LineNumbers.Result = LineNumbers(target)

    assertSourceInfo(result)
  }

  private def assertSourceInfo(result: LineNumbers.Result): Unit = {
    result match {
      case SourceFile(filename) =>
        assertEquals("LineNumbersTest.scala", filename)
      case SourceFileLines(filename, from, to) =>
        assertEquals("LineNumbersTest.scala", filename)
        assertTrue(from > 0, s"expected a positive starting line number, got $from")
        assertTrue(to >= from, s"expected ending line $to to be greater than or equal to $from")
      case NoSourceInfo =>
        fail("expected line number source information")
      case UnknownSourceFormat(explanation) =>
        fail(s"expected readable line number source information, got: $explanation")
    }
  }
}

final class LineNumbersPlainTarget(val name: String)

@FunctionalInterface
trait LineNumbersSerializableCallable extends Callable[String] with Serializable
