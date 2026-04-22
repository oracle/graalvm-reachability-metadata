/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_zaxxer.HikariCP_java7;

import com.zaxxer.hikari.util.FastList;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class FastListTest {
    @Test
    public void defaultConstructorCreatesListWithDefaultCapacity() {
        FastList<String> values = new FastList<>(String.class);

        values.add("first");

        assertThat(values.size()).isEqualTo(1);
        assertThat(values.get(0)).isEqualTo("first");
    }

    @Test
    public void sizedConstructorCreatesListWithRequestedCapacity() {
        FastList<String> values = new FastList<>(String.class, 2);

        values.add("first");
        values.add("second");

        assertThat(values.size()).isEqualTo(2);
        assertThat(values.get(0)).isEqualTo("first");
        assertThat(values.get(1)).isEqualTo("second");
    }

    @Test
    public void addResizesBackingArrayWhenCapacityIsExceeded() {
        FastList<String> values = new FastList<>(String.class, 1);

        values.add("first");
        values.add("second");

        assertThat(values.size()).isEqualTo(2);
        assertThat(values.get(0)).isEqualTo("first");
        assertThat(values.get(1)).isEqualTo("second");
        assertThat(values.removeLast()).isEqualTo("second");
        assertThat(values.removeLast()).isEqualTo("first");
        assertThat(values.isEmpty()).isTrue();
    }
}
