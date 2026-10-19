/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_pulsar.pulsar_client_all;

import static org.assertj.core.api.Assertions.assertThat;

import org.apache.pulsar.shade.org.glassfish.jersey.internal.util.collection.MultivaluedStringMap;
import org.junit.jupiter.api.Test;

public class MultivaluedStringMapTest {
    @Test
    void convertsFirstValueWithBothTypedOverloads() {
        MultivaluedStringMap values = new MultivaluedStringMap();
        values.putSingle("count", "42");
        values.putSingle("priority", "7");

        NumericValue count = values.getFirst("count", NumericValue.class);
        NumericValue priority = values.getFirst("priority", new NumericValue("0"));

        assertThat(count.value).isEqualTo(42);
        assertThat(priority.value).isEqualTo(7);
        assertThat(values.getFirst("missing", new NumericValue("5")).value).isEqualTo(5);
    }

    public static class NumericValue {
        private final int value;

        public NumericValue(String value) {
            this.value = Integer.parseInt(value);
        }
    }
}
