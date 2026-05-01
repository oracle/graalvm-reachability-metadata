/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_scala_lang.scala_library

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

import scala.collection.JavaConverters._
import scala.collection.immutable.Vector

final class VectorStaticsTest {
  @Test
  def prependsSingleElementToTrimmedVectorWithPrefixSpace(): Unit = {
    val base: Vector[VectorElement] = Vector.tabulate(64)(index => VectorElement(index, s"value-$index"))
    val trimmed: Vector[VectorElement] = base.drop(8)
    val prepended: VectorElement = VectorElement(-1, "prepended")

    val result: Vector[VectorElement] = trimmed.prependedAll(List(prepended))

    assertThat(result.length).isEqualTo(trimmed.length + 1)
    assertThat(result.head).isSameAs(prepended)
    assertThat(result.tail.toList.asJava).containsExactlyElementsOf(trimmed.toList.asJava)
  }

  @Test
  def mapsDeepVectorAfterUnchangedNestedPrefixes(): Unit = {
    val original: Vector[VectorElement] = Vector.tabulate(4096)(index => VectorElement(index, s"value-$index"))
    val changedIndex: Int = 1500

    val result: Vector[VectorElement] = original.map { element: VectorElement =>
      if (element.index == changedIndex) {
        VectorElement(element.index, "changed")
      } else {
        element
      }
    }

    assertThat(result.length).isEqualTo(original.length)
    assertThat(result(changedIndex)).isEqualTo(VectorElement(changedIndex, "changed"))
    assertThat(result(changedIndex - 1)).isSameAs(original(changedIndex - 1))
    assertThat(result(changedIndex + 1)).isSameAs(original(changedIndex + 1))
    assertThat(result.take(3).toList.asJava).containsExactlyElementsOf(original.take(3).toList.asJava)
  }

  private final case class VectorElement(index: Int, label: String)
}
