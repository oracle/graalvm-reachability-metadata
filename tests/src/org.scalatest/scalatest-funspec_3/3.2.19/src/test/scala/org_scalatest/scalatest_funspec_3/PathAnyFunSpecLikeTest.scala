/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_scalatest.scalatest_funspec_3

import org.junit.jupiter.api.Assertions.assertInstanceOf
import org.junit.jupiter.api.Assertions.assertNotSame
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.scalatest.funspec.PathAnyFunSpec
import org.scalatest.funspec.PathAnyFunSpecLike

class PathAnyFunSpecLikeTest {
  @Test
  def newInstanceCreatesAnotherPathFunSpec(): Unit = {
    val spec: PathAnyFunSpecLike = new PathAnyFunSpec

    val newSpec: PathAnyFunSpecLike = spec.newInstance

    assertInstanceOf(classOf[PathAnyFunSpec], newSpec)
    assertNotSame(spec, newSpec)
    assertTrue(newSpec.testNames.isEmpty)
  }
}
