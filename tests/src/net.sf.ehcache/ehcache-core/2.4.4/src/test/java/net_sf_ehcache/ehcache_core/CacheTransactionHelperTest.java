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
import net.sf.ehcache.config.CacheConfiguration.TransactionalMode;
import net.sf.ehcache.transaction.manager.TransactionManagerLookup;
import net.sf.ehcache.transaction.xa.EhcacheXAResource;
import net.sf.ehcache.util.CacheTransactionHelper;

import org.junit.jupiter.api.Test;

public class CacheTransactionHelperTest {
    @Test
    void delegatesXaTransactionLifecycleThroughTransactionManagerLookup() {
        RecordingTransactionManager transactionManager = new RecordingTransactionManager();
        Cache cache = newXaCache(transactionManager);

        assertThat(CacheTransactionHelper.isTransactionStarted(cache)).isFalse();

        CacheTransactionHelper.beginTransactionIfNeeded(cache);

        assertThat(CacheTransactionHelper.isTransactionStarted(cache)).isTrue();
        assertThat(transactionManager.getBeginCount()).isEqualTo(1);

        CacheTransactionHelper.commitTransactionIfNeeded(cache);

        assertThat(CacheTransactionHelper.isTransactionStarted(cache)).isFalse();
        assertThat(transactionManager.getCommitCount()).isEqualTo(1);
    }

    private static Cache newXaCache(RecordingTransactionManager transactionManager) {
        CacheConfiguration configuration = new CacheConfiguration("cache-transaction-helper-coverage", 10)
                .transactionalMode(TransactionalMode.XA);
        Cache cache = new Cache(configuration);
        cache.setTransactionManagerLookup(new FixedTransactionManagerLookup(transactionManager));
        return cache;
    }

    private static final class FixedTransactionManagerLookup implements TransactionManagerLookup {
        private final TransactionManager transactionManager;

        private FixedTransactionManagerLookup(TransactionManager transactionManager) {
            this.transactionManager = transactionManager;
        }

        @Override
        public TransactionManager getTransactionManager() {
            return transactionManager;
        }

        @Override
        public void register(EhcacheXAResource resource) {
        }

        @Override
        public void unregister(EhcacheXAResource resource) {
        }

        @Override
        public void setProperties(Properties properties) {
        }
    }

    public static final class RecordingTransactionManager implements TransactionManager {
        private int status = Status.STATUS_NO_TRANSACTION;
        private int beginCount;
        private int commitCount;

        @Override
        public void begin() throws NotSupportedException, SystemException {
            status = Status.STATUS_ACTIVE;
            beginCount++;
        }

        @Override
        public void commit() throws RollbackException, HeuristicMixedException, HeuristicRollbackException,
                SecurityException, IllegalStateException, SystemException {
            status = Status.STATUS_NO_TRANSACTION;
            commitCount++;
        }

        @Override
        public int getStatus() throws SystemException {
            return status;
        }

        @Override
        public Transaction getTransaction() throws SystemException {
            return null;
        }

        @Override
        public void resume(Transaction transaction) throws InvalidTransactionException, IllegalStateException,
                SystemException {
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
        }

        @Override
        public Transaction suspend() throws SystemException {
            return null;
        }

        private int getBeginCount() {
            return beginCount;
        }

        private int getCommitCount() {
            return commitCount;
        }
    }
}
