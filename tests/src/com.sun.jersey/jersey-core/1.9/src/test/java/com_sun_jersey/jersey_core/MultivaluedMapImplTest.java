/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_sun_jersey.jersey_core;

import com.sun.jersey.core.util.MultivaluedMapImpl;
import java.util.List;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class MultivaluedMapImplTest {
    @Test
    void getConvertsEveryValueWithStringConstructor() {
        MultivaluedMapImpl values = new MultivaluedMapImpl();
        values.add("header", "first");
        values.add("header", "second");

        List<StringConstructedValue> converted = values.get("header", StringConstructedValue.class);

        assertThat(converted)
                .extracting(StringConstructedValue::value)
                .containsExactly("first", "second");
    }

    @Test
    void getFirstWithClassConvertsFirstValueWithStringConstructor() {
        MultivaluedMapImpl values = new MultivaluedMapImpl();
        values.add("header", "first");
        values.add("header", "second");

        StringConstructedValue converted = values.getFirst("header", StringConstructedValue.class);

        assertThat(converted.value()).isEqualTo("first");
    }

    @Test
    void getFirstWithDefaultValueConvertsPresentValueWithDefaultValueType() {
        MultivaluedMapImpl values = new MultivaluedMapImpl();
        values.add("header", "first");
        StringConstructedValue defaultValue = new StringConstructedValue("default");

        StringConstructedValue converted = values.getFirst("header", defaultValue);

        assertThat(converted).isNotSameAs(defaultValue);
        assertThat(converted.value()).isEqualTo("first");
    }

    public static final class StringConstructedValue {
        private final String value;

        public StringConstructedValue(String value) {
            this.value = value;
        }

        public String value() {
            return value;
        }
    }
}
