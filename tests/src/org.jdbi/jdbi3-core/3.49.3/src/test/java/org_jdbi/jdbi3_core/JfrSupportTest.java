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

public class JfrSupportTest {
    @Test
    void recordsStatementLifecycleWhileExecutingATransaction() {
        Jdbi jdbi = Jdbi.create("jdbc:h2:mem:jfr_statements;DB_CLOSE_DELAY=-1");

        int result = jdbi.inTransaction(handle -> handle.createQuery("select 91")
                .mapTo(int.class)
                .one());

        assertThat(result).isEqualTo(91);
    }
}
