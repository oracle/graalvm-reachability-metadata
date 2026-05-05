/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_keycloak.keycloak_client_common_synced;

import org.junit.jupiter.api.Test;
import org.keycloak.common.util.KerberosJdkProvider;

import java.lang.reflect.InvocationTargetException;
import org.keycloak.common.util.KerberosSerializationUtils.KerberosSerializationException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

public class KerberosJdkProviderInnerSunJDKProviderTest {
    @Test
    void convertsGssCredentialThroughSunJdkProvider() {
        KerberosJdkProvider provider = KerberosJdkProvider.getProvider();
        assertThat(provider.getClass().getSimpleName()).isEqualTo("SunJDKProvider");

        assertThatThrownBy(() -> provider.gssCredentialToKerberosTicket(null, null))
                .isInstanceOf(KerberosSerializationException.class)
                .satisfies(exception -> {
                    KerberosSerializationException failure =
                            (KerberosSerializationException) exception;
                    assertSunJdkConversionWasAttempted(failure);
                });
    }

    private static void assertSunJdkConversionWasAttempted(
            KerberosSerializationException exception) {
        String message = exception.getMessage();
        if (message.contains("Not available kerberosTicket in subject credentials")) {
            return;
        }
        if (message.contains("Unexpected error during convert GSSCredential to KerberosTicket")) {
            assertThat(exception.getCause())
                    .isInstanceOfAny(IllegalAccessException.class, InvocationTargetException.class);
            return;
        }
        throw new AssertionError("Unexpected Kerberos conversion failure: " + message, exception);
    }
}
