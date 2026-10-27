/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_playframework_anorm.anorm_3

import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

import scala.runtime.LazyVals

class ScalaRuntimeLazyValsTest {
  @Test
  def resolvesInstanceFieldOffset(): Unit = {
    val offset: Long = LazyVals.getOffset(classOf[LazyValsOffsetTarget], "bitmap")

    assertTrue(offset >= 0L, s"Expected a valid field offset but got $offset")
  }
}

class LazyValsOffsetTarget {
  var bitmap: Long = 0L
}
