/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_logging_log4j.log4j_1_2_api;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Properties;

import org.apache.log4j.config.PropertySetter;
import org.apache.log4j.spi.OptionHandler;
import org.junit.jupiter.api.Test;

public class PropertySetterTest {

    @Test
    void setsBeanPropertiesFromStringValues() {
        ConfigurableTarget target = new ConfigurableTarget();
        PropertySetter setter = new PropertySetter(target);
        Properties properties = new Properties();
        properties.setProperty("handler", RecordingOptionHandler.class.getName());
        properties.setProperty("handler.name", "configured-handler");

        setter.setProperty("name", "example-appender");
        setter.setProperty("count", "42");
        setter.setProperty("timeout", "123456789");
        setter.setProperty("enabled", "true");
        PropertySetter.setProperties(target, properties, "");

        assertThat(target.getName()).isEqualTo("example-appender");
        assertThat(target.getCount()).isEqualTo(42);
        assertThat(target.getTimeout()).isEqualTo(123456789L);
        assertThat(target.isEnabled()).isTrue();
        assertThat(target.getHandler()).isInstanceOfSatisfying(RecordingOptionHandler.class, handler -> {
            assertThat(handler.getName()).isEqualTo("configured-handler");
            assertThat(handler.isActivated()).isTrue();
        });
    }

    public static final class ConfigurableTarget {

        private String name;
        private int count;
        private long timeout;
        private boolean enabled;
        private OptionHandler handler;

        public String getName() {
            return name;
        }

        public void setName(String name) {
            this.name = name;
        }

        public int getCount() {
            return count;
        }

        public void setCount(int count) {
            this.count = count;
        }

        public long getTimeout() {
            return timeout;
        }

        public void setTimeout(long timeout) {
            this.timeout = timeout;
        }

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        public OptionHandler getHandler() {
            return handler;
        }

        public void setHandler(OptionHandler handler) {
            this.handler = handler;
        }
    }

    public static final class RecordingOptionHandler implements OptionHandler {

        private String name;
        private boolean activated;

        public String getName() {
            return name;
        }

        public void setName(String name) {
            this.name = name;
        }

        public boolean isActivated() {
            return activated;
        }

        @Override
        public void activateOptions() {
            activated = true;
        }
    }
}
