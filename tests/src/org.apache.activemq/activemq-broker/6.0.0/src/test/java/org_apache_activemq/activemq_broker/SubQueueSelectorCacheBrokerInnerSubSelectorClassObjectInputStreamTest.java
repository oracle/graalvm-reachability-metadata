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

    @TempDir
    Path temporaryDirectory;

    @Test
    @Timeout(30)
    void restoresPersistedSelectorCache() throws Exception {
        BrokerService brokerService = startBroker("selector-cache-restore-broker");
        Path cacheFile = temporaryDirectory.resolve("selectors.dat");
        SubQueueSelectorCacheBroker writer = null;
        SubQueueSelectorCacheBroker reader = null;

        try {
            writer = new SubQueueSelectorCacheBroker(brokerService.getBroker(), cacheFile.toFile());
            writer.stop();
            writer = null;

            reader = new SubQueueSelectorCacheBroker(brokerService.getBroker(), cacheFile.toFile());
            assertThat(reader.getSelectorsForDestination("queue://orders")).isEmpty();
        } finally {
            if (reader != null) {
                reader.stop();
            }
            if (writer != null) {
                writer.stop();
            }
            brokerService.stop();
        }
    }

    private static BrokerService startBroker(String name) throws Exception {
        BrokerService brokerService = new BrokerService();
        brokerService.setBrokerName(name);
        brokerService.setPersistent(false);
        brokerService.setUseJmx(false);
        brokerService.setUseShutdownHook(false);
        brokerService.start();
        return brokerService;
    }
}
