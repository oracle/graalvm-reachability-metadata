/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_keycloak.keycloak_client_common_synced;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.ietf.jgss.GSSCredential;
import org.ietf.jgss.GSSException;
import org.ietf.jgss.GSSName;
import org.ietf.jgss.Oid;
import org.junit.jupiter.api.Test;
import org.keycloak.common.util.KerberosJdkProvider;
import org.keycloak.common.util.KerberosSerializationUtils.KerberosSerializationException;

public class KerberosJdkProviderInnerSunJDKProviderTest {

    @Test
    void rejectsCredentialThatDoesNotExposeKerberosTicket() {
        KerberosJdkProvider provider = KerberosJdkProvider.getProvider();

        assertThatThrownBy(
                () -> provider.gssCredentialToKerberosTicket(null, new EmptyGssCredential()))
                .isInstanceOf(KerberosSerializationException.class)
                .hasMessageContaining("Not available kerberosTicket in subject credentials");
    }

    private static final class EmptyGssCredential implements GSSCredential {

        @Override
        public void dispose() throws GSSException {
            throw unexpectedCall();
        }

        @Override
        public GSSName getName() throws GSSException {
            throw unexpectedCall();
        }

        @Override
        public GSSName getName(Oid mech) throws GSSException {
            throw unexpectedCall();
        }

        @Override
        public int getRemainingLifetime() throws GSSException {
            throw unexpectedCall();
        }

        @Override
        public int getRemainingInitLifetime(Oid mech) throws GSSException {
            throw unexpectedCall();
        }

        @Override
        public int getRemainingAcceptLifetime(Oid mech) throws GSSException {
            throw unexpectedCall();
        }

        @Override
        public int getUsage() throws GSSException {
            throw unexpectedCall();
        }

        @Override
        public int getUsage(Oid mech) throws GSSException {
            throw unexpectedCall();
        }

        @Override
        public Oid[] getMechs() throws GSSException {
            throw unexpectedCall();
        }

        @Override
        public void add(
                GSSName name,
                int initLifetime,
                int acceptLifetime,
                Oid mech,
                int usage) throws GSSException {
            throw unexpectedCall();
        }

        private AssertionError unexpectedCall() {
            return new AssertionError("GSSUtil should not inspect non-JDK credential implementations");
        }
    }
}
