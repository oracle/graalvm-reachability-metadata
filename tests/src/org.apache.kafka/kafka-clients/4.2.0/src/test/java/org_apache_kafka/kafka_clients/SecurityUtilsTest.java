/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_kafka.kafka_clients;

import org.apache.kafka.common.config.SecurityConfig;
import org.apache.kafka.common.security.ssl.mock.TestProvider;
import org.apache.kafka.common.security.ssl.mock.TestProviderCreator;
import org.apache.kafka.common.utils.SecurityUtils;
import org.junit.jupiter.api.Test;

import java.security.Provider;
import java.security.Security;
import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

public class SecurityUtilsTest {

    @Test
    void addsConfiguredSecurityProviderCreator() {
        Map<String, Object> configs = new HashMap<>();
        configs.put(SecurityConfig.SECURITY_PROVIDERS_CONFIG, TestProviderCreator.class.getName());
        Security.removeProvider("TestProvider");

        try {
            SecurityUtils.addConfiguredSecurityProviders(configs);

            Provider provider = Security.getProvider("TestProvider");
            assertThat(provider).isInstanceOf(TestProvider.class);
        } finally {
            Security.removeProvider("TestProvider");
        }
    }
}
