/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com.mchange.v1.cachedstore;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class CachedStoreFactoryTest {
    @Test
    void createSynchronousCleanupSoftKeyCachedStoreCachesResolvedValues() throws Exception {
        RecordingManager manager = new RecordingManager();
        TweakableCachedStore store = CachedStoreFactory.createSynchronousCleanupSoftKeyCachedStore(manager);

        Object first = store.find("alpha");
        Object second = store.find("alpha");

        assertThat(first).isEqualTo("value-alpha-1");
        assertThat(second).isEqualTo(first);
        assertThat(store.getCachedValue("alpha")).isEqualTo(first);
        assertThat(manager.recreateCalls()).isEqualTo(1);
        assertThat(cachedKeysOf(store)).containsExactly("alpha");
    }

    @Test
    void createSynchronousCleanupSoftKeyCachedStoreDelegatesCacheMutationsThroughTheProxy() throws Exception {
        RecordingManager manager = new RecordingManager();
        TweakableCachedStore store = CachedStoreFactory.createSynchronousCleanupSoftKeyCachedStore(manager);

        store.setCachedValue("beta", "manual-value");

        assertThat(store.getCachedValue("beta")).isEqualTo("manual-value");
        assertThat(cachedKeysOf(store)).containsExactly("beta");

        store.removeFromCache("beta");

        assertThat(store.getCachedValue("beta")).isNull();
        assertThat(cachedKeysOf(store)).isEmpty();
    }

    private static List<Object> cachedKeysOf(TweakableCachedStore store) throws Exception {
        List<Object> keys = new ArrayList<>();
        Iterator iterator = store.cachedKeys();
        while (iterator.hasNext()) {
            keys.add(iterator.next());
        }
        return keys;
    }

    private static final class RecordingManager implements CachedStore.Manager {
        private final AtomicInteger recreateCalls = new AtomicInteger();

        @Override
        public boolean isDirty(Object key, Object cached) {
            return false;
        }

        @Override
        public Object recreateFromKey(Object key) {
            int call = recreateCalls.incrementAndGet();
            return "value-" + key + "-" + call;
        }

        int recreateCalls() {
            return recreateCalls.get();
        }
    }
}
