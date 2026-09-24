/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_oracle_database_jdbc.ojdbc_provider_common;

import static oracle.jdbc.provider.parameter.Parameter.CommonAttribute.REQUIRED;
import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;
import oracle.jdbc.provider.factory.Resource;
import oracle.jdbc.provider.factory.ResourceFactory;
import oracle.jdbc.provider.parameter.Parameter;
import oracle.jdbc.provider.resource.AbstractResourceProvider;
import oracle.jdbc.provider.resource.ResourceParameter;
import oracle.jdbc.spi.OracleResourceProvider;
import org.junit.jupiter.api.Test;

public class AbstractResourceProviderTest {
    private static final Parameter<String> SERVICE = Parameter.create(REQUIRED);
    private static final ResourceParameter SERVICE_PARAMETER =
            new ResourceParameter("service", SERVICE);
    private static final ResourceFactory<String> FACTORY =
            parameters ->
                    Resource.createPermanentResource(
                            "resolved-" + parameters.getRequired(SERVICE), false);

    @Test
    void parsesSpiValuesAndRequestsTheConfiguredResource() {
        TestResourceProvider provider = new TestResourceProvider();

        assertThat(provider.getName()).isEqualTo("ojdbc-provider-demo-configuration");
        assertThat(provider.getParameters())
                .extracting(OracleResourceProvider.Parameter::name)
                .containsExactly("service");
        assertThat(provider.resolve(Map.of(provider.serviceParameter(), "orders")))
                .isEqualTo("resolved-orders");
    }

    private static final class TestResourceProvider extends AbstractResourceProvider {
        private TestResourceProvider() {
            super("demo", "configuration", SERVICE_PARAMETER);
        }

        private OracleResourceProvider.Parameter serviceParameter() {
            return SERVICE_PARAMETER;
        }

        private String resolve(
                Map<OracleResourceProvider.Parameter, CharSequence> parameterValues) {
            return getResource(FACTORY, parameterValues);
        }
    }
}
