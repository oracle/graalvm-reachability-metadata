/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_keycloak.keycloak_client_common_synced;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.ObjectOutputStream;
import java.util.Date;

import org.junit.jupiter.api.Test;
import org.keycloak.common.util.KerberosSerializationUtils;
import org.keycloak.common.util.KerberosSerializationUtils.KerberosSerializationException;

public class KerberosSerializationUtilsTest {
    @Test
    void rejectsSerializedObjectThatIsNotKerberosTicket() throws IOException {
        String serializedDate = serializeForKerberosInput(new Date(0L));

        assertThatThrownBy(() -> KerberosSerializationUtils.deserializeCredential(serializedDate))
                .isInstanceOf(KerberosSerializationException.class)
                .hasMessageContaining("Deserialized object is not KerberosTicket");
    }

    private static String serializeForKerberosInput(Object value) throws IOException {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (ObjectOutputStream outputStream = new ObjectOutputStream(bytes)) {
            outputStream.writeObject(value);
        }
        return java.util.Base64.getEncoder().encodeToString(bytes.toByteArray());
    }
}
