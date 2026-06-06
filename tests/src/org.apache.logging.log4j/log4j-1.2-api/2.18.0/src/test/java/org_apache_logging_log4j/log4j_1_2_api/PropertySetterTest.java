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

        setter.setProperty("name", "example-appender");
        setter.setProperty("count", "42");
        setter.setProperty("timeout", "123456789");
        setter.setProperty("enabled", "true");

        assertThat(target.getName()).isEqualTo("example-appender");
        assertThat(target.getCount()).isEqualTo(42);
        assertThat(target.getTimeout()).isEqualTo(123456789L);
        assertThat(target.isEnabled()).isTrue();
    }

    @Test
    void setsNestedOptionHandlerPropertyFromProperties() {
        ConfigurableTarget target = new ConfigurableTarget();
        Properties properties = new Properties();
        properties.setProperty("log4j.appender.test.handler", RecordingOptionHandler.class.getName());
        properties.setProperty("log4j.appender.test.handler.message", "configured-handler");

        PropertySetter.setProperties(target, properties, "log4j.appender.test.");

        assertThat(target.getHandler()).isInstanceOf(RecordingOptionHandler.class);
        RecordingOptionHandler handler = (RecordingOptionHandler) target.getHandler();
        assertThat(handler.getMessage()).isEqualTo("configured-handler");
        assertThat(handler.isActivated()).isTrue();
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

        private String message;
        private boolean activated;

        public String getMessage() {
            return message;
        }

        public void setMessage(String message) {
            this.message = message;
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
