/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com.zaxxer.hikaricp.test;

import com.zaxxer.hikari.util.PropertyElf;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.Properties;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

public class PropertyElfTest {
    @Test
    void populatesBeanAndDiscoversWritableProperties() {
        MutableBean bean = new MutableBean();
        Properties properties = new Properties();
        properties.setProperty("intValue", "42");
        properties.setProperty("longValue", "42000000000");
        properties.setProperty("enabled", "true");
        properties.setProperty("letters", "abc");
        properties.setProperty("payload", ConstructedPayload.class.getName());

        PropertyElf.setTargetFromProperties(bean, properties);
        Set<String> propertyNames = PropertyElf.getPropertyNames(MutableBean.class);

        assertThat(bean.getIntValue()).isEqualTo(42);
        assertThat(bean.getLongValue()).isEqualTo(42_000_000_000L);
        assertThat(bean.isEnabled()).isTrue();
        assertThat(bean.getLetters()).containsExactly('a', 'b', 'c');
        assertThat(bean.getPayload()).isInstanceOf(ConstructedPayload.class);
        assertThat(PropertyElf.getProperty("intValue", bean)).isEqualTo(42);
        assertThat(propertyNames).contains("enabled", "intValue", "letters", "longValue", "payload");
        assertThat(propertyNames).doesNotContain("class", "readOnlyValue");
    }

    public static final class MutableBean {
        private int intValue;
        private long longValue;
        private boolean enabled;
        private char[] letters;
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

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        public char[] getLetters() {
            return letters;
        }

        public void setLetters(char[] letters) {
            this.letters = Arrays.copyOf(letters, letters.length);
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

    public static final class ConstructedPayload {
        public ConstructedPayload() {
        }
    }
}
