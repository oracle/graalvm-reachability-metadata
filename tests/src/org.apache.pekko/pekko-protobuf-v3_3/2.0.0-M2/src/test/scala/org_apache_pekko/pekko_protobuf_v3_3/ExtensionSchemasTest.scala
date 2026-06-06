/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_pekko.pekko_protobuf_v3_3

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class ExtensionSchemasTest {
  @Test
  def initializesFullRuntimeExtensionSchemaForProto2GeneratedMessages(): Unit = {
    val message: ExtensionSchemasProto2Probe = new ExtensionSchemasProto2Probe()

    message.initializeEmptyPayloadSchema()
    val liteMessage: ExtensionSchemasProto2Probe.ExtendableLiteProbe =
      ExtensionSchemasProto2Probe.parseExtendableLiteMessageWithUnknownField()

    assertThat(message.getDescriptorForType.getFile.toProto.hasSyntax).isFalse
    assertThat(message.getDescriptorForType.getName).isEqualTo("ExtensionSchemasProto2Probe")
    assertThat(liteMessage).isNotNull
    assertThat(liteMessage.getDefaultInstanceForType)
      .isSameAs(ExtensionSchemasProto2Probe.ExtendableLiteProbe.getDefaultInstance)
  }
}
