/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_json4s.json4s_scalap_3

import org.assertj.core.api.Assertions.assertThat
import org.json4s.scalap.scalasig.ByteCode
import org.junit.jupiter.api.Test

final class ByteCodeTest {
  @Test
  def forClassLoadsTheLibraryClassFileResource(): Unit = {
    val byteCode: ByteCode = ByteCode.forClass(classOf[ByteCode])

    assertThat(byteCode.length).isGreaterThan(4)
    assertThat(byteCode.take(4).toLong).isEqualTo(0xcafebabeL)
    assertThat(byteCode.toString).isEqualTo(s"${byteCode.length}bytes")
  }
}
