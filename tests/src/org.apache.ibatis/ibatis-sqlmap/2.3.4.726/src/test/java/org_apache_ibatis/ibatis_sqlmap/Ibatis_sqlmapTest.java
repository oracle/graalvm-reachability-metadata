/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_ibatis.ibatis_sqlmap;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import static org.assertj.core.api.Assertions.assertThat;

@Timeout(120)
public class Ibatis_sqlmapTest {
    @Test
    void queriesMappedStatementsWithParameters() throws Exception {
        try (SqlMapFixture fixture = new SqlMapFixture(false)) {
            assertThat(fixture.client().queryForList("catalog.names", 7))
                    .containsExactly("alpha", "beta");
            assertThat(fixture.client().queryForList("catalog.names", 99)).isEmpty();
        }
    }
}
