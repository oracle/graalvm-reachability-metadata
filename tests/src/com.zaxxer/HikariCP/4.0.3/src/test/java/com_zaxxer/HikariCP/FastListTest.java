/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_zaxxer.HikariCP;

import com.zaxxer.hikari.util.FastList;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class FastListTest {

    @Test
    void defaultConstructorCreatesTypedBackingArrayAndGrowsWhenCapacityIsExceeded() {
        FastList<String> list = new FastList<>(String.class);

        for (int index = 0; index < 33; index++) {
            list.add("value-" + index);
        }

        assertThat(list).hasSize(33);
        assertThat(list.get(0)).isEqualTo("value-0");
        assertThat(list.get(31)).isEqualTo("value-31");
        assertThat(list.get(32)).isEqualTo("value-32");
    }

    @Test
    void capacityConstructorCreatesTypedBackingArrayUsingRequestedCapacity() {
        FastList<Integer> list = new FastList<>(Integer.class, 2);

        list.add(7);
        list.add(11);

        assertThat(list).hasSize(2);
        assertThat(list.get(0)).isEqualTo(7);
        assertThat(list.get(1)).isEqualTo(11);
    }
}
