/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_mockito.mockito_core;

import org.junit.jupiter.api.Test;
import org.mockito.internal.util.collections.HashCodeAndEqualsSafeSet;

import static org.assertj.core.api.Assertions.assertThat;

public class HashCodeAndEqualsSafeSetTest {
    @Test
    void toArrayCreatesTypedArrayWhenProvidedArrayIsTooSmall() {
        UnhashableMock first = new UnhashableMock("first");
        UnhashableMock second = new UnhashableMock("second");
        HashCodeAndEqualsSafeSet mocks = HashCodeAndEqualsSafeSet.of(first, second);

        UnhashableMock[] array = mocks.toArray(new UnhashableMock[0]);

        assertThat(array).hasSize(2);
        assertThat(array).anySatisfy(element -> assertThat(element).isSameAs(first));
        assertThat(array).anySatisfy(element -> assertThat(element).isSameAs(second));
        assertThat(array.getClass().getComponentType()).isEqualTo(UnhashableMock.class);
    }

    private static final class UnhashableMock {
        private final String name;

        private UnhashableMock(String name) {
            this.name = name;
        }

        @Override
        public boolean equals(Object other) {
            throw new AssertionError("HashCodeAndEqualsSafeSet should not call mock equals");
        }

        @Override
        public int hashCode() {
            throw new AssertionError("HashCodeAndEqualsSafeSet should not call mock hashCode");
        }

        @Override
        public String toString() {
            return name;
        }
    }
}
