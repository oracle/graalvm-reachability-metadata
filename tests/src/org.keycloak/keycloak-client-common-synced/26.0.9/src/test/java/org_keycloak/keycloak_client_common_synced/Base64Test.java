/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_keycloak.keycloak_client_common_synced;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

import org.junit.jupiter.api.Test;
import org.keycloak.common.util.Base64;

import static org.assertj.core.api.Assertions.assertThat;

public class Base64Test {
    private static final byte[] SERIALIZED_STREAM_MAGIC = {(byte) 0xAC, (byte) 0xED, 0x00, 0x05};

    @Test
    void encodeObjectSerializesAndBase64EncodesSerializableValues() throws IOException {
        String encoded = Base64.encodeObject("keycloak-client-common");

        byte[] serializedObject = Base64.decode(encoded);

        assertThat(encoded).isNotBlank();
        assertThat(serializedObject).startsWith(SERIALIZED_STREAM_MAGIC);
        assertThat(new String(serializedObject, StandardCharsets.ISO_8859_1))
                .contains("keycloak-client-common");
    }
}
