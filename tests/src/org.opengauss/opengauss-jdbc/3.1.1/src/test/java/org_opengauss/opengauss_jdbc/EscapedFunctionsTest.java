/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_opengauss.opengauss_jdbc;

import org.junit.jupiter.api.Test;
import org.postgresql.jdbc.EscapedFunctions;

import java.sql.SQLException;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@SuppressWarnings("deprecation")
public class EscapedFunctionsTest {
    @Test
    void resolvesEscapedFunctionTranslatorThroughPublishedLookup() throws SQLException {
        assertThat(EscapedFunctions.getFunction(EscapedFunctions.POWER)).isNotNull();
        assertThat(EscapedFunctions.sqlpower(List.of("2", "8"))).isEqualTo("pow(2,8)");
    }
}
