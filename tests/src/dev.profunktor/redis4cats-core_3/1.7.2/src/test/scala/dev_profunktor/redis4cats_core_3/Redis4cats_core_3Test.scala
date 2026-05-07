/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package dev_profunktor.redis4cats_core_3

import dev.profunktor.redis4cats.codecs.splits.{SplitEpi, SplitMono}
import org.junit.jupiter.api.Assertions.{assertEquals, assertNotSame}
import org.junit.jupiter.api.Test

class Redis4cats_core_3Test {
  @Test
  def test(): Unit = {
    println("This is just a placeholder, implement your test")
  }

  @Test
  def splitEpiAndSplitMonoComposeAndReversePureTransformations(): Unit = {
    val parseNumber: SplitEpi[String, Int] = SplitEpi[String, Int](_.toInt, _.toString)
    val numberToCounter: SplitEpi[Int, CounterValue] = SplitEpi[Int, CounterValue](value => CounterValue(value.toLong), _.value.toInt)
    val parseCounter: SplitEpi[String, CounterValue] = parseNumber.andThen(numberToCounter)
    val counterToString: SplitMono[CounterValue, String] = parseCounter.reverse

    assertEquals(CounterValue(7L), parseCounter("7"))
    assertEquals("7", parseCounter.reverseGet(CounterValue(7L)))
    assertEquals("7", counterToString(CounterValue(7L)))
    assertEquals(CounterValue(7L), counterToString.reverseGet("7"))
    assertNotSame(parseCounter, parseCounter.copy())
  }

  @Test
  def splitMonoCompositionPreservesBidirectionalConversions(): Unit = {
    val trimInput: SplitMono[String, String] = SplitMono[String, String](_.trim, identity)
    val lengthValue: SplitMono[String, StringLength] = SplitMono[String, StringLength](value => StringLength(value.length), length => "x" * length.value)
    val trimmedLength: SplitMono[String, StringLength] = trimInput.andThen(lengthValue)
    val lengthToString: SplitEpi[StringLength, String] = trimmedLength.reverse

    assertEquals(StringLength(5), trimmedLength("  redis  "))
    assertEquals("xxx", trimmedLength.reverseGet(StringLength(3)))
    assertEquals("xxxx", lengthToString(StringLength(4)))
    assertEquals(StringLength(4), lengthToString.reverseGet("data"))
    assertEquals("SplitMono", trimmedLength.productPrefix)
  }
}

final case class CounterValue(value: Long)

final case class StringLength(value: Int)
