/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package io_micronaut_security.micronaut_security_ojdbc_extensions;

import static org.assertj.core.api.Assertions.assertThat;

import io.micronaut.security.ojdbc.extensions.MicronautEndUserSecurityContextProvider;
import java.util.Map;
import java.util.ServiceLoader;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;
import java.util.stream.Collectors;
import oracle.jdbc.spi.EndUserSecurityContextProvider;
import oracle.jdbc.spi.OracleResourceProvider;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

public class MicronautEndUserSecurityContextProviderConfigurationTest {
    @Test
    @Timeout(value = 30, unit = TimeUnit.SECONDS)
    void exposesOracleJdbcProviderIdentityAndParameterContract() {
        MicronautEndUserSecurityContextProvider provider = new MicronautEndUserSecurityContextProvider();

        assertThat(provider.getName()).isEqualTo("ojdbc-provider-micronaut-end-user-security-context");
        Map<String, OracleResourceProvider.Parameter> parameters = provider.getParameters().stream()
                .collect(Collectors.toMap(OracleResourceProvider.Parameter::name, Function.identity()));

        assertThat(parameters).containsOnlyKeys(
                "tokenUrl",
                "clientId",
                "clientSecret",
                "scope",
                "dataRoles",
                "endUserContextAttributes",
                "rolePrefix",
                "attributeNames");
        assertParameter(parameters.get("tokenUrl"), true, false, null);
        assertParameter(parameters.get("clientId"), true, false, null);
        assertParameter(parameters.get("clientSecret"), true, true, null);
        assertParameter(parameters.get("scope"), false, false, null);
        assertParameter(parameters.get("dataRoles"), false, false, null);
        assertParameter(parameters.get("endUserContextAttributes"), false, false, null);
        assertParameter(parameters.get("rolePrefix"), false, false, "ORACLE_DATA_ROLE_");
        assertParameter(parameters.get("attributeNames"), false, false, "ORACLE_CONTEXT_ATTRIBUTES");
    }

    @Test
    @Timeout(value = 30, unit = TimeUnit.SECONDS)
    void discoversAndInvokesProviderThroughOracleJdbcServiceProviderInterface() {
        ServiceLoader<EndUserSecurityContextProvider> serviceLoader =
                ServiceLoader.load(EndUserSecurityContextProvider.class);
        EndUserSecurityContextProvider provider = serviceLoader.stream()
                .map(ServiceLoader.Provider::get)
                .filter(candidate -> candidate.getName()
                        .equals("ojdbc-provider-micronaut-end-user-security-context"))
                .findFirst()
                .orElseThrow();

        assertThat(provider.getParameters()).hasSize(8);
        assertThat(provider.getEndUserSecurityContext(Map.of())).isNull();
    }

    private static void assertParameter(
            OracleResourceProvider.Parameter parameter,
            boolean required,
            boolean sensitive,
            String defaultValue) {
        assertThat(parameter).isNotNull();
        assertThat(parameter.isRequired()).isEqualTo(required);
        assertThat(parameter.isSensitive()).isEqualTo(sensitive);
        assertThat(parameter.defaultValue()).isEqualTo(defaultValue);
    }
}
