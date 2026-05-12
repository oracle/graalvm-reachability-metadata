/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_velocity.velocity;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;

import org.apache.commons.collections.ExtendedProperties;
import org.apache.velocity.runtime.RuntimeConstants;
import org.apache.velocity.runtime.RuntimeInstance;
import org.apache.velocity.runtime.RuntimeServices;
import org.apache.velocity.runtime.log.Log;
import org.apache.velocity.runtime.log.LogChute;
import org.apache.velocity.runtime.resource.ResourceManagerImpl;
import org.junit.jupiter.api.Test;

public class ResourceManagerImplTest {
    @Test
    void initializesDefaultResourceCacheWhenConfiguredCacheClassHasWrongType() throws Exception {
        ExtendedProperties configuration = new ExtendedProperties();
        configuration.setProperty(RuntimeConstants.RESOURCE_MANAGER_CACHE_CLASS, ResourceManagerImpl.class.getName());

        ConfiguredRuntimeInstance runtime = new ConfiguredRuntimeInstance(configuration);
        ResourceManagerImpl resourceManager = new ResourceManagerImpl();
        resourceManager.initialize(runtime);

        assertThat(resourceManager.getLoaderNameForResource("missing.vm")).isNull();
        assertThat(runtime.loggedMessages())
                .anySatisfy(message -> assertThat(message)
                        .contains("does not implement")
                        .contains("org.apache.velocity.runtime.resource.ResourceCache"));
    }

    private static final class ConfiguredRuntimeInstance extends RuntimeInstance {
        private final ExtendedProperties configuration;
        private final RecordingLogChute logChute;
        private final Log log;

        ConfiguredRuntimeInstance(ExtendedProperties configuration) {
            this.configuration = configuration;
            this.logChute = new RecordingLogChute();
            this.log = new Log(logChute);
        }

        List<String> loggedMessages() {
            return logChute.messages;
        }

        @Override
        public ExtendedProperties getConfiguration() {
            if (configuration == null) {
                return super.getConfiguration();
            }
            return configuration;
        }

        @Override
        public String getString(String key) {
            if (configuration == null) {
                return super.getString(key);
            }
            return configuration.getString(key);
        }

        @Override
        public boolean getBoolean(String key, boolean defaultValue) {
            if (configuration == null) {
                return super.getBoolean(key, defaultValue);
            }
            return configuration.getBoolean(key, defaultValue);
        }

        @Override
        public int getInt(String key, int defaultValue) {
            if (configuration == null) {
                return super.getInt(key, defaultValue);
            }
            return configuration.getInt(key, defaultValue);
        }

        @Override
        public Object getProperty(String key) {
            if (configuration == null) {
                return super.getProperty(key);
            }
            return configuration.getProperty(key);
        }

        @Override
        public Log getLog() {
            if (log == null) {
                return super.getLog();
            }
            return log;
        }

        @Override
        public void error(Object message) {
            getLog().error(message);
        }

        @Override
        public void warn(Object message) {
            getLog().warn(message);
        }

        @Override
        public void info(Object message) {
            getLog().info(message);
        }
    }

    private static final class RecordingLogChute implements LogChute {
        private final List<String> messages = new ArrayList<>();

        @Override
        public void init(RuntimeServices runtimeServices) {
        }

        @Override
        public void log(int level, String message) {
            messages.add(message);
        }

        @Override
        public void log(int level, String message, Throwable throwable) {
            messages.add(message);
        }

        @Override
        public boolean isLevelEnabled(int level) {
            return true;
        }
    }
}
