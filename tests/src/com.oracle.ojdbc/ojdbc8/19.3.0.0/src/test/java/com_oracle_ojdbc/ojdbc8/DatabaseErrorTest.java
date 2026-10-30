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

public class DatabaseErrorTest {
    @Test
    void suppliesTheJdbcDiagnosticForAnUnknownOracleProtocol() {
        SQLException error = assertThrows(SQLException.class,
                () -> new OracleDriver().connect("jdbc:oracle:unknown:@sample", null));

        assertThat(error.getErrorCode()).isEqualTo(17067);
        assertThat(error.getSQLState()).isNotBlank();
        assertThat(error.getMessage()).containsIgnoringCase("Invalid Oracle URL");
    }
}
