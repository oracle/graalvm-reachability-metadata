/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_jdbi.jdbi3_core;

import org.jdbi.v3.core.Jdbi;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class ArrayColumnMapperTest {
    @Test
    void mapsASqlArrayColumnToAPrimitiveArray() {
        Jdbi jdbi = Jdbi.create("jdbc:h2:mem:array_columns;DB_CLOSE_DELAY=-1");

        int[] values = jdbi.withHandle(handle -> handle
                .createQuery("select cast(array[2, 4, 8] as integer array)")
                .mapTo(int[].class)
                .one());

        assertThat(values).containsExactly(2, 4, 8);
    }
}
