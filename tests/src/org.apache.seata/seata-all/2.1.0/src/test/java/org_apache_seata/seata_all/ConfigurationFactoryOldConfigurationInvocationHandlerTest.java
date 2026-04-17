/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_seata.seata_all;

import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import io.seata.config.AbstractConfiguration;
import io.seata.config.ConfigurationProvider;
import org.apache.seata.common.loader.EnhancedServiceLoader;
import org.apache.seata.common.loader.LoadLevel;
import org.apache.seata.config.Configuration;
import org.apache.seata.config.ConfigurationCache;
import org.apache.seata.config.ConfigurationChangeEvent;
import org.apache.seata.config.ConfigurationChangeListener;
import org.apache.seata.config.ConfigurationFactory;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

public class ConfigurationFactoryOldConfigurationInvocationHandlerTest {
    private static final String CONFIG_TYPE_PROPERTY = "config.type";
    private static final String TEST_PROVIDER_NAME = "legacy-bridge-test";

    private String previousConfigType;

    @AfterEach
    void tearDown() {
        ConfigurationCache.clear();
        EnhancedServiceLoader.unloadAll();
        if (previousConfigType == null) {
            System.clearProperty(CONFIG_TYPE_PROPERTY);
        } else {
            System.setProperty(CONFIG_TYPE_PROPERTY, previousConfigType);
        }
    }

    @Test
    void configurationFactoryBridgesLegacyConfigurationProviders() {
        previousConfigType = System.getProperty(CONFIG_TYPE_PROPERTY);
        System.setProperty(CONFIG_TYPE_PROPERTY, TEST_PROVIDER_NAME);
        EnhancedServiceLoader.unloadAll();
        ConfigurationFactory.reload();

        Configuration configuration = ConfigurationFactory.getInstance();
        TrackingConfigurationChangeListener listener = new TrackingConfigurationChangeListener();

        assertThat(configuration.putConfig("sample.data-id", "configured-value")).isTrue();
        assertThat(configuration.putConfigIfAbsent("sample.data-id", "other-value")).isFalse();
        configuration.addConfigListener("sample.data-id", listener);

        assertThat(configuration.getConfigListeners("sample.data-id")).contains(listener);
        assertThatCode(() -> configuration.removeConfigListener("sample.data-id", listener)).doesNotThrowAnyException();
    }

    public static final class TrackingConfigurationChangeListener implements ConfigurationChangeListener {
        @Override
        public void onChangeEvent(ConfigurationChangeEvent event) {
        }
    }

    @LoadLevel(name = TEST_PROVIDER_NAME)
    public static final class TestOldConfigurationProvider implements ConfigurationProvider {
        @Override
        public io.seata.config.Configuration provide() {
            return new TestOldConfiguration();
        }
    }

    public static final class TestOldConfiguration extends AbstractConfiguration {
        private final Map<String, String> values = new ConcurrentHashMap<>();
        private final Map<String, Set<io.seata.config.ConfigurationChangeListener>> listenersByDataId =
                new ConcurrentHashMap<>();

        @Override
        public String getTypeName() {
            return TEST_PROVIDER_NAME;
        }

        @Override
        public String getLatestConfig(String dataId, String defaultValue, long timeoutMills) {
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
        public void addConfigListener(String dataId, io.seata.config.ConfigurationChangeListener listener) {
            listenersByDataId.computeIfAbsent(dataId, ignored -> ConcurrentHashMap.newKeySet()).add(listener);
        }

        @Override
        public void removeConfigListener(String dataId, io.seata.config.ConfigurationChangeListener listener) {
            Set<io.seata.config.ConfigurationChangeListener> listeners = listenersByDataId.get(dataId);
            if (listeners != null) {
                listeners.remove(listener);
            }
        }

        @Override
        public Set<io.seata.config.ConfigurationChangeListener> getConfigListeners(String dataId) {
            Set<io.seata.config.ConfigurationChangeListener> listeners = listenersByDataId.get(dataId);
            if (listeners == null) {
                return null;
            }
            return new LinkedHashSet<>(listeners);
        }
    }
}
