/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_activemq.activemq_broker;

import org.apache.activemq.broker.Broker;
import org.apache.activemq.broker.EmptyBroker;
import org.apache.activemq.security.JaasAuthenticationPlugin;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class JaasAuthenticationPluginTest {

    private static final String LOGIN_CONFIG_PROPERTY = "java.security.auth.login.config";

    @Test
    void discoversBundledLoginConfiguration() {
        String originalConfig = System.getProperty(LOGIN_CONFIG_PROPERTY);
        System.clearProperty(LOGIN_CONFIG_PROPERTY);

        try {
            Broker securedBroker = new JaasAuthenticationPlugin().installPlugin(new EmptyBroker());

            assertThat(securedBroker).isNotNull();
            assertThat(System.getProperty(LOGIN_CONFIG_PROPERTY)).endsWith("login.config");
        } finally {
            if (originalConfig == null) {
                System.clearProperty(LOGIN_CONFIG_PROPERTY);
            } else {
                System.setProperty(LOGIN_CONFIG_PROPERTY, originalConfig);
            }
        }
    }
}
