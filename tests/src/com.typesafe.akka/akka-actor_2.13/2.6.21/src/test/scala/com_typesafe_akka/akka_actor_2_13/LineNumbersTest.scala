/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_typesafe_akka.akka_actor_2_13

import akka.util.LineNumbers
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class LineNumbersTest {
  @Test
  def readsSourceInformationFromAnOrdinaryClassResource(): Unit = {
    val target: PlainLineNumberTarget = new PlainLineNumberTarget("ordinary-class")

    val result: LineNumbers.Result = LineNumbers(target)

    assertThat(target.name).isEqualTo("ordinary-class")
    assertThat(result).isNotEqualTo(LineNumbers.NoSourceInfo)
    assertThat(result.toString).contains("LineNumbersTest.scala")
  }

  private final class PlainLineNumberTarget(val name: String)
}
