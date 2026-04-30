/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_pekko.pekko_protobuf_v3_3

import org.apache.pekko.protobufv3.internal.DescriptorProtos
import org.apache.pekko.protobufv3.internal.Internal
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class InternalTest {
  @Test
  def resolvesGeneratedMessageDefaultInstance(): Unit = {
    val defaultInstance: DescriptorProtos.FileDescriptorProto = Internal.getDefaultInstance(
      classOf[DescriptorProtos.FileDescriptorProto]
    )

    assertThat(defaultInstance).isSameAs(DescriptorProtos.FileDescriptorProto.getDefaultInstance())
    assertThat(defaultInstance.getName).isEmpty()
  }
}
