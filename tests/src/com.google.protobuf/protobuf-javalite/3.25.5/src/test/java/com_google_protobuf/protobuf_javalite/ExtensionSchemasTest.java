/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_google_protobuf.protobuf_javalite;

import com_google_protobuf.protobuf_javalite.fixture.ExtensionSchemasCoverageProto.Proto2Message;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class ExtensionSchemasTest {
    @Test
    void proto2LiteMessagesUseExtensionSchemaDuringSerialization() throws Exception {
        Proto2Message message = Proto2Message.newBuilder()
                .setLabel("extension schema coverage")
                .build();

        byte[] serialized = message.toByteArray();
        Proto2Message parsed = Proto2Message.parseFrom(serialized);

        assertThat(parsed.getLabel()).isEqualTo("extension schema coverage");
    }
}
