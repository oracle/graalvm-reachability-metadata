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
  def prependsSingleElementToSmallVectorWithPrefixSpace(): Unit = {
    val original: Vector[VectorElement] = Vector(
      VectorElement(1, "one"),
      VectorElement(2, "two"),
      VectorElement(3, "three")
    )
    val prepended: VectorElement = VectorElement(0, "zero")

    val result: Vector[VectorElement] = original.prependedAll(Vector(prepended))

    assertThat(result.length).isEqualTo(original.length + 1)
    assertThat(result.head).isSameAs(prepended)
    assertThat(result.tail.toList.asJava).containsExactlyElementsOf(original.toList.asJava)
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
