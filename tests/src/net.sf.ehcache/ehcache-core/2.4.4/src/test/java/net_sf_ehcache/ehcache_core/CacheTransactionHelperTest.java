/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package net_sf_ehcache.ehcache_core;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Properties;

import javax.transaction.HeuristicMixedException;
import javax.transaction.HeuristicRollbackException;
import javax.transaction.InvalidTransactionException;
import javax.transaction.NotSupportedException;
import javax.transaction.RollbackException;
import javax.transaction.Status;
import javax.transaction.SystemException;
import javax.transaction.Transaction;
import javax.transaction.TransactionManager;

import net.sf.ehcache.Cache;
import net.sf.ehcache.config.CacheConfiguration;
import net.sf.ehcache.transaction.manager.TransactionManagerLookup;
import net.sf.ehcache.transaction.xa.EhcacheXAResource;
import net.sf.ehcache.util.CacheTransactionHelper;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

public class CacheTransactionHelperTest {
    @BeforeAll
    static void disableUpdateChecks() {
        System.setProperty("net.sf.ehcache.skipUpdateCheck", "true");
    }

    @Test
    void xaCacheHelperBeginsReportsAndCommitsTransactionThroughLookup() {
        RecordingTransactionManager transactionManager = new RecordingTransactionManager();
        Cache cache = new Cache(new CacheConfiguration("xa-helper", 10)
                .transactionalMode(CacheConfiguration.TransactionalMode.XA)
                .eternal(true)
                .overflowToDisk(false));
        cache.setTransactionManagerLookup(new RecordingTransactionManagerLookup(transactionManager));

        assertThat(CacheTransactionHelper.isTransactionStarted(cache)).isFalse();

        CacheTransactionHelper.beginTransactionIfNeeded(cache);
        assertThat(CacheTransactionHelper.isTransactionStarted(cache)).isTrue();

        CacheTransactionHelper.commitTransactionIfNeeded(cache);
        assertThat(CacheTransactionHelper.isTransactionStarted(cache)).isFalse();

        assertThat(transactionManager.beginCalls).isEqualTo(1);
        assertThat(transactionManager.commitCalls).isEqualTo(1);
        assertThat(transactionManager.statusCalls).isEqualTo(3);
    }

    public static final class RecordingTransactionManager implements TransactionManager {
        private int status = Status.STATUS_NO_TRANSACTION;
        private int beginCalls;
        private int commitCalls;
        private int statusCalls;
        private int transactionTimeoutSeconds;

        @Override
        public void begin() throws NotSupportedException, SystemException {
            beginCalls++;
            status = Status.STATUS_ACTIVE;
        }

        @Override
        public void commit() throws RollbackException, HeuristicMixedException, HeuristicRollbackException,
                SecurityException, IllegalStateException, SystemException {
            commitCalls++;
            status = Status.STATUS_NO_TRANSACTION;
        }

        @Override
        public int getStatus() throws SystemException {
            statusCalls++;
            return status;
        }

        @Override
        public Transaction getTransaction() throws SystemException {
            return null;
        }

        @Override
        public void resume(Transaction transaction) throws InvalidTransactionException, IllegalStateException,
                SystemException {
            status = Status.STATUS_ACTIVE;
        }

        @Override
        public void rollback() throws IllegalStateException, SecurityException, SystemException {
            status = Status.STATUS_NO_TRANSACTION;
        }

        @Override
        public void setRollbackOnly() throws IllegalStateException, SystemException {
            status = Status.STATUS_MARKED_ROLLBACK;
        }

        @Override
        public void setTransactionTimeout(int seconds) throws SystemException {
            transactionTimeoutSeconds = seconds;
        }

        @Override
        public Transaction suspend() throws SystemException {
            status = Status.STATUS_NO_TRANSACTION;
            return null;
        }
    }

    private static final class RecordingTransactionManagerLookup implements TransactionManagerLookup {
        private final TransactionManager transactionManager;
        private int registrations;
        private int unregistrations;
        private Properties properties;

        private RecordingTransactionManagerLookup(TransactionManager manager) {
            this.transactionManager = manager;
        }

        @Override
        public TransactionManager getTransactionManager() {
            return transactionManager;
        }

        @Override
        public void register(EhcacheXAResource resource) {
            registrations++;
        }

        @Override
        public void unregister(EhcacheXAResource resource) {
            unregistrations++;
        }

        @Override
        public void setProperties(Properties lookupProperties) {
            this.properties = lookupProperties;
        }
    }
}
