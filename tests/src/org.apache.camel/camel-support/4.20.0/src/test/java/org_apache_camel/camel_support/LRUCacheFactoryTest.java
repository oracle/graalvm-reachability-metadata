/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_camel.camel_support;

import java.util.Map;
import java.util.function.Consumer;

import org.apache.camel.support.DefaultLRUCacheFactory;
import org.apache.camel.support.LRUCacheFactory;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class LRUCacheFactoryTest extends LRUCacheFactory {
    private final LRUCacheFactory delegate = new DefaultLRUCacheFactory();

    @AfterEach
    void restoreDefaultFactory() {
        LRUCacheFactory.setLRUCacheFactory(new DefaultLRUCacheFactory());
    }

    @Test
    void discoversFactoryFromServiceDescriptor() {
        LRUCacheFactory.setLRUCacheFactory(null);

        LRUCacheFactory factory = LRUCacheFactory.getInstance();
        Map<String, String> cache = LRUCacheFactory.newLRUCache(2);
        cache.put("first", "one");

        assertThat(factory).isInstanceOf(LRUCacheFactoryTest.class);
        assertThat(cache).containsEntry("first", "one");
    }

    @Override
    public <K, V> Map<K, V> createLRUCache(int maximumCacheSize) {
        return delegate.createLRUCache(maximumCacheSize);
    }

    @Override
    public <K, V> Map<K, V> createLRUCache(int maximumCacheSize, Consumer<V> onEvict) {
        return delegate.createLRUCache(maximumCacheSize, onEvict);
    }

    @Override
    public <K, V> Map<K, V> createLRUCache(int initialCapacity, int maximumCacheSize) {
        return delegate.createLRUCache(initialCapacity, maximumCacheSize);
    }

    @Override
    public <K, V> Map<K, V> createLRUCache(int initialCapacity, int maximumCacheSize, boolean stopOnEviction) {
        return delegate.createLRUCache(initialCapacity, maximumCacheSize, stopOnEviction);
    }

    @Override
    public <K, V> Map<K, V> createLRUSoftCache(int maximumCacheSize) {
        return delegate.createLRUSoftCache(maximumCacheSize);
    }

    @Override
    public <K, V> Map<K, V> createLRUSoftCache(int initialCapacity, int maximumCacheSize) {
        return delegate.createLRUSoftCache(initialCapacity, maximumCacheSize);
    }

    @Override
    public <K, V> Map<K, V> createLRUSoftCache(int initialCapacity, int maximumCacheSize, boolean stopOnEviction) {
        return delegate.createLRUSoftCache(initialCapacity, maximumCacheSize, stopOnEviction);
    }

    @Override
    @Deprecated(since = "4.2.0")
    public <K, V> Map<K, V> createLRUWeakCache(int maximumCacheSize) {
        return delegate.createLRUWeakCache(maximumCacheSize);
    }

    @Override
    @Deprecated(since = "4.2.0")
    public <K, V> Map<K, V> createLRUWeakCache(int initialCapacity, int maximumCacheSize) {
        return delegate.createLRUWeakCache(initialCapacity, maximumCacheSize);
    }

    @Override
    @Deprecated(since = "4.2.0")
    public <K, V> Map<K, V> createLRUWeakCache(int initialCapacity, int maximumCacheSize, boolean stopOnEviction) {
        return delegate.createLRUWeakCache(initialCapacity, maximumCacheSize, stopOnEviction);
    }
}
