/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package net_sf_ehcache.ehcache_core;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;

import javax.transaction.RollbackException;
import javax.transaction.SystemException;
import javax.transaction.TransactionManager;
import javax.transaction.xa.XAException;
import javax.transaction.xa.XAResource;
import javax.transaction.xa.Xid;

import bitronix.tm.TransactionManagerServices;
import bitronix.tm.resource.ResourceRegistrar;
import net.sf.ehcache.transaction.manager.DefaultTransactionManagerLookup;
import net.sf.ehcache.transaction.xa.EhcacheXAResource;
import net.sf.ehcache.transaction.xa.XAExecutionListener;
import net.sf.ehcache.transaction.xa.XATransactionContext;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

public class DefaultTransactionManagerLookupTest {
    @TempDir
    Path temporaryDirectory;

    @BeforeAll
    static void disableUpdateChecks() {
        System.setProperty("net.sf.ehcache.skipUpdateCheck", "true");
    }

    @BeforeEach
    void configureBitronix() throws IOException {
        Path configurationFile = temporaryDirectory.resolve("bitronix.properties");
        Properties properties = new Properties();
        properties.setProperty("bitronix.tm.serverId", "ehcache-default-lookup-test");
        properties.setProperty("bitronix.tm.disableJmx", "true");
        properties.setProperty("bitronix.tm.journal", "null");
        try (OutputStream output = Files.newOutputStream(configurationFile)) {
            properties.store(output, "Bitronix configuration for DefaultTransactionManagerLookupTest");
        }
        System.setProperty("bitronix.tm.configuration", configurationFile.toString());
    }

    @AfterEach
    void shutdownBitronix() {
        try {
            if (TransactionManagerServices.isTransactionManagerRunning()) {
                TransactionManagerServices.getTransactionManager().shutdown();
            }
        } finally {
            System.clearProperty("bitronix.tm.configuration");
        }
    }

    @Test
    void registersAndUnregistersXaResourceWithDiscoveredBitronixManager() {
        DefaultTransactionManagerLookup lookup = new DefaultTransactionManagerLookup();

        TransactionManager transactionManager = lookup.getTransactionManager();

        RecordingEhcacheXAResource resource = new RecordingEhcacheXAResource("default-lookup-bitronix-cache");
        assertThat(transactionManager).isNotNull();
        assertThat(ResourceRegistrar.getResourcesUniqueNames()).doesNotContain(resource.getCacheName());

        lookup.register(resource);
        try {
            assertThat(ResourceRegistrar.getResourcesUniqueNames()).contains(resource.getCacheName());
            assertThat(ResourceRegistrar.findXAResourceHolder(resource)).isNotNull();
        } finally {
            lookup.unregister(resource);
        }

        assertThat(ResourceRegistrar.getResourcesUniqueNames()).doesNotContain(resource.getCacheName());
        assertThat(ResourceRegistrar.findXAResourceHolder(resource)).isNull();
    }

    private static final class RecordingEhcacheXAResource implements EhcacheXAResource {
        private final String cacheName;
        private int transactionTimeoutSeconds;

        private RecordingEhcacheXAResource(String cacheName) {
            this.cacheName = cacheName;
        }

        @Override
        public void addTwoPcExecutionListener(XAExecutionListener listener) {
        }

        @Override
        public String getCacheName() {
            return cacheName;
        }

        @Override
        public XATransactionContext createTransactionContext() throws SystemException, RollbackException {
            return null;
        }

        @Override
        public XATransactionContext getCurrentTransactionContext() {
            return null;
        }

        @Override
        public void commit(Xid xid, boolean onePhase) throws XAException {
        }

        @Override
        public void end(Xid xid, int flags) throws XAException {
        }

        @Override
        public void forget(Xid xid) throws XAException {
        }

        @Override
        public int getTransactionTimeout() throws XAException {
            return transactionTimeoutSeconds;
        }

        @Override
        public boolean isSameRM(XAResource xaResource) throws XAException {
            return xaResource == this;
        }

        @Override
        public int prepare(Xid xid) throws XAException {
            return XA_OK;
        }

        @Override
        public Xid[] recover(int flag) throws XAException {
            return new Xid[0];
        }

        @Override
        public void rollback(Xid xid) throws XAException {
        }

        @Override
        public boolean setTransactionTimeout(int seconds) throws XAException {
            transactionTimeoutSeconds = seconds;
            return true;
        }

        @Override
        public void start(Xid xid, int flags) throws XAException {
        }
    }
}
