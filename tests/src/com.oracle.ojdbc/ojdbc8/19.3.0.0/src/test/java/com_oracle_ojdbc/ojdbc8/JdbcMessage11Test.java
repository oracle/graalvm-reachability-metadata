/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_oracle_ojdbc.ojdbc8;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.sql.SQLException;
import oracle.jdbc.OracleDriver;
import org.junit.jupiter.api.Test;

public class JdbcMessage11Test {
    @Test
    void formatsTheMissingJdbcUrlDiagnostic() {
        SQLException error = assertThrows(SQLException.class, () -> new OracleDriver().acceptsURL(null));

        assertThat(error.getErrorCode()).isEqualTo(17296);
        assertThat(error.getMessage()).containsIgnoringCase("URL").containsIgnoringCase("null");
    }
}
