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
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.io.TempDir;

public class SubQueueSelectorCacheBrokerInnerSubSelectorClassObjectInputStreamTest {

    @TempDir
    Path temporaryDirectory;

    @Test
    @Timeout(30)
    void resolvesAllowedClassesFromPersistedSelectorCache() throws Exception {
        Path cacheFile = temporaryDirectory.resolve("allowed-selectors.dat");
        BrokerService writer = SubQueueSelectorCacheBrokerTest.startBroker(cacheFile);
        try {
            SubQueueSelectorCacheBrokerTest.addSelectorConsumer(writer);
        } finally {
            writer.stop();
        }

        BrokerService reader = SubQueueSelectorCacheBrokerTest.startBroker(cacheFile);
        try {
            assertThat(SubQueueSelectorCacheBrokerTest.selectorCache(reader)
                            .getSelectorsForDestination(
                                    SubQueueSelectorCacheBrokerTest.qualifiedQueueName()))
                    .containsExactly(SubQueueSelectorCacheBrokerTest.SELECTOR);
        } finally {
            reader.stop();
        }
    }
}
