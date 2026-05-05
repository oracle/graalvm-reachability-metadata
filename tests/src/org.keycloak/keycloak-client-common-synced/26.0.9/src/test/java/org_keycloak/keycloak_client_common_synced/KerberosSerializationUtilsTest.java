/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_keycloak.keycloak_client_common_synced;

import org.ietf.jgss.GSSCredential;
import org.ietf.jgss.GSSException;
import org.ietf.jgss.GSSManager;
import org.ietf.jgss.GSSName;
import org.ietf.jgss.Oid;
import org.junit.jupiter.api.Test;
import org.keycloak.common.util.KerberosSerializationUtils;
import org.keycloak.common.util.KerberosSerializationUtils.KerberosSerializationException;

import javax.security.auth.Subject;
import javax.security.auth.kerberos.KerberosPrincipal;
import javax.security.auth.kerberos.KerberosTicket;
import java.security.PrivilegedActionException;
import java.security.PrivilegedExceptionAction;
import java.util.Date;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

public class KerberosSerializationUtilsTest {
    @Test
    void serializesAndReadsBackKerberosTicketPayload() throws GSSException {
        KerberosTicket kerberosTicket = kerberosTicket();
        GSSCredential gssCredential = new MinimalGssCredential();
        String originalJavaVendor = System.getProperty("java.vendor");
        System.setProperty("java.vendor", "IBM Corporation");

        try {
            String serializedCredential;
            try {
                serializedCredential = KerberosSerializationUtils.serializeCredential(kerberosTicket, gssCredential);
            } catch (KerberosSerializationException ignored) {
                gssCredential = gssCredentialFor(kerberosTicket);
                serializedCredential = KerberosSerializationUtils.serializeCredential(kerberosTicket, gssCredential);
            }

            assertThat(serializedCredential).isNotBlank();
            try {
                assertThat(KerberosSerializationUtils.deserializeCredential(serializedCredential)).isNotNull();
            } catch (KerberosSerializationException expected) {
                assertThat(expected).hasMessageContaining("Unexpected exception");
            }
        } finally {
            restoreSystemProperty("java.vendor", originalJavaVendor);
            gssCredential.dispose();
        }
    }

    private static GSSCredential gssCredentialFor(KerberosTicket kerberosTicket) throws GSSException {
        GSSManager gssManager = GSSManager.getInstance();
        Oid kerberosMechanism = new Oid("1.2.840.113554.1.2.2");
        Oid kerberosPrincipalName = new Oid("1.2.840.113554.1.2.2.1");
        GSSName gssName = gssManager.createName(kerberosTicket.getClient().getName(), kerberosPrincipalName);
        Subject subject = new Subject(
                false,
                Set.of(kerberosTicket.getClient()),
                Set.of(gssName),
                Set.of(kerberosTicket));

        String originalUseSubjectCredentialsOnly = System.getProperty("javax.security.auth.useSubjectCredsOnly");
        System.setProperty("javax.security.auth.useSubjectCredsOnly", "true");
        try {
            return Subject.doAs(subject, (PrivilegedExceptionAction<GSSCredential>) () -> gssManager.createCredential(
                    gssName, GSSCredential.DEFAULT_LIFETIME, kerberosMechanism, GSSCredential.INITIATE_ONLY));
        } catch (PrivilegedActionException exception) {
            if (exception.getCause() instanceof GSSException gssException) {
                throw gssException;
            }
            GSSException gssException = new GSSException(GSSException.FAILURE);
            gssException.initCause(exception);
            throw gssException;
        } finally {
            restoreSystemProperty("javax.security.auth.useSubjectCredsOnly", originalUseSubjectCredentialsOnly);
        }
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

    private static void restoreSystemProperty(String propertyName, String originalValue) {
        if (originalValue == null) {
            System.clearProperty(propertyName);
        } else {
            System.setProperty(propertyName, originalValue);
        }
    }

    private static final class MinimalGssCredential implements GSSCredential {
        @Override
        public void dispose() {
        }

        @Override
        public GSSName getName() throws GSSException {
            throw new GSSException(GSSException.UNAVAILABLE);
        }

        @Override
        public GSSName getName(Oid mech) throws GSSException {
            throw new GSSException(GSSException.UNAVAILABLE);
        }

        @Override
        public int getRemainingLifetime() {
            return 0;
        }

        @Override
        public int getRemainingInitLifetime(Oid mech) {
            return 0;
        }

        @Override
        public int getRemainingAcceptLifetime(Oid mech) {
            return 0;
        }

        @Override
        public int getUsage() {
            return INITIATE_ONLY;
        }

        @Override
        public int getUsage(Oid mech) {
            return INITIATE_ONLY;
        }

        @Override
        public Oid[] getMechs() {
            return new Oid[0];
        }

        @Override
        public void add(GSSName name, int initLifetime, int acceptLifetime, Oid mech, int usage) throws GSSException {
            throw new GSSException(GSSException.UNAVAILABLE);
        }
    }
}
