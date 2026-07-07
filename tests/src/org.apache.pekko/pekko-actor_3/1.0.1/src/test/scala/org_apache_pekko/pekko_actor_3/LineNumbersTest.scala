/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_pekko.pekko_actor_3

import org.apache.pekko.util.LineNumbers
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Test

class LineNumbersTest {
  @Test
  def readsOrdinaryObjectClassResource(): Unit = {
    val target: PlainLineNumbersTarget = new PlainLineNumbersTarget("ordinary-object")

    val result: LineNumbers.Result = LineNumbers(target)

    assertNotNull(result)
  }

  @Test
  def readsSerializableLambdaImplementationClassResource(): Unit = {
    val target: SerializableThunk = () => "serializable-lambda"

    assertEquals("serializable-lambda", target.value())
    val result: LineNumbers.Result = LineNumbers(target)

    assertNotNull(result)
  }

  private final class PlainLineNumbersTarget(val name: String)

  private trait SerializableThunk extends Serializable {
    def value(): String
  }
}
