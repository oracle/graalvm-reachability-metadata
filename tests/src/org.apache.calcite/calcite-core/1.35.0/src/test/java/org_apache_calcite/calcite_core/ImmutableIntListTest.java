/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_calcite.calcite_core;

import static org.assertj.core.api.Assertions.assertThat;

import org.apache.calcite.util.ImmutableIntList;
import org.junit.jupiter.api.Test;

public class ImmutableIntListTest {
    @Test
    void toArrayAllocatesArrayUsingRequestedRuntimeComponentType() {
        ImmutableIntList list = ImmutableIntList.of(2, 3, 5);
        Number[] destination = new Number[0];

        Number[] values = list.toArray(destination);

        assertThat(values).isNotSameAs(destination);
        assertThat(values.getClass()).isEqualTo(Number[].class);
        assertThat(values).containsExactly(2, 3, 5);
    }
}
