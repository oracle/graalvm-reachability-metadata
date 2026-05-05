/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_pekko.pekko_protobuf_v3_2_13

import org.apache.pekko.protobufv3.internal.ByteString
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class ByteBufferWriterTest {
  @Test
  def initializesByteBufferWriterRuntimeSupport(): Unit = {
    val className: String = "org.apache.pekko.protobufv3.internal.ByteBufferWriter"
    val writerClass: Class[_] = Class.forName(className, true, classOf[ByteString].getClassLoader)

    assertThat(writerClass.getName).isEqualTo(className)
  }
}
