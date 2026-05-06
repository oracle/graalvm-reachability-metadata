/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_pekko.pekko_actor_2_13

import org.apache.pekko.util.LineNumbers
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Assertions.fail
import org.junit.jupiter.api.Test

class LineNumbersTest {
  @Test
  def reportsSourceForRegularClassResource(): Unit = {
    val result: LineNumbers.Result = LineNumbers(new LineNumbersTest.RegularClass)

    LineNumbersTest.assertSourceFromThisFile(result)
  }

  @Test
  def reportsSourceForSerializableLambda(): Unit = {
    val task: LineNumbersTest.SerializableTask = () => "completed"

    assertThat(task.run()).isEqualTo("completed")
    LineNumbersTest.assertSourceFromThisFile(LineNumbers(task))
  }
}

object LineNumbersTest {
  final class RegularClass

  trait SerializableTask extends Serializable {
    def run(): String
  }

  private def assertSourceFromThisFile(result: LineNumbers.Result): Unit = {
    result match {
      case lines: LineNumbers.SourceFileLines =>
        assertThat(lines.filename).isEqualTo("LineNumbersTest.scala")
        assertThat(lines.from).isPositive()
        assertThat(lines.to).isGreaterThanOrEqualTo(lines.from)
      case file: LineNumbers.SourceFile =>
        assertThat(file.filename).isEqualTo("LineNumbersTest.scala")
      case other =>
        fail(s"Expected source information from LineNumbersTest.scala but got $other")
    }
  }
}
