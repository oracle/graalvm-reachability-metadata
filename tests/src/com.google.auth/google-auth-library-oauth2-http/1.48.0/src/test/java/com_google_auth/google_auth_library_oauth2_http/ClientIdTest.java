/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_google_auth.google_auth_library_oauth2_http;

import static org.assertj.core.api.Assertions.assertThat;

import com.google.auth.oauth2.ClientId;
import java.io.IOException;
import org.junit.jupiter.api.Test;

public class ClientIdTest {
    @Test
    public void fromResourceLoadsClientIdJsonLocatedRelativeToCaller() throws IOException {
        ClientId clientId = ClientId.fromResource(ClientIdTest.class, "client_id.json");

        assertThat(clientId.getClientId()).isEqualTo("test-client-id.apps.googleusercontent.com");
        assertThat(clientId.getClientSecret()).isEqualTo("test-client-secret");

        ClientId rotatedClientId = clientId.toBuilder()
                .setClientSecret("rotated-client-secret")
                .build();
        assertThat(rotatedClientId.getClientId()).isEqualTo(clientId.getClientId());
        assertThat(rotatedClientId.getClientSecret()).isEqualTo("rotated-client-secret");
    }
}
