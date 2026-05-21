/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_activemq.artemis_commons;

import static org.assertj.core.api.Assertions.assertThat;

import org.apache.activemq.artemis.utils.collections.LongHashSet;
import org.junit.jupiter.api.Test;

public class LongHashSetTest {
    @Test
    public void toArrayCreatesTypedArrayWhenDestinationIsTooSmall() {
        LongHashSet set = new LongHashSet();
        set.add(7L);
        set.add(11L);
        set.add(-2L);

        Long[] values = set.toArray(new Long[0]);

        assertThat(values).containsExactlyInAnyOrder(7L, 11L, -2L);
    }
}
