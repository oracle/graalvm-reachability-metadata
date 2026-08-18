/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_keycloak.keycloak_client_common_synced;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;
import org.keycloak.common.util.KerberosSerializationUtils;
import org.keycloak.common.util.KerberosSerializationUtils.KerberosSerializationException;

public class KerberosSerializationUtilsTest {
    private static final String SERIALIZED_NON_TICKET = "rO0ABXQADG5vdC1hLXRpY2tldA==";

    @Test
    void rejectsSerializedObjectThatIsNotKerberosTicket() {
        assertThatThrownBy(() -> KerberosSerializationUtils.deserializeCredential(SERIALIZED_NON_TICKET))
                .isInstanceOf(KerberosSerializationException.class)
                .hasMessageContaining("Deserialized object is not KerberosTicket");
    }
}
