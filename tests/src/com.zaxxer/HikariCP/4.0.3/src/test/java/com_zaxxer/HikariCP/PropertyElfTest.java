/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_zaxxer.HikariCP;

import java.util.Properties;
import java.util.Set;

import com.zaxxer.hikari.util.PropertyElf;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class PropertyElfTest {

    @Test
    void setTargetFromPropertiesUsesSupportedSetterConversions() {
        ConfigurableBean bean = new ConfigurableBean();
        Object rawValue = new Object();
        Properties properties = new Properties();
        properties.setProperty("intValue", "7");
        properties.setProperty("longValue", "123456789");
        properties.setProperty("shortValue", "12");
        properties.setProperty("enabled", "true");
        properties.setProperty("name", "integration-pool");
        properties.setProperty("component", InstantiableComponent.class.getName());
        properties.put("rawValue", rawValue);

        PropertyElf.setTargetFromProperties(bean, properties);

        assertThat(bean.getIntValue()).isEqualTo(7);
        assertThat(bean.getLongValue()).isEqualTo(123456789L);
        assertThat(bean.getShortValue()).isEqualTo((short) 12);
        assertThat(bean.isEnabled()).isTrue();
        assertThat(bean.getName()).isEqualTo("integration-pool");
        assertThat(bean.getComponent()).isInstanceOf(InstantiableComponent.class);
        assertThat(bean.getRawValue()).isSameAs(rawValue);
    }

    @Test
    void getPropertyNamesFindsPropertiesBackedByGettersAndSetters() {
        Set<String> propertyNames = PropertyElf.getPropertyNames(ConfigurableBean.class);

        assertThat(propertyNames).contains(
                "intValue",
                "longValue",
                "shortValue",
                "enabled",
                "name",
                "component",
                "rawValue"
        );
    }

    @Test
    void getPropertyUsesGetAndIsAccessors() {
        ConfigurableBean bean = new ConfigurableBean();
        bean.setName("steady");
        bean.setEnabled(true);

        assertThat(PropertyElf.getProperty("name", bean)).isEqualTo("steady");
        assertThat(PropertyElf.getProperty("enabled", bean)).isEqualTo(true);
    }

    public interface Component {
        String value();
    }

    public static final class InstantiableComponent implements Component {

        @Override
        public String value() {
            return "instantiated";
        }
    }

    public static final class ConfigurableBean {

        private int intValue;
        private long longValue;
        private short shortValue;
        private boolean enabled;
        private String name;
        private Component component;
        private Object rawValue;

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

        public short getShortValue() {
            return shortValue;
        }

        public void setShortValue(short shortValue) {
            this.shortValue = shortValue;
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

        public Component getComponent() {
            return component;
        }

        public void setComponent(Component component) {
            this.component = component;
        }

        public Object getRawValue() {
            return rawValue;
        }

        public void setRawValue(Object rawValue) {
            this.rawValue = rawValue;
        }
    }
}
