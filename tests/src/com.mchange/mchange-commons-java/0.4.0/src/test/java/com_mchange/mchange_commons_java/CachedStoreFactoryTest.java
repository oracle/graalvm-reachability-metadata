/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_mchange.mchange_commons_java;

import com.mchange.v1.cachedstore.CachedStore;
import com.mchange.v1.cachedstore.CachedStoreException;
import com.mchange.v1.cachedstore.CachedStoreFactory;
import com.mchange.v1.cachedstore.TweakableCachedStore;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class CachedStoreFactoryTest {
    @Test
    void createSynchronousCleanupSoftKeyCachedStoreCachesAndRefreshesValues() throws CachedStoreException {
        CountingManager manager = new CountingManager();
        TweakableCachedStore store = CachedStoreFactory.createSynchronousCleanupSoftKeyCachedStore(manager);

        CacheValue first = (CacheValue) store.find("alpha");
        CacheValue second = (CacheValue) store.find("alpha");
        manager.markDirty();
        CacheValue refreshed = (CacheValue) store.find("alpha");

        assertThat(first.sequence).isEqualTo(1);
        assertThat(second).isSameAs(first);
        assertThat(refreshed.sequence).isEqualTo(2);
        assertThat(refreshed).isNotSameAs(first);
        assertThat(manager.recreateCalls).isEqualTo(2);
        assertThat(manager.dirtyChecks).isEqualTo(2);
    }

    private static final class CountingManager implements CachedStore.Manager {
        private int recreateCalls;
        private int dirtyChecks;
        private boolean dirty;

        @Override
        public boolean isDirty(Object key, Object cached) {
            dirtyChecks++;
            return dirty;
        }

        @Override
        public Object recreateFromKey(Object key) {
            recreateCalls++;
            dirty = false;
            return new CacheValue(String.valueOf(key), recreateCalls);
        }

        void markDirty() {
            dirty = true;
        }
    }

    private static final class CacheValue {
        private final String key;
        private final int sequence;

        private CacheValue(String key, int sequence) {
            this.key = key;
            this.sequence = sequence;
        }

        @Override
        public String toString() {
            return key + ":" + sequence;
        }
    }
}
