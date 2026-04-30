/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_kafka.kafka_clients;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.security.Provider;
import java.security.Security;
import java.util.Map;

import org.apache.kafka.common.config.SecurityConfig;
import org.apache.kafka.common.security.auth.SecurityProviderCreator;
import org.apache.kafka.common.utils.SecurityUtils;
import org.junit.jupiter.api.Test;

public class SecurityUtilsTest {
    private static final String TEST_PROVIDER_NAME = "SecurityUtilsDynamicAccessProvider";

    @Test
    void installsConfiguredSecurityProviderFromCreatorClassName() {
        Security.removeProvider(TEST_PROVIDER_NAME);
        TestSecurityProviderCreator.configured = false;

        String creatorClassName = System.getProperty(
                "security.utils.test.provider.creator",
                TestSecurityProviderCreator.class.getName());
        Map<String, Object> configs = Map.of(
                SecurityConfig.SECURITY_PROVIDERS_CONFIG,
                "  " + creatorClassName.substring(0, creatorClassName.length()) + "  ");

        try {
            SecurityUtils.addConfiguredSecurityProviders(configs);

            assertTrue(TestSecurityProviderCreator.configured);
            assertSame(TestSecurityProviderCreator.PROVIDER, Security.getProvider(TEST_PROVIDER_NAME));
            assertEquals(TEST_PROVIDER_NAME, Security.getProviders()[0].getName());
        } finally {
            Security.removeProvider(TEST_PROVIDER_NAME);
        }
    }

    public static final class TestSecurityProviderCreator implements SecurityProviderCreator {
        static final Provider PROVIDER = new TestSecurityProvider();
        static boolean configured;

        @Override
        public void configure(Map<String, ?> config) {
            configured = true;
        }

        @Override
        public Provider getProvider() {
            return PROVIDER;
        }
    }

    private static final class TestSecurityProvider extends Provider {
        private static final long serialVersionUID = 1L;

        TestSecurityProvider() {
            super(TEST_PROVIDER_NAME, "1.0", "SecurityUtils dynamic-access test provider");
        }
    }
}
