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

import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

@Timeout(60)
public class SubQueueSelectorCacheBrokerInnerSubSelectorClassObjectInputStreamTest {

    @TempDir
    Path temporaryDirectory;

    @Test
    void restoresSelectorsForMultipleQueuesAfterRestart() throws Exception {
        Path cacheFile = temporaryDirectory.resolve("selectors.ser");
        BrokerService writer = SelectorCacheTestSupport.startBroker(cacheFile);
        try {
            SelectorCacheTestSupport.subscribe(writer, "audit.events", "priority > 5");
            SelectorCacheTestSupport.subscribe(writer, "orders.events", "region = 'eu'");
        } finally {
            writer.stop();
        }
        BrokerService reader = SelectorCacheTestSupport.startBroker(cacheFile);
        try {
            assertThat(SelectorCacheTestSupport.cache(reader).getSelectorsForDestination("queue://audit.events"))
                    .containsExactly("priority > 5");
            assertThat(SelectorCacheTestSupport.cache(reader).getSelectorsForDestination("queue://orders.events"))
                    .containsExactly("region = 'eu'");
        } finally {
            reader.stop();
        }
    }
}
