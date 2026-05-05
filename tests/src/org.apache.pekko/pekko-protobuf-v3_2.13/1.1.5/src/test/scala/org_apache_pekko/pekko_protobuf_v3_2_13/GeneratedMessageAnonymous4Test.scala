/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_pekko.pekko_protobuf_v3_2_13

import org.apache.pekko.protobufv3.internal.ByteString
import org.apache.pekko.protobufv3.internal.Descriptors
import org.apache.pekko.protobufv3.internal.GeneratedMessage
import org.apache.pekko.protobufv3.internal.Message
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class GeneratedMessageAnonymous4Test {
  @Test
  def fileScopedGeneratedExtensionLoadsDescriptorFromGeneratedOuterClass(): Unit = {
    val extension: GeneratedMessage.GeneratedExtension[Message, ByteString] =
      GeneratedMessage.newFileScopedGeneratedExtension[Message, ByteString](
        classOf[ByteString],
        null,
        classOf[GeneratedMessageAnonymous4Probe].getName,
        "probe_extension")

    val descriptor: Descriptors.FieldDescriptor = extension.getDescriptor

    assertThat(descriptor.getName).isEqualTo("probe_extension")
    assertThat(descriptor.getContainingType.getFullName)
      .isEqualTo("coverage.GeneratedMessageAnonymous4ProbeMessage")
    assertThat(descriptor.getFile).isSameAs(GeneratedMessageAnonymous4Probe.descriptor)
  }
}
