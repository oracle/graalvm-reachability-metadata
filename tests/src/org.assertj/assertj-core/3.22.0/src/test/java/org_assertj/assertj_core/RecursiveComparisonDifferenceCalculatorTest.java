/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_assertj.assertj_core;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.Map;

import org.junit.jupiter.api.Test;

public class RecursiveComparisonDifferenceCalculatorTest {
    @Test
    void canHonorOrIgnoreDomainEquality() {
        Shipment actual = new Shipment(new Destination("north", "first label"));
        Shipment expected = new Shipment(new Destination("north", "second label"));
        assertThat(actual).usingRecursiveComparison().usingOverriddenEquals().isEqualTo(expected);
        AssertionError failure = assertThrows(AssertionError.class,
                () -> assertThat(actual).usingRecursiveComparison().isEqualTo(expected));
        assertThat(failure).hasMessageContaining("destination.label");
    }

    @Test
    void comparesMapsWithDomainKeysAndNestedValues() {
        Map<Destination, Quantity> actual = Map.of(new Destination("north", "warehouse"), new Quantity(12));
        Map<Destination, Quantity> expected = Map.of(new Destination("north", "warehouse"), new Quantity(12));
        assertThat(actual).usingRecursiveComparison().isEqualTo(expected);
        AssertionError failure = assertThrows(AssertionError.class,
                () -> assertThat(actual).usingRecursiveComparison()
                        .isEqualTo(Map.of(new Destination("north", "warehouse"), new Quantity(13))));
        assertThat(failure).hasMessageContaining("count");
    }

    @Test
    void comparesMapKeysByTheirFieldsWhenTheyHaveNoHashCodeOverride() {
        Map<Quantity, String> actual = Map.of(new Quantity(12), "available");
        Map<Quantity, String> expected = Map.of(new Quantity(12), "available");
        assertThat(actual).usingRecursiveComparison().isEqualTo(expected);
    }

    public static class Shipment {
        private final Destination destination;

        public Shipment(Destination destination) {
            this.destination = destination;
        }
    }

    public static class Destination {
        private final String code;
        private final String label;

        public Destination(String code, String label) {
            this.code = code;
            this.label = label;
        }

        @Override
        public boolean equals(Object other) {
            return other instanceof Destination && code.equals(((Destination) other).code);
        }

        @Override
        public int hashCode() {
            return code.hashCode();
        }
    }

    public static class Quantity {
        private final int count;

        public Quantity(int count) {
            this.count = count;
        }
    }
}
