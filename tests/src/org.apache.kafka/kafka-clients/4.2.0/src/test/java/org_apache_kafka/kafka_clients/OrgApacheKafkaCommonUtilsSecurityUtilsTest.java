/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_kafka.kafka_clients;

import org.apache.kafka.common.config.SecurityConfig;
import org.apache.kafka.common.security.auth.SecurityProviderCreator;
import org.apache.kafka.common.utils.SecurityUtils;
import org.junit.jupiter.api.Test;

import java.security.Provider;
import java.security.Security;
import java.util.Collections;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

public class OrgApacheKafkaCommonUtilsSecurityUtilsTest {

    private static final String PROVIDER_NAME = "ForgeKafkaClientsProvider";

    @Test
    void addsConfiguredSecurityProviderCreatorByClassName() {
        Map<String, Object> configs = Collections.singletonMap(
                SecurityConfig.SECURITY_PROVIDERS_CONFIG,
                TestSecurityProviderCreator.class.getName());

        Security.removeProvider(PROVIDER_NAME);
        try {
            SecurityUtils.addConfiguredSecurityProviders(configs);

            assertThat(Security.getProvider(PROVIDER_NAME)).isNotNull();
        } finally {
            Security.removeProvider(PROVIDER_NAME);
        }
    }

    public static final class TestSecurityProviderCreator implements SecurityProviderCreator {

        private static final Provider PROVIDER = new TestSecurityProvider();

        @Override
        public Provider getProvider() {
            return PROVIDER;
        }
    }

    public static final class TestSecurityProvider extends Provider {

        private TestSecurityProvider() {
            super(PROVIDER_NAME, "1.0", "Provider used by kafka-clients reachability tests");
            put("MessageDigest.ForgeKafkaClients", TestSecurityProvider.class.getName());
        }
    }
}
