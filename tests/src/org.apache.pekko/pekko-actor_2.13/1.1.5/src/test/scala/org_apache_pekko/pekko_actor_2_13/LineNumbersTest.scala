/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_pekko.pekko_actor_2_13

import org.apache.pekko.util.LineNumbers
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class LineNumbersTest {
  @Test
  def readsClassResourceForOrdinaryObject(): Unit = {
    val target: LineNumbersTarget = new LineNumbersTarget("ordinary-object")

    val result: LineNumbers.Result = LineNumbers(target)
    val prettyName: String = LineNumbers.prettyName(target)

    assertThat(result).isNotEqualTo(LineNumbers.NoSourceInfo)
    assertThat(prettyName).contains("LineNumbers")
  }

  @Test
  def readsImplementationClassResourceForSerializableLambda(): Unit = {
    val target: SerializableThunk = () => "serializable-lambda"

    assertThat(target.value()).isEqualTo("serializable-lambda")
    val result: LineNumbers.Result = LineNumbers(target)

    assertThat(result).isNotEqualTo(LineNumbers.NoSourceInfo)
  }

  private final class LineNumbersTarget(val name: String)

  private trait SerializableThunk extends Serializable {
    def value(): String
  }
}
