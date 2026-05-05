/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_keycloak.keycloak_client_common_synced;

import org.junit.jupiter.api.Test;
import org.keycloak.common.util.Base64;

import java.io.ByteArrayInputStream;
import java.io.ObjectInputStream;

import static org.assertj.core.api.Assertions.assertThat;

public class Base64Test {
    @Test
    void encodesSerializableObjectUsingObjectOutputStream() throws Exception {
        String original = "keycloak-client-common";

        String encoded = Base64.encodeObject(original);
        Object decoded;
        try (ObjectInputStream input = new ObjectInputStream(new ByteArrayInputStream(Base64.decode(encoded)))) {
            decoded = input.readObject();
        }

        assertThat(encoded).isNotBlank();
        assertThat(decoded).isEqualTo(original);
    }
}
