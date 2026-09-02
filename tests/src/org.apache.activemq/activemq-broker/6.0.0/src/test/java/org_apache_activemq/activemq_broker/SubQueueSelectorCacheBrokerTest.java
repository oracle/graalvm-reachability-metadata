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

import org.apache.activemq.broker.BrokerService;
import org.apache.activemq.plugin.SubQueueSelectorCacheBroker;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.io.TempDir;

public class SubQueueSelectorCacheBrokerTest {

    @TempDir
    Path temporaryDirectory;

    @Test
    @Timeout(30)
    void persistsSelectorCacheOnShutdown() throws Exception {
        BrokerService brokerService = startBroker("selector-cache-persist-broker");
        Path cacheFile = temporaryDirectory.resolve("selectors.dat");
        SubQueueSelectorCacheBroker cacheBroker = null;

        try {
            cacheBroker = new SubQueueSelectorCacheBroker(
                    brokerService.getBroker(), cacheFile.toFile());
            cacheBroker.stop();
            cacheBroker = null;

            assertThat(cacheFile).isRegularFile();
            assertThat(Files.size(cacheFile)).isPositive();
        } finally {
            if (cacheBroker != null) {
                cacheBroker.stop();
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
