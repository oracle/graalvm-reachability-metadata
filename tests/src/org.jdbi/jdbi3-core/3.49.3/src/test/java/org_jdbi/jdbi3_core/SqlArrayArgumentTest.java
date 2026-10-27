/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_jdbi.jdbi3_core;

import org.jdbi.v3.core.Jdbi;
import org.jdbi.v3.core.h2.H2DatabasePlugin;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class SqlArrayArgumentTest {
    @Test
    void bindsAPrimitiveArrayUsingTheH2ArrayStrategy() {
        Jdbi jdbi = Jdbi.create("jdbc:h2:mem:array_arguments;DB_CLOSE_DELAY=-1")
                .installPlugin(new H2DatabasePlugin());

        int size = jdbi.withHandle(handle -> handle.createQuery("select cardinality(:numbers)")
                .bind("numbers", new int[] {3, 6, 9, 12})
                .mapTo(int.class)
                .one());

        assertThat(size).isEqualTo(4);
    }
}
