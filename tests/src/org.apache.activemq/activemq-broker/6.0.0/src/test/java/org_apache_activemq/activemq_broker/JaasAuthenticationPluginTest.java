/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_activemq.activemq_broker;

import static org.assertj.core.api.Assertions.assertThat;

import org.apache.activemq.broker.Broker;
import org.apache.activemq.broker.BrokerService;
import org.apache.activemq.security.JaasAuthenticationBroker;
import org.apache.activemq.security.JaasAuthenticationPlugin;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

public class JaasAuthenticationPluginTest {

    @Test
    @Timeout(30)
    void installsJaasAuthenticationBrokerWithClasspathDiscoveryEnabled() throws Exception {
        String propertyName = "java.security.auth.login.config";
        String previousValue = System.getProperty(propertyName);
        System.clearProperty(propertyName);
        BrokerService brokerService = startBroker();

        try {
            JaasAuthenticationPlugin plugin = new JaasAuthenticationPlugin();
            Broker securedBroker = plugin.installPlugin(brokerService.getBroker());

            assertThat(securedBroker).isInstanceOf(JaasAuthenticationBroker.class);
            assertThat(((JaasAuthenticationBroker) securedBroker).getNext())
                    .isSameAs(brokerService.getBroker());
        } finally {
            brokerService.stop();
            restoreProperty(propertyName, previousValue);
        }
    }

    private static BrokerService startBroker() throws Exception {
        BrokerService brokerService = new BrokerService();
        brokerService.setBrokerName("jaas-plugin-broker");
        brokerService.setPersistent(false);
        brokerService.setUseJmx(false);
        brokerService.setUseShutdownHook(false);
        brokerService.start();
        return brokerService;
    }

    private static void restoreProperty(String propertyName, String value) {
        if (value == null) {
            System.clearProperty(propertyName);
        } else {
            System.setProperty(propertyName, value);
        }
    }
}
