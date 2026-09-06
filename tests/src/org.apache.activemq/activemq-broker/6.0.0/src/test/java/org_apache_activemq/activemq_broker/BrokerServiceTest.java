/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_activemq.activemq_broker;

import org.apache.activemq.broker.BrokerService;
import org.apache.activemq.store.PersistenceAdapter;
import org.apache.activemq.store.memory.MemoryPersistenceAdapter;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

public class BrokerServiceTest {

    @TempDir
    Path temporaryDirectory;

    @Test
    void lazilyCreatesPersistentStores() throws Exception {
        BrokerService defaultStoreBroker = persistentBroker("default-store");
        PersistenceAdapter persistenceAdapter = defaultStoreBroker.getPersistenceAdapter();

        assertThat(persistenceAdapter.getClass().getName())
                .isEqualTo("org.apache.activemq.store.kahadb.KahaDBPersistenceAdapter");
        assertThat(defaultStoreBroker.getTempDataStore()).isNotNull();

        BrokerService schedulerBroker = persistentBroker("scheduler-store");
        schedulerBroker.setSchedulerSupport(true);
        schedulerBroker.setPersistenceAdapter(new MemoryPersistenceAdapter());

        assertThat(schedulerBroker.getJobSchedulerStore()).isNotNull();
    }

    private BrokerService persistentBroker(String directoryName) {
        BrokerService broker = new BrokerService();
        broker.setPersistent(true);
        broker.setDataDirectoryFile(temporaryDirectory.resolve(directoryName).toFile());
        broker.setTmpDataDirectory(temporaryDirectory.resolve(directoryName + "-tmp").toFile());
        broker.setSchedulerDirectoryFile(temporaryDirectory.resolve(directoryName + "-scheduler").toFile());
        return broker;
    }
}
