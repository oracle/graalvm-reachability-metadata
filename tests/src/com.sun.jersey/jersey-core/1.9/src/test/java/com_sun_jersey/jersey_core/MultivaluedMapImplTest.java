/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_sun_jersey.jersey_core;

import static org.assertj.core.api.Assertions.assertThat;

import com.sun.jersey.core.util.MultivaluedMapImpl;
import java.util.List;
import java.util.Objects;
import org.junit.jupiter.api.Test;

public class MultivaluedMapImplTest {
    @Test
    void convertsAllValuesWithPublicStringConstructor() {
        final MultivaluedMapImpl map = new MultivaluedMapImpl();
        map.add("letters", "alpha");
        map.add("letters", "beta");

        final List<ConvertedValue> values = map.get("letters", ConvertedValue.class);

        assertThat(values).containsExactly(new ConvertedValue("alpha"), new ConvertedValue("beta"));
    }

    @Test
    void convertsFirstValueWithExplicitTargetType() {
        final MultivaluedMapImpl map = new MultivaluedMapImpl();
        map.add("letters", "alpha");
        map.add("letters", "beta");

        final ConvertedValue firstValue = map.getFirst("letters", ConvertedValue.class);

        assertThat(firstValue).isEqualTo(new ConvertedValue("alpha"));
    }

    @Test
    void convertsFirstValueUsingDefaultValueType() {
        final MultivaluedMapImpl map = new MultivaluedMapImpl();
        map.add("letters", "alpha");
        final ConvertedValue defaultValue = new ConvertedValue("fallback");

        final ConvertedValue firstValue = map.getFirst("letters", defaultValue);

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
