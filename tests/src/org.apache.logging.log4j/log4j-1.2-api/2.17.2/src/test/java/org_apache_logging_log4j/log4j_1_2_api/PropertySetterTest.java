/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_logging_log4j.log4j_1_2_api;

import static org.assertj.core.api.Assertions.assertThat;

import org.apache.log4j.Level;
import org.apache.log4j.config.PropertySetter;
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
        setter.setProperty("threshold", "ERROR");

        assertThat(target.getName()).isEqualTo("example-appender");
        assertThat(target.getCount()).isEqualTo(42);
        assertThat(target.getTimeout()).isEqualTo(123456789L);
        assertThat(target.isEnabled()).isTrue();
        assertThat(target.getThreshold()).isEqualTo(Level.ERROR);
    }

    public static final class ConfigurableTarget {

        private String name;
        private int count;
        private long timeout;
        private boolean enabled;
        private Level threshold;

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

        public Level getThreshold() {
            return threshold;
        }

        public void setThreshold(Level threshold) {
            this.threshold = threshold;
        }
    }
}
