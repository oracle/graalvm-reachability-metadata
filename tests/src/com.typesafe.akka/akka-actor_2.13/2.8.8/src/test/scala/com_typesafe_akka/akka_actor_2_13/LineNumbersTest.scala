/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_typesafe_akka.akka_actor_2_13

import java.io.Serializable
import java.util.function.Supplier

import akka.util.LineNumbers
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class LineNumbersTest {
  @Test
  def readsSourceInformationFromAClassResource(): Unit = {
    val target: LineNumbersTarget = new LineNumbersTarget

    assertThat(target.marker).isEqualTo("line-number-target")
    val result: LineNumbers.Result = LineNumbers.`for`(target)

    assertThat(result).isInstanceOf(classOf[LineNumbers.SourceFileLines])
    assertThat(result.toString).contains("LineNumbersTest.scala")
  }

  @Test
  def readsSourceInformationFromASerializableLambda(): Unit = {
    val supplier: SerializableStringSupplier = () => "line-number-lambda"

    assertThat(supplier.get()).isEqualTo("line-number-lambda")
    val result: LineNumbers.Result = LineNumbers.`for`(supplier)

    assertThat(result).isInstanceOf(classOf[LineNumbers.SourceFileLines])
    assertThat(result.toString).contains("LineNumbersTest.scala")
  }
}

class LineNumbersTarget {
  def marker: String = "line-number-target"
}

trait SerializableStringSupplier extends Supplier[String] with Serializable
