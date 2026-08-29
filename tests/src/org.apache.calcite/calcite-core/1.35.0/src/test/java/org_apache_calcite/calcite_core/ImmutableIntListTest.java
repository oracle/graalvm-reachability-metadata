/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_calcite.calcite_core;

import org.apache.calcite.util.ImmutableIntList;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class ImmutableIntListTest {
    @Test
    void createsTypedArrayWhenDestinationIsTooSmall() {
        Number[] values = ImmutableIntList.of(2, 4, 6).toArray(new Number[0]);

        assertThat(values).containsExactly(2, 4, 6).isExactlyInstanceOf(Number[].class);
    }
}
