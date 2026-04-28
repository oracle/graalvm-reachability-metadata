/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_thoughtworks_xstream.xstream;

import com.thoughtworks.xstream.converters.extended.ToStringConverter;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class ToStringConverterTest {
    @Test
    void createsValueUsingPublicStringConstructor() throws NoSuchMethodException {
        ToStringConverter converter = new ToStringConverter(StringBackedValue.class);

        Object converted = converter.fromString("native metadata");

        assertThat(converted).isInstanceOf(StringBackedValue.class);
        assertThat(((StringBackedValue)converted).value()).isEqualTo("native metadata");
    }

    @Test
    void convertsValueToItsStringRepresentation() throws NoSuchMethodException {
        ToStringConverter converter = new ToStringConverter(StringBackedValue.class);
        StringBackedValue value = new StringBackedValue("xstream converter");

        assertThat(converter.canConvert(StringBackedValue.class)).isTrue();
        assertThat(converter.toString(value)).isEqualTo("value:xstream converter");
    }

    public static final class StringBackedValue {
        private final String value;

        public StringBackedValue(String value) {
            this.value = value;
        }

        String value() {
            return value;
        }

        @Override
        public String toString() {
            return "value:" + value;
        }
    }
}
