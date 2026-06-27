/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_scala_lang.scala3_library_3

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

import scala.runtime.LazyVals
import scala.runtime.TupleXXL

final class ScalaRuntimeLazyValsTest {
  @Test
  def getOffsetFindsScalaRuntimeField(): Unit = {
    val offset: Long = LazyVals.getOffset(classOf[TupleXXL], "es")

    assertThat(offset).isNotNegative
  }

  @Test
  def stateExtractsLazyValBits(): Unit = {
    val initializedState: Long = 1L << LazyVals.BITS_PER_LAZY_VAL

    assertThat(LazyVals.STATE(initializedState, 1)).isEqualTo(1L)
  }
}
