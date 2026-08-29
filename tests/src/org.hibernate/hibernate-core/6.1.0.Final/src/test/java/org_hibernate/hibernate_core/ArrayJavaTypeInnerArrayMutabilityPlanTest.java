/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_hibernate.hibernate_core;

import org.hibernate.type.descriptor.java.ArrayJavaType;
import org.hibernate.type.descriptor.java.StringJavaType;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNoException;

public class ArrayJavaTypeInnerArrayMutabilityPlanTest {

    @Test
    public void deepCopiesAnObjectArray() {
        ArrayJavaType<String> type = new ArrayJavaType<>(StringJavaType.INSTANCE);
        String[] original = {"one", "two"};
        String[] copy = type.getMutabilityPlan().deepCopy(original);

        assertThat(copy).containsExactly("one", "two").isNotSameAs(original);
        assertThatNoException().isThrownBy(() -> copy[0] = "changed");
    }
}
