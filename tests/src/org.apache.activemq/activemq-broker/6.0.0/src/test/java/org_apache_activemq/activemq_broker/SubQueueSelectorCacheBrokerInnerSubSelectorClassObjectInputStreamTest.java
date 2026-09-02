/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_activemq.activemq_broker;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.ObjectOutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

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
        String destination = "queue://orders";
        String selector = "region = 'west'";
        writeSelectorCache(cacheFile, destination, selector);
        SubQueueSelectorCacheBroker reader = null;

        try {
            reader = new SubQueueSelectorCacheBroker(brokerService.getBroker(), cacheFile.toFile());

            assertThat(reader.getSelectorsForDestination(destination)).containsExactly(selector);
        } finally {
            if (reader != null) {
                reader.stop();
            }
            brokerService.stop();
        }
    }

    private static void writeSelectorCache(Path cacheFile, String destination, String selector)
            throws Exception {
        ConcurrentMap<String, Set<String>> cache = new ConcurrentHashMap<>();
        cache.put(destination, new HashSet<>(Set.of(selector)));
        try (ObjectOutputStream output = new ObjectOutputStream(Files.newOutputStream(cacheFile))) {
            output.writeObject(cache);
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
