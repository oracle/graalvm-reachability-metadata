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
import org.apache.activemq.store.PersistenceAdapter;
import org.apache.activemq.store.kahadb.KahaDBPersistenceAdapter;
import org.apache.activemq.store.kahadb.plist.PListStoreImpl;
import org.apache.activemq.store.memory.MemoryPersistenceAdapter;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.io.TempDir;

public class BrokerServiceTest {

    @TempDir
    Path temporaryDirectory;

    @Test
    @Timeout(30)
    void createsDefaultPersistentStores() throws Exception {
        BrokerService brokerService = newPersistentBroker("persistent-store-broker");

        try {
            PersistenceAdapter persistenceAdapter = brokerService.getPersistenceAdapter();

            assertThat(persistenceAdapter).isInstanceOf(KahaDBPersistenceAdapter.class);
            assertThat(brokerService.getTempDataStore()).isInstanceOf(PListStoreImpl.class);
            assertThat(brokerService.getJobSchedulerStore()).isNotNull();
        } finally {
            brokerService.stop();
        }
    }

    @Test
    @Timeout(30)
    void fallsBackToKahaDbJobSchedulerForUnsupportedPersistenceAdapter() throws Exception {
        BrokerService brokerService = newPersistentBroker("scheduler-fallback-broker");
        brokerService.setPersistenceAdapter(new MemoryPersistenceAdapter());

        try {
            assertThat(brokerService.getJobSchedulerStore()).isNotNull();
        } finally {
            brokerService.stop();
        }
    }

    private BrokerService newPersistentBroker(String brokerName) {
        BrokerService brokerService = new BrokerService();
        brokerService.setBrokerName(brokerName);
        brokerService.setDataDirectoryFile(temporaryDirectory.resolve(brokerName).toFile());
        brokerService.setUseJmx(false);
        brokerService.setUseShutdownHook(false);
        brokerService.setPersistent(true);
        return brokerService;
    }
}
