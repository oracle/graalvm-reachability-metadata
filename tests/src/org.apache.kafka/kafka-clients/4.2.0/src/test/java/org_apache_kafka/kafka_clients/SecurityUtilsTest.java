/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_kafka.kafka_clients;

import static org.assertj.core.api.Assertions.assertThat;

import java.security.Provider;
import java.security.Security;
import java.util.HashMap;
import java.util.Map;

import org.apache.kafka.common.config.SecurityConfig;
import org.apache.kafka.common.security.auth.SecurityProviderCreator;
import org.apache.kafka.common.utils.SecurityUtils;
import org.junit.jupiter.api.Test;

public class SecurityUtilsTest {

    private static final String CUSTOM_CONFIG_KEY = "security.utils.test.config";
    private static final String CUSTOM_CONFIG_VALUE = "configured";
    private static final String PROVIDER_NAME = "SecurityUtilsTestProvider";

    @Test
    void addConfiguredSecurityProvidersLoadsAndInstantiatesConfiguredCreator() {
        Security.removeProvider(PROVIDER_NAME);
        ConfiguredSecurityProviderCreator.reset();

        Map<String, Object> configs = new HashMap<>();
        configs.put(SecurityConfig.SECURITY_PROVIDERS_CONFIG, ConfiguredSecurityProviderCreator.class.getName());
        configs.put(CUSTOM_CONFIG_KEY, CUSTOM_CONFIG_VALUE);

        try {
            SecurityUtils.addConfiguredSecurityProviders(configs);

            assertThat(ConfiguredSecurityProviderCreator.constructorCalls()).isEqualTo(1);
            assertThat(ConfiguredSecurityProviderCreator.lastConfiguredValue()).isEqualTo(CUSTOM_CONFIG_VALUE);
            assertThat(Security.getProvider(PROVIDER_NAME)).isNotNull();
            assertThat(Security.getProviders()[0].getName()).isEqualTo(PROVIDER_NAME);
        } finally {
            Security.removeProvider(PROVIDER_NAME);
            ConfiguredSecurityProviderCreator.reset();
        }
    }

    public static class ConfiguredSecurityProviderCreator implements SecurityProviderCreator {
        private static int constructorCalls;
        private static String lastConfiguredValue;

        public ConfiguredSecurityProviderCreator() {
            constructorCalls++;
        }

        @Override
        public void configure(Map<String, ?> config) {
            lastConfiguredValue = (String) config.get(CUSTOM_CONFIG_KEY);
        }

        @Override
        public Provider getProvider() {
            return new TestProvider();
        }

        static int constructorCalls() {
            return constructorCalls;
        }

        static String lastConfiguredValue() {
            return lastConfiguredValue;
        }

        static void reset() {
            constructorCalls = 0;
            lastConfiguredValue = null;
        }
    }

    private static final class TestProvider extends Provider {
        private static final long serialVersionUID = 1L;

        private TestProvider() {
            super(PROVIDER_NAME, "1.0", "Test provider installed by SecurityUtilsTest");
        }
    }
}
