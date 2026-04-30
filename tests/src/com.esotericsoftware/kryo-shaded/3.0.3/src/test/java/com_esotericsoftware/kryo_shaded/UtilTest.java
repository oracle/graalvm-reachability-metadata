/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_esotericsoftware.kryo_shaded;

import com.esotericsoftware.kryo.util.Util;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class UtilTest {
    @Test
    void formatsObjectUsingPublicToStringOverride() {
        StringBackedValue value = new StringBackedValue("kryo");

        String formatted = Util.string(value);

        assertThat(formatted).isEqualTo("value:kryo");
    }

    @Test
    void formatsObjectWithoutToStringOverrideByTypeName() {
        String formatted = Util.string(new PlainValue());

        assertThat(formatted).endsWith("PlainValue");
    }

    public static class StringBackedValue {
        private final String value;

        public StringBackedValue(String value) {
            this.value = value;
        }

        @Override
        public String toString() {
            return "value:" + value;
        }
    }

    public static class PlainValue {
    }
}
