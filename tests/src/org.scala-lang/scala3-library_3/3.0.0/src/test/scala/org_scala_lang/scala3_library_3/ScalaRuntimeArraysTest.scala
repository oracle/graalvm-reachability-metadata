/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_scala_lang.scala3_library_3

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

import scala.runtime.Arrays

class ScalaRuntimeArraysTest {
  @Test
  def createsArraysFromSequenceAndDimensions(): Unit = {
    val copied: Array[String] = Arrays.seqToArray(Seq("alpha", "beta"), classOf[String])
    assertEquals(2, copied.length)
    assertEquals("alpha", copied(0))
    assertEquals("beta", copied(1))

    val matrix: Array[Array[String]] = Arrays.newArray(
      classOf[String],
      classOf[Array[Array[String]]],
      Array(2, 3)
    )
    assertEquals(2, matrix.length)
    assertEquals(3, matrix(0).length)
  }
}
