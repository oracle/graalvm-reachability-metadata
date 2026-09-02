/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_activemq.activemq_broker;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;

import jakarta.jms.Connection;
import jakarta.jms.MessageConsumer;
import jakarta.jms.Session;

import org.apache.activemq.ActiveMQConnectionFactory;
import org.apache.activemq.broker.BrokerPlugin;
import org.apache.activemq.broker.BrokerService;
import org.apache.activemq.command.ActiveMQQueue;
import org.apache.activemq.plugin.SubQueueSelectorCacheBroker;
import org.apache.activemq.plugin.SubQueueSelectorCacheBrokerPlugin;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.io.TempDir;

public class SubQueueSelectorCacheBrokerTest {

    private static final String QUEUE_NAME = "selector.persist.orders";
    private static final String SELECTOR = "priority = 9";

    @TempDir
    Path temporaryDirectory;

    @Test
    @Timeout(30)
    void persistsSelectorsConfiguredThroughBrokerPlugin() throws Exception {
        Path cacheFile = temporaryDirectory.resolve("selectors.dat");
        BrokerService brokerService = startBroker("selector-cache-persist-broker", cacheFile);

        try {
            addSelectorConsumer(brokerService, QUEUE_NAME, SELECTOR);

            SubQueueSelectorCacheBroker cacheBroker = selectorCache(brokerService);
            assertThat(cacheBroker.getSelectorsForDestination(qualifiedName(QUEUE_NAME)))
                    .containsExactly(SELECTOR);
        } finally {
            brokerService.stop();
            brokerService.waitUntilStopped();
        }

        assertThat(cacheFile).isRegularFile();
        assertThat(Files.size(cacheFile)).isPositive();
    }

    static BrokerService startBroker(String brokerName, Path cacheFile) throws Exception {
        SubQueueSelectorCacheBrokerPlugin plugin = new SubQueueSelectorCacheBrokerPlugin();
        plugin.setPersistFile(cacheFile.toFile());

        BrokerService brokerService = new BrokerService();
        brokerService.setBrokerName(brokerName);
        brokerService.setPersistent(false);
        brokerService.setUseJmx(false);
        brokerService.setUseShutdownHook(false);
        brokerService.setPlugins(new BrokerPlugin[] {plugin});
        brokerService.start();
        brokerService.waitUntilStarted();
        return brokerService;
    }

    static void addSelectorConsumer(BrokerService brokerService, String queueName, String selector)
            throws Exception {
        ActiveMQConnectionFactory connectionFactory =
                new ActiveMQConnectionFactory("vm://" + brokerService.getBrokerName() + "?create=false");
        try (Connection connection = connectionFactory.createConnection();
                Session session = connection.createSession(false, Session.AUTO_ACKNOWLEDGE);
                MessageConsumer consumer = session.createConsumer(session.createQueue(queueName), selector)) {
            connection.start();
            assertThat(consumer.getMessageSelector()).isEqualTo(selector);
        }
    }

    static SubQueueSelectorCacheBroker selectorCache(BrokerService brokerService) throws Exception {
        return (SubQueueSelectorCacheBroker) brokerService.getBroker()
                .getAdaptor(SubQueueSelectorCacheBroker.class);
    }

    static String qualifiedName(String queueName) {
        return new ActiveMQQueue(queueName).getQualifiedName();
    }
}
