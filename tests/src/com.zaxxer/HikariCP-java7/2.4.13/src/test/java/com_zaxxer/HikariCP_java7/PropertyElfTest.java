/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_zaxxer.HikariCP_java7;

import java.util.Properties;
import java.util.Set;

import com.zaxxer.hikari.util.PropertyElf;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class PropertyElfTest {
    @Test
    public void setTargetFromPropertiesAppliesSupportedSetterTypes() {
        TestTarget target = new TestTarget();
        Properties properties = new Properties();
        FallbackValue fallbackValue = new FallbackValue("fallback-value");

        properties.setProperty("intValue", "37");
        properties.setProperty("longValue", "123456789");
        properties.setProperty("enabled", "true");
        properties.setProperty("name", "pool");
        properties.put("fallbackDependency", fallbackValue);

        PropertyElf.setTargetFromProperties(target, properties);

        assertThat(target.getIntValue()).isEqualTo(37);
        assertThat(target.getLongValue()).isEqualTo(123456789L);
        assertThat(target.isEnabled()).isTrue();
        assertThat(target.getName()).isEqualTo("pool");
        assertThat(target.getFallbackDependency()).isSameAs(fallbackValue);
    }

    @Test
    public void getPropertyAndPropertyNamesResolveBeanAccessors() {
        TestTarget target = new TestTarget();
        target.setName("pool");
        target.setEnabled(true);

        Set<String> propertyNames = PropertyElf.getPropertyNames(TestTarget.class);

        assertThat(propertyNames).contains("name", "enabled");
        assertThat(propertyNames).doesNotContain("readOnly");
        assertThat(PropertyElf.getProperty("name", target)).isEqualTo("pool");
        assertThat(PropertyElf.getProperty("enabled", target)).isEqualTo(true);
    }

    public static final class TestTarget {
        private int intValue;
        private long longValue;
        private boolean enabled;
        private String name;
        private Object fallbackDependency;

        public int getIntValue() {
            return intValue;
        }

        public void setIntValue(int intValue) {
            this.intValue = intValue;
        }

        public long getLongValue() {
            return longValue;
        }

        public void setLongValue(long longValue) {
            this.longValue = longValue;
        }

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        public String getName() {
            return name;
        }

        public void setName(String name) {
            this.name = name;
        }

        public Object getFallbackDependency() {
            return fallbackDependency;
        }

        public void setFallbackDependency(Object fallbackDependency) {
            this.fallbackDependency = fallbackDependency;
        }

        public String getReadOnly() {
            return "read-only";
        }
    }

    public static final class FallbackValue {
        private final String value;

        public FallbackValue(String value) {
            this.value = value;
        }

        @Override
        public String toString() {
            return value;
        }
    }
}
