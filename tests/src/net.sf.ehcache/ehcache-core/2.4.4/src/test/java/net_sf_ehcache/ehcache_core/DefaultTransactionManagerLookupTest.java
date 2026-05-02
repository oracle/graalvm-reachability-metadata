/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package net_sf_ehcache.ehcache_core;

import static org.assertj.core.api.Assertions.assertThat;

import javax.transaction.RollbackException;
import javax.transaction.SystemException;
import javax.transaction.TransactionManager;
import javax.transaction.xa.XAException;
import javax.transaction.xa.XAResource;
import javax.transaction.xa.Xid;

import bitronix.tm.TransactionManagerServices;
import bitronix.tm.resource.ehcache.EhCacheXAResourceProducer;
import net.sf.ehcache.transaction.manager.DefaultTransactionManagerLookup;
import net.sf.ehcache.transaction.xa.EhcacheXAResource;
import net.sf.ehcache.transaction.xa.XAExecutionListener;
import net.sf.ehcache.transaction.xa.XATransactionContext;
import org.junit.jupiter.api.Test;

public class DefaultTransactionManagerLookupTest {
    @Test
    void registersAndUnregistersEhcacheXaResourceWithBitronixProducer() {
        assertThat(TransactionManagerServices.class.getName()).isEqualTo("bitronix.tm.TransactionManagerServices");
        assertThat(EhCacheXAResourceProducer.class.getName())
                .isEqualTo("bitronix.tm.resource.ehcache.EhCacheXAResourceProducer");
        EhCacheXAResourceProducer.reset();

        DefaultTransactionManagerLookup lookup = new DefaultTransactionManagerLookup();
        TransactionManager transactionManager = lookup.getTransactionManager();
        TestEhcacheXAResource resource = new TestEhcacheXAResource("bitronix-backed-cache");

        lookup.register(resource);
        lookup.unregister(resource);

        assertThat(transactionManager).isSameAs(TransactionManagerServices.getTransactionManager());
        assertThat(EhCacheXAResourceProducer.getRegisteredUniqueNames()).containsExactly(resource.getCacheName());
        assertThat(EhCacheXAResourceProducer.getRegisteredResources()).containsExactly(resource);
        assertThat(EhCacheXAResourceProducer.getUnregisteredUniqueNames()).containsExactly(resource.getCacheName());
        assertThat(EhCacheXAResourceProducer.getUnregisteredResources()).containsExactly(resource);
    }

    private static final class TestEhcacheXAResource implements EhcacheXAResource {
        private final String cacheName;

        private TestEhcacheXAResource(String cacheName) {
            this.cacheName = cacheName;
        }

        @Override
        public String getCacheName() {
            return cacheName;
        }

        @Override
        public void addTwoPcExecutionListener(XAExecutionListener listener) {
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
            return 0;
        }

        @Override
        public boolean isSameRM(XAResource xaResource) throws XAException {
            return xaResource == this;
        }

        @Override
        public int prepare(Xid xid) throws XAException {
            return XAResource.XA_OK;
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
            return false;
        }

        @Override
        public void start(Xid xid, int flags) throws XAException {
        }
    }
}
