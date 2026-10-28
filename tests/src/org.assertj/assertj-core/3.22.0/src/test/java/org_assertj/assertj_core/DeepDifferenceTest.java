/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_assertj.assertj_core;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.Set;

import org.junit.jupiter.api.Test;

public class DeepDifferenceTest {
    @Test
    @SuppressWarnings("deprecation")
    void comparesUnorderedObjectGraphsAndHonorsValueEquality() {
        Inventory actual = new Inventory(Set.of(new Part("bolt", 12), new Part("nut", 24)),
                new Reference("batch-a", "original label"));
        Inventory expected = new Inventory(Set.of(new Part("nut", 24), new Part("bolt", 12)),
                new Reference("batch-a", "updated label"));
        assertThat(actual).isEqualToComparingFieldByFieldRecursively(expected);
    }

    @Test
    @SuppressWarnings("deprecation")
    void reportsDifferentNestedCollectionValues() {
        Inventory actual = new Inventory(Set.of(new Part("bolt", 12)), new Reference("a", "stock"));
        Inventory expected = new Inventory(Set.of(new Part("bolt", 13)), new Reference("a", "stock"));
        AssertionError failure = assertThrows(AssertionError.class,
                () -> assertThat(actual).isEqualToComparingFieldByFieldRecursively(expected));
        assertThat(failure).hasMessageContaining("parts");
    }

    public static class Inventory {
        private final Set<Part> parts;
        private final Reference reference;

        public Inventory(Set<Part> parts, Reference reference) {
            this.parts = parts;
            this.reference = reference;
        }
    }

    public static class Part {
        private final String name;
        private final int quantity;

        public Part(String name, int quantity) {
            this.name = name;
            this.quantity = quantity;
        }
    }

    public static class Reference {
        private final String id;
        private final String label;

        public Reference(String id, String label) {
            this.id = id;
            this.label = label;
        }

        @Override
        public boolean equals(Object other) {
            return other instanceof Reference && id.equals(((Reference) other).id);
        }

        @Override
        public int hashCode() {
            return id.hashCode();
        }
    }
}
