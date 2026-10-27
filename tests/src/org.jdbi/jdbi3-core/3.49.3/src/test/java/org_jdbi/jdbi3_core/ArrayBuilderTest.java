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

public class ArrayBuilderTest {
    @Test
    void collectsRowsIntoAPrimitiveArray() {
        Jdbi jdbi = Jdbi.create("jdbc:h2:mem:array_collector;DB_CLOSE_DELAY=-1");

        int[] values = jdbi.withHandle(handle -> handle
                .createQuery("select 3 as number union all select 1 union all select 4")
                .collectInto(int[].class));

        assertThat(values).containsExactly(3, 1, 4);
    }
}
