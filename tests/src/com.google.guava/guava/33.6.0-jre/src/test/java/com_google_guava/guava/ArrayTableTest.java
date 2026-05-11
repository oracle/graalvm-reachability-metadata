/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_google_guava.guava;

import static org.assertj.core.api.Assertions.assertThat;

import com.google.common.collect.ArrayTable;
import java.util.List;
import org.junit.jupiter.api.Test;

public class ArrayTableTest {
    @Test
    void toArrayReturnsTypedTwoDimensionalCopy() {
        ArrayTable<String, String, Integer> table = ArrayTable.create(
                List.of("north", "south"),
                List.of("morning", "evening"));
        table.put("north", "morning", 11);
        table.put("south", "evening", 22);

        Integer[][] array = table.toArray(Integer.class);

        assertThat(array).isInstanceOf(Integer[][].class);
        assertThat(array.length).isEqualTo(2);
        assertThat(array[0].length).isEqualTo(2);
        assertThat(array[0]).containsExactly(11, null);
        assertThat(array[1]).containsExactly(null, 22);

        table.put("north", "morning", 33);
        array[1][0] = 44;

        assertThat(array[0][0]).isEqualTo(11);
        assertThat(table.get("south", "morning")).isNull();
    }
}
