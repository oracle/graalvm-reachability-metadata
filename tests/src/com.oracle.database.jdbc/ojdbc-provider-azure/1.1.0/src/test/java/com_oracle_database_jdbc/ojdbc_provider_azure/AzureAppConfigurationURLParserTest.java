/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_oracle_database_jdbc.ojdbc_provider_azure;

import static org.assertj.core.api.Assertions.assertThat;

import oracle.jdbc.provider.azure.authentication.AzureAuthenticationMethod;
import oracle.jdbc.provider.azure.authentication.TokenCredentialFactory;
import oracle.jdbc.provider.azure.configuration.AzureAppConfigurationURLParser;
import oracle.jdbc.provider.parameter.ParameterSet;
import org.junit.jupiter.api.Test;

public class AzureAppConfigurationURLParserTest {
    @Test
    void parsesStoreFiltersAndServicePrincipalConfiguration() {
        AzureAppConfigurationURLParser parser =
                new AzureAppConfigurationURLParser(
                        "orders-config?key=jdbc/&label=production"
                                + "&authentication=AZURE_SERVICE_PRINCIPAL"
                                + "&azure_client_id=orders-client"
                                + "&azure_tenant_id=orders-tenant"
                                + "&azure_client_secret=orders-secret");
        ParameterSet parameters = parser.getParameters();

        assertThat(parser.getName()).isEqualTo("orders-config");
        assertThat(parser.getPrefix()).isEqualTo("jdbc/");
        assertThat(parameters.getOptional(AzureAppConfigurationURLParser.LABEL))
                .isEqualTo("production");
        assertThat(parameters.getOptional(TokenCredentialFactory.AUTHENTICATION_METHOD))
                .isEqualTo(AzureAuthenticationMethod.SERVICE_PRINCIPLE);
        assertThat(parameters.getOptional(TokenCredentialFactory.CLIENT_ID))
                .isEqualTo("orders-client");
        assertThat(parameters.getOptional(TokenCredentialFactory.TENANT_ID))
                .isEqualTo("orders-tenant");
        assertThat(parameters.getOptional(TokenCredentialFactory.CLIENT_SECRET))
                .isEqualTo("orders-secret");
    }

    @Test
    void parsesStoreWithoutOptionalConfiguration() {
        AzureAppConfigurationURLParser parser =
                new AzureAppConfigurationURLParser("inventory-config");

        assertThat(parser.getName()).isEqualTo("inventory-config");
        assertThat(parser.getPrefix()).isNull();
        assertThat(parser.getParameters().contains(AzureAppConfigurationURLParser.KEY))
                .isFalse();
        assertThat(parser.getParameters().contains(AzureAppConfigurationURLParser.LABEL))
                .isFalse();
    }
}
