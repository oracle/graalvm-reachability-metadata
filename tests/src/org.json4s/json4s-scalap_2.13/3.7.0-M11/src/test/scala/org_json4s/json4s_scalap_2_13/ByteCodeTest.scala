/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_json4s.json4s_scalap_2_13

import org.json4s.scalap.scalasig.ByteCode
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class ByteCodeTest {
  @Test
  def loadsClassFileResourceForLibraryClass(): Unit = {
    val byteCode: ByteCode = ByteCode.forClass(classOf[ByteCode])

    assertTrue(byteCode.length > 4)
    assertEquals(0xCAFEBABEL.toInt, byteCode.take(4).toInt)
  }
}
