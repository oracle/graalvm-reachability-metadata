/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_keycloak.keycloak_client_common_synced;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;

import org.junit.jupiter.api.Test;
import org.keycloak.common.util.Base64;

@SuppressWarnings("deprecation")
public class Base64Test {
    @Test
    void encodesSerializableObjectAsBase64SerializedStream() throws IOException {
        String encoded = Base64.encodeObject("Keycloak Base64 serialized");

        assertThat(Base64.decode(encoded)).isEqualTo(serializedStringBytes());
    }

    private static byte[] serializedStringBytes() {
        return new byte[] {
                (byte) 0xac, (byte) 0xed, 0x00, 0x05, 0x74, 0x00, 0x1a, 0x4b,
                0x65, 0x79, 0x63, 0x6c, 0x6f, 0x61, 0x6b, 0x20,
                0x42, 0x61, 0x73, 0x65, 0x36, 0x34, 0x20, 0x73,
                0x65, 0x72, 0x69, 0x61, 0x6c, 0x69, 0x7a, 0x65, 0x64
        };
    }
}
