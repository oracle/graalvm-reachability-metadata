/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_keycloak.keycloak_client_common_synced;

import java.io.ByteArrayOutputStream;
import java.io.ObjectOutputStream;
import java.util.Base64;
import java.util.Date;
import javax.security.auth.kerberos.KerberosPrincipal;
import javax.security.auth.kerberos.KerberosTicket;

import org.ietf.jgss.GSSCredential;
import org.ietf.jgss.GSSException;
import org.ietf.jgss.GSSName;
import org.ietf.jgss.Oid;
import org.junit.jupiter.api.Test;
import org.keycloak.common.util.KerberosSerializationUtils;
import org.keycloak.common.util.KerberosSerializationUtils.KerberosSerializationException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

public class KerberosSerializationUtilsTest {
    private static final String CLIENT_PRINCIPAL_NAME = "client@EXAMPLE.COM";
    private static final String SERVER_PRINCIPAL_NAME = "krbtgt/EXAMPLE.COM@EXAMPLE.COM";
    private static final long TICKET_LIFETIME_MILLIS = 60_000L;

    @Test
    void serializesKerberosTicketCredentials() {
        String originalJavaVendor = System.getProperty("java.vendor");
        System.setProperty("java.vendor", "IBM Corporation");

        try {
            String serialized = KerberosSerializationUtils.serializeCredential(
                    createKerberosTicket(),
                    new UnusedGssCredential());

            assertThat(serialized).isNotBlank();
            assertThat(Base64.getMimeDecoder().decode(serialized)).isNotEmpty();
        } finally {
            restoreJavaVendor(originalJavaVendor);
        }
    }

    @Test
    void rejectsSerializedPayloadsThatAreNotKerberosTickets() throws Exception {
        String serializedPrincipal = serializeForInput(new KerberosPrincipal(CLIENT_PRINCIPAL_NAME));

        assertThatThrownBy(() -> KerberosSerializationUtils.deserializeCredential(serializedPrincipal))
                .isInstanceOf(KerberosSerializationException.class)
                .hasMessageContaining("Deserialized object is not KerberosTicket");
    }

    private static KerberosTicket createKerberosTicket() {
        Date startTime = new Date();
        Date endTime = new Date(startTime.getTime() + TICKET_LIFETIME_MILLIS);
        boolean[] flags = new boolean[7];
        flags[1] = true;
        flags[3] = true;

        return new KerberosTicket(
                new byte[] {1, 2, 3},
                new KerberosPrincipal(CLIENT_PRINCIPAL_NAME),
                new KerberosPrincipal(SERVER_PRINCIPAL_NAME),
                new byte[] {4, 5, 6, 7, 8, 9, 10, 11},
                1,
                flags,
                startTime,
                startTime,
                endTime,
                endTime,
                null);
    }

    private static String serializeForInput(Object value) throws Exception {
        ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();
        try (ObjectOutputStream objectOutputStream = new ObjectOutputStream(byteArrayOutputStream)) {
            objectOutputStream.writeObject(value);
        }
        return Base64.getEncoder().encodeToString(byteArrayOutputStream.toByteArray());
    }

    private static void restoreJavaVendor(String originalJavaVendor) {
        if (originalJavaVendor == null) {
            System.clearProperty("java.vendor");
            return;
        }

        System.setProperty("java.vendor", originalJavaVendor);
    }

    private static final class UnusedGssCredential implements GSSCredential {
        @Override
        public void dispose() {
        }

        @Override
        public GSSName getName() throws GSSException {
            throw new GSSException(GSSException.UNAVAILABLE);
        }

        @Override
        public GSSName getName(Oid mechanism) throws GSSException {
            throw new GSSException(GSSException.UNAVAILABLE);
        }

        @Override
        public int getRemainingLifetime() {
            return DEFAULT_LIFETIME;
        }

        @Override
        public int getRemainingInitLifetime(Oid mechanism) {
            return DEFAULT_LIFETIME;
        }

        @Override
        public int getRemainingAcceptLifetime(Oid mechanism) {
            return DEFAULT_LIFETIME;
        }

        @Override
        public int getUsage() {
            return INITIATE_ONLY;
        }

        @Override
        public int getUsage(Oid mechanism) {
            return INITIATE_ONLY;
        }

        @Override
        public Oid[] getMechs() {
            return new Oid[0];
        }

        @Override
        public void add(GSSName name, int initLifetime, int acceptLifetime, Oid mechanism, int usage)
                throws GSSException {
            throw new GSSException(GSSException.UNAVAILABLE);
        }
    }
}
