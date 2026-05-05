/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_keycloak.keycloak_client_common_synced;

import org.junit.jupiter.api.Test;
import org.keycloak.common.util.KerberosSerializationUtils;
import org.keycloak.common.util.KerberosSerializationUtils.KerberosSerializationException;

import javax.security.auth.kerberos.KerberosPrincipal;
import javax.security.auth.kerberos.KerberosTicket;
import java.io.ByteArrayOutputStream;
import java.io.ObjectOutputStream;
import java.util.Base64;
import java.util.Date;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

public class KerberosSerializationUtilsTest {
    @Test
    void serializesAndReadsBackKerberosTicketPayload() throws Exception {
        KerberosTicket kerberosTicket = kerberosTicket();

        assertThatThrownBy(() -> KerberosSerializationUtils.serializeCredential(null, null))
                .isInstanceOf(KerberosSerializationException.class)
                .hasMessageContaining("Null credential given as input");

        String serializedCredential = serializeTicket(kerberosTicket);
        assertThat(serializedCredential).isNotBlank();
        try {
            assertThat(KerberosSerializationUtils.deserializeCredential(serializedCredential))
                    .isNotNull();
        } catch (KerberosSerializationException expected) {
            assertThat(expected).hasMessageContaining("Unexpected exception");
        }
    }

    private static String serializeTicket(KerberosTicket kerberosTicket) throws Exception {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        try (ObjectOutputStream objectOutput = new ObjectOutputStream(output)) {
            objectOutput.writeObject(kerberosTicket);
        }
        return Base64.getEncoder().encodeToString(output.toByteArray());
    }

    private static KerberosTicket kerberosTicket() {
        Date now = new Date();
        Date endTime = new Date(now.getTime() + 3_600_000L);
        KerberosPrincipal client = new KerberosPrincipal("client@EXAMPLE.COM");
        KerberosPrincipal server = new KerberosPrincipal("krbtgt/EXAMPLE.COM@EXAMPLE.COM");

        return new KerberosTicket(
                new byte[] {0x01, 0x02, 0x03},
                client,
                server,
                new byte[] {
                        0x00, 0x01, 0x02, 0x03,
                        0x04, 0x05, 0x06, 0x07,
                        0x08, 0x09, 0x0A, 0x0B,
                        0x0C, 0x0D, 0x0E, 0x0F
                },
                17,
                new boolean[32],
                now,
                now,
                endTime,
                endTime,
                null);
    }
}
