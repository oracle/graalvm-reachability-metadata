/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_pekko.pekko_protobuf_v3_2_13

import org.apache.pekko.protobufv3.internal.ExtensionRegistry
import org.apache.pekko.protobufv3.internal.JavaFeaturesProto
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class GeneratedFileTest {
  @Test
  def addOptionalExtensionLoadsPublicGeneratedExtensionField(): Unit = {
    val registry: ExtensionRegistry = ExtensionRegistry.newInstance()

    GeneratedFileProbe.addExtensionByName(
      registry,
      classOf[JavaFeaturesProto].getName,
      "java_"
    )

    val extensionInfo: ExtensionRegistry.ExtensionInfo = registry.findExtensionByName(
      JavaFeaturesProto.java_.getDescriptor.getFullName
    )

    assertThat(extensionInfo).isNotNull
    assertThat(extensionInfo.descriptor).isSameAs(JavaFeaturesProto.java_.getDescriptor)
  }
}
