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

import java.security.PrivilegedAction

class LineNumbersTest {
  @Test
  def readsSourceInformationFromAnOrdinaryClassResource(): Unit = {
    val target: PlainLineNumberTarget = new PlainLineNumberTarget("ordinary-class")

    val result: LineNumbers.Result = LineNumbers(target)

    assertThat(target.name).isEqualTo("ordinary-class")
    assertThat(result).isNotEqualTo(LineNumbers.NoSourceInfo)
    assertThat(result.toString).contains("LineNumbersTest.scala")
  }

  @Test
  def readsSourceInformationFromASerializableLambdaImplementationClassResource(): Unit = {
    val target: PrivilegedAction[String] = LineNumberSerializableLambdas.action("serializable-lambda")

    val result: LineNumbers.Result = LineNumbers(target)

    assertThat(target.run()).isEqualTo("serializable-lambda")
    assertThat(result).isNotEqualTo(LineNumbers.NoSourceInfo)
    assertThat(result.toString).contains("LineNumberSerializableLambdas.java")
  }

  private final class PlainLineNumberTarget(val name: String)
}
