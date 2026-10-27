/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_oracle_database_jdbc.ojdbc_provider_azure;

import static org.assertj.core.api.Assertions.assertThat;

import com.azure.core.credential.TokenCredential;
import com.azure.identity.ClientSecretCredential;
import oracle.jdbc.provider.azure.authentication.AzureAuthenticationMethod;
import oracle.jdbc.provider.azure.authentication.TokenCredentialFactory;
import oracle.jdbc.provider.factory.Resource;
import oracle.jdbc.provider.parameter.ParameterSet;
import org.junit.jupiter.api.Test;

public class TokenCredentialFactoryTest {
    @Test
    void createsSensitiveClientSecretCredentialForServicePrincipal() {
        ParameterSet parameters =
                ParameterSet.builder()
                        .add(
                                "authentication",
                                TokenCredentialFactory.AUTHENTICATION_METHOD,
                                AzureAuthenticationMethod.SERVICE_PRINCIPLE)
                        .add("clientId", TokenCredentialFactory.CLIENT_ID, "orders-client")
                        .add("tenantId", TokenCredentialFactory.TENANT_ID, "orders-tenant")
                        .add(
                                "clientSecret",
                                TokenCredentialFactory.CLIENT_SECRET,
                                "orders-secret")
                        .build();

        Resource<TokenCredential> credentialResource =
                TokenCredentialFactory.getInstance().request(parameters);

        assertThat(credentialResource.getContent()).isInstanceOf(ClientSecretCredential.class);
        assertThat(credentialResource.isSensitive()).isTrue();
        assertThat(credentialResource.isValid()).isTrue();
    }
}
