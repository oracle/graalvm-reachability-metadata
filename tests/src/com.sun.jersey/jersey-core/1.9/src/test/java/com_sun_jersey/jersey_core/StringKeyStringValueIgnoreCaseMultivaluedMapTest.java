/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_sun_jersey.jersey_core;

import com.sun.jersey.core.util.StringKeyStringValueIgnoreCaseMultivaluedMap;
import java.util.List;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class StringKeyStringValueIgnoreCaseMultivaluedMapTest {
    @Test
    void getConvertsEveryCaseInsensitiveValueWithStringConstructor() {
        StringKeyStringValueIgnoreCaseMultivaluedMap values = new StringKeyStringValueIgnoreCaseMultivaluedMap();
        values.addObject("Accept", "application/json");
        values.addObject("accept", "text/plain");

        List<StringConstructedValue> converted = values.get("ACCEPT", StringConstructedValue.class);

        assertThat(converted)
                .extracting(StringConstructedValue::value)
                .containsExactly("application/json", "text/plain");
    }

    @Test
    void getFirstWithClassConvertsFirstCaseInsensitiveValueWithStringConstructor() {
        StringKeyStringValueIgnoreCaseMultivaluedMap values = new StringKeyStringValueIgnoreCaseMultivaluedMap();
        values.addObject("Content-Type", "text/plain");
        values.addObject("content-type", "application/json");

        StringConstructedValue converted = values.getFirst("CONTENT-TYPE", StringConstructedValue.class);

        assertThat(converted.value()).isEqualTo("text/plain");
    }

    @Test
    void getFirstWithDefaultValueConvertsPresentValueWithDefaultValueType() {
        StringKeyStringValueIgnoreCaseMultivaluedMap values = new StringKeyStringValueIgnoreCaseMultivaluedMap();
        values.putSingleObject("ETag", "strong-value");
        StringConstructedValue defaultValue = new StringConstructedValue("default");

        StringConstructedValue converted = values.getFirst("etag", defaultValue);

        assertThat(converted).isNotSameAs(defaultValue);
        assertThat(converted.value()).isEqualTo("strong-value");
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
