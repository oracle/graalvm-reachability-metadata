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
import com.azure.identity.DefaultAzureCredential;
import com.azure.identity.DefaultAzureCredentialBuilder;
import java.time.Duration;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

@Timeout(60)
public class BrokerCredentialTest {
    private static final Duration ASYNC_TIMEOUT = Duration.ofSeconds(50);
    private static final Duration PROCESS_TIMEOUT = Duration.ofSeconds(10);

    @Test
    void developmentChainCreatesBrokerCredential() {
        Configuration configuration
                = Configuration.NONE.clone().put("AZURE_TOKEN_CREDENTIALS", "dev");
        DefaultAzureCredential credential = new DefaultAzureCredentialBuilder()
                .tenantId("identity-tenant")
                .configuration(configuration)
                .credentialProcessTimeout(PROCESS_TIMEOUT)
                .build();

        TokenRequestContext request
                = new TokenRequestContext().addScopes("https://management.azure.com/.default");

        assertThatThrownBy(() -> credential.getToken(request).block(ASYNC_TIMEOUT))
                .isInstanceOf(ClientAuthenticationException.class)
                .hasMessageContaining("DefaultAzureCredential");
    }
}
