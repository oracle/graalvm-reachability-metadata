/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_activemq.activemq_broker;

import org.apache.activemq.broker.Broker;
import org.apache.activemq.broker.BrokerService;
import org.apache.activemq.broker.ConnectionContext;
import org.apache.activemq.broker.EmptyBroker;
import org.apache.activemq.command.ActiveMQQueue;
import org.apache.activemq.command.ConsumerInfo;
import org.apache.activemq.plugin.SubQueueSelectorCacheBroker;
import org.apache.activemq.plugin.SubQueueSelectorCacheBrokerPlugin;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

public class SubQueueSelectorCacheBrokerTest {

    @TempDir
    Path temporaryDirectory;

    @Test
    void persistsCachedSelectorsWhenStopped() throws Exception {
        Path cacheFile = temporaryDirectory.resolve("selectors.ser");
        SubQueueSelectorCacheBroker broker = createCacheBroker(cacheFile);
        ConsumerInfo consumer = new ConsumerInfo();
        consumer.setDestination(new ActiveMQQueue("Consumer.orders.VirtualTopic.events"));
        consumer.setSelector("region = 'eu'");

        try {
            broker.addConsumer(new ConnectionContext(), consumer);
            assertThat(broker.getSelectorsForDestination("queue://Consumer.orders.VirtualTopic.events"))
                    .contains("region = 'eu'");
        } finally {
            broker.stop();
        }

        assertThat(Files.size(cacheFile)).isPositive();
    }

    private SubQueueSelectorCacheBroker createCacheBroker(Path cacheFile) throws Exception {
        BrokerService brokerService = new BrokerService();
        brokerService.setUseJmx(false);
        Broker next = new EmptyBroker() {
            @Override
            public BrokerService getBrokerService() {
                return brokerService;
            }
        };
        SubQueueSelectorCacheBrokerPlugin plugin = new SubQueueSelectorCacheBrokerPlugin();
        plugin.setPersistFile(cacheFile.toFile());
        plugin.setPersistInterval(10_000L);
        return (SubQueueSelectorCacheBroker) plugin.installPlugin(next);
    }
}
