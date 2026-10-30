/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_activemq.activemq_broker;

import org.apache.activemq.broker.BrokerService;
import org.apache.activemq.store.PersistenceAdapter;
import org.apache.activemq.store.PListStore;
import org.apache.activemq.store.memory.MemoryPersistenceAdapter;
import org.apache.activemq.broker.scheduler.JobSchedulerStore;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.security.Provider;
import java.security.Security;

import static org.assertj.core.api.Assertions.assertThat;

@Timeout(60)
public class BrokerServiceTest {

    @TempDir
    Path temporaryDirectory;

    @Test
    void registersAvailableSecurityProvider() {
        new BrokerService();
        Provider provider = Security.getProvider("BC");
        assertThat(provider).isNotNull();
        assertThat(provider.getService("Cipher", "AES")).isNotNull();
    }

    @Test
    void configuresPersistentStoresLazily() throws Exception {
        BrokerService broker = persistentBroker("default-store");
        try {
            PersistenceAdapter persistence = broker.getPersistenceAdapter();
            assertThat(persistence.getClass().getName())
                    .isEqualTo("org.apache.activemq.store.kahadb.KahaDBPersistenceAdapter");
            assertThat(persistence.getDirectory()).isEqualTo(
                    broker.getBrokerDataDirectory().toPath().resolve("KahaDB").toFile());
            assertThat(broker.getPersistenceAdapter()).isSameAs(persistence);
            PListStore temporary = broker.getTempDataStore();
            assertThat(temporary.getDirectory()).isEqualTo(broker.getTmpDataDirectory());
            assertThat(broker.getTempDataStore()).isSameAs(temporary);
        } finally {
            broker.stop();
        }

        BrokerService schedulerBroker = persistentBroker("scheduler-store");
        schedulerBroker.setSchedulerSupport(true);
        schedulerBroker.setPersistenceAdapter(new MemoryPersistenceAdapter());
        try {
            JobSchedulerStore scheduler = schedulerBroker.getJobSchedulerStore();
            assertThat(scheduler).isNotNull();
            assertThat(scheduler.getDirectory()).isEqualTo(schedulerBroker.getSchedulerDirectoryFile());
            assertThat(schedulerBroker.getJobSchedulerStore()).isSameAs(scheduler);
        } finally {
            schedulerBroker.stop();
        }
    }

    private BrokerService persistentBroker(String directoryName) {
        BrokerService broker = new BrokerService();
        broker.setPersistent(true);
        broker.setUseJmx(false);
        broker.setUseShutdownHook(false);
        broker.setDataDirectoryFile(temporaryDirectory.resolve(directoryName).toFile());
        broker.setTmpDataDirectory(temporaryDirectory.resolve(directoryName + "-tmp").toFile());
        broker.setSchedulerDirectoryFile(temporaryDirectory.resolve(directoryName + "-scheduler").toFile());
        return broker;
    }
}
