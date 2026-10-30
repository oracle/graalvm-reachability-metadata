/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_activemq.activemq_broker;

import org.apache.activemq.broker.BrokerService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

@Timeout(60)
public class SubQueueSelectorCacheBrokerTest {

    @TempDir
    Path temporaryDirectory;

    @Test
    void persistsConsumerSelectorsOnShutdown() throws Exception {
        Path cacheFile = temporaryDirectory.resolve("selectors.ser");
        BrokerService broker = SelectorCacheTestSupport.startBroker(cacheFile);
        String queue = "Consumer.orders.VirtualTopic.events";
        try {
            SelectorCacheTestSupport.subscribe(broker, queue, "region = 'eu'");
            assertThat(SelectorCacheTestSupport.cache(broker).getSelectorsForDestination("queue://" + queue))
                    .containsExactly("region = 'eu'");
        } finally {
            broker.stop();
        }
        assertThat(Files.size(cacheFile)).isPositive();
    }
}
