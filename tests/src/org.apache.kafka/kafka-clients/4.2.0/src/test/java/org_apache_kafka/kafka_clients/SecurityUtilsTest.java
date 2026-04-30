/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_kafka.kafka_clients;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;

import java.security.Provider;
import java.security.Security;
import java.util.Map;

import org.apache.kafka.common.config.SecurityConfig;
import org.apache.kafka.common.serialization.StringSerializer;
import org.apache.kafka.common.utils.SecurityUtils;
import org.junit.jupiter.api.Test;

public class SecurityUtilsTest {
    @Test
    void resolvesConfiguredSecurityProviderClassesBeforeRejectingWrongType() {
        Provider[] providersBefore = Security.getProviders();
        Map<String, Object> configs = Map.of(
                SecurityConfig.SECURITY_PROVIDERS_CONFIG,
                "  " + StringSerializer.class.getName() + "  ");

        SecurityUtils.addConfiguredSecurityProviders(configs);

        assertArrayEquals(providersBefore, Security.getProviders());
    }
}
