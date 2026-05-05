/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_scala_lang.scala3_library_3

import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

import scala.deriving.Mirror
import scala.runtime.LazyVals

class ScalaRuntimeLazyValsTest {
  @Test
  def obtainsOffsetForDeclaredField(): Unit = {
    val offset: Long = LazyVals.getOffset(classOf[Mirror.SingletonProxy], "value")

    assertTrue(offset >= 0L)
  }
}
