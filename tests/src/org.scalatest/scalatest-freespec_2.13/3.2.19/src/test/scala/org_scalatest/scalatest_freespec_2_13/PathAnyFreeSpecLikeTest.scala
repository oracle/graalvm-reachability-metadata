/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_scalatest.scalatest_freespec_2_13

import org.junit.jupiter.api.Assertions.assertInstanceOf
import org.junit.jupiter.api.Assertions.assertNotSame
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.scalatest.freespec.PathAnyFreeSpec
import org.scalatest.freespec.PathAnyFreeSpecLike

class PathAnyFreeSpecLikeTest {
  @Test
  def newInstanceCreatesAnotherPathFreeSpec(): Unit = {
    val spec: PathAnyFreeSpecLike = new PathAnyFreeSpec

    val newSpec: PathAnyFreeSpecLike = spec.newInstance

    assertInstanceOf(classOf[PathAnyFreeSpec], newSpec)
    assertNotSame(spec, newSpec)
    assertTrue(newSpec.testNames.isEmpty)
  }
}
