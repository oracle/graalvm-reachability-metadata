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
import java.util.UUID;

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

    static final String QUEUE_NAME = "selector.orders";
    static final String SELECTOR = "priority = 9";

    @TempDir
    Path temporaryDirectory;

    @Test
    @Timeout(30)
    void reloadsPersistedSelectorCache() throws Exception {
        Path cacheFile = temporaryDirectory.resolve("selectors.dat");
        BrokerService firstBroker = startBroker(cacheFile);

        try {
            addSelectorConsumer(firstBroker);
            assertThat(selectorCache(firstBroker).getSelectorsForDestination(qualifiedQueueName()))
                    .containsExactly(SELECTOR);
        } finally {
            firstBroker.stop();
        }

        assertThat(cacheFile).isRegularFile();
        assertThat(Files.size(cacheFile)).isPositive();

        BrokerService secondBroker = startBroker(cacheFile);
        try {
            assertThat(selectorCache(secondBroker).getSelectorsForDestination(qualifiedQueueName()))
                    .containsExactly(SELECTOR);
        } finally {
            secondBroker.stop();
        }
    }

    static BrokerService startBroker(Path cacheFile) throws Exception {
        SubQueueSelectorCacheBrokerPlugin plugin = new SubQueueSelectorCacheBrokerPlugin();
        plugin.setPersistFile(cacheFile.toFile());

        BrokerService brokerService = new BrokerService();
        brokerService.setBrokerName("selector-cache-" + UUID.randomUUID().toString().replace("-", ""));
        brokerService.setPersistent(false);
        brokerService.setUseJmx(false);
        brokerService.setUseShutdownHook(false);
        brokerService.setPlugins(new BrokerPlugin[] {plugin});
        brokerService.start();
        return brokerService;
    }

    static void addSelectorConsumer(BrokerService brokerService) throws Exception {
        ActiveMQConnectionFactory connectionFactory =
                new ActiveMQConnectionFactory("vm://" + brokerService.getBrokerName() + "?create=false");
        try (Connection connection = connectionFactory.createConnection();
                Session session = connection.createSession(false, Session.AUTO_ACKNOWLEDGE);
                MessageConsumer consumer = session.createConsumer(
                        session.createQueue(QUEUE_NAME), SELECTOR)) {
            connection.start();
            assertThat(consumer.getMessageSelector()).isEqualTo(SELECTOR);
        }
    }

    static SubQueueSelectorCacheBroker selectorCache(BrokerService brokerService) throws Exception {
        return (SubQueueSelectorCacheBroker) brokerService.getBroker()
                .getAdaptor(SubQueueSelectorCacheBroker.class);
    }

    static String qualifiedQueueName() {
        return new ActiveMQQueue(QUEUE_NAME).getQualifiedName();
    }
}
