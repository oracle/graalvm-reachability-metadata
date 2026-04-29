/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_seata.seata_all;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Collections;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import org.apache.seata.common.loader.EnhancedServiceLoader;
import org.apache.seata.common.loader.LoadLevel;
import org.apache.seata.common.loader.Scope;
import org.apache.seata.config.Configuration;
import org.apache.seata.config.ConfigurationChangeEvent;
import org.apache.seata.config.ConfigurationChangeListener;
import org.apache.seata.config.ConfigurationFactory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

public class ConfigurationFactoryInnerOldConfigurationInvocationHandlerTest {
    private static final String CONFIG_TYPE_PROPERTY = "config.type";

    @BeforeEach
    void loadLegacyConfigurationProvider() {
        System.setProperty(CONFIG_TYPE_PROPERTY, "oldcompat");
        EnhancedServiceLoader.unload(io.seata.config.ConfigurationProvider.class);
        LegacyConfigurationProvider.CONFIGURATION.clear();
        ConfigurationFactory.reload();
    }

    @Test
    void adaptsLegacyConfigurationMethodsAndListeners() {
        Configuration configuration = ConfigurationFactory.getInstance();

        String value = configuration.getConfig("simple.key", "fallback");

        assertThat(value).isEqualTo("legacy-simple.key");
        assertThat(LegacyConfigurationProvider.CONFIGURATION.requestedDataIds()).contains("simple.key");

        RecordingConfigurationChangeListener listener = new RecordingConfigurationChangeListener();
        configuration.addConfigListener("listener.key", listener);

        Set<ConfigurationChangeListener> listeners = configuration.getConfigListeners("listener.key");

        assertThat(listeners).contains(listener);
    }

    @LoadLevel(name = "oldcompat", scope = Scope.PROTOTYPE)
    public static class LegacyConfigurationProvider implements io.seata.config.ConfigurationProvider {
        static final LegacyConfiguration CONFIGURATION = new LegacyConfiguration();

        @Override
        public io.seata.config.Configuration provide() {
            return CONFIGURATION;
        }
    }

    public static class LegacyConfiguration extends io.seata.config.AbstractConfiguration {
        private final Map<String, String> requestedConfigs = new ConcurrentHashMap<>();
        private final Map<String, Set<io.seata.config.ConfigurationChangeListener>> listeners =
                new ConcurrentHashMap<>();

        void clear() {
            requestedConfigs.clear();
            listeners.clear();
        }

        Set<String> requestedDataIds() {
            return requestedConfigs.keySet();
        }

        @Override
        public String getLatestConfig(String dataId, String defaultValue, long timeoutMills) {
            String value = "legacy-" + dataId;
            requestedConfigs.put(dataId, value);
            return value;
        }

        @Override
        public boolean putConfig(String dataId, String content, long timeoutMills) {
            requestedConfigs.put(dataId, content);
            return true;
        }

        @Override
        public boolean putConfigIfAbsent(String dataId, String content, long timeoutMills) {
            requestedConfigs.putIfAbsent(dataId, content);
            return true;
        }

        @Override
        public boolean removeConfig(String dataId, long timeoutMills) {
            requestedConfigs.remove(dataId);
            return true;
        }

        @Override
        public void addConfigListener(String dataId, io.seata.config.ConfigurationChangeListener listener) {
            listeners.computeIfAbsent(dataId, ignored -> ConcurrentHashMap.newKeySet()).add(listener);
        }

        @Override
        public void removeConfigListener(String dataId, io.seata.config.ConfigurationChangeListener listener) {
            Set<io.seata.config.ConfigurationChangeListener> dataIdListeners = listeners.get(dataId);
            if (dataIdListeners != null) {
                dataIdListeners.remove(listener);
            }
        }

        @Override
        public Set<io.seata.config.ConfigurationChangeListener> getConfigListeners(String dataId) {
            return listeners.getOrDefault(dataId, Collections.emptySet());
        }

        @Override
        public String getTypeName() {
            return "oldcompat";
        }
    }

    private static class RecordingConfigurationChangeListener implements ConfigurationChangeListener {
        @Override
        public void onChangeEvent(ConfigurationChangeEvent event) {
        }
    }
}
