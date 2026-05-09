/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_sun_jersey.jersey_core;

import static org.assertj.core.api.Assertions.assertThat;

import com.sun.jersey.core.util.StringKeyStringValueIgnoreCaseMultivaluedMap;
import java.util.List;
import java.util.Objects;
import org.junit.jupiter.api.Test;

public class StringKeyStringValueIgnoreCaseMultivaluedMapTest {
    @Test
    void convertsAllValuesWithPublicStringConstructor() {
        final StringKeyStringValueIgnoreCaseMultivaluedMap map = new StringKeyStringValueIgnoreCaseMultivaluedMap();
        map.addObject("Header", "alpha");
        map.addObject("HEADER", "beta");

        final List<ConvertedValue> values = map.get("header", ConvertedValue.class);

        assertThat(values).containsExactly(new ConvertedValue("alpha"), new ConvertedValue("beta"));
    }

    @Test
    void convertsFirstValueWithExplicitTargetType() {
        final StringKeyStringValueIgnoreCaseMultivaluedMap map = new StringKeyStringValueIgnoreCaseMultivaluedMap();
        map.addObject("Header", "alpha");
        map.addObject("header", "beta");

        final ConvertedValue firstValue = map.getFirst("HEADER", ConvertedValue.class);

        assertThat(firstValue).isEqualTo(new ConvertedValue("alpha"));
    }

    @Test
    void convertsFirstValueUsingDefaultValueType() {
        final StringKeyStringValueIgnoreCaseMultivaluedMap map = new StringKeyStringValueIgnoreCaseMultivaluedMap();
        map.putSingleObject("Header", "alpha");
        final ConvertedValue defaultValue = new ConvertedValue("fallback");

        final ConvertedValue firstValue = map.getFirst("HEADER", defaultValue);

        assertThat(firstValue).isEqualTo(new ConvertedValue("alpha"));
        assertThat(map.getFirst("missing", defaultValue)).isSameAs(defaultValue);
    }

    public static final class ConvertedValue {
        private final String value;

        public ConvertedValue(String value) {
            this.value = value;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) {
                return true;
            }
            if (!(o instanceof ConvertedValue)) {
                return false;
            }
            final ConvertedValue that = (ConvertedValue) o;
            return Objects.equals(value, that.value);
        }

        @Override
        public int hashCode() {
            return Objects.hash(value);
        }
    }
}
