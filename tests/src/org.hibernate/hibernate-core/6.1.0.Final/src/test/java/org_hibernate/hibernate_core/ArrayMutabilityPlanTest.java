/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_hibernate.hibernate_core;

import org.hibernate.type.descriptor.java.ArrayMutabilityPlan;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class ArrayMutabilityPlanTest {

    @Test
    public void deepCopiesArraysOfImmutableValues() {
        String[] original = {"one", "two"};
        String[] copy = (String[]) ArrayMutabilityPlan.INSTANCE.deepCopy(original);

        assertThat(copy).containsExactly("one", "two").isNotSameAs(original);
    }
}
