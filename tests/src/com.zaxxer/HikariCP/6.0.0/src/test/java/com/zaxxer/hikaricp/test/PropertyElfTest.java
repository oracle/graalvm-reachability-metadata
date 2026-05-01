/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com.zaxxer.hikaricp.test;

import com.zaxxer.hikari.util.PropertyElf;
import org.junit.jupiter.api.Test;

import java.util.Properties;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

public class PropertyElfTest {
    @Test
    void setsTypedPropertiesFromTextValues() {
        MutableBean bean = new MutableBean();
        Properties properties = new Properties();
        properties.setProperty("intValue", "42");
        properties.setProperty("longValue", "42000000000");
        properties.setProperty("shortValue", "7");
        properties.setProperty("enabled", "true");
        properties.setProperty("flag", "true");
        properties.setProperty("letters", "abc");
        properties.setProperty("name", "hikari");
        properties.setProperty("payload", ConstructedPayload.class.getName());

        PropertyElf.setTargetFromProperties(bean, properties);

        assertThat(bean.getIntValue()).isEqualTo(42);
        assertThat(bean.getLongValue()).isEqualTo(42_000_000_000L);
        assertThat(bean.getShortValue()).isEqualTo((short) 7);
        assertThat(bean.isEnabled()).isTrue();
        assertThat(bean.getFlag()).isTrue();
        assertThat(bean.getLetters()).containsExactly('a', 'b', 'c');
        assertThat(bean.getName()).isEqualTo("hikari");
        assertThat(bean.getPayload()).isInstanceOf(ConstructedPayload.class);
    }

    @Test
    void usesOriginalValueWhenObjectPropertyIsNotAClassName() {
        MutableBean bean = new MutableBean();
        Properties properties = new Properties();
        properties.setProperty("payload", "not.a.LoadableClassName");

        PropertyElf.setTargetFromProperties(bean, properties);

        assertThat(bean.getPayload()).isEqualTo("not.a.LoadableClassName");
    }

    @Test
    void discoversBeanPropertyNamesWithMatchingSetters() {
        Set<String> propertyNames = PropertyElf.getPropertyNames(MutableBean.class);

        assertThat(propertyNames)
                .contains(
                        "enabled",
                        "flag",
                        "intValue",
                        "letters",
                        "longValue",
                        "name",
                        "payload",
                        "shortValue")
                .doesNotContain("class", "readOnlyValue");
    }

    @Test
    void readsPropertiesThroughGetAndIsAccessors() {
        MutableBean bean = new MutableBean();
        bean.setName("hikari");
        bean.setEnabled(true);

        assertThat(PropertyElf.getProperty("name", bean)).isEqualTo("hikari");
        assertThat(PropertyElf.getProperty("enabled", bean)).isEqualTo(true);
        assertThat(PropertyElf.getProperty("missing", bean)).isNull();
    }

    public static final class ConstructedPayload {
    }

    public static final class MutableBean {
        private int intValue;
        private long longValue;
        private short shortValue;
        private boolean enabled;
        private Boolean flag;
        private char[] letters;
        private String name;
        private Object payload;

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

        public Boolean getFlag() {
            return flag;
        }

        public void setFlag(Boolean flag) {
            this.flag = flag;
        }

        public char[] getLetters() {
            return letters;
        }

        public void setLetters(char[] letters) {
            this.letters = letters;
        }

        public String getName() {
            return name;
        }

        public void setName(String name) {
            this.name = name;
        }

        public Object getPayload() {
            return payload;
        }

        public void setPayload(Object payload) {
            this.payload = payload;
        }

        public String getReadOnlyValue() {
            return "read-only";
        }
    }
}
