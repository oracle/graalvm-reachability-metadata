/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_pekko.pekko_protobuf_v3_3

import org.apache.pekko.protobufv3.internal.GeneratedMessage
import org.apache.pekko.protobufv3.internal.Message
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Test

class GeneratedMessageAnonymous4Test {
  @Test
  def fileScopedGeneratedExtensionLoadsDescriptorFromOuterClass(): Unit = {
    val extension: GeneratedMessage.GeneratedExtension[Message, GeneratedMessageAnonymous4Descriptor] =
      GeneratedMessage.newFileScopedGeneratedExtension(
        classOf[GeneratedMessageAnonymous4Descriptor],
        null,
        classOf[GeneratedMessageAnonymous4Descriptor].getName,
        "host_extension"
      )

    val descriptor = extension.getDescriptor()

    assertSame(GeneratedMessageAnonymous4Descriptor.descriptor, descriptor.getFile)
    assertEquals("host_extension", descriptor.getName)
    assertEquals(100, descriptor.getNumber)
  }
}
