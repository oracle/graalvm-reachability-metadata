/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_pekko.pekko_protobuf_v3_3 {
  import java.nio.charset.StandardCharsets

  import org.apache.pekko.protobufv3.internal.UnsafeByteOperations
  import org.assertj.core.api.Assertions.assertThat
  import org.junit.jupiter.api.Test

  class UnsafeUtilTest {
    @Test
    def validatesUtf8ThroughUnsafeAwareByteString(): Unit = {
      val text: String = "Pekko protobuf shaded runtime exercises UnsafeUtil"
      val bytes: Array[Byte] = text.getBytes(StandardCharsets.UTF_8)
      val byteString = UnsafeByteOperations.unsafeWrap(bytes)

      assertThat(byteString.isValidUtf8).isTrue()
      assertThat(byteString.toStringUtf8).isEqualTo(text)
    }
  }
}
