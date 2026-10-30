/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_activemq.activemq_broker;

import org.apache.activemq.ActiveMQConnectionFactory;
import org.apache.activemq.broker.BrokerPlugin;
import org.apache.activemq.broker.BrokerService;
import org.apache.activemq.plugin.SubQueueSelectorCacheBroker;
import org.apache.activemq.plugin.SubQueueSelectorCacheBrokerPlugin;

import jakarta.jms.Connection;
import jakarta.jms.MessageConsumer;
import jakarta.jms.Session;
import java.nio.file.Path;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

final class SelectorCacheTestSupport {

    private SelectorCacheTestSupport() {
    }

    static BrokerService startBroker(Path cacheFile) throws Exception {
        SubQueueSelectorCacheBrokerPlugin plugin = new SubQueueSelectorCacheBrokerPlugin();
        plugin.setPersistFile(cacheFile.toFile());
        BrokerService broker = new BrokerService();
        broker.setBrokerName("selector-cache-" + UUID.randomUUID());
        broker.setUseJmx(false);
        broker.setUseShutdownHook(false);
        broker.setPersistent(false);
        broker.setPlugins(new BrokerPlugin[]{plugin});
        try {
            broker.start();
            return broker;
        } catch (Exception exception) {
            broker.stop();
            throw exception;
        }
    }

    static void subscribe(BrokerService broker, String queue, String selector) throws Exception {
        ActiveMQConnectionFactory factory = new ActiveMQConnectionFactory(
                "vm://" + broker.getBrokerName() + "?create=false");
        factory.setConnectResponseTimeout(10000);
        try (Connection connection = factory.createConnection();
                Session session = connection.createSession(false, Session.AUTO_ACKNOWLEDGE);
                MessageConsumer consumer = session.createConsumer(session.createQueue(queue), selector)) {
            connection.start();
            assertThat(consumer.getMessageSelector()).isEqualTo(selector);
        }
    }

    static SubQueueSelectorCacheBroker cache(BrokerService broker) throws Exception {
        return (SubQueueSelectorCacheBroker) broker.getBroker().getAdaptor(SubQueueSelectorCacheBroker.class);
    }
}
