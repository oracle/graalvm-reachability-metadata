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
  def readsSourceInformationFromAnOrdinaryObjectClassResource(): Unit = {
    val target: PlainLineNumberTarget = new PlainLineNumberTarget("ordinary-object")

    val result: LineNumbers.Result = LineNumbers(target)

    assertThat(result).isNotEqualTo(LineNumbers.NoSourceInfo)
    assertThat(result).isNotInstanceOf(classOf[LineNumbers.UnknownSourceFormat])
    assertThat(result.toString).contains("LineNumbersTest.scala")
  }

  @Test
  def readsSourceInformationFromASerializableLambdaImplementationClassResource(): Unit = {
    val target: SerializableJavaLambdaFactory.SerializableCallable =
      SerializableJavaLambdaFactory.create("serializable-lambda")

    assertThat(target.call()).isEqualTo("serializable-lambda")
    val result: LineNumbers.Result = LineNumbers(target.asInstanceOf[AnyRef])

    assertThat(result).isNotEqualTo(LineNumbers.NoSourceInfo)
    assertThat(result).isNotInstanceOf(classOf[LineNumbers.UnknownSourceFormat])
    assertThat(result.toString).contains("SerializableJavaLambdaFactory.java")
  }

  private final class PlainLineNumberTarget(val name: String)
}
