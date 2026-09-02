/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_activemq.activemq_broker;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Path;

import org.apache.activemq.broker.BrokerService;
import org.apache.activemq.plugin.SubQueueSelectorCacheBroker;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.io.TempDir;

public class SubQueueSelectorCacheBrokerInnerSubSelectorClassObjectInputStreamTest {

    private static final String QUEUE_NAME = "selector.restore.orders";
    private static final String SELECTOR = "region = 'west'";

    @TempDir
    Path temporaryDirectory;

    @Test
    @Timeout(30)
    void restoresSelectorsPersistedByBrokerPlugin() throws Exception {
        Path cacheFile = temporaryDirectory.resolve("selectors.dat");
        BrokerService writer = SubQueueSelectorCacheBrokerTest.startBroker(
                "selector-cache-writer-broker", cacheFile);
        try {
            SubQueueSelectorCacheBrokerTest.addSelectorConsumer(writer, QUEUE_NAME, SELECTOR);
        } finally {
            writer.stop();
            writer.waitUntilStopped();
        }

        BrokerService reader = SubQueueSelectorCacheBrokerTest.startBroker(
                "selector-cache-reader-broker", cacheFile);
        try {
            SubQueueSelectorCacheBroker cacheBroker =
                    SubQueueSelectorCacheBrokerTest.selectorCache(reader);

            assertThat(cacheBroker.getSelectorsForDestination(
                            SubQueueSelectorCacheBrokerTest.qualifiedName(QUEUE_NAME)))
                    .containsExactly(SELECTOR);
        } finally {
            reader.stop();
            reader.waitUntilStopped();
        }
    }
}
