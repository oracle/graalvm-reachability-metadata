/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_seata.seata_all;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;

import org.apache.seata.config.AbstractConfiguration;
import org.apache.seata.config.Configuration;
import org.apache.seata.config.ConfigurationCache;
import org.apache.seata.config.ConfigurationChangeEvent;
import org.apache.seata.config.ConfigurationChangeListener;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class ConfigurationCacheTest {
    @AfterEach
    void tearDown() {
        ConfigurationCache.clear();
    }

    @Test
    void proxyCachesGetterResultsAndRefreshesThemAfterChangeEvents() throws Exception {
        String dataId = "configuration.cache.test." + UUID.randomUUID();
        CountingConfiguration originalConfiguration = new CountingConfiguration();
        Configuration proxiedConfiguration = ConfigurationCache.getInstance().proxy(originalConfiguration);

        originalConfiguration.putConfig(dataId, "7");

        assertThat(proxiedConfiguration.getInt(dataId)).isEqualTo(7);
        assertThat(originalConfiguration.getLatestConfigInvocationCount()).isEqualTo(1);
        assertThat(originalConfiguration.getAddedListenerDataIds()).containsExactly(dataId);

        originalConfiguration.putConfig(dataId, "9");

        assertThat(proxiedConfiguration.getInt(dataId)).isEqualTo(7);
        assertThat(originalConfiguration.getLatestConfigInvocationCount()).isEqualTo(1);

        originalConfiguration.publishChange(dataId, "9");

        assertThat(proxiedConfiguration.getInt(dataId)).isEqualTo(9);
        assertThat(originalConfiguration.getLatestConfigInvocationCount()).isEqualTo(1);
    }

    private static final class CountingConfiguration extends AbstractConfiguration {
        private final Map<String, String> values = new ConcurrentHashMap<>();
        private final Map<String, Set<ConfigurationChangeListener>> listenersByDataId = new ConcurrentHashMap<>();
        private final List<String> addedListenerDataIds = new CopyOnWriteArrayList<>();
        private final AtomicInteger getLatestConfigInvocationCount = new AtomicInteger();

        @Override
        public String getTypeName() {
            return "test";
        }

        @Override
        public String getLatestConfig(String dataId, String defaultValue, long timeoutMills) {
            getLatestConfigInvocationCount.incrementAndGet();
            return values.getOrDefault(dataId, defaultValue);
        }

        @Override
        public boolean putConfig(String dataId, String content, long timeoutMills) {
            values.put(dataId, content);
            return true;
        }

        @Override
        public boolean putConfigIfAbsent(String dataId, String content, long timeoutMills) {
            return values.putIfAbsent(dataId, content) == null;
        }

        @Override
        public boolean removeConfig(String dataId, long timeoutMills) {
            return values.remove(dataId) != null;
        }

        @Override
        public void addConfigListener(String dataId, ConfigurationChangeListener listener) {
            addedListenerDataIds.add(dataId);
            listenersByDataId.computeIfAbsent(dataId, ignored -> ConcurrentHashMap.newKeySet()).add(listener);
        }

        @Override
        public void removeConfigListener(String dataId, ConfigurationChangeListener listener) {
            Set<ConfigurationChangeListener> listeners = listenersByDataId.get(dataId);
            if (listeners != null) {
                listeners.remove(listener);
            }
        }

        @Override
        public Set<ConfigurationChangeListener> getConfigListeners(String dataId) {
            return listenersByDataId.get(dataId);
        }

        int getLatestConfigInvocationCount() {
            return getLatestConfigInvocationCount.get();
        }

        List<String> getAddedListenerDataIds() {
            return addedListenerDataIds;
        }

        void publishChange(String dataId, String newValue) {
            Set<ConfigurationChangeListener> listeners = listenersByDataId.get(dataId);
            if (listeners == null) {
                return;
            }
            ConfigurationChangeEvent event = new ConfigurationChangeEvent(dataId, newValue);
            for (ConfigurationChangeListener listener : listeners) {
                listener.onChangeEvent(event);
            }
        }
    }
}
