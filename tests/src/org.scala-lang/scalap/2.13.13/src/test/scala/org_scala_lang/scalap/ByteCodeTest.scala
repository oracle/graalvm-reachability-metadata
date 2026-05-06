/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_scala_lang.scalap

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import scala.tools.scalap.scalax.rules.scalasig.ByteCode

class ByteCodeTest {
  @Test
  def forClassLoadsClassFileResource(): Unit = {
    val byteCode: ByteCode = ByteCode.forClass(classOf[ByteCode])

    assertThat(byteCode.length).isGreaterThan(4)
    assertThat(byteCode.take(4).toLong).isEqualTo(0xcafebabeL)
    assertThat(byteCode.toString).isEqualTo(s"${byteCode.length} bytes")
  }
}
