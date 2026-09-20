/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_azure.azure_identity;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.azure.core.credential.TokenRequestContext;
import com.azure.core.exception.ClientAuthenticationException;
import com.azure.core.util.Configuration;
import com.azure.identity.InteractiveBrowserCredential;
import com.azure.identity.broker.InteractiveBrowserBrokerCredentialBuilder;
import java.time.Duration;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

@Timeout(60)
public class IdentityClientBaseTest {
    private static final Duration IO_TIMEOUT = Duration.ofSeconds(10);

    @Test
    void brokerValidatesTokenRequestsWithoutScopes() {
        InteractiveBrowserCredential credential = new InteractiveBrowserBrokerCredentialBuilder()
                .clientId("identity-client")
                .tenantId("identity-tenant")
                .setWindowHandle(0)
                .configuration(Configuration.NONE)
                .build();

        assertThatThrownBy(() -> credential.authenticate(new TokenRequestContext()).block(IO_TIMEOUT))
                .isInstanceOf(ClientAuthenticationException.class)
                .hasMessageContaining("Interactive Browser Authentication");
    }
}
