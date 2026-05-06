/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_pekko.pekko_protobuf_v3_3

import org.apache.pekko.protobufv3.internal.Descriptors
import org.apache.pekko.protobufv3.internal.GeneratedMessage
import org.apache.pekko.protobufv3.internal.Message
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class GeneratedMessageAnonymous4Test {
  @Test
  def fileScopedExtensionLoadsDescriptorFromGeneratedDescriptorClass(): Unit = {
    val extension: GeneratedMessage.GeneratedExtension[Message, String] =
      GeneratedMessage.newFileScopedGeneratedExtension[Message, String](
        classOf[GeneratedMessageAnonymous4DescriptorProbe],
        null,
        classOf[GeneratedMessageAnonymous4DescriptorProbe].getName,
        "covered_extension")

    val descriptor: Descriptors.FieldDescriptor = extension.getDescriptor

    assertThat(descriptor.getName).isEqualTo("covered_extension")
    assertThat(descriptor.getNumber).isEqualTo(100)
    assertThat(descriptor.getJavaType).isEqualTo(Descriptors.FieldDescriptor.JavaType.STRING)
    assertThat(descriptor.getFile).isSameAs(GeneratedMessageAnonymous4DescriptorProbe.descriptor)
  }
}
